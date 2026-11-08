# Misty Steps: workflow and status

Status as of 2026-11-08. Next session: test on a real walk (see the checklists below), and do the first battery test runs (`debug/README.md`).

## What the app does

An Android app (Kotlin, Jetpack Compose and osmdroid) that covers the OpenStreetMap world in drifting white fog.

- **Clearing fog:** while recording ("Start wandering"), every GPS spot clears the fog in a 50 m circle. The spot where a walk starts clears 100 m.
- **Trails:** stored only on the phone (SQLite). Backup and device transfer are switched off.
- **Stats page:** how much of your city, your country and the world you have cleared. Borders come from OSM Nominatim, are fetched once per area and cached on the phone. Only your position rounded to about 1 km is sent.
- **Character select:** six pixel-art animals in hiking gear (16x16, 4-frame walk cycle, idle bob), all with four hiking boots: **Trail Cat** (bucket hat, backpack; default), **Scout Dog** (bandana, backpack), **Sea Otter** (yellow raincoat and wellies), **Capybara** (mandarin on its head, backpack), **Wander Cow** (red scarf), **Trek Horse** (saddlebag). Replaced the wizard/rogue set on 2026-11-08; old saved picks fall back to the cat. Your character stands on your location on the map and faces the way you walk.
- **Walks (sessions):** every "Start wandering" to stop is one walk, logged in the `sessions` table (start, end, distance, spots, battery % at start and end). Each spot remembers its walk. Walks the app got killed during are closed at their last spot on the next start. The stats page lists recent walks under "Your walks".
- **Trail:** each walk leaves a dotted line behind the character. The walk you're on is drawn in rose (Ember), earlier walks in faint grey-blue. The line breaks where the GPS jumped more than ~330 m.
- **Camera:** on opening the app (and when coming back after more than 5 minutes) the camera starts zoomed out (zoom 15.5) on your character and glides after it as you walk. A one-finger drag stops following; the location button starts it again. Pinch and double-tap zoom keep following. On the very first launch it shows the world and flies down to you on the first fix.
- **New ground per walk:** when a walk ends, the app counts the ~10 m cells it cleared that no other walk had (`new_area_m2` on the walk). The walk log shows it ("0.6 ha new mist").
- **No clearing in motor vehicles:** anything under your own power clears the mist: walking, running, cycling, skating, a step, a wheelchair or mobility scooter (not everyone can walk). Above 32 km/h held over 30 s (car, train, bus out of town) clearing pauses until you're below 25 km/h again (`record/SpeedGate.kt`). The app shows "Too fast for the mist: it only clears under your own steam", the notification says so too, and GPS drops to one fix per 5 s meanwhile. The trail isn't joined across the ride. Known gap: a slow city bus (15–25 km/h) still clears; see "Tell buses from bikes" in the roadmap.
- **Walk postcard:** after a walk of 50 m or more, a pixel-style postcard pops up: the map with that walk's clearing cut out of the mist (tinted for the time of day it ended), the rose trail, your character where you stopped, and distance, duration and new ground. Save puts it in Pictures/Misty Steps; Share opens the share sheet. Tap any walk in the walk log to see its postcard again.
- **Neighbourhood stats:** the stats page has a neighbourhood card above the city: % cleared of your wijk (OSM `place=quarter`, falling back to suburb, neighbourhood, then admin levels 11/10/9). All neighbourhood borders of your city are fetched once from Overpass by the city's OSM id and cached in `files/boundaries/neighbourhoods/`; which one you're in is worked out on the phone, so no position is sent.
- **Street completion:** in the same card, "x of y streets walked", plus "Still in the mist": the streets you haven't finished, the nearly-done ones first. A street counts once 70% of it (the part inside the neighbourhood) is within 25 m of your trail. Streets are fetched once per neighbourhood by its OSM id and cached in `files/boundaries/streets/`. Overpass is often busy, so three servers are tried in turn.
- **Fog through the day:** the mist is tinted by the sun's height where you are (worked out from the clock, no network): blue-grey at night, lilac at blue hour, rosy around sunrise and sunset, warm in golden hour, white by day. Updates once a minute (`fog/MistPalette.kt`).
- **Battery logging (debug builds only):** writes `battery.csv` and `sessions.csv` to the app's private `files/debug/`. Pull them with `debug/run.sh`. See `debug/README.md`.
- **Launcher icon:** the pixel trail cat in a cleared patch of map. It is generated from the sprite data.

