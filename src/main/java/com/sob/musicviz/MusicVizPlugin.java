package com.sob.musicviz;

import com.google.inject.Provides;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.TileObject;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
    name = "Music Visualizer",
    description = "Flashes nearby objects on the notes of the current music track.",
    tags = {"music", "visualizer", "overlay", "midi"}
)
public class MusicVizPlugin extends Plugin
{
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private MusicVizConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private MusicVizOverlay overlay;
    @Inject private MidiScheduler scheduler;
    @Inject private ObjectScanner scanner;
    @Inject private FlashStore flashes;

    private ScheduledExecutorService pollExec;
    private ScheduledFuture<?> pollTask;
    private volatile Object lastRequest;
    private final Random rr = new Random();
    private int rrCursor = 0;

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);
        scheduler.start(this::onNote);
        startPolling();
    }

    @Override
    protected void shutDown()
    {
        stopPolling();
        scheduler.stop();
        overlayManager.remove(overlay);
        flashes.clear();
        lastRequest = null;
    }

    @Provides
    MusicVizConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(MusicVizConfig.class);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged ev)
    {
        // Currently no-op — scheduler holds its own clock. We may want to
        // clear flashes on logout so they don't linger on the login screen.
    }

    @Subscribe
    public void onGameTick(GameTick ev)
    {
        scanner.refresh(config.radius());
    }

    private void startPolling()
    {
        pollExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "musicviz-trackpoll");
            t.setDaemon(true);
            return t;
        });
        pollTask = pollExec.scheduleAtFixedRate(
            () -> clientThread.invoke(this::pollActiveTrack),
            500, config.pollIntervalMs(), TimeUnit.MILLISECONDS);
    }

    private void stopPolling()
    {
        if (pollTask != null) { pollTask.cancel(false); pollTask = null; }
        if (pollExec != null) { pollExec.shutdownNow(); pollExec = null; }
    }

    /**
     * Runs on client thread. Detects active-track change by reference
     * identity of the first MidiRequest. On change, kicks off off-thread
     * load + parse so we don't stall the client.
     */
    private void pollActiveTrack()
    {
        try
        {
            List<?> active = invokeListAccessor();
            if (active == null || active.isEmpty())
            {
                if (lastRequest != null)
                {
                    lastRequest = null;
                    scheduler.loadTrack(java.util.Collections.emptyList(), System.nanoTime());
                }
                return;
            }
            Object first = active.get(0);
            if (first == lastRequest) return;
            lastRequest = first;
            long startNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(config.syncOffsetMs());
            loadTrackAsync(first, startNanos);
        }
        catch (Throwable t)
        {
            log.debug("pollActiveTrack failed", t);
        }
    }

    /**
     * `Client.getActiveMidiRequests()` is the documented method but its
     * presence/name has shifted across RuneLite versions. Look it up
     * reflectively so we degrade gracefully on older/newer clients.
     */
    private List<?> invokeListAccessor()
    {
        try
        {
            java.lang.reflect.Method m = client.getClass().getMethod("getActiveMidiRequests");
            Object res = m.invoke(client);
            if (res instanceof List) return (List<?>) res;
        }
        catch (NoSuchMethodException e)
        {
            log.debug("Client.getActiveMidiRequests not present on this RuneLite version");
        }
        catch (Exception e)
        {
            log.debug("getActiveMidiRequests threw", e);
        }
        return null;
    }

    private void loadTrackAsync(Object request, long startNanos)
    {
        if (pollExec == null) return;
        pollExec.submit(() -> {
            try
            {
                byte[] bytes = MidiTrackResolver.extractMidiBytes(request);
                if (bytes == null)
                {
                    if (config.logCacheMisses())
                    {
                        log.info("No MIDI bytes accessible on {} — visualizer idle for this track", request.getClass().getName());
                    }
                    scheduler.loadTrack(java.util.Collections.emptyList(), startNanos);
                    return;
                }
                List<NoteEvent> events = MidiTrackResolver.flatten(bytes);
                scheduler.loadTrack(events, startNanos);
                flashes.clear();
            }
            catch (Throwable t)
            {
                log.warn("Failed to load MIDI for {}", request, t);
            }
        });
    }

    private void onNote(NoteEvent ev)
    {
        if (ev.channel != config.melodyChannel()) return;
        List<TileObject> targets = scanner.get();
        if (targets.isEmpty()) return;

        int idx;
        if (config.selectionMode() == MusicVizConfig.SelectionMode.HASH_BY_NOTE)
        {
            idx = Math.floorMod(ev.note, targets.size());
        }
        else
        {
            idx = Math.floorMod(rrCursor++, targets.size());
        }
        TileObject target = targets.get(idx);
        flashes.add(new FlashState(target, System.currentTimeMillis(), NoteColor.forNote(ev.note)));
    }
}
