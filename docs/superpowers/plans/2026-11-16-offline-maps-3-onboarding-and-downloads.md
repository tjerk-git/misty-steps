# Offline maps, plan 3: catalog, downloads, onboarding and province picker

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new install gets a short onboarding and a province picker; existing users get the picker once. Chosen provinces download in the background from `maps.potloodgum.com`, resume after a cut-off, and are listed under "Your maps". The map screen still draws with osmdroid until plan 2, so a downloaded pack is not shown yet.

**Architecture:** Pure Kotlin classes (catalog parsing, province lookup, pack installer with resume, picker and first-run rules) carry the logic and have JVM tests. A WorkManager job runs the installer. Compose screens (onboarding, picker, offer card, "Your maps") sit on top and are checked on the Pixel. The server side publishes the real 12 province files and the catalog first.

**Tech Stack:** Kotlin, Jetpack Compose, WorkManager (`androidx.work:work-runtime-ktx`), `HttpURLConnection` with `Range`; Python and rclone for publishing.

**Spec:** `docs/superpowers/specs/2026-11-16-offline-maps-provinces-design.md` (sections "What the user gets" and "Downloads and state"); probe results in `2026-11-16-offline-maps-probe-results.md`.

## Global Constraints

- `minSdk` 26. Release and debug builds both get this feature. No new network host: only `https://maps.potloodgum.com`. The app contacts no OpenStreetMap-run service.
- Catalog URL `https://maps.potloodgum.com/v2/catalog.json`; files under `v2/nl/<year>/<id>/`. Province ids `dr fl fr ge gr li nb nh ov ut ze zh`.
- Onboarding is three screens, each skippable (Skip link, Next button); then the picker. Shown once (`onboarding_done` in the `misty` preferences). Existing users (any stored trail points) skip the onboarding and see the picker once (`picker_seen`).
- Picker: alphabetical, check box and size per province, nothing pre-selected for new installs, no location request. For existing users the province with most of their trail points is pre-selected.
- Mobile data warning: on a metered connection a download of 50 MB or more first asks "<size>. You're on mobile data. Download anyway?" with Download and Wait for Wi-Fi.
- Offer card: "No map of <province> yet. Download (<size>)" with Not now, once per province, remembered.
- Resume with HTTP `Range`; check sha256; unzip or rename into place only after the check; a half-finished pack never counts as installed. Check free space (size times 1.2) before starting.
- Storage: `filesDir/maps/nl/<year>/<id>/map.pmtiles`.
- All copy in English and Dutch, using the strings listed under Task 8. No emoji.
- Cloudflare work uses the `cf` CLI; uploads use the `misty-r2` rclone remote (bucket `misty-maps`). Never put keys in the repo.

## Review Focus

- A download cut at the very end, or with an empty `.part` file, still finishes correctly (Task 4).
- The server answers a resume with `200` instead of `206`: restart from zero, never append a full body to a partial one (Task 4).
- Tapping Download twice, or rotating the phone, or the app being killed during onboarding: one job per province, and the screens keep their state (Tasks 5 and 6).
- A newer year in the catalog while an older pack is installed: Update is offered, the old pack stays usable until the new one is verified and swapped in (Task 4).
- The phone is in the sea, outside the Netherlands or has no fix: no offer card and no crash (Task 7).
- An existing user with no trail points gets the plain picker with nothing ticked (Task 2).
- No internet at first launch: the picker shows "Go online once to see the maps." and Skip works (Task 6).

## File Structure

| File | Responsibility |
|---|---|
| `tools/maps/publish.py` | sha256, catalog JSON, upload of the 12 provinces to R2 |
| `app/src/main/java/com/mistysteps/app/maps/ProvinceIndex.kt` | province borders, `provinceAt`, `mostWalked` |
| `.../maps/Catalog.kt` | catalog model and parser, cached fetch |
| `.../maps/PackInstaller.kt` | resumable download, sha256, swap into place |
| `.../maps/ProvincePacks.kt` | per-province state, storage layout, remove, versions |
| `.../maps/PackDownloadWorker.kt` | WorkManager job, foreground notification |
| `.../maps/DownloadPolicy.kt` | metered-data and free-space decisions |
| `.../maps/FirstRun.kt`, `PickerState.kt`, `OfferRules.kt` | pure screen-decision logic |
| `.../ui/OnboardingScreen.kt`, `ProvincePickerScreen.kt`, `MapsCards.kt` | Compose screens and cards |
| `app/src/main/res/values/strings.xml`, `values-nl/strings.xml` | copy |

