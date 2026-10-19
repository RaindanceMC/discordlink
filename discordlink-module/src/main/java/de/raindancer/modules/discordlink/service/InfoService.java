package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.NetworkStatus;
import de.raindancer.modules.discordlink.model.PlayerCard;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** /status and /player, put together from public facts. On an async thread: some facts are database I/O. */
public final class InfoService implements IDiscordLinkService {

    private final PlayerFacts facts;

    public InfoService(PlayerFacts facts) {
        this.facts = facts;
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        // Nothing configurable: what is public is decided here, not in a setting somebody might flip by accident.
    }

    public NetworkStatus status() {
        return new NetworkStatus(facts.onlineCount(), facts.servers().stream()
                .map(server -> new NetworkStatus.Line(server.name(), server.players())).toList());
    }

    public Optional<PlayerCard> card(String typed) {
        String name = typed == null ? "" : typed.strip();
        if (!name.matches("\\.?[A-Za-z0-9_ ]{1,16}")) {
            return Optional.empty();
        }
        Map<String, String> servers = facts.serverNames();
        Optional<PlayerFacts.Online> online = facts.online(name);
        Optional<UUID> id = online.map(PlayerFacts.Online::id).or(() -> facts.idOf(name));
        if (id.isEmpty()) {
            return Optional.empty();
        }
        UUID player = id.get();
        Optional<PlayerFacts.Seen> seen = facts.lastSeen(player);
        String shownName = online.map(PlayerFacts.Online::name).or(() -> seen.map(PlayerFacts.Seen::name)).orElse(name);
        // Not visible but "online" in the table = vanished or just left: shown as last seen, never as online.
        return Optional.of(new PlayerCard(player, shownName,
                online.map(here -> servers.getOrDefault(here.server(), here.server())),
                online.isPresent() ? Optional.empty() : seen.map(PlayerFacts.Seen::at),
                online.isPresent() ? Optional.empty() : seen.map(s -> servers.getOrDefault(s.server(), s.server())),
                facts.firstSeen(player), facts.playtimeMinutes(player), facts.rank(player), facts.linked(player)));
    }
}
