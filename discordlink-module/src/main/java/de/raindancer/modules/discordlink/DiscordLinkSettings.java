package de.raindancer.modules.discordlink;

import de.raindancer.core.data.settings.Describe;
import de.raindancer.core.data.settings.In;
import de.raindancer.core.data.settings.Key;
import de.raindancer.core.data.settings.Range;
import de.raindancer.core.data.settings.Settings;
import de.raindancer.core.data.settings.Title;
import de.raindancer.core.data.settings.Topic;
import org.bukkit.Material;

/**
 * What an owner decides in /settings. The bot token is not here: settings are shown and pasted around, so it is
 * a secret set from the console. Which roles may whitelist friends and which role gives which group is chosen in
 * Discord with /mcadmin, from the server's real roles.
 */
@Settings(id = "discordlink", topics = {
        @Topic(path = "discordlink", title = "Discord link", icon = Material.NAME_TAG),
})
public record DiscordLinkSettings(

        @In("discordlink") @Title("Discord bot on")
        @Describe("Runs the bot on one server of the network at a time; if that server stops, another takes over.")
        @Key("enabled")
        boolean enabled,

        @In("discordlink") @Title("Discord server id")
        @Describe("Lilly's Discord server. Developer mode on, right click the server icon, Copy Server ID.")
        @Key("guild-id")
        String guildId,

        @In("discordlink") @Title("Join codes on the kick screen")
        @Describe("Somebody who is not whitelisted sees a code to type into Discord's /link instead of the plain "
                + "\"not whitelisted\" message.")
        @Key("join-codes")
        boolean joinCodes,

        @In("discordlink") @Title("A code lasts, minutes") @Range(min = 2, max = 120)
        @Key("code-minutes")
        int codeMinutes,

        @In("discordlink") @Title("Groups checked against Discord roles, every so many minutes") @Range(min = 5, max = 1440)
        @Key("sync-minutes")
        int syncMinutes) {

    public static final DiscordLinkSettings DEFAULTS = new DiscordLinkSettings(true, "", true, 30, 30);

    public boolean hasGuild() {
        return guildId != null && guildId.strip().matches("\\d{15,25}");
    }
}
