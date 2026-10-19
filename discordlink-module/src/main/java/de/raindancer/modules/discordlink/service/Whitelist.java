package de.raindancer.modules.discordlink.service;

import java.util.UUID;

/**
 * The server's whitelist, as this module sees it. The Bukkit side whitelists through vanilla, which RainsCore's
 * network whitelist then carries to every server that takes part.
 */
public interface Whitelist {

    boolean isWhitelisted(UUID player);

    void add(UUID player, String name);
}