## Project layout

| Area | Files |
|---|---|
| App start, osmdroid config | `app/src/main/java/com/mistysteps/app/MistyApplication.kt`, `MainActivity.kt` |
| Map, camera follow, fog, location marker | `ui/MapController.kt`, `fog/FogOverlay.kt`, `fog/FogTexture.kt` |
| Trail line | `trail/TrailOverlay.kt`, `data/Trails.kt` |
| Recording (foreground service) | `record/RecordingService.kt` |
| Trail and walk storage, spatial index | `data/FogStore.kt`, `data/TrailDatabase.kt` (DB version 4: `points`, `sessions` with `new_area_m2`), `data/PointIndex.kt`, `data/Mercator.kt` |
| Vehicle-speed pause | `record/SpeedGate.kt`, used by `data/FogStore.kt` |
| Time-of-day mist | `fog/MistPalette.kt` |
| Neighbourhood, streets | `stats/Neighbourhoods.kt`, `stats/Overpass.kt`, `stats/OsmRings.kt` (joins relation pieces into rings), `stats/StreetCoverage.kt` |
| Walk postcard | `postcard/PostcardRenderer.kt`, `postcard/PostcardShare.kt`, `ui/PostcardScreen.kt`, FileProvider in the manifest |
| Battery logging (debug builds) | `debug/BatteryLog.kt` in the app; `debug/` folder at the project root for test runs |
| Stats | `stats/StatsRepository.kt`, `stats/BoundaryProvider.kt`, `stats/Boundary.kt`, `stats/ClearedCells.kt`, `ui/StatsScreen.kt` |
| Characters | `assets/characters.txt` (sprite data), `character/Characters.kt`, `character/CharacterOverlay.kt`, `ui/CharacterScreen.kt` |
| Main screen | `ui/MistyScreen.kt`, `ui/Theme.kt` |
| Tools | `tools/preview_sprites.py` (render all sprites to a PNG), `tools/make_icon.py` (regenerate the launcher icon from the cat sprite), `tools/sprites.py` (shared parser) |
| Tests | `app/src/test/...`: Mercator, spatial index, border area and containment, cleared-cell counting, trail splitting, percent and duration formatting, speed gate, sun position and mist colours, ring assembly, neighbourhood picking, street coverage (27 tests) |

## Build and run

