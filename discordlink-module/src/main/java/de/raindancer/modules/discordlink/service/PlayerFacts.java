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

    /** One server for the network status: up or down, how it runs. {@code downSince} 0 while up; numbers NaN when unknown. */
    record ServerState(String id, String name, boolean online, int players, double tps, double mspt, long startedAt,
                       long downSince) {
    }

    record Achievements(int earned, int points) {
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

    /** Every server heard of lately, up or down. Without anything better: the ones answering, numbers unknown. */
    default List<ServerState> network() {
        return servers().stream().map(s -> new ServerState(s.id(), s.name(), true, s.players(), Double.NaN, Double.NaN, 0, 0)).toList();
    }

    /** Their money, written as the economy writes it; empty where no economy runs. */
    default Optional<String> balance(UUID player) {
        return Optional.empty();
    }

    /** Their guild, "[TAG] Name"; empty for none or where guilds don't run. */
    default Optional<String> guild(UUID player) {
        return Optional.empty();
    }

    /** The job (role) they hold. */
    default Optional<String> job(UUID player) {
        return Optional.empty();
    }

    default Optional<Achievements> achievements(UUID player) {
        return Optional.empty();
    }
}
