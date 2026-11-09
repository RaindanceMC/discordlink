package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.data.store.Secrets;
import de.raindancer.modules.discordlink.DiscordLinkSettings;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Settings plus the secret token as one {@link HushConfig}, and the console's way to set or clear the token. */
public final class HushControl {

    public static final String TOKEN_SECRET = "hush-token";
    /** The token is shared by the network's servers through the secrets table; a change elsewhere is picked up within this. */
    static final long REREAD_MILLIS = 30_000;

    private final Function<String, Optional<String>> read;
    private final BiPredicate<String, String> write;
    private final Supplier<DiscordLinkSettings> settings;
    private final Supplier<String> networkId;
    private final LongSupplier clock;
    private volatile HushFeed feed;
    private volatile HushToken token = new HushToken("");
    // not Long.MIN_VALUE: now - MIN_VALUE overflows to a negative number and the saved token was never read
    private volatile long readAt;
    private volatile boolean everRead;

    public HushControl(Secrets secrets, Supplier<DiscordLinkSettings> settings, Supplier<String> networkId, LongSupplier clock) {
        this(secrets::text, (name, value) -> value == null ? secrets.remove(name) : secrets.putText(name, value), settings, networkId, clock);
    }

    /** {@code write} gets a null value to remove the secret. */
    HushControl(Function<String, Optional<String>> read, BiPredicate<String, String> write, Supplier<DiscordLinkSettings> settings,
                Supplier<String> networkId, LongSupplier clock) {
        this.read = read;
        this.write = write;
        this.settings = settings;
        this.networkId = networkId;
        this.clock = clock;
    }

    public void feed(HushFeed feed) {
        this.feed = feed;
    }

    /** Read before each send, so a changed setting or token applies at once. */
    public HushConfig config() {
        DiscordLinkSettings now = settings.get();
        String name = now.hushServerName() == null ? "" : now.hushServerName().strip();
        return new HushConfig(now.hushUrl(), name.isEmpty() ? networkId.get() : name, token(),
                now.hushAnticheat(), now.hushPunishments(), now.hushReports(), now.hushServer(),
                now.hushAdminMode(), now.hushActions(), now.hushPollSeconds());
    }

    private HushToken token() {
        long now = clock.getAsLong();
        if (!everRead || now - readAt >= REREAD_MILLIS) {
            everRead = true;
            readAt = now;
            token = new HushToken(read.apply(TOKEN_SECRET).orElse(""));
        }
        return token;
    }

    /** @return whether it was saved */
    public boolean setToken(String value) {
        HushToken fresh = new HushToken(value);
        boolean done = write.test(TOKEN_SECRET, fresh.isBlank() ? null : fresh.value());
        if (done) {
            token = fresh;
            everRead = true;
            readAt = clock.getAsLong();
        }
        return done;
    }

    public boolean clearToken() {
        return setToken("");
    }

    /** One line each; never the token. */
    public List<String> statusLines() {
        HushFeed live = feed;
        return live == null ? List.of("not running") : live.status().lines();
    }

    public Optional<HushFeed> feed() {
        return Optional.ofNullable(feed);
    }
}
