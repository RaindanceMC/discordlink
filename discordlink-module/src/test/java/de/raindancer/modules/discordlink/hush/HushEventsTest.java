package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.moderation.feed.AnticheatAlert;
import de.raindancer.core.moderation.feed.PunishmentNotice;
import de.raindancer.core.moderation.feed.ReportNotice;
import de.raindancer.core.moderation.punishment.Punishment;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The JSON per event type, as docs/MINECRAFT.md of the bot fixes it. */
class HushEventsTest {

    private static final UUID ALICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BOB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID MOD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Map<UUID, String> NAMES = Map.of(ALICE, "Alice", BOB, "Bob", MOD, "Mod");
    private static final long NOW = 1_731_100_000_000L;

    private static Punishment given(PunishmentKind kind, UUID moderator, Duration length) {
        Instant at = Instant.ofEpochMilli(NOW);
        return new Punishment("case-9", ALICE, kind, moderator, "x-ray", at, length == null ? null : at.plus(length),
                null, null, null);
    }

    private static Optional<DataObject> punishment(Punishment p, boolean lifted) {
        return HushEvents.punishment(NOW, new PunishmentNotice(p, lifted), id -> NAMES.getOrDefault(id, id.toString()));
    }

    @Test
    @DisplayName("anticheat: player, check, vl as a number, client, brand, ping, verbose")
    void anticheat() {
        DataObject e = HushEvents.anticheat(NOW,
                new AnticheatAlert(ALICE, "Alice", "Simulation", 7.5, "off by 0.3", 42, "26.3", "fabric"));

        assertThat(e.getString("type")).isEqualTo("anticheat");
        assertThat(e.getLong("at")).isEqualTo(NOW);
        assertThat(e.getObject("player").getString("name")).isEqualTo("Alice");
        assertThat(e.getObject("player").getString("uuid")).isEqualTo(ALICE.toString());
        assertThat(e.getString("check")).isEqualTo("Simulation");
        assertThat(e.getDouble("vl")).isEqualTo(7.5);
        assertThat(e.getString("client")).isEqualTo("26.3");
        assertThat(e.getString("brand")).isEqualTo("fabric");
        assertThat(e.getInt("ping")).isEqualTo(42);
        assertThat(e.getString("verbose")).isEqualTo("off by 0.3");
    }

    @Test
    @DisplayName("anticheat: unknown client and brand are left out, verbose is cut at 300 characters")
    void anticheatOptionalAndClamped() {
        DataObject e = HushEvents.anticheat(NOW,
                new AnticheatAlert(ALICE, "Alice", "Fly", 1, "x".repeat(1000), 0, null, null));

        assertThat(e.hasKey("client")).isFalse();
        assertThat(e.hasKey("brand")).isFalse();
        assertThat(e.getString("verbose")).hasSize(300);
    }

    @Test
    @DisplayName("a ban without an end is ban, with an end tempban and its length in ms; staff by name")
    void banAndTempban() {
        DataObject ban = punishment(given(PunishmentKind.BAN, MOD, null), false).orElseThrow();
        assertThat(ban.getString("type")).isEqualTo("punishment");
        assertThat(ban.getString("action")).isEqualTo("ban");
        assertThat(ban.getObject("player").getString("name")).isEqualTo("Alice");
        assertThat(ban.getString("staff")).isEqualTo("Mod");
        assertThat(ban.getString("reason")).isEqualTo("x-ray");
        assertThat(ban.getString("id")).isEqualTo("case-9");
        assertThat(ban.hasKey("duration")).as("only temp ones have a duration").isFalse();

        DataObject temp = punishment(given(PunishmentKind.BAN, MOD, Duration.ofDays(7)), false).orElseThrow();
        assertThat(temp.getString("action")).isEqualTo("tempban");
        assertThat(temp.getLong("duration")).isEqualTo(Duration.ofDays(7).toMillis());
    }

