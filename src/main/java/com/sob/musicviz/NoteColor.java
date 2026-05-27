package com.sob.musicviz;

import java.awt.Color;

final class NoteColor
{
    private static final Color[] WHEEL = new Color[12];

    static
    {
        for (int i = 0; i < 12; i++)
        {
            WHEEL[i] = Color.getHSBColor(i / 12f, 0.85f, 1f);
        }
    }

    private NoteColor() {}

    static Color forNote(int midiNote)
    {
        int idx = ((midiNote % 12) + 12) % 12;
        return WHEEL[idx];
    }
}
