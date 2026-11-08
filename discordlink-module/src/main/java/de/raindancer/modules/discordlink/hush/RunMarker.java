package de.raindancer.modules.discordlink.hush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A file that exists while the server runs. One found at startup means the last run never reached its shutdown:
 * a crash, or a kill. Best effort: a folder that cannot be written just means no crash reports.
 */
public final class RunMarker {

    private final Path file;

    public RunMarker(Path file) {
        this.file = file;
    }

    /** Marks this run as started. @return whether the run before it ended without a clean shutdown */
    public boolean begin() {
        boolean unclean = Files.exists(file);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, String.valueOf(System.currentTimeMillis()));
        } catch (IOException | RuntimeException unwritable) {
            // no crash detection on this server
        }
        return unclean;
    }

    public void clean() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException ignored) {
            // the next start will think it crashed, which is the lesser harm
        }
    }
}
