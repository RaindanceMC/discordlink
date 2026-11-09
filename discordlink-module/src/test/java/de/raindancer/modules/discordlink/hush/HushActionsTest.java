package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Purpose;
import de.raindancer.modules.discordlink.rules.CodeRule;
import de.raindancer.modules.discordlink.rules.LinkRule;
import de.raindancer.modules.discordlink.rules.RoleSyncRule;
import de.raindancer.modules.discordlink.service.CodeService;
import de.raindancer.modules.discordlink.service.FakeGroups;
import de.raindancer.modules.discordlink.service.InfoService;
import de.raindancer.modules.discordlink.service.LinkService;
import de.raindancer.modules.discordlink.service.PlayerFacts;
import de.raindancer.modules.discordlink.service.Whitelist;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class HushActionsTest {

    @TempDir
    Path folder;

    private Database database;
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Set<UUID> whitelisted = new HashSet<>();
    private final FakeGroups groups = new FakeGroups();
    private final List<DataObject> answers = new ArrayList<>();
    private final List<String> answerTypes = new ArrayList<>();
    private CodeService codes;
    private LinkService links;
    private HushActions actions;
    private HushGroupWatcher watcher;

    @BeforeEach
    void setUp() {
        database = TestDatabases.open(folder.resolve("discordlink.db"), LinkStore.SCHEMA);
        LinkStore store = new LinkStore(database);
        codes = new CodeService(store, new CodeRule(), new Random(7), now::get, DiscordLinkSettings.DEFAULTS);
        Whitelist whitelist = new Whitelist() {
            @Override
            public boolean isWhitelisted(UUID player) {
                return whitelisted.contains(player);
            }

            @Override
            public void add(UUID player, String name, String by) {
                whitelisted.add(player);
            }
        };
        links = new LinkService(codes, store, new RoleConfigStore(new YamlStore(folder.resolve("roles.yml"))), new LinkRule(),
                whitelist, now::get);
        PlayerFacts facts = new PlayerFacts() {
            @Override public Optional<Online> online(String name) { return Optional.empty(); }
            @Override public Optional<UUID> idOf(String name) { return name.equals("Alex") ? Optional.of(ALEX) : Optional.empty(); }
            @Override public Optional<Seen> lastSeen(UUID player) { return Optional.of(new Seen("Alex", "smp", 5_000, false)); }
            @Override public Optional<Long> firstSeen(UUID player) { return Optional.of(1_000L); }
            @Override public long playtimeMinutes(UUID player) { return 125; }
            @Override public Optional<String> rank(UUID player) { return Optional.of("admin"); }
            @Override public boolean linked(UUID player) { return links.linkOf(player).isPresent(); }
            @Override public List<Server> servers() { return List.of(); }
            @Override public int onlineCount() { return 0; }
            @Override public Map<String, String> serverNames() { return Map.of("smp", "Hyades SMP"); }
        };
        watcher = new HushGroupWatcher(links, () -> groups, (type, build) -> { }, now::get);
        actions = new HushActions(links, new InfoService(facts), () -> groups, new RoleSyncRule(), (type, build) -> {
            answerTypes.add(type);
            answers.add(build.get());
        }, now::get, Runnable::run, watcher);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static final UUID ALEX = UUID.randomUUID();

    private static DataObject action(String id, String type) {
        return DataObject.empty().put("id", id).put("type", type);
    }

    @Test
    @DisplayName("redeem: a join code links the account and never whitelists, the answer names the player")
    void redeemLinksWithoutWhitelisting() {
        String code = codes.issue(ALEX, "Alex", Purpose.JOIN).orElseThrow();

        actions.handle(action("a1", "redeem").put("code", code).put("discord", "111"));

        DataObject result = answers.get(0);
        assertThat(result.getString("ref")).isEqualTo("a1");
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(result.getString("outcome")).isEqualTo("linked");
        assertThat(result.getObject("data").getObject("player").getString("name")).isEqualTo("Alex");
        assertThat(links.linkOf(ALEX).orElseThrow().discord()).isEqualTo("111");
        assertThat(whitelisted).isEmpty();
    }

    @Test
    @DisplayName("redeem: a wrong code is a failed answer, not an exception")
    void redeemWrong() {
        actions.handle(action("a2", "redeem").put("code", "NOPE-0000").put("discord", "111"));

        assertThat(answers.get(0).getBoolean("ok")).isFalse();
        assertThat(answers.get(0).getString("outcome")).isEqualTo("wrong_code");
    }

    @Test
    @DisplayName("an action asked twice is answered again from memory and done once")
    void idempotent() {
        String code = codes.issue(ALEX, "Alex", Purpose.LINK).orElseThrow();
        DataObject redeem = action("a3", "redeem").put("code", code).put("discord", "111");

        actions.handle(redeem);
        actions.handle(redeem);

        assertThat(answers).hasSize(2);
        assertThat(answers.get(1).getString("outcome")).isEqualTo("linked");
    }

    @Test
    @DisplayName("unlink: ends every link of that Discord account and says which")
    void unlink() {
        links.redeem(codes.issue(ALEX, "Alex", Purpose.LINK).orElseThrow(), "111", List.of(), false);

        actions.handle(action("u1", "unlink").put("discord", "111"));
        actions.handle(action("u2", "unlink").put("discord", "111"));

        assertThat(answers.get(0).getString("outcome")).isEqualTo("unlinked");
        assertThat(answers.get(0).getObject("data").getArray("accounts").getString(0)).isEqualTo("Alex");
        assertThat(links.linkOf(ALEX)).isEmpty();
        assertThat(answers.get(1).getString("outcome")).isEqualTo("not_linked");
    }

    @Test
    @DisplayName("set_groups: joins what is wanted, leaves only managed groups no longer wanted, never touches the rest")
    void setGroups() {
        groups.existing.addAll(Set.of("admin", "mod", "owner", "tester", "default"));
        groups.direct.put(ALEX, new HashSet<>(Set.of("mod", "tester", "default")));

        actions.handle(action("g1", "set_groups").put("player", ALEX.toString())
                .put("groups", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("admin")))
                .put("managed", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("owner", "admin", "mod"))));

        DataObject result = answers.get(0);
        assertThat(result.getBoolean("ok")).isTrue();
        assertThat(result.getObject("data").getArray("joined").getString(0)).isEqualTo("admin");
        assertThat(result.getObject("data").getArray("left").getString(0)).isEqualTo("mod");
        assertThat(groups.direct.get(ALEX)).containsExactlyInAnyOrder("admin", "tester", "default");
    }

    @Test
    @DisplayName("set_groups: a group that does not exist is reported, the rest is still applied")
    void setGroupsMissingGroup() {
        groups.existing.add("admin");

        actions.handle(action("g2", "set_groups").put("player", ALEX.toString())
                .put("groups", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("admin", "ghost")))
                .put("managed", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("admin", "ghost"))));

        assertThat(answers.get(0).getBoolean("ok")).isFalse();
        assertThat(answers.get(0).getString("outcome")).isEqualTo("partly");
        assertThat(groups.direct.get(ALEX)).containsExactly("admin");
    }

    @Test
    @DisplayName("sync_links lists every link as a links event, then answers")
    void syncLinks() {
        links.redeem(codes.issue(ALEX, "Alex", Purpose.LINK).orElseThrow(), "111", List.of(), false);

        actions.handle(action("s1", "sync_links"));

        assertThat(answerTypes).containsExactly("links", "result");
        DataObject entry = answers.get(0).getArray("entries").getObject(0);
        assertThat(entry.getString("discord")).isEqualTo("111");
        assertThat(entry.getObject("player").getString("uuid")).isEqualTo(ALEX.toString());
    }

    @Test
    @DisplayName("player: the card of a known player, an unknown_player answer for anybody else")
    void player() {
        actions.handle(action("p1", "player").put("name", "Alex"));
        actions.handle(action("p2", "player").put("name", "Nobody"));

        DataObject card = answers.get(0).getObject("data");
        assertThat(answers.get(0).getString("outcome")).isEqualTo("card");
        assertThat(card.getString("name")).isEqualTo("Alex");
        assertThat(card.getLong("playtimeMinutes")).isEqualTo(125);
        assertThat(card.getString("rank")).isEqualTo("admin");
        assertThat(card.getString("onlineOn", null)).isNull();
        assertThat(answers.get(1).getString("outcome")).isEqualTo("unknown_player");
    }

    @Test
    @DisplayName("an unknown action type and one without an id are not crashes")
    void garbage() {
        actions.handle(action("x1", "launch_rocket"));
        actions.handle(DataObject.empty().put("type", "redeem"));

        assertThat(answers).hasSize(1);
        assertThat(answers.get(0).getString("outcome")).isEqualTo("unknown_action");
    }

    @Test
    @DisplayName("what set_groups changed is remembered, so the watcher does not tell Discord about it")
    void noEcho() {
        groups.existing.add("admin");
        links.redeem(codes.issue(ALEX, "Alex", Purpose.LINK).orElseThrow(), "111", List.of(), false);
        List<String> told = new ArrayList<>();
        HushGroupWatcher quiet = new HushGroupWatcher(links, () -> groups, (type, build) -> told.add(type), now::get);
        actions = new HushActions(links, null, () -> groups, new RoleSyncRule(), (type, build) -> { }, now::get, Runnable::run, quiet);
        quiet.seed();

        actions.handle(action("g9", "set_groups").put("player", ALEX.toString())
                .put("groups", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("admin")))
                .put("managed", net.dv8tion.jda.api.utils.data.DataArray.fromCollection(List.of("admin"))));

        assertThat(quiet.check(ALEX)).isFalse();
        assertThat(told).isEmpty();
    }

    @Test
    @DisplayName("a change made in game is told once, with all direct groups")
    void gameChangeIsTold() {
        links.redeem(codes.issue(ALEX, "Alex", Purpose.LINK).orElseThrow(), "111", List.of(), false);
        List<DataObject> told = new ArrayList<>();
        HushGroupWatcher w = new HushGroupWatcher(links, () -> groups, (type, build) -> told.add(build.get()), now::get);
        w.seed();

        groups.direct.put(ALEX, new HashSet<>(Set.of("mod", "default")));

        assertThat(w.check(ALEX)).isTrue();
        assertThat(w.check(ALEX)).as("same state, nothing new").isFalse();
        DataObject event = told.get(0);
        assertThat(event.getString("type")).isEqualTo("groups");
        assertThat(event.getString("discord")).isEqualTo("111");
        assertThat(event.getArray("groups").toList()).containsExactly("default", "mod");
    }

    @Test
    @DisplayName("somebody who is not linked is never told to Hush")
    void unlinkedIsIgnored() {
        List<String> told = new ArrayList<>();
        HushGroupWatcher w = new HushGroupWatcher(links, () -> groups, (type, build) -> told.add(type), now::get);
        groups.direct.put(ALEX, new HashSet<>(Set.of("mod")));

        assertThat(w.check(ALEX)).isFalse();
        assertThat(told).isEmpty();
    }
}
