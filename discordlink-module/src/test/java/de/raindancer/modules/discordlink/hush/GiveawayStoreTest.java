package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GiveawayStoreTest {

    @TempDir
    Path folder;

    private final List<String> warnings = new ArrayList<>();

    private GiveawayStore store() {
        return new GiveawayStore(folder.resolve("giveaway.json"), warnings::add);
    }

    private static Giveaway giveaway() {
        return Giveaway.parse(DataObject.fromJson(GiveawayTest.FULL)).orElseThrow();
    }

    @Test
    @DisplayName("before anything arrived there is no giveaway")
    void startsEmpty() {
        assertThat(store().current().running()).isFalse();
    }

    @Test
    @DisplayName("the last snapshot replaces the one before, and a restart still shows it")
    void keptOnDisk() {
        GiveawayStore first = store();
        first.replace(Giveaway.NONE);
        first.replace(giveaway());
        assertThat(first.current()).isEqualTo(giveaway());

        assertThat(store().current()).isEqualTo(giveaway());
    }

    @Test
    @DisplayName("an ended giveaway (title null) replaces a running one, also after a restart")
    void endedReplaces() {
        GiveawayStore first = store();
        first.replace(giveaway());
        first.replace(Giveaway.NONE);
        assertThat(first.current().running()).isFalse();
        assertThat(store().current().running()).isFalse();
    }

    @Test
    @DisplayName("a broken file is a warning and no giveaway, not a failure to start")
    void brokenFile() throws Exception {
        Files.writeString(folder.resolve("giveaway.json"), "{ nope");
        assertThat(store().current().running()).isFalse();
        assertThat(warnings).hasSize(1);
    }

    @Test
    @DisplayName("an unwritable place keeps the snapshot in memory and warns once")
    void unwritable() throws Exception {
        Path blocked = Files.createFile(folder.resolve("blocked"));
        GiveawayStore store = new GiveawayStore(blocked.resolve("giveaway.json"), warnings::add);
        store.replace(giveaway());
        assertThat(store.current()).isEqualTo(giveaway());
        assertThat(warnings).hasSize(1);
    }
}
