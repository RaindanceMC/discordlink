package de.raindancer.modules.discordlink.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryRuleTest {

    private final RetryRule rule = new RetryRule();

    @Test
    @DisplayName("a failing bot waits longer each time, up to half an hour — never a login every few seconds")
    void backsOff() {
        assertThat(rule.delayMillisAfter(0)).isZero();
        assertThat(rule.delayMillisAfter(1)).isEqualTo(30_000);
        assertThat(rule.delayMillisAfter(2)).isEqualTo(60_000);
        assertThat(rule.delayMillisAfter(3)).isEqualTo(120_000);
        assertThat(rule.delayMillisAfter(7)).isEqualTo(30 * 60_000);
        assertThat(rule.delayMillisAfter(500)).as("no overflow").isEqualTo(30 * 60_000);
    }

    @Test
    @DisplayName("at most about 70 logins a day while it keeps failing, far under Discord's 1000")
    void staysUnderDiscordsLimit() {
        long day = 24 * 60 * 60_000L;
        long spent = 0;
        int attempts = 0;
        while (spent < day) {
            attempts++;
            spent += rule.delayMillisAfter(attempts);
        }
        assertThat(attempts).isLessThan(100);
    }
}
