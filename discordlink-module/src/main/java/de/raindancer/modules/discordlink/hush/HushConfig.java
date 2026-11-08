package de.raindancer.modules.discordlink.hush;

/** What the feed needs to send, read fresh before each send so a settings change or a new token applies at once. */
public record HushConfig(String url, String serverName, HushToken token,
                         boolean anticheat, boolean punishments, boolean reports, boolean server) {

    public static final int SERVER_NAME_MAX = 32;

    public HushConfig {
        url = url == null ? "" : url.strip();
        String name = serverName == null ? "" : serverName.strip();
        serverName = name.length() > SERVER_NAME_MAX ? name.substring(0, SERVER_NAME_MAX) : name;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public boolean wants(String type) {
        return switch (type) {
            case "anticheat" -> anticheat;
            case "punishment" -> punishments;
            case "report" -> reports;
            case "server" -> server;
            default -> false;
        };
    }
}
