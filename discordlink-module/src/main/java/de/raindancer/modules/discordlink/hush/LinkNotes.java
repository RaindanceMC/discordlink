package de.raindancer.modules.discordlink.hush;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who made a link by hand, kept for the one link event that follows, so Hush can say "linked by …". */
public final class LinkNotes {

    private final Map<UUID, String> by = new ConcurrentHashMap<>();

    public void note(UUID player, String staff) {
        by.put(player, staff);
    }

    public Optional<String> take(UUID player) {
        return Optional.ofNullable(by.remove(player));
    }
}
