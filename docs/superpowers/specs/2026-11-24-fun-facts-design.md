# Fun facts on the stats page: design

Status: approved by the user on 2026-11-24 ("yes" to the design below: a Fun facts card with crazy comparisons).

## Intent and why

The stats page is numbers. The owner wants it quirkier: crazy comparisons that make a number feel like something ("you wandered 1,5 marathons", "you cleared 2 times Monaco").

## Goals

- A "Fun facts" card on the stats page, right under the two top tiles (Wandered, Cleared), with up to three lines: one for the distance wandered, one for the area cleared, one for the mist cleared (volume).
- Each line compares the number with a real reference (Afsluitdijk, Elfstedentocht, football pitch, Vondelpark, Monaco, Olympic pool, a bathtub, the moon and so on), chosen so the result reads nicely: between 1,5 and 9.999 times the reference.
- The choice rotates by day, so it is a different joke now and then, and the same all day.
- English and Dutch; each sentence is written for its own reference so the grammar is right in both languages.

## Non-goals

- No change to the pill, the postcard or how anything is counted.
- No random jokes that change on every visit, no network, no sharing.
- No facts for people with nothing yet: a line with no sensible comparison is left out; with none at all the card is hidden.

## Anti-goals

- Never "0 football pitches", "1,0 marathons" or a number that reads as absurd; never a wrong constant.
- The moon line only shows when the share is meaningful (between 0,0001% and 100%).

## Constraints

- No emoji anywhere (project rule).
- Real, approximate constants with the source named in a comment next to each (rounded; this is a joke, not a survey).
- One shared implementation for both apps; the day comes from a small `expect fun currentEpochDay()`.

## Approaches

1. Hand-written sentences with numbers baked in: breaks as soon as the numbers change.
2. **A table of references per measure, a pure picker, a sentence per reference in the strings files (chosen).**
3. Generate sentences from a generic template ("N times the size of X"): shorter, but the Dutch and English grammar would be stiff.

## Design

- `MistBuckets.LITRES_PER_SQUARE_METRE = 2000` (2 m of mist, 1000 litres per m3).
- `FunFacts`: three tables (distance in m, area in m2, volume in litres) of `FactReference(key, size, asPercent)`; `FunFacts.pick(measure, amount, day)` returns the reference and the ratio, or null; `FunFacts.roundForReading(value)` rounds for display (two significant digits from 100 up; one decimal under 10).
- Strings: `facts_title` and one sentence per reference, `fact_<measure>_<reference>`, with `%1$s` for the number.
- `funFactLines(stats, day)` (shared, suspend) builds up to three lines; `StatsScreen` shows them in a `FunFactsCard` under the tiles.

## Left to the builder

The card's exact spacing, and which references stay in the tables if two are too similar.
