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
import de.raindancer.modules.discordlink.service.CoreWhitelist;
import de.raindancer.modules.discordlink.service.Whitelist;
import de.raindancer.modules.discordlink.service.CodeService;
import de.raindancer.modules.discordlink.service.CorePlayerFacts;
import de.raindancer.modules.discordlink.service.InfoService;
import de.raindancer.modules.discordlink.service.DiscordBot;
import de.raindancer.modules.discordlink.service.LinkService;
import de.raindancer.modules.discordlink.service.RoleSyncService;
import de.raindancer.modules.discordlink.store.LinkStore;
import de.raindancer.modules.discordlink.store.RoleConfigStore;

import de.raindancer.modules.discordlink.hush.HttpHushTransport;
import de.raindancer.modules.discordlink.hush.HushActions;
import de.raindancer.modules.discordlink.hush.LinkNotes;
import de.raindancer.modules.discordlink.service.MojangHttp;
import de.raindancer.modules.discordlink.service.PlayerResolver;
import de.raindancer.modules.discordlink.hush.HushControl;
import de.raindancer.modules.discordlink.hush.HushEvents;
import de.raindancer.modules.discordlink.hush.HushGroupWatcher;
import de.raindancer.modules.discordlink.hush.HushFeed;
import de.raindancer.modules.discordlink.hush.HushHooks;
import de.raindancer.modules.discordlink.hush.HushServerListener;
import de.raindancer.modules.discordlink.hush.RunMarker;
import de.raindancer.modules.discordlink.rules.HushRetryRule;

import java.security.SecureRandom;
import java.util.List;

/**
 * Lilly's Discord and her Minecraft servers, joined: somebody who is not whitelisted gets a code on the kick
 * screen, types it into Discord's /link, and is whitelisted with their account linked. Discord roles give
 * LuckPerms groups. One bot for the whole network, run by whichever server holds the role.
 */
public final class DiscordLinkModule implements FlexModule {

    private static final ModuleInfo INFO = ModuleInfo.of("discordlink", "Discord Link", "0.14.0")
            .describedAs("Whitelisting and account links through Discord's /link; Discord roles give LuckPerms groups")
            .by("Raindancer118");

    /** How often each server asks whether it should run the bot. A third of the network lease. */
    private static final long TICK_SECONDS = 5;

    private DiscordBot bot;
    private HushFeed hushFeed;
    private HushHooks hushHooks;
    private HushGroupWatcher hushGroups;
    private HushServerListener hushServer;
    private RunMarker runMarker;

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
        Whitelist whitelist = new BukkitWhitelist(context.plugin(), context.plugin().getServer());
        // The network's list — Core 2.38.0; on an older Core vanilla's, as before.
        try {
            context.core().whitelist();
            whitelist = new CoreWhitelist(() -> context.core().whitelist(), whitelist);
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.38.0: /link whitelists on this server's own list.");
        }
        LinkService links = new LinkService(codes, store, roles, new LinkRule(), whitelist, System::currentTimeMillis);
        RoleSyncService sync = new RoleSyncService(() -> context.core().groups(), roles, store, new RoleSyncRule(), context.log());
        bot = new DiscordBot(context.plugin(), context.plugin().getServer(), context.core().messages(), context.log(),
                context.core().secrets(), context.core().networkRoles(), links, sync, roles, whitelist,
                () -> context.core().groups(), new InfoService(new CorePlayerFacts(context.core(), store)), settings.current());
        settings.onChange(codes::settings);
        settings.onChange(links::settings);
        settings.onChange(sync::settings);
        settings.onChange(bot::settings);

        var server = context.plugin().getServer();
        PlayerResolver resolver = new PlayerResolver(new PlayerResolver.Known() {
            @Override
            public java.util.Optional<PlayerResolver.Resolved> byName(String name) {
                for (var online : server.getOnlinePlayers()) {
                    if (online.getName().equalsIgnoreCase(name)) {
                        return java.util.Optional.of(new PlayerResolver.Resolved(online.getUniqueId(), online.getName()));
                    }
                }
                var cached = server.getOfflinePlayerIfCached(name);
                return cached == null || cached.getName() == null ? java.util.Optional.empty()
                        : java.util.Optional.of(new PlayerResolver.Resolved(cached.getUniqueId(), cached.getName()));
            }

            @Override
            public java.util.Optional<String> nameOf(java.util.UUID id) {
                return java.util.Optional.ofNullable(server.getOfflinePlayer(id).getName());
            }
        }, new MojangHttp(), typed -> {
            // Core 2.74.0 (also remembers the answer); an older Core: Known and Mojang above
            try {
                var lookup = context.core().profileLookup();
                return lookup.byNameOrId(typed).join().map(found -> new PlayerResolver.Resolved(found.id(),
                        found.name() != null ? found.name()
                                : lookup.nameOf(found.id()).orElse(found.id().toString().substring(0, 8))));
            } catch (LinkageError | RuntimeException unavailable) {
                return java.util.Optional.empty();
            }
        });
        LinkNotes notes = new LinkNotes();
        HushControl hush = startHush(context, settings::current, links, new InfoService(new CorePlayerFacts(context.core(), store)),
                resolver, notes);
        DiscordLinkServices services = new DiscordLinkServices(context.plugin(), server,
                context.core().messages(), settings::current, context.core().secrets(), codes, links, bot, hush, resolver, notes);
        context.listener(new JoinGateListener(services));
        DiscordLinkCommands.ready(services);
        // Screens for /screen — Core 2.10.0; on an older Core there are simply none.
        try {
            var screens = context.core().screens();
            if (screens != null) {
                new de.raindancer.modules.discordlink.service.LinkScreens(
                        player -> store.linkOf(player), store::count, System::currentTimeMillis)
                        .register(screens, context.plugin(), context.plugin().getServer());
                context.closeWith(() -> de.raindancer.modules.discordlink.service.LinkScreens.KINDS
                        .forEach(screens::unregister));
            }
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.10.0: no Discord screens until it is updated.");
        }

