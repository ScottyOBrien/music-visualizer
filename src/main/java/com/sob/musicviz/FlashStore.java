package com.sob.musicviz;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javax.inject.Singleton;

@Singleton
class FlashStore
{
    private final ConcurrentLinkedQueue<FlashState> active = new ConcurrentLinkedQueue<>();

    void add(FlashState flash)
    {
        active.add(flash);
    }

    void clear()
    {
        active.clear();
    }

    void forEachActive(long nowMs, int decayMs, Consumer<FlashState> visitor)
    {
        Iterator<FlashState> it = active.iterator();
        while (it.hasNext())
        {
            FlashState f = it.next();
            if (f.expired(nowMs, decayMs))
            {
                it.remove();
                continue;
            }
            visitor.accept(f);
        }
    }
}
