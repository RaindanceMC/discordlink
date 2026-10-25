package de.raindancer.modules.discordlink.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.rules.ScreenLineRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The Discord screens for Core's {@code /screen}. They are asked every two seconds for everybody near a screen, while
 * every answer is a database read — so each is kept for a while.
 */
public final class LinkScreens implements IDiscordLinkService {

    public static final List<String> KINDS = List.of("discord-you", "discord-linked");
    static final long TTL_MILLIS = 30_000;
    static final long COUNT_TTL_MILLIS = 60_000;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private record Cached(Optional<Link> link, long at) {
    }

    private final Function<UUID, Optional<Link>> links;
    private final LongSupplier linkedCount;
    private final LongSupplier clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final ScreenLineRule rule = new ScreenLineRule();
    private volatile long counted = -1;
    private volatile long countedAt;

    public LinkScreens(Function<UUID, Optional<Link>> links, LongSupplier linkedCount, LongSupplier clock) {
        this.links = links;
        this.linkedCount = linkedCount;
        this.clock = clock;
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        // Nothing configurable.
    }

    Optional<Link> link(UUID player) {
        long now = clock.getAsLong();
        Cached held = cache.get(player);
        if (held != null && now - held.at() < TTL_MILLIS) {
            return held.link();
        }
        if (cache.size() > 2_000) {
            cache.values().removeIf(old -> now - old.at() >= TTL_MILLIS);
        }
        Optional<Link> fresh = links.apply(player);
        cache.put(player, new Cached(fresh, now));
        return fresh;
    }

    long linkedCount() {
        long now = clock.getAsLong();
        if (counted < 0 || now - countedAt >= COUNT_TTL_MILLIS) {
            counted = linkedCount.getAsLong();
            countedAt = now;
        }
        return counted;
    }

    public void register(Screens screens, Plugin owner, Server server) {
        screens.register(owner, ScreenKind.personal("discord-you", "Your Discord",
                "Everybody's own: whether their Minecraft account is linked to Discord, and since when.",
                Material.NAME_TAG, this::you));
        screens.register(owner, ScreenKind.everyone("discord-linked", "Discord link",
                "How many players have linked their Discord, and how many of them are on this server.",
                Material.ENDER_EYE, () -> linked(server)));
    }

    private Component you(ScreenKind.Viewer viewer) {
        Optional<Link> link = link(viewer.id());
        return MINI.deserialize(rule.you(link.isPresent(), link.map(held ->
                Duration.ofMillis(Math.max(0, clock.getAsLong() - held.linkedAt()))).orElse(null)));
    }

    private Component linked(Server server) {
        int here = 0;
        for (Player player : server.getOnlinePlayers()) {
            if (link(player.getUniqueId()).isPresent()) {
                here++;
            }
        }
        return MINI.deserialize(rule.linked(linkedCount(), here));
    }
}
