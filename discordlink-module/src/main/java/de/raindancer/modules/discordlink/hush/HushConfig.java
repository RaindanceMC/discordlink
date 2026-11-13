package de.raindancer.modules.discordlink.hush;

/** What the feed needs to send, read fresh before each send so a settings change or a new token applies at once. */
public record HushConfig(String url, String serverName, HushToken token,
                         boolean anticheat, boolean punishments, boolean reports, boolean server,
                         boolean adminMode, boolean actions, int pollSeconds, boolean reviews) {

    public static final int POLL_DEFAULT = 3;

    /** The feed of protocol v1: no admin mode, no reverse channel. */
    public HushConfig(String url, String serverName, HushToken token,
                      boolean anticheat, boolean punishments, boolean reports, boolean server) {
        this(url, serverName, token, anticheat, punishments, reports, server, true, false, POLL_DEFAULT, true);
    }

    /** Before staff reviews (0.14.0): they are on. */
    public HushConfig(String url, String serverName, HushToken token, boolean anticheat, boolean punishments,
                      boolean reports, boolean server, boolean adminMode, boolean actions, int pollSeconds) {
        this(url, serverName, token, anticheat, punishments, reports, server, adminMode, actions, pollSeconds, true);
    }

    public static final int SERVER_NAME_MAX = 32;

    public HushConfig {
        url = url == null ? "" : url.strip();
        String name = serverName == null ? "" : serverName.strip();
        pollSeconds = Math.max(1, Math.min(60, pollSeconds <= 0 ? POLL_DEFAULT : pollSeconds));
        serverName = name.length() > SERVER_NAME_MAX ? name.substring(0, SERVER_NAME_MAX) : name;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /** Hush can hand this server a code to redeem: a token is set and the reverse channel is on. */
    public boolean actionsReady() {
        return hasToken() && actions;
    }

    public boolean wants(String type) {
        return switch (type) {
            case "anticheat" -> anticheat;
            case "punishment" -> punishments;
            case "report" -> reports;
            case "server" -> server;
            case "admin" -> adminMode;
            // a request staff decide with buttons in Discord: without the reverse channel nothing could come back
            case "review" -> reviews && actions;
            // answers to what Hush asked for, and the link and group changes that keep Discord's roles right
            case "result", "link", "links", "groups" -> actions;
            default -> false;
        };
    }
}
