package com.sob.musicviz.vendored;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal Standard MIDI File parser. Reads the subset of SMF needed by the
 * Music Visualizer plugin: ticks-per-quarter resolution, tempo meta events,
 * and note-on events with non-zero velocity.
 *
 * Written in-house to avoid pulling in javax.sound.midi (Plugin Hub flags
 * it because most plugins misuse it for audio playback; we only need to
 * parse byte streams).
 */
public final class SmfParser
{
    public static final class NoteOn
    {
        public final long tick;
        public final int channel;
        public final int note;
        public final int velocity;

        NoteOn(long tick, int channel, int note, int velocity)
        {
            this.tick = tick;
            this.channel = channel;
            this.note = note;
            this.velocity = velocity;
        }
    }

    public static final class TempoChange
    {
        public final long tick;
        public final long microsPerQuarter;

        TempoChange(long tick, long microsPerQuarter)
        {
            this.tick = tick;
            this.microsPerQuarter = microsPerQuarter;
        }
    }

    public static final class Parsed
    {
        public final int divisionPpq;
        public final List<NoteOn> noteOns;
        public final List<TempoChange> tempoChanges;

        Parsed(int divisionPpq, List<NoteOn> noteOns, List<TempoChange> tempoChanges)
        {
            this.divisionPpq = divisionPpq;
            this.noteOns = noteOns;
            this.tempoChanges = tempoChanges;
        }
    }

    private SmfParser() {}

    public static Parsed parse(byte[] smf)
    {
        Reader r = new Reader(smf, 0, smf.length);
        if (r.readU32() != 0x4D546864) // "MThd"
        {
            throw new IllegalArgumentException("not an SMF (missing MThd)");
        }
        int headerLen = r.readU32();
        int headerEnd = r.pos + headerLen;
        r.readU16();           // format (ignored)
        int trackCount = r.readU16();
        int division = r.readU16();
        r.pos = headerEnd;

        List<NoteOn> noteOns = new ArrayList<>();
        List<TempoChange> tempos = new ArrayList<>();

        for (int t = 0; t < trackCount; t++)
        {
            if (r.pos >= smf.length) break;
            if (r.readU32() != 0x4D54726B) // "MTrk"
            {
                // Some encoders pad; try to resync by scanning.
                int sync = indexOf(smf, r.pos, new byte[]{'M','T','r','k'});
                if (sync < 0) break;
                r.pos = sync + 4;
            }
            int trackLen = r.readU32();
            int trackEnd = r.pos + trackLen;
            parseTrack(r, trackEnd, noteOns, tempos);
            r.pos = trackEnd;
        }

        return new Parsed(division, noteOns, tempos);
    }

    private static void parseTrack(Reader r, int end, List<NoteOn> noteOns, List<TempoChange> tempos)
    {
        long tick = 0;
        int runningStatus = 0;
        while (r.pos < end)
        {
            tick += r.readVarLen();
            int status = r.peekU8();
            if (status < 0x80)
            {
                status = runningStatus;
            }
            else
            {
                r.pos++;
                if (status < 0xF0) runningStatus = status;
            }

            if (status >= 0x80 && status < 0xF0)
            {
                int type = status & 0xF0;
                int channel = status & 0x0F;
                int d1 = r.readU8();
                if (type == 0xC0 || type == 0xD0)
                {
                    // Program change / channel pressure — 1 data byte.
                    continue;
                }
                int d2 = r.readU8();
                if (type == 0x90 && d2 > 0)
                {
                    noteOns.add(new NoteOn(tick, channel, d1, d2));
                }
                // All other channel messages (note off, CC, pitch bend, etc.) ignored.
            }
            else if (status == 0xFF)
            {
                int metaType = r.readU8();
                int len = r.readVarLen();
                if (metaType == 0x51 && len >= 3)
                {
                    long us = (r.readU8() << 16) | (r.readU8() << 8) | r.readU8();
                    tempos.add(new TempoChange(tick, us));
                    r.pos += (len - 3);
                }
                else if (metaType == 0x2F)
                {
                    return;
                }
                else
                {
                    r.pos += len;
                }
            }
            else if (status == 0xF0 || status == 0xF7)
            {
                int len = r.readVarLen();
                r.pos += len;
            }
            else
            {
                // Unknown / malformed — bail out of this track.
                return;
            }
        }
    }

    private static int indexOf(byte[] hay, int from, byte[] needle)
    {
        outer:
        for (int i = from; i <= hay.length - needle.length; i++)
        {
            for (int j = 0; j < needle.length; j++)
            {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static final class Reader
    {
        final byte[] b;
        int pos;
        final int end;

        Reader(byte[] b, int pos, int end)
        {
            this.b = b;
            this.pos = pos;
            this.end = end;
        }

        int peekU8()
        {
            return b[pos] & 0xFF;
        }

        int readU8()
        {
            return b[pos++] & 0xFF;
        }

        int readU16()
        {
            return (readU8() << 8) | readU8();
        }

        int readU32()
        {
            return (readU8() << 24) | (readU8() << 16) | (readU8() << 8) | readU8();
        }

        int readVarLen()
        {
            int v = 0;
            for (int i = 0; i < 4; i++)
            {
                int x = readU8();
                v = (v << 7) | (x & 0x7F);
                if ((x & 0x80) == 0) break;
            }
            return v;
        }
    }

    // Suppress unused warning if the user calls Arrays.toString in debug builds.
    @SuppressWarnings("unused")
    private static String dumpHead(byte[] b)
    {
        return Arrays.toString(Arrays.copyOf(b, Math.min(b.length, 16)));
    }
}
