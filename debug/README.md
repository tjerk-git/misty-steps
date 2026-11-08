# Battery test runs

The goal: find out what drains the battery (GPS, the drifting fog, the map tiles) so we know what to improve.

**Debug builds only.** The debug APK writes two log files on the phone, in the app's private folder `files/debug/`. Release builds log nothing. The logs stay on the phone until you pull them with the script.

| File | What's in it |
|---|---|
| `battery.csv` | One row a minute while the map is on screen or a walk is recording, plus a row at every change (`app_start`, `map_shown`, `map_hidden`, `walk_start`, `walk_end`, `vehicle`, `own_power`). Each row is the state **from that moment on**. Columns: battery %, charge counter (µAh), current (µA), voltage, temperature, plugged in, screen on, recording, map visible, battery saver, GPS fixes since the last row, in vehicle (clearing paused at car/train speed). |
| `sessions.csv` | One row per finished walk: start, end, duration, distance, spots stored, battery % at start and end, new ground (m²). |

If the columns change in a new build, the app moves the old file aside (`battery.csv.<time>.old`) and starts a fresh one.

The walks also appear on the stats page ("Your walks"). In debug builds each walk there shows its battery % at start and end.

## Doing a test run

```sh
export ADB_SERIAL=<phone>   # the Pixel; stays the same when the port changes
debug/run.sh start                    # resets Android's battery stats and clears the app's logs
# unplug the phone, then do the test
debug/run.sh pull pocket-walk         # saves everything to debug/runs/<date>_pocket-walk/
```

Each run folder gets:

- `battery.csv` and `sessions.csv` from the app
- `batterystats.txt`: Android's own accounting for the app (`dumpsys batterystats`), including GPS time, wakelocks and CPU
- `battery_now.txt`, `device.txt`: phone and app version
- `summary.txt`: output of `summarize.py`, drain per state in %/h and mA
- `notes.md`: fill in what you did

You can run the summary on its own: `python3 debug/summarize.py debug/runs/<run>/battery.csv debug/runs/<run>/sessions.csv`.

## Tests worth doing

Do each one for at least 30 minutes; shorter runs mostly measure noise. Keep the brightness the same between runs.

1. **Baseline:** app closed, phone idle in the pocket. This is what the phone uses without us.
2. **Map open, standing still:** screen on, map visible. Measures the fog animation and the location marker.
3. **Pocket walk:** recording, screen off. This is the main use, and the number to get down.
4. **Walk with the map open:** recording, screen on the whole time.
5. **Pocket walk with battery saver on.**

## What we could change, depending on the numbers

- GPS interval: recording asks for a fix every 2 s / 5 m (`RecordingService.startListening`). A longer interval while walking slowly saves a lot.
- Fog frame rate: 20 fps (`FRAME_MS` in `FogOverlay.kt`). It could drop while nothing moves.
- The map's own location marker keeps GPS running while the map is open, even when not recording.
- Camera follow checks four times a second once settled (`MapController.followTick`).

## Where the files live

`debug/runs/` holds the pulled runs. They contain your GPS-derived walk times and distances, but not coordinates.
