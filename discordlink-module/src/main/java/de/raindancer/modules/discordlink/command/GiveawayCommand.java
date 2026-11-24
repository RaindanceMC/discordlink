package de.raindancer.modules.discordlink.command;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.discordlink.DiscordLinkServices;
import de.raindancer.modules.discordlink.rules.GiveawayView;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Supplier;

/** {@code /giveaway} — the running referral giveaway: time left, your tickets, your referral link and your friends. */
public final class GiveawayCommand implements IDiscordLinkCommand {

    private final Supplier<DiscordLinkServices> services;
    private final GiveawayView view = new GiveawayView();

    public GiveawayCommand(Supplier<DiscordLinkServices> services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "shows the running giveaway, your tickets, your referral link and your friends";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        DiscordLinkServices live = services.get();
        if (!(source.getSender() instanceof Player player)) {
            live.messages().send(source.getSender(), "discordlink.only-a-player");
            return;
        }
        // The link lookup is a database read.
        Scheduling.async(live.plugin(), () -> {
            var settings = live.settings().get();
            List<GiveawayView.Line> lines = view.lines(live.giveaways().current(), player.getUniqueId(),
                    live.links().linkOf(player.getUniqueId()).isPresent(), System.currentTimeMillis(), settings::button);
            Object[] wording = settings.wording();
            Scheduling.entity(live.plugin(), player, () -> {
                for (int i = 0; i < lines.size(); i++) {
                    GiveawayView.Line line = lines.get(i);
                    Object[] values = java.util.stream.Stream.concat(line.values().stream(), java.util.Arrays.stream(wording)).toArray();
                    if (i == 0) {
                        live.messages().send(player, line.key(), values);
                    } else {
                        live.messages().sendPlain(player, line.key(), values);
                    }
                }
            });
        });
    }
}
