# Misty Steps: workflow and status

Status as of 2026-11-08. Next session: test on a real walk (see the checklists below), and do the first battery test runs (`debug/README.md`).

## What the app does

An Android app (Kotlin, Jetpack Compose and osmdroid) that covers the OpenStreetMap world in drifting white fog.

- **Clearing fog:** while recording ("Start wandering"), every GPS spot clears the fog in a 50 m circle. The spot where a walk starts clears 100 m.
- **Trails:** stored only on the phone (SQLite). Backup and device transfer are switched off.
- **Stats page:** how much of your city, your country and the world you have cleared. City and country borders come from `assets/nl-index.json` (all Dutch municipalities and the country border, built from a Geofabrik dump by `tools/maps/index.py`), so the lookup happens on the phone and nothing is sent. Outside the Netherlands only the world stat shows. Since 1.3.1 the app makes no Nominatim or Overpass requests (their usage policies count all users of an app together; see `docs/superpowers/specs/2026-11-10-offline-maps-design.md`). CI fails if one comes back.
- **Character select:** six pixel-art animals in hiking gear (plus the Waddle Duck, an Indian runner duck on two feet, added 2026-11-22 and the Santa Rat, a rat in a Christmas hat, added 2026-11-24) (16x16, 4-frame walk cycle, idle bob), all with four hiking boots: **Trail Cat** (bucket hat, backpack; default), **Scout Dog** (bandana, backpack), **Sea Otter** (yellow raincoat and wellies), **Capybara** (mandarin on its head, backpack), **Wander Cow** (red scarf), **Trek Horse** (saddlebag). Replaced the wizard/rogue set on 2026-11-08; old saved picks fall back to the cat. Your character stands on your location on the map and faces the way you walk.
- **Christmas card (2026-11-24):** after the first-run steps, once per install, a small card shows the walking Santa Rat with "Merry Christmas!" and two buttons: "Try the rat" (makes it your character) and "Not now". Texts in English and Dutch (`rat_card_*` strings); the saved flag is `rat_xmas_2026_seen` (Android `misty` preferences, iPhone `UserDefaults`). Shared code: `announce/RatAnnouncement.kt`, `ui/RatCard.kt`; iPhone test hook `-ratcard 1` shows it whatever the flag says.
- **The "You" page (2026-11-24):** the character button on the map opens a page for you, not for numbers: your character walking, its name (yours or the species, with a pencil to rename it) and "Change character", which opens a bottom sheet with all the characters. Under "Settings": Your maps, Your trails (export and import), Usage counts and the privacy note; under "About": Made by and Send feedback. The stats page behind the bar-chart button is now only numbers: totals, neighbourhood, city, country, world, Your walks and Your places. Shared code: `ui/YouScreen.kt`, `ui/CharacterScreen.kt` (`CharacterSheet`), `ui/StatsScreen.kt`; Android `AndroidYouScreen`, iPhone route `you` (`-route you` opens it in the simulator).
- **First run (changed 2026-11-24):** a new install sees onboarding, then the province picker. The question "Count opens, anonymously?" is gone; the open count stays off until someone turns on "Usage counts" on the You page, and earlier answers are kept. Whether to drop the feature altogether is open.
- **The pill at the top of the map (2026-11-24):** no app name any more: the mist you cleared in your city or town, told in buckets (Dutch: emmers). Three short lines: a big number ("7.800.000"), "buckets of mist cleared", and "in Leeuwarden (0,066%)". One bucket is 10 litres and the mist is 2 m deep, so one square metre is 200 buckets (`stats/MistBuckets.kt`). The place is the town (woonplaats) you stand in from the bundled borders (`stats/PillPick.kt`: town, else the country, else just the total area cleared). It is recounted at most every 10 s, only when the cleared ground or your spot (about 50 m) changed (`PillRefresh.key`), only while the map is in front (Android stops with the activity, iPhone with the scene phase and when a page covers the map), keeping the old text if a count fails. The banner and the eraser bar sit under the pill, however tall it is. Tap it for the stats page.
- **Fun facts on the stats page (2026-11-24):** a card under the two top tiles with up to three quirky lines: your distance, the ground you cleared and the mist you cleared (as volume: 2 m deep) compared with something real (marathons, the Afsluitdijk, Elfstedentocht, football pitches, Vondelpark, Monaco, Texel, Olympic pools, the IJsselmeer, the moon). `stats/FunFacts.kt` has the tables of references with a source note per constant, picks one whose ratio reads nicely (1,5 to 9.999 times; the moon as a percentage) and rotates by day; the sentences are in the strings files (`fact_<measure>_<reference>`, English and Dutch, each written for its own reference); `ui/FunFactsText.kt` builds the lines. A measure with nothing sensible to say is left out, and with no lines the card is hidden. Under the distance line comes your selected animal's own count, using its stride from `characters.txt`: "Your Sea Otter needed about 530 steps to walk all that." (your name for it if you gave one; `fact_character_steps`).
- **Walks (sessions):** every "Start wandering" to stop is one walk, logged in the `sessions` table (start, end, distance, spots, battery % at start and end). Each spot remembers its walk. Walks the app got killed during are closed at their last spot on the next start. The stats page lists recent walks under "Your walks".
- **Trail:** each walk leaves a dotted line behind the character. The walk you're on is drawn in rose (Ember), earlier walks in faint grey-blue. The line breaks where the GPS jumped more than ~330 m.
- **Camera:** on opening the app (and when coming back after more than 5 minutes) the camera starts zoomed out (zoom 15.5) on your character and glides after it as you walk. A one-finger drag stops following; the location button starts it again. When your character is off-screen (after a drag, or after erasing somewhere else) a small round pointer with its face and an arrow sits on the screen edge toward it; a tap does what the location button does (added 2026-11-24, `shared/.../map/OffscreenPointer.kt`, Android `ui/OffscreenPointerButton.kt`, iPhone `OffscreenPointerView.swift`; iPhone simulator hook `-pointerdemo 1` starts with follow off). Pinch and double-tap zoom keep following. On the very first launch it shows the world and flies down to you on the first fix.
- **New ground per walk:** when a walk ends, the app counts the ~10 m cells it cleared that no other walk had (`new_area_m2` on the walk). The walk log shows it ("0.6 ha new mist").
- **No clearing in motor vehicles:** anything under your own power clears the mist: walking, running, cycling, skating, a step, a wheelchair or mobility scooter (not everyone can walk). Above 32 km/h held over 30 s (car, train, bus out of town) clearing pauses until you're below 25 km/h again (`record/SpeedGate.kt`). The app shows "Too fast for the mist: it only clears under your own steam", the notification says so too, and GPS drops to one fix per 5 s meanwhile. The trail isn't joined across the ride. Known gap: a slow city bus (15–25 km/h) still clears; see "Tell buses from bikes" in the roadmap.
- **Walk postcard:** after a walk of 50 m or more, a pixel-style postcard pops up: the map with that walk's clearing cut out of the mist (tinted for the time of day it ended), the rose trail, your character where you stopped, and distance, duration and new ground. Save puts it in Pictures/Misty Steps; Share opens the share sheet. Tap any walk in the walk log to see its postcard again.
- **Swiping through postcards (2026-11-24):** on the postcard screen you swipe left and right through your walks, newest first (the finished walks the walk log lists, and the one you opened), with a small "3 / 17" under the title; Save and Share act on the one on screen. Each postcard is painted when you reach it, one at a time, and only the current one and its neighbours stay in memory. Opening a postcard from the stats page keeps the stats page underneath, so leaving brings it back with its scroll position; the postcard after a finished walk returns to the map. Shared code: `ui/PostcardPaging.kt`, `ui/PostcardScreen.kt` (`PostcardHost.walkIds`).
- **Steps on the postcard (2026-11-24):** under the stats line the postcard says what the walk's distance is in steps of your animal, for example "That is about 31,000 Trail Cat steps." (your own name for it if you gave one). Each character has a playful `stride = <metres>` line in `characters.txt` (cat 0.2, horse 0.9, rat 0.05, and so on; 0.3 when missing); the maths and the text are shared (`postcard/WalkSteps.kt`, `ui/PostcardSteps.kt`), the two renderers only draw the line and shrink it to fit. Not stored per walk: an old postcard follows the animal you have selected now.
- **Neighbourhood stats:** the stats page has a neighbourhood card above the city: % cleared of your wijk (OSM `place=quarter`, falling back to suburb, neighbourhood, then admin levels 11/10/9). Since 1.3.1 these are only read from what earlier versions saved in `files/boundaries/neighbourhoods/` (keyed by the city's OSM relation); nothing is fetched. Without saved data the card is simply left out.
- **Street completion:** in the same card, "x of y streets walked", plus "Still in the mist": the streets you haven't finished, the nearly-done ones first. A street counts once 70% of it (the part inside the neighbourhood) is within 25 m of your trail. Since 1.3.1 streets are only read from what earlier versions saved in `files/boundaries/streets/`.
- **Fog through the day:** the mist is tinted by the sun's height where you are (worked out from the clock, no network): blue-grey at night, lilac at blue hour, rosy around sunrise and sunset, warm in golden hour, white by day. Updates once a minute (`fog/MistPalette.kt`).
- **Battery logging (debug builds only):** writes `battery.csv` and `sessions.csv` to the app's private `files/debug/`. Pull them with `debug/run.sh`. See `debug/README.md`.
- **Launcher icon:** the pixel trail cat in a cleared patch of map. It is generated from the sprite data.
- **Pins:** leave a note where something caught your eye. While wandering, "Drop a pin" puts one where you stand; a long-press on ground you have already cleared adds one there (in the fog: "Walk here first to leave a pin"). Pick one of 12 labels in four groups (Rest, Senses, Animals, Finds) or "Something else", plus an optional note of up to 80 characters. Pins show as pixel icons on the map (dots when zoomed out, hidden below zoom 10), on the walk's postcard, in the walk log and under "Your places" on the stats page. Tap one to read, edit or delete it. Stored in the `pins` table, included in export/import, never sent anywhere. Icons are in `assets/pins.txt`, same format as the characters. Design: `docs/superpowers/specs/2026-11-09-pins-design.md`.

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
| Neighbourhood, streets (saved data only) | `stats/Neighbourhoods.kt`, `stats/OsmRings.kt` (joins relation pieces into rings), `stats/StreetCoverage.kt` |
| Walk postcard | `postcard/PostcardRenderer.kt`, `postcard/PostcardShare.kt`, `ui/PostcardScreen.kt`, FileProvider in the manifest |
| Battery logging (debug builds) | `debug/BatteryLog.kt` in the app; `debug/` folder at the project root for test runs |
| Stats | `stats/StatsRepository.kt`, `offline/CityIndex.kt` (town and country borders from `assets/nl-index.json`), `stats/Boundary.kt`, `stats/ClearedCells.kt`, `ui/StatsScreen.kt` |
| Town index builder | `tools/maps/index.py` (pyosmium + shapely, `tools/maps/requirements.txt`); the Geofabrik dump goes in `tools/maps/data/` (git-ignored) |
| Characters | `assets/characters.txt` (sprite data), `character/Characters.kt`, `character/CharacterOverlay.kt`, `ui/CharacterScreen.kt` |
| Main screen | `ui/MistyScreen.kt`, `ui/Theme.kt` |
| Tools | `tools/preview_sprites.py` (render all sprites to a PNG), `tools/make_icon.py` (regenerate the launcher icon from the cat sprite), `tools/sprites.py` (shared parser) |
| Tests | `app/src/test/...`: Mercator, spatial index, border area and containment, cleared-cell counting, trail splitting, percent and duration formatting, speed gate, sun position and mist colours, ring assembly, neighbourhood picking, street coverage (27 tests) |

## Build and run

Needs JDK 17 and the Android SDK at `/opt/homebrew/share/android-commandlinetools` (already set in `local.properties`).

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew testDebugUnitTest assembleDebug
adb -s <phone> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <phone> shell am start -n com.potloodgum.mistysteps.debug/com.mistysteps.app.MainActivity   # the Dev app
```

### Connecting the Pixel 10 Pro

- **Use wireless debugging, not USB.** adb over USB can't see the phone from Claude Code's sandbox.
- The Mac is already paired with the phone. To reconnect: on the phone, turn on Developer options → **Wireless debugging**, then run `adb mdns services` to find the IP:port and `adb connect <ip:port>`. The port changes whenever wireless debugging restarts.
- The phone can show up twice (manual connection plus auto-discovered), so always pass `-s <ip:port>`.
- The auto-discovered name doesn't change when the port does, so it works as a serial too: `-s <phone>`, where `<phone>` is the `adb-…._adb-tls-connect._tcp` name `adb devices` shows (also as `ADB_SERIAL` for `debug/run.sh`).
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

## Test pins (built 2026-11-09, released in 1.2.0)

Install over the current Dev build (don't uninstall first): the database upgrades from version 4 to 5.

- [x] Install over the current build: old fog still there.
- [x] Walk, drop a pin with a label and a note: it appears where you stood, linked to the walk (shows in its walk log row).
- [x] Stand still for more than 30 s while wandering, then drop a pin: the sheet opens (no "Looking for you in the mist…").
- [x] Two pins on the same spot: tapping opens the newest (the one on top).
- [x] Long-press on cleared ground: sheet opens, pin lands there. Long-press in fog: "Walk here first to leave a pin".
- [x] Tap a pin: read, edit label and note, delete.
- [x] Zoom out past 14: pins turn into dots; past 10: gone.
- [x] Postcard of the walk shows its pins; the walk log row shows the icons.
- [x] "Your places": groups and counts right; tapping a row flies to the pin.
- [x] Export, wipe the app's data, import: pins come back. Import again: no doubles.

Checked on the phone 2026-11-09 (over USB, indoors). Fixed the same day: the "Show my location" button had the same icon as "Drop a pin" (now a crosshair), the pin sheets were see-through, the keyboard hid "Place pin", long notes in "Your places" were cut off without "…", and the backup messages didn't count pins. Indoors the GPS is only good to 40-47 m, over the 35 m limit, so dropping a pin there says "Looking for you in the mist…": test the walk items outside. The walk items passed outside the same day.

## Test the UI review fixes (built 2026-11-09, released in 1.3.0)

Seen on the phone already: stats, About and wanderer pages stop at the status bar; bar-chart stats button; walks under 50 m without pins left out of the log; 5 walks with "Show all"; a "More" section for backup, About and feedback; no emoji; serif percentages; "Plenty left to explore." under Earth.

- [ ] After a walk of 50 m or more: a tip under the title says to long-press cleared ground for a pin. Tap closes it for good; so does placing a pin with a long-press.
- [ ] In a bus or car: the "Too fast for the mist" message shows under the title, not over the round buttons.
- [ ] Fresh install (no trail yet): "Tap Start wandering and go outside…" shows under the title, and goes away once you've walked.

## Publishing (own F-Droid-style repo, by Potloodgum)

Decided 2026-11-08: publish in Tjerk's own F-Droid-style repo, not the Play Store. In the app, an About screen (stats page → "Made by Potloodgum") says who made it and why, links to potloodgum.com, and has a "Buy me a coffee" button (buymeacoffee.com/tjerk). The text is in `res/values/strings.xml` (`about_*`).

Before the first public release:
- [x] **App id:** `com.potloodgum.mistysteps` (changed 2026-11-08, before any release). Never change it again. The code package (`namespace`) stays `com.mistysteps.app`; users don't see it. The old `com.mistysteps.app` install on Tjerk's phone had its data copied over.
- [x] **Release signing key** (made 2026-11-08): `~/Keystores/potloodgum/mistysteps-release.jks`, alias `mistysteps`, RSA 4096, valid until ~2054. The password is in `keystore.properties` at the project root (not in git). `./gradlew assembleRelease` signs with it automatically. Certificate SHA-256: `7c:f2:c4:77:f3:f7:31:e8:21:02:17:f1:81:38:cf:f1:fb:96:31:72:5d:47:ad:97:5e:2c:a8:9b:b4:0b:65:15`.
- [ ] **Back up the key:** copy `mistysteps-release.jks` and the password (from `keystore.properties`) to a password manager or a second disk. If you lose either one, you can never publish an update to people who installed the app.
- [ ] Debug builds (what goes on the phone during development) are signed with a different key, so switching that phone to the release APK means uninstalling first. Copy the data over with run-as first, like the move from the old app id.
- [ ] **Version numbers.** Raise `versionCode` (and `versionName`) in `app/build.gradle.kts` for every release.
- [ ] **Release build.** `./gradlew assembleRelease`. The battery logging is debug-only, so it's not in the release build.
- [x] **Store texts** (2026-11-08): `fastlane/metadata/android/en-US/`: title, short and full description, `changelogs/1.txt`, icon (`tools/make_store_icon.py`) and 5 screenshots (map, stats, postcard, characters, About), taken with a clean demo status bar. Tjerk OK'd showing his neighbourhood and route in them.
- [x] **Repo** (live 2026-11-08): **https://fdroid.potloodgum.com/repo**, fingerprint `df66fa48438843de50b11a6fece8ad44c5793d6740134f5cb068b79887cab209`. A landing page at https://fdroid.potloodgum.com explains how to add it. The repo's own signing key is `~/Keystores/potloodgum/fdroid-repo.p12` (password in `fdroid/config.yml`, not in git). **Back it up together with the app key**; losing it means everyone has to re-add the repo. The listing (`fdroid/metadata/com.potloodgum.mistysteps.yml`) has Donate, WebSite, License and categories. Hosting: a static-assets Cloudflare Worker `potloodgum-fdroid` (config in `fdroid/site/`, deployed with the `cf` CLI) on the custom domain fdroid.potloodgum.com.
- [x] **License:** GPL-3.0 (`LICENSE`). The code is still in a private GitHub repo; making it public is a separate choice.

- [x] **Website** (2026-11-08): post at https://www.potloodgum.com/posts/misty-steps/ and a side project card and page at /websites/misty-steps/ (repo `~/Projects/pgum_11ty`, Eleventy; Vercel publishes on push to main). The post was written with Claude at Tjerk's request; he'll check it. The blog elsewhere says it's "A.I free".

### Releasing an update

Ask Claude to "release a new version" (the `/fdroid-release` skill in `.claude/skills/`), or do it by hand:

1. Write `fastlane/metadata/android/en-US/changelogs/<next versionCode>.txt` (for users, max 500 characters).
2. Commit everything else.
3. `tools/release.sh patch|minor|major --dry-run`: tests, lint, signed build, APK checks and repo build, without publishing.
4. `tools/release.sh patch|minor|major`: does it all for real, deploys, checks it's live, then commits, tags `v<version>` and pushes.

**Versions:** `version.properties` is the only place the version is set. The release script bumps it: `versionCode` +1 per release (never reused), `versionName` as semver. Git tags are `v1.0.1` and so on.

**Two apps on the phone:**
- **Misty Steps** (`com.potloodgum.mistysteps`): the release from F-Droid.
- **Misty Steps Dev** (`com.potloodgum.mistysteps.debug`, version `x.y.z-dev`): the local build from `./gradlew installDebug`. It has its own data and keeps the battery logging.

**Tests:** 28 unit tests plus lint run on every push (GitHub Actions, `.github/workflows/test.yml`) and again in every release.

**How it's doing:** `tools/stats.sh [days]` shows app downloads per version, phones checking the repo per day, and visits to the landing page. It reads Cloudflare's own request analytics for fdroid.potloodgum.com: path, user agent and status only, with no IP addresses. Nothing is added to the app or the site. Scripts and bots are left out. "At least N phones" counts different F-Droid/Droid-ify versions, so it's a lower bound.

### Map tiles (decided 2026-11-09)

The map uses OpenStreetMap's public tile servers (`TileSourceFactory.MAPNIK` in `ui/MapController.kt` and `postcard/PostcardRenderer.kt`). Their [tile usage policy](https://operations.osmfoundation.org/policies/tiles/) has no fixed limit; OSM blocks apps that behave badly or put a big load on the servers, by User-Agent, so a block hits every install at once. The app follows the rules: its own User-Agent (`com.potloodgum.mistysteps/<version>`, `MistyApplication.kt`), osmdroid's tile cache on the phone, no bulk downloading, visible attribution. Up to a few hundred users is no problem. Nominatim and Overpass count per IP address, so they don't get busier as users are added.

**Decision: switch to a paid tile provider when the app grows**: past ~1,000 downloads (`tools/stats.sh`), or before a big post (Hacker News, a large subreddit). MapTiler, Stadia or Thunderforest: free tiers cover thousands of users, then about $20–50 a month. The work is about an hour: a new tile source with the provider's URL and API key in both files above. The repo is public, so keep the key out of git (in `keystore.properties` or `local.properties`, passed in as a `BuildConfig` field). The key can still be read from the APK, so set a usage cap at the provider. Change the attribution to the provider's text. The long-term goal is offline region maps (see Roadmap, "Later"), which removes the need for any tile server.

Looked at and not chosen:
- **Own raster tile server** (PostGIS, OSM-Carto, renderd): the whole planet needs ~1 TB NVMe and 64 GB+ RAM, about €60–150 a month, plus daily updates and upkeep.
- **Protomaps (PMTiles) on Cloudflare R2**: cheap to host (~$2/month), but vector tiles need MapLibre Native instead of osmdroid, which means rewriting the fog, trail, pin and postcard rendering. Only worth it as its own project, if a custom map style or offline maps are wanted.

### Releases

- **1.3.1** (versionCode 5, 2026-11-10): hotfix: no more Nominatim or Overpass requests (users warned the app was out of step with their usage policies). City and country come from an index of Dutch towns shipped in the app; neighbourhood and street stats only from data earlier versions saved. New store screenshots made on an emulator with demo walks in Amsterdam, so they no longer show home.
- **1.4.0** (versionCode 7, 2026-11-18): the last F-Droid release. Offline province maps, a new MapLibre map, first-run province picker, consent-based open count, character names, English and Dutch. The F-Droid APK is arm64-only (the Worker allows 25 MiB per file; four CPU types made it 51 MiB). Releases move to the Play Store and the App Store (TestFlight first); see the free-forever decision in the Roadmap.
- **1.4.1** (versionCode 8, 2026-11-20): fix for the app feeling frozen on a Samsung Galaxy S21 (Android 15) while a map downloads: the map reloaded its style on every download progress tick (about 4 times a second); it now reloads only when a pack is installed or removed. Also ships the download progress card (speed, time left). Not reproduced on a real S21; if reports continue on an idle map, look at the fog drawing (`fog/FogPainter.kt`).
- **1.4.2** (versionCode 9, 2026-11-21): the mist adapts to slow phones: `FogGovernor` watches frame times (and battery saver) and steps the mist down (two swirls, one swirl drifting slowly, a still veil), then back up after a long calm stretch. Aimed at the S21 / battery saver "buttons unresponsive" reports. Emulator tests showed only a small gain (about 15% at 20% CPU speed), so not expected to be the whole cause; the S21 report is still open.
- **1.4.3** (versionCode 10, 2026-11-21): the real fix for the Galaxy S21 "buttons unresponsive" report. `blockTouchesBelow()` consumed every pointer event in the Final pass, which makes Compose cancel any click or scroll inside a full-screen page when the finger sends small move events (a real S21 panel does; `adb input tap` does not, so it never showed in tests). Buttons, list rows and scrolling on the first-run questions, picker, stats, characters and about pages were dead for real touches. Found by recording raw touches on a real S21 (`getevent`), reproduced on the emulator with `adb shell input swipe x y x+4 y+1 150`, confirmed fixed on the phone. Also: installed map packs are cached in memory instead of listing folders on every `state()` call (about 46 ms per download progress tick on the S21 before).
- **1.5.0** (versionCode 11, 2026-11-22): neighbourhoods and streets offline for everyone: the stats page shows your wijk percentage, "x of y streets walked" and "Still in the mist" again, read from a small `stats.zip` that comes with each province's map (all 12 published to R2 the same day; installed maps get theirs quietly on an unmetered connection; the picker shows the combined size; "Your maps" says when streets are ready). The paid magic eraser ships in the code but the F-Droid build has no store, so no eraser button and no billing code there; it is for the Play build (`-Pstore=play`).
- **1.3.2** (versionCode 6, 2026-11-13): smoother map panning: the fog mask is built on a worker thread instead of the main thread (janky frames in a Pixel pan test 3.8-6.7% -> ~1%). Aimed at the "scrolling and buttons sometimes stop responding" reports; not confirmed to be their cause.
- **1.3.0** (versionCode 4, 2026-11-09): UI review fixes: pages stop at the status bar, map messages under the title, pin tip and welcome message, shorter walk log without tiny walks, "More" section, chart icon, version on the stats page, no emoji.
- **1.2.0** (versionCode 3, 2026-11-09): pins: drop them on a walk or long-press cleared ground, with a label, pixel icon and note; shown in the walk log, on postcards and under "Your places"; included in backups.
- **1.1.0** (versionCode 2, 2026-11-08): export and import of trails, "Send feedback" button. Tjerk moved to this F-Droid install using export/import; it works. The F-Droid listing now links the source and issues (the repo went public the same day).
- **1.0.0** (versionCode 1, 2026-11-08): first release. (Published as "1.0" before the version system existed.)

## Magic eraser (built 2026-11-22 on branch `magic-eraser`, not merged or released)

A paid hand tool for completionists: a round button on the map picks up an eraser, one finger wipes the mist anywhere (zoom 14 or closer, brush about 28 dp and at most 100 m), two fingers still pan and zoom, the last 10 strokes can be undone, and putting it down makes them final. EUR 3,99 once, through Google Play Billing; the F-Droid build has no button. Design: `docs/superpowers/specs/2026-11-21-magic-eraser-design.md`, plan: `docs/superpowers/plans/2026-11-21-magic-eraser.md`.

- **Storage:** erased mist is ordinary `points` rows with `erase_stroke` set (database version 6). The trail line, walked distance, walk log and postcards ignore them; the fog and the cleared-area and percentage stats count them. Backups carry an optional sixth value per point.
- **Code:** rules, spot spacing, undo list, access states and the taste counter are in `shared/.../eraser/` (reused by the iPhone version later); the touch handling is `app/.../eraser/EraserController.kt`; the Play code is in `app/src/play` and only joins the build with `-Pstore=play` (the default build uses `app/src/fdroid` and carries no billing code).
- **Testing without Play:** debug builds honour `adb shell settings put global misty_eraser 0|1|2` (0 unlocked, 1 locked, 2 a fresh 100 m free taste). Test real-finger taps with `adb shell input swipe x y x+3 y+1 150`.
- **iPhone (built 2026-11-22):** same engine (moved to `:shared`), same rules, StoreKit 2 Non-Consumable product `magic_eraser` (EUR 3,99), local test file `iosApp/MistySteps/Misty.storekit` (used when running from Xcode, not shipped). Simulator test hooks: launch arguments `-eraser 0|1|2`, `-eraserdemo 1|2` (draw, and draw then undo), `-erasersheet 1`. Checked in the simulator: stroke drawn and saved (SQLite), undo deletes exactly that stroke, an old database gets the `erase_stroke` column with its rows kept. Not checked: a real finger (drag, second finger, Undo button) and a real purchase: both need TestFlight and the in-app purchase in App Store Connect recreated as Non-Consumable (the draft `eraser` is a Consumable; the type cannot be changed).
- **Still to do before release:** create the Play listing and the in-app product `magic_eraser` (EUR 3,99, one-time, non-consumable), run a purchase test on the internal testing track, update the Play data-safety form, review the Dutch and English copy in `shared/strings`, and build with `./gradlew bundleRelease -Pstore=play`. Not yet tried on the Galaxy S21 with a real finger. iPhone version comes later (the iPhone store passes `eraseStroke: nil` for now).

## Streets and neighbourhoods offline (built 2026-11-22 on branch `streets-stats`, not merged or published)

The neighbourhood percentage and "x of y streets walked" with "Still in the mist" come back for everyone, with no network request: each province's map now has a `stats.zip` next to it. Design: `docs/superpowers/specs/2026-11-22-streets-and-neighbourhoods-design.md`, plan next to it under `plans/`.

- **Build and publish (one command, your side):** `uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt python tools/maps/publish.py --year 2026 --data-date 2026-11-10 --pbf tools/maps/data/netherlands-latest.osm.pbf` builds any missing `out/<id>.stats.zip` from the dump, uploads it with the pack, checks both with HEAD requests and writes the catalog last (the catalog then lists `stats`). To build only the files: `python tools/maps/stats_build.py --pbf ... --provinces fr --data-date 2026-11-10`. Friesland takes about 2 minutes and gives 0.3 MB (0.5% of the map; 9 towns with 121 neighbourhoods and 120 street files; the biggest has 187 streets). Towns whose neighbourhoods are not tagged in OpenStreetMap simply have no neighbourhood card.
- **File format 1:** described at the top of `tools/maps/stats_build.py` (index, one hoods file per town, one streets file per neighbourhood, delta-encoded 1e-5 degree). The app reads single entries (`ZipStatsPack`, `StatsPacks`), skips damaged or newer files, and still shows what older versions saved when there is no stats file.
- **People who already have a map** get the stats file quietly on an unmetered connection (`StatsDownloadWorker`, unique work `stats-<id>`) when the installed map is of the catalog's year; new downloads include it and the picker shows the combined size. "Your maps" shows "Streets and neighbourhoods are ready".
- **Checked:** unit tests (builder, format reader, zip reading, pack handling, fetch rule); a real Friesland file on the emulator showed the Leeuwarden neighbourhood card with its streets; a garbage file gave no card and no crash; reading the biggest real town and street entries takes 30 to 40 ms and 7 to 12 ms on the laptop JVM.
- **Not yet checked:** the quiet download end to end (needs the files published to R2, which needs your rclone credentials; the worker is only built and reviewed), and a real phone.

## Roadmap (chosen 2026-11-08)

The current priorities (Must, Should, Could, Won't) are in `docs/moscow.md`; the lists below are the older, detailed notes.

### Built (2026-11-08)

New ground per walk, no clearing at vehicle speed, walk postcard, neighbourhood stats, street completion, fog through the day. See "What the app does" for how each one works. The plans below are kept for reference.

1. **New ground per walk.** Each walk in the log shows how much new area it cleared, separate from ground already walked ("1.8 km · 0.6 ha new mist"). At walk end, count the walk's ~10 m cells (`stats/ClearedCells.kt`) that weren't cleared before it started. Store it as `new_area_m2` on the `sessions` row.
2. **No clearing in motor vehicles.** Built with a speed rule (32 km/h on, 25 km/h off). Bikes, skates, steps and wheelchairs keep clearing (changed 2026-11-08: not everyone can walk).
3. **Walk postcard.** After you stop: a pixel-style card with the walk's trail on the map, distance, duration, new ground and your character. Save it to the photo gallery or share it via the Android share sheet. Render it offscreen to a bitmap (map snapshot plus trail plus text). Show it automatically on stop, and from the walk log.
4. **Neighbourhood stats.** A percentage for your neighbourhood (OSM admin level 10, "buurt"/"wijk") on the stats page, above the city. Uses the same Nominatim lookup and cache as the city and country borders. Some places have no level 10; fall back to level 9 or hide the card.
5. **Street completion.** "41 of 120 streets walked in the Jordaan." Fetch the neighbourhood's streets once (Overpass, by area id, so no position is sent) and cache them. A street counts as walked when most of its length is within ~25 m of your trail. List streets you haven't walked yet as suggestions.
6. **Fog that follows the time of day.** Tint the fog from the phone's clock and the sun position: rosy at sunrise and sunset, white by day, blue-grey at night. Calculate sunrise from the last known position, with no network. Only `basePaint` and the swirl tints in `FogOverlay.kt` change, and the system bar icons must stay readable.

### Later

- **Things hidden in the fog.** Windmills, viewpoints, churches, benches and bridges from OSM stay hidden until you clear their spot, then go into a "Discoveries" collection. Needs an area-based Overpass lookup and a collection screen. **Parked 2026-11-22 ("in the fridge", a Maybe/Could):** the owner chose to wait. Agreed so far: call it "things you passed" (windmills, benches, bridges you walked past, no quests, goals or counters). It would reuse the neighbourhoods-and-streets pipeline (`tools/maps/stats_build.py`: points of interest from the same OpenStreetMap dump, shipped in each province's `stats.zip`). Open: where you see it (a map icon, a calm list on the stats page, or a line after a walk) and how it should feel; nothing designed or built.
- **Home screen widget.** Your character, your last walk and your neighbourhood %.
- **GPX export.** Export walks as GPX files through the Android share sheet or file picker: a backup without accounts.
- **GPX import (Maybe, parked 2026-11-22).** Bring past walks from Strava, Komoot or a watch and clear the mist with them. Considered earlier and set aside by the owner: it works against the point of walking, and the magic eraser covers gaps. Kept as a Maybe in case people ask. If built: a file picker, no server, imported walks would be marked like erased ground so the stats stay honest, and it needs a design first.
- **Tell buses from bikes (without Google Play services).** Speed alone lets a slow city bus clear fog. No Google: use the phone's own motion sensor. Pedalling, walking and pushing a wheelchair make a rhythmic 1–2 Hz movement; riding in a bus or car is smooth with bumps and engine hum. Plan: (1) log accelerometer features during test rides in the debug build (`debug/` runs: bike, bus, car, train), (2) tune a simple rule from that data, (3) pause clearing when it says "vehicle" at any speed. Sensor batching keeps the battery cost low. Cheap first step: also treat short bursts above ~40 km/h as a vehicle, which catches buses on through roads.
- **Offline region maps (a 2.0 goal, idea 2026-11-09).** Like Organic Maps: users download their country's map once and the phone draws it, so there are no tile requests at all (not even OSM sees where people scroll), it works offline, and there are no tile limits or bills. This replaces the paid-provider plan in "Map tiles" for good. Plan: (1) cut region files (Netherlands, Germany, ...) from a Protomaps planet build with `pmtiles extract`; a country is a few hundred MB to a few GB, depending on size and top zoom. (2) Host them on Cloudflare R2, which charges nothing for downloads, so many users cost about the same as one. (3) A "Download a map" screen in the app. (4) Draw the map with MapLibre Native in our own style (check first that it reads PMTiles files directly). (5) Ship a small low-zoom world map in the app so the world view and fog specks work everywhere. The big job is moving off osmdroid: the fog, trail, pin and postcard rendering all have to be rewritten for MapLibre. Also needed: a job to rebuild the regions now and then.
- **Unlocks.** New characters or hats at distance milestones, finished streets or neighbourhood percentages. Builds on the character select.
- **iOS release in the EU (looked into 2026-11-09, port built 2026-11-17).** Ship on an EU alternative app marketplace such as AltStore PAL, which works much like the F-Droid repo: a self-hosted source plus a build Apple has notarized. Needs the paid Apple Developer account (EUR 99/yr), Apple's EU alternative distribution terms, Xcode, and notarization of every build. Since 2026-10-01 Apple charges 5% on digital sales only, so nothing for a free app. Only users with an EU account who are in the EU can install it. Re-check Apple's terms before notarizing: they change often. **State:** the port exists on branch `kmp-shared` (Kotlin Multiplatform `:shared` module, shared Compose screens, an iOS app in `iosApp/`) and runs in the simulator with the whole core experience. Background recording with the screen locked is built (2026-11-21, TestFlight build 2611212202) but untested on a real walk; open: battery on a real iPhone, signing and notarization, the AltStore source. See docs/kmp-ios-review.md for the review guide, the Pixel checklist and the release decisions.
- **Paid provinces (idea 2026-11-17).** One province free, more provinces unlocked by paying. Prices floated so far, not decided: EUR 2.99 per province and an "all of the Netherlands" bundle at EUR 6.99; sell provinces only, not single towns; say clearly that the first one stays free for good. An older todo in Todododo still says "EUR 2.99 paid once", so settle which model it is. Questions to answer before building: (1) **how people pay.** Google Play Billing is not allowed in the F-Droid repo, and this app ships through the own F-Droid repo, so it needs its own payment (for example a payment link and a licence code) and that has to fit the "no accounts" promise; on iOS in the EU, Apple's alternative terms allow your own payment processor (5% commission on digital sales, check the current terms). (2) **where it is enforced.** The catalog and packs on maps.potloodgum.com are public, so a paywall that only hides a button is easy to get around; real enforcement means the host checks a licence before serving a paid pack. (3) **what "free province" means** (the first one picked? the one you are in?), what happens to existing users who already downloaded several (keep them free), and the Dutch and English wording on the picker. (4) **the open licence**: if the code is open source, anyone can build without the check; decide if that is acceptable. (5) fees and VAT for a one-person studio.

## Fun extras (ideas only, not built)

### StreetPass mode

When two people with Misty Steps walk past each other, their characters meet. Later you find a little visitor in your fog: "A Pixel Fox passed you on Nieuwestad, 14:32".

- **How:** while recording, the phone sends out a short Bluetooth LE signal with a random ID that changes every 15 minutes, plus the chosen character. Phones that hear each other within ~10 m save a "pass". Android's Nearby Connections API is an alternative.
- **What you'd see:** the visitor's character stands where you met, in a small cleared spot. A "Passes" list on the stats page, and maybe a small bonus clearing for both.
- **Privacy:** opt-in only. No accounts and no server: only the random ID and the character are exchanged, never names or trails. Needs the Bluetooth "Nearby devices" permission, and more battery while on (measure it with the `debug/` runs).
- **Name:** "StreetPass" is Nintendo's trademark. It's fine as a working title, but pick our own name before release (e.g. "Mist Meetings" or "Passing Wanderers").

## Considered and dropped

- **Polarsteps import (2026-11-08):** doesn't fit. Misty Steps is about walking, and imported trips (sparse points, cars, trains, flights) would clear fog you didn't walk.
