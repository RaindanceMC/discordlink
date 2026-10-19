package de.raindancer.modules.discordlink;

import de.raindancer.modules.api.WordingContract;

import java.nio.file.Path;

/**
 * The wording rules, kept by this module. See {@code WordingContract} for what is actually
 * checked; this only says where to look.
 */
class WordingTest implements WordingContract {

    @Override
    public Path moduleSource() {
        return Path.of("src/main/java/de/raindancer/modules/discordlink");
    }

    @Override
    public Path messagesFile() {
        return Path.of("src/main/resources/de/raindancer/modules/discordlink/messages.yml");
    }

    @Override
    public int fewestWordingLines() {
        return 20;
    }

    @Override
    public int fewestSourceFiles() {
        return 20;
    }
}
