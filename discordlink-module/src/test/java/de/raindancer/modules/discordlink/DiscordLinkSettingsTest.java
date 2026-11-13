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
                d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), d.hushReviews(), d.discordName(), d.networkName(), d.accent(), d.invite());
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
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), d.hushReviews(),
                "the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", "<#6EC1EA>Hyades</#6EC1EA>", "#6EC1EA", "");
        assertEquals("Hyades", hyades.plainNetwork());
        assertEquals("the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", hyades.wording()[1].toString());
        assertEquals("#6EC1EA", hyades.wording()[3].toString());
        DiscordLinkSettings blank = new DiscordLinkSettings(d.enabled(), d.guildId(), d.joinCodes(), d.codeMinutes(), d.syncMinutes(),
                d.moreLinkGuildIds(), d.lookupGuildId(), d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(),
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), d.hushReviews(), " ", null, "", "");
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
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(), d.hushReviews(),
                "the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", "Hyades", "#6EC1EA", "");
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

    private static DiscordLinkSettings invite(String invite) {
        DiscordLinkSettings d = DiscordLinkSettings.DEFAULTS;
        return new DiscordLinkSettings(d.enabled(), d.guildId(), d.joinCodes(), d.codeMinutes(), d.syncMinutes(),
                d.moreLinkGuildIds(), d.lookupGuildId(), d.lookupUrl(), d.hushUrl(), d.hushServerName(), d.hushAnticheat(),
                d.hushPunishments(), d.hushReports(), d.hushServer(), d.hushAdminMode(), d.hushActions(), d.hushPollSeconds(),
                d.hushReviews(), "the <#6EC1EA>hyadesmc.net</#6EC1EA> Discord", "Hyades", "#6EC1EA", invite);
    }

    @Test
    void noInviteByDefaultSoDiscordStaysTheLinkCode() {
        assertEquals("", DiscordLinkSettings.DEFAULTS.invite());
        assertEquals(java.util.Optional.empty(), DiscordLinkSettings.DEFAULTS.inviteUrl());
        assertEquals(java.util.Optional.empty(), invite("   ").inviteUrl());
        assertEquals(java.util.Optional.empty(), invite(null).inviteUrl());
    }

    @Test
    void anInviteIsAnHttpsAddressAndABareOneGetsTheScheme() {
        assertEquals(java.util.Optional.of("https://discord.gg/7w6FQncTqf"), invite(" https://discord.gg/7w6FQncTqf ").inviteUrl());
        assertEquals(java.util.Optional.of("https://discord.gg/7w6FQncTqf"), invite("discord.gg/7w6FQncTqf").inviteUrl());
        assertEquals(java.util.Optional.of("https://discord.com/invite/abc-DEF_1"), invite("https://discord.com/invite/abc-DEF_1").inviteUrl());
    }

    @Test
    void anythingThatCouldBreakOutOfTheClickIsRefused() {
        for (String bad : java.util.List.of("http://discord.gg/abc", "javascript:alert(1)", "https://discord.gg/a'><click:run_command:'/op me'>",
                "https://discord.gg/a b", "https://discord.gg/<red>", "https://", "ftp://discord.gg/abc")) {
            assertEquals(java.util.Optional.empty(), invite(bad).inviteUrl(), bad);
        }
    }

    @Test
    void theInviteMessageIsOneClickableLine() {
        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                java.nio.file.Path.of("src/main/resources/de/raindancer/modules/discordlink/messages.yml").toFile());
        DiscordLinkSettings hyades = invite("https://discord.gg/7w6FQncTqf");
        var mini = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        var component = mini.deserialize(de.raindancer.core.ui.text.Text.fill(yaml.getString("discordlink.invite"),
                hyades.inviteValues().toArray()));
        assertEquals("Join the hyadesmc.net Discord: discord.gg/7w6FQncTqf (click) · /link ties your account to it.",
                plain.serialize(component));
        java.util.List<net.kyori.adventure.text.event.ClickEvent> clicks = new java.util.ArrayList<>();
        collectClicks(component, clicks);
        assertEquals(1, clicks.size(), "exactly one button");
        assertEquals(net.kyori.adventure.text.event.ClickEvent.Action.OPEN_URL, clicks.get(0).action());
        assertEquals("https://discord.gg/7w6FQncTqf",
                ((net.kyori.adventure.text.event.ClickEvent.Payload.Text) clicks.get(0).payload()).value());
    }

    private static void collectClicks(net.kyori.adventure.text.Component component,
                                      java.util.List<net.kyori.adventure.text.event.ClickEvent> into) {
        var click = component.clickEvent();
        if (click != null && (into.isEmpty() || !into.get(into.size() - 1).equals(click))) {
            into.add(click);
        }
        component.children().forEach(child -> collectClicks(child, into));
    }
}
