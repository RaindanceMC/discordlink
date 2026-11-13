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
                }),
                moderation.onAdminMode(notice -> {
                    long at = clock.getAsLong();
                    sink.accept("admin", () -> HushEvents.adminMode(at, notice, names));
                }));
    }

    /** Requests waiting for staff (Core 2.94.0): each state goes at once, somebody is looking at the card. */
    public static de.raindancer.core.moderation.review.StaffReviews.Subscription reviews(
            de.raindancer.core.moderation.review.StaffReviews reviews, BiConsumer<String, Supplier<DataObject>> sink,
            LongSupplier clock) {
        return reviews.onReview(review -> {
            long at = clock.getAsLong();
            sink.accept("review", () -> HushEvents.review(at, review));
        });
    }

    @Override
    public void close() {
        for (ModerationFeed.Subscription subscription : subscriptions) {
            subscription.close();
        }
    }
}
