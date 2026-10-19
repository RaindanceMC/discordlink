package de.raindancer.modules.discordlink.model;

/** Why a code was handed out. */
public enum Purpose {
    /** Shown on the kick screen to a player who is not whitelisted: redeeming it whitelists and links. */
    JOIN,
    /** Asked for in game by a whitelisted player who wants their Discord account linked. */
    LINK
}
