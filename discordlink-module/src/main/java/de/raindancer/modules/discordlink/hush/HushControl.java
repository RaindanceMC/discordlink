package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.data.store.Secrets;
import de.raindancer.modules.discordlink.DiscordLinkSettings;

import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Settings plus the secret token as one {@link HushConfig}, and the console's way to set or clear the token. */
public final class HushControl {

    public static final String TOKEN_SECRET = "hush-token";
    /** The token is shared by the network's servers through the secrets table; a change elsewhere is picked up within this. */
    static final long REREAD_MILLIS = 30_000;

    private final Secrets secrets;
    private final Supplier<DiscordLinkSettings> settings;
    private final Supplier<String> networkId;
    private final LongSupplier clock;
    private volatile HushFeed feed;
    private volatile HushToken token = new HushToken("");
    private volatile long readAt = Long.MIN_VALUE;

    public HushControl(Secrets secrets, Supplier<DiscordLinkSettings> settings, Supplier<String> networkId, LongSupplier clock) {
        this.secrets = secrets;
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
                now.hushAnticheat(), now.hushPunishments(), now.hushReports(), now.hushServer());
    }

    private HushToken token() {
        long now = clock.getAsLong();
        if (now - readAt >= REREAD_MILLIS) {
            readAt = now;
            token = new HushToken(secrets.text(TOKEN_SECRET).orElse(""));
        }
        return token;
    }

    /** @return whether it was saved */
    public boolean setToken(String value) {
        HushToken fresh = new HushToken(value);
        boolean done = fresh.isBlank() ? secrets.remove(TOKEN_SECRET) : secrets.putText(TOKEN_SECRET, fresh.value());
        if (done) {
            token = fresh;
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
