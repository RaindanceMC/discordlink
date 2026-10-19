package de.raindancer.modules.discordlink.service;

import de.raindancer.core.platform.util.Scheduling;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Whitelists through vanilla. RainsCore's network whitelist hears the change and writes it to the network
 * document, so every server that takes part lets the player in — the module never touches that document itself.
 */
public final class BukkitWhitelist implements Whitelist {

    private final Plugin plugin;
    private final Server server;

    public BukkitWhitelist(Plugin plugin, Server server) {
        this.plugin = plugin;
        this.server = server;
    }

    @Override
    public boolean isWhitelisted(UUID player) {
        return server.getOfflinePlayer(player).isWhitelisted();
    }

    @Override
    public void add(UUID player, String name) {
        Scheduling.global(plugin, () -> server.getOfflinePlayer(player).setWhitelisted(true));
    }
}
