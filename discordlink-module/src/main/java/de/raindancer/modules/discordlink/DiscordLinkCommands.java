package de.raindancer.modules.discordlink;

import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.discordlink.command.DiscordLinkAdminCommand;
import de.raindancer.modules.discordlink.command.LinkCommand;

import java.util.List;

/**
 * Declared at bootstrap, filled in when the module starts — Paper registers commands before any plugin is enabled,
 * so the commands point at {@link #require} until then.
 */
public final class DiscordLinkCommands {

    public static final String ADMIN_NODE = "rains.discordlink.admin";

    private static volatile DiscordLinkServices services;

    private DiscordLinkCommands() {
    }

    public static List<ModuleCommand> declared() {
        return List.of(
                ModuleCommand.of("link", "Shows a code to link your Discord account", new LinkCommand(DiscordLinkCommands::require))
                        .aliased("discord")
                        .taking("(nothing) — a code to type into Discord's /link", "unlink — unlinks your account"),
                ModuleCommand.of("discordlink", "The Discord link bot: status, restart, token",
                                new DiscordLinkAdminCommand(DiscordLinkCommands::require))
                        .needing(ADMIN_NODE)
                        .taking("status", "restart", "token <token> | token clear — console only"));
    }

    static void ready(DiscordLinkServices live) {
        services = live;
    }

    static void stopped() {
        services = null;
    }

    private static DiscordLinkServices require() {
        DiscordLinkServices live = services;
        if (live == null) {
            throw new IllegalStateException("the Discord link module is not running");
        }
        return live;
    }
}
