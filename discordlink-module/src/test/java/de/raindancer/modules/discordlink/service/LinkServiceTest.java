package de.raindancer.modules.discordlink.service;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Purpose;
import de.raindancer.modules.discordlink.model.RoleConfig;
import de.raindancer.modules.discordlink.rules.CodeRule;
import de.raindancer.modules.discordlink.rules.LinkRule;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class LinkServiceTest {

    @TempDir
    Path folder;

    private Database database;
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Map<UUID, String> whitelisted = new HashMap<>();
    private CodeService codes;
    private LinkService links;
    private RoleConfigStore roles;

    @BeforeEach
    void setUp() {
        database = TestDatabases.open(folder.resolve("discordlink.db"), LinkStore.SCHEMA);
        LinkStore store = new LinkStore(database);
        roles = new RoleConfigStore(new YamlStore(folder.resolve("roles.yml")));
        codes = new CodeService(store, new CodeRule(), new Random(7), now::get, DiscordLinkSettings.DEFAULTS);
        Whitelist whitelist = new Whitelist() {
            @Override
            public boolean isWhitelisted(UUID player) {
                return whitelisted.containsKey(player);
            }

            @Override
            public void add(UUID player, String name) {
                whitelisted.put(player, name);
            }
        };
        links = new LinkService(codes, store, roles, new LinkRule(), whitelist, now::get);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a join code whitelists and links; the same code twice does nothing more")
    void joinCode() {
        UUID alex = UUID.randomUUID();
        String code = codes.issue(alex, "Alex", Purpose.JOIN).orElseThrow();
        assertThat(codes.issue(alex, "Alex", Purpose.JOIN)).as("same code while it lasts").contains(code);

        LinkService.Result result = links.redeem(code.toLowerCase(), "111", List.of());

        assertThat(result.outcome()).isEqualTo(LinkService.Outcome.LINKED_AND_WHITELISTED);
        assertThat(whitelisted).containsEntry(alex, "Alex");
        assertThat(links.linkOf(alex)).isPresent();
        assertThat(links.redeem(code, "111", List.of()).outcome()).isEqualTo(LinkService.Outcome.WRONG_CODE);
    }

    @Test
    @DisplayName("a link code from in game links without touching the whitelist")
    void linkCode() {
        UUID alex = UUID.randomUUID();
        whitelisted.put(alex, "Alex");
        String code = links.codeToLink(alex, "Alex").orElseThrow();
        assertThat(links.redeem(code, "111", List.of()).outcome()).isEqualTo(LinkService.Outcome.LINKED);
    }

    @Test
    @DisplayName("a second account needs a whitelister role, picked by the admins")
    void secondAccount() {
        String first = codes.issue(UUID.randomUUID(), "Alex", Purpose.JOIN).orElseThrow();
        String second = codes.issue(UUID.randomUUID(), "AlexAlt", Purpose.JOIN).orElseThrow();
        links.redeem(first, "111", List.of("r-member"));

        LinkService.Result refused = links.redeem(second, "111", List.of("r-member"));
        assertThat(refused.outcome()).isEqualTo(LinkService.Outcome.ONE_IS_ENOUGH);
        assertThat(refused.yours()).extracting(l -> l.name()).containsExactly("Alex");

        roles.save(RoleConfig.EMPTY.withWhitelisters(Set.of("r-vip")));
        String again = codes.issue(UUID.randomUUID(), "AlexAlt", Purpose.JOIN).orElseThrow();
        assertThat(links.redeem(again, "111", List.of("r-vip")).outcome()).isEqualTo(LinkService.Outcome.LINKED_AND_WHITELISTED);
    }

    @Test
    @DisplayName("somebody else's player can't be taken over with a code")
    void someoneElses() {
        UUID alex = UUID.randomUUID();
        links.redeem(codes.issue(alex, "Alex", Purpose.JOIN).orElseThrow(), "111", List.of());
        String stolen = links.codeToLink(alex, "Alex").orElseThrow();
        assertThat(links.redeem(stolen, "222", List.of()).outcome()).isEqualTo(LinkService.Outcome.SOMEONE_ELSES);
        assertThat(links.linkOf(alex).orElseThrow().discord()).isEqualTo("111");
    }

    @Test
    @DisplayName("five wrong codes lock a Discord account out for ten minutes, then it may try again")
    void guessing() {
        for (int i = 0; i < 5; i++) {
            assertThat(links.redeem("AAAAA-AAAAA", "333", List.of()).outcome()).isEqualTo(LinkService.Outcome.WRONG_CODE);
        }
        String real = codes.issue(UUID.randomUUID(), "Late", Purpose.JOIN).orElseThrow();
        assertThat(links.redeem(real, "333", List.of()).outcome()).isEqualTo(LinkService.Outcome.TOO_MANY_TRIES);

        now.addAndGet(CodeService.GUESS_WINDOW_MILLIS + 1);
        String fresh = codes.issue(UUID.randomUUID(), "Later", Purpose.JOIN).orElseThrow();
        assertThat(links.redeem(fresh, "333", List.of()).outcome()).isEqualTo(LinkService.Outcome.LINKED_AND_WHITELISTED);
    }

    @Test
    @DisplayName("an expired code is refused")
    void expired() {
        String code = codes.issue(UUID.randomUUID(), "Slow", Purpose.JOIN).orElseThrow();
        now.addAndGet(DiscordLinkSettings.DEFAULTS.codeMinutes() * 60_000L + 1);
        assertThat(links.redeem(code, "444", List.of()).outcome()).isEqualTo(LinkService.Outcome.WRONG_CODE);
    }

    @Test
    @DisplayName("role choices made in Discord survive being read again (another server, a restart)")
    void roleConfigPersists() {
        roles.save(RoleConfig.EMPTY.withWhitelisters(Set.of("1027920950911574016")).mapping("1111111111111111111", "vip"));
        RoleConfigStore again = new RoleConfigStore(new YamlStore(folder.resolve("roles.yml")));
        again.reload();
        assertThat(again.current().whitelisters()).containsExactly("1027920950911574016");
        assertThat(again.current().groups()).containsEntry("1111111111111111111", "vip");
    }
}
