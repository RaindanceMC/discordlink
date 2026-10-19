package de.raindancer.modules.discordlink.service;

import de.raindancer.core.RainsCore;
import de.raindancer.core.platform.network.NetworkPlayers;
import de.raindancer.core.platform.network.NetworkServers;
import de.raindancer.modules.discordlink.store.LinkStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** {@link PlayerFacts} from RainsCore: presence and servers from memory, history and ranks from the database. */
public final class CorePlayerFacts implements PlayerFacts {

    private final RainsCore core;
    private final LinkStore links;

    public CorePlayerFacts(RainsCore core, LinkStore links) {
        this.core = core;
        this.links = links;
    }

    @Override
    public Optional<Online> online(String name) {
        return core.networkPlayers().flatMap(players -> players.named(name, false))
                .map(here -> new Online(here.player(), here.name(), here.server()));
    }

    @Override
    public Optional<UUID> idOf(String name) {
        return core.playerHistory().flatMap(history -> history.idOf(name));
    }

    @Override
    public Optional<Seen> lastSeen(UUID player) {
        return core.playerHistory().flatMap(history -> history.lastSeen(player))
                .map(seen -> new Seen(seen.name(), seen.server(), seen.at(), seen.online()));
    }

    @Override
    public Optional<Long> firstSeen(UUID player) {
        return core.playerHistory().flatMap(history -> history.firstSeen(player));
    }

    @Override
    public long playtimeMinutes(UUID player) {
        return core.playtime().minutes(player);
    }

    @Override
    public Optional<String> rank(UUID player) {
        return core.groups().primaryGroup(player);
    }

    @Override
    public boolean linked(UUID player) {
        return links.linkOf(player).isPresent();
    }

    @Override
    public List<Server> servers() {
        return core.networkPlayers().map(NetworkPlayers::servers).orElse(List.of()).stream()
                .map(server -> new Server(server.id(), server.name(), server.players())).toList();
    }

    @Override
    public int onlineCount() {
        return core.networkPlayers().map(players -> players.everyone(false).size()).orElse(0);
    }

    @Override
    public Map<String, String> serverNames() {
        Map<String, String> names = new HashMap<>();
        core.networkPlayers().map(NetworkPlayers::servers).orElse(List.of())
                .forEach((NetworkServers.Server server) -> names.put(server.id(), server.name()));
        return names;
    }
}
