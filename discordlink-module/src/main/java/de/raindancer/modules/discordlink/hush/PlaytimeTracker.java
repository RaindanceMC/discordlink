package de.raindancer.modules.discordlink.hush;

import de.raindancer.modules.discordlink.hush.PlaytimeCounter.Tracked;
import de.raindancer.modules.discordlink.hush.PlaytimeCounter.Who;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Counts the active minutes of linked players on this server, one tick a minute, and gives them to Hush as
 * {@code playtime} events: every fifth tick what changed, and a player's minutes when they leave. Only linked players
 * are looked at at all. "Online" is kept here from join and quit, so the tick, which runs off the server thread,
 * never touches Bukkit's player list. Today's counts are written to a small file after each tick and picked up again
 * after a restart.
 */
public final class PlaytimeTracker implements AutoCloseable {

    public static final int FLUSH_EVERY_TICKS = 5;

    private final Function<UUID, Optional<Who>> linked;
    private final BooleanSupplier awayKnown;
    private final Predicate<UUID> away;
    private final Supplier<ZoneId> zone;
    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private final BiConsumer<String, Supplier<DataObject>> sink;
    private final Function<UUID, Long> firstSeen;
    private final Path file;
    private final Consumer<String> warn;

    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final PlaytimeCounter counter;
    private int ticks;

    /**
     * @param linked    the player's link, empty when they are not linked
     * @param awayKnown whether anything answers {@code away} at all; when not, being online is being active
     * @param sink      {@link HushFeed#offer}: the recipe runs on the feed's thread, which may look things up
     * @param firstSeen epoch ms the player was first seen, null or 0 when unknown
     */
    public PlaytimeTracker(Function<UUID, Optional<Who>> linked, BooleanSupplier awayKnown, Predicate<UUID> away,
                           Supplier<ZoneId> zone, BooleanSupplier enabled, LongSupplier clock,
                           BiConsumer<String, Supplier<DataObject>> sink, Function<UUID, Long> firstSeen, Path file,
                           Consumer<String> warn) {
        this.linked = linked;
        this.awayKnown = awayKnown;
        this.away = away;
        this.zone = zone;
        this.enabled = enabled;
        this.clock = clock;
        this.sink = sink;
        this.firstSeen = firstSeen;
        this.file = file;
        this.warn = warn;
        this.counter = restore();
    }

    // ------------------------------------------------------------------ who is here

    /** Everybody online now; once, when the module starts. */
    public void seed(Collection<UUID> players) {
        online.addAll(players);
    }

    public void joined(UUID player) {
        online.add(player);
    }

    /** Sends the player's minutes at once if they changed since last sent. */
    public void left(UUID player) {
        online.remove(player);
        counter.quit(player).ifPresent(tracked -> send(counter.day(), List.of(tracked)));
    }

    /** An account was unlinked: it is not tracked or sent any more. */
    public void forget(UUID player) {
        counter.forget(player);
    }

    // ------------------------------------------------------------------ the tick

    /** One minute passed. Call once a minute, off the server thread. Never throws. */
    public void tick() {
        try {
            if (!enabled.getAsBoolean()) {
                return;
            }
            LocalDate today = LocalDate.ofInstant(Instant.ofEpochMilli(clock.getAsLong()), zone.get());
            boolean watching = awayKnown.getAsBoolean();
            Map<UUID, Who> active = new LinkedHashMap<>();
            for (UUID player : online) {
                if (watching && away.test(player)) {
                    continue;
                }
                linked.apply(player).ifPresent(who -> active.put(player, who));
            }
            counter.minute(today, active).ifPresent(ended -> send(ended.day(), ended.entries()));
            if (++ticks % FLUSH_EVERY_TICKS == 0) {
                counter.changed().ifPresent(batch -> send(batch.day(), batch.entries()));
            }
            save();
        } catch (RuntimeException broken) {
            warn.accept("Playtime tick failed: " + broken.getClass().getSimpleName());
        }
    }

    private void send(LocalDate day, List<Tracked> entries) {
        long at = clock.getAsLong();
        String date = day.toString();
        for (int from = 0; from < entries.size(); from += HushEvents.PLAYTIME_MAX_ENTRIES) {
            List<Tracked> part = entries.subList(from, Math.min(entries.size(), from + HushEvents.PLAYTIME_MAX_ENTRIES));
            sink.accept("playtime", () -> HushEvents.playtime(at, date, part.stream()
                    .map(t -> new HushEvents.PlaytimeEntry(t.player(), t.name(), t.discord(), t.minutes(), known(t.player())))
                    .toList()));
        }
    }

    private Long known(UUID player) {
        Long at = firstSeen.apply(player);
        return at == null || at <= 0 ? null : at;
    }

    // ------------------------------------------------------------------ the file

    @Override
    public void close() {
        save();
    }

    private synchronized void save() {
        PlaytimeCounter.Batch state = counter.snapshot();
        if (state.day() == null) {
            return;
        }
        DataArray entries = DataArray.empty();
        for (Tracked tracked : state.entries()) {
            entries.add(DataObject.empty().put("uuid", tracked.player().toString()).put("name", tracked.name())
                    .put("discord", tracked.discord()).put("minutes", tracked.minutes()));
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(temp, DataObject.empty().put("day", state.day().toString()).put("entries", entries).toString(),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failed) {
            warn.accept("Could not save the playtime counts: " + failed.getClass().getSimpleName());
        }
    }

    private PlaytimeCounter restore() {
        if (!Files.isRegularFile(file)) {
            return new PlaytimeCounter();
        }
        try {
            DataObject saved = DataObject.fromJson(Files.readString(file, StandardCharsets.UTF_8));
            List<Tracked> entries = new ArrayList<>();
            DataArray array = saved.getArray("entries");
            for (int i = 0; i < array.length(); i++) {
                DataObject entry = array.getObject(i);
                entries.add(new Tracked(UUID.fromString(entry.getString("uuid")), entry.getString("name"),
                        entry.getString("discord"), entry.getInt("minutes")));
            }
            return new PlaytimeCounter(LocalDate.parse(saved.getString("day")), entries);
        } catch (IOException | RuntimeException unreadable) {
            warn.accept("The saved playtime counts could not be read, counting starts empty: "
                    + unreadable.getClass().getSimpleName());
            return new PlaytimeCounter();
        }
    }
}
