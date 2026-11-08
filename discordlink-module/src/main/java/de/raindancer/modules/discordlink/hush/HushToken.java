package de.raindancer.modules.discordlink.hush;

/** The feed's bearer token. Prints as nothing, so no log line, status or exception message can leak it. */
public record HushToken(String value) {

    public HushToken {
        value = value == null ? "" : value.strip();
    }

    public boolean isBlank() {
        return value.isEmpty();
    }

    @Override
    public String toString() {
        return "HushToken[***]";
    }
}
