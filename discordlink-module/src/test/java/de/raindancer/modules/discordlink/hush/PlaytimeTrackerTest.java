package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlaytimeTrackerTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    @TempDir
    Path folder;

    private final AtomicLong clock = new AtomicLong(at("2026-11-28T18:00:00"));
    private final Map<UUID, PlaytimeCounter.Who> linked = new HashMap<>();
    private final Set<UUID> away = new HashSet<>();
    private final AtomicBoolean awayKnown = new AtomicBoolean(true);
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    private final List<DataObject> events = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private ZoneId zone = BERLIN;
    private PlaytimeTracker tracker;

    private static long at(String berlinLocal) {
        return java.time.LocalDateTime.parse(berlinLocal).atZone(BERLIN).toInstant().toEpochMilli();
    }

    @BeforeEach
    void setUp() {
        linked.put(ALEX, new PlaytimeCounter.Who("Alex", "111"));
        linked.put(BOB, new PlaytimeCounter.Who("Bob", "222"));
        firstSeen.put(ALEX, 1_700_000_000_000L);
        tracker = tracker();
    }

    private PlaytimeTracker tracker() {
        return new PlaytimeTracker(id -> Optional.ofNullable(linked.get(id)), awayKnown::get, away::contains, () -> zone,
                enabled::get, clock::get, (type, build) -> {
                    assertThat(type).isEqualTo("playtime");
                    DataObject event = build.get();
                    if (event != null) {
                        events.add(event);
                    }
                }, id -> firstSeen.getOrDefault(id, 0L), folder.resolve("playtime.json"), warnings::add);
    }

    private void minutes(int count) {
        for (int i = 0; i < count; i++) {
            clock.addAndGet(60_000);
            tracker.tick();
        }
    }

    private static List<DataObject> entries(DataObject event) {
        DataArray array = event.getArray("entries");
        List<DataObject> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            out.add(array.getObject(i));
        }
        return out;
    }

    @Test
    @DisplayName("nothing is sent for the first four minutes, then one event with the day and the minutes so far")
    void sendsEveryFiveMinutes() {
        tracker.joined(ALEX);
        minutes(4);
        assertThat(events).isEmpty();
        minutes(1);

        assertThat(events).hasSize(1);
        DataObject event = events.getFirst();
        assertThat(event.getString("type")).isEqualTo("playtime");
        assertThat(event.getString("day")).isEqualTo("2026-11-28");
        assertThat(event.getLong("at")).isEqualTo(clock.get());
        DataObject entry = entries(event).getFirst();
        assertThat(entry.getObject("player").getString("name")).isEqualTo("Alex");
        assertThat(entry.getObject("player").getString("uuid")).isEqualTo(ALEX.toString());
        assertThat(entry.getString("discord")).isEqualTo("111");
        assertThat(entry.getInt("minutes")).isEqualTo(5);
        assertThat(entry.getLong("firstSeen")).isEqualTo(1_700_000_000_000L);
    }

    @Test
    @DisplayName("the next event carries only players whose minutes changed")
    void onlyChanged() {
        tracker.joined(ALEX);
        tracker.joined(BOB);
        minutes(5);
        tracker.left(BOB);
        events.clear();
        minutes(5);

        assertThat(events).hasSize(1);
        assertThat(entries(events.getFirst())).extracting(e -> e.getObject("player").getString("name")).containsExactly("Alex");
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(10);
    }

    @Test
    @DisplayName("a player who is not linked is neither counted nor sent")
    void unlinkedIsIgnored() {
        tracker.joined(STRANGER);
        tracker.joined(ALEX);
        minutes(5);
        tracker.left(STRANGER);

        assertThat(events).hasSize(1);
        assertThat(entries(events.getFirst())).hasSize(1);
        assertThat(Files.exists(folder.resolve("playtime.json")) ? read() : "").doesNotContain(STRANGER.toString());
    }

    @Test
    @DisplayName("an away player does not count while somebody watches for away, and does when nobody does")
    void away() {
        tracker.joined(ALEX);
        away.add(ALEX);
        minutes(5);
        assertThat(events).isEmpty();

        awayKnown.set(false);
        minutes(5);
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(5);
    }

    @Test
    @DisplayName("leaving sends the player's minutes at once, without waiting for the five minutes")
    void quitSendsAtOnce() {
        tracker.joined(ALEX);
        minutes(2);
        tracker.left(ALEX);

        assertThat(events).hasSize(1);
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(2);
        minutes(3);
        assertThat(events).hasSize(1);
    }

    @Test
    @DisplayName("a player who was online only before being counted sends nothing when they leave")
    void quitWithoutMinutes() {
        tracker.joined(ALEX);
        tracker.left(ALEX);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("firstSeen is null when nobody knows it")
    void firstSeenUnknown() {
        tracker.joined(BOB);
        minutes(5);
        assertThat(entries(events.getFirst()).getFirst().isNull("firstSeen")).isTrue();
        assertThat(entries(events.getFirst()).getFirst().hasKey("firstSeen")).isTrue();
    }

    @Test
    @DisplayName("at midnight in the setting's zone the old day's final values go once and counting starts at zero")
    void midnight() {
        clock.set(at("2026-11-28T23:56:30"));
        tracker.joined(ALEX);
        minutes(3);
        events.clear();
        // 23:59:30 -> 00:00:30: the tick after midnight
        minutes(1);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getString("day")).isEqualTo("2026-11-28");
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(3);

        minutes(1);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getString("day")).isEqualTo("2026-11-29");
        assertThat(entries(events.get(1)).getFirst().getInt("minutes")).isEqualTo(2);
    }

    @Test
    @DisplayName("the day follows the zone from the setting, not the server's")
    void zoneFromSetting() {
        zone = ZoneId.of("Asia/Tokyo");
        clock.set(at("2026-11-28T18:00:00"));
        tracker.joined(ALEX);
        minutes(5);
        assertThat(events.getFirst().getString("day")).isEqualTo("2026-11-29");
    }

    @Test
    @DisplayName("more than 200 players are split into events of at most 200")
    void split() {
        for (int i = 0; i < 201; i++) {
            UUID id = new UUID(1, i);
            linked.put(id, new PlaytimeCounter.Who("P" + i, String.valueOf(1000 + i)));
            tracker.joined(id);
        }
        minutes(5);

        assertThat(events).hasSize(2);
        assertThat(entries(events.get(0))).hasSize(200);
        assertThat(entries(events.get(1))).hasSize(1);
    }

    @Test
    @DisplayName("switched off: nothing is counted or sent")
    void switchedOff() {
        enabled.set(false);
        tracker.joined(ALEX);
        minutes(5);
        enabled.set(true);
        minutes(1);
        tracker.left(ALEX);
        assertThat(events).hasSize(1);
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(1);
    }

    @Test
    @DisplayName("an account that gets unlinked stops being sent")
    void unlinkedLater() {
        tracker.joined(ALEX);
        minutes(3);
        linked.remove(ALEX);
        tracker.forget(ALEX);
        minutes(2);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("today's counts survive a restart and go on from where they were")
    void restart() {
        tracker.joined(ALEX);
        minutes(3);
        tracker.close();

        PlaytimeTracker again = tracker();
        again.joined(ALEX);
        clock.addAndGet(60_000);
        again.tick();
        again.left(ALEX);

        assertThat(entries(events.getLast()).getFirst().getInt("minutes")).isEqualTo(4);
        assertThat(events.getLast().getString("day")).isEqualTo("2026-11-28");
    }

    @Test
    @DisplayName("counts left from yesterday are sent as yesterday's final values, never added to today")
    void restartNextDay() {
        tracker.joined(ALEX);
        minutes(3);
        tracker.close();

        clock.set(at("2026-11-29T09:00:00"));
        PlaytimeTracker again = tracker();
        again.joined(ALEX);
        clock.addAndGet(60_000);
        again.tick();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getString("day")).isEqualTo("2026-11-28");
        assertThat(entries(events.getFirst()).getFirst().getInt("minutes")).isEqualTo(3);
        again.left(ALEX);
        assertThat(events.getLast().getString("day")).isEqualTo("2026-11-29");
        assertThat(entries(events.getLast()).getFirst().getInt("minutes")).isEqualTo(1);
    }

    @Test
    @DisplayName("a broken file is ignored with a warning, counting starts empty")
    void brokenFile() throws Exception {
        Files.writeString(folder.resolve("playtime.json"), "{ not json");
        PlaytimeTracker again = tracker();
        again.joined(ALEX);
        clock.addAndGet(60_000);
        again.tick();
        again.left(ALEX);
        assertThat(warnings).hasSize(1);
        assertThat(entries(events.getLast()).getFirst().getInt("minutes")).isEqualTo(1);
    }

    private String read() {
        try {
            return Files.readString(folder.resolve("playtime.json"));
        } catch (java.io.IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
