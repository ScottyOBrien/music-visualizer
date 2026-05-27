# Music Visualizer

A RuneLite plugin that paints the music onto the game world. Nearby in-game objects flash on the notes of the currently playing OSRS music track — color picked from the note's pitch, target picked from objects within a configurable radius of the player.

## How it works

OSRS music is MIDI-driven and deterministic. The plugin:

1. Detects the active track via `Client.getActiveMidiRequests()`.
2. Asks the running RuneLite client for the track's raw cache bytes via `Client.getIndex(...).loadData(archiveId, 0)`, converts Jagex's packed format to standard SMF using a ported version of `net.runelite.cache.definitions.loaders.TrackLoader`, and parses the result with `javax.sound.midi`.
3. Runs a background scheduler that "plays through" the score in lockstep with wall-clock time since the track started — **without emitting audio**. The actual sound still comes from OSRS.
4. On each note-on event, picks one nearby object and flashes it. The color is derived from the note's pitch on a chromatic color wheel.

## Colors

Each flash's color comes from the MIDI note's pitch, in three steps:

1. **Drop the octave.** Take `note % 12`, which collapses every octave onto the same 12-element wheel. Middle C and the C two octaves above it both hash to index 0 — that's why melodic motifs that repeat at different octaves show up as the same color cluster.
2. **Map the index to a hue on a chromatic color wheel.** Index 0 (C) sits at 0° (red), and each semitone adds 30°. So C♯ is orange, D is yellow, E is green, G is sky blue, B is pink-red, etc.
3. **Hold saturation and brightness constant** at 0.85 and 1.0, so only the hue varies. Notes close in pitch land close on the color wheel; notes that clash musically (a tritone apart) land on opposite sides — which is also where your eye reads "opposite color."

This mapping is sometimes called a **chromatic circle** and has a long history in music visualization (Scriabin famously used a version of it).

## Sync

A `Sync offset (ms)` slider lets you nudge the visualization forward or backward to match what you hear. Each new track resyncs automatically.

## Data collection

This plugin runs entirely on your machine and sends no data anywhere.

## Special thanks to

- [runelite/runelite](https://github.com/runelite/runelite) — the `net.runelite.cache.definitions.loaders.TrackLoader` class does the heavy lifting of converting Jagex's packed music format into standard SMF bytes.
- [Rune-Status/lequietriot-RS-MIDI-Dumper](https://github.com/Rune-Status/lequietriot-RS-MIDI-Dumper) — `net/openrs/cache/track/Track.java` (Adam @ sigterm.info, 2017) is the reference decoder that pointed us at the right loader in the runelite-cache library.

## License

BSD-2-Clause. See [LICENSE](LICENSE).
