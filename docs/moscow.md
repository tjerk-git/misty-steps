# Misty Steps: MoSCoW list

Last updated 2026-11-22. One place for "what next, what maybe, what never". Details of built features are in `workflow.md`; designs and plans are in `docs/superpowers/`.

How to read it: **Must** = needed before the next public step. **Should** = wanted, planned. **Could** (the Maybe pile) = parked on purpose, nothing designed or built unless said. **Won't** = decided against; reopen only on purpose.

Items marked (suggested) are my proposals and not yet decided by the owner.

## Must

- **Publish the stats files.** Run `tools/maps/publish.py ... --pbf ...` so each province has its `stats.zip` on R2 (needs the owner's rclone credentials). Until then the neighbourhood card only works from hand-placed files. Then check the quiet download on a real phone.
- **Google Play set-up for the magic eraser.** Listing, the in-app product `magic_eraser` (EUR 3,99, one-time), internal test purchase, closed test with about 12 testers for 14 days, data-safety form. The owner is doing this.
- **First iPhone testers on TestFlight.** Check recording with the screen locked and the battery on a real walk (build 2611212202), add the build to the external group, then watch the feedback.
- **Confirm the Galaxy S21 fix.** The reporter should be happy on 1.4.3 (dead buttons on full-screen pages).

## Should

- **Release the eraser and streets work to the stores** once the Must items above are done (Play build with `-Pstore=play`; the F-Droid build has neither the eraser button nor billing).
- **Review the eraser's Dutch and English copy** (`shared/strings`); the owner rewrote the sheet text on 2026-11-22.
- **iPhone version of the stats files** (the iPhone downloader does not fetch `stats.zip` yet). The iPhone eraser is built (2026-11-22); it still needs a real-finger test and the in-app purchase recreated as Non-Consumable in App Store Connect.

## Could (Maybe, parked)

- **Monthly recap card** (parked 2026-11-22, moved down from Should after a first look). A "Month in the mist" card on the stats page with a Share button: a picture made on the phone, shared through the share sheet, never uploaded. People compare their city percentage with each other, so the total counts, eraser ground included. What a first look found: the postcard renderer and share code can be reused; a month is the walks in a date range; erased ground has its own timestamps so walked and erased can be shown apart; "from x% to y%" can be rebuilt from spot timestamps; the streets line needs the stats files published. Open: what a friend should see first, and whether it also appears on the 1st by itself. Nothing designed or built.
- **Things you passed** (parked 2026-11-22, "in the fridge"). Windmills, benches, bridges you walked past, shown without quests, goals or counters. Would reuse the stats pipeline. Open: where it is shown and how it feels.
- **GPX import** (parked 2026-11-22). Bring past walks in from Strava, Komoot or a watch. Set aside because it works against the point of walking and the eraser covers gaps. If ever built, imported ground must be marked so stats stay honest.
- **GPX export** as a backup without accounts.
- **Home-screen widget:** your character, last walk, neighbourhood percentage.
- **A calm nudge**, off by default: about one line a week, such as "a corner of your wijk is still misty".
- **Printable mist poster** (scratchable maps), possibly as a donation-style extra.
- **A new-phone recovery path** (suggested): an encrypted export file and a restore, before the store releases. The export and import file already exists; this is about making it harder to lose.
- **StreetPass mode** (idea only, see `workflow.md` "Fun extras").
- **Cheaper mist drawing on slow phones**, if reports return (the adaptive fog from 1.4.2 is in; its gain was small).

## Won't

- **Paid provinces, unlocks and supporter perks.** The app is free for good; the tip jar stays separate. The magic eraser is the one deliberate exception (a tool, not a badge).
- **Accounts, leaderboards, friends, quests, gamification.** Comparing happens outside the app by sharing a stat card.
- **Network lookups of streets or borders from public services** (the OpenStreetMap services asked not to be used this way; CI fails if one comes back).
- **A licence server or receipt checking.**
- **Any F-Droid release of the paid eraser** (F-Droid cannot charge; the last planned F-Droid release was 1.4.x).
- **Emoji anywhere in the app, docs or code.**
