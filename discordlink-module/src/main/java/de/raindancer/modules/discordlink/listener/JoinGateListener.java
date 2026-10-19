package de.raindancer.modules.discordlink.listener;

import de.raindancer.modules.discordlink.DiscordLinkServices;
import de.raindancer.modules.discordlink.model.Purpose;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Somebody who is not whitelisted is turned away with a code instead of the plain vanilla line: typed into
 * Discord's /link, it whitelists them and links the account in one go. This runs before vanilla's own whitelist
 * check and on an async thread, which is where the code's database write may happen.
 *
 * <p>Only while a bot answers somewhere in the network: a code nobody can redeem is worse than the plain message.
 */
public final class JoinGateListener implements IDiscordLinkListener {

    private final DiscordLinkServices services;

    public JoinGateListener(DiscordLinkServices services) {
        this.services = services;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED
                || !services.settings().get().joinCodes()
                || !services.server().hasWhitelist()) {
            return;
        }
        UUID id = event.getUniqueId();
        OfflinePlayer player = services.server().getOfflinePlayer(id);
        if (player.isWhitelisted() || player.isOp() || !services.bot().runningSomewhere()) {
            return;
        }
        Optional<String> code = services.codes().issue(id, event.getName(), Purpose.JOIN);
        if (code.isEmpty()) {
            return;   // the database is down: vanilla's message is still true
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                services.messages().get("discordlink.join.kick", "code", code.get(),
                        "minutes", String.valueOf(services.codes().lifetimeMinutes())));
    }

    @Override
    public void forget(UUID player) {
        // Nothing is remembered per player here; the codes live in the database.
    }
}
