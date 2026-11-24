package de.raindancer.modules.discordlink.hush;

import de.raindancer.modules.discordlink.hush.PlaytimeCounter.Tracked;
import de.raindancer.modules.discordlink.hush.PlaytimeCounter.Who;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaytimeCounterTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);
    private static final Map<UUID, Who> ALEX_ONLY = Map.of(ALEX, new Who("Alex", "111"));
    private static final Map<UUID, Who> BOTH = Map.of(ALEX, new Who("Alex", "111"), BOB, new Who("Bob", "222"));

    private final PlaytimeCounter counter = new PlaytimeCounter();

    @Test
    @DisplayName("every tick adds one minute for each active player, and the value is the day's total")
    void countsMinutes() {
        counter.minute(MONDAY, ALEX_ONLY);
        counter.minute(MONDAY, BOTH);
        counter.minute(MONDAY, ALEX_ONLY);

        var batch = counter.changed().orElseThrow();
        assertThat(batch.day()).isEqualTo(MONDAY);
        assertThat(batch.entries()).containsExactlyInAnyOrder(new Tracked(ALEX, "Alex", "111", 3), new Tracked(BOB, "Bob", "222", 1));
    }

    @Test
    @DisplayName("only what changed since it was last handed out comes again")
    void onlyChanges() {
        counter.minute(MONDAY, BOTH);
        counter.changed();
        assertThat(counter.changed()).isEmpty();

        counter.minute(MONDAY, ALEX_ONLY);
        assertThat(counter.changed().orElseThrow().entries()).containsExactly(new Tracked(ALEX, "Alex", "111", 2));
    }

    @Test
    @DisplayName("a player who left is handed out once, with the minutes they have")
    void quit() {
        counter.minute(MONDAY, BOTH);
        counter.minute(MONDAY, ALEX_ONLY);

        assertThat(counter.quit(BOB)).contains(new Tracked(BOB, "Bob", "222", 1));
        assertThat(counter.quit(BOB)).isEmpty();
        assertThat(counter.changed().orElseThrow().entries()).extracting(Tracked::player).containsExactly(ALEX);
        assertThat(counter.quit(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("a new day hands out the old day's final values once and starts again at zero")
    void midnight() {
        counter.minute(MONDAY, ALEX_ONLY);
        counter.minute(MONDAY, ALEX_ONLY);
        counter.changed();
        counter.minute(MONDAY, ALEX_ONLY);

        var ended = counter.minute(MONDAY.plusDays(1), BOB_ONLY()).orElseThrow();
        assertThat(ended.day()).isEqualTo(MONDAY);
        assertThat(ended.entries()).containsExactly(new Tracked(ALEX, "Alex", "111", 3));

        var next = counter.changed().orElseThrow();
        assertThat(next.day()).isEqualTo(MONDAY.plusDays(1));
        assertThat(next.entries()).containsExactly(new Tracked(BOB, "Bob", "222", 1));
        assertThat(counter.minute(MONDAY.plusDays(1), BOB_ONLY())).isEmpty();
    }

    @Test
    @DisplayName("a day that was handed out completely is not handed out again at midnight")
    void nothingNewAtMidnight() {
        counter.minute(MONDAY, ALEX_ONLY);
        counter.changed();
        assertThat(counter.minute(MONDAY.plusDays(1), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("a clock that goes back (zone changed) keeps counting the same day")
    void clockBack() {
        counter.minute(MONDAY, ALEX_ONLY);
        assertThat(counter.minute(MONDAY.minusDays(1), ALEX_ONLY)).isEmpty();
        assertThat(counter.changed().orElseThrow().entries()).extracting(Tracked::minutes).containsExactly(2);
    }

    @Test
    @DisplayName("forgetting a player drops them: they are not handed out any more")
    void forget() {
        counter.minute(MONDAY, BOTH);
        counter.forget(BOB);
        assertThat(counter.changed().orElseThrow().entries()).extracting(Tracked::player).containsExactly(ALEX);
    }

    @Test
    @DisplayName("restored counts continue, and none of them counts as already sent")
    void restore() {
        PlaytimeCounter restored = new PlaytimeCounter(MONDAY, List.of(new Tracked(ALEX, "Alex", "111", 40)));
        restored.minute(MONDAY, ALEX_ONLY);
        assertThat(restored.changed().orElseThrow().entries()).containsExactly(new Tracked(ALEX, "Alex", "111", 41));
        assertThat(restored.snapshot().entries()).containsExactly(new Tracked(ALEX, "Alex", "111", 41));
    }

    @Test
    @DisplayName("a name or Discord id that changed is the one handed out")
    void latestWho() {
        counter.minute(MONDAY, ALEX_ONLY);
        counter.minute(MONDAY, Map.of(ALEX, new Who("Alexander", "999")));
        assertThat(counter.changed().orElseThrow().entries()).containsExactly(new Tracked(ALEX, "Alexander", "999", 2));
    }

    private static Map<UUID, Who> BOB_ONLY() {
        return Map.of(BOB, new Who("Bob", "222"));
    }
}
