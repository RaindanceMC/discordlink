package de.raindancer.modules.discordlink.hush;

import net.dv8tion.jda.api.utils.data.DataObject;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Tells Hush the server is up — once Paper says it has finished loading — and, first, that the run before it crashed. */
public final class HushServerListener implements Listener {

    private final BiConsumer<String, Supplier<DataObject>> sink;
    private final LongSupplier clock;
    private final boolean crashed;
    private boolean told;

    public HushServerListener(BiConsumer<String, Supplier<DataObject>> sink, LongSupplier clock, boolean crashed) {
        this.sink = sink;
        this.clock = clock;
        this.crashed = crashed;
    }

    @EventHandler
    public void onLoad(ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
            started();
        }
    }

    /** Once: the load event, or the module's own enable when the server has long been up. */
    public synchronized void started() {
        if (told) {
            return;
        }
        told = true;
        long at = clock.getAsLong();
        if (crashed) {
            sink.accept("server", () -> HushEvents.server(at, "crash", "the last run did not shut down cleanly"));
        }
        sink.accept("server", () -> HushEvents.server(at, "start", null));
    }

    public void stopping() {
        long at = clock.getAsLong();
        sink.accept("server", () -> HushEvents.server(at, "stop", null));
    }
}
