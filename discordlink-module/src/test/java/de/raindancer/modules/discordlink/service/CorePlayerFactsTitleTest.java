package de.raindancer.modules.discordlink.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorePlayerFactsTitleTest {

    @Test
    void groupsBecomeTitles() {
        assertThat(CorePlayerFacts.title("owner")).isEqualTo("Owner");
        assertThat(CorePlayerFacts.title("mc_admin")).isEqualTo("Mc Admin");
        assertThat(CorePlayerFacts.title("tester-team")).isEqualTo("Tester Team");
        assertThat(CorePlayerFacts.title("_")).isEqualTo("_");
    }
}
