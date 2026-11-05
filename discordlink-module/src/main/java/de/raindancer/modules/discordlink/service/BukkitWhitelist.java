package de.raindancer.modules.discordlink.service;

import de.raindancer.core.platform.util.Scheduling;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/** Whitelists through vanilla: for a server whose Core keeps no network whitelist. */
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
    public void add(UUID player, String name, String by) {
        Scheduling.global(plugin, () -> server.getOfflinePlayer(player).setWhitelisted(true));
    }
}
