package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What Hush last told this server about the referral giveaway (the {@code giveaway} action): the giveaway itself and,
 * per linked member, their tickets, referral link and friends. {@link #NONE} is "no giveaway is running". Immutable.
 */
public record Giveaway(String title, long endsAt, String rulesUrl, Rules rules, Map<UUID, Member> players) {

    /** A friend counts with {@code minutes} in all on at least {@code days} days of {@code dayMinutes} each. */
    public record Rules(int minutes, int days, int dayMinutes) {
    }

    public record Friend(String name, int minutes, int days, boolean counted) {
    }

    /** {@code link} is null while the member has not taken a referral link; {@code counted} is how many friends count. */
    public record Member(int tickets, String link, int counted, List<Friend> friends) {
    }

    public static final Giveaway NONE = new Giveaway(null, 0, null, new Rules(0, 0, 0), Map.of());

    private static final Member NOBODY = new Member(0, null, 0, List.of());
    private static final int NAME_MAX = 32;

    public boolean running() {
        return title != null;
    }

    public Member member(UUID player) {
        return players.getOrDefault(player, NOBODY);
    }

    /**
     * Reads the action's fields. A missing {@code title} key, or a field of the wrong kind, makes it no giveaway at
     * all (empty), so a half-read snapshot is never stored. A link that is not a plain https address is dropped, not
     * made clickable and not a reason to refuse everything else.
     */
    public static Optional<Giveaway> parse(DataObject data) {
        try {
            if (!data.hasKey("title")) {
                return Optional.empty();
            }
            if (data.isNull("title") || data.getString("title").isBlank()) {
                return Optional.of(NONE);
            }
            if (!data.hasKey("endsAt") || data.isNull("endsAt") || !data.isType("endsAt", net.dv8tion.jda.api.utils.data.DataType.INT)) {
                return Optional.empty();
            }
            DataObject rules = data.getObject("rules");
            Rules need = new Rules(count(rules, "minutes"), count(rules, "days"), count(rules, "dayMinutes"));
            Map<UUID, Member> players = new LinkedHashMap<>();
            if (data.hasKey("players") && !data.isNull("players")) {
                DataArray array = data.getArray("players");
                for (int i = 0; i < array.length(); i++) {
                    DataObject player = array.getObject(i);
                    players.put(UUID.fromString(player.getString("uuid")), member(player));
                }
            }
            return Optional.of(new Giveaway(data.getString("title").strip(), data.getLong("endsAt"),
                    url(data, "rulesUrl"), need, Map.copyOf(players)));
        } catch (RuntimeException notAGiveaway) {
            return Optional.empty();
        }
    }

    private static Member member(DataObject player) {
        List<Friend> friends = new ArrayList<>();
        if (player.hasKey("friends") && !player.isNull("friends")) {
            DataArray array = player.getArray("friends");
            for (int i = 0; i < array.length(); i++) {
                DataObject friend = array.getObject(i);
                String name = friend.getString("name").strip();
                friends.add(new Friend(name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name,
                        count(friend, "minutes"), count(friend, "days"), friend.getBoolean("counted")));
            }
        }
        return new Member(count(player, "tickets"), url(player, "link"), player.hasKey("counted") ? count(player, "counted") : 0,
                List.copyOf(friends));
    }

    /** A whole number of 0 or more; anything else is not a giveaway. */
    private static int count(DataObject data, String key) {
        if (!data.isType(key, net.dv8tion.jda.api.utils.data.DataType.INT)) {
            throw new IllegalArgumentException(key);
        }
        int value = data.getInt(key);
        if (value < 0) {
            throw new IllegalArgumentException(key);
        }
        return value;
    }

    private static String url(DataObject data, String key) {
        return data.hasKey(key) && !data.isNull(key) ? SafeUrl.https(data.getString(key)).orElse(null) : null;
    }

    /** The same shape the action has, so what is saved is read back by {@link #parse}. */
    public DataObject toJson() {
        DataObject out = DataObject.empty().put("title", title);
        if (!running()) {
            return out.put("players", DataArray.empty());
        }
        DataArray array = DataArray.empty();
        players.forEach((id, member) -> {
            DataArray friends = DataArray.empty();
            member.friends().forEach(friend -> friends.add(DataObject.empty().put("name", friend.name())
                    .put("minutes", friend.minutes()).put("days", friend.days()).put("counted", friend.counted())));
            array.add(DataObject.empty().put("uuid", id.toString()).put("tickets", member.tickets())
                    .put("link", member.link()).put("counted", member.counted()).put("friends", friends));
        });
        return out.put("endsAt", endsAt).put("rulesUrl", rulesUrl)
                .put("rules", DataObject.empty().put("minutes", rules.minutes()).put("days", rules.days())
                        .put("dayMinutes", rules.dayMinutes()))
                .put("players", array);
    }
}
