package de.raindancer.modules.discordlink.model;

import java.util.Optional;
import java.util.UUID;

/**
 * What /player shows: only public things. Whether somebody linked Discord is a yes or no — never to whom. Vanished
 * staff show as offline.
 */
public record PlayerCard(UUID id, String name, Optional<String> onlineOn, Optional<Long> lastSeen, Optional<String> lastServer,
                         Optional<Long> firstSeen, long playtimeMinutes, Optional<String> rank, boolean linked) {

    public String playtimeText() {
        long hours = playtimeMinutes / 60;
        long minutes = playtimeMinutes % 60;
        return hours == 0 ? minutes + " min" : "%d h %d min".formatted(hours, minutes);
    }

    /** Floodgate gives Bedrock players ids with an all-zero top half; there is no Java skin to draw for those. */
    public Optional<String> headUrl() {
        return id.getMostSignificantBits() == 0 ? Optional.empty() : Optional.of("https://mc-heads.net/avatar/" + id + "/64");
    }
}
