# Misty Steps: reported bugs

Reports from users. Status: open until reproduced and fixed. "Suspects" are guesses from reading the code, not verified.
Reported against 1.3.1 (versionCode 5) as of 2026-11-12.

## BUG-1: Scrolling and buttons sometimes stop responding

- **Reported:** 2026-11-12, by several users (no device or Android version details yet).
- **Symptom:** map scrolling or panning, and taps on buttons, sometimes do nothing. Not constant, no known trigger.
- **Severity:** high (app feels frozen, no workaround known besides waiting or restarting).
- **Status:** FIXED in 1.4.3 (2026-11-21). Real cause found on a Galaxy S21 (SM-G991B, Android 15, One UI 7): `blockTouchesBelow()` consumed every pointer event in the Final pass, so Compose cancelled every click and scroll inside a full-screen page (first-run questions, province picker, stats, characters, about) whenever the finger sent small move events between down and up. A real S21 panel does; `adb input tap` does not, which is why it never showed in earlier tests. The main map's own buttons were not affected. The earlier suspects (fog mask on the main thread, 1.3.2) were not the cause. Found by recording raw touches with `adb shell getevent -lt` (touch range is 0..4095 on both axes, so scale x by 1079.7/4095 and y by 2399.4/4095), reproduced on the emulator with `adb shell input swipe x y x+4 y+1 150`, confirmed fixed on the phone with a real finger. Also fixed in the same release: `ProvincePacks.state()` listed folders on every call (about 46 ms per download progress tick on the S21), and the map style was reloaded on every download progress tick (1.4.1).
- **Needed from reporters:** device model and Android version, whether a walk was being recorded, whether the screen had just been unlocked or the app resumed from background, whether it recovers on its own, whether battery saver was on.
- **Suspects:**
  - Main thread blocked: the fog overlay redraws on `invalidate()` (`fog/FogOverlay.kt:76`, `:252`) while the map is also drawing; heavy redraw while GPS points arrive could starve touch handling.
  - Fog overlay or another map overlay consuming touch events (long-press for pins vs. pan) and not releasing them.
  - Stats or backup work done on the main thread (`StatsRepository`, `FogStore`) instead of `Dispatchers.IO`.
  - Compose buttons on top of the osmdroid `MapView` losing touch to the view underneath.
- **To try:** reproduce with a long recorded walk and many cleared cells; check for ANRs / dropped frames with the android-profiler or ANR audit; look at `adb shell dumpsys gfxinfo` while it is stuck.

## BUG-2: Map does not work on Android phone with battery saver on

- **Reported:** 2026-11-12, one user (Android phone, model unknown).
- **Symptom:** the map did not work with battery saver (power saving mode) turned on. Unclear if the map stayed blank/grey, showed no tiles, or showed no position and fog.
- **Severity:** high (core screen unusable for users with battery saver on).
- **Status:** resolved for now (2026-11-16, per owner). Never reproduced; cause unknown, possibly the same main-thread fog cost fixed in 1.3.2. Reopen if reports return.
- **Needed from reporter:** device model and Android version, what "did not work" looked like (grey tiles, no fog, no position dot, crash), whether it works again with battery saver off, whether wifi or mobile data was used.
- **Suspects:**
  - Battery saver restricts background data and background activity: OSM tile downloads (osmdroid, `ui/MapController.kt:39`) may be blocked or throttled, giving a grey map.
  - Battery saver on many phones turns GPS location off or lowers its rate, especially when the screen is off; the foreground location service (`record/RecordingService.kt`) may get no fixes, so no position and no fog clearing.
  - Battery saver can disable animations and throttle CPU; the animated fog (`fog/FogOverlay.kt`) may behave badly with animator scale 0 or a throttled CPU.
  - Vendor-specific app killers (Samsung, Xiaomi, OnePlus) stopping the foreground service.
- **To try:** on the Pixel, enable battery saver (`adb shell settings put global low_power 1`) and check map tiles, GPS fixes and fog; also test with animator duration scale 0 and with `adb shell cmd netpolicy` restrictions. If tiles are the cause, show a message when tiles fail to load. If GPS is the cause, detect `PowerManager.isPowerSaveMode` and tell the user in the app.

## Repro attempt 2026-11-13 (Pixel 10 Pro, release 1.3.1, USB adb)

