package de.raindancer.modules.discordlink.store;

import de.raindancer.core.data.sql.Database;
import de.raindancer.core.data.sql.Schema;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.PendingCode;
import de.raindancer.modules.discordlink.model.Purpose;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Links and pending codes, for the whole network: the hub hands out a code on its kick screen, the bot on another
 * server redeems it. Every call is database I/O — never on the server thread. Discord ids are text: snowflakes do
 * not fit SQL's INTEGER on Postgres.
 */
public final class LinkStore {

    public static final String NAME = "discordlink";

    public static final Schema SCHEMA = Schema.of("""
            CREATE TABLE discord_link (
                player    TEXT    NOT NULL,
                name      TEXT    NOT NULL,
                discord   TEXT    NOT NULL,
                linked_at BIGINT  NOT NULL,
                PRIMARY KEY (player)
            )""",
            "CREATE INDEX discord_link_discord ON discord_link (discord)",
            """
            CREATE TABLE discord_link_code (
                code       TEXT   NOT NULL,
                player     TEXT   NOT NULL,
                name       TEXT   NOT NULL,
                purpose    TEXT   NOT NULL,
                expires_at BIGINT NOT NULL,
                PRIMARY KEY (code)
            )""",
            "CREATE INDEX discord_link_code_player ON discord_link_code (player, purpose)");

    private final Database database;

    public LinkStore(Database database) {
        this.database = database;
    }

    // ---- codes ------------------------------------------------------------------------------

    /** The player's unexpired code for this purpose, so a second kick (or a second server) shows the same one. */
    public Optional<PendingCode> codeFor(UUID player, Purpose purpose, long now) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT code, name, expires_at FROM discord_link_code WHERE player = ? AND purpose = ? AND expires_at > ?")) {
                select.setString(1, player.toString());
                select.setString(2, purpose.name());
                select.setLong(3, now);
                try (ResultSet row = select.executeQuery()) {
                    return row.next() ? new PendingCode(row.getString(1), player, row.getString(2), purpose, row.getLong(3)) : null;
                }
            }
        }).filter(java.util.Objects::nonNull);
    }

    public boolean saveCode(PendingCode code, long now) {
        return database.write(connection -> {
            try (PreparedStatement clean = connection.prepareStatement(
                    "DELETE FROM discord_link_code WHERE expires_at <= ? OR (player = ? AND purpose = ?)");
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT INTO discord_link_code (code, player, name, purpose, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                clean.setLong(1, now);
                clean.setString(2, code.player().toString());
                clean.setString(3, code.purpose().name());
                clean.executeUpdate();
                insert.setString(1, code.code());
                insert.setString(2, code.player().toString());
                insert.setString(3, code.name());
                insert.setString(4, code.purpose().name());
                insert.setLong(5, code.expiresAt());
                insert.executeUpdate();
            }
        });
    }

    /** Looks a code up and removes it in one go: a code works once. */
    public Optional<PendingCode> take(String code, long now) {
        PendingCode[] found = new PendingCode[1];
        boolean written = database.write(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT player, name, purpose, expires_at FROM discord_link_code WHERE code = ?");
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM discord_link_code WHERE code = ?")) {
                select.setString(1, code);
                try (ResultSet row = select.executeQuery()) {
                    if (row.next()) {
                        found[0] = new PendingCode(code, UUID.fromString(row.getString(1)), row.getString(2),
                                Purpose.valueOf(row.getString(3)), row.getLong(4));
                    }
                }
                delete.setString(1, code);
                delete.executeUpdate();
            }
        });
        if (!written || found[0] == null || found[0].expired(now)) {
            return Optional.empty();
        }
        return Optional.of(found[0]);
    }

    // ---- links ------------------------------------------------------------------------------

    public Optional<Link> linkOf(UUID player) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT player, name, discord, linked_at FROM discord_link WHERE player = ?")) {
                select.setString(1, player.toString());
                try (ResultSet row = select.executeQuery()) {
                    return row.next() ? link(row) : null;
                }
            }
        }).filter(java.util.Objects::nonNull);
    }

    public List<Link> linksOf(String discord) {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT player, name, discord, linked_at FROM discord_link WHERE discord = ? ORDER BY linked_at")) {
                select.setString(1, discord);
                return all(select);
            }
        }).orElse(List.of());
    }

    /** How many players are linked. */
    public long count() {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM discord_link");
                 ResultSet row = select.executeQuery()) {
                return row.next() ? row.getLong(1) : 0L;
            }
        }).orElse(0L);
    }

    public List<Link> all() {
        return database.read(connection -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT player, name, discord, linked_at FROM discord_link ORDER BY linked_at")) {
                return all(select);
            }
        }).orElse(List.of());
    }

    /** @return false if it could not be written; a player already linked elsewhere is not taken over here */
    public boolean link(Link link) {
        return database.write(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO discord_link (player, name, discord, linked_at) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, link.player().toString());
                insert.setString(2, link.name());
                insert.setString(3, link.discord());
                insert.setLong(4, link.linkedAt());
                insert.executeUpdate();
            }
        });
    }

    public boolean unlink(UUID player) {
        int[] removed = new int[1];
        boolean written = database.write(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM discord_link WHERE player = ?")) {
                delete.setString(1, player.toString());
                removed[0] = delete.executeUpdate();
            }
        });
        return written && removed[0] > 0;
    }

    private static List<Link> all(PreparedStatement select) throws SQLException {
        List<Link> links = new ArrayList<>();
        try (ResultSet row = select.executeQuery()) {
            while (row.next()) {
                links.add(link(row));
            }
        }
        return links;
    }

    private static Link link(ResultSet row) throws SQLException {
        return new Link(UUID.fromString(row.getString(1)), row.getString(2), row.getString(3), row.getLong(4));
    }
}