    @Test
    @DisplayName("mute, tempmute, kick, warn; no moderator is the Console")
    void otherKinds() {
        assertThat(punishment(given(PunishmentKind.MUTE, null, null), false).orElseThrow().getString("action")).isEqualTo("mute");
        assertThat(punishment(given(PunishmentKind.MUTE, MOD, Duration.ofHours(1)), false).orElseThrow().getString("action"))
                .isEqualTo("tempmute");
        assertThat(punishment(given(PunishmentKind.KICK, MOD, null), false).orElseThrow().getString("action")).isEqualTo("kick");
        DataObject warn = punishment(given(PunishmentKind.WARNING, null, null), false).orElseThrow();
        assertThat(warn.getString("action")).isEqualTo("warn");
        assertThat(warn.getString("staff")).isEqualTo("Console");
    }

    @Test
    @DisplayName("lifting a ban or mute is unban or unmute, by whoever lifted it and why")
    void lifts() {
        Punishment lifted = new Punishment("case-9", ALICE, PunishmentKind.BAN, MOD, "x-ray", Instant.ofEpochMilli(NOW), null,
                BOB, "appeal accepted", Instant.ofEpochMilli(NOW + 5));
        DataObject unban = punishment(lifted, true).orElseThrow();
        assertThat(unban.getString("action")).isEqualTo("unban");
        assertThat(unban.getString("staff")).isEqualTo("Bob");
        assertThat(unban.getString("reason")).isEqualTo("appeal accepted");
        assertThat(unban.hasKey("duration")).isFalse();

        Punishment mute = new Punishment("m1", ALICE, PunishmentKind.MUTE, MOD, "spam", Instant.ofEpochMilli(NOW), null,
                null, "sorry", Instant.ofEpochMilli(NOW + 5));
        assertThat(punishment(mute, true).orElseThrow().getString("action")).isEqualTo("unmute");
        assertThat(punishment(mute, true).orElseThrow().getString("staff")).isEqualTo("Console");
    }

    @Test
    @DisplayName("freezes and fines are not part of the feed; neither is a mute copied from the linked server")
    void notEverythingIsTold() {
        assertThat(punishment(given(PunishmentKind.FREEZE, MOD, null), false)).isEmpty();
        assertThat(punishment(given(PunishmentKind.FINE, MOD, null), false)).isEmpty();
    }

    @Test
    @DisplayName("report: reporter, target, reason")
    void report() {
        DataObject e = HushEvents.report(NOW, new ReportNotice("R7", ALICE, "Alice", BOB, "Bob", "flying"));
        assertThat(e.getString("type")).isEqualTo("report");
        assertThat(e.getObject("reporter").getString("name")).isEqualTo("Alice");
        assertThat(e.getObject("reporter").getString("uuid")).isEqualTo(ALICE.toString());
        assertThat(e.getObject("target").getString("name")).isEqualTo("Bob");
        assertThat(e.getString("reason")).isEqualTo("flying");
    }

    @Test
    @DisplayName("server: state and an optional detail")
    void server() {
        DataObject start = HushEvents.server(NOW, "start", null);
        assertThat(start.getString("type")).isEqualTo("server");
        assertThat(start.getString("state")).isEqualTo("start");
        assertThat(start.hasKey("detail")).isFalse();
        assertThat(HushEvents.server(NOW, "crash", "did not shut down cleanly").getString("detail")).isEqualTo("did not shut down cleanly");
    }

    @Test
    @DisplayName("the body is {v:2, server, events:[…]} and says what the server lets in when asked to")
    void body() {
        String json = HushEvents.body("beta-smp", List.of(HushEvents.server(NOW, "stop", null), HushEvents.server(NOW, "start", null)));
        DataObject parsed = DataObject.fromJson(json);
        assertThat(parsed.getInt("v")).isEqualTo(2);
        assertThat(parsed.hasKey("access")).isFalse();
        assertThat(DataObject.fromJson(HushEvents.body("x", List.of(), "whitelist")).getString("access")).isEqualTo("whitelist");
        assertThat(parsed.getString("server")).isEqualTo("beta-smp");
        DataArray events = parsed.getArray("events");
        assertThat(events.length()).isEqualTo(2);
        assertThat(events.getObject(0).getString("state")).isEqualTo("stop");
    }

