package de.raindancer.modules.discordlink.hush;

import de.raindancer.modules.discordlink.hush.HushTransport.Response;
import de.raindancer.modules.discordlink.rules.HushRetryRule;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class HushFeedTest {

    private static final String SECRET = "s3cr3t-token-value";

    /** Answers from a script, remembers what it was sent. */
    private static final class FakeTransport implements HushTransport {
        final Deque<Response> script = new ArrayDeque<>();
        final List<String> bodies = new ArrayList<>();
        final List<HushToken> tokens = new ArrayList<>();

        @Override
        public Response post(String url, HushToken token, String json) {
            bodies.add(json);
            tokens.add(token);
            return script.isEmpty() ? Response.status(200, -1) : script.poll();
        }

        int events(int call) {
            return DataObject.fromJson(bodies.get(call)).getArray("events").length();
        }
    }

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final FakeTransport transport = new FakeTransport();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> infos = new ArrayList<>();
    private HushConfig config;
    private HushFeed feed;

    @BeforeEach
    void setUp() {
        config = new HushConfig("https://hush.example/api/minecraft/events", "beta-smp", new HushToken(SECRET),
                true, true, true, true);
        feed = new HushFeed(transport, () -> config, clock::get, warnings::add, infos::add, new HushRetryRule(), 2_000);
    }

    private static DataObject event(int n) {
        return DataObject.empty().put("type", "server").put("state", "start").put("detail", "n" + n);
    }

    private void add(int count) {
        for (int i = 0; i < count; i++) {
            feed.enqueue("server", event(i));
        }
    }

    @Test
    @DisplayName("nothing is sent before two seconds have passed, then everything waiting goes in one request")
    void flushesAfterTwoSeconds() {
        add(3);
        feed.pump(false);
        assertThat(transport.bodies).isEmpty();

        clock.addAndGet(1_999);
        feed.pump(false);
        assertThat(transport.bodies).isEmpty();

        clock.addAndGet(1);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(1);
        assertThat(transport.events(0)).isEqualTo(3);
        assertThat(feed.status().queued()).isZero();
        assertThat(DataObject.fromJson(transport.bodies.get(0)).getString("server")).isEqualTo("beta-smp");
    }

    @Test
    @DisplayName("fifty events go at once, in batches of at most fifty")
    void flushesAtFifty() {
        add(49);
        feed.pump(false);
        assertThat(transport.bodies).isEmpty();
        add(1);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(1);
        assertThat(transport.events(0)).isEqualTo(50);

        add(120);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(3);
        assertThat(transport.events(1)).isEqualTo(50);
        assertThat(transport.events(2)).isEqualTo(50);
        assertThat(feed.status().queued()).isEqualTo(20);
    }

    @Test
    @DisplayName("a 503 keeps the batch and waits with a growing backoff; the same events go again, then success clears the streak")
    void retriesServerTrouble() {
        transport.script.add(Response.status(503, -1));
        transport.script.add(Response.status(503, -1));
        add(2);
        clock.addAndGet(2_000);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(1);
        assertThat(feed.status().queued()).isEqualTo(2);

        clock.addAndGet(1_999);
        feed.pump(false);
        assertThat(transport.bodies).as("still backing off (2 s)").hasSize(1);
        clock.addAndGet(1);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(2);

        clock.addAndGet(3_999);
        feed.pump(false);
        assertThat(transport.bodies).as("the second failure waits 4 s").hasSize(2);
        clock.addAndGet(1);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(3);
        assertThat(transport.events(2)).isEqualTo(2);
        assertThat(feed.status().queued()).isZero();
        assertThat(feed.status().failureStreak()).isZero();
        assertThat(feed.status().lastSuccessAt()).isNotNull();
    }

    @Test
    @DisplayName("429 with Retry-After waits at least that long")
    void honoursRetryAfter() {
        transport.script.add(Response.status(429, 30));
        add(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        clock.addAndGet(29_999);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(1);
        clock.addAndGet(1);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(2);
    }

    @Test
    @DisplayName("400, 401 and 404 drop the batch and are not retried")
    void dropsWhatWillNotImprove() {
        for (int status : new int[]{400, 401, 404}) {
            transport.bodies.clear();
            transport.script.add(Response.status(status, -1));
            add(2);
            clock.addAndGet(2_000);
            feed.pump(false);
            assertThat(transport.bodies).as("status " + status).hasSize(1);
            assertThat(feed.status().queued()).as("status " + status).isZero();
            clock.addAndGet(10 * 60_000);
            feed.pump(false);
            assertThat(transport.bodies).as("nothing to resend for " + status).hasSize(1);
        }
        assertThat(feed.status().dropped()).isEqualTo(6);
    }

    @Test
    @DisplayName("after a dropped batch new events wait out the backoff instead of hitting a 401 every two seconds")
    void dropsAreNotHammered() {
        transport.script.add(Response.status(401, -1));
        add(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        add(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        assertThat(transport.bodies).hasSize(1);
    }

    @Test
    @DisplayName("events still unsent after five minutes are dropped, not sent")
    void givesUpAfterFiveMinutes() {
        transport.script.add(Response.status(503, -1));
        add(2);
        clock.addAndGet(2_000);
        feed.pump(false);
        clock.addAndGet(5 * 60_000);
        feed.enqueue("server", event(9));
        feed.pump(false);
        assertThat(feed.status().dropped()).isEqualTo(2);
        assertThat(feed.status().queued()).isEqualTo(1);
    }

    @Test
    @DisplayName("an answer that never came counts as trouble and is retried")
    void ioFailureIsRetried() {
        transport.script.add(new Response(HushTransport.IO_FAILURE, -1, "ConnectException"));
        add(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        assertThat(feed.status().queued()).isEqualTo(1);
        assertThat(feed.status().lastFailure()).contains("ConnectException");
    }

    @Test
    @DisplayName("one warning per failure streak, one note when it is over; the token is in neither")
    void quietLogs() {
        for (int i = 0; i < 4; i++) {
            transport.script.add(Response.status(503, -1));
        }
        add(1);
        for (int i = 0; i < 4; i++) {
            clock.addAndGet(20_000);
            feed.pump(false);
        }
        assertThat(warnings).hasSize(1);
        assertThat(infos).isEmpty();

        clock.addAndGet(20_000);
        feed.pump(false);
        assertThat(infos).hasSize(1);

        transport.script.add(Response.status(401, -1));
        add(1);
        clock.addAndGet(60_000);
        feed.pump(false);
        assertThat(warnings).as("a new streak warns again").hasSize(2);
        assertThat(String.join("\n", warnings) + String.join("\n", infos)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("the token goes to the transport only, never into toString of the feed, its config or its status")
    void tokenNeverPrints() {
        add(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        assertThat(transport.tokens.get(0).value()).isEqualTo(SECRET);
        assertThat(feed.toString()).doesNotContain(SECRET);
        assertThat(config.toString()).doesNotContain(SECRET);
        assertThat(config.token().toString()).doesNotContain(SECRET);
        assertThat(feed.status().toString()).doesNotContain(SECRET);
        assertThat(String.join("\n", feed.status().lines())).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("a switched-off type, and everything without a token, is never queued")
    void togglesAndMissingToken() {
        config = new HushConfig(config.url(), "beta-smp", config.token(), false, true, true, true);
        feed.enqueue("anticheat", event(1));
        assertThat(feed.status().queued()).isZero();
        feed.enqueue("report", event(2));
        assertThat(feed.status().queued()).isEqualTo(1);

        config = new HushConfig(config.url(), "beta-smp", new HushToken(""), true, true, true, true);
        feed.enqueue("report", event(3));
        assertThat(feed.status().queued()).isEqualTo(1);
        clock.addAndGet(2_000);
        feed.pump(false);
        assertThat(transport.bodies).as("no token, nothing leaves the server").isEmpty();
    }

    @Test
    @DisplayName("the queue is bounded: the oldest give way, and that is counted")
    void boundedQueue() {
        add(HushFeed.QUEUE_LIMIT + 10);
        assertThat(feed.status().queued()).isEqualTo(HushFeed.QUEUE_LIMIT);
        assertThat(feed.status().dropped()).isEqualTo(10);
    }

    @Test
    @DisplayName("stopping sends what is waiting even before the two seconds are up")
    void forcedFlush() {
        add(2);
        feed.pump(true);
        assertThat(transport.bodies).hasSize(1);
    }

    @Test
    @DisplayName("an event the transport chokes on never escapes pump")
    void pumpNeverThrows() {
        HushFeed broken = new HushFeed((url, token, json) -> {
            throw new IllegalStateException("boom");
        }, () -> config, clock::get, warnings::add, infos::add, new HushRetryRule(), 2_000);
        broken.enqueue("server", event(1));
        clock.addAndGet(2_000);
        broken.pump(false);
        assertThat(broken.status().failureStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("a body over 64 KiB is split into smaller requests")
    void bodyStaysUnderLimit() {
        for (int i = 0; i < 50; i++) {
            feed.enqueue("server", DataObject.empty().put("type", "server").put("state", "start").put("detail", "y".repeat(2_000)));
        }
        feed.pump(true);
        assertThat(transport.bodies).hasSizeGreaterThan(1);
        assertThat(transport.bodies).allSatisfy(body -> assertThat(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(64 * 1024));
    }
}
