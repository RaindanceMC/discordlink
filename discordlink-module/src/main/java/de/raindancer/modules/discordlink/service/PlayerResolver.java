package de.raindancer.modules.discordlink.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Minecraft account from what staff type: a name or a UUID, joined or never joined. A name this server knows is
 * answered from here, any other from Mojang's profile service. Blocks on the network: async threads only.
 */
public final class PlayerResolver implements IDiscordLinkService {

    public record Resolved(UUID id, String name) {
    }

    /** What this server already knows: its online and cached players, lower-case name or UUID in, the account out. */
    public interface Known {
        Optional<Resolved> byName(String name);

        Optional<String> nameOf(UUID id);
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UUID_TEXT = Pattern.compile("([0-9a-fA-F]{8})-?([0-9a-fA-F]{4})-?([0-9a-fA-F]{4})-?([0-9a-fA-F]{4})-?([0-9a-fA-F]{12})");
    private static final Pattern JSON_ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");
    private static final Pattern JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{1,16})\"");

    private final Known known;
    private final Function<String, Optional<String>> fetch;
    private final Function<String, Optional<Resolved>> core;

    /** @param fetch an HTTPS GET: the body, or empty for 404 and every failure */
    public PlayerResolver(Known known, Function<String, Optional<String>> fetch) {
        this(known, fetch, typed -> Optional.empty());
    }

    /** @param core RainsCore's ProfileLookup (2.74.0), asked first: it also remembers what Mojang answered; empty on an older Core */
    public PlayerResolver(Known known, Function<String, Optional<String>> fetch, Function<String, Optional<Resolved>> core) {
        this.known = known;
        this.fetch = fetch;
        this.core = core;
    }

    @Override
    public void settings(de.raindancer.modules.discordlink.DiscordLinkSettings settings) {
        // nothing to configure
    }

    public Optional<Resolved> resolve(String typed) {
        String text = typed == null ? "" : typed.strip();
        Optional<Resolved> viaCore = core.apply(text);
        if (viaCore.isPresent()) {
            return viaCore;
        }
        Matcher uuid = UUID_TEXT.matcher(text);
        if (uuid.matches()) {
            UUID id = UUID.fromString(String.join("-", uuid.group(1), uuid.group(2), uuid.group(3), uuid.group(4), uuid.group(5)));
            String name = known.nameOf(id).or(() -> mojangName(id)).orElse(null);
            return Optional.of(new Resolved(id, name == null ? id.toString().substring(0, 8) : name));
        }
        if (!NAME.matcher(text).matches()) {
            return Optional.empty();
        }
        return known.byName(text).or(() -> mojangId(text));
    }

    private Optional<Resolved> mojangId(String name) {
        return fetch.apply("https://api.mojang.com/users/profiles/minecraft/" + name).flatMap(body -> {
            Matcher id = JSON_ID.matcher(body);
            Matcher exact = JSON_NAME.matcher(body);
            if (!id.find()) {
                return Optional.empty();
            }
            return Optional.of(new Resolved(fromPlain(id.group(1)), exact.find() ? exact.group(1) : name));
        });
    }

    private Optional<String> mojangName(UUID id) {
        return fetch.apply("https://sessionserver.mojang.com/session/minecraft/profile/" + id.toString().replace("-", ""))
                .flatMap(body -> {
                    Matcher name = JSON_NAME.matcher(body);
                    return name.find() ? Optional.of(name.group(1)) : Optional.empty();
                });
    }

    private static UUID fromPlain(String hex) {
        return UUID.fromString(hex.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }
}
