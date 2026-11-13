package de.raindancer.modules.discordlink.command;

import de.raindancer.modules.discordlink.DiscordLinkServices;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.function.Supplier;

/** {@code /discord}, {@code /dc} — the server's Discord invite as a button. Without an invite set it is {@code /link}, as it used to be. */
public final class DiscordCommand implements IDiscordLinkCommand {

    private final Supplier<DiscordLinkServices> services;
    private final LinkCommand link;

    public DiscordCommand(Supplier<DiscordLinkServices> services) {
        this.services = services;
        this.link = new LinkCommand(services);
    }

    @Override
    public String describe() {
        return "shows the invite to the server's Discord";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        DiscordLinkServices live = services.get();
        var settings = live.settings().get();
        if (settings.inviteUrl().isEmpty()) {
            link.execute(source, args);
            return;
        }
        live.messages().send(source.getSender(), "discordlink.invite", settings.inviteValues().toArray());
    }
}
