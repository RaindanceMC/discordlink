package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.NetworkStatus;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatusViewTest {

    private static final long NOW = 1_800_000_000_000L;
    private final StatusView view = new StatusView();

    private static NetworkStatus.Line up(String name, int players, double tps, double mspt, long upMinutes) {
        return new NetworkStatus.Line(name, true, players, tps, mspt, NOW - upMinutes * 60_000, 0);
    }

    private static NetworkStatus.Line down(String name, long downMinutes) {
        return new NetworkStatus.Line(name, false, 0, Double.NaN, Double.NaN, 0, NOW - downMinutes * 60_000);
    }

    @Test
    void everythingRunningSmoothly() {
        MessageEmbed embed = view.embed(new NetworkStatus(13, List.of(up("Lilly's SMP", 12, 19.98, 11.4, 192),
                up("Hub", 1, 20, 3.2, 30))), NOW);
        assertThat(embed.getTitle()).isEqualTo("🟢 Lilly's network is running smoothly");
        assertThat(embed.getColorRaw()).isEqualTo(StatusView.GREEN);
        assertThat(embed.getDescription()).contains("**13** players online").contains("**2** of **2** servers up");
        MessageEmbed.Field smp = embed.getFields().getFirst();
        assertThat(smp.getName()).isEqualTo("🟢 Lilly's SMP");
        assertThat(smp.getValue()).contains("👥 12 players").contains("⚡ 20.0 TPS · 11 ms/tick").contains("⏱ up 3 h 12 min");
        assertThat(smp.isInline()).isTrue();
        assertThat(embed.getTimestamp()).isNotNull();
    }

    @Test
    void aSlowServerTurnsTheVerdictYellow() {
        MessageEmbed embed = view.embed(new NetworkStatus(5, List.of(up("Lilly's SMP", 5, 16.2, 61.8, 5), up("Hub", 0, 20, 2, 5))), NOW);
        assertThat(embed.getTitle()).isEqualTo("🟡 Lilly's network is up, but a bit slow");
        assertThat(embed.getColorRaw()).isEqualTo(StatusView.YELLOW);
        assertThat(embed.getFields().getFirst().getName()).isEqualTo("🟡 Lilly's SMP");
        assertThat(embed.getFields().getFirst().getValue()).contains("👥 5 players").contains("16.2 TPS · 62 ms/tick").contains("up 5 min");
    }

    @Test
    void anOfflineServerIsListedWithSince() {
        MessageEmbed embed = view.embed(new NetworkStatus(1, List.of(up("Hub", 1, 20, 2, 60), down("Arena", 90))), NOW);
        assertThat(embed.getTitle()).isEqualTo("🟠 1 of 2 servers are up");
        assertThat(embed.getColorRaw()).isEqualTo(StatusView.ORANGE);
        MessageEmbed.Field arena = embed.getFields().get(1);
        assertThat(arena.getName()).isEqualTo("🔴 Arena");
        assertThat(arena.getValue()).contains("Offline since <t:" + (NOW - 90 * 60_000) / 1000 + ":R>");
        assertThat(embed.getFields().get(0).getValue()).contains("👤 1 player").doesNotContain("1 players");
    }

    @Test
    void unknownNumbersAreLeftOutNotFaked() {
        MessageEmbed embed = view.embed(new NetworkStatus(0, List.of(up("CTF", 0, Double.NaN, Double.NaN, 1))), NOW);
        assertThat(embed.getFields().getFirst().getValue()).doesNotContain("NaN").doesNotContain("TPS").contains("Nobody on");
    }

    @Test
    void nothingKnownAtAll() {
        MessageEmbed embed = view.embed(new NetworkStatus(0, List.of()), NOW);
        assertThat(embed.getTitle()).isEqualTo("🔴 No server is answering right now");
        assertThat(embed.getColorRaw()).isEqualTo(StatusView.RED);
    }
}
