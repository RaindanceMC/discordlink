package de.raindancer.modules.discordlink.service;

import de.raindancer.core.moderation.whitelist.NetworkWhitelist;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Whitelists on the network's list (RainsCore 2.38.0): the proxy lets them in at once, every server mirrors it.
 * Database I/O, never on the server thread. Vanilla's own list where Core has none to give.
 */
public final class CoreWhitelist implements Whitelist {

    private final Supplier<Optional<NetworkWhitelist>> network;
    private final Whitelist vanilla;

    public CoreWhitelist(Supplier<Optional<NetworkWhitelist>> network, Whitelist vanilla) {
        this.network = network;
        this.vanilla = vanilla;
    }

    @Override
    public boolean isWhitelisted(UUID player) {
        return network.get().map(list -> list.isWhitelisted(player)).orElseGet(() -> vanilla.isWhitelisted(player));
    }

    @Override
    public void add(UUID player, String name, String by) {
        Optional<NetworkWhitelist> list = network.get();
        if (list.isEmpty() || !list.get().add(player, name, by)) {
            vanilla.add(player, name, by);
        }
    }
}
