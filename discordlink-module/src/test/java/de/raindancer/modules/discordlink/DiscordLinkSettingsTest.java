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
                STAFF, d.lookupUrl());
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
}
