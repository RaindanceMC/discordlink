package de.raindancer.modules.discordlink.hush;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Active minutes per linked player for one calendar day, and what of it was already handed out. Pure: the caller says
 * what day it is and who was active; this only adds up and remembers. The values are the day's totals, never
 * increments, so an event Hush gets twice or not at all cannot throw its count off.
 */
public final class PlaytimeCounter {

    /** Who a minute is credited to: the account's name and the Discord id it is linked to. */
    public record Who(String name, String discord) {
    }

    public record Tracked(UUID player, String name, String discord, int minutes) {
    }

    public record Batch(LocalDate day, List<Tracked> entries) {
    }

    private static final class Slot {
        String name;
        String discord;
        int minutes;
        int sent = -1;
    }

    private final Map<UUID, Slot> slots = new LinkedHashMap<>();
    private LocalDate day;

    public PlaytimeCounter() {
    }

    /** Continues a day from what was saved; none of it counts as handed out. */
    public PlaytimeCounter(LocalDate day, Collection<Tracked> restored) {
        this.day = day;
        for (Tracked tracked : restored) {
            Slot slot = new Slot();
            slot.name = tracked.name();
            slot.discord = tracked.discord();
            slot.minutes = tracked.minutes();
            slots.put(tracked.player(), slot);
        }
    }

    /**
     * One minute passed. A date after the current one ends that day first (a date before it, from a changed zone,
     * does not: the same day goes on).
     *
     * @return the ended day's values, if any of them were not handed out yet
     */
    public synchronized Optional<Batch> minute(LocalDate today, Map<UUID, Who> active) {
        Optional<Batch> ended = Optional.empty();
        if (day == null) {
            day = today;
        } else if (today.isAfter(day)) {
            ended = batch();
            slots.clear();
            day = today;
        }
        active.forEach((player, who) -> {
            Slot slot = slots.computeIfAbsent(player, id -> new Slot());
            slot.name = who.name();
            slot.discord = who.discord();
            slot.minutes++;
        });
        return ended;
    }

    /** What changed since it was last handed out, for the current day. */
    public synchronized Optional<Batch> changed() {
        return batch();
    }

    /** The player's minutes if they changed since they were last handed out. */
    public synchronized Optional<Tracked> quit(UUID player) {
        Slot slot = slots.get(player);
        if (slot == null || slot.minutes == slot.sent) {
            return Optional.empty();
        }
        slot.sent = slot.minutes;
        return Optional.of(new Tracked(player, slot.name, slot.discord, slot.minutes));
    }

    public synchronized void forget(UUID player) {
        slots.remove(player);
    }

    public synchronized LocalDate day() {
        return day;
    }

    /** Everything, for saving; nothing is marked as handed out. */
    public synchronized Batch snapshot() {
        List<Tracked> all = new ArrayList<>();
        slots.forEach((player, slot) -> all.add(new Tracked(player, slot.name, slot.discord, slot.minutes)));
        return new Batch(day, all);
    }

    private Optional<Batch> batch() {
        List<Tracked> changed = new ArrayList<>();
        slots.forEach((player, slot) -> {
            if (slot.minutes > 0 && slot.minutes != slot.sent) {
                slot.sent = slot.minutes;
                changed.add(new Tracked(player, slot.name, slot.discord, slot.minutes));
            }
        });
        return changed.isEmpty() ? Optional.empty() : Optional.of(new Batch(day, changed));
    }
}