---

### Task 1: Publish the 12 province files and the catalog

**Files:**
- Create: `tools/maps/publish.py`, `tools/maps/tests/test_publish.py`

**Interfaces:**
- Produces: `sha256_of(path: str) -> str`; `catalog_json(year: int, data_date: str, provinces: list[dict]) -> dict` where each province dict is `{"id", "name", "bbox": [w, s, e, n], "map": {"path": str, "url": str}}` and the result has `format: 2`, `country: "nl"`, and per province `map: {url, bytes, sha256}` read from the file at `path` (no `stats` key yet; plan 4 adds it); CLI `python3 tools/maps/publish.py --year 2026 --data-date 2026-11-10 [--dry-run]`.

- [ ] **Step 1: Write failing tests** in `test_publish.py`: `test_sha256_matches_hashlib` (temp file); `test_catalog_has_format_2_and_nl`; `test_catalog_entry_has_url_bytes_and_sha256` (bytes equals the file size, sha256 equals `sha256_of`); `test_catalog_has_no_stats_key_yet`; `test_provinces_sorted_by_id`.
- [ ] **Step 2: Run** `uv run --no-project --python 3.13 --with-requirements tools/maps/requirements.txt python -m pytest tools/maps/tests/test_publish.py -q`. Expected: FAIL (module missing).
- [ ] **Step 3: Implement `publish.py`.** The CLI builds all 12 provinces with `build.py`'s `extract_cmd` (the country file `tools/maps/out/nl.pmtiles` and the region files exist already; run `provinces.py` again if `out/*.geojson` are missing), takes names from `app/src/main/assets/nl-provinces.json` and the bbox from each region's GeoJSON bounds, uploads each `map.pmtiles` to `misty-r2:misty-maps/v2/nl/<year>/<id>/map.pmtiles` with `--header-upload "Cache-Control: public, max-age=31536000, immutable"` and `--header-upload "Content-Type: application/octet-stream"` (`rclone copyto ... --s3-no-check-bucket`), checks each upload with `rclone check` or a `HEAD`, and uploads `v2/catalog.json` last with `Cache-Control: public, max-age=3600` and `Content-Type: application/json`. `--dry-run` prints sizes and the catalog without uploading.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Dry run**, then the real run: `python3 tools/maps/publish.py --year 2026 --data-date 2026-11-10`. Expected: 12 files uploaded, the largest under 150 MB; if any province is over, record it in the ledger and in the results doc rather than stopping. Then `curl -s https://maps.potloodgum.com/v2/catalog.json` shows 12 provinces and `curl -sI` on one pack shows `accept-ranges: bytes`.
- [ ] **Step 6: Commit** `git add tools/maps && git commit -m "Publish the province maps and catalog to R2"`.

---

### Task 2: Province index and first-run rules

**Files:**
- Create: `app/src/main/java/com/mistysteps/app/maps/ProvinceIndex.kt`, `.../maps/FirstRun.kt`, tests `ProvinceIndexTest.kt`, `FirstRunTest.kt` under `app/src/test/java/com/mistysteps/app/maps/`

**Interfaces:**
- Produces: `class Province(val id: String, val name: String, val boundary: Boundary)`; `class ProvinceIndex(val provinces: List<Province>) { fun provinceAt(lat: Double, lon: Double): Province?; fun mostWalked(points: List<TrailPoint>): Province?; companion object { fun load(context: Context, asset: String = "nl-provinces.json"): ProvinceIndex; fun parse(text: String): ProvinceIndex } }` (`parse` rejects `format` above 1 with `IllegalArgumentException`); `enum class FirstRunScreen { ONBOARDING_THEN_PICKER, PICKER, NONE }`; `object FirstRun { fun decide(onboardingDone: Boolean, pickerSeen: Boolean, hasTrailPoints: Boolean): FirstRunScreen }`.
- Consumes: `Boundary(name, polygons, osmType, osmId)` and `Boundary.contains(lat, lon)` from `stats/Boundary.kt`; `TrailPoint(lat, lon, ...)` from `data/TrailDatabase.kt`.

