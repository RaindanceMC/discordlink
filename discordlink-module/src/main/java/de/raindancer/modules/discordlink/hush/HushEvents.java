package de.raindancer.modules.discordlink.hush;

import de.raindancer.core.moderation.feed.AdminModeNotice;
import de.raindancer.core.moderation.feed.AnticheatAlert;
import de.raindancer.core.moderation.feed.PunishmentNotice;
import de.raindancer.core.moderation.feed.ReportNotice;
import de.raindancer.core.moderation.punishment.Punishment;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** The JSON of each event type of Hush's Minecraft feed (protocol v2, see Hush's docs/MINECRAFT.md). Pure. */
public final class HushEvents {

    public static final int VERBOSE_MAX = 300;
    private static final int TEXT_MAX = 500;

    private HushEvents() {
    }

    public static DataObject anticheat(long at, AnticheatAlert alert) {
        DataObject event = DataObject.empty().put("type", "anticheat").put("at", at)
                .put("player", who(alert.name(), alert.player()))
                .put("check", cut(alert.check(), 64))
                .put("vl", Math.round(alert.level() * 100.0) / 100.0)
                .put("ping", alert.ping())
                .put("verbose", cut(alert.detail(), VERBOSE_MAX));
        if (alert.client() != null && !alert.client().isBlank()) {
            event.put("client", cut(alert.client(), 32));
        }
        if (alert.brand() != null && !alert.brand().isBlank()) {
            event.put("brand", cut(alert.brand(), 64));
        }
        return event;
    }

    /** Empty for what the feed does not carry: freezes and fines. */
    public static Optional<DataObject> punishment(long at, PunishmentNotice notice, Function<UUID, String> names) {
        Punishment p = notice.punishment();
        boolean temporary = p.endsAt() != null;
        String action = switch (p.kind()) {
            case BAN -> notice.lifted() ? "unban" : temporary ? "tempban" : "ban";
            case MUTE -> notice.lifted() ? "unmute" : temporary ? "tempmute" : "mute";
            case KICK -> "kick";
            case WARNING -> "warn";
            default -> null;
        };
        if (action == null) {
            return Optional.empty();
        }
        UUID staff = notice.lifted() ? p.lifter() : p.moderator();
        String reason = notice.lifted() ? p.lifterReason() : p.reason();
        DataObject event = DataObject.empty().put("type", "punishment").put("at", at).put("action", action)
                .put("player", who(names.apply(p.target()), p.target()))
                .put("staff", staff == null ? "Console" : cut(names.apply(staff), 64))
                .put("reason", cut(reason == null || reason.isBlank() ? "no reason given" : reason, TEXT_MAX))
                .put("id", cut(p.id(), 64));
        if (temporary && !notice.lifted()) {
            event.put("duration", Duration.between(p.givenAt(), p.endsAt()).toMillis());
        }
        return Optional.of(event);
    }

    public static DataObject report(long at, ReportNotice report) {
        return DataObject.empty().put("type", "report").put("at", at)
                .put("reporter", who(report.reporterName(), report.reporter()))
                .put("target", who(report.targetName(), report.target()))
                .put("reason", cut(report.reason(), TEXT_MAX));
    }

    public static DataObject server(long at, String state, String detail) {
        DataObject event = DataObject.empty().put("type", "server").put("at", at).put("state", state);
        if (detail != null && !detail.isBlank()) {
            event.put("detail", cut(detail, TEXT_MAX));
        }
        return event;
    }

    /** Staff went into or came out of admin mode; the duration is only on leaving, and only when known. */
    public static DataObject adminMode(long at, AdminModeNotice notice, Function<UUID, String> names) {
        DataObject event = DataObject.empty().put("type", "admin").put("at", at)
                .put("player", who(names.apply(notice.player()), notice.player()))
                .put("state", notice.entered() ? "enter" : "leave");
        if (!notice.entered() && notice.durationMillis() > 0) {
            event.put("duration", notice.durationMillis());
        }
        return event;
    }

    /** The answer to an action Hush sent (protocol v2): {@code ref} is the action's id. */
    public static DataObject result(long at, String ref, boolean ok, String outcome, DataObject data) {
        DataObject event = DataObject.empty().put("type", "result").put("at", at).put("ref", cut(ref, 64))
                .put("ok", ok).put("outcome", cut(outcome, 64));
        if (data != null) {
            event.put("data", data);
        }
        return event;
    }

    /** A Minecraft account was linked to a Discord account, or the link ended. */
    public static DataObject link(long at, String action, UUID player, String name, String discord) {
        return DataObject.empty().put("type", "link").put("at", at).put("action", action)
                .put("player", who(name, player)).put("discord", discord);
    }

    /** Every link there is, for Hush to compare with what it knows. */
    public static DataObject links(long at, java.util.List<DataObject> entries) {
        DataArray array = DataArray.empty();
        entries.forEach(array::add);
        return DataObject.empty().put("type", "links").put("at", at).put("entries", array);
    }

    /** Somebody's direct LuckPerms groups, after a change here. */
    public static DataObject groups(long at, UUID player, String name, String discord, java.util.Collection<String> groups) {
        DataArray array = DataArray.empty();
        groups.stream().sorted().forEach(array::add);
        return DataObject.empty().put("type", "groups").put("at", at).put("player", who(name, player))
                .put("discord", discord).put("groups", array);
    }

    public static DataObject linkEntry(UUID player, String name, String discord) {
        return DataObject.empty().put("player", who(name, player)).put("discord", discord);
    }

    public static String body(String server, List<DataObject> events) {
        return body(server, events, null);
    }

    /** @param access "whitelist" while this server only lets whitelisted players in, "open" otherwise; null = not said */
    public static String body(String server, List<DataObject> events, String access) {
        DataArray array = DataArray.empty();
        events.forEach(array::add);
        DataObject body = DataObject.empty().put("v", 2).put("server", server).put("events", array);
        if (access != null) {
            body.put("access", access);
        }
        return body.toString();
    }

    private static DataObject who(String name, UUID id) {
        return DataObject.empty().put("name", cut(name == null || name.isBlank() ? String.valueOf(id) : name, 64))
                .put("uuid", String.valueOf(id));
    }

    private static String cut(String text, int max) {
        String value = text == null ? "" : text;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
