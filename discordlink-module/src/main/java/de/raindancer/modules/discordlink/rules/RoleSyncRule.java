package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.RoleConfig;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which LuckPerms groups a player should be in, going by their Discord roles. Only groups that some role maps to
 * are ever touched: a group given by hand in game stays, whatever Discord says.
 */
public final class RoleSyncRule implements IDiscordLinkRule {

    @Override
    public String describe() {
        return "which mapped LuckPerms groups a player joins or leaves for their Discord roles";
    }


    public record Change(Set<String> join, Set<String> leave) {
        public boolean nothing() {
            return join.isEmpty() && leave.isEmpty();
        }
    }

    /**
     * @param memberRoles the Discord roles now; empty for somebody who left the server
     * @param current     the player's direct groups now
     */
    public Change judge(RoleConfig config, Collection<String> memberRoles, Collection<String> current) {
        Set<String> wanted = new TreeSet<>();
        memberRoles.forEach(role -> {
            String group = config.groups().get(role);
            if (group != null) {
                wanted.add(group);
            }
        });
        Set<String> join = new TreeSet<>(wanted);
        join.removeAll(current);
        Set<String> leave = new TreeSet<>(config.managedGroups());
        leave.removeAll(wanted);
        leave.retainAll(current);
        return new Change(join, leave);
    }
}
