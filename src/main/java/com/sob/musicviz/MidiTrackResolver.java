package com.sob.musicviz;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import lombok.extern.slf4j.Slf4j;
import com.sob.musicviz.vendored.TrackDefinition;
import com.sob.musicviz.vendored.TrackLoader;

@Slf4j
final class MidiTrackResolver
{
    private MidiTrackResolver() {}

    static List<NoteEvent> flatten(byte[] cacheBytes) throws Exception
    {
        TrackDefinition def = new TrackLoader().load(cacheBytes);
        byte[] smf = def.midi;
        Sequence seq = javax.sound.midi.MidiSystem.getSequence(new ByteArrayInputStream(smf));
        int resolution = seq.getResolution();
        if (resolution <= 0) return Collections.emptyList();

        long[] tempoMap = buildTempoMap(seq);

        List<NoteEvent> out = new ArrayList<>();
        for (Track track : seq.getTracks())
        {
            for (int i = 0; i < track.size(); i++)
            {
                MidiEvent ev = track.get(i);
                MidiMessage msg = ev.getMessage();
                if (!(msg instanceof ShortMessage)) continue;
                ShortMessage sm = (ShortMessage) msg;
                if (sm.getCommand() != ShortMessage.NOTE_ON) continue;
                int vel = sm.getData2();
                if (vel == 0) continue;

                long ms = tickToMs(ev.getTick(), tempoMap, resolution);
                out.add(new NoteEvent(ms, sm.getChannel(), sm.getData1(), vel));
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Honor MIDI tempo meta-events (0x51). Returns a flat [tick, usPerQuarter, ...]
     * array sorted by tick, with an initial 120-BPM entry at tick 0.
     */
    private static long[] buildTempoMap(Sequence seq)
    {
        List<long[]> changes = new ArrayList<>();
        changes.add(new long[]{0L, 500_000L});
        for (Track t : seq.getTracks())
        {
            for (int i = 0; i < t.size(); i++)
            {
                MidiEvent ev = t.get(i);
                if (!(ev.getMessage() instanceof MetaMessage)) continue;
                MetaMessage mm = (MetaMessage) ev.getMessage();
                if (mm.getType() != 0x51) continue;
                byte[] data = mm.getData();
                if (data.length < 3) continue;
                long us = ((data[0] & 0xFFL) << 16) | ((data[1] & 0xFFL) << 8) | (data[2] & 0xFFL);
                changes.add(new long[]{ev.getTick(), us});
            }
        }
        changes.sort((a, b) -> Long.compare(a[0], b[0]));
        long[] flat = new long[changes.size() * 2];
        for (int i = 0; i < changes.size(); i++)
        {
            flat[i * 2] = changes.get(i)[0];
            flat[i * 2 + 1] = changes.get(i)[1];
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
