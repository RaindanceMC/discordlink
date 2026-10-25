package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.model.Link;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("The Discord screens read the link book, not asking the database every two seconds")
class LinkScreensTest {

    private final UUID alex = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger counts = new AtomicInteger();
    private Optional<Link> stored = Optional.empty();

    private LinkScreens screens() {
        return new LinkScreens(player -> {
            reads.incrementAndGet();
            return stored;
        }, () -> {
            counts.incrementAndGet();
            return 5;
        }, now::get);
    }

    @Test
    @DisplayName("a player's link is read once per half minute, then again")
    void cachedPerPlayer() {
        LinkScreens screens = screens();
        assertThat(screens.link(alex)).isEmpty();
        stored = Optional.of(new Link(alex, "Alex", "1", 10));
        assertThat(screens.link(alex)).as("still the cached answer").isEmpty();
        assertThat(reads).hasValue(1);
        now.addAndGet(LinkScreens.TTL_MILLIS);
        assertThat(screens.link(alex)).isPresent();
        assertThat(reads).hasValue(2);
    }

    @Test
    @DisplayName("the number of links is read once a minute")
    void cachedCount() {
        LinkScreens screens = screens();
        assertThat(screens.linkedCount()).isEqualTo(5);
        assertThat(screens.linkedCount()).isEqualTo(5);
        assertThat(counts).hasValue(1);
        now.addAndGet(LinkScreens.COUNT_TTL_MILLIS);
        screens.linkedCount();
        assertThat(counts).hasValue(2);
    }
}
