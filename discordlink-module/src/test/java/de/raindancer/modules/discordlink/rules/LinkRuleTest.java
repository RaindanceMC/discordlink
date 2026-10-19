package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.Link;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LinkRuleTest {

    private final LinkRule rule = new LinkRule();
    private final Link mine = new Link(UUID.randomUUID(), "Alex", "111", 1);

    @Test
    @DisplayName("one account per person, unless a whitelister role says more")
    void oneUnlessAllowed() {
        assertThat(rule.judge("111", Optional.empty(), List.of(), false)).isEqualTo(LinkRule.Verdict.OK);
        assertThat(rule.judge("111", Optional.empty(), List.of(mine), false)).isEqualTo(LinkRule.Verdict.ONE_IS_ENOUGH);
        assertThat(rule.judge("111", Optional.empty(), List.of(mine), true)).isEqualTo(LinkRule.Verdict.OK);
    }

    @Test
    @DisplayName("a player can't be taken from somebody else, and linking your own again is no change")
    void owners() {
        assertThat(rule.judge("111", Optional.of(mine), List.of(mine), false)).isEqualTo(LinkRule.Verdict.ALREADY_YOURS);
        assertThat(rule.judge("222", Optional.of(mine), List.of(), true)).isEqualTo(LinkRule.Verdict.SOMEONE_ELSES);
    }
}
