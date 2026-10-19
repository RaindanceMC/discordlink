package de.raindancer.modules.discordlink.model;

import java.util.UUID;

/** One Minecraft account belonging to one Discord account. A Discord account may hold several, a player only one. */
public record Link(UUID player, String name, String discord, long linkedAt) {
}
