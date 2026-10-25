package de.raindancer.modules.discordlink.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.testkit.TestDatabases;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.PendingCode;
import de.raindancer.modules.discordlink.model.Purpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("database")
class LinkStoreTest {

    @TempDir
    Path folder;

    private Database database;
    private final UUID alex = UUID.randomUUID();

    private LinkStore store() {
        database = TestDatabases.open(folder.resolve("discordlink.db"), LinkStore.SCHEMA);
        return new LinkStore(database);
    }

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    @DisplayName("a code works once, and not after it expired")
    void codesAreSingleUse() {
        LinkStore store = store();
        store.saveCode(new PendingCode("ABCDE-23456", alex, "Alex", Purpose.JOIN, 1_000), 0);
        store.saveCode(new PendingCode("FGHJK-23456", UUID.randomUUID(), "Sam", Purpose.JOIN, 50), 0);

        assertThat(store.take("ABCDE-23456", 10)).map(PendingCode::name).contains("Alex");
        assertThat(store.take("ABCDE-23456", 10)).isEmpty();
        assertThat(store.take("FGHJK-23456", 60)).as("expired").isEmpty();
    }

    @Test
    @DisplayName("the same player and purpose keep one code, so every server shows the same")
    void oneCodePerPlayer() {
        LinkStore store = store();
        store.saveCode(new PendingCode("ABCDE-23456", alex, "Alex", Purpose.JOIN, 1_000), 0);
        assertThat(store.codeFor(alex, Purpose.JOIN, 10)).map(PendingCode::code).contains("ABCDE-23456");
        assertThat(store.codeFor(alex, Purpose.LINK, 10)).isEmpty();
        assertThat(store.codeFor(alex, Purpose.JOIN, 2_000)).as("expired").isEmpty();
    }

    @Test
    @DisplayName("links: one owner per player, several players per Discord account, and long snowflakes survive")
    void links() {
        LinkStore store = store();
        String snowflake = "1027920950911574016";
        UUID sam = UUID.randomUUID();
        assertThat(store.link(new Link(alex, "Alex", snowflake, 1))).isTrue();
        assertThat(store.link(new Link(sam, "Sam", snowflake, 2))).isTrue();
        assertThat(store.link(new Link(alex, "Alex", "999", 3))).as("a player belongs to one account").isFalse();

        assertThat(store.linksOf(snowflake)).extracting(Link::name).containsExactly("Alex", "Sam");
        assertThat(store.linkOf(alex)).map(Link::discord).contains(snowflake);
        assertThat(store.unlink(alex)).isTrue();
        assertThat(store.unlink(alex)).isFalse();
        assertThat(store.all()).extracting(Link::name).containsExactly("Sam");
    }

    @Test
    @DisplayName("how many players are linked, without loading them all")
    void count() {
        LinkStore store = store();
        assertThat(store.count()).isZero();
        store.link(new Link(alex, "Alex", "1", 1));
        store.link(new Link(UUID.randomUUID(), "Sam", "1", 2));
        assertThat(store.count()).isEqualTo(2);
        store.unlink(alex);
        assertThat(store.count()).isEqualTo(1);
    }
}
