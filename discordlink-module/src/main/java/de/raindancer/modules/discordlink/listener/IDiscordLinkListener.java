package de.raindancer.modules.discordlink.listener;

import org.bukkit.event.Listener;

import java.util.UUID;

/** A listener belonging to this module; {@link #forget} because a listener that remembers a player must be told when they leave. */
public interface IDiscordLinkListener extends Listener {

    void forget(UUID player);
}
