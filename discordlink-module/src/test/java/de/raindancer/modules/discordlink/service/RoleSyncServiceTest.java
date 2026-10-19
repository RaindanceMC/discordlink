package de.raindancer.modules.discordlink.service;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.log.Log;
import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.RoleConfig;
import de.raindancer.modules.discordlink.rules.RoleSyncRule;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class RoleSyncServiceTest {

    @TempDir
    Path folder;

    private Database database;
    private final Map<UUID, Set<String>> memberships = new HashMap<>();

    /** A LuckPerms stand-in: only the four calls the service makes. */
    private PermissionGroups groups() {
        return (PermissionGroups) Proxy.newProxyInstance(PermissionGroups.class.getClassLoader(),
                new Class<?>[]{PermissionGroups.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "directGroupsOf" -> Set.copyOf(memberships.getOrDefault((UUID) args[0], Set.of()));
                    case "join" -> memberships.computeIfAbsent((UUID) args[0], k -> new HashSet<>()).add((String) args[1]);
                    case "leave" -> memberships.getOrDefault((UUID) args[0], new HashSet<>()).remove((String) args[1]);
                    case "exists" -> true;
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("roles become groups for every linked account; leaving Discord takes the mapped ones, hand-given stay")
    void sync() {
        database = TestDatabases.open(folder.resolve("discordlink.db"), LinkStore.SCHEMA);
        LinkStore links = new LinkStore(database);
        RoleConfigStore roles = new RoleConfigStore(new YamlStore(folder.resolve("roles.yml")));
        roles.save(new RoleConfig(Set.of(), Map.of("r-sub", "subscriber")));
        UUID alex = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        links.link(new Link(alex, "Alex", "111", 1));
        links.link(new Link(alt, "AlexAlt", "111", 2));
        memberships.put(alex, new HashSet<>(Set.of("builder")));
        PermissionGroups fake = groups();
        RoleSyncService sync = new RoleSyncService(() -> fake, roles, links, new RoleSyncRule(), Log.of("test"));

        sync.sync("111", List.of("r-sub"));
        assertThat(memberships.get(alex)).containsExactlyInAnyOrder("builder", "subscriber");
        assertThat(memberships.get(alt)).containsExactly("subscriber");

        sync.syncAll(Map.of());   // nobody is on the server any more
        assertThat(memberships.get(alex)).containsExactly("builder");
        assertThat(memberships.get(alt)).isEmpty();
    }
}
