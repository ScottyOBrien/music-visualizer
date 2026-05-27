package com.sob.musicviz;

final class NoteEvent implements Comparable<NoteEvent>
{
    final long timestampMs;
    final int channel;
    final int note;
    final int velocity;

    NoteEvent(long timestampMs, int channel, int note, int velocity)
    {
        this.timestampMs = timestampMs;
        this.channel = channel;
        this.note = note;
        this.velocity = velocity;
    }

    @Override
    public int compareTo(NoteEvent other)
    {
        return Long.compare(timestampMs, other.timestampMs);
    }
}
