package de.raindancer.modules.discordlink.service;

import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.PendingCode;
import de.raindancer.modules.discordlink.model.Purpose;
import de.raindancer.modules.discordlink.rules.LinkRule;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Turning a redeemed code into a link (and, for a join code, a whitelisting). Everything here is database I/O and
 * runs on a Discord or async thread.
 */
public final class LinkService implements IDiscordLinkService {

    public enum Outcome { LINKED, LINKED_AND_WHITELISTED, ALREADY_YOURS, SOMEONE_ELSES, ONE_IS_ENOUGH, WRONG_CODE, TOO_MANY_TRIES, FAILED }

    public record Result(Outcome outcome, String player, List<Link> yours) {
    }

    private final CodeService codes;
    private final LinkStore store;
    private final RoleConfigStore roles;
    private final LinkRule rule;
    private final Whitelist whitelist;
    private final LongSupplier clock;

    public LinkService(CodeService codes, LinkStore store, RoleConfigStore roles, LinkRule rule, Whitelist whitelist,
                       LongSupplier clock) {
        this.codes = codes;
        this.store = store;
        this.roles = roles;
        this.rule = rule;
        this.whitelist = whitelist;
        this.clock = clock;
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        // Nothing to swap: who may link what is the admins' role choice, read live from the role store.
    }

    /** Discord's /link: the code proves the Minecraft account is theirs. */
    public Result redeem(String typed, String discord, Collection<String> memberRoles) {
        CodeService.Redeemed redeemed = codes.redeem(typed, discord);
        if (redeemed.code().isEmpty()) {
            return new Result(redeemed.failure() == CodeService.Failure.TOO_MANY_TRIES ? Outcome.TOO_MANY_TRIES : Outcome.WRONG_CODE,
                    null, store.linksOf(discord));
        }
        PendingCode code = redeemed.code().get();
        List<Link> theirs = store.linksOf(discord);
        LinkRule.Verdict verdict = rule.judge(discord, store.linkOf(code.player()), theirs,
                roles.current().mayWhitelistOthers(memberRoles));
        switch (verdict) {
            case ALREADY_YOURS -> {
                return new Result(Outcome.ALREADY_YOURS, code.name(), theirs);
            }
            case SOMEONE_ELSES -> {
                return new Result(Outcome.SOMEONE_ELSES, code.name(), theirs);
            }
            case ONE_IS_ENOUGH -> {
                return new Result(Outcome.ONE_IS_ENOUGH, code.name(), theirs);
            }
            case OK -> {
            }
        }
        if (!store.link(new Link(code.player(), code.name(), discord, clock.getAsLong()))) {
            return new Result(Outcome.FAILED, code.name(), theirs);
        }
        boolean newlyWhitelisted = code.purpose() == Purpose.JOIN && !whitelist.isWhitelisted(code.player());
        if (code.purpose() == Purpose.JOIN) {
            whitelist.add(code.player(), code.name());
        }
        return new Result(newlyWhitelisted ? Outcome.LINKED_AND_WHITELISTED : Outcome.LINKED, code.name(), store.linksOf(discord));
    }

    public List<Link> linksOf(String discord) {
        return store.linksOf(discord);
    }

    public Optional<Link> linkOf(UUID player) {
        return store.linkOf(player);
    }

    public boolean unlink(UUID player) {
        return store.unlink(player);
    }

    public List<String> allDiscordIds() {
        return store.all().stream().map(Link::discord).distinct().toList();
    }

    public Optional<Link> byName(String name) {
        return store.all().stream().filter(link -> link.name().equalsIgnoreCase(name)).findFirst();
    }

    /** In-game /link: a code for an already whitelisted player to confirm in Discord. Empty if the database is down. */
    public Optional<String> codeToLink(UUID player, String name) {
        return codes.issue(player, name, Purpose.LINK);
    }
}
