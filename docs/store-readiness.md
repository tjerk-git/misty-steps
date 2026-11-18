# Store readiness: TestFlight and Google Play testing

Status 2026-11-18. Both stores, Netherlands only, free, no ads, no tracking.

## What the app does with data (facts for every form below)

- Trail, pins, settings: stored only on the phone. Nothing is uploaded.
- Location: used on the phone to draw the map and clear the fog. Never sent anywhere.
- Map packs: downloaded from our R2 host; the request carries no account or location, only the pack name.
- Optional open count: one request to stats.potloodgum.com when the app opens, only if the person opted in. It carries app version, platform and a random daily value; no IP is stored, no device id.
- Backup: an export file the person saves themselves.
- No accounts, no analytics SDK, no ads, no third-party code that collects data.
- OpenStreetMap attribution is shown on the map.

## Privacy policy (host at https://potloodgum.com/misty-steps/privacy, needs publishing)

Misty Steps does not collect your data. Your walks, pins and settings stay on your phone. Location is used on your phone to show the map and clear the fog, and is never sent to us or anyone else. The app downloads map files for the province you choose; this request does not include your location or any identifier. If you switch on the anonymous open count, the app tells us once per day that it was opened, with the app version and platform only. We cannot tell who you are. We do not use advertising, analytics or tracking tools. Backups are files you save yourself; we never see them. If you delete the app, its data is deleted with it. Contact: <contact address, user to fill in>.

## Google Play

Build: `./gradlew bundleRelease` gives `app/build/outputs/bundle/release/app-release.aab` (about 24 MB, all ABIs). Play signs the final app; the AAB is signed with the upload key (release keystore).

User must: create the app in Play Console, enrol in Play App Signing, upload the AAB to an internal or closed testing track, add testers.

Data safety form answers: collects no data; shares no data; data is not encrypted in transit question applies only to the optional open count (HTTPS, so yes encrypted); no deletion mechanism needed because nothing is collected. Optional open count carries no personal data, so declare nothing.

Declarations to prepare:
- Foreground service types: `location` (recording a walk) and `dataSync` (map download). Play asks for a short video and justification for location in the foreground service form: the notification shows while recording, started by the person tapping Start wandering.
- Location permission: foreground only, no background location permission is requested.
- Content rating: no user content, no violence; answer the questionnaire as everyone.
- Target audience: 13 and over is safest; not a children's app.
- Countries: Netherlands only.
- Store listing texts and screenshots exist in `fastlane/metadata/android/` (English; Dutch still to write).
- New personal developer accounts must run a closed test with 12 testers for 14 days before production access (check current rule in Play Console).

## TestFlight (iOS)

Done in the repo: app icon (1024, from the Android icon), privacy manifest (`PrivacyInfo.xcprivacy`, no tracking, no collected data, UserDefaults and file timestamp reasons), export compliance flag (no non-exempt encryption), location usage string, photo add usage string, category.

User must:
1. Give the Apple team id; set `DEVELOPMENT_TEAM` in `iosApp/project.yml`.
2. Create the app in App Store Connect with bundle id `com.potloodgum.mistysteps.ios` (change in project.yml if another id is wanted; Apple ids cannot be reused).
3. Real iPhone test first (background GPS and battery are untested).
4. Archive and upload (`xcodebuild archive` then Xcode Organizer or `xcrun altool`), add internal testers.

Open points before App Review (not TestFlight internal testing):
- Background location: the app uses When In Use only. While the screen is off a walk stops recording unless `UIBackgroundModes: location` and `allowsBackgroundLocationUpdates` are added. Decide after the real iPhone test. If added, App Review wants a clear reason (walk recording with a visible indicator).
- App privacy answers in App Store Connect: Data Not Collected.
- Age rating questionnaire: none of the content flags apply.
- Support URL and privacy policy URL are required.
- Launch screen is the generated blank one; fine for review.
