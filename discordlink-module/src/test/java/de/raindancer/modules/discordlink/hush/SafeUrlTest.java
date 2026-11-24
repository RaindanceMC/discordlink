package de.raindancer.modules.discordlink.hush;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeUrlTest {

    @Test
    void plainHttpsAddressesPass() {
        assertThat(SafeUrl.https("https://hyadesmc.net/giveaway/")).contains("https://hyadesmc.net/giveaway/");
        assertThat(SafeUrl.https(" https://hushbot.net/go/hyades/referral/open-the-end/alex ")).contains("https://hushbot.net/go/hyades/referral/open-the-end/alex");
        assertThat(SafeUrl.https("https://discord.gg/abc123?x=1&y=%20#top")).isPresent();
    }

    @Test
    void anythingThatCouldBreakOutOfAClickTagDoesNot() {
        assertThat(SafeUrl.https("http://hyadesmc.net")).isEmpty();
        assertThat(SafeUrl.https("javascript:alert(1)")).isEmpty();
        assertThat(SafeUrl.https("https://x.net/'><click:run_command:'/op me'>")).isEmpty();
        assertThat(SafeUrl.https("https://x.net/a b")).isEmpty();
        assertThat(SafeUrl.https("https://x.net/\"")).isEmpty();
        assertThat(SafeUrl.https("")).isEmpty();
        assertThat(SafeUrl.https(null)).isEmpty();
    }
}
