package com.sob.musicviz;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("musicviz")
public interface MusicVizConfig extends Config
{
    @ConfigSection(name = "Display", description = "Visual options", position = 0)
    String displaySection = "displaySection";

    @ConfigSection(name = "Sync", description = "Audio sync tuning", position = 1)
    String syncSection = "syncSection";

    @ConfigSection(name = "Advanced", description = "Power-user settings", position = 99, closedByDefault = true)
    String advancedSection = "advancedSection";

    @Range(min = 5, max = 30)
    @ConfigItem(keyName = "radius", name = "Radius (tiles)", description = "Scan range around the player", section = displaySection, position = 0)
    default int radius() { return 20; }

    @Range(min = 100, max = 2000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(keyName = "flashDecayMs", name = "Flash decay", description = "How long each flash lingers", section = displaySection, position = 1)
    default int flashDecayMs() { return 400; }

    @Range(min = 0, max = 255)
    @ConfigItem(keyName = "colorAlpha", name = "Flash alpha", description = "Peak opacity of each flash (0-255)", section = displaySection, position = 2)
    default int colorAlpha() { return 160; }

    @ConfigItem(keyName = "selectionMode", name = "Target selection", description = "How notes map to nearby objects", section = displaySection, position = 3)
    default SelectionMode selectionMode() { return SelectionMode.HASH_BY_NOTE; }

    @Range(min = 0, max = 15)
    @ConfigItem(keyName = "melodyChannel", name = "MIDI channel", description = "Channel to react to. 9 = GM percussion", section = syncSection, position = 0)
    default int melodyChannel() { return 1; }

    @Range(min = -2000, max = 2000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(keyName = "syncOffsetMs", name = "Sync offset", description = "Nudge the visualization forward (+) or back (-)", section = syncSection, position = 1)
    default int syncOffsetMs() { return 0; }

    @Range(min = 100, max = 1000)
    @Units(Units.MILLISECONDS)
    @ConfigItem(keyName = "pollIntervalMs", name = "Track poll", description = "How often to check for a track change", section = advancedSection, position = 0)
    default int pollIntervalMs() { return 250; }

    @ConfigItem(keyName = "logCacheMisses", name = "Log cache misses", description = "Log once when a track's MIDI cannot be loaded", section = advancedSection, position = 1)
    default boolean logCacheMisses() { return true; }

    enum SelectionMode
    {
        HASH_BY_NOTE,
        ROUND_ROBIN
    }
}
