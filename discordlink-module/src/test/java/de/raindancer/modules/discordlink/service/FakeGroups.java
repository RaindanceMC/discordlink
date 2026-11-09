package de.raindancer.modules.discordlink.service;

import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.core.platform.permission.GroupSeed;
import de.raindancer.core.platform.permission.PermissionGroups;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** LuckPerms in memory; only the groups in {@code existing} can be joined. */
public final class FakeGroups implements PermissionGroups {

    public final Map<UUID, Set<String>> direct = new HashMap<>();
    public final Set<String> existing = new HashSet<>();

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    public boolean seed(GroupSeed seed) {
        return false;
    }

    @Override
    public boolean exists(String group) {
        return existing.contains(group);
    }

    @Override
    public boolean join(UUID who, String group) {
        return exists(group) && direct.computeIfAbsent(who, id -> new HashSet<>()).add(group);
    }

    @Override
    public boolean leave(UUID who, String group) {
        return direct.getOrDefault(who, new HashSet<>()).remove(group);
    }

    @Override
    public Set<String> directGroupsOf(UUID who) {
        return Set.copyOf(direct.getOrDefault(who, Set.of()));
    }

    @Override
    public Set<String> groupsOf(UUID who) {
        return directGroupsOf(who);
    }

    @Override
    public Set<UUID> membersOf(String group) {
        return Set.of();
    }

    @Override
    public Set<String> dropDirect(UUID who, Collection<String> nodes) {
        return Set.of();
    }

    @Override
    public NetworkBus.Subscription onChange(Consumer<UUID> watcher) {
        return () -> { };
    }
}
