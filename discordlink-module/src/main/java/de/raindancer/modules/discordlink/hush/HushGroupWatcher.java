package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.service.LinkService;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Tells Hush when the direct LuckPerms groups of a <em>linked</em> player change, so Discord's roles can follow.
 * Compares with the groups last told (or last set by Hush itself, see {@link #remember}), so a change that came from
 * Discord is never echoed back and LuckPerms' many recalculations cost nothing.
 */
public final class HushGroupWatcher implements AutoCloseable {

    private static final long SETTLE_MILLIS = 1_500;
    private static final long ALL_EVERY_MILLIS = 30_000;

    private final LinkService links;
    private final Supplier<PermissionGroups> groups;
    private final BiConsumer<String, Supplier<DataObject>> sink;
    private final LongSupplier clock;
    private final Map<UUID, Set<String>> known = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "hush-groups");
        thread.setDaemon(true);
        return thread;
    });
    private NetworkBus.Subscription subscription = () -> { };
    private long allAt;

    public HushGroupWatcher(LinkService links, Supplier<PermissionGroups> groups,
                            BiConsumer<String, Supplier<DataObject>> sink, LongSupplier clock) {
        this.links = links;
        this.groups = groups;
        this.sink = sink;
        this.clock = clock;
    }

    /** Remembers everybody's groups as they are now, then listens. Seeding reads LuckPerms: not on the server thread. */
    public void start() {
        PermissionGroups permissions = groups.get();
        if (permissions == PermissionGroups.NONE) {
            return;
        }
        scheduler.execute(this::seed);
        subscription = permissions.onChange(this::changed);
        links.onChange((link, linked) -> scheduler.execute(() -> {
            if (linked) {
                known.put(link.player(), groups.get().directGroupsOf(link.player()));
            } else {
                known.remove(link.player());
            }
        }));
    }

    void seed() {
        for (Link link : links.all()) {
            known.put(link.player(), groups.get().directGroupsOf(link.player()));
        }
    }

    /** What Hush just set (or what a join changed to): not news. */
    public void remember(UUID player, Set<String> now) {
        known.put(player, Set.copyOf(now));
    }

    void changed(UUID who) {
        if (who == null) {
            long now = clock.getAsLong();
            if (now - allAt < ALL_EVERY_MILLIS) {
                return;
            }
            allAt = now;
            scheduler.schedule(() -> links.all().forEach(link -> check(link.player())), SETTLE_MILLIS, TimeUnit.MILLISECONDS);
            return;
        }
        if (known.containsKey(who) && pending.add(who)) {
            scheduler.schedule(() -> {
                pending.remove(who);
                check(who);
            }, SETTLE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /** @return whether a change was told to Hush */
    boolean check(UUID player) {
        Optional<Link> link = links.linkOf(player);
        if (link.isEmpty()) {
            known.remove(player);
            return false;
        }
        Set<String> now = groups.get().directGroupsOf(player);
        Set<String> before = known.put(player, now);
        if (before == null || before.equals(now)) {
            return false;
        }
        long at = clock.getAsLong();
        String name = link.get().name();
        String discord = link.get().discord();
        sink.accept("groups", () -> HushEvents.groups(at, player, name, discord, now));
        return true;
    }

    @Override
    public void close() {
        subscription.close();
        scheduler.shutdownNow();
    }
}
