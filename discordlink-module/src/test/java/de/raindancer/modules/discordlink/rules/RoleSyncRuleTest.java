package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.RoleConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSyncRuleTest {

    private final RoleSyncRule rule = new RoleSyncRule();
    private final RoleConfig config = new RoleConfig(Set.of(), Map.of("r-sub", "subscriber", "r-vip", "vip", "r-mod", "mod"));

    @Test
    @DisplayName("roles give their groups; a group nobody maps to is never touched")
    void givesAndLeavesHandGrantedAlone() {
        var change = rule.judge(config, List.of("r-sub", "r-other"), List.of("default", "builder"));
        assertThat(change.join()).containsExactly("subscriber");
        assertThat(change.leave()).isEmpty();
    }

    @Test
    @DisplayName("losing a role takes away only its group, and leaving the server takes all mapped ones")
    void takesAway() {
        var change = rule.judge(config, List.of("r-sub"), List.of("subscriber", "vip", "builder"));
        assertThat(change.join()).isEmpty();
        assertThat(change.leave()).containsExactly("vip");

        var gone = rule.judge(config, List.of(), List.of("subscriber", "vip", "builder"));
        assertThat(gone.leave()).containsExactlyInAnyOrder("subscriber", "vip");
    }

    @Test
    @DisplayName("two roles giving the same group keep it while either is there")
    void sharedGroup() {
        RoleConfig shared = new RoleConfig(Set.of(), Map.of("a", "vip", "b", "vip"));
        assertThat(rule.judge(shared, List.of("b"), List.of("vip")).nothing()).isTrue();
    }
}
