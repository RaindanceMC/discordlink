package de.raindancer.modules.discordlink.rules;

/**
 * How long a bot that failed to start waits before the next try. Every try is a login, and Discord resets a token
 * after 1000 logins in a day — a fixed few seconds would burn that in an hour (it did, nearly, on 19.10.2026).
 */
public final class RetryRule implements IDiscordLinkRule {

    static final long FIRST_MILLIS = 30_000;
    static final long LONGEST_MILLIS = 30 * 60_000;

    @Override
    public String describe() {
        return "how long the bot waits after a failed start";
    }

    /** @param failures failed starts in a row; 0 = try now. Pure. */
    public long delayMillisAfter(int failures) {
        if (failures <= 0) {
            return 0;
        }
        int doublings = Math.min(failures - 1, 20);
        return Math.min(LONGEST_MILLIS, FIRST_MILLIS << doublings);
    }
}
