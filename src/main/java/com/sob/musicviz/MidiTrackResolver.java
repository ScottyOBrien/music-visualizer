package com.sob.musicviz;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.sob.musicviz.vendored.SmfParser;
import com.sob.musicviz.vendored.TrackDefinition;
import com.sob.musicviz.vendored.TrackLoader;

@Slf4j
final class MidiTrackResolver
{
    private MidiTrackResolver() {}

    static List<NoteEvent> flatten(byte[] cacheBytes) throws Exception
    {
        TrackDefinition def = new TrackLoader().load(cacheBytes);
        if (def == null || def.midi == null || def.midi.length == 0)
        {
            return Collections.emptyList();
        }

        SmfParser.Parsed parsed = SmfParser.parse(def.midi);
        if (parsed.divisionPpq <= 0) return Collections.emptyList();

        long[] tempoMap = buildTempoMap(parsed.tempoChanges);

        List<NoteEvent> out = new ArrayList<>(parsed.noteOns.size());
        for (SmfParser.NoteOn n : parsed.noteOns)
        {
            long ms = tickToMs(n.tick, tempoMap, parsed.divisionPpq);
            out.add(new NoteEvent(ms, n.channel, n.note, n.velocity));
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Flatten the SMF tempo events (microseconds per quarter note at a tick)
     * into a sparse [tick, usPerQuarter, ...] table sorted by tick. Tick 0
     * defaults to 120 BPM (500000 us/q) if no earlier tempo is specified.
     */
    private static long[] buildTempoMap(List<SmfParser.TempoChange> changes)
    {
        List<long[]> rows = new ArrayList<>(changes.size() + 1);
        rows.add(new long[]{0L, 500_000L});
        for (SmfParser.TempoChange tc : changes)
        {
            rows.add(new long[]{tc.tick, tc.microsPerQuarter});
        }
        rows.sort((a, b) -> Long.compare(a[0], b[0]));
        long[] flat = new long[rows.size() * 2];
        for (int i = 0; i < rows.size(); i++)
        {
            flat[i * 2] = rows.get(i)[0];
            flat[i * 2 + 1] = rows.get(i)[1];
        }
        return flat;
    }

    private static long tickToMs(long targetTick, long[] tempoMap, int ppq)
    {
        long ms = 0;
        long prevTick = 0;
        long prevUsPerQ = 500_000L;
        for (int i = 0; i < tempoMap.length; i += 2)
        {
            long tick = tempoMap[i];
            long usPerQ = tempoMap[i + 1];
            if (tick >= targetTick)
            {
                ms += ((targetTick - prevTick) * prevUsPerQ) / (ppq * 1000L);
                return ms;
            }
            ms += ((tick - prevTick) * prevUsPerQ) / (ppq * 1000L);
            prevTick = tick;
            prevUsPerQ = usPerQ;
        }
        ms += ((targetTick - prevTick) * prevUsPerQ) / (ppq * 1000L);
        return ms;
    }
}
