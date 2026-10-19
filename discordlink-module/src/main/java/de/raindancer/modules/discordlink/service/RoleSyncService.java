package de.raindancer.modules.discordlink.service;

import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.rules.RoleSyncRule;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Discord roles → LuckPerms groups, one way. LuckPerms calls block, so this runs on a Discord or async thread.
 * Works for offline players too: RainsCore's group API loads them.
 */
public final class RoleSyncService implements IDiscordLinkService {

    private final Supplier<PermissionGroups> groups;
    private final RoleConfigStore roles;
    private final LinkStore links;
    private final RoleSyncRule rule;
    private final LogChannel log;

    public RoleSyncService(Supplier<PermissionGroups> groups, RoleConfigStore roles, LinkStore links, RoleSyncRule rule,
                           LogChannel log) {
        this.groups = groups;
        this.roles = roles;
        this.links = links;
        this.rule = rule;
        this.log = log;
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        // How often to sweep is the bot's timer; this only applies what it is given.
    }

    /** Every account the Discord user has linked, brought in line with these roles (empty = left the server). */
    public void sync(String discord, Collection<String> memberRoles) {
        for (Link link : links.linksOf(discord)) {
            apply(link, memberRoles);
        }
    }

    /** The whole network at once, for the regular sweep: discord id → their roles now (absent = not a member). */
    public void syncAll(Map<String, List<String>> rolesByDiscord) {
        for (Link link : links.all()) {
            apply(link, rolesByDiscord.getOrDefault(link.discord(), List.of()));
        }
    }

    /** Takes away every group this module gave the one account: it is being unlinked. */
    public void clear(Link link) {
        apply(link, List.of());
    }

    private void apply(Link link, Collection<String> memberRoles) {
        PermissionGroups permissions = groups.get();
        if (roles.current().groups().isEmpty() || permissions == PermissionGroups.NONE) {
            return;
        }
        RoleSyncRule.Change change = rule.judge(roles.current(), memberRoles, permissions.directGroupsOf(link.player()));
        change.join().forEach(group -> {
            if (!permissions.join(link.player(), group)) {
                log.warn("Could not put {} into LuckPerms group '{}' (does it exist?).", link.name(), group);
            }
        });
        change.leave().forEach(group -> permissions.leave(link.player(), group));
        if (!change.nothing()) {
            log.info("{}: joined {}, left {} (Discord roles).", link.name(), change.join(), change.leave());
        }
    }
}
