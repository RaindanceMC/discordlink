package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

/** The giveaway snapshot in memory, and the last one on disk so a restart still has something to show. */
public final class GiveawayStore {

    private final Path file;
    private final Consumer<String> warn;
    private volatile Giveaway current = Giveaway.NONE;

    public GiveawayStore(Path file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
        load();
    }

    /** Memory only, for a server that has nowhere to save it. */
    public GiveawayStore() {
        this(null, text -> { });
    }

    public Giveaway current() {
        return current;
    }

    public synchronized void replace(Giveaway giveaway) {
        current = giveaway;
        if (file == null) {
            return;
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temp, giveaway.toJson().toString(), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failed) {
            warn.accept("Could not save the giveaway: " + failed.getClass().getSimpleName());
        }
    }

    private void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            current = Giveaway.parse(DataObject.fromJson(Files.readString(file, StandardCharsets.UTF_8))).orElseThrow();
        } catch (IOException | RuntimeException unreadable) {
            warn.accept("The saved giveaway could not be read, there is none until Hush sends it again.");
        }
    }
}
