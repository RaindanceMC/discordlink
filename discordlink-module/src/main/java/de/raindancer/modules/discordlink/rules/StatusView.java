package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.NetworkStatus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;
import java.util.Locale;

/**
 * /status as a network status board: one verdict with its colour, then every server — up or down, how many are on,
 * how well it ticks, how long it has been up. Numbers a server doesn't send are left out, never made up.
 */
public final class StatusView implements IDiscordLinkRule {

    static final int GREEN = 0x2ECC71;
    static final int YELLOW = 0xF1C40F;
    static final int ORANGE = 0xE67E22;
    static final int RED = 0xE74C3C;

    /** A Minecraft server ticks 20 times a second; below 18 players start to feel it, below 14 it's rough. */
    private static final double SMOOTH_TPS = 18;
    private static final double ROUGH_TPS = 14;
    /** 50 ms is a whole tick's budget: above it the server can't keep up. */
    private static final double SMOOTH_MSPT = 50;

    private enum Health { GOOD, SLOW, ROUGH, DOWN }

    public MessageEmbed embed(NetworkStatus status, long now) {
        long up = status.servers().stream().filter(NetworkStatus.Line::online).count();
        int total = status.servers().size();
        boolean slow = status.servers().stream().anyMatch(line -> health(line) == Health.SLOW || health(line) == Health.ROUGH);
        EmbedBuilder embed = new EmbedBuilder().setTimestamp(Instant.ofEpochMilli(now));
        if (up == 0) {
            embed.setTitle("🔴 No server is answering right now").setColor(RED);
        } else if (up < total) {
            embed.setTitle("🟠 " + up + " of " + total + " servers are up").setColor(ORANGE);
        } else if (slow) {
            embed.setTitle("🟡 Lilly's network is up, but a bit slow").setColor(YELLOW);
        } else {
            embed.setTitle("🟢 Lilly's network is running smoothly").setColor(GREEN);
        }
        embed.setDescription("**" + status.online() + "** player" + (status.online() == 1 ? "" : "s") + " online · **"
                + up + "** of **" + total + "** servers up");
        for (NetworkStatus.Line line : status.servers().stream().limit(24).toList()) {
            embed.addField(mark(health(line)) + " " + line.name(), value(line, now), true);
        }
        embed.setFooter("Refresh with the button below");
        return embed.build();
    }

    private static Health health(NetworkStatus.Line line) {
        if (!line.online()) {
            return Health.DOWN;
        }
        if (Double.isNaN(line.tps())) {
            return Health.GOOD;
        }
        if (line.tps() >= SMOOTH_TPS && (Double.isNaN(line.mspt()) || line.mspt() <= SMOOTH_MSPT)) {
            return Health.GOOD;
        }
        return line.tps() >= ROUGH_TPS ? Health.SLOW : Health.ROUGH;
    }

    private static String mark(Health health) {
        return switch (health) {
            case GOOD -> "🟢";
            case SLOW -> "🟡";
            case ROUGH -> "🟠";
            case DOWN -> "🔴";
        };
    }

    private static String value(NetworkStatus.Line line, long now) {
        if (!line.online()) {
            return line.downSince() > 0 ? "Offline since <t:" + line.downSince() / 1000 + ":R>" : "Offline";
        }
        StringBuilder value = new StringBuilder(switch (line.players()) {
            case 0 -> "💤 Nobody on";
            case 1 -> "👤 1 player";
            default -> "👥 " + line.players() + " players";
        });
        if (!Double.isNaN(line.tps())) {
            value.append("\n⚡ ").append(String.format(Locale.ROOT, "%.1f", line.tps())).append(" TPS");
            if (!Double.isNaN(line.mspt())) {
                value.append(" · ").append(Math.round(line.mspt())).append(" ms/tick");
            }
        }
        if (line.startedAt() > 0 && line.startedAt() <= now) {
            value.append("\n⏱ up ").append(duration(now - line.startedAt()));
        }
        return value.toString();
    }

    static String duration(long millis) {
        long minutes = millis / 60_000;
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + " h " + (minutes % 60) + " min";
        }
        return (hours / 24) + " d " + (hours % 24) + " h";
    }

    @Override
    public String describe() {
        return "lays out /status as a network status board";
    }
}
