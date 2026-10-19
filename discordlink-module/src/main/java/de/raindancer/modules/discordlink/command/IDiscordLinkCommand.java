package de.raindancer.modules.discordlink.command;

import io.papermc.paper.command.brigadier.BasicCommand;

/** A command built at bootstrap: it holds a {@code Supplier} and captures nothing the module builds. */
public interface IDiscordLinkCommand extends BasicCommand {

    String describe();
}
