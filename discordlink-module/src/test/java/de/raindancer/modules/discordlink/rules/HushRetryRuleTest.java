package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.rules.HushRetryRule.Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HushRetryRuleTest {

    private final HushRetryRule rule = new HushRetryRule();

    @Test
    @DisplayName("200 is done; 429, 5xx and no answer are retried; 400, 401, 404, 413 and the rest are dropped")
    void decisions() {
        assertThat(rule.actionFor(200)).isEqualTo(Action.DONE);
        for (int retried : new int[]{429, 500, 502, 503, 504, 0}) {
            assertThat(rule.actionFor(retried)).as("status " + retried).isEqualTo(Action.RETRY);
        }
        for (int dropped : new int[]{400, 401, 403, 404, 413, 302, -1}) {
            assertThat(rule.actionFor(dropped)).as("status " + dropped).isEqualTo(Action.DROP);
        }
    }

    @Test
    @DisplayName("backoff doubles from 2 s to a minute and never overflows")
    void backoff() {
        assertThat(rule.delayMillisAfter(1, -1)).isEqualTo(2_000);
        assertThat(rule.delayMillisAfter(2, -1)).isEqualTo(4_000);
        assertThat(rule.delayMillisAfter(3, -1)).isEqualTo(8_000);
        assertThat(rule.delayMillisAfter(6, -1)).isEqualTo(60_000);
        assertThat(rule.delayMillisAfter(500, -1)).isEqualTo(60_000);
    }

    @Test
    @DisplayName("Retry-After is honoured when it asks for more than the backoff, capped at the give-up time")
    void retryAfter() {
        assertThat(rule.delayMillisAfter(1, 30)).isEqualTo(30_000);
        assertThat(rule.delayMillisAfter(6, 10)).as("the backoff is longer").isEqualTo(60_000);
        assertThat(rule.delayMillisAfter(1, 86_400)).isEqualTo(HushRetryRule.GIVE_UP_MILLIS);
    }

    @Test
    @DisplayName("after a dropped batch the next send waits much longer: 30 s doubling to five minutes")
    void afterDrop() {
        assertThat(rule.delayMillisAfterDrop(1)).isEqualTo(30_000);
        assertThat(rule.delayMillisAfterDrop(2)).isEqualTo(60_000);
        assertThat(rule.delayMillisAfterDrop(4)).isEqualTo(240_000);
        assertThat(rule.delayMillisAfterDrop(5)).isEqualTo(HushRetryRule.GIVE_UP_MILLIS);
        assertThat(rule.delayMillisAfterDrop(500)).isEqualTo(HushRetryRule.GIVE_UP_MILLIS);
    }

    @Test
    @DisplayName("an event is given up on after five minutes")
    void expiry() {
        assertThat(rule.expired(1_000, 1_000 + 299_999)).isFalse();
        assertThat(rule.expired(1_000, 1_000 + 300_000)).isTrue();
    }
}
