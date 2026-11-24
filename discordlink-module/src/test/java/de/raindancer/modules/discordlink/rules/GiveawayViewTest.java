package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.hush.Giveaway;
import de.raindancer.modules.discordlink.rules.GiveawayView.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GiveawayViewTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final long NOW = 1_796_000_000_000L;
    private static final long ENDS = NOW + 2 * 86_400_000L + 3 * 3_600_000L;
    private static final Giveaway.Rules RULES = new Giveaway.Rules(60, 2, 10);
    private static final String LINK = "https://hushbot.net/go/hyades/referral/open-the-end/alex";

    private final GiveawayView view = new GiveawayView();

    private static Giveaway running(Giveaway.Member alex) {
        return new Giveaway("Open the End", ENDS, "https://hyadesmc.net/giveaway/", RULES, Map.of(ALEX, alex));
    }

    private List<Line> lines(Giveaway giveaway, boolean linked, long now) {
        return view.lines(giveaway, ALEX, linked, now, url -> "BUTTON(" + url + ")");
    }

    private static List<String> keys(List<Line> lines) {
        return lines.stream().map(Line::key).toList();
    }

    @Test
    @DisplayName("no snapshot or title null: only that no giveaway is running")
    void none() {
        assertThat(keys(lines(Giveaway.NONE, true, NOW))).containsExactly("discordlink.giveaway.none");
        assertThat(keys(lines(Giveaway.NONE, false, NOW))).containsExactly("discordlink.giveaway.none");
    }

    @Test
    @DisplayName("running: title and time left, tickets, the referral link, the rules link, then the friends")
    void running() {
        Giveaway giveaway = running(new Giveaway.Member(7, LINK, 1, List.of(
                new Giveaway.Friend("Bob", 35, 1, false), new Giveaway.Friend("Cy", 80, 3, true))));

        List<Line> lines = lines(giveaway, true, NOW);

        assertThat(keys(lines)).containsExactly("discordlink.giveaway.head", "discordlink.giveaway.tickets",
                "discordlink.giveaway.link", "discordlink.giveaway.rules", "discordlink.giveaway.friends",
                "discordlink.giveaway.friend-progress", "discordlink.giveaway.friend-counts");
        assertThat(lines.get(0).value("title")).isEqualTo("Open the End");
        assertThat(lines.get(0).value("left")).isEqualTo("2d 3h");
        assertThat(lines.get(1).value("tickets")).isEqualTo("7");
        assertThat(lines.get(2).value("button")).isEqualTo("BUTTON(" + LINK + ")");
        assertThat(lines.get(3).value("button")).isEqualTo("BUTTON(https://hyadesmc.net/giveaway/)");
        assertThat(lines.get(4).value("counted")).isEqualTo("1");
        Line bob = lines.get(5);
        assertThat(bob.value("name")).isEqualTo("Bob");
        assertThat(bob.value("minutes")).isEqualTo("35");
        assertThat(bob.value("need-minutes")).isEqualTo("60");
        assertThat(bob.value("days")).isEqualTo("1");
        assertThat(bob.value("need-days")).isEqualTo("2");
        assertThat(lines.get(6).value("name")).isEqualTo("Cy");
    }

    @Test
    @DisplayName("without a link, or when the player is not in the snapshot, it says how to get one")
    void noLink() {
        assertThat(keys(lines(running(new Giveaway.Member(0, null, 0, List.of())), true, NOW)))
                .contains("discordlink.giveaway.no-link", "discordlink.giveaway.no-friends")
                .doesNotContain("discordlink.giveaway.link");
        assertThat(keys(lines(new Giveaway("T", ENDS, null, RULES, Map.of()), true, NOW)))
                .contains("discordlink.giveaway.tickets", "discordlink.giveaway.no-link")
                .doesNotContain("discordlink.giveaway.rules");
    }

    @Test
    @DisplayName("a player who is not linked is told to link first, and sees nothing of the rest")
    void notLinked() {
        Giveaway giveaway = running(new Giveaway.Member(7, LINK, 1, List.of(new Giveaway.Friend("Bob", 1, 1, false))));
        assertThat(keys(lines(giveaway, false, NOW))).containsExactly("discordlink.giveaway.head",
                "discordlink.giveaway.not-linked", "discordlink.giveaway.rules");
    }

    @Test
    @DisplayName("after the end it says so instead of a time left")
    void ended() {
        List<Line> lines = lines(running(new Giveaway.Member(3, null, 0, List.of())), true, ENDS + 1);
        assertThat(lines.getFirst().key()).isEqualTo("discordlink.giveaway.head-ended");
    }

    @Test
    @DisplayName("at most ten friends are listed, the rest only counted")
    void tenFriends() {
        List<Giveaway.Friend> friends = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            friends.add(new Giveaway.Friend("F" + i, i, 0, false));
        }
        List<Line> lines = lines(running(new Giveaway.Member(0, LINK, 0, friends)), true, NOW);

        assertThat(keys(lines).stream().filter(k -> k.equals("discordlink.giveaway.friend-progress"))).hasSize(10);
        assertThat(lines.getLast().key()).isEqualTo("discordlink.giveaway.friends-more");
        assertThat(lines.getLast().value("more")).isEqualTo("3");
        assertThat(lines.get(lines.size() - 2).value("name")).isEqualTo("F9");
    }

    @Test
    @DisplayName("exactly ten friends have no more line")
    void exactlyTen() {
        List<Giveaway.Friend> friends = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            friends.add(new Giveaway.Friend("F" + i, i, 0, false));
        }
        assertThat(keys(lines(running(new Giveaway.Member(0, LINK, 0, friends)), true, NOW)))
                .doesNotContain("discordlink.giveaway.friends-more");
    }
}
