package de.raindancer.modules.discordlink.rules;

import de.raindancer.core.moderation.whitelist.JoinCode;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What a code looks like — RainsCore's {@link JoinCode}, which RainsNetworkProxy's codes match too.
 */
public final class CodeRule implements IDiscordLinkRule {

    @Override
    public String describe() {
        return "what a link code looks like, and whether something typed is one";
    }


    static final String ALPHABET = JoinCode.ALPHABET;
    static final int LENGTH = JoinCode.LENGTH;

    public String newCode(RandomGenerator random) {
        return JoinCode.newCode(random);
    }

    /** "abcde 12345", "ABCDE-12345 " → "ABCDE-12345"; anything that can't be a code → empty. Pure. */
    public Optional<String> normalise(String typed) {
        return JoinCode.normalise(typed);
    }
}
