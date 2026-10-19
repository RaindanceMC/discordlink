package de.raindancer.modules.discordlink.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the Discord admins picked: which roles may whitelist more than their own account, which Discord role
 * gives which LuckPerms group, and where new whitelistings are announced. Role ids are kept as strings: snowflakes are too long for YAML's numbers to keep.
 */
public record RoleConfig(Set<String> whitelisters, Map<String, String> groups, String announceChannel) {

    public static final RoleConfig EMPTY = new RoleConfig(Set.of(), Map.of(), null);

    /** Without an announcement channel: welcomes go to wherever /link was used. */
    public RoleConfig(Set<String> whitelisters, Map<String, String> groups) {
        this(whitelisters, groups, null);
    }

    public RoleConfig {
        whitelisters = Set.copyOf(whitelisters);
        groups = Map.copyOf(groups);
    }

    public boolean mayWhitelistOthers(Collection<String> memberRoles) {
        return memberRoles.stream().anyMatch(whitelisters::contains);
    }

    /** Every group this module hands out, and so the only groups it ever takes away. */
    public Set<String> managedGroups() {
        return new TreeSet<>(groups.values());
    }

    public RoleConfig withWhitelisters(Collection<String> roles) {
        return new RoleConfig(Set.copyOf(roles), groups, announceChannel);
    }

    /** @param channel null = back to "wherever /link was used" */
    public RoleConfig announcingIn(String channel) {
        return new RoleConfig(whitelisters, groups, channel);
    }

    public RoleConfig mapping(String role, String group) {
        Map<String, String> next = new LinkedHashMap<>(groups);
        if (group == null) {
            next.remove(role);
        } else {
            next.put(role, group);
        }
        return new RoleConfig(whitelisters, next, announceChannel);
    }
}
