package de.raindancer.modules.discordlink.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CodeRuleTest {

    private final CodeRule rule = new CodeRule();

    @Test
    @DisplayName("codes are two blocks of five from an alphabet without look-alikes, and read back as themselves")
    void shape() {
        Random random = new Random(118);
        for (int i = 0; i < 200; i++) {
            String code = rule.newCode(random);
            assertThat(code).matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}").doesNotContain("O", "I", "L", "0", "1");
            assertThat(rule.normalise(code)).contains(code);
        }
    }

    @Test
    @DisplayName("what people actually type is understood: lower case, spaces, no dash")
    void forgiving() {
        assertThat(rule.normalise(" abcde 23456 ")).contains("ABCDE-23456");
        assertThat(rule.normalise("abcde23456")).contains("ABCDE-23456");
        assertThat(rule.normalise("ABCDE-2345")).isEmpty();
        assertThat(rule.normalise("ABCDE-23450")).as("0 is not in the alphabet").isEmpty();
        assertThat(rule.normalise(null)).isEmpty();
        assertThat(rule.normalise("'; DROP TABLE--")).isEmpty();
    }
}
