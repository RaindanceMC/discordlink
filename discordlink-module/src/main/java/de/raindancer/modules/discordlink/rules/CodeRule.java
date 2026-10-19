package de.raindancer.modules.discordlink.rules;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What a code looks like. Read off a kick screen and typed into Discord, so no 0/O and 1/I/L, and long enough
 * that guessing is pointless: 31^10 ≈ 8·10^14, against five wrong tries per Discord account per ten minutes.
 */
public final class CodeRule implements IDiscordLinkRule {

    @Override
    public String describe() {
        return "what a link code looks like, and whether something typed is one";
    }


    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 10;

    public String newCode(RandomGenerator random) {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** "abcde 12345", "ABCDE-12345 " → "ABCDE-12345"; anything that can't be a code → empty. Pure. */
    public Optional<String> normalise(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String letters = typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (letters.length() != LENGTH || !letters.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0)) {
            return Optional.empty();
        }
        return Optional.of(letters.substring(0, LENGTH / 2) + "-" + letters.substring(LENGTH / 2));
    }
}
