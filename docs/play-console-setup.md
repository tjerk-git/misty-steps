# Google Play Console: set-up checklist for Misty Steps

Written 2026-11-22. Values to copy into the console, in the order the console asks. Play Console changes its screens now and then: if a name below does not match, look for the closest one. Anything about declarations (data safety, location, content rating) is the owner's attestation: read each answer and change it if it is not true.

The signed bundle for the first upload is `~/Desktop/misty-play/misty-steps-1.5.0-play.aab` (version 1.5.0, versionCode 11, built with `./gradlew bundleRelease -Pstore=play`). Rebuild it for every upload; the versionCode must go up each time (`tools/release.sh` bumps it for F-Droid releases; for a Play-only build bump `version.properties` by hand and say so in the commit).

## 1. Create the app

- Name: **Misty Steps**. Default language: English (Netherlands). App (not game). Free.
- The app ID `com.potloodgum.mistysteps` is fixed by the first upload.

## 2. App content

- **Privacy policy:** `https://www.potloodgum.com/misty-steps/privacy/`
- **App access:** all functionality available without logging in.
- **Ads:** no.
- **Target audience:** adults and general audience; not designed for children.
- **Content rating:** no violence, no user-generated content, no purchases of random items, no gambling. Expected result: suitable for everyone.
- **Government, financial, health apps:** none. (The store category is health and fitness; it is not a medical app.)

### Data safety (answer what is true; this is what the app does)

- Location: used on the device to show your position and clear the mist; **never sent anywhere**. Not collected, not shared.
- Optional open count: only if the person says yes, one small message to `stats.potloodgum.com` with the app version, the platform and a day count. No identifier, no location, nothing about walks. Declare as App activity (other), optional, not linked to the user, used for analytics.
- Maps: downloaded from `maps.potloodgum.com`; a download request is not user data we keep.
- No accounts, no ads, no tracking.
- Purchases go through Google Play.
- Data is encrypted in transit (HTTPS). People cannot ask us to delete data, because we hold none.

### Permission declarations

- **Location (foreground):** needed to show where you are on the map and to clear the mist where you walk. Used only in the app and in the walk recording service.
- **Foreground service type location:** a walk keeps recording with the screen locked while you have tapped Start wandering, shown by a notification you can stop. Play may ask for a short video: open the app, tap Start wandering, lock the screen, show the notification, unlock, stop.
- **Foreground service type dataSync:** downloads the offline province map the person chose, with a progress notification.
- **Notifications:** the walk and download notifications.
- No background location permission is declared (the app does not use `ACCESS_BACKGROUND_LOCATION`).

## 3. Store listing (English)

- Title, short description and full description: copy from `fastlane/metadata/android/en-US/` (`title.txt` 11 characters of 30, `short_description.txt` 76 of 80, `full_description.txt` 1695 of 4000).
- App icon: `images/icon.png` (512 x 512).
- Phone screenshots: `images/phoneScreenshots/1_map.png` to `5_about.png` (1080 x 2400). If the console rejects the tall shape, crop to 1080 x 2160.
- **Feature graphic 1024 x 500: not made yet** (required). Ask Claude to make one.
- Category: Health and fitness. Contact email: the support address used in the other listings. Dutch translation can be added later under Manage translations.

## 4. Signing

- Play App Signing is required. Let Google create the app signing key and treat `~/Keystores/potloodgum/mistysteps-release.jks` as the **upload key** (its SHA-256 starts `7C:F2:C4:77`).
- Consequence: Play builds are signed with a different key than F-Droid builds, so someone cannot update across the two stores without uninstalling first.

## 5. First upload and testers

- Testing, Internal testing, Create release, upload the `.aab`. Add yourself under Testers (an email list) and open the opt-in link on a phone signed in with that Google account.
- Release notes (short): "First test build: offline maps, the magic eraser and your neighbourhood stats."

## 6. In-app product

- Monetize with Play, In-app products, Create product.
- Product ID: `magic_eraser` (exactly). Name: Magic Eraser. Description: "Rub away the mist you missed." Price: 3.99 EUR (set other countries automatically). Managed, one-time. Activate it.
- A payments profile (name, address, bank account) may be needed first: that is the owner's to fill in.
- Setup, License testing: add your Gmail so test purchases are free on the internal track.
- The app finds the product by ID. In a build without a store (F-Droid, debug) the eraser button shows the sheet but no Buy button.

## 7. Production access (new personal accounts)

- Closed test with at least 12 testers opted in for 14 days in a row, then apply for production in the console.
- Start collecting testers as early as possible: the 14 days start only once 12 have joined.

## After release

- Check the real purchase on the internal track with a license tester, then restore it on a second phone (reinstall, same account).
- Update the data safety form if anything about data changes.

## Price note (2026-11-22)
The price is now EUR 3,99 on both stores. The app never hard-codes it: the sheet shows what the store returns. Apple product: type must be Non-Consumable (a Consumable cannot be restored); product ids are not reused after deletion.
