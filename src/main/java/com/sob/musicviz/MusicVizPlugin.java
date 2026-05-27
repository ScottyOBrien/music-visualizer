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
import net.runelite.api.GameObject;
import net.runelite.api.MidiRequest;
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
    private volatile int lastArchiveId = Integer.MIN_VALUE;
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
        lastArchiveId = Integer.MIN_VALUE;
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
                if (lastArchiveId != Integer.MIN_VALUE)
                {
                    lastArchiveId = Integer.MIN_VALUE;
                    scheduler.loadTrack(java.util.Collections.emptyList(), System.nanoTime());
                }
                return;
            }
            Object first = active.get(0);
            if (!(first instanceof MidiRequest)) return;
            MidiRequest req = (MidiRequest) first;
            int archiveId = req.getArchiveId();
            boolean jingle = req.isJingle();
            if (archiveId == lastArchiveId) return;
            lastArchiveId = archiveId;
            long startNanos = System.nanoTime() - TimeUnit.MILLISECONDS.toNanos(config.syncOffsetMs());
            loadTrackAsync(archiveId, jingle, startNanos);
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

    private void loadTrackAsync(int archiveId, boolean jingle, long startNanos)
    {
        if (pollExec == null) return;
        pollExec.submit(() -> {
            try
            {
                byte[] bytes = MidiCacheLoader.load(archiveId, jingle);
                if (bytes == null)
                {
                    if (config.logCacheMisses())
                    {
                        log.info("musicviz: no MIDI bytes for archive {} — idle", archiveId);
                    }
                    scheduler.loadTrack(java.util.Collections.emptyList(), startNanos);
                    return;
                }
                log.info("musicviz: archive {} -> {} bytes; head={}", archiveId, bytes.length, hexHead(bytes, 32));
                int mthd = findMThd(bytes);
                log.info("musicviz: MThd offset in archive {}: {}", archiveId, mthd);
                List<NoteEvent> events = MidiTrackResolver.flatten(bytes);
                log.info("musicviz: parsed {} note events from archive {}", events.size(), archiveId);
                scheduler.loadTrack(events, startNanos);
                flashes.clear();
            }
            catch (Throwable t)
            {
                log.warn("musicviz: failed to load MIDI for archive {}", archiveId, t);
            }
        });
    }

    private static String hexHead(byte[] b, int n)
    {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(n, b.length);
        for (int i = 0; i < limit; i++)
        {
            sb.append(String.format("%02x", b[i] & 0xFF));
            if (i < limit - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static int findMThd(byte[] b)
    {
        for (int i = 0; i + 4 <= b.length; i++)
        {
            if (b[i] == 'M' && b[i+1] == 'T' && b[i+2] == 'h' && b[i+3] == 'd') return i;
        }
        return -1;
    }

    private void onNote(NoteEvent ev)
    {
        int wantedChannel = config.melodyChannel();
        if (wantedChannel >= 0 && ev.channel != wantedChannel) return;
        List<GameObject> targets = scanner.get();
        if (targets.isEmpty()) return;

        int idx;
        switch (config.selectionMode())
        {
            case HASH_BY_NOTE:
                idx = (int) Math.floorMod((long) ev.note * 2654435761L, targets.size());
                break;
            case ROUND_ROBIN:
                idx = Math.floorMod(rrCursor++, targets.size());
                break;
            case RANDOM:
            default:
                idx = rr.nextInt(targets.size());
                break;
        }
        GameObject target = targets.get(idx);
        flashes.add(new FlashState(target, System.currentTimeMillis(), NoteColor.forNote(ev.note)));
    }
}
