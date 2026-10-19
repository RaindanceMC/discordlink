package de.raindancer.modules.discordlink.model;

import java.util.UUID;

/** A code waiting to be typed into Discord's {@code /link}. */
public record PendingCode(String code, UUID player, String name, Purpose purpose, long expiresAt) {

    public boolean expired(long now) {
        return now >= expiresAt;
    }
}