    @Test
    @DisplayName("v2 events: result, link, links and groups carry what Hush needs and nothing else")
    void v2Events() {
        java.util.UUID id = java.util.UUID.randomUUID();
        DataObject result = HushEvents.result(NOW, "a1", true, "linked", DataObject.empty().put("x", 1));
        assertThat(result.getString("type")).isEqualTo("result");
        assertThat(result.getString("ref")).isEqualTo("a1");
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(HushEvents.result(NOW, "a2", false, "wrong_code", null).hasKey("data")).isFalse();

        DataObject link = HushEvents.link(NOW, "linked", id, "Alex", "111");
        assertThat(link.getString("action")).isEqualTo("linked");
        assertThat(link.getString("discord")).isEqualTo("111");
        assertThat(link.getObject("player").getString("uuid")).isEqualTo(id.toString());

        DataObject groups = HushEvents.groups(NOW, id, "Alex", "111", List.of("mod", "admin"));
        assertThat(groups.getArray("groups").toList()).containsExactly("admin", "mod");

        DataObject links = HushEvents.links(NOW, List.of(HushEvents.linkEntry(id, "Alex", "111")));
        assertThat(links.getArray("entries").length()).isEqualTo(1);
    }

    private static de.raindancer.core.moderation.feed.AdminSessionRecap recap(List<de.raindancer.core.moderation.feed.AdminSessionRecap.Line> commands) {
        return new de.raindancer.core.moderation.feed.AdminSessionRecap(NOW, NOW + 60_000, commands, 2,
                List.of(new de.raindancer.core.moderation.feed.AdminSessionRecap.Line(NOW + 5_000, "banned Bob — x-ray")), 0,
                Map.of("STONE", 3), Map.of(), 1, List.of("world_nether"), List.of("SURVIVAL → CREATIVE"));
    }

    private static DataObject left(de.raindancer.core.moderation.feed.AdminSessionRecap recap) {
        return HushEvents.adminMode(NOW, new de.raindancer.core.moderation.feed.AdminModeNotice(MOD, false, 60_000, recap),
                id -> NAMES.getOrDefault(id, id.toString()));
    }

    @Test
    @DisplayName("leaving admin mode carries the recap: commands in order with their time, audited actions, changes")
    void adminRecap() {
        DataObject event = left(recap(List.of(
                new de.raindancer.core.moderation.feed.AdminSessionRecap.Line(NOW + 1_000, "/gamemode creative"),
                new de.raindancer.core.moderation.feed.AdminSessionRecap.Line(NOW + 2_000, "/ban Bob x-ray"))));

        DataObject recap = event.getObject("recap");
        assertThat(recap.getLong("start")).isEqualTo(NOW);
        assertThat(recap.getLong("end")).isEqualTo(NOW + 60_000);
        DataArray commands = recap.getArray("commands");
        assertThat(commands.length()).isEqualTo(2);
        assertThat(commands.getObject(0).getString("text")).isEqualTo("/gamemode creative");
        assertThat(commands.getObject(1).getLong("at")).isEqualTo(NOW + 2_000);
        assertThat(recap.getInt("commandsOmitted")).isEqualTo(2);
        assertThat(recap.getArray("actions").getObject(0).getString("text")).isEqualTo("banned Bob — x-ray");
        assertThat(recap.getObject("placed").getInt("STONE")).isEqualTo(3);
        assertThat(recap.getObject("broken").keys()).isEmpty();
        assertThat(recap.getInt("teleports")).isEqualTo(1);
        assertThat(recap.getArray("worlds").getString(0)).isEqualTo("world_nether");
        assertThat(recap.getArray("gamemodes").getString(0)).isEqualTo("SURVIVAL → CREATIVE");
    }

    @Test
    @DisplayName("without a recap the leave event is what it always was")
    void adminWithoutRecap() {
        assertThat(left(null).hasKey("recap")).isFalse();
    }

    @Test
    @DisplayName("a huge recap is cut to fit one request to Hush; what is cut is counted as omitted")
    void adminRecapFits() {
        List<de.raindancer.core.moderation.feed.AdminSessionRecap.Line> many = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            many.add(new de.raindancer.core.moderation.feed.AdminSessionRecap.Line(NOW + i, "/say " + "x".repeat(250)));
        }

        DataObject event = left(recap(many));

        assertThat(event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThan(HushEvents.RECAP_BUDGET + 2_000);
        DataObject recap = event.getObject("recap");
        int kept = recap.getArray("commands").length();
        assertThat(kept).isBetween(50, 499);
        assertThat(recap.getInt("commandsOmitted")).isEqualTo(500 - kept + 2);
    }
}
