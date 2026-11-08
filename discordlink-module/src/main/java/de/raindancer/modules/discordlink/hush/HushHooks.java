package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.moderation.feed.ModerationFeed;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Connects Core's {@link ModerationFeed} to the Hush feed. Each hook only hands over a recipe for the event; names
 * are looked up and the JSON built on the feed's own thread.
 */
public final class HushHooks implements AutoCloseable {

    private final ModerationFeed.Subscription[] subscriptions;

    private HushHooks(ModerationFeed.Subscription... subscriptions) {
        this.subscriptions = subscriptions;
    }

    /** @param sink {@link HushFeed#offer}, or anything that takes the event type and a recipe */
    public static HushHooks connect(ModerationFeed moderation, BiConsumer<String, Supplier<DataObject>> sink,
                                    LongSupplier clock, Function<UUID, String> names) {
        return new HushHooks(
                moderation.onAnticheat(alert -> {
                    long at = clock.getAsLong();
                    sink.accept("anticheat", () -> HushEvents.anticheat(at, alert));
                }),
                moderation.onPunishment(notice -> {
                    long at = clock.getAsLong();
                    // Empty (a freeze, a fine) is not an event: the recipe's null is skipped by the feed.
                    sink.accept("punishment", () -> HushEvents.punishment(at, notice, names).orElse(null));
                }),
                moderation.onReport(report -> {
                    long at = clock.getAsLong();
                    sink.accept("report", () -> HushEvents.report(at, report));
                }));
    }

    @Override
    public void close() {
        for (ModerationFeed.Subscription subscription : subscriptions) {
            subscription.close();
        }
    }
}
