package de.raindancer.modules.discordlink.hush;

import de.raindancer.modules.discordlink.DiscordLinkSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class HushControlTest {

    private final Map<String, String> stored = new HashMap<>();
    private final AtomicLong now = new AtomicLong(1_794_183_596_902L);

    private HushControl control() {
        return new HushControl(name -> Optional.ofNullable(stored.get(name)), (name, value) -> {
            if (value == null) stored.remove(name); else stored.put(name, value);
            return true;
        }, () -> DiscordLinkSettings.DEFAULTS, () -> "smp", now::get);
    }

    @Test
    @DisplayName("after a restart the saved token is read again (it was never read: readAt overflowed)")
    void tokenSurvivesRestart() {
        control().setToken("hmc_abcdefghijklmnopqrstuvwxyz0123456789");
        HushControl afterRestart = control();
        assertThat(afterRestart.config().hasToken()).isTrue();
    }

    @Test
    @DisplayName("a token set on another server is picked up within the reread time")
    void picksUpChanges() {
        HushControl here = control();
        assertThat(here.config().hasToken()).isFalse();
        stored.put(HushControl.TOKEN_SECRET, "hmc_abcdefghijklmnopqrstuvwxyz0123456789");
        assertThat(here.config().hasToken()).isFalse();
        now.addAndGet(HushControl.REREAD_MILLIS);
        assertThat(here.config().hasToken()).isTrue();
    }
}
