package de.raindancer.modules.discordlink.store;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.modules.discordlink.model.RoleConfig;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The role choices made in Discord, as a network document: whichever server runs the bot, it reads the same.
 * Written from Discord's /mcadmin, so it is not a @Settings record (those are edited in game and shown in /settings).
 */
public final class RoleConfigStore {

    private final YamlStore document;
    private volatile RoleConfig current = RoleConfig.EMPTY;

    public RoleConfigStore(YamlStore document) {
        this.document = document;
    }

    /** Reads the document again whenever another server changed it. */
    public NetworkBus.Subscription follow() {
        return document.onChange(this::reload);
    }

    public RoleConfig current() {
        return current;
    }

    /** Database I/O: not on the server thread. */
    public void reload() {
        document.readIfReadable().ifPresent(yaml -> {
            Set<String> whitelisters = new HashSet<>(yaml.getStringList("whitelister-roles"));
            Map<String, String> groups = new LinkedHashMap<>();
            var section = yaml.getConfigurationSection("role-groups");
            if (section != null) {
                for (String role : section.getKeys(false)) {
                    String group = section.getString(role, "");
                    if (!group.isBlank()) {
                        groups.put(role, group);
                    }
                }
            }
            current = new RoleConfig(whitelisters, groups);
        });
    }

    /** Database I/O: not on the server thread. */
    public boolean save(RoleConfig config) {
        boolean written = document.update(yaml -> {
            yaml.set("whitelister-roles", config.whitelisters().stream().sorted().toList());
            yaml.set("role-groups", null);
            config.groups().forEach((role, group) -> yaml.set("role-groups." + role, group));
        });
        if (written) {
            current = config;
        }
        return written;
    }
}
