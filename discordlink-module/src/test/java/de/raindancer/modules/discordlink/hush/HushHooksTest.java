package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.moderation.feed.AnticheatAlert;
import de.raindancer.core.moderation.feed.ModerationFeed;
import de.raindancer.core.moderation.feed.ReportNotice;
import de.raindancer.core.moderation.punishment.Punishment;
import de.raindancer.core.moderation.punishment.PunishmentKind;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class HushHooksTest {

    private static final UUID ALICE = UUID.randomUUID();
    private final ModerationFeed moderation = new ModerationFeed();
    private final List<String> types = new ArrayList<>();
    private final List<Supplier<DataObject>> recipes = new ArrayList<>();

    private HushHooks connect() {
        return HushHooks.connect(moderation, (type, recipe) -> {
            types.add(type);
            recipes.add(recipe);
        }, () -> 1234L, id -> "Alice");
    }

    @Test
    @DisplayName("Core's alerts, punishments and reports each reach the feed as their own event type, stamped when they happened")
    void everythingArrives() {
        connect();
        moderation.anticheat(new AnticheatAlert(ALICE, "Alice", "Fly", 3, "d", 10, null, null));
        moderation.punished(new Punishment(null, ALICE, PunishmentKind.BAN, null, "x", Instant.EPOCH, null, null, null, null));
        moderation.lifted(new Punishment(null, ALICE, PunishmentKind.MUTE, null, "x", Instant.EPOCH, null, null, "ok", Instant.EPOCH));
        moderation.reported(new ReportNotice("R1", ALICE, "Alice", UUID.randomUUID(), "Bob", "why"));

        assertThat(types).containsExactly("anticheat", "punishment", "punishment", "report");
        assertThat(recipes.get(0).get().getLong("at")).isEqualTo(1234L);
        assertThat(recipes.get(1).get().getString("action")).isEqualTo("ban");
        assertThat(recipes.get(2).get().getString("action")).isEqualTo("unmute");
        assertThat(recipes.get(3).get().getString("type")).isEqualTo("report");
    }

    @Test
    @DisplayName("a fine is no event: its recipe yields nothing")
    void aFineIsSkipped() {
        connect();
        moderation.punished(new Punishment(null, ALICE, PunishmentKind.FINE, null, "x", Instant.EPOCH, null, null, null, null));
        assertThat(Optional.ofNullable(recipes.get(0).get())).isEmpty();
    }

    @Test
    @DisplayName("closed, it hears nothing")
    void closes() {
        connect().close();
        moderation.anticheat(new AnticheatAlert(ALICE, "Alice", "Fly", 3, "d", 10, null, null));
        assertThat(types).isEmpty();
    }

    @Test
    @DisplayName("a marker left behind means the last run did not shut down cleanly")
    void runMarker(@TempDir Path folder) {
        RunMarker marker = new RunMarker(folder.resolve("data/hush-running"));
        assertThat(marker.begin()).as("first ever start").isFalse();
        assertThat(marker.begin()).as("the last run never cleaned up").isTrue();
        marker.clean();
        assertThat(marker.begin()).as("after a clean stop").isFalse();
    }

    @Test
    @DisplayName("admin mode switches arrive as admin events: the way out carries the duration, the way in none")
    void adminMode() {
        connect();
        moderation.adminMode(new de.raindancer.core.moderation.feed.AdminModeNotice(ALICE, true, 0));
        moderation.adminMode(new de.raindancer.core.moderation.feed.AdminModeNotice(ALICE, false, 90_000));

        assertThat(types).containsExactly("admin", "admin");
        DataObject in = recipes.get(0).get();
        DataObject out = recipes.get(1).get();
        assertThat(in.getString("state")).isEqualTo("enter");
        assertThat(in.hasKey("duration")).isFalse();
        assertThat(in.getObject("player").getString("name")).isEqualTo("Alice");
        assertThat(out.getString("state")).isEqualTo("leave");
        assertThat(out.getLong("duration")).isEqualTo(90_000L);
    }
}
