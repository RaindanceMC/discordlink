package de.raindancer.modules.discordlink.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the Discord screens say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    @Test
    @DisplayName("a linked player sees since when; an unlinked one is told how, and no Discord account is ever named")
    void you() {
        String linked = rule.you(true, Duration.ofDays(3).plusHours(2));
        assertThat(linked).startsWith("<gold><bold>Your Discord</bold></gold>")
                .contains("<green>✔ <white>Linked").contains("<gray>since <white>3d 2h ago");
        String not = rule.you(false, null);
        assertThat(not).contains("<gray>✘ Not linked").contains("<white>/link");
    }

    @Test
    @DisplayName("the network's count: players linked, and how many of those are here now; singular reads right")
    void linked() {
        assertThat(rule.linked(120, 7)).startsWith("<gold><bold>Discord link</bold></gold>")
                .contains("<gray>players linked <white>120").contains("<gray>online here <white>7");
        assertThat(rule.linked(0, 0)).contains("<gray>players linked <white>0");
    }
}
