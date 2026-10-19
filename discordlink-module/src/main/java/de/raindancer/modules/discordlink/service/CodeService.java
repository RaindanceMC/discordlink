package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.PendingCode;
import de.raindancer.modules.discordlink.model.Purpose;
import de.raindancer.modules.discordlink.rules.CodeRule;
import de.raindancer.modules.discordlink.store.LinkStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/**
 * Hands out codes and takes them back. Database I/O, so never on the server thread. Wrong guesses are counted per
 * Discord account on the bot's server only, which is the only place codes are redeemed.
 */
public final class CodeService implements IDiscordLinkService {

    static final int MOST_WRONG_GUESSES = 5;
    static final long GUESS_WINDOW_MILLIS = 10 * 60 * 1000L;

    public enum Failure { WRONG, TOO_MANY_TRIES }

    public record Redeemed(Optional<PendingCode> code, Failure failure) {
    }

    private record Guesses(int wrong, long since) {
    }

    private final LinkStore store;
    private final CodeRule rule;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private final Map<String, Guesses> guesses = new HashMap<>();
    private volatile long lifetimeMillis;

    public CodeService(LinkStore store, CodeRule rule, RandomGenerator random, LongSupplier clock, DiscordLinkSettings settings) {
        this.store = store;
        this.rule = rule;
        this.random = random;
        this.clock = clock;
        settings(settings);
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        lifetimeMillis = settings.codeMinutes() * 60_000L;
    }

    public long lifetimeMinutes() {
        return lifetimeMillis / 60_000L;
    }

    /** The same code again while it lasts, so every server and every retry shows the same one. Empty if the database is down. */
    public Optional<String> issue(UUID player, String name, Purpose purpose) {
        long now = clock.getAsLong();
        Optional<PendingCode> existing = store.codeFor(player, purpose, now);
        if (existing.isPresent()) {
            return Optional.of(existing.get().code());
        }
        PendingCode fresh = new PendingCode(rule.newCode(random), player, name, purpose, now + lifetimeMillis);
        return store.saveCode(fresh, now) ? Optional.of(fresh.code()) : Optional.empty();
    }

    public synchronized Redeemed redeem(String typed, String discord) {
        long now = clock.getAsLong();
        Guesses seen = guesses.get(discord);
        if (seen != null && now - seen.since() > GUESS_WINDOW_MILLIS) {
            guesses.remove(discord);
            seen = null;
        }
        if (seen != null && seen.wrong() >= MOST_WRONG_GUESSES) {
            return new Redeemed(Optional.empty(), Failure.TOO_MANY_TRIES);
        }
        Optional<PendingCode> code = rule.normalise(typed).flatMap(normalised -> store.take(normalised, now));
        if (code.isEmpty()) {
            guesses.put(discord, seen == null ? new Guesses(1, now) : new Guesses(seen.wrong() + 1, seen.since()));
            if (guesses.size() > 10_000) {
                guesses.clear();
            }
            return new Redeemed(Optional.empty(), Failure.WRONG);
        }
        guesses.remove(discord);
        return new Redeemed(code, null);
    }
}
