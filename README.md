# Music Visualizer

A RuneLite plugin that paints the music onto the game world. Nearby in-game objects flash on the notes of the currently playing OSRS music track — color picked from the note's pitch, target picked from objects within a configurable radius of the player.

## How it works

OSRS music is MIDI-driven and deterministic. The plugin:

1. Detects the active track via `Client.getActiveMidiRequests()`.
2. Loads the same MIDI file from your local OSRS cache and parses it with `javax.sound.midi`.
3. Runs a background scheduler that "plays through" the score in lockstep with wall-clock time since the track started — **without emitting audio**. The actual sound still comes from OSRS.
4. On each note-on event, picks one nearby object and flashes it. The color is derived from the note's pitch on a chromatic color wheel.

## Sync

A `Sync offset (ms)` slider lets you nudge the visualization forward or backward to match what you hear. Each new track resyncs automatically.

## Data collection

This plugin runs entirely on your machine and sends no data anywhere.

## Special thanks to

- [runelite/runelite](https://github.com/runelite/runelite) — the `net.runelite.cache.definitions.loaders.TrackLoader` class does the heavy lifting of converting Jagex's packed music format into standard SMF bytes.
- [Rune-Status/lequietriot-RS-MIDI-Dumper](https://github.com/Rune-Status/lequietriot-RS-MIDI-Dumper) — `net/openrs/cache/track/Track.java` (Adam @ sigterm.info, 2017) is the reference decoder that pointed us at the right loader in the runelite-cache library.

## License

BSD-2-Clause. See [LICENSE](LICENSE).
