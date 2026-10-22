package de.raindancer.modules.discordlink;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.discordlink.listener.JoinGateListener;
import de.raindancer.modules.discordlink.rules.CodeRule;
import de.raindancer.modules.discordlink.rules.LinkRule;
import de.raindancer.modules.discordlink.rules.RoleSyncRule;
import de.raindancer.modules.discordlink.service.BukkitWhitelist;
import de.raindancer.modules.discordlink.service.CodeService;
import de.raindancer.modules.discordlink.service.CorePlayerFacts;
import de.raindancer.modules.discordlink.service.InfoService;
import de.raindancer.modules.discordlink.service.DiscordBot;
import de.raindancer.modules.discordlink.service.LinkService;
import de.raindancer.modules.discordlink.service.RoleSyncService;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;

import java.security.SecureRandom;
import java.util.List;

/**
 * Lilly's Discord and her Minecraft servers, joined: somebody who is not whitelisted gets a code on the kick
 * screen, types it into Discord's /link, and is whitelisted with their account linked. Discord roles give
 * LuckPerms groups. One bot for the whole network, run by whichever server holds the role.
 */
public final class DiscordLinkModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("discordlink", "Discord Link", "0.5.0")
            .describedAs("Whitelisting and account links through Discord's /link; Discord roles give LuckPerms groups")
            .by("Raindancer118");

    /** How often each server asks whether it should run the bot. A third of the network lease. */
    private static final long TICK_SECONDS = 5;

    private DiscordBot bot;

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        SettingsStore<DiscordLinkSettings> settings = context.settings(DiscordLinkSettings.class, DiscordLinkSettings.DEFAULTS);
        context.core().messages().defineFrom(DiscordLinkModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        context.core().messages().seriousFrom(DiscordLinkModule.class.getResourceAsStream("messages-serious.yml"));

        LinkStore store = new LinkStore(context.core().databases().shared(LinkStore.NAME, LinkStore.SCHEMA));
        RoleConfigStore roles = new RoleConfigStore(YamlStore.network(context.dataFolder().resolve("roles.yml")));
        context.closeWith(roles.follow());
        Scheduling.async(context.plugin(), roles::reload);

        CodeService codes = new CodeService(store, new CodeRule(), new SecureRandom(), System::currentTimeMillis,
                settings.current());
        BukkitWhitelist whitelist = new BukkitWhitelist(context.plugin(), context.plugin().getServer());
        LinkService links = new LinkService(codes, store, roles, new LinkRule(), whitelist, System::currentTimeMillis);
        RoleSyncService sync = new RoleSyncService(() -> context.core().groups(), roles, store, new RoleSyncRule(), context.log());
        bot = new DiscordBot(context.plugin(), context.plugin().getServer(), context.core().messages(), context.log(),
                context.core().secrets(), context.core().networkRoles(), links, sync, roles, whitelist,
                () -> context.core().groups(), new InfoService(new CorePlayerFacts(context.core(), store)), settings.current());
        settings.onChange(codes::settings);
        settings.onChange(links::settings);
        settings.onChange(sync::settings);
        settings.onChange(bot::settings);

        DiscordLinkServices services = new DiscordLinkServices(context.plugin(), context.plugin().getServer(),
                context.core().messages(), settings::current, context.core().secrets(), codes, links, bot);
        context.listener(new JoinGateListener(services));
        DiscordLinkCommands.ready(services);

        var timer = Scheduling.asyncTimer(context.plugin(), 2, TICK_SECONDS, task -> bot.tick());
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
        context.log().info("Discord link is up; the bot runs on whichever server holds '{}'.", DiscordBot.ROLE);
    }

    @Override
    public void disable() {
        DiscordLinkCommands.stopped();
        if (bot != null) {
            bot.stop();
        }
    }

    @Override
    public List<ModuleCommand> commands() {
        return DiscordLinkCommands.declared();
    }
}
