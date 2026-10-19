package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.model.NetworkStatus;
import de.raindancer.modules.discordlink.model.PlayerCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InfoServiceTest {

    private final UUID alex = UUID.fromString("c9e2c821-7b5e-44e9-a5f7-7554a4da0120");
    private final UUID ghost = UUID.randomUUID();

    /** A network with Alex on the SMP, a vanished admin on the arena, and Ghost who left yesterday. */
    private final PlayerFacts facts = new PlayerFacts() {
        @Override
        public Optional<Online> online(String name) {
            return name.equalsIgnoreCase("alex") ? Optional.of(new Online(alex, "Alex", "smp")) : Optional.empty();
        }

        @Override
        public Optional<UUID> idOf(String name) {
            return name.equalsIgnoreCase("ghost") ? Optional.of(ghost) : name.equalsIgnoreCase("alex") ? Optional.of(alex) : Optional.empty();
        }

        @Override
        public Optional<Seen> lastSeen(UUID player) {
            if (player.equals(ghost)) {
                return Optional.of(new Seen("Ghost", "arena", 5_000, false));
            }
            return player.equals(alex) ? Optional.of(new Seen("Alex", "smp", 9_000, true)) : Optional.empty();
        }

        @Override
        public Optional<Long> firstSeen(UUID player) {
            return Optional.of(1_000L);
        }

        @Override
        public long playtimeMinutes(UUID player) {
            return player.equals(alex) ? 754 : 15;
        }

        @Override
        public Optional<String> rank(UUID player) {
            return Optional.of(player.equals(alex) ? "admin" : "default");
        }

        @Override
        public boolean linked(UUID player) {
            return player.equals(alex);
        }

        @Override
        public List<Server> servers() {
            return List.of(new Server("smp", "Lilly's SMP", 1), new Server("arena", "Arena", 0));
        }

        @Override
        public int onlineCount() {
            return 1;
        }

        @Override
        public Map<String, String> serverNames() {
            return Map.of("smp", "Lilly's SMP", "arena", "Arena");
        }

        @Override
        public Optional<String> balance(UUID player) {
            return player.equals(alex) ? Optional.of("342,180 coins") : Optional.empty();
        }

        @Override
        public Optional<String> guild(UUID player) {
            return player.equals(alex) ? Optional.of("[ELF] Elves") : Optional.empty();
        }

        @Override
        public Optional<String> job(UUID player) {
            return player.equals(alex) ? Optional.of("Miner") : Optional.empty();
        }

        @Override
        public Optional<Achievements> achievements(UUID player) {
            return player.equals(alex) ? Optional.of(new Achievements(12, 340)) : Optional.empty();
        }

        @Override
        public List<ServerState> network() {
            return List.of(new ServerState("smp", "Lilly's SMP", true, 1, 19.9, 12.0, 100L, 0),
                    new ServerState("ctf", "CTF", false, 0, Double.NaN, Double.NaN, 0, 4_000L));
        }
    };

    private final InfoService info = new InfoService(facts);

    @Test
    @DisplayName("an online player shows where they are, by their display server name")
    void online() {
        PlayerCard card = info.card("ALEX").orElseThrow();
        assertThat(card.name()).isEqualTo("Alex");
        assertThat(card.onlineOn()).contains("Lilly's SMP");
        assertThat(card.playtimeText()).isEqualTo("12 h 34 min");
        assertThat(card.rank()).contains("admin");
        assertThat(card.linked()).isTrue();
        assertThat(card.headUrl()).hasValueSatisfying(url -> assertThat(url).startsWith("https://mc-heads.net/avatar/" + alex));
    }

    @Test
    @DisplayName("somebody offline shows when and where they were last; vanished staff look offline too")
    void offline() {
        PlayerCard card = info.card("ghost").orElseThrow();
        assertThat(card.onlineOn()).isEmpty();
        assertThat(card.lastSeen()).contains(5_000L);
        assertThat(card.lastServer()).contains("Arena");
        assertThat(card.playtimeText()).isEqualTo("15 min");
        assertThat(card.linked()).isFalse();
    }

    @Test
    @DisplayName("a name the network never saw finds nothing")
    void unknown() {
        assertThat(info.card("nobody_at_all")).isEmpty();
        assertThat(info.card("")).isEmpty();
    }

    @Test
    @DisplayName("the status counts everybody visible, server by server")
    void status() {
        NetworkStatus status = info.status();
        assertThat(status.online()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bedrock players (Floodgate ids) get no Java head picture")
    void bedrockHead() {
        UUID floodgate = new UUID(0L, 0x0009_01F4_A3B2L);
        assertThat(new PlayerCard(floodgate, "Pocket", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0,
                Optional.empty(), false).headUrl()).isEmpty();
    }

    @Test
    @DisplayName("the card carries balance, guild, job and achievements where the network knows them")
    void extras() {
        PlayerCard alexCard = info.card("Alex").orElseThrow();
        assertThat(alexCard.balance()).contains("342,180 coins");
        assertThat(alexCard.guild()).contains("[ELF] Elves");
        assertThat(alexCard.job()).contains("Miner");
        assertThat(alexCard.achievements()).isEqualTo(12);
        assertThat(alexCard.achievementPoints()).isEqualTo(340);
        assertThat(info.card("Ghost").orElseThrow().balance()).isEmpty();
    }

    @Test
    @DisplayName("the network status lists every known server, up or down, with how it runs")
    void networkStatus() {
        NetworkStatus status = info.status();
        assertThat(status.servers()).extracting(NetworkStatus.Line::name).containsExactly("Lilly's SMP", "CTF");
        assertThat(status.servers().getFirst().tps()).isEqualTo(19.9);
        assertThat(status.servers().get(1).online()).isFalse();
        assertThat(status.servers().get(1).downSince()).isEqualTo(4_000L);
    }
}
