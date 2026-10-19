package de.raindancer.modules.discordlink.rules;

import de.raindancer.modules.discordlink.model.Link;

import java.util.List;
import java.util.Optional;

/**
 * May this Discord account take this Minecraft account? One account per person — so nobody whitelists their
 * friends — unless they hold one of the roles the admins picked. Pure: decides, changes nothing.
 */
public final class LinkRule implements IDiscordLinkRule {

    @Override
    public String describe() {
        return "whether a Discord account may take a Minecraft account";
    }


    public enum Verdict { OK, ALREADY_YOURS, SOMEONE_ELSES, ONE_IS_ENOUGH }

    /**
     * @param owner       who holds the player now, if anybody
     * @param theirs      the Discord account's links as they are
     * @param mayHaveMore whether the account holds a whitelister role
     */
    public Verdict judge(String discord, Optional<Link> owner, List<Link> theirs, boolean mayHaveMore) {
        if (owner.isPresent()) {
            return owner.get().discord().equals(discord) ? Verdict.ALREADY_YOURS : Verdict.SOMEONE_ELSES;
        }
        if (!theirs.isEmpty() && !mayHaveMore) {
            return Verdict.ONE_IS_ENOUGH;
        }
        return Verdict.OK;
    }
}
