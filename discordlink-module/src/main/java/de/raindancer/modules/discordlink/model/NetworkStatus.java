package de.raindancer.modules.discordlink.model;

import java.util.List;

/** Who is on, for /status: the servers that answer the network's heartbeat, and the visible players on each. */
public record NetworkStatus(int online, List<Line> servers) {

    public record Line(String name, int players) {
    }
}
