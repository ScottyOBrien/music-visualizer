package com.sob.musicviz;

import java.awt.Color;
import net.runelite.api.TileObject;

final class FlashState
{
    final TileObject target;
    final long startMs;
    final Color color;

    FlashState(TileObject target, long startMs, Color color)
    {
        this.target = target;
        this.startMs = startMs;
        this.color = color;
    }

    float progress(long nowMs, int decayMs)
    {
        long age = nowMs - startMs;
        if (age <= 0) return 0f;
        if (age >= decayMs) return 1f;
        return age / (float) decayMs;
    }

    boolean expired(long nowMs, int decayMs)
    {
        return nowMs - startMs >= decayMs;
    }
}
