package de.raindancer.modules.discordlink.hush;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Tells the {@link PlaytimeTracker} who is online, so its tick never has to ask Bukkit from another thread. */
public final class PlaytimeListener implements Listener {

    private final PlaytimeTracker tracker;

    public PlaytimeListener(PlaytimeTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        tracker.joined(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tracker.left(event.getPlayer().getUniqueId());
    }
}
