# Backup and restore for a new phone (design)

Status: agreed in conversation 2026-11-18, awaiting written approval.
Size: small change.

## Intent and why

Misty Steps is going to the Google Play Store and the App Store (Netherlands only), free forever with an
optional tip jar (see the free-forever decision in workflow.md Roadmap). Walks live only on the phone. Someone
who deletes the app or gets a new phone must get their walks and their maps back easily, with no account and no
server.

## Goals

- One recovery path: a backup file the person saves themselves (Files, iCloud Drive, Google Drive, AirDrop).
- Restore on a new phone from the first screen, and later from Settings.
- Installed provinces come back: after a restore the app downloads the same maps again in the background.
- A calm, rare reminder to save a backup.

## Non-goals and anti-goals

- No account, no server, no cloud backup run by us.
- No platform auto-backup (Android Auto Backup stays off; iCloud device backup is not relied on).
- Cross-platform restore (Android to iPhone) is not a goal. The file format happens to work both ways; it is
  not tested or promised.
- No nagging, counters, streaks or badges. The reminder is never a gate.
- No paid maps and no licence checks: every province is free, so a restore only re-downloads public files.

## Approaches considered

1. Automatic platform backup (Android Auto Backup, iCloud): easy for people, but per platform, depends on phone
   settings, and Auto Backup limits size. Not chosen as the main path.
2. Our own backup server with a recovery code: works everywhere, but means holding people's walking history.
   Against "personal and local" and the long-term cost worry. Rejected.
3. **Export file the person keeps (chosen by the owner).** Exists today; needs a smoother flow.

## Design

1. **File:** add an optional `provinces` list (ids of installed packs) to `Backup`. Old files without it still
   import. Bump nothing that breaks old readers: unknown or missing field is ignored.
2. **Restore flow:** after importing, for each province in the file that is not installed, queue the normal pack
   download (the existing pack manager, Wi-Fi and resume rules apply). Show the maps card state as today.
3. **First run:** next to "Start fresh", a "Restore from a backup" choice that opens the file picker, imports,
   then goes on to the map. Skips the province picker when the file lists provinces.
4. **Settings:** the existing Import row stays and gets the same behaviour (re-download missing provinces).
5. **Save:** one tap on "Save a backup" opens the share sheet or Files with a dated filename
   (`misty-steps-2026-11-18.json`).
6. **Reminder:** one card on the map screen, shown after 30 days since the last export (or since first walk) and
   at least one new walk. Dismissing hides it for 90 days. Never blocks anything. Same calm tone as the donation
   ask; the two cards never show on the same day.
7. **Wording** (English and Dutch): "Your walks live only on this phone. Save a backup to move to a new phone or
   keep them safe."

## Left to the builder

Exact card styling, storage of last-export and dismiss dates (existing settings store), the filename format,
the tests (extend `BackupTest` for the `provinces` field and the missing-field case).
Shared code goes in `shared/commonMain`, platform pieces (file picker, share sheet) stay behind the existing
`BackupHost`.

## Where this sits in the store release

Separate pieces, in the order I propose: (1) this backup and restore work; (2) the tip jar and the donation ask;
(3) store readiness for Android (Play listing, privacy policy and data-safety form, NL-only, 1.4.0 first);
(4) iOS for the App Store (real iPhone, signing, background GPS, App Review); (5) later: scratchable print.
