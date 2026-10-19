package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.LookupReport;
import de.raindancer.modules.discordlink.model.PlayerCard;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.Optional;

/**
 * /lookup as one embed: what the Minecraft server knows first (it's only here), then the website's sections, each
 * row marked. Cut to Discord's limits rather than refused.
 */
public final class LookupView implements IDiscordLinkRule {

    static final int COLOUR = 0xFF2E88;

    /**
     * @param report what the website said; null when it couldn't be asked
     * @param linkedTo the Discord id the Minecraft account is linked to — staff see to whom
     */
    public MessageEmbed embed(LookupReport report, Optional<PlayerCard> card, Optional<String> linkedTo) {
        EmbedBuilder embed = new EmbedBuilder().setColor(COLOUR);
        String title = report != null ? report.title() : card.map(PlayerCard::name).orElse("?");
        embed.setTitle(cut(title, MessageEmbed.TITLE_MAX_LENGTH), report != null ? report.url() : null);
        if (report != null && report.thumbnail() != null) {
            embed.setThumbnail(report.thumbnail());
        } else {
            card.flatMap(PlayerCard::headUrl).ifPresent(embed::setThumbnail);
        }
        card.ifPresent(c -> embed.addField("On the Minecraft server", cut(server(c, linkedTo), MessageEmbed.VALUE_MAX_LENGTH), false));
        if (report != null) {
            for (LookupReport.Section section : report.sections()) {
                if (embed.getFields().size() >= 24) {
                    break;
                }
                StringBuilder value = new StringBuilder();
                for (LookupReport.Row row : section.rows()) {
                    value.append(value.isEmpty() ? "" : "\n").append(mark(row.state())).append(" **").append(row.text()).append("**");
                    if (row.detail() != null && !row.detail().isBlank()) {
                        value.append(" · ").append(row.detail());
                    }
                }
                embed.addField(cut(section.name(), MessageEmbed.TITLE_MAX_LENGTH), cut(value.toString(), MessageEmbed.VALUE_MAX_LENGTH), false);
            }
        }
        StringBuilder description = new StringBuilder();
        if (report == null) {
            description.append("The website couldn't be asked, so only the server's own stats.");
        } else {
            if (!report.misses().isEmpty()) {
                description.append("Nothing found on ").append(String.join(" · ", report.misses())).append(".");
            }
            if (report.panel() != null) {
                description.append(description.isEmpty() ? "" : "\n").append("[Open in the panel](").append(report.panel()).append(")");
            }
        }
        if (!description.isEmpty()) {
            embed.setDescription(cut(description.toString(), 2000));
        }
        return embed.build();
    }

    private static String server(PlayerCard c, Optional<String> linkedTo) {
        StringBuilder s = new StringBuilder("**").append(c.name()).append("**\n");
        s.append(c.onlineOn().map(server -> "🟢 Online on " + server)
                .orElseGet(() -> c.lastSeen().map(at -> "Last seen <t:" + at / 1000 + ":R> on " + c.lastServer().orElse("?"))
                        .orElse("Never seen on the network"))).append("\n");
        c.firstSeen().ifPresent(at -> s.append("First joined <t:").append(at / 1000).append(":D>\n"));
        s.append(c.playtimeText()).append(" played");
        c.rank().ifPresent(rank -> s.append(" · Rank ").append(rank));
        s.append("\n").append(linkedTo.map(id -> "Linked to <@" + id + ">").orElse(c.linked() ? "Linked to Discord" : "Not linked to Discord"));
        return s.toString();
    }

    static String mark(String state) {
        return switch (state == null ? "" : state) {
            case "yes" -> "✅";
            case "no" -> "❌";
            case "sub" -> "⭐";
            case "unknown" -> "❔";
            default -> "▫️";
        };
    }

    static String cut(String text, int most) {
        if (text == null || text.length() <= most) {
            return text;
        }
        return text.substring(0, most - 1) + "…";
    }

    @Override
    public String describe() {
        return "lays out /lookup: the server's stats, then the website's rows";
    }
}
