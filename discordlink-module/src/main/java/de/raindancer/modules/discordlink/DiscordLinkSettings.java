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
        String lookupUrl,

        @In("discordlink") @Title("Hush feed: address")
        @Describe("Where this server posts its Minecraft feed (anti-cheat alerts, punishments, reports, start and stop) "
                + "for the Discord bot Hush. The token is a secret set from the console (/discordlink hushtoken).")
        @Key("hush.url")
        String hushUrl,

        @In("discordlink") @Title("Hush feed: this server's name")
        @Describe("Shown in the embeds, 32 characters at most. Empty: the server's id on the network.")
        @Key("hush.server-name")
        String hushServerName,

        @In("discordlink") @Title("Hush feed: anti-cheat alerts")
        @Key("hush.anticheat")
        boolean hushAnticheat,

        @In("discordlink") @Title("Hush feed: punishments")
        @Describe("Bans, mutes, kicks and warnings given here, and bans and mutes lifted.")
        @Key("hush.punishments")
        boolean hushPunishments,

        @In("discordlink") @Title("Hush feed: reports")
        @Describe("Reports players file with /report.")
        @Key("hush.reports")
        boolean hushReports,

        @In("discordlink") @Title("Hush feed: server start and stop")
        @Key("hush.server")
        boolean hushServer,

        @In("discordlink") @Title("Hush feed: admin mode")
        @Describe("Staff entering and leaving admin mode, with how long they stayed.")
        @Key("hush.admin-mode")
        boolean hushAdminMode,

        @In("discordlink") @Title("Hush: actions from Discord")
        @Describe("Asks Hush every few seconds whether Discord wants something done here: /link and /unlink, /player, "
                + "and Discord roles and in-game groups kept the same. Off: the feed only sends.")
        @Key("hush.actions")
        boolean hushActions,

        @In("discordlink") @Title("Hush: ask every, seconds") @Range(min = 1, max = 60)
        @Key("hush.poll-seconds")
        int hushPollSeconds,

        @In("discordlink") @Title("Hush: requests for staff")
        @Describe("Requests waiting for staff, like Prime map art, go to Discord with a picture and Approve and Deny "
                + "buttons. Needs the actions from Discord.")
        @Key("hush.reviews")
        boolean hushReviews,

        @In("discordlink") @Title("Wording: the Discord server")
        @Describe("How in-game messages call the Discord server, MiniMessage allowed: \"Type /link CODE in <discord>\".")
        @Key("discord-name")
        String discordName,

        @In("discordlink") @Title("Wording: the network")
        @Describe("How Discord's /status calls the network, plain text: \"<name> is running smoothly\".")
        @Key("network-name")
        String networkName,

        @In("discordlink") @Title("Wording: accent colour")
        @Describe("The colour of the /link code on screen: a MiniMessage colour like light_purple or #6EC1EA.")
        @Key("accent")
        String accent,

        @In("discordlink") @Title("Discord invite")
        @Describe("The invite /discord and /dc show as a button, e.g. https://discord.gg/abc123. Empty: /discord gives a "
                + "link code, like /link.")
        @Key("invite")
        String invite) {

    public static final String HUSH_URL = "https://hush.raindancer118.de/api/minecraft/events";

    public static final DiscordLinkSettings DEFAULTS = new DiscordLinkSettings(true, "", true, 30, 30, "", "",
            "https://applications.lillyyxoxo.net", HUSH_URL, "", true, true, true, true, true, true, 3, true,
            "<#5865F2>Lilly's Discord</#5865F2>", "Lilly's network", "light_purple", "");

    // The address goes into <click:open_url:'…'>, so nothing that could close the quote or open a tag gets through.
    private static final java.util.regex.Pattern SAFE_INVITE = java.util.regex.Pattern.compile("https://[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/[A-Za-z0-9._~/?=&%#-]*)?");

    /** The invite as a safe https address, or empty when none is set or it is not one. */
    public java.util.Optional<String> inviteUrl() {
        if (invite == null || invite.isBlank()) {
            return java.util.Optional.empty();
        }
        String url = invite.strip();
        if (!url.contains("://")) {
            url = "https://" + url;
        }
        return SAFE_INVITE.matcher(url).matches() ? java.util.Optional.of(url) : java.util.Optional.empty();
    }

    /** Placeholders for discordlink.invite: the wording plus the button, opened and closed in one piece. */
    public java.util.List<Object> inviteValues() {
        String url = inviteUrl().orElse("");
        String shown = url.replaceFirst("^https://(www\\.)?", "");
        String accent = orElse(this.accent, DEFAULTS.accent());
        java.util.List<Object> values = new java.util.ArrayList<>(java.util.Arrays.asList(wording()));
        values.addAll(java.util.List.of("button", de.raindancer.core.ui.text.Markup.of(
                "<click:open_url:'" + url + "'><hover:show_text:'<gray>Open it in your browser'><" + accent + "><u>" + shown
                        + "</u></" + accent + "> <dark_gray>(click)</dark_gray></hover></click>")));
        return values;
    }

    /** Placeholders for the messages that name the Discord server and the network. */
    public Object[] wording() {
        // markup on purpose: both are written by the server owner, never by a player
        return new Object[]{"discord", de.raindancer.core.ui.text.Markup.of(orElse(discordName, DEFAULTS.discordName())),
                "accent", de.raindancer.core.ui.text.Markup.of(orElse(accent, DEFAULTS.accent()))};
    }

    /** The network's name without MiniMessage tags, for Discord. */
    public String plainNetwork() {
        return orElse(networkName, DEFAULTS.networkName()).replaceAll("<[^>]*>", "");
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

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
