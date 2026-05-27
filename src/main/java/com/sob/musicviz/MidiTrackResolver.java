package com.sob.musicviz;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
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

@Slf4j
final class MidiTrackResolver
{
    private MidiTrackResolver() {}

    /**
     * Pulls the raw MIDI byte buffer off whatever object RuneLite's music
     * API hands us. The exact accessor on `MidiRequest` is not part of the
     * stable plugin API surface — try a few likely method names. If none
     * work, the user needs to log what their version exposes and we wire
     * it up explicitly. This is the riskiest piece of the plugin.
     */
    static byte[] extractMidiBytes(Object midiRequest)
    {
        if (midiRequest == null) return null;
        String[] candidates = {"getMidi", "getData", "getMidiData", "getBytes"};
        for (String name : candidates)
        {
            try
            {
                Method m = midiRequest.getClass().getMethod(name);
                Object result = m.invoke(midiRequest);
                if (result instanceof byte[])
                {
                    return (byte[]) result;
                }
            }
            catch (NoSuchMethodException ignored)
            {
            }
            catch (Exception e)
            {
                log.debug("midi accessor {} threw", name, e);
            }
        }
        return null;
    }

    static List<NoteEvent> flatten(byte[] midiBytes) throws Exception
    {
        Sequence seq = javax.sound.midi.MidiSystem.getSequence(new ByteArrayInputStream(midiBytes));
        int resolution = seq.getResolution();
        if (resolution <= 0) return Collections.emptyList();

        long[] tempoByTick = buildTempoMap(seq, resolution);

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

                long ms = tickToMs(ev.getTick(), tempoByTick, resolution);
                out.add(new NoteEvent(ms, sm.getChannel(), sm.getData1(), vel));
            }
        }
        Collections.sort(out);
        return out;
    }

    /**
     * Build a per-tick-bucket tempo lookup so tick→ms can honor tempo
     * changes (MIDI meta event 0x51). Buckets are sparse — we store
     * [tick, microsPerQuarter] pairs and interpolate linearly between.
     */
    private static long[] buildTempoMap(Sequence seq, int ppq)
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
