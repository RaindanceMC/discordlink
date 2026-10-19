package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.DiscordLinkSettings;

/** A service belonging to this module: does, and asks a rule rather than deciding. Keeps a settings snapshot. */
public interface IDiscordLinkService {

    void settings(DiscordLinkSettings settings);
}
