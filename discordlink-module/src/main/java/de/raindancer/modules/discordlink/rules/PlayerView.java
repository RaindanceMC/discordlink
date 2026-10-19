package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.PlayerCard;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** /player as one card: where they are, then everything the network knows — fields it doesn't know stay out. */
public final class PlayerView implements IDiscordLinkRule {

    public MessageEmbed embed(PlayerCard c) {
        EmbedBuilder embed = new EmbedBuilder().setColor(LookupView.COLOUR).setTitle(c.name());
        c.headUrl().ifPresent(embed::setThumbnail);
        embed.addField("Status", c.onlineOn().map(server -> "🟢 Online on " + server)
                .orElseGet(() -> c.lastSeen().map(at -> "⚫ Last seen <t:" + at / 1000 + ":R> on " + c.lastServer().orElse("?"))
                        .orElse("⚫ Never seen on the network")), false);
        embed.addField("⏱️ Playtime", c.playtimeText(), true);
        c.rank().ifPresent(rank -> embed.addField("🎖️ Rank", rank, true));
        c.firstSeen().ifPresent(at -> embed.addField("📅 First joined", "<t:" + at / 1000 + ":D>", true));
        c.balance().ifPresent(balance -> embed.addField("💰 Balance", balance, true));
        c.guild().ifPresent(guild -> embed.addField("🛡️ Guild", guild, true));
        c.job().ifPresent(job -> embed.addField("⛏️ Job", job, true));
        if (c.achievements() > 0) {
            embed.addField("🏆 Achievements", c.achievements() + " earned · " + c.achievementPoints() + " points", true);
        }
        embed.addField("🔗 Discord", c.linked() ? "Linked" : "Not linked", true);
        return embed.build();
    }

    @Override
    public String describe() {
        return "lays out /player";
    }
}
