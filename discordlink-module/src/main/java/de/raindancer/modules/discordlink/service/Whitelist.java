package de.raindancer.modules.discordlink.service;

import java.util.UUID;

/**
 * The whitelist, as this module sees it: the network's ({@link CoreWhitelist}) — RainsCore's list that
 * RainsNetworkProxy checks at the door — or vanilla's on a server without it.
 */
public interface Whitelist {

    boolean isWhitelisted(UUID player);

    /** @param by who did it, for the record: {@code discord:<id>} */
    void add(UUID player, String name, String by);
}
