package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.PlayerCard;
import de.raindancer.modules.discordlink.model.RoleConfig;
import de.raindancer.modules.discordlink.rules.RoleSyncRule;
import de.raindancer.modules.discordlink.service.InfoService;
import de.raindancer.modules.discordlink.service.LinkService;
import de.raindancer.modules.discordlink.service.PlayerResolver;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What Hush asks of this server (protocol v2, see Hush's docs/MINECRAFT.md): redeem a link code, unlink, set a
 * player's groups, list the links, build a player card. Every action is answered with a {@code result} event whose
 * {@code ref} is the action's id. An id seen twice (Hush asks again when an answer got lost) is answered again from
 * memory, never done twice.
 *
 * <p>Actions touch the database and LuckPerms, so they run on {@code async}, never on the feed's thread.
 */
public final class HushActions {

    private static final int REMEMBER = 300;
    private static final int MAX_LINKS = 400;

    private final LinkService links;
    private final InfoService info;
    private final Supplier<PermissionGroups> groups;
    private final RoleSyncRule rule;
    private final BiConsumer<String, Supplier<DataObject>> answer;
    private final LongSupplier clock;
    private final Executor async;
    private final HushGroupWatcher watcher;
    private final PlayerResolver resolver;
    private final LinkNotes notes;

    private final Map<String, DataObject> answered = new LinkedHashMap<>();

    /** @param answer {@link HushFeed#offerNow}: somebody is waiting for these */
    public HushActions(LinkService links, InfoService info, Supplier<PermissionGroups> groups, RoleSyncRule rule,
                       BiConsumer<String, Supplier<DataObject>> answer, LongSupplier clock, Executor async,
                       HushGroupWatcher watcher, PlayerResolver resolver, LinkNotes notes) {
        this.links = links;
        this.info = info;
        this.groups = groups;
        this.rule = rule;
        this.answer = answer;
        this.clock = clock;
        this.async = async;
        this.watcher = watcher;
        this.resolver = resolver;
        this.notes = notes;
    }

    /** Takes one action off the feed's thread at once; the work and its answer follow. */
    public void handle(DataObject action) {
        String id = action.getString("id", "");
        String type = action.getString("type", "");
        if (id.isBlank() || type.isBlank()) {
            return;
        }
        DataObject before;
        synchronized (answered) {
            before = answered.get(id);
        }
        if (before != null) {
            answer.accept("result", () -> before);
            return;
        }
        async.execute(() -> {
            DataObject result;
            try {
                result = run(id, type, action);
            } catch (RuntimeException failed) {
                result = HushEvents.result(clock.getAsLong(), id, false, "error", DataObject.empty()
                        .put("message", failed.getClass().getSimpleName()));
            }
            DataObject done = result;
            synchronized (answered) {
                answered.put(id, done);
                if (answered.size() > REMEMBER) {
                    answered.remove(answered.keySet().iterator().next());
                }
            }
            answer.accept("result", () -> done);
        });
    }

    private DataObject run(String id, String type, DataObject action) {
        return switch (type) {
            case "redeem" -> redeem(id, action.getString("code", ""), action.getString("discord", ""));
            case "unlink" -> unlink(id, action.getString("discord", ""), action.getString("player", ""), action.getString("by", ""));
            case "link" -> link(id, action.getString("discord", ""), action.getString("player", ""), action.getString("by", ""));
            case "set_groups" -> setGroups(id, action);
            case "sync_links" -> syncLinks(id);
            case "player" -> player(id, action.getString("name", ""));
            default -> HushEvents.result(clock.getAsLong(), id, false, "unknown_action", null);
        };
    }

    private DataObject redeem(String id, String code, String discord) {
        if (discord.isBlank()) {
            return HushEvents.result(clock.getAsLong(), id, false, "failed", null);
        }
        // Hush's network has no whitelist: a join code only links.
        LinkService.Result result = links.redeem(code, discord, List.of(), false);
        boolean ok = switch (result.outcome()) {
            case LINKED, LINKED_AND_WHITELISTED, WHITELISTED_AGAIN, ALREADY_YOURS -> true;
            default -> false;
        };
        String outcome = switch (result.outcome()) {
            case LINKED, LINKED_AND_WHITELISTED, WHITELISTED_AGAIN -> "linked";
            default -> result.outcome().name().toLowerCase(java.util.Locale.ROOT);
        };
        DataObject data = DataObject.empty();
        if (result.player() != null) {
            links.byName(result.player()).ifPresentOrElse(
                    link -> data.put("player", HushEvents.linkEntry(link.player(), link.name(), link.discord()).getObject("player")),
                    () -> data.put("player", DataObject.empty().put("name", result.player())));
        }
        return HushEvents.result(clock.getAsLong(), id, ok, outcome, data);
    }

    /** Staff link by name or UUID: no code, the player may never have joined. */
    private DataObject link(String id, String discord, String typed, String by) {
        if (discord.isBlank()) {
            return HushEvents.result(clock.getAsLong(), id, false, "failed", null);
        }
        Optional<PlayerResolver.Resolved> found = resolver.resolve(typed);
        if (found.isEmpty()) {
            return HushEvents.result(clock.getAsLong(), id, false, "unknown_player", null);
        }
        PlayerResolver.Resolved who = found.get();
        if (!by.isBlank()) {
            notes.note(who.id(), by);
        }
        LinkService.Result result = links.adopt(who.id(), who.name(), discord);
        boolean ok = result.outcome() == LinkService.Outcome.LINKED || result.outcome() == LinkService.Outcome.ALREADY_YOURS;
        if (result.outcome() != LinkService.Outcome.LINKED) {
            notes.take(who.id());
        }
        return HushEvents.result(clock.getAsLong(), id, ok, result.outcome() == LinkService.Outcome.LINKED ? "linked"
                : result.outcome().name().toLowerCase(java.util.Locale.ROOT),
                DataObject.empty().put("player", DataObject.empty().put("name", who.name()).put("uuid", who.id().toString())));
    }

    private DataObject unlink(String id, String discord, String typed, String by) {
        List<Link> mine = new ArrayList<>();
        if (!typed.isBlank()) {
            resolver.resolve(typed).flatMap(who -> links.linkOf(who.id())).ifPresent(mine::add);
        } else {
            mine.addAll(links.linksOf(discord));
        }
        DataArray names = DataArray.empty();
        for (Link link : mine) {
            if (!by.isBlank()) {
                notes.note(link.player(), by);
            }
            if (links.unlink(link.player())) {
                names.add(link.name());
            }
        }
        for (Link link : mine) {
            if (names.length() == 0) {
                notes.take(link.player());
            }
        }
        return HushEvents.result(clock.getAsLong(), id, names.length() > 0, names.length() > 0 ? "unlinked" : "not_linked",
                DataObject.empty().put("accounts", names));
    }

    private DataObject setGroups(String id, DataObject action) {
        UUID player;
        try {
            player = UUID.fromString(action.getString("player", ""));
        } catch (IllegalArgumentException notAnId) {
            return HushEvents.result(clock.getAsLong(), id, false, "bad_player", null);
        }
        PermissionGroups permissions = groups.get();
        if (permissions == PermissionGroups.NONE) {
            return HushEvents.result(clock.getAsLong(), id, false, "no_groups", null);
        }
        Set<String> wanted = strings(action, "groups");
        Set<String> managed = strings(action, "managed");
        managed.addAll(wanted);
        Map<String, String> identity = new LinkedHashMap<>();
        managed.forEach(group -> identity.put(group, group));
        RoleSyncRule.Change change = rule.judge(new RoleConfig(Set.of(), identity), wanted, permissions.directGroupsOf(player));
        DataArray joined = DataArray.empty();
        DataArray left = DataArray.empty();
        DataArray failed = DataArray.empty();
        change.join().forEach(group -> (permissions.join(player, group) ? joined : failed).add(group));
        change.leave().forEach(group -> (permissions.leave(player, group) ? left : failed).add(group));
        // What we just changed ourselves is not news to tell Discord again.
        watcher.remember(player, permissions.directGroupsOf(player));
        return HushEvents.result(clock.getAsLong(), id, failed.length() == 0, failed.length() == 0 ? "applied" : "partly",
                DataObject.empty().put("joined", joined).put("left", left).put("failed", failed)
                        .put("player", DataObject.empty().put("uuid", player.toString())));
    }

    private DataObject syncLinks(String id) {
        List<DataObject> entries = new ArrayList<>();
        for (Link link : links.all()) {
            if (entries.size() < MAX_LINKS) {
                entries.add(HushEvents.linkEntry(link.player(), link.name(), link.discord()));
            }
        }
        long at = clock.getAsLong();
        answer.accept("links", () -> HushEvents.links(at, entries));
        return HushEvents.result(at, id, true, "listed", DataObject.empty().put("count", entries.size()));
    }

    private DataObject player(String id, String name) {
        Optional<PlayerCard> card = info.card(name);
        if (card.isEmpty()) {
            return HushEvents.result(clock.getAsLong(), id, false, "unknown_player", null);
        }
        return HushEvents.result(clock.getAsLong(), id, true, "card", cardData(card.get()));
    }

    static DataObject cardData(PlayerCard c) {
        DataObject data = DataObject.empty().put("name", c.name()).put("uuid", c.id().toString())
                .put("playtimeMinutes", c.playtimeMinutes()).put("linked", c.linked())
                .put("achievements", c.achievements()).put("achievementPoints", c.achievementPoints());
        c.onlineOn().ifPresent(server -> data.put("onlineOn", server));
        c.lastSeen().ifPresent(at -> data.put("lastSeen", at));
        c.lastServer().ifPresent(server -> data.put("lastServer", server));
        c.firstSeen().ifPresent(at -> data.put("firstSeen", at));
        c.rank().ifPresent(rank -> data.put("rank", rank));
        c.balance().ifPresent(balance -> data.put("balance", balance));
        c.guild().ifPresent(guild -> data.put("guild", guild));
        c.job().ifPresent(job -> data.put("job", job));
        return data;
    }

    private static Set<String> strings(DataObject action, String key) {
        Set<String> values = new LinkedHashSet<>();
        if (action.hasKey(key) && !action.isNull(key)) {
            DataArray array = action.getArray(key);
            for (int i = 0; i < array.length(); i++) {
                String value = array.getString(i, "").strip();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    /** For tests and status. */
    Collection<String> remembered() {
        synchronized (answered) {
            return List.copyOf(answered.keySet());
        }
    }
}
