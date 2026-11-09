package de.raindancer.modules.discordlink.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.discordlink.DiscordLinkServices;
import de.raindancer.modules.discordlink.service.DiscordBot;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.function.Supplier;

/**
 * {@code /discordlink status|restart|token <token>|token clear|lookupkey <key>|lookupkey clear|hushtoken <token>|hushtoken clear|hush status}. The token only from the console: typed in chat it
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
            case "lookupkey" -> {
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
                    boolean done = clear ? live.secrets().remove(DiscordBot.LOOKUP_SECRET)
                            : live.secrets().putText(DiscordBot.LOOKUP_SECRET, args[1].strip());
                    live.messages().send(sender, done ? (clear ? "discordlink.admin.lookupkey-cleared" : "discordlink.admin.lookupkey-set")
                            : "discordlink.admin.token-failed");
                });
            }
            case "hushtoken" -> hushToken(live, sender, args, 1);
            case "link", "unlink" -> byHand(live, sender, what.equals("link"), args);
            case "hush" -> {
                String sub = args.length < 2 ? "status" : args[1].toLowerCase(java.util.Locale.ROOT);
                switch (sub) {
                    case "status" -> Scheduling.async(live.plugin(), () -> {
                        live.messages().send(sender, "discordlink.admin.hush-status");
                        live.hush().statusLines().forEach(line -> live.messages().send(sender, "discordlink.admin.hush-line", "line", line));
                    });
                    case "token" -> hushToken(live, sender, args, 2);
                    default -> live.messages().send(sender, "discordlink.admin.usage");
                }
            }
            default -> live.messages().send(sender, "discordlink.admin.usage");
        }
    }

    /** {@code link <name|uuid> <discord id>} / {@code unlink <name|uuid>}: staff link an account without a code. */
    private static void byHand(DiscordLinkServices live, CommandSender sender, boolean link, String[] args) {
        if (args.length < (link ? 3 : 2)) {
            live.messages().send(sender, "discordlink.admin.usage");
            return;
        }
        String discord = link ? args[2].replaceAll("[<@!>]", "") : "";
        if (link && !discord.matches("\\d{15,25}")) {
            live.messages().send(sender, "discordlink.admin.link-discord");
            return;
        }
        String staff = sender instanceof ConsoleCommandSender ? "Console" : sender.getName();
        Scheduling.async(live.plugin(), () -> {
            var found = live.resolver().resolve(args[1]);
            if (found.isEmpty()) {
                live.messages().send(sender, "discordlink.admin.link-unknown", "player", args[1]);
                return;
            }
            var who = found.get();
            live.notes().note(who.id(), staff);
            if (!link) {
                boolean done = live.links().unlink(who.id());
                if (!done) {
                    live.notes().take(who.id());
                }
                live.messages().send(sender, done ? "discordlink.admin.unlink-done" : "discordlink.admin.unlink-none", "player", who.name());
                return;
            }
            var result = live.links().adopt(who.id(), who.name(), discord);
            if (result.outcome() != de.raindancer.modules.discordlink.service.LinkService.Outcome.LINKED) {
                live.notes().take(who.id());
            }
            String key = switch (result.outcome()) {
                case LINKED -> "discordlink.admin.link-done";
                case ALREADY_YOURS -> "discordlink.admin.link-already";
                case SOMEONE_ELSES -> "discordlink.admin.link-taken";
                default -> "discordlink.admin.link-failed";
            };
            live.messages().send(sender, key, "player", who.name(), "discord", discord);
        });
    }

    /** The feed's token, console only and never repeated back: {@code args[at]} is the token or {@code clear}. */
    private static void hushToken(DiscordLinkServices live, CommandSender sender, String[] args, int at) {
        if (!(sender instanceof ConsoleCommandSender)) {
            live.messages().send(sender, "discordlink.admin.console-only");
            return;
        }
        if (args.length <= at) {
            live.messages().send(sender, "discordlink.admin.usage");
            return;
        }
        boolean clear = args[at].equalsIgnoreCase("clear");
        Scheduling.async(live.plugin(), () -> {
            boolean done = clear ? live.hush().clearToken() : live.hush().setToken(args[at]);
            live.messages().send(sender, done ? (clear ? "discordlink.admin.hushtoken-cleared" : "discordlink.admin.hushtoken-set")
                    : "discordlink.admin.token-failed");
        });
    }
}
