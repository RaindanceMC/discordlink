package de.raindancer.modules.discordlink;

import de.raindancer.core.data.store.Secrets;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.discordlink.service.CodeService;
import de.raindancer.modules.discordlink.service.DiscordBot;
import de.raindancer.modules.discordlink.service.LinkService;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

/** The module's collaborators, handed to its listeners and commands. */
public record DiscordLinkServices(Plugin plugin, Server server, Messages messages, Supplier<DiscordLinkSettings> settings,
                                  Secrets secrets, CodeService codes, LinkService links, DiscordBot bot) {
}
