package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GiveawayTest {

    static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    static final String FULL = """
            { "id": "g1", "type": "giveaway",
              "title": "Open the End", "endsAt": 1796396400000, "rulesUrl": "https://hyadesmc.net/giveaway/",
              "rules": { "minutes": 60, "days": 2, "dayMinutes": 10 },
              "players": [ { "uuid": "00000000-0000-0000-0000-00000000000a", "tickets": 7,
                             "link": "https://hushbot.net/go/hyades/referral/open-the-end/alex", "counted": 2,
                             "friends": [ { "name": "Bob", "minutes": 35, "days": 1, "counted": false },
                                          { "name": "Cy", "minutes": 80, "days": 3, "counted": true } ] } ] }""";

    @Test
    @DisplayName("a running giveaway is read completely")
    void parsesRunning() {
        Giveaway giveaway = Giveaway.parse(DataObject.fromJson(FULL)).orElseThrow();

        assertThat(giveaway.running()).isTrue();
        assertThat(giveaway.title()).isEqualTo("Open the End");
        assertThat(giveaway.endsAt()).isEqualTo(1796396400000L);
        assertThat(giveaway.rulesUrl()).isEqualTo("https://hyadesmc.net/giveaway/");
        assertThat(giveaway.rules()).isEqualTo(new Giveaway.Rules(60, 2, 10));
        Giveaway.Member alex = giveaway.member(ALEX);
        assertThat(alex.tickets()).isEqualTo(7);
        assertThat(alex.link()).isEqualTo("https://hushbot.net/go/hyades/referral/open-the-end/alex");
        assertThat(alex.counted()).isEqualTo(2);
        assertThat(alex.friends()).containsExactly(new Giveaway.Friend("Bob", 35, 1, false), new Giveaway.Friend("Cy", 80, 3, true));
    }

    @Test
    @DisplayName("title null means no giveaway is running, with or without the other fields")
    void noGiveaway() {
        Giveaway none = Giveaway.parse(DataObject.fromJson("{\"id\":\"g\",\"type\":\"giveaway\",\"title\":null,\"players\":[]}")).orElseThrow();
        assertThat(none.running()).isFalse();
        assertThat(none.member(ALEX).tickets()).isZero();
    }

    @Test
    @DisplayName("somebody the snapshot does not list has no tickets, no link and no friends")
    void unknownMember() {
        Giveaway.Member nobody = Giveaway.parse(DataObject.fromJson(FULL)).orElseThrow().member(UUID.randomUUID());
        assertThat(nobody).isEqualTo(new Giveaway.Member(0, null, 0, java.util.List.of()));
    }

    @Test
    @DisplayName("a link that is null stays null, one that is not a plain https address is dropped, the rest is kept")
    void links() {
        String json = FULL.replace("\"https://hushbot.net/go/hyades/referral/open-the-end/alex\"", "null");
        assertThat(Giveaway.parse(DataObject.fromJson(json)).orElseThrow().member(ALEX).link()).isNull();

        String bad = FULL.replace("https://hushbot.net/go/hyades/referral/open-the-end/alex", "https://x.net/'><click:run_command:'/op me'>");
        Giveaway kept = Giveaway.parse(DataObject.fromJson(bad)).orElseThrow();
        assertThat(kept.member(ALEX).link()).isNull();
        assertThat(kept.member(ALEX).tickets()).isEqualTo(7);

        String http = FULL.replace("https://hyadesmc.net/giveaway/", "javascript:alert(1)");
        assertThat(Giveaway.parse(DataObject.fromJson(http)).orElseThrow().rulesUrl()).isNull();
    }

    @Test
    @DisplayName("what is not a giveaway is refused, not half stored")
    void refuses() {
        assertThat(Giveaway.parse(DataObject.fromJson("{\"id\":\"g\",\"type\":\"giveaway\"}"))).as("no title key").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"endsAt\": 1796396400000,", "")))).as("no end").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"endsAt\": 1796396400000", "\"endsAt\": \"soon\"")))).as("end is no number").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"rules\": { \"minutes\": 60, \"days\": 2, \"dayMinutes\": 10 },", "")))).as("no rules").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("00000000-0000-0000-0000-00000000000a\"", "nobody\"")))).as("bad uuid").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"tickets\": 7", "\"tickets\": \"many\"")))).as("tickets no number").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"players\": [", "\"players\": {} , \"x\": ["))))
                .as("players no list").isEmpty();
        assertThat(Giveaway.parse(DataObject.fromJson(FULL.replace("\"tickets\": 7", "\"tickets\": -1")))).as("negative").isEmpty();
    }

    @Test
    @DisplayName("what is stored reads back the same")
    void roundTrip() {
        Giveaway giveaway = Giveaway.parse(DataObject.fromJson(FULL)).orElseThrow();
        assertThat(Giveaway.parse(DataObject.fromJson(giveaway.toJson().toString()))).contains(giveaway);
        assertThat(Giveaway.parse(DataObject.fromJson(Giveaway.NONE.toJson().toString()))).contains(Giveaway.NONE);
    }
}
