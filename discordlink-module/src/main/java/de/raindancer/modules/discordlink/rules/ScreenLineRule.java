package de.raindancer.modules.discordlink.rules;

import de.raindancer.core.world.time.Times;

import java.time.Duration;

/**
 * What the Discord screens say, as MiniMessage text. Never a Discord account: a screen is seen by everybody near it,
 * so it says whether somebody is linked, not to whom.
 */
public final class ScreenLineRule implements IDiscordLinkRule {

    /** @param linkedFor how long ago, null when unknown */
    public String you(boolean linked, Duration linkedFor) {
        String head = "<gold><bold>Your Discord</bold></gold>\n";
        if (!linked) {
            return head + "<gray>✘ Not linked <dark_gray>· <white>/link";
        }
        return head + "<green>✔ <white>Linked" + (linkedFor == null ? ""
                : "\n<gray>since <white>" + Times.brief(linkedFor) + " ago");
    }

    public String linked(long linkedPlayers, int onlineHere) {
        return "<gold><bold>Discord link</bold></gold>\n<gray>players linked <white>" + linkedPlayers
                + "\n<gray>online here <white>" + onlineHere;
    }

    @Override
    public String describe() {
        return "what the Discord screens say: a player's own link, and how many are linked";
    }
}