        var timer = Scheduling.asyncTimer(context.plugin(), 2, TICK_SECONDS, task -> bot.tick());
        if (timer != null) {
            context.closeWith(timer::cancel);
        }
        context.log().info("Discord link is up; the bot runs on whichever server holds '{}'.", DiscordBot.ROLE);
    }

    /** Hush's Minecraft feed: Core's moderation events and this server's start and stop, sent off-thread in batches. */
    private HushControl startHush(ModuleContext context, java.util.function.Supplier<DiscordLinkSettings> settings,
                                  LinkService links, InfoService info, PlayerResolver resolver, LinkNotes notes) {
        HushControl hush = new HushControl(context.core().secrets(), settings, () -> context.core().network().serverId(),
                System::currentTimeMillis);
        hushFeed = new HushFeed(new HttpHushTransport(), hush::config, System::currentTimeMillis,
                text -> context.log().warn("{}", text), text -> context.log().info("{}", text), new HushRetryRule(), 2_000);
        hush.feed(hushFeed);
        hushFeed.access(() -> context.plugin().getServer().hasWhitelist() ? "whitelist" : "open");
        startHushActions(context, links, info, resolver, notes);
        hushFeed.start();
        runMarker = new RunMarker(context.dataFolder().resolve("hush-running"));
        hushServer = new HushServerListener(hushFeed::offer, System::currentTimeMillis, runMarker.begin());
        context.listener(hushServer);
        if (context.plugin().getServer().getCurrentTick() > 0) {
            hushServer.started();
        }
        // Core 2.71.0; on an older Core there is no punishment, anti-cheat or report side, only start and stop.
        try {
            hushHooks = HushHooks.connect(context.core().moderationFeed(), hushFeed::offer, System::currentTimeMillis,
                    id -> {
                        String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
                        return name == null ? id.toString() : name;
                    });
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.71.0: the Hush feed carries only server start and stop.");
        }
        return hush;
    }

    /** Protocol v2: what Discord asks of this server, and the link and group changes Discord's roles follow. */
    private void startHushActions(ModuleContext context, LinkService links, InfoService info, PlayerResolver resolver,
                                  LinkNotes notes) {
        try {
            hushGroups = new HushGroupWatcher(links, () -> context.core().groups(), hushFeed::offer, System::currentTimeMillis);
            HushActions.Reviews decide;
            try {
                de.raindancer.core.moderation.review.StaffReviews staff = context.core().staffReviews();
                context.closeWith(HushHooks.reviews(staff, hushFeed::offerNow, System::currentTimeMillis));
                decide = (kind, id, approve, by, reason) -> staff.decide(kind, id, approve, by, reason).name()
                        .toLowerCase(java.util.Locale.ROOT);
            } catch (LinkageError olderCore) {
                context.log().info("RainsCore is older than 2.94.0: no requests for staff (map art) on Discord.");
                decide = (kind, id, approve, by, reason) -> "not_here";
            }
            HushActions actions = new HushActions(links, info, () -> context.core().groups(), new RoleSyncRule(),
                    hushFeed::offerNow, System::currentTimeMillis, work -> Scheduling.async(context.plugin(), work), hushGroups,
                    resolver, notes, decide);
            hushFeed.actions(actions::handle);
            links.onChange((link, linked) -> {
                long at = System.currentTimeMillis();
                String by = notes.take(link.player()).orElse(null);
                hushFeed.offerNow("link", () -> HushEvents.link(at, linked ? "linked" : "unlinked", link.player(), link.name(), link.discord(), by));
            });
            hushGroups.start();
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is too old for the Hush actions: no /link, /player or role sync through Hush.");
        }
    }

    @Override
    public void disable() {
        if (hushGroups != null) {
            hushGroups.close();
        }
        if (hushHooks != null) {
            hushHooks.close();
        }
        if (hushFeed != null) {
            if (hushServer != null && org.bukkit.Bukkit.isStopping()) {
                hushServer.stopping();
            }
            hushFeed.stop(3_000);
        }
        if (runMarker != null) {
            runMarker.clean();
        }
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
