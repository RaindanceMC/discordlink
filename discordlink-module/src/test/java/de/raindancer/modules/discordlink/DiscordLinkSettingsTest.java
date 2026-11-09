package de.raindancer.modules.discordlink;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLinkSettingsTest {

    private static final String LILLY = "1524104605972037752";
    private static final String OTHER = "1548490432240025651";
    private static final String STAFF = "1561747222280933476";

    private static DiscordLinkSettings with(String guild, String more) {
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        return new DiscordLinkSettings(d.enabled(), guild, d.joinCodes(), d.codeMinutes(), d.syncMinutes(), more,
                STAFF, d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(), d.hushPunishments(),
                d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), d.discordName(), d.networkName(), d.accent());
    }

    @Test
    void hushFeedDefaults() {
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        assertEquals("https://hush.raindancer118.de/api/minecraft/events", d.hushUrl());
        assertEquals("", d.hushServerName());
        assertTrue(d.hushAnticheat() && d.hushPunishments() && d.hushReports() && d.hushServer());
    }

    @Test
    void noMoreServersByDefault() {
        assertEquals(List.of(), DiscordLinkSettings.DEFAULTS.moreLinkGuilds());
        assertFalse(with(LILLY, "").isLinkGuild(OTHER));
    }

    @Test
    void theMainServerAndEveryListedOneTakeLink() {
        DiscordLinkSettings s = with(LILLY, OTHER);
        assertTrue(s.isLinkGuild(LILLY));
        assertTrue(s.isLinkGuild(OTHER));
        assertFalse(s.isLinkGuild(STAFF));
        assertFalse(s.isLinkGuild(null));
    }

    @Test
    void idsMaySitApartByCommaOrSpaceAndJunkIsDropped() {
        DiscordLinkSettings s = with(LILLY, " " + OTHER + ",  1111111111111111111 ; nonsense, 12 ");
        assertEquals(List.of(OTHER, "1111111111111111111"), s.moreLinkGuilds());
    }

    @Test
    void theMainServerAndRepeatsAreNotListedTwice() {
        DiscordLinkSettings s = with(LILLY, OTHER + " " + LILLY + " " + OTHER);
        assertEquals(List.of(OTHER), s.moreLinkGuilds());
    }

    @Test
    void wordingDefaultsStayLillys() {
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        assertEquals("Lilly's network", d.plainNetwork());
        assertEquals("[discord, <#5865F2>Lilly's Discord</#5865F2>, accent, light_purple]", java.util.Arrays.toString(d.wording()));
    }

    @Test
    void wordingOfAnotherNetworkAndBlankFallsBack() {
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        DiscordLinkSettings hyades = new DiscordLinkSettings(d.enabled(), d.guildId(), d.joinCodes(), d.codeMinutes(), d.syncMinutes(),
                d.moreLinkGuildIds(), d.lookupGuildId(), d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(),
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(),
                "the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", "<#6EC1EA>Hyades</#6EC1EA>", "#6EC1EA");
        assertEquals("Hyades", hyades.plainNetwork());
        assertEquals("the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", hyades.wording()[1].toString());
        assertEquals("#6EC1EA", hyades.wording()[3].toString());
        DiscordLinkSettings blank = new DiscordLinkSettings(d.enabled(), d.guildId(), d.joinCodes(), d.codeMinutes(), d.syncMinutes(),
                d.moreLinkGuildIds(), d.lookupGuildId(), d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(),
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), " ", null, "");
        assertEquals("Lilly's network", blank.plainNetwork());
        assertEquals("light_purple", blank.wording()[3].toString());
    }

    @Test
    void theLinkMessagesRenderWithTheNetworksOwnWording() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                java.nio.file.Path.of("src/main/resources/de/raindancer/modules/discordlink/messages.yml").toFile());
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        DiscordLinkSettings hyades = new DiscordLinkSettings(d.enabled(), d.guildId(), d.joinCodes(), d.codeMinutes(), d.syncMinutes(),
                d.moreLinkGuildIds(), d.lookupGuildId(), d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(),
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(),
                "the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", "Hyades", "#6EC1EA");
        var mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for (var which : java.util.List.of(d, hyades)) {
            Object[] values = java.util.stream.Stream.concat(java.util.Arrays.stream(new Object[]{"code", "75R6Y-MJUAM", "minutes", "30"}),
                    java.util.Arrays.stream(which.wording())).toArray();
            String code = plain.serialize(mini.deserialize(de.raindancer.core.ui.text.Text.fill(yaml.getString("discordlink.link.code"), values)));
            String kick = plain.serialize(mini.deserialize(de.raindancer.core.ui.text.Text.fill(yaml.getString("discordlink.join.kick"), values)));
            String name = which == d ? "Lilly's Discord" : "the hyadesmc.net Discord";
            assertEquals("Type /link 75R6Y-MJUAM in " + name + " to link your account. (lasts 30 minutes)", code);
            assertTrue(kick.contains("Type this in " + name + ":"), kick);
            assertTrue(kick.contains("/link 75R6Y-MJUAM"), kick);
            assertTrue(!kick.contains("<") && !code.contains("<"), "no markup left over");
        }
    }
}
