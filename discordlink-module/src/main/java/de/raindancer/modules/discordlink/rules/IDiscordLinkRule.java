package de.raindancer.modules.discordlink.rules;

/** A rule belonging to this module: decides, and does nothing else. No side effects, safe from any thread. */
public interface IDiscordLinkRule {

    /** What this rule decides, for a diagnostic. */
    String describe();
}
