# The "You" page: character and settings together

Status: approved by the user on 2026-11-24 (mockup "One You page", steps 1 to 3). One call of mine differs from the mockup: Maps and Your trails stay as the existing cards on the page for now, not detail pages.

## Intent and why

The page behind the bar-chart button mixes what you have achieved (stats) with how the app is set up (maps, backup, usage counts, about, feedback), and that is confusing. Settings move to a "You" page together with the character, because your character is your avatar: in map-first apps the avatar opens a profile page with identity on top and preferences below.

## Goals

- The bar-chart button opens a page with only stats: totals, neighbourhood, city, country, world, Your walks, Your places.
- The character button (your character's face) opens a new "You" page: your character and its name on top, then Settings, then About.
- "Change character" opens a bottom sheet with the character grid; picking one closes it and you are back on "You".
- Same behaviour on Android and iPhone, English and Dutch.

## Non-goals

- No new button on the map and no change to the other buttons (so the pointer's insets stay).
- No new detail pages for Maps and Your trails (follow-up if wanted); no change to the About page, the province picker or what any card does.
- No bottom tab bar.

## Anti-goals

- Nothing that was reachable before may become unreachable: maps, export and import, the usage toggle, the privacy note, Made by (About), feedback.
- Renaming a character must stay possible.

## Constraints

- No emoji anywhere (project rule).
- Shared Compose screens, hosted as before: Android overlays in `MistyScreen`, iPhone `Route` pages. The platform-specific cards stay as slots.
- Strings in English and Dutch in `shared/strings`.

## Approaches

1. Tabs "Stats | Settings" on one page: still no home for the character. Dropped.
2. **A "You" page behind the character button (chosen):** character on top, grouped settings below, stats on its own page.
3. A bottom tab bar (Map, Stats, You): the textbook pattern, but takes map space and rewrites navigation on both platforms. Dropped.

## Design

- `StatsScreen` keeps the numbers, Your walks and Your places; loses the slots, Maps, privacy note, More block, Made by and feedback.
- New `YouScreen(slots, versionName, onClose, onOpenAbout)`: back arrow and title "You"; a card with the walking sprite, the name (your own name or the species name) with a pencil to rename, and "Change character"; header "Settings" with the maps card, the backup card and the usage card, then the privacy note; header "About" with Made by and Send feedback.
- `CharacterScreen` becomes `CharacterSheet`: a bottom sheet with the same card grid, title and rename dialog.
- Hosts: Android shows `AndroidYouScreen` from the character button; the iPhone gets route `.you` replacing `.characters`, and `statsViewController` loses its settings arguments.

## Left to the builder

Spacing and the exact card order inside Settings (keep today's order: maps, backup, usage), Dutch wording ("Jij", "Instellingen", "Over"), the name of the new strings.
