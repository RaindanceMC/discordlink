package de.raindancer.modules.discordlink.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.discordlink.DiscordLinkServices;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** {@code /link} — for a whitelisted player: a code to type into Discord's /link. {@code /link unlink} undoes it. */
public final class LinkCommand implements IDiscordLinkCommand {

    private final Supplier<DiscordLinkServices> services;

    public LinkCommand(Supplier<DiscordLinkServices> services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "shows a code to link your Discord account";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        DiscordLinkServices live = services.get();
        if (!(source.getSender() instanceof Player player)) {
            live.messages().send(source.getSender(), "discordlink.only-a-player");
            return;
        }
        boolean unlink = args.length > 0 && args[0].equalsIgnoreCase("unlink");
        Scheduling.async(live.plugin(), () -> {
            String key;
            Object[] values = {};
            if (unlink) {
                key = live.links().unlink(player.getUniqueId()) ? "discordlink.link.unlinked" : "discordlink.link.not-linked";
            } else if (live.links().linkOf(player.getUniqueId()).isPresent()) {
                key = "discordlink.link.already";
            } else if (!live.bot().runningSomewhere()) {
                key = "discordlink.not-running";
            } else {
                var code = live.links().codeToLink(player.getUniqueId(), player.getName());
                key = code.isPresent() ? "discordlink.link.code" : "discordlink.link.failed";
                values = new Object[]{"code", code.orElse(""), "minutes", String.valueOf(live.codes().lifetimeMinutes())};
            }
            String finalKey = key;
            Object[] finalValues = values;
            Scheduling.entity(live.plugin(), player, () -> live.messages().send(player, finalKey, finalValues));
        });
    }
}
