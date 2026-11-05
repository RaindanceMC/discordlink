package de.raindancer.modules.discordlink.service;

import de.raindancer.core.data.sql.CoreSchema;
import de.raindancer.core.data.sql.Database;
import de.raindancer.core.moderation.whitelist.NetworkWhitelist;
import de.raindancer.core.moderation.whitelist.WhitelistTable;
import de.raindancer.core.testkit.TestDatabases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Discord's /link and /whitelist write the network's list — the one RainsNetworkProxy checks at the door. */
@Tag("database")
class CoreWhitelistTest {

    private static final UUID ALEX = UUID.randomUUID();

    @TempDir
    Path folder;

    private Database database;
    private NetworkWhitelist network;
    private final Map<UUID, String> vanilla = new HashMap<>();
    private final Map<UUID, String> fallback = new HashMap<>();

    @BeforeEach
    void setUp() {
        database = TestDatabases.open(folder.resolve("core.db"), CoreSchema.CORE);
        network = new NetworkWhitelist(new WhitelistTable(database, () -> 1L), null, "smp", new NetworkWhitelist.Server() {
            @Override
            public Map<UUID, String> whitelisted() {
                return Map.copyOf(vanilla);
            }

            @Override
            public void whitelist(UUID player, String name, boolean on) {
                if (on) {
                    vanilla.put(player, name);
                } else {
                    vanilla.remove(player);
                }
            }

            @Override
            public void whitelistNow(UUID player, String name) {
                vanilla.put(player, name);
            }
        }, () -> { });
    }

    @AfterEach
    void close() {
        database.close();
    }

    private Whitelist vanillaOnly() {
        return new Whitelist() {
            @Override
            public boolean isWhitelisted(UUID player) {
                return fallback.containsKey(player);
            }

            @Override
            public void add(UUID player, String name, String by) {
                fallback.put(player, name);
            }
        };
    }

    @Test
    @DisplayName("on the network's list, with who did it, and on this server's mirror")
    void writesTheNetworksList() {
        Whitelist whitelist = new CoreWhitelist(() -> Optional.of(network), vanillaOnly());
        assertThat(whitelist.isWhitelisted(ALEX)).isFalse();

        whitelist.add(ALEX, "Alex", "discord:111");

        assertThat(whitelist.isWhitelisted(ALEX)).isTrue();
        assertThat(new WhitelistTable(database, () -> 1L).contains(ALEX)).contains(true);
        assertThat(vanilla).containsKey(ALEX);
        assertThat(fallback).isEmpty();
    }

    @Test
    @DisplayName("no network whitelist (yet): vanilla's, as before")
    void withoutCore() {
        Whitelist whitelist = new CoreWhitelist(Optional::empty, vanillaOnly());
        whitelist.add(ALEX, "Alex", "discord:111");
        assertThat(whitelist.isWhitelisted(ALEX)).isTrue();
        assertThat(fallback).containsKey(ALEX);
    }
}