- [ ] **Step 1: Write failing tests.** `ProvinceIndexTest` with a small inline JSON of two square provinces (`fr`, `gr`) sharing an edge: `provinceAt` inside each; a point in neither gives null; a point on the shared edge gives one of the two without throwing; `mostWalked` of 7 points in `fr` and 3 in `gr` gives `fr`; of no points gives null; of points outside both gives null; `parse` of `{"format": 2, ...}` throws. `FirstRunTest`: `decide(false, false, false) == ONBOARDING_THEN_PICKER`; `decide(false, false, true) == PICKER` (existing user); `decide(true, false, false) == PICKER` (onboarding done, picker not); `decide(true, true, true) == NONE`; `decide(false, true, false) == NONE`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.mistysteps.app.maps.*'`. Expected: FAIL (classes missing).
- [ ] **Step 3: Implement** both files. `ProvinceIndex.parse` reads the layout of `assets/nl-provinces.json` (`{"format": 1, "provinces": [{"id", "name", "polygons"}]}`, polygons as flat-ring lists, the same as `CityIndex`'s private `polygons`; copy that small parser rather than making it public).
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the province index and first-run rules"`.

---

### Task 3: Catalog

**Files:**
- Create: `.../maps/Catalog.kt`, test `CatalogTest.kt`

**Interfaces:**
- Produces: `data class FileRef(val url: String, val bytes: Long, val sha256: String)`; `data class PackEntry(val id: String, val name: String, val year: Int, val bbox: List<Double>, val map: FileRef, val stats: FileRef?)`; `class Catalog(val year: Int, val dataDate: String, val provinces: List<PackEntry>) { fun entry(id: String): PackEntry? ; companion object { const val URL = "https://maps.potloodgum.com/v2/catalog.json"; const val BASE = "https://maps.potloodgum.com/"; fun parse(text: String): Catalog } }`; `class CatalogFormatException : Exception`; `class CatalogStore(context: Context) { fun cached(): Catalog?; suspend fun refresh(force: Boolean = false): Catalog? }` (fetches at most once a week unless forced; returns the cached copy on any network error; writes `filesDir/maps/catalog.json` through a temp file).
- Relative URLs in the catalog are resolved against `Catalog.BASE`.

- [ ] **Step 1: Write failing tests** (`CatalogTest`): parses the spec's example (one province, with `stats`); `stats` is optional (null when absent); URLs become absolute; `format` 3 throws `CatalogFormatException`; a province with a negative size or a sha256 that is not 64 hex characters is rejected with `IllegalArgumentException`; `entry("zz")` is null.
- [ ] **Step 2: Run the tests.** Expected: FAIL.
- [ ] **Step 3: Implement `Catalog.parse`** and `CatalogStore` (HttpURLConnection, 15 s timeouts, User-Agent `<applicationId>/<versionName>`). `CatalogStore.refresh` is not unit tested; it is exercised on the phone in Task 9.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the map catalog"`.

---

### Task 4: Resumable pack installer and per-province state

**Files:**
- Create: `.../maps/PackInstaller.kt`, `.../maps/ProvincePacks.kt`, tests `PackInstallerTest.kt`, `ProvincePacksTest.kt`

**Interfaces:**
- Produces:
  - `interface Downloader { fun open(url: String, from: Long): Response }` with `class Response(val status: Int, val contentLength: Long, val body: InputStream)` (status 200 or 206; the real implementation uses `HttpURLConnection` and sends `Range: bytes=<from>-` when `from > 0`).
  - `class PackInstaller(private val downloader: Downloader) { fun install(ref: FileRef, dest: File, onProgress: (Long) -> Unit = {}): InstallResult }` where `sealed class InstallResult { object Done; class Failed(val reason: Reason) }` and `enum class Reason { NETWORK, CHECKSUM, NO_SPACE }`. It writes `dest.path + ".part"`, resumes from its length, checks the sha256, then renames to `dest`.
  - `sealed class PackState { object NotDownloaded; class Downloading(val bytes: Long, val total: Long); class Ready(val year: Int); class UpdateAvailable(val installedYear: Int, val newYear: Int); class Failed(val reason: Reason) }`.
  - `class ProvincePacks(private val root: File) { val version: StateFlow<Int>; fun installedYear(id: String): Int?; fun mapFile(id: String): File?; fun packDir(year: Int, id: String): File; fun state(entry: PackEntry): PackState; fun remove(id: String); fun removeOlderThan(year: Int, id: String); fun markDownloading(id: String, bytes: Long, total: Long); fun markFailed(id: String, reason: Reason); fun clearTransient(id: String) }`. `root` is `filesDir/maps/nl`; layout `<year>/<id>/map.pmtiles`.
- Consumes: `FileRef`, `PackEntry` from Task 3.

- [ ] **Step 1: Write failing tests** for `PackInstaller` with a fake `Downloader` serving a 100,000-byte body:
  - `freshDownloadWritesFileAndChecksOut`: file equals the body, no `.part` left.
  - `resumeContinuesFromPartLength`: a 40,000-byte `.part` exists; the downloader sees `from == 40000`, answers 206; result file equals the body.
  - `serverIgnoringRangeRestartsFromZero`: with a `.part`, the fake answers 200 with the full body; the file equals the body (no duplicated bytes).
  - `cutAtTheEndStillFinishes`: `.part` is already the full length; the fake answers 206 with an empty body (or 416); result Done after the checksum passes.
  - `emptyPartFileIsTreatedAsFresh`.
  - `wrongChecksumDeletesPartAndFails`: `Failed(CHECKSUM)`, no `.part`, no `dest`.
  - `connectionCutKeepsPartAndFails`: the body throws `IOException` after 30,000 bytes; `Failed(NETWORK)` and the `.part` is 30,000 bytes long.
  - `progressReportsBytesSoFar`.
  And for `ProvincePacks` (temp dir): `state` is `NotDownloaded` with nothing installed; `Ready(2026)` after creating `2026/fr/map.pmtiles`; `UpdateAvailable(2026, 2027)` when the catalog entry says 2027; `markDownloading` shows `Downloading`; `remove("fr")` deletes the folder and bumps `version`; `removeOlderThan(2027, "fr")` removes the 2026 folder only after `2027/fr/map.pmtiles` exists.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.mistysteps.app.maps.PackInstallerTest' --tests 'com.mistysteps.app.maps.ProvincePacksTest'`. Expected: FAIL.
- [ ] **Step 3: Implement both classes** plus `HttpDownloader : Downloader` (15 s connect timeout, 30 s read timeout, User-Agent as in Task 3, follows redirects, treats any status other than 200 or 206 as `IOException`).
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add the resumable pack installer and pack state"`.

---

### Task 5: Download policy and the WorkManager job

**Files:**
- Modify: `app/build.gradle.kts` (add `implementation("androidx.work:work-runtime-ktx:2.10.0")`)
- Create: `.../maps/DownloadPolicy.kt`, `.../maps/PackDownloadWorker.kt`, test `DownloadPolicyTest.kt`

**Interfaces:**
- Produces: `object DownloadPolicy { const val WARN_BYTES = 50L * 1024 * 1024; fun needsDataWarning(metered: Boolean, bytes: Long): Boolean; fun hasSpaceFor(bytes: Long, freeBytes: Long): Boolean }` (space needs `bytes * 1.2` free); `object PackDownloads { fun enqueue(context: Context, entry: PackEntry, unmeteredOnly: Boolean); fun cancel(context: Context, id: String) }`; `class PackDownloadWorker(context, params) : CoroutineWorker` that reads `id` from its input data, loads the catalog entry from `CatalogStore.cached()`, installs the map (and the `stats` file if the entry has one) with `PackInstaller` into `ProvincePacks`, reports progress with `markDownloading`, calls `removeOlderThan` after a successful swap, and shows a foreground notification (channel "Map downloads") with a progress bar and the province name.
- Work is unique per province: `enqueueUniqueWork("pack-<id>", ExistingWorkPolicy.KEEP, ...)`; constraints `NetworkType.CONNECTED`, or `UNMETERED` when `unmeteredOnly`; `BackoffPolicy.EXPONENTIAL` from 30 s.

- [ ] **Step 1: Write failing tests** (`DownloadPolicyTest`): `needsDataWarning(true, 62 MB)` true; `(true, 49 MB)` false; `(false, 500 MB)` false; `(true, exactly 50 MB)` true; `hasSpaceFor(100, 120)` true; `hasSpaceFor(100, 119)` false.
- [ ] **Step 2: Run** the test class. Expected: FAIL.
- [ ] **Step 3: Implement `DownloadPolicy`**, then the worker and `PackDownloads`. In `doWork`: if free space fails the policy, `markFailed(NO_SPACE)` and return `Result.failure()`; a `NETWORK` failure returns `Result.retry()`; `CHECKSUM` returns `Result.failure()` after `markFailed`. The foreground type is `dataSync`: add `android.permission.FOREGROUND_SERVICE_DATA_SYNC` and `foregroundServiceType="dataSync"` for WorkManager's `SystemForegroundService` in the manifest.
- [ ] **Step 4: Run the tests and `./gradlew assembleDebug`.** Expected: PASS and a successful build.
- [ ] **Step 5: Commit** `git commit -m "Download province maps with WorkManager"`.

---

### Task 6: Picker, offer and card logic

**Files:**
- Create: `.../maps/PickerState.kt`, `.../maps/OfferRules.kt`, tests `PickerStateTest.kt`, `OfferRulesTest.kt`

**Interfaces:**
- Produces: `class PickerState(val options: List<PackEntry>, preselected: Set<String> = emptySet()) { val selected: Set<String>; val totalBytes: Long; fun toggle(id: String): PickerState; fun toDownload(packs: ProvincePacks): List<PackEntry> }` (immutable; `options` sorted by name; `toDownload` leaves out provinces already `Ready` or `Downloading`); `object OfferRules { fun shouldOffer(here: Province?, state: PackState?, dismissed: Set<String>): Boolean }` (true only for a known province that is `NotDownloaded` and not dismissed); `fun formatSize(bytes: Long): String` in `maps/Sizes.kt` ("62 MB", "1.2 GB", locale number format).
- Consumes: `Province` (Task 2), `PackEntry`, `PackState`.

- [ ] **Step 1: Write failing tests.** `PickerStateTest`: options sorted by name; nothing selected by default; `toggle` adds and removes; `totalBytes` sums the selected; `preselected` ids unknown to the catalog are dropped; `toDownload` skips an installed province. `OfferRulesTest`: null province false; `NotDownloaded` true; `Ready`, `Downloading` and `UpdateAvailable` false; dismissed false. `SizesTest`: `formatSize(62_000_000) == "62 MB"`, `formatSize(1_200_000_000)` reads "1.2 GB" (US locale), `formatSize(0) == "0 MB"`.
- [ ] **Step 2: Run** the tests. Expected: FAIL.
- [ ] **Step 3: Implement.** Sizes use decimal units (1 MB = 1,000,000 bytes), as the catalog's `bytes` do.
- [ ] **Step 4: Run the tests.** Expected: PASS.
- [ ] **Step 5: Commit** `git commit -m "Add picker and offer rules"`.

---

### Task 7: Onboarding, picker and cards (Compose)

**Files:**
- Create: `.../ui/OnboardingScreen.kt`, `.../ui/ProvincePickerScreen.kt`, `.../ui/MapsCards.kt`
- Modify: `.../ui/MistyScreen.kt` (host the screens and the offer card), `.../ui/StatsScreen.kt` ("Your maps" card), `.../MistyApplication.kt` (create `ProvincePacks` and `CatalogStore`, refresh the catalog on start when online)

**Interfaces:**
- Produces: `@Composable fun OnboardingScreen(onDone: () -> Unit, onSkip: () -> Unit)` (three pages with Skip and Next, the last page's button is "Choose provinces"); `@Composable fun ProvincePickerScreen(catalog: Catalog?, packs: ProvincePacks, preselected: Set<String>, existingUser: Boolean, onDone: () -> Unit)` (rows with check box, name and size; total; Download button; Skip for now; "Go online once to see the maps." when `catalog` is null; the mobile-data dialog; starts `PackDownloads.enqueue`); `@Composable fun OfferCard(province: Province, entry: PackEntry, onDownload: () -> Unit, onNotNow: () -> Unit)`; `@Composable fun YourMapsCard(catalog: Catalog?, packs: ProvincePacks, onAdd: () -> Unit)` (rows with state text, Update, Remove).
- Consumes: Tasks 2 to 6.
- State that must survive rotation and process death: the onboarding page, the picker's ticked ids (`rememberSaveable`), which screen is showing.

- [ ] **Step 1: Strings.** Add every string from Task 8's table to `values/` and `values-nl/`.
- [ ] **Step 2: Implement the screens** in the app's look (`Frost` cards, `Ink` text, `Ember` accents, Serif titles; same page chrome as `StatsScreen` and `CharacterScreen`, including `blockTouchesBelow()` and `BackHandler`). Onboarding pages use the three titles and bodies from Task 8, with a simple pixel-art illustration drawn from the existing sprites (the selected character plus a mist patch); no new image assets.
- [ ] **Step 3: Wire up `MistyScreen`.** On first composition read `onboarding_done` and `picker_seen` from the `misty` preferences and `FogStore.loadAllPoints().isNotEmpty()` (on `Dispatchers.IO`), call `FirstRun.decide`, and show the matching screen above the map. Existing users get `ProvinceIndex.mostWalked(points)` as `preselected` and the extra line (`picker_existing`). Finishing or skipping each screen sets its flag. The offer card shows when `OfferRules.shouldOffer` is true for `ProvinceIndex.provinceAt` of the current place (`FogStore.lastKnown`), the `Not now` ids kept in the `misty` preferences. `YourMapsCard` goes between the "Your places" and "More" blocks of the stats page; its "Add a province" opens the picker.
- [ ] **Step 4: Build** `./gradlew assembleDebug testDebugUnitTest`. Expected: success, all tests pass.
- [ ] **Step 5: Commit** `git commit -m "Add the onboarding, province picker and map cards"`.

---

### Task 8: Copy (English and Dutch)

Strings to add. Keys are the Android resource names; `%1$s` and friends keep their positions in both languages.

| Key | English | Dutch |
|---|---|---|
| `onboarding_skip` | Skip | Overslaan |
| `onboarding_next` | Next | Volgende |
| `onboarding_choose` | Choose provinces | Kies provincies |
| `onboarding_1_title` | Mist covers the map | De mist bedekt de kaart |
| `onboarding_1_body` | Misty Steps hides the Netherlands in mist. | Misty Steps verbergt Nederland in de mist. |
| `onboarding_2_title` | Your steps clear it | Jouw stappen ruimen hem op |
| `onboarding_2_body` | Tap Start wandering and walk, run or cycle. The mist lifts around you. | Tik op Start wandeling en loop, ren of fiets. De mist trekt op om je heen. |
| `onboarding_3_title` | Yours alone | Alleen van jou |
| `onboarding_3_body` | No account, no tracking. Your trails never leave your phone. | Geen account, geen tracking. Je routes verlaten je telefoon nooit. |
| `picker_title` | Which provinces do you want to explore? | Welke provincies wil je verkennen? |
| `picker_total` | Total: %1$s | Totaal: %1$s |
| `picker_download` (plurals: one/other) | Download %1$d province / Download %1$d provinces | Download %1$d provincie / Download %1$d provincies |
| `picker_skip` | Skip for now | Nu overslaan |
| `picker_later` | You can add more any time under Your maps. | Je kunt altijd meer toevoegen onder Je kaarten. |
| `picker_existing` | The online map is gone in this version. Pick the provinces you walk in to get your streets back. | De online kaart is in deze versie verdwenen. Kies de provincies waar je loopt om je straten terug te krijgen. |
| `picker_offline` | Go online once to see the maps. | Ga één keer online om de kaarten te zien. |
| `data_warning` | %1$s. You're on mobile data. Download anyway? | %1$s. Je gebruikt mobiele data. Toch downloaden? |
| `data_download` | Download | Download |
| `data_wait_wifi` | Wait for Wi-Fi | Wacht op wifi |
| `offer_card` | No map of %1$s yet. Download (%2$s) | Nog geen kaart van %1$s. Download (%2$s) |
| `maps_title` | Your maps | Je kaarten |
| `maps_add` | Add a province | Voeg een provincie toe |
| `maps_update` | Update | Werk bij |
| `maps_remove` | Remove | Verwijder |
| `maps_data_year` | Data from %1$d | Gegevens uit %1$d |
| `maps_downloading` | Downloading… %1$d%% | Downloaden… %1$d%% |
| `maps_failed` | Download failed. Try again. | Download mislukt. Probeer het opnieuw. |
| `maps_no_space` | Not enough space on this phone. | Niet genoeg ruimte op deze telefoon. |
| `maps_update_app` | Update the app to use new maps. | Werk de app bij om nieuwe kaarten te gebruiken. |
| `maps_downloads_channel` | Map downloads | Kaartdownloads |
| `maps_downloading_notif` | Downloading %1$s | %1$s wordt gedownload |

(`not_now` already exists.) This task has no separate test: the strings are added with Task 7, and the key check in Task 9 compares the two files.

---

### Task 9: Check it on the Pixel

**Files:** none (verification), plus the notes in `docs/superpowers/specs/2026-11-16-offline-maps-probe-results.md`.

- [ ] **Step 1: Key parity check.** Run a script (python) that compares the resource names in `values/strings.xml` and `values-nl/strings.xml`. Expected: both sets are equal.
- [ ] **Step 2: Fresh-launch flow without losing trail data.** Install the debug build, then reset only the first-run flags: `adb shell run-as com.potloodgum.mistysteps.debug sh -c 'rm -f shared_prefs/misty.xml'` and restart the app. Because the database holds trail points the phone counts as an existing user: expect the picker, with the province with most of the trail ticked (Friesland on this phone). Screenshot it in English and in Dutch (`adb shell cmd locale set-app-locales <app id> --user 0 --locales nl`, reset afterwards).
- [ ] **Step 3: New-install flow.** To see the onboarding without deleting the Dev app's data, add a debug-only intent extra to `MainActivity` (`--ez fresh true`) that makes `FirstRun.decide` run with `hasTrailPoints = false`, and show all three screens and the picker. The extra is guarded by `BuildConfig.DEBUG` and listed in the ledger.
- [ ] **Step 4: Download.** Tick Friesland and press Download on Wi-Fi; expect the foreground notification with progress and, at the end, `files/maps/nl/2026/fr/map.pmtiles` with the size from the catalog (`adb shell run-as ... ls -l files/maps/nl/2026/fr`). The "Your maps" card on the stats page shows Friesland, "Data from 2026", Remove.
- [ ] **Step 5: Resume.** Start a Zuid-Holland download, switch on airplane mode at about 40%, check that a `.part` file stays, switch it off and check that the download finishes and the sha256 of the final file equals the catalog's (`adb shell run-as ... sha256sum`). Repeat once with `adb shell am force-stop` during the download.
- [ ] **Step 6: Metered warning.** Turn off Wi-Fi so the phone is on mobile data; start a download of a 50 MB or larger province and check the dialog, then "Wait for Wi-Fi": expect no download until Wi-Fi returns.
- [ ] **Step 7: Offer card and Remove.** Remove Friesland from "Your maps" (expect the folder to be gone), restart the app inside Friesland (use the last known place) and expect the offer card; "Not now" hides it and it stays hidden after a restart.
- [ ] **Step 8: Record** results, anything that failed, and screenshots' names in the results doc, and commit `git commit -m "Record the onboarding and download checks"`.
