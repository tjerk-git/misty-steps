# Swipe through your postcards: design

Status: approved by the user on 2026-11-24 ("yes": swipe left and right through the postcards, exit back to the screen you came from).

## Intent and why

Tapping a walk in the stats log opens its postcard, and that is a dead end: to see another one you go back, scroll and tap again, and leaving drops you on the map instead of on the stats page you came from.

## Goals

- On the postcard screen, swiping left and right moves through your walks, newest first (swipe left for an older walk).
- A small "3 / 17" shows where you are; Save and Share act on the postcard on screen.
- Leaving returns to the screen you came from: from the stats page, the stats page (same scroll position); from the postcard after a walk just ended, the map.
- Same on Android and iPhone.

## Non-goals

- No change to how a postcard is painted or what is on it.
- No new screens, no list or grid of postcards.
- No change to when the postcard appears after a walk.

## Anti-goals

- Swiping through many walks must not pile up pictures in memory or repaint the same walk again and again.
- Save or Share must never act on a different postcard than the one on screen (the iPhone host used to remember only the last painted one).

## Constraints

- No emoji anywhere (project rule).
- Painting a postcard is expensive (a map snapshot), so a page paints only when it is reached, and only the current page and its neighbours stay in memory.
- The walks to cycle through are the ones the log shows (`walksToShow`: long enough, or with a pin, or still running), newest first, and the walk you opened is always among them.

## Approaches

1. Open the postcard of the next walk by closing and reopening the screen: flickers and loses the place.
2. **A horizontal pager inside the postcard screen, fed by the host with the list of walks (chosen).**
3. A carousel of small postcards you pick from: another screen, not what was asked.

## Design

- `PostcardPaging.walkIds(walks, walksWithPins, startId)` (shared, pure): newest first (walk ids are start times), only the walks the log shows, the start walk always included.
- `PostcardHost.walkIds(startId)` (suspend): Android reads the sessions and the pins; the iPhone painter gives the sessions and the pins' walk ids and the shared host calls `PostcardPaging`.
- `PostcardScreen(walkId, host, onClose)` keeps its signature: it loads the list, then shows a `HorizontalPager` starting on the opened walk; each page paints itself when composed and caches the picture; pages more than one away are dropped from the cache.
- iPhone host keeps the PNG of every painted walk by file name, so Save and Share use the right one.
- Android: opening a postcard from the stats page no longer closes the stats page, so closing the postcard shows it again. iPhone: the same through the route and the overlay order (the postcard is drawn above the stats page).

## Left to the builder

The look of the "3 / 17" text, and how many postcards stay cached (current and the two neighbours).
