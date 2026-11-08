package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.hush.HushTransport;

/**
 * What to do with a batch after Hush answered, and how long to wait. Rate limits (429) and server trouble (5xx, no
 * answer at all) are worth another try; a bad body, an unknown or revoked token, a module switched off (400, 401,
 * 404) will not get better by asking again, so the batch is dropped.
 */
public final class HushRetryRule implements IDiscordLinkRule {

    public enum Action { DONE, RETRY, DROP }

    static final long FIRST_MILLIS = 2_000;
    static final long LONGEST_MILLIS = 60_000;
    /** Events older than this are not worth sending any more. */
    public static final long GIVE_UP_MILLIS = 5 * 60_000;

    @Override
    public String describe() {
        return "what happens to a batch of events after Hush's answer";
    }

    public Action actionFor(int status) {
        if (status >= 200 && status < 300) {
            return Action.DONE;
        }
        if (status == HushTransport.IO_FAILURE || status == 429 || status >= 500) {
            return Action.RETRY;
        }
        return Action.DROP;
    }

    /**
     * Wait before the next send after {@code failures} in a row (1 = the first): doubling from two seconds to a
     * minute, never less than what Hush asked for with Retry-After (at most the give-up time). Pure.
     */
    public long delayMillisAfter(int failures, long retryAfterSeconds) {
        long backoff = failures <= 0 ? 0 : Math.min(LONGEST_MILLIS, FIRST_MILLIS << Math.min(failures - 1, 20));
        long asked = retryAfterSeconds <= 0 ? 0 : Math.min(GIVE_UP_MILLIS, retryAfterSeconds * 1000);
        return Math.max(backoff, asked);
    }

    /** Wait after a dropped batch: asking again with the same token or body would only be refused again. */
    public long delayMillisAfterDrop(int failures) {
        return Math.min(GIVE_UP_MILLIS, 30_000L << Math.min(Math.max(failures - 1, 0), 20));
    }

    public boolean expired(long enqueuedAt, long now) {
        return now - enqueuedAt >= GIVE_UP_MILLIS;
    }
}
