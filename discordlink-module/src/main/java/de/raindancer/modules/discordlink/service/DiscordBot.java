package de.raindancer.modules.discordlink.service;

import de.raindancer.core.data.store.Secrets;
import de.raindancer.core.platform.log.LogChannel;
import de.raindancer.core.platform.network.NetworkRoles;
import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.discordlink.DiscordLinkSettings;
import de.raindancer.modules.discordlink.model.Link;
import de.raindancer.modules.discordlink.model.RoleConfig;
import de.raindancer.modules.discordlink.rules.RetryRule;
import de.raindancer.modules.discordlink.store.RoleConfigStore;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The Discord side: one bot for the whole network. Every server ticks {@link #tick}; whoever holds the network role
 * runs the bot, the others leave it be, and if that server stops another one takes over within a lease.
 *
 * <p>Interaction handlers defer at once and do their work on an async thread: links, codes and LuckPerms are all
 * database I/O, and a Discord thread that blocks stops answering everybody else.
 */
public final class DiscordBot implements IDiscordLinkService {

    public static final String ROLE = "discordlink-bot";
    public static final String TOKEN_SECRET = "discordlink-token";
    /** The applications website's LOOKUP_API_KEY, for /lookup. */
    public static final String LOOKUP_SECRET = "discordlink-lookup-key";
    static final String WHITELISTERS_MENU = "discordlink:whitelisters";
    static final String STATUS_REFRESH = "discordlink:status-refresh";
    /**
     * A server started with this set to "off" never runs the bot, whatever the database says. For copies of the
     * live servers (staging tests): their database copy holds the real token and settings, and a second bot on the
     * same token would answer every command twice.
     */
    static final String OFF_VARIABLE = "RAINS_DISCORDLINK_BOT";
    private static final boolean OFF_HERE = "off".equalsIgnoreCase(System.getenv(OFF_VARIABLE));

    /** Lilly pink. */
    static final int EMBED_COLOUR = 0xFF2E88;

    private final Plugin plugin;
    private final Server server;
    private final Messages messages;
    private final LogChannel log;
    private final Secrets secrets;
    private final Optional<NetworkRoles> leadership;
    private final LinkService links;
    private final RoleSyncService sync;
    private final RoleConfigStore roles;
    private final Whitelist whitelist;
    private final Supplier<PermissionGroups> groups;
    private final InfoService info;

    private final RetryRule retry = new RetryRule();
    private final de.raindancer.modules.discordlink.rules.LookupView lookupView = new de.raindancer.modules.discordlink.rules.LookupView();
    private final LookupClient lookups = new LookupClient();
    private final de.raindancer.modules.discordlink.rules.StatusView statusView = new de.raindancer.modules.discordlink.rules.StatusView();
    private final de.raindancer.modules.discordlink.rules.PlayerView playerView = new de.raindancer.modules.discordlink.rules.PlayerView();
    private volatile String lookupProblemLogged;

    private volatile DiscordLinkSettings settings;
    private volatile JDA jda;
    private volatile String problem = "not started yet";
    private volatile long nextSweep;
    /** Failed starts in a row, and when the next one may be tried: every try is a Discord login. */
    private int failures;
    private long nextAttempt;
    private String lastLogged;
    private int triedToken;

    public DiscordBot(Plugin plugin, Server server, Messages messages, LogChannel log, Secrets secrets,
                      Optional<NetworkRoles> leadership, LinkService links, RoleSyncService sync, RoleConfigStore roles,
                      Whitelist whitelist, Supplier<PermissionGroups> groups, InfoService info, DiscordLinkSettings settings) {
        this.plugin = plugin;
        this.server = server;
        this.messages = messages;
        this.log = log;
        this.secrets = secrets;
        this.leadership = leadership;
        this.links = links;
        this.sync = sync;
        this.roles = roles;
        this.whitelist = whitelist;
        this.groups = groups;
        this.info = info;
        this.settings = settings;
    }

    @Override
    public void settings(DiscordLinkSettings settings) {
        DiscordLinkSettings before = this.settings;
        this.settings = settings;
        if (!before.guildId().equals(settings.guildId()) || before.enabled() != settings.enabled()
                || !before.moreLinkGuilds().equals(settings.moreLinkGuilds())) {
            stop();   // the next tick starts it again against the new server, if it should run at all
            freshStart();
        } else if (!java.util.Objects.equals(before.lookupGuildId(), settings.lookupGuildId())) {
            JDA running = jda;
            if (running != null) {
                Guild old = before.hasLookupGuild() ? running.getGuildById(before.lookupGuildId().strip()) : null;
                if (old != null && settings.isLinkGuild(old.getId())) {
                    registerCommands(old);   // /lookup leaves, the link commands stay
                } else if (old != null) {
                    old.updateCommands().queue();   // /lookup leaves the server it was on
                }
                registerLookup(running);
            }
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------

    /** Every few seconds, on an async thread: run the bot if this server holds the role, stop it if not. */
    public synchronized void tick() {
        if (OFF_HERE) {
            problem = "switched off on this server (" + OFF_VARIABLE + "=off)";
            stop();
            return;
        }
        DiscordLinkSettings live = settings;
        Optional<String> token = secrets.text(TOKEN_SECRET).map(String::strip).filter(t -> !t.isEmpty());
        if (!live.enabled() || !live.hasGuild() || token.isEmpty()) {
            problem = !live.enabled() ? "switched off in /settings" : !live.hasGuild() ? "no Discord server id in /settings"
                    : "no bot token (console: /discordlink token <token>)";
            stop();
            return;
        }
        boolean mine = leadership.map(network -> network.claim(ROLE)).orElse(true);
        if (!mine) {
            problem = null;
            stop();
            return;
        }
        if (jda == null) {
            if (token.get().hashCode() != triedToken) {
                freshStart();   // a new token deserves a try at once
                triedToken = token.get().hashCode();
            }
            if (System.currentTimeMillis() < nextAttempt) {
                return;
            }
            start(token.get(), live.guildId().strip());
            if (jda == null) {
                failures++;
                nextAttempt = System.currentTimeMillis() + retry.delayMillisAfter(failures);
            }
        } else if (System.currentTimeMillis() >= nextSweep) {
            sweep();
        }
    }

    /** Whether a bot is answering somewhere in the network — the kick screen only offers a code then. */
    public boolean runningSomewhere() {
        if (jda != null) {
            return true;
        }
        return leadership.flatMap(network -> network.holder(ROLE)).isPresent()
                && secrets.text(TOKEN_SECRET).filter(t -> !t.isBlank()).isPresent();
    }

    public String status() {
        String holder = leadership.flatMap(network -> network.holder(ROLE)).orElse("nobody");
        if (jda != null) {
            return "running here (" + jda.getSelfUser().getName() + ")";
        }
        return problem != null ? problem + "; bot held by " + holder : "running on " + holder;
    }

    public synchronized void restart() {
        stop();
        freshStart();
        tick();
    }

    private void freshStart() {
        failures = 0;
        nextAttempt = 0;
        lastLogged = null;
    }

    /** Once per problem, not once per try: a failing bot would otherwise fill the console. */
    private void report(String what) {
        problem = what;
        if (!what.equals(lastLogged)) {
            lastLogged = what;
            log.warn("Discord link: {} (trying again later, waiting longer each time; /discordlink restart tries now).", what);
        }
    }

    private void start(String token, String guildId) {
        try {
            JDA started = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MEMBERS))
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .addEventListeners(new Events(guildId))
                    .build();
            started.awaitReady();
            Guild guild = started.getGuildById(guildId);
            if (guild == null) {
                report("the bot is not on Discord server " + guildId + " — invite it first");
                started.shutdown();
                return;
            }
            jda = started;
            problem = null;
            freshStart();
            registerCommands(guild);
            registerMoreLinkGuilds(started);
            registerLookup(started);
            nextSweep = System.currentTimeMillis() + 60_000L;
            log.info("Discord link bot is up on '{}'.", guild.getName());
        } catch (InvalidTokenException refused) {
            report("Discord refused the bot token");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failed) {
            String reason = String.valueOf(failed.getMessage());
            report(reason.contains("intent") ? "the Server Members Intent is off in the Discord developer portal"
                    : "the bot could not start: " + reason.lines().reduce((a, b) -> b).orElse(reason));
        }
    }

    public synchronized void stop() {
        JDA running = jda;
        jda = null;
        if (running != null) {
            running.shutdown();
            leadership.ifPresent(network -> network.release(ROLE));
            log.info("Discord link bot stopped on this server.");
        }
    }

    private static net.dv8tion.jda.api.interactions.commands.build.SlashCommandData lookupCommand() {
        return Commands.slash("lookup", "Look somebody up: Twitch, watch time, Discord, Minecraft, applications")
                .addOption(OptionType.STRING, "name", "A Twitch, Discord or Minecraft name (twitch:, discord:, mc: for one place)", true)
                .addOption(OptionType.BOOLEAN, "share", "Post it in the channel instead of only for you", false);
    }

    /** /lookup on the staff server only. Lilly's own server keeps its commands as they are. */
    private void registerLookup(JDA running) {
        DiscordLinkSettings now = settings;
        if (!now.hasLookupGuild() || now.isLinkGuild(now.lookupGuildId().strip())) {
            return;   // registerCommands put /lookup there along with the rest
        }
        Guild staff = running.getGuildById(now.lookupGuildId().strip());
        if (staff == null) {
            String what = "the bot is not on the /lookup server " + now.lookupGuildId().strip() + " — invite it there";
            if (!what.equals(lookupProblemLogged)) {
                lookupProblemLogged = what;
                log.warn("Discord link: {}.", what);
            }
            return;
        }
        lookupProblemLogged = null;
        staff.updateCommands().addCommands(lookupCommand())
                .queue(ok -> log.info("Discord link: /lookup is ready on '{}'.", staff.getName()),
                        failed -> log.warn("Could not register /lookup: {}", failed.getMessage()));
    }

    /** The other servers /link works on. One the bot is not on is said once and skipped; the rest still run. */
    private void registerMoreLinkGuilds(JDA running) {
        for (String id : settings.moreLinkGuilds()) {
            Guild guild = running.getGuildById(id);
            if (guild == null) {
                log.warn("Discord link: the bot is not on Discord server {} (more servers for /link) — invite it there.", id);
                continue;
            }
            registerCommands(guild);
            log.info("Discord link: /link is ready on '{}' too.", guild.getName());
        }
    }

    private void registerCommands(Guild guild) {
        var lookupHere = settings.hasLookupGuild() && settings.lookupGuildId().strip().equals(guild.getId());
        boolean main = guild.getId().equals(settings.guildId().strip());
        guild.updateCommands().addCommands(lookupHere ? List.of(lookupCommand()) : List.of()).addCommands(
                Commands.slash("link", "Link your Minecraft account (and get whitelisted)")
                        .addOption(OptionType.STRING, "code", "The code the server showed you", true),
                Commands.slash("unlink", "Unlink a Minecraft account from your Discord account")
                        .addOption(OptionType.STRING, "player", "Which one, if you linked several", false),
                Commands.slash("accounts", "Show the Minecraft accounts linked to your Discord account"),
                Commands.slash("status", "How Lilly's Minecraft network is doing: servers, players, performance"),
                Commands.slash("player", "A Minecraft player: online, playtime, balance, guild, job and more")
                        .addOption(OptionType.STRING, "name", "Their Minecraft name", true),
                Commands.slash("whitelist", "Whitelist a friend's Java account (special roles only)")
                        .addOption(OptionType.STRING, "player", "Their Minecraft Java username", true))
                .addCommands(main ? List.of(mcadminCommand()) : List.of())
                .queue(ok -> { }, failed -> log.warn("Could not register the Discord commands on '{}': {}",
                        guild.getName(), failed.getMessage()));
    }

    /** Lilly's server only: roles and channels it picks are hers. */
    private static net.dv8tion.jda.api.interactions.commands.build.SlashCommandData mcadminCommand() {
        return Commands.slash("mcadmin", "Minecraft link settings")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                        .addSubcommands(
                                new SubcommandData("whitelisters", "Pick the roles that may whitelist friends"),
                                new SubcommandData("map", "Give a LuckPerms group to everybody with a Discord role")
                                        .addOption(OptionType.ROLE, "role", "The Discord role", true)
                                        .addOption(OptionType.STRING, "group", "The LuckPerms group", true),
                                new SubcommandData("unmap", "Stop giving a group for a role")
                                        .addOption(OptionType.ROLE, "role", "The Discord role", true),
                                new SubcommandData("announce", "Where new whitelistings are announced")
                                        .addOption(OptionType.CHANNEL, "channel", "The channel (leave out: where /link was used)", false),
                                new SubcommandData("show", "Show what is set up"),
                                new SubcommandData("sync", "Check everybody's groups against their roles now"),
                                new SubcommandData("unlink", "Unlink a Minecraft account from anybody")
                                        .addOption(OptionType.STRING, "player", "The Minecraft username", true));
    }

    // ---- sweep ------------------------------------------------------------------------------

    /** Everybody's groups against their roles now; somebody no longer on the server has no roles. */
    public void sweep() {
        JDA running = jda;
        Guild guild = running == null ? null : running.getGuildById(settings.guildId().strip());
        nextSweep = System.currentTimeMillis() + settings.syncMinutes() * 60_000L;
        if (guild == null || roles.current().groups().isEmpty()) {
            return;
        }
        List<Long> ids = links.allDiscordIds().stream().map(Long::parseLong).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        guild.retrieveMembersByIds(ids).onSuccess(members -> Scheduling.async(plugin, () -> {
            Map<String, List<String>> byDiscord = new HashMap<>();
            members.forEach(member -> byDiscord.put(member.getId(), roleIds(member)));
            sync.syncAll(byDiscord);
        })).onError(failed -> log.warn("Discord link: could not read the members for the role sync: {}", failed.getMessage()));
    }

    // ---- Discord events ---------------------------------------------------------------------

    private final class Events extends ListenerAdapter {

        private final String guildId;

        Events(String guildId) {
            this.guildId = guildId;
        }

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            if (event.getName().equals("lookup")) {
                lookup(event);
                return;
            }
            boolean here = event.getGuild() != null && event.getMember() != null;
            boolean main = here && event.getGuild().getId().equals(guildId);
            if (!here || !settings.isLinkGuild(event.getGuild().getId()) || (!main && event.getName().equals("mcadmin"))) {
                event.reply(text("discordlink.discord.not-here")).setEphemeral(true).queue();
                return;
            }
            boolean everyone = event.getName().equals("status") || event.getName().equals("player");
            event.deferReply(!everyone).queue();
            Member used = event.getMember();
            String discord = used.getId();
            Scheduling.async(plugin, () -> {
                String answer;
                try {
                    // Roles and Manage Server count on Lilly's server only, wherever the command was used.
                    Member member = main ? used : homeMember(event.getJDA(), discord);
                    List<String> memberRoles = member == null ? List.of() : roleIds(member);
                    answer = switch (event.getName()) {
                        case "status" -> status(event);
                        case "player" -> player(event);
                        case "link" -> link(event, discord, memberRoles);
                        case "unlink" -> unlink(discord, option(event, "player"));
                        case "accounts" -> accounts(discord);
                        case "whitelist" -> whitelistFriend(event, member, memberRoles, option(event, "player"));
                        case "mcadmin" -> admin(event, member);
                        default -> text("discordlink.discord.not-here");
                    };
                } catch (RuntimeException failed) {
                    log.warn("Discord link: /{} failed: {}", event.getName(), failed.getMessage());
                    answer = text("discordlink.discord.failed");
                }
                if (answer != null) {
                    event.getHook().editOriginal(answer).queue();
                }
            });
        }

        /** /status's Refresh: anybody may press it, it only reads. */
        @Override
        public void onButtonInteraction(@NotNull net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
            if (!STATUS_REFRESH.equals(event.getComponentId())) {
                return;
            }
            event.deferEdit().queue();
            Scheduling.async(plugin, () -> {
                try {
                    event.getHook().editOriginalEmbeds(statusEmbed()).setComponents(statusButtons()).queue();
                } catch (RuntimeException failed) {
                    log.warn("Discord link: refreshing /status failed: {}", failed.getMessage());
                }
            });
        }

        @Override
        public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
            if (!WHITELISTERS_MENU.equals(event.getComponentId()) || event.getMember() == null
                    || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                return;
            }
            List<String> picked = event.getMentions().getRoles().stream().map(ISnowflake::getId).toList();
            event.deferEdit().queue();
            Scheduling.async(plugin, () -> {
                boolean saved = roles.save(roles.current().withWhitelisters(picked));
                event.getHook().editOriginal(saved
                        ? text("discordlink.discord.admin-whitelisters-saved", "roles", mentions(picked))
                        : text("discordlink.discord.save-failed")).setComponents().queue();
            });
        }

        @Override
        public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
            changed(event.getMember());
        }

        @Override
        public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
            changed(event.getMember());
        }

        @Override
        public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
            if (!event.getGuild().getId().equals(guildId)) {
                return;   // leaving the staff server or another /link server takes no groups away
            }
            String discord = event.getUser().getId();
            Scheduling.async(plugin, () -> sync.sync(discord, List.of()));
        }

        private void changed(Member member) {
            if (member.getGuild().getId().equals(guildId)) {
                String discord = member.getId();
                List<String> memberRoles = roleIds(member);
                Scheduling.async(plugin, () -> sync.sync(discord, memberRoles));
            }
        }
    }

    // ---- the commands, all on an async thread -----------------------------------------------

    private String link(SlashCommandInteractionEvent event, String discord, List<String> memberRoles) {
        LinkService.Result result = links.redeem(option(event, "code"), discord, memberRoles);
        String player = result.player() == null ? "?" : result.player();
        switch (result.outcome()) {
            case LINKED, LINKED_AND_WHITELISTED -> {
                sync.sync(discord, memberRoles);
                links.linksOf(discord).stream().filter(link -> link.name().equals(result.player())).findFirst()
                        .ifPresent(link -> tellInGame(link.player(), "discordlink.linked-in-game", "account", event.getUser().getName()));
                if (result.outcome() == LinkService.Outcome.LINKED_AND_WHITELISTED) {
                    announce(event, text("discordlink.discord.welcome", "user", event.getUser().getAsMention(), "player", player),
                            result.player());
                }
                return text(result.outcome() == LinkService.Outcome.LINKED_AND_WHITELISTED
                        ? "discordlink.discord.linked-whitelisted" : "discordlink.discord.linked", "player", player);
            }
            case ALREADY_YOURS -> {
                return text("discordlink.discord.already-yours", "player", player);
            }
            case SOMEONE_ELSES -> {
                return text("discordlink.discord.someone-elses", "player", player);
            }
            case ONE_IS_ENOUGH -> {
                return text("discordlink.discord.one-is-enough", "linked", names(result.yours()));
            }
            case TOO_MANY_TRIES -> {
                return text("discordlink.discord.too-many-tries");
            }
            case WRONG_CODE -> {
                return text("discordlink.discord.wrong-code");
            }
            default -> {
                return text("discordlink.discord.failed");
            }
        }
    }

    private String unlink(String discord, String which) {
        List<Link> theirs = links.linksOf(discord);
        if (theirs.isEmpty()) {
            return text("discordlink.discord.unlink-none");
        }
        Optional<Link> target = which == null || which.isBlank()
                ? (theirs.size() == 1 ? Optional.of(theirs.getFirst()) : Optional.empty())
                : theirs.stream().filter(link -> link.name().equalsIgnoreCase(which.strip())).findFirst();
        if (target.isEmpty()) {
            return text("discordlink.discord.unlink-which", "linked", names(theirs));
        }
        sync.clear(target.get());   // the groups came with the link, so they go with it
        links.unlink(target.get().player());
        return text("discordlink.discord.unlink-done", "player", target.get().name());
    }

    private String accounts(String discord) {
        List<Link> theirs = links.linksOf(discord);
        return theirs.isEmpty() ? text("discordlink.discord.accounts-none")
                : text("discordlink.discord.accounts", "linked", names(theirs));
    }

    private String whitelistFriend(SlashCommandInteractionEvent event, Member member, List<String> memberRoles, String name) {
        if (member == null
                || (!roles.current().mayWhitelistOthers(memberRoles) && !member.hasPermission(Permission.MANAGE_SERVER))) {
            return text("discordlink.discord.whitelist-not-allowed");
        }
        String wanted = name == null ? "" : name.strip();
        if (wanted.startsWith(".") || !wanted.matches("[A-Za-z0-9_]{3,16}")) {
            return text("discordlink.discord.whitelist-java-only");
        }
        var profile = server.createProfile(wanted);
        if (!profile.complete(false) || profile.getId() == null) {   // asks Mojang; we are on an async thread
            return text("discordlink.discord.whitelist-unknown", "player", wanted);
        }
        UUID id = profile.getId();
        String exact = profile.getName() == null ? wanted : profile.getName();
        if (whitelist.isWhitelisted(id)) {
            return text("discordlink.discord.whitelist-already", "player", exact);
        }
        whitelist.add(id, exact);
        log.info("Discord link: {} whitelisted {} from Discord.", member.getUser().getName(), exact);
        announceIn(event.getGuild(), event.getGuild().getId().equals(settings.guildId().strip()) ? null
                : event.getChannel().getId(), text("discordlink.discord.friend-welcome", "user", member.getAsMention(), "player", exact),
                "https://mc-heads.net/avatar/" + id + "/64");
        return text("discordlink.discord.whitelist-done", "player", exact);
    }

    private String admin(SlashCommandInteractionEvent event, Member member) {
        if (!member.hasPermission(Permission.MANAGE_SERVER)) {
            return text("discordlink.discord.admin-not-allowed");
        }
        RoleConfig config = roles.current();
        switch (String.valueOf(event.getSubcommandName())) {
            case "whitelisters" -> {
                EntitySelectMenu menu = EntitySelectMenu.create(WHITELISTERS_MENU, EntitySelectMenu.SelectTarget.ROLE)
                        .setRequiredRange(0, 25)
                        .setPlaceholder("Roles that may whitelist friends")
                        .setDefaultValues(config.whitelisters().stream().map(EntitySelectMenu.DefaultValue::role).toList())
                        .build();
                event.getHook().editOriginal(text("discordlink.discord.admin-whitelisters-pick"))
                        .setComponents(ActionRow.of(menu)).queue();
                return null;
            }
            case "map" -> {
                Role role = event.getOption("role").getAsRole();
                String group = option(event, "group").strip().toLowerCase(java.util.Locale.ROOT);
                PermissionGroups permissions = groups.get();
                if (permissions == PermissionGroups.NONE) {
                    return text("discordlink.discord.admin-no-luckperms");
                }
                if (!permissions.exists(group)) {
                    return text("discordlink.discord.admin-map-unknown-group", "group", group);
                }
                if (!roles.save(config.mapping(role.getId(), group))) {
                    return text("discordlink.discord.save-failed");
                }
                sweepSoon();
                return text("discordlink.discord.admin-mapped", "role", role.getAsMention(), "group", group);
            }
            case "unmap" -> {
                Role role = event.getOption("role").getAsRole();
                if (!roles.save(config.mapping(role.getId(), null))) {
                    return text("discordlink.discord.save-failed");
                }
                return text("discordlink.discord.admin-unmapped", "role", role.getAsMention());
            }
            case "announce" -> {
                var channel = event.getOption("channel");
                String id = channel == null ? null : channel.getAsChannel().getId();
                if (!roles.save(config.announcingIn(id))) {
                    return text("discordlink.discord.save-failed");
                }
                return id == null ? text("discordlink.discord.admin-announce-here")
                        : text("discordlink.discord.admin-announce-set", "channel", "<#" + id + ">");
            }
            case "show" -> {
                String mapping = config.groups().isEmpty() ? "—" : config.groups().entrySet().stream()
                        .map(entry -> "<@&" + entry.getKey() + "> → " + entry.getValue())
                        .collect(Collectors.joining("\n"));
                return text("discordlink.discord.admin-show", "whitelisters",
                        config.whitelisters().isEmpty() ? "—" : mentions(config.whitelisters()),
                        "mapping", mapping, "links", String.valueOf(links.allDiscordIds().size()),
                        "announce", config.announceChannel() == null ? "where /link is used" : "<#" + config.announceChannel() + ">");
            }
            case "sync" -> {
                sweepSoon();
                return text("discordlink.discord.admin-sync-started");
            }
            case "unlink" -> {
                String name = option(event, "player").strip();
                Optional<Link> found = links.byName(name);
                if (found.isEmpty()) {
                    return text("discordlink.discord.unlink-none");
                }
                sync.clear(found.get());
                links.unlink(found.get().player());
                return text("discordlink.discord.unlink-done", "player", found.get().name());
            }
            default -> {
                return text("discordlink.discord.not-here");
            }
        }
    }

    // ---- /lookup (staff server only) --------------------------------------------------------

    private void lookup(SlashCommandInteractionEvent event) {
        DiscordLinkSettings now = settings;
        if (event.getGuild() == null || !now.hasLookupGuild() || !event.getGuild().getId().equals(now.lookupGuildId().strip())) {
            event.reply(text("discordlink.discord.lookup-not-here")).setEphemeral(true).queue();
            return;
        }
        var shareOption = event.getOption("share");
        boolean share = shareOption != null && shareOption.getAsBoolean();
        String name = option(event, "name");
        event.deferReply(!share).queue();
        Scheduling.async(plugin, () -> {
            try {
                Optional<String> key = secrets.text(LOOKUP_SECRET).map(String::strip).filter(k -> !k.isEmpty());
                var report = key.flatMap(k -> lookups.fetch(now.lookupUrl(), k, name));
                String mcName = name.toLowerCase(java.util.Locale.ROOT).startsWith("mc:") ? name.substring(3).strip()
                        : name.contains(":") ? null : name.strip();
                var card = mcName == null ? Optional.<de.raindancer.modules.discordlink.model.PlayerCard>empty() : info.card(mcName);
                var linkedTo = card.flatMap(c -> links.byName(c.name())).map(de.raindancer.modules.discordlink.model.Link::discord);
                if (report.isEmpty() && card.isEmpty()) {
                    event.getHook().editOriginal(key.isEmpty() ? text("discordlink.discord.lookup-no-key")
                            : text("discordlink.discord.lookup-nothing", "name", name)).queue();
                    return;
                }
                event.getHook().editOriginalEmbeds(lookupView.embed(report.orElse(null), card, linkedTo))
                        .setAllowedMentions(List.of()).queue();
            } catch (RuntimeException failed) {
                log.warn("Discord link: /lookup failed: {}", failed.getMessage());
                event.getHook().editOriginal(text("discordlink.discord.failed")).queue();
            }
        });
    }

    // ---- public answers ---------------------------------------------------------------------

    private String status(SlashCommandInteractionEvent event) {
        event.getHook().editOriginalEmbeds(statusEmbed()).setComponents(statusButtons()).queue();
        return null;
    }

    /** The network status board, stamped with when — the Refresh button below builds it again. */
    private net.dv8tion.jda.api.entities.MessageEmbed statusEmbed() {
        return statusView.embed(info.status(), System.currentTimeMillis());
    }

    private static ActionRow statusButtons() {
        return ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary(STATUS_REFRESH, "🔄 Refresh"));
    }

    private String player(SlashCommandInteractionEvent event) {
        var card = info.card(option(event, "name"));
        if (card.isEmpty()) {
            return text("discordlink.discord.player-unknown", "player", String.valueOf(option(event, "name")));
        }
        event.getHook().editOriginalEmbeds(playerView.embed(card.get())).queue();
        return null;
    }

    /** A new whitelisting, said where everybody sees it: the configured channel, else where /link was used. */
    private void announce(SlashCommandInteractionEvent event, String line, String playerName) {
        String head = links.byName(playerName).map(link -> "https://mc-heads.net/avatar/" + link.player() + "/64").orElse(null);
        announceIn(event.getGuild(), event.getChannel().getId(), line, head);
    }

    private void announceIn(Guild guild, String fallbackChannel, String line, String head) {
        if (guild == null) {
            return;
        }
        String id = roles.current().announceChannel() != null ? roles.current().announceChannel() : fallbackChannel;
        // The picked channel is on Lilly's server even when /link was used on another one.
        var channel = id == null ? null
                : guild.getJDA().getChannelById(net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, id);
        if (channel == null || !channel.canTalk()) {
            log.warn("Discord link: can't post the welcome in channel {} (gone, or the bot may not write there).", id);
            return;
        }
        EmbedBuilder embed = new EmbedBuilder().setColor(EMBED_COLOUR).setDescription(line);
        if (head != null) {
            embed.setThumbnail(head);
        }
        // The mention reads as a name; it doesn't ping anybody.
        channel.sendMessageEmbeds(embed.build()).setAllowedMentions(List.of()).queue(ok -> { },
                failed -> log.warn("Discord link: the welcome could not be posted: {}", failed.getMessage()));
    }

    private void sweepSoon() {
        nextSweep = 0;
    }

    // ---- helpers ----------------------------------------------------------------------------

    private void tellInGame(UUID player, String key, Object... values) {
        Player online = server.getPlayer(player);
        if (online != null) {
            Scheduling.entity(plugin, online, () -> messages.send(online, key, values));
        }
    }

    /** Somebody as a member of Lilly's server, or null if they aren't on it. Blocks: async threads only. */
    private Member homeMember(JDA running, String discord) {
        Guild home = running.getGuildById(settings.guildId().strip());
        if (home == null) {
            return null;
        }
        try {
            return home.retrieveMemberById(discord).complete();
        } catch (net.dv8tion.jda.api.exceptions.ErrorResponseException failed) {
            // Only "not a member" means no roles; anything else must not read as that, or the sync takes groups away.
            if (failed.getErrorResponse() == net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MEMBER) {
                return null;
            }
            throw failed;
        }
    }

    private static List<String> roleIds(Member member) {
        return member.getRoles().stream().map(ISnowflake::getId).toList();
    }

    private static String option(SlashCommandInteractionEvent event, String name) {
        var option = event.getOption(name);
        return option == null ? null : option.getAsString();
    }

    private static String names(Collection<Link> links) {
        return links.stream().map(Link::name).collect(Collectors.joining(", "));
    }

    private static String mentions(Collection<String> roleIds) {
        return roleIds.stream().map(id -> "<@&" + id + ">").collect(Collectors.joining(" "));
    }

    private String text(String key, Object... values) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key, values));
    }
}
