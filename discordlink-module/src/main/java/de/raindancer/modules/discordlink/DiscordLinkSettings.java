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
        int syncMinutes,

        @In("discordlink") @Title("More Discord servers for /link")
        @Describe("Server ids, apart by spaces or commas, where /link, /unlink, /accounts, /whitelist, /status and "
                + "/player work too. Roles, groups and /mcadmin stay on Lilly's server: whitelister roles and "
                + "LuckPerms groups always come from somebody's roles there.")
        @Key("more-link-guild-ids")
        String moreLinkGuildIds,

        @In("discordlink") @Title("Discord server for /lookup")
        @Describe("The staff server where /lookup exists and works — nowhere else. Empty: no /lookup.")
        @Key("lookup-guild-id")
        String lookupGuildId,

        @In("discordlink") @Title("Website /lookup asks")
        @Describe("The applications website; its key is a secret set from the console (/discordlink lookupkey).")
        @Key("lookup-url")
        String lookupUrl) {

    public static final DiscordLinkSettings DEFAULTS = new DiscordLinkSettings(true, "", true, 30, 30, "", "",
            "https://applications.lillyyxoxo.net");

    public boolean hasLookupGuild() {
        return lookupGuildId != null && lookupGuildId.strip().matches("\\d{15,25}");
    }

    public boolean hasGuild() {
        return guildId != null && guildId.strip().matches("\\d{15,25}");
    }

    /** The other servers /link works on, valid ids only, in order, without the main one or repeats. */
    public java.util.List<String> moreLinkGuilds() {
        if (moreLinkGuildIds == null) {
            return java.util.List.of();
        }
        String main = guildId == null ? "" : guildId.strip();
        return java.util.Arrays.stream(moreLinkGuildIds.split("[\\s,;]+"))
                .filter(id -> id.matches("\\d{15,25}") && !id.equals(main))
                .distinct().toList();
    }

    /** Lilly's server or one of the others. */
    public boolean isLinkGuild(String id) {
        return id != null && ((hasGuild() && guildId.strip().equals(id)) || moreLinkGuilds().contains(id));
    }
}
