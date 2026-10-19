package de.raindancer.modules.discordlink.model;

import java.util.List;

/** /status: every server heard of lately, up or down, how each runs, and the visible players. */
public record NetworkStatus(int online, List<Line> servers) {

    /** {@code downSince} 0 while up; {@code tps}/{@code mspt} NaN when the server doesn't say. */
    public record Line(String name, boolean online, int players, double tps, double mspt, long startedAt, long downSince) {

        public Line(String name, int players) {
            this(name, true, players, Double.NaN, Double.NaN, 0, 0);
        }
    }
}