Needs JDK 17 and the Android SDK at `/opt/homebrew/share/android-commandlinetools` (already set in `local.properties`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
adb -s adb-57291FDCH000SR-zksp6T._adb-tls-connect._tcp install -r app/build/outputs/apk/debug/app-debug.apk
adb -s adb-57291FDCH000SR-zksp6T._adb-tls-connect._tcp shell am start -n com.potloodgum.mistysteps/com.mistysteps.app.MainActivity
```

### Connecting the Pixel 10 Pro

- **Use wireless debugging, not USB.** adb over USB can't see the phone from Claude Code's sandbox.
- The Mac is already paired with the phone. To reconnect: on the phone, turn on Developer options → **Wireless debugging**, then run `adb mdns services` to find the IP:port and `adb connect <ip:port>`. The port changes whenever wireless debugging restarts.
- The phone can show up twice (manual connection plus auto-discovered), so always pass `-s <ip:port>`.
- The auto-discovered name doesn't change when the port does, so it works as a serial too: `-s adb-57291FDCH000SR-zksp6T._adb-tls-connect._tcp` (also as `ADB_SERIAL` for `debug/run.sh`).
- Screenshots come out black when the phone is locked. Keep it unlocked while testing, or run `adb shell svc power stayon true` while it charges (undo with `false`).

### Editing characters

1. Edit `app/src/main/assets/characters.txt`. The format is explained at the top of the file.
2. Run `python3 tools/preview_sprites.py /tmp/sprites.png` and look at the frames.
3. If you changed the cat, run `python3 tools/make_icon.py` to regenerate `res/drawable/ic_launcher_foreground.xml`.

## Verified on the phone (2026-11-07)

- The map loads. The fog drifts, and the cleared circle lines up with the streets. Status bar icons are readable, and the OSM attribution is visible.
- Stats: Leeuwarden 0.016%, Netherlands 0.000014%, Earth 0.0000000018% (from 22 m walked, 9200 m² cleared). Borders are cached in `files/boundaries/`.
- Character select works: picking a character is saved and shows on the map and on the button. Taps on empty areas no longer fall through to the map.
- The launcher icon shows the cat.
- No crashes.

## Test tomorrow (needs a real walk outside)

- [ ] Start wandering → the notification appears and the fog clears **100 m** at the starting spot, then **50 m** along the trail.
- [ ] The character plays its **walk animation** while you move and the **idle bob** when you stop. It **faces the direction** you walk and doesn't flip while standing still.
- [ ] Screen off and phone in a pocket for about 10 minutes → the trail keeps recording, and "x m of mist cleared" goes up.
- [ ] Stop from the notification's **Stop** button and from the in-app button.
- [ ] Stats page after the walk: the city and country percentages go up.
- [ ] Permission flows. Easiest after `adb uninstall com.potloodgum.mistysteps`, then reinstall:
  - [ ] Deny once → the explanation dialog shows next time. Deny again → the "Open settings" dialog.
  - [ ] Grant "Approximate" only → asked to allow precise location.
  - [ ] The "Show my location" button asks for permission and zooms in. It must **not** start recording.
- [ ] Rotate to landscape: the buttons stay clear of the camera cutout.
- [ ] Panning and zooming at world level stays smooth.

## Test the new things (added 2026-11-08)

Install over the old build (don't uninstall first): the database upgrades from version 2 to 3, and the old fog must stay.

- [ ] Old cleared fog is still there after the upgrade. Old spots show as a faint grey-blue line.
- [ ] Open the app → the camera starts zoomed out on your character, not wherever you left it.
- [ ] Walk → the camera glides after you without jumping. Drag the map → it stops following. Location button → it glides back and follows again. Pinch zoom → still follows.
- [ ] Walk → a rose dotted line grows behind the character. Stop → the line turns grey-blue. Start again → a new rose line, not joined to the old one.
- [ ] Stats page → "Your walks" lists the walk with time, duration, distance (and battery % in the debug build).
- [ ] Battery log: `debug/run.sh start` before, `debug/run.sh pull <label>` after. `summary.txt` has rows for the states you were in.

## Test the roadmap batch (built 2026-11-08, not yet seen on the phone)

Installed and running; the map and the stats page were checked on the phone. The neighbourhood and street logic was checked against real Leeuwarden data on the Mac: it picks the right wijk (0.38 km², 45 streets).

- [x] Stats page: the neighbourhood card shows your wijk with %, streets walked and "Still in the mist" (2026-11-08: a wijk in Leeuwarden 15%, 5.9 of 38.1 ha, 1 of 43 streets). Loads straight away once cached. The first fetch was slow because Overpass was busy; streets now load separately below the % with a "Try again" button.
- [ ] Walk ≥ 50 m and stop → the postcard pops up with map tiles, the clearing, the rose trail and your character. Save → appears in Photos (Pictures/Misty Steps). Share → share sheet with the image.
- [ ] Walk log: the walk shows "… new mist"; tapping it opens its postcard. Walking an old route again gives little or no new mist.
- [ ] Bike ride while recording: the fog keeps clearing the whole way, even fast downhill.
- [ ] Car or train ride while recording: after ~15–30 s above 32 km/h the "Too fast for the mist" chip shows, the notification changes, and the fog stops clearing. Out and walking: clearing resumes, with no line drawn across the ride.
- [ ] Mist colour: open the app around sunset and after dark. Rosy, then blue-grey; status bar icons still readable.

## Publishing (own F-Droid-style repo, by Potloodgum)

Decided 2026-11-08: publish in Tjerk's own F-Droid-style repo, not the Play Store. In the app, an About screen (stats page → "Made by Potloodgum") says who made it and why, links to potloodgum.com, and has a "Buy me a coffee" button (buymeacoffee.com/tjerk). The text is in `res/values/strings.xml` (`about_*`).

Before the first public release:
- [x] **App id:** `com.potloodgum.mistysteps` (changed 2026-11-08, before any release). Never change it again. The code package (`namespace`) stays `com.mistysteps.app`; users don't see it. The old `com.mistysteps.app` install on Tjerk's phone had its data copied over.
- [x] **Release signing key** (made 2026-11-08): `~/Keystores/potloodgum/mistysteps-release.jks`, alias `mistysteps`, RSA 4096, valid until ~2054. The password is in `keystore.properties` at the project root (not in git). `./gradlew assembleRelease` signs with it automatically. Certificate SHA-256: `7c:f2:c4:77:f3:f7:31:e8:21:02:17:f1:81:38:cf:f1:fb:96:31:72:5d:47:ad:97:5e:2c:a8:9b:b4:0b:65:15`.
- [ ] **Back up the key:** copy `mistysteps-release.jks` and the password (from `keystore.properties`) to a password manager or a second disk. If you lose either one, you can never publish an update to people who installed the app.
- [ ] Debug builds (what goes on the phone during development) are signed with a different key, so switching that phone to the release APK means uninstalling first. Copy the data over with run-as first, like the move from the old app id.
- [ ] **Version numbers.** Raise `versionCode` (and `versionName`) in `app/build.gradle.kts` for every release.
- [ ] **Release build.** `./gradlew assembleRelease`. The battery logging is debug-only, so it's not in the release build.
- [ ] **Store texts.** Add `fastlane/metadata/android/en-US/` with `title.txt`, `short_description.txt`, `full_description.txt`, `images/phoneScreenshots/`, and `changelogs/<versionCode>.txt`. F-Droid tools read these.
- [ ] **Repo.** `fdroid init` and `fdroid update` (fdroidserver), then host the `repo/` folder as static files (e.g. under potloodgum.com). Users add the repo URL and fingerprint to F-Droid or Droid-ify. In the metadata, set `Donate: https://buymeacoffee.com/tjerk` and `WebSite: https://potloodgum.com`.
- [ ] **License.** Pick one (e.g. GPL-3.0 or MIT) and add a `LICENSE` file, if the source is going public.
- [ ] **OpenStreetMap services.** The map tiles, Nominatim and Overpass are free public services for light use. The app identifies itself and caches what it can, but if many people use it, switch the tiles to another provider (see the note at the bottom of README.md).



- **Netherlands looks too big:** the country area (64,809 km²) includes territorial waters from the OSM border. Land plus inland water is about 41,500 km², so the country percentage reads about 35% low. Possible fix: measure against land-only borders.
- **"City" means the town itself:** for Leeuwarden that's 58.66 km², not the whole municipality (about 255 km²).
- **No export:** trails can't be exported or backed up. GPX export is on the roadmap (Later).
- **Public tile servers:** the app uses OSM's public tiles. Switch provider if more people start using it.
- **Battery (fixed 2026-11-08):** the map was redrawing at 60 fps all the time. The camera follow tried to move by fractions of a pixel, the map rounded them away, and the loop never settled. The camera now keeps its own exact position and stops once centred (with an 8 px deadband for GPS jitter). The fog also moved from CPU to GPU drawing and runs at ~12 fps (`FRAME_MS` in `FogOverlay.kt`); with battery saver on it stands still. Measured with the map open: 1238 → 388 frames per 20 s, and CPU 1.27 → 0.24 cores. The fog still pauses when the stats or character page is open. Re-measure the "map on screen" drain (was 13.6 %/h) on the next test run.
- **Notification icon:** the small icon is still the footprints, not the cat.
- **Trail drawing at scale:** the trail path is rebuilt every frame while panning. Fine for months of walks; if it ever stutters, cache it per zoom level.

## Roadmap (chosen 2026-11-08)

### Built (2026-11-08)

New ground per walk, no clearing at vehicle speed, walk postcard, neighbourhood stats, street completion, fog through the day. See "What the app does" for how each one works. The plans below are kept for reference.

1. **New ground per walk.** Each walk in the log shows how much new area it cleared, separate from ground already walked ("1.8 km · 0.6 ha new mist"). At walk end, count the walk's ~10 m cells (`stats/ClearedCells.kt`) that weren't cleared before it started. Store it as `new_area_m2` on the `sessions` row.
2. **No clearing in motor vehicles.** Built with a speed rule (32 km/h on, 25 km/h off). Bikes, skates, steps and wheelchairs keep clearing (changed 2026-11-08: not everyone can walk).
3. **Walk postcard.** After you stop: a pixel-style card with the walk's trail on the map, distance, duration, new ground and your character. Save it to the photo gallery or share it via the Android share sheet. Render it offscreen to a bitmap (map snapshot plus trail plus text). Show it automatically on stop, and from the walk log.
4. **Neighbourhood stats.** A percentage for your neighbourhood (OSM admin level 10, "buurt"/"wijk") on the stats page, above the city. Uses the same Nominatim lookup and cache as the city and country borders. Some places have no level 10; fall back to level 9 or hide the card.
5. **Street completion.** "41 of 120 streets walked in the Jordaan." Fetch the neighbourhood's streets once (Overpass, by area id, so no position is sent) and cache them. A street counts as walked when most of its length is within ~25 m of your trail. List streets you haven't walked yet as suggestions.
6. **Fog that follows the time of day.** Tint the fog from the phone's clock and the sun position: rosy at sunrise and sunset, white by day, blue-grey at night. Calculate sunrise from the last known position, with no network. Only `basePaint` and the swirl tints in `FogOverlay.kt` change, and the system bar icons must stay readable.

### Later

- **Things hidden in the fog.** Windmills, viewpoints, churches, benches and bridges from OSM stay hidden until you clear their spot, then go into a "Discoveries" collection. Needs an area-based Overpass lookup and a collection screen.
- **Home screen widget.** Your character, your last walk and your neighbourhood %.
- **GPX export.** Export walks as GPX files through the Android share sheet or file picker: a backup without accounts.
- **Tell buses from bikes (without Google Play services).** Speed alone lets a slow city bus clear fog. No Google: use the phone's own motion sensor. Pedalling, walking and pushing a wheelchair make a rhythmic 1–2 Hz movement; riding in a bus or car is smooth with bumps and engine hum. Plan: (1) log accelerometer features during test rides in the debug build (`debug/` runs: bike, bus, car, train), (2) tune a simple rule from that data, (3) pause clearing when it says "vehicle" at any speed. Sensor batching keeps the battery cost low. Cheap first step: also treat short bursts above ~40 km/h as a vehicle, which catches buses on through roads.
- **Unlocks.** New characters or hats at distance milestones, finished streets or neighbourhood percentages. Builds on the character select.

## Fun extras (ideas only, not built)

### StreetPass mode

When two people with Misty Steps walk past each other, their characters meet. Later you find a little visitor in your fog: "A Pixel Fox passed you on Nieuwestad, 14:32".

- **How:** while recording, the phone sends out a short Bluetooth LE signal with a random ID that changes every 15 minutes, plus the chosen character. Phones that hear each other within ~10 m save a "pass". Android's Nearby Connections API is an alternative.
- **What you'd see:** the visitor's character stands where you met, in a small cleared spot. A "Passes" list on the stats page, and maybe a small bonus clearing for both.
- **Privacy:** opt-in only. No accounts and no server: only the random ID and the character are exchanged, never names or trails. Needs the Bluetooth "Nearby devices" permission, and more battery while on (measure it with the `debug/` runs).
- **Name:** "StreetPass" is Nintendo's trademark. It's fine as a working title, but pick our own name before release (e.g. "Mist Meetings" or "Passing Wanderers").

## Considered and dropped

- **Polarsteps import (2026-11-08):** doesn't fit. Misty Steps is about walking, and imported trips (sparse points, cars, trains, flights) would clear fog you didn't walk.
