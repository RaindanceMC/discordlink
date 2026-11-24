package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** The giveaway action of Hush's protocol: stored, answered, never done twice. */
class GiveawayActionTest {

    @TempDir
    Path folder;

    private final List<DataObject> answers = new ArrayList<>();
    private GiveawayStore store;
    private HushActions actions;

    private void start() {
        store = new GiveawayStore(folder.resolve("giveaway.json"), text -> { });
        actions = new HushActions(null, null, null, null, (String type, Supplier<DataObject> build) -> answers.add(build.get()),
                () -> 7L, Runnable::run, null, null, null, (kind, id, approve, by, reason) -> "not_here",
                (kind, player, name, link, options) -> new HushActions.Taken("not_here", "", ""),
                (ban, player, name, by, reason) -> "failed", store);
    }

    @Test
    @DisplayName("giveaway: the snapshot is stored and answered with ok and outcome stored")
    void stored() {
        start();
        actions.handle(DataObject.fromJson(GiveawayTest.FULL));

        assertThat(answers).hasSize(1);
        assertThat(answers.getFirst().getString("type")).isEqualTo("result");
        assertThat(answers.getFirst().getString("ref")).isEqualTo("g1");
        assertThat(answers.getFirst().getBoolean("ok")).isTrue();
        assertThat(answers.getFirst().getString("outcome")).isEqualTo("stored");
        assertThat(store.current().title()).isEqualTo("Open the End");
    }

    @Test
    @DisplayName("giveaway: title null is stored too, it ends the giveaway")
    void ended() {
        start();
        actions.handle(DataObject.fromJson(GiveawayTest.FULL));
        actions.handle(DataObject.fromJson("{\"id\":\"g2\",\"type\":\"giveaway\",\"title\":null,\"players\":[]}"));

        assertThat(answers.getLast().getString("outcome")).isEqualTo("stored");
        assertThat(store.current().running()).isFalse();
    }

    @Test
    @DisplayName("giveaway: a bad payload is refused with bad_giveaway and the stored one stays")
    void bad() {
        start();
        actions.handle(DataObject.fromJson(GiveawayTest.FULL));
        actions.handle(DataObject.fromJson(GiveawayTest.FULL.replace("\"g1\"", "\"g2\"").replace("\"tickets\": 7", "\"tickets\": \"x\"")));

        assertThat(answers.getLast().getBoolean("ok")).isFalse();
        assertThat(answers.getLast().getString("outcome")).isEqualTo("bad_giveaway");
        assertThat(store.current().member(GiveawayTest.ALEX).tickets()).isEqualTo(7);
    }

    @Test
    @DisplayName("giveaway: an id asked twice is answered again from memory and not stored twice")
    void idempotent() {
        start();
        actions.handle(DataObject.fromJson(GiveawayTest.FULL));
        store.replace(Giveaway.NONE);

        actions.handle(DataObject.fromJson(GiveawayTest.FULL));

        assertThat(answers).hasSize(2);
        assertThat(answers.get(1).getString("outcome")).isEqualTo("stored");
        assertThat(store.current().running()).as("the repeat did not run again").isFalse();
    }
}
