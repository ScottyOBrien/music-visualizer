package com.sob.musicviz;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
class MidiScheduler
{
    private ScheduledExecutorService exec;
    private ScheduledFuture<?> task;

    private volatile List<NoteEvent> events = Collections.emptyList();
    private volatile long trackStartNanos = 0L;
    private volatile int cursor = 0;
    private volatile Consumer<NoteEvent> onNote = e -> {};

    @Inject
    MidiScheduler() {}

    synchronized void start(Consumer<NoteEvent> sink)
    {
        if (exec != null) return;
        this.onNote = sink;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "musicviz-scheduler");
            t.setDaemon(true);
            return t;
        });
        task = exec.scheduleAtFixedRate(this::tick, 0, 5, TimeUnit.MILLISECONDS);
    }

    synchronized void stop()
    {
        if (task != null) { task.cancel(false); task = null; }
        if (exec != null) { exec.shutdownNow(); exec = null; }
        events = Collections.emptyList();
        cursor = 0;
    }

    synchronized void loadTrack(List<NoteEvent> noteEvents, long startNanos)
    {
        this.events = noteEvents;
        this.trackStartNanos = startNanos;
        this.cursor = 0;
        log.debug("loaded track: {} events", noteEvents.size());
    }

    private void tick()
    {
        try
        {
            List<NoteEvent> evs = events;
            if (evs.isEmpty()) return;

            long elapsedMs = (System.nanoTime() - trackStartNanos) / 1_000_000L;
            int i = cursor;
            while (i < evs.size() && evs.get(i).timestampMs <= elapsedMs)
            {
                try { onNote.accept(evs.get(i)); }
                catch (Throwable t) { log.warn("onNote sink threw", t); }
                i++;
            }
            cursor = i;
        }
        catch (Throwable t)
        {
            log.warn("scheduler tick threw", t);
        }
    }
}
