package de.raindancer.modules.discordlink.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.discordlink.DiscordLinkServices;
import de.raindancer.modules.discordlink.service.DiscordBot;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.function.Supplier;

/**
 * {@code /discordlink status|restart|token <token>|token clear}. The token only from the console: typed in chat it
 * lands in the chat log and on everybody's screen who watches a stream.
 */
public final class DiscordLinkAdminCommand implements IDiscordLinkCommand {

    private final Supplier<DiscordLinkServices> services;

    public DiscordLinkAdminCommand(Supplier<DiscordLinkServices> services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "the Discord link bot: status, restart, token";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        DiscordLinkServices live = services.get();
        CommandSender sender = source.getSender();
        String what = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (what) {
            case "status" -> Scheduling.async(live.plugin(), () ->
                    live.messages().send(sender, "discordlink.admin.status", "status", live.bot().status()));
            case "restart" -> {
                live.messages().send(sender, "discordlink.admin.restarting");
                Scheduling.async(live.plugin(), live.bot()::restart);
            }
            case "token" -> {
                if (!(sender instanceof ConsoleCommandSender)) {
                    live.messages().send(sender, "discordlink.admin.console-only");
                    return;
                }
                if (args.length < 2) {
                    live.messages().send(sender, "discordlink.admin.usage");
                    return;
                }
                boolean clear = args[1].equalsIgnoreCase("clear");
                Scheduling.async(live.plugin(), () -> {
                    boolean done = clear ? live.secrets().remove(DiscordBot.TOKEN_SECRET)
                            : live.secrets().putText(DiscordBot.TOKEN_SECRET, args[1].strip());
                    live.messages().send(sender, done ? (clear ? "discordlink.admin.token-cleared" : "discordlink.admin.token-set")
                            : "discordlink.admin.token-failed");
                    if (done) {
                        live.bot().restart();
                    }
                });
            }
            default -> live.messages().send(sender, "discordlink.admin.usage");
        }
    }
}
