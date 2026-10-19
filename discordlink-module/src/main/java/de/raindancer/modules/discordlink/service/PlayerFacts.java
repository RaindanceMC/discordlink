package de.raindancer.modules.discordlink.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the public player information comes from — RainsCore on a live server, a fake in a test. Some calls are
 * database or LuckPerms I/O: never on the server thread.
 */
public interface PlayerFacts {

    record Online(UUID id, String name, String server) {
    }

    record Seen(String name, String server, long at, boolean online) {
    }

    record Server(String id, String name, int players) {
    }

    /** Visible players only: a vanished admin is not online to Discord. */
    Optional<Online> online(String name);

    Optional<UUID> idOf(String name);

    Optional<Seen> lastSeen(UUID player);

    Optional<Long> firstSeen(UUID player);

    long playtimeMinutes(UUID player);

    Optional<String> rank(UUID player);

    boolean linked(UUID player);

    /** The servers answering the network's heartbeat right now. */
    List<Server> servers();

    int onlineCount();

    /** Server id → what to call it. */
    Map<String, String> serverNames();
}
