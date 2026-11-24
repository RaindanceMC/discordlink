package de.raindancer.modules.discordlink.rules;

import de.raindancer.core.platform.util.Times;
import de.raindancer.modules.discordlink.hush.Giveaway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * What /giveaway says to one player, as message keys with their values, in the order they are shown. The words are in
 * messages.yml; this decides which lines there are.
 */
public final class GiveawayView implements IDiscordLinkRule {

    public static final int MAX_FRIENDS = 10;

    /** A message key and its placeholders as name, value, name, value … */
    public record Line(String key, List<Object> values) {

        public String value(String name) {
            for (int i = 0; i + 1 < values.size(); i += 2) {
                if (values.get(i).equals(name)) {
                    return String.valueOf(values.get(i + 1));
                }
            }
            throw new IllegalArgumentException("no value " + name + " in " + key);
        }
    }

    @Override
    public String describe() {
        return "the lines /giveaway shows: the giveaway, tickets, referral link, rules and friends";
    }

    /**
     * @param linked whether the player has a Minecraft-Discord link at all; without one they cannot take part
     * @param button an address as a clickable value for a message
     */
    public List<Line> lines(Giveaway giveaway, UUID player, boolean linked, long now, Function<String, Object> button) {
        if (!giveaway.running()) {
            return List.of(line("discordlink.giveaway.none"));
        }
        List<Line> lines = new ArrayList<>();
        long left = giveaway.endsAt() - now;
        lines.add(left > 0 ? line("discordlink.giveaway.head", "title", giveaway.title(), "left", Times.compact(left))
                : line("discordlink.giveaway.head-ended", "title", giveaway.title()));
        if (!linked) {
            lines.add(line("discordlink.giveaway.not-linked"));
            rules(giveaway, button, lines);
            return lines;
        }
        Giveaway.Member me = giveaway.member(player);
        lines.add(line("discordlink.giveaway.tickets", "tickets", me.tickets()));
        lines.add(me.link() == null ? line("discordlink.giveaway.no-link") : line("discordlink.giveaway.link", "button", button.apply(me.link())));
        rules(giveaway, button, lines);
        if (me.friends().isEmpty()) {
            lines.add(line("discordlink.giveaway.no-friends"));
            return lines;
        }
        lines.add(line("discordlink.giveaway.friends", "counted", me.counted()));
        Giveaway.Rules rules = giveaway.rules();
        for (Giveaway.Friend friend : me.friends().stream().limit(MAX_FRIENDS).toList()) {
            lines.add(friend.counted() ? line("discordlink.giveaway.friend-counts", "name", friend.name())
                    : line("discordlink.giveaway.friend-progress", "name", friend.name(), "minutes", friend.minutes(),
                            "need-minutes", rules.minutes(), "days", friend.days(), "need-days", rules.days()));
        }
        if (me.friends().size() > MAX_FRIENDS) {
            lines.add(line("discordlink.giveaway.friends-more", "more", me.friends().size() - MAX_FRIENDS));
        }
        return lines;
    }

    private static void rules(Giveaway giveaway, Function<String, Object> button, List<Line> lines) {
        if (giveaway.rulesUrl() != null) {
            lines.add(line("discordlink.giveaway.rules", "button", button.apply(giveaway.rulesUrl())));
        }
    }

    private static Line line(String key, Object... values) {
        return new Line(key, List.of(values));
    }
}
