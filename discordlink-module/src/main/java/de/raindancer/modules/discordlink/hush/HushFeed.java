package de.raindancer.modules.discordlink.hush;

import de.raindancer.modules.discordlink.hush.HushTransport.Response;
import de.raindancer.modules.discordlink.rules.HushRetryRule;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Batches events for Hush and sends them off the server's threads: every ~2 seconds or at 50 waiting, whichever is
 * first. It never throws to its caller, never blocks a caller, and says one thing per failure streak instead of a
 * line per failed request — a Hush that is down must not cost the server anything.
 *
 * <p>All state is touched by one thread (the feed's own); only {@link #status()} reads it from outside.
 */
public final class HushFeed implements AutoCloseable {

    public static final int MAX_BATCH = 50;
    public static final int QUEUE_LIMIT = 2_000;
    static final int MAX_BODY_BYTES = 64 * 1024;
    /** The pump's own beat; the 2 s flush is checked against it. */
    private static final long BEAT_MILLIS = 250;

    private record Queued(String type, DataObject event, long at) {
    }

    /** What /discordlink hush status shows. Holds no token. */
    public record Status(String url, String serverName, boolean hasToken, Long lastSuccessAt, Long lastFailureAt,
                         String lastFailure, int queued, long dropped, long sent, int failureStreak) {

        public List<String> lines() {
            List<String> lines = new ArrayList<>();
            lines.add("url: " + (url == null || url.isBlank() ? "(none)" : url));
            lines.add("server name: " + serverName);
            lines.add("token: " + (hasToken ? "set" : "NOT SET (console: /discordlink hushtoken TOKEN)"));
            lines.add("last success: " + (lastSuccessAt == null ? "never" : java.time.Instant.ofEpochMilli(lastSuccessAt)));
            lines.add("last failure: " + (lastFailureAt == null ? "never"
                    : java.time.Instant.ofEpochMilli(lastFailureAt) + " (" + lastFailure + ")"));
            lines.add("queue: " + queued + " waiting, " + sent + " sent, " + dropped + " dropped"
                    + (failureStreak > 0 ? ", failing for " + failureStreak + " tries" : ""));
            return lines;
        }
    }

    private final HushTransport transport;
    private final Supplier<HushConfig> config;
    private final LongSupplier clock;
    private final Consumer<String> warn;
    private final Consumer<String> info;
    private final HushRetryRule rule;
    private final long flushMillis;

    private final ArrayDeque<Queued> queue = new ArrayDeque<>();
    private long retryAt;
    private int failures;
    private boolean warned;
    private volatile Long lastSuccessAt;
    private volatile Long lastFailureAt;
    private volatile String lastFailure = "";
    private volatile long dropped;
    private volatile long sent;
    private volatile int queued;

    private ExecutorService executor;

    public HushFeed(HushTransport transport, Supplier<HushConfig> config, LongSupplier clock, Consumer<String> warn,
                    Consumer<String> info, HushRetryRule rule, long flushMillis) {
        this.transport = transport;
        this.config = config;
        this.clock = clock;
        this.warn = warn;
        this.info = info;
        this.rule = rule;
        this.flushMillis = flushMillis;
    }

    // ------------------------------------------------------------------ for the server

    /** Starts the feed's own thread and its beat. */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        ScheduledExecutorService own = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "hush-feed");
            thread.setDaemon(true);
            return thread;
        });
        own.scheduleWithFixedDelay(() -> pump(false), BEAT_MILLIS, BEAT_MILLIS, TimeUnit.MILLISECONDS);
        executor = own;
    }

    /**
     * Builds and queues an event on the feed's thread, so the caller (a flag, a command, a join) pays nothing.
     * {@code build} runs there too: it may look up names.
     */
    public void offer(String type, Supplier<DataObject> build) {
        ExecutorService run = executor;
        if (run == null) {
            return;
        }
        try {
            run.execute(() -> {
                try {
                    if (!wanted(type)) {
                        return;
                    }
                    DataObject event = build.get();
                    if (event == null) {
                        return;
                    }
                    enqueue(type, event);
                    if (queue.size() >= MAX_BATCH) {
                        pump(false);
                    }
                } catch (RuntimeException | LinkageError broken) {
                    lastFailure = "could not build an event: " + broken.getClass().getSimpleName();
                    lastFailureAt = clock.getAsLong();
                }
            });
        } catch (RuntimeException rejected) {
            // shut down: the server is going away
        }
    }

    /** Sends what is left, for at most {@code waitMillis}, and stops. */
    public void stop(long waitMillis) {
        ExecutorService run;
        synchronized (this) {
            run = executor;
            executor = null;
        }
        if (run == null) {
            return;
        }
        try {
            run.submit(() -> pump(true));
            run.shutdown();
            if (!run.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                run.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            run.shutdownNow();
        } catch (RuntimeException ignored) {
            run.shutdownNow();
        }
    }

    @Override
    public void close() {
        stop(0);
    }

    public Status status() {
        HushConfig now = config.get();
        return new Status(now.url(), now.serverName(), now.hasToken(), lastSuccessAt, lastFailureAt, lastFailure, queued,
                dropped, sent, failures);
    }

    @Override
    public String toString() {
        return "HushFeed[queued=" + queued + ", failures=" + failures + "]";
    }

    // ------------------------------------------------------------------ the queue

    private boolean wanted(String type) {
        HushConfig now = config.get();
        return now.hasToken() && now.wants(type);
    }

    /** Queues an event unless its type is switched off or there is no token to send it with. Feed thread only. */
    void enqueue(String type, DataObject event) {
        if (!wanted(type)) {
            return;
        }
        if (queue.size() >= QUEUE_LIMIT) {
            queue.poll();
            dropped++;
        }
        queue.add(new Queued(type, event, clock.getAsLong()));
        queued = queue.size();
    }

    /**
     * One round of sending: whatever is due goes, in batches, until nothing is due or a send fails. Never throws.
     *
     * @param force send even what is younger than the flush time and even inside a backoff — for stopping
     */
    void pump(boolean force) {
        try {
            long now = clock.getAsLong();
            expire(now);
            if (queue.isEmpty()) {
                return;
            }
            HushConfig cfg = config.get();
            if (!cfg.hasToken()) {
                queue.clear();
                queued = 0;
                return;
            }
            boolean first = true;
            while (!queue.isEmpty()) {
                now = clock.getAsLong();
                boolean due = queue.size() >= MAX_BATCH || now - queue.peek().at() >= flushMillis;
                if (!(first && force) && (now < retryAt || !(due || force))) {
                    return;
                }
                first = false;
                if (!sendBatch(cfg, now)) {
                    return;
                }
            }
        } catch (RuntimeException | LinkageError broken) {
            noteFailure(clock.getAsLong(), broken.getClass().getSimpleName(), false, -1);
        } finally {
            queued = queue.size();
        }
    }

    private void expire(long now) {
        while (!queue.isEmpty() && rule.expired(queue.peek().at(), now)) {
            queue.poll();
            dropped++;
        }
        queued = queue.size();
    }

    /** @return whether it went through and the next batch may follow */
    private boolean sendBatch(HushConfig cfg, long now) {
        int size = Math.min(MAX_BATCH, queue.size());
        String body;
        List<Queued> batch;
        while (true) {
            batch = new ArrayList<>(queue.stream().limit(size).toList());
            body = HushEvents.body(cfg.serverName(), batch.stream().map(Queued::event).toList());
            if (size == 1 || body.getBytes(StandardCharsets.UTF_8).length <= MAX_BODY_BYTES) {
                break;
            }
            size = Math.max(1, size / 2);
        }
        Response response;
        try {
            response = transport.post(cfg.url(), cfg.token(), body);
        } catch (RuntimeException broken) {
            response = new Response(HushTransport.IO_FAILURE, -1, broken.getClass().getSimpleName());
        }
        now = clock.getAsLong();
        switch (rule.actionFor(response.status())) {
            case DONE -> {
                for (int i = 0; i < batch.size(); i++) {
                    queue.poll();
                }
                sent += batch.size();
                lastSuccessAt = now;
                if (failures > 0 && warned) {
                    info.accept("Hush feed is reachable again.");
                }
                failures = 0;
                warned = false;
                retryAt = 0;
                return true;
            }
            case RETRY -> {
                noteFailure(now, describe(response), true, response.retryAfterSeconds());
                return false;
            }
            default -> {
                for (int i = 0; i < batch.size(); i++) {
                    queue.poll();
                }
                dropped += batch.size();
                noteFailure(now, describe(response), false, -1);
                return false;
            }
        }
    }

    private void noteFailure(long now, String what, boolean retry, long retryAfter) {
        failures++;
        lastFailureAt = now;
        lastFailure = what;
        retryAt = now + (retry ? rule.delayMillisAfter(failures, retryAfter) : rule.delayMillisAfterDrop(failures));
        if (!warned) {
            warned = true;
            warn.accept("Hush feed: " + what + (retry ? " — trying again" : " — those events are dropped") + hint(what)
                    + ". Further failures are not logged until it works again.");
        }
    }

    private static String describe(Response response) {
        if (response.status() == HushTransport.IO_FAILURE) {
            return response.failure() == null ? "no answer" : response.failure();
        }
        if (response.status() == HushTransport.BAD_URL) {
            return response.failure() == null ? "bad url" : response.failure();
        }
        return "HTTP " + response.status();
    }

    private static String hint(String what) {
        return switch (what) {
            case "HTTP 401" -> " (token unknown or revoked: /discordlink hushtoken TOKEN)";
            case "HTTP 404" -> " (the Minecraft module is switched off on that Discord server)";
            case "HTTP 400" -> " (Hush did not understand the body)";
            default -> "";
        };
    }
}
