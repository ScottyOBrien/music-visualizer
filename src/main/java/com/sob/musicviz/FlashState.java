package com.sob.musicviz;

import java.awt.Color;
import net.runelite.api.GameObject;

final class FlashState
{
    final GameObject target;
    final long startMs;
    final Color color;

    FlashState(GameObject target, long startMs, Color color)
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