Method: 8 x pan swipes back and forth, `dumpsys gfxinfo` before/after. Saver simulated with `dumpsys battery unplug` + `settings put global low_power 1` (saver does not engage while charging; `low_power 1` alone gave "Battery Saver is currently: OFF"). Phone restored afterwards.

- Normal: 468 frames, 0.43% janky, p50 10 ms, p99 15 ms, 0 slow UI thread.
- Battery saver on: 286 frames, 1.4% janky, p50 11 ms, p99 16 ms, 0 slow UI thread. Tiles load, fog and position dot show, panning smooth.
- Recording with saver on, screen off 40 s: `RecordingService` stays foreground, HIGH_ACCURACY location request stays registered.
- Not reproduced on the Pixel: BUG-2 is not a plain tiles/render failure, and BUG-1 did not show on a fresh start with 23.2 km cleared.
- Not tested: real GPS fixes outdoors under saver, low-end or throttled CPU, long recording, resume from background, vendor app killers (Samsung, Xiaomi, OnePlus).
- Code note: `FogOverlay.draw()` calls `rebuildMask()` on the main thread whenever the view offset changes, so every pan frame repaints the mask on the CPU. Cost grows with cleared cells in view; fine on a Pixel 10 Pro, a plausible freeze source on slow phones (BUG-1) and under CPU throttling (BUG-2).

## Mask rebuild timing 2026-11-13 (Pixel 10 Pro, Dev build, temporary log, since reverted)

`FogOverlay.rebuildMask()` wall time while panning, main thread:

| Zoom | Rebuilds | Avg | Max |
|---|---|---|---|
| 15 | 470 | 2.7 ms | 12 ms |
| 13 | 416 | 2.8 ms | 12 ms |
| 11 | 326 | 1.7 ms | 16 ms |

- Dev data is small (about 4400 fixes). Cost scales with spots in view, so a long-time user (hundreds of km) on a phone several times slower than a Pixel 10 Pro could reach 100+ ms per pan frame. That would feel like frozen scrolling (BUG-1), though buttons freezing too fits main-thread starvation only.
- Not proven to be the user-reported cause. Needs reporter device info, or a stress import of a large trail.
- Fix idea if confirmed: build the mask off the main thread and draw the stale mask (translated) until the new one is ready.

## Fix attempt for BUG-1/BUG-2 suspect, 2026-11-13 (uncommitted)

Fog mask is now built on a worker thread instead of in `FogOverlay.draw()`. `draw()` copies the needed spots (`PointIndex.snapshotIn`), asks for a build only when `MaskPlan.needsRebuild` says so, and meanwhile moves and scales the last finished mask (`MaskPlan.place`). Mask is built 25% larger than the view so small pans never show a gap. Tests: `PointIndexSnapshotTest`, `MaskPlanTest`.

Pan test on the Pixel (Dev build, 8 x back-and-forth swipes, two runs each):

| | Janky | p50 | p99 | Slow UI thread |
|---|---|---|---|---|
| Old (sync rebuild) | 3.8%, 6.7% | 14, 16 ms | 26, 29 ms | 17, 28 |
| New (async) | 1.1%, 1.0% | 12 ms | 17 ms | 5, 4 |

Still unverified: that this is what real users hit (no confirmation from reporters), behaviour on a slow phone, a walk recorded outdoors (mask rebuilds each GPS fix via the version bump), and a visual check of fast zoom (stale mask is scaled until the new one lands).

## Notes

- BUG-1 and BUG-2 may share a cause (a throttled CPU or background restriction making the main thread or overlay slow). Check this before fixing them separately.
- The in-app "Send feedback" button mails debug@potloodgum.com; ask reporters to use it so the report includes version info.


## Lessons from the S21 hunt (2026-11-21)

- Synthetic input (`adb input tap`) hides touch bugs that depend on move events. Test full-screen pages with `adb shell input swipe x y x+4 y+1 150` as well, and on a real device with a real finger.
- When buttons "do nothing", record the raw touches (`getevent -lt`) and compare them with where the buttons are before suspecting performance.
- Emulator frame timings are dominated by the host: close other apps, use `-gpu host`, and do not trust software rendering. A throttle that pauses the emulator in 20 ms blocks sets its own frame-time floor.
- 1.4.2 also shipped an adaptive fog (`FogGovernor`); emulator tests showed it gains only about 15% at 20% CPU speed. It was not the cause and can be removed if it ever gets in the way.
