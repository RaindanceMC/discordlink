package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.PlayerCard;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerViewTest {

    private final PlayerView view = new PlayerView();
    private final UUID alex = UUID.fromString("c9e2c821-7b5e-44e9-a5f7-7554a4da0120");

    private MessageEmbed.Field field(MessageEmbed embed, String name) {
        return embed.getFields().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
    }

    @Test
    void everythingTheNetworkKnows() {
        PlayerCard card = new PlayerCard(alex, "Alex", Optional.of("Lilly's SMP"), Optional.empty(), Optional.empty(),
                Optional.of(1_750_000_000_000L), 754, Optional.of("admin"), true,
                Optional.of("342,180 coins"), Optional.of("[ELF] Elves"), Optional.of("Miner"), 12, 340);
        MessageEmbed embed = view.embed(card);

        assertThat(embed.getTitle()).isEqualTo("Alex");
        assertThat(embed.getThumbnail().getUrl()).isEqualTo("https://mc-heads.net/avatar/" + alex + "/64");
        assertThat(field(embed, "Status").getValue()).isEqualTo("🟢 Online on Lilly's SMP");
        assertThat(field(embed, "💰 Balance").getValue()).isEqualTo("342,180 coins");
        assertThat(field(embed, "🛡️ Guild").getValue()).isEqualTo("[ELF] Elves");
        assertThat(field(embed, "⛏️ Job").getValue()).isEqualTo("Miner");
        assertThat(field(embed, "🏆 Achievements").getValue()).isEqualTo("12 earned · 340 points");
        assertThat(field(embed, "⏱️ Playtime").getValue()).isEqualTo("12 h 34 min");
        assertThat(field(embed, "🎖️ Rank").getValue()).isEqualTo("admin");
        assertThat(field(embed, "📅 First joined").getValue()).isEqualTo("<t:1750000000:D>");
        assertThat(field(embed, "🔗 Discord").getValue()).isEqualTo("Linked");
    }

    @Test
    void whatIsNotKnownIsLeftOut() {
        PlayerCard card = new PlayerCard(alex, "Ghost", Optional.empty(), Optional.of(5_000_000L), Optional.of("Arena"),
                Optional.empty(), 15, Optional.empty(), false);
        MessageEmbed embed = view.embed(card);
        assertThat(field(embed, "Status").getValue()).isEqualTo("⚫ Last seen <t:5000:R> on Arena");
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName)
                .doesNotContain("💰 Balance", "🛡️ Guild", "⛏️ Job", "🏆 Achievements", "🎖️ Rank", "📅 First joined");
        assertThat(field(embed, "🔗 Discord").getValue()).isEqualTo("Not linked");
    }
}
