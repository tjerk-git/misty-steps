# Off-screen character pointer: design

Status: approved by the user on 2026-11-24 (short design, small change).

## Intent and why

After buying the magic eraser, a user erased ground away from where they stood. A one-finger drag, and so every eraser stroke,
turns camera follow off (`MistyMap.watchForDrags` on Android, the `following` binding in `MapView.swift` on iPhone). The character
was then off-screen with nothing on screen saying so, and it looked as if it had disappeared. The location button fixed it, but
nobody knows to try that. The screen should say where you are when you are out of view.

## Goals

- When the character is off-screen and a location fix exists, show a small pointer on the screen edge in the direction of the character.
- Tapping it does what the location button does (Android `flyToMe()`, iPhone recentre; both also turn follow back on).
- Same behaviour on Android and iPhone.

## Non-goals

- No auto-recentering, no banner, no text, no animation.
- No change to when follow stops or starts.
- No distance read-out.

## Anti-goals

- The pointer must never cover a control (left button column, bottom bar, header cards, location button) or block the map's own gestures outside its own tap area.
- It must not flicker at the screen edge when the character is partly visible.

## Constraints

- Android: the character is drawn on a non-clickable overlay (`MarkersView`), so the tap comes from the Compose layer in `MistyScreen`.
- Shared code (`shared/commonMain`) holds the geometry so both apps agree and it can be unit tested.
- No emoji anywhere (project rule). The pointer is the character's sprite face plus an arrow drawn as pixels or vector.

## Approaches

1. **Edge pointer, tap to return (chosen).** Smallest on-screen cost, says where you are, one tap back.
2. Banner "you are not in view": uses more space, adds text to translate in all languages.
3. Re-center when leaving eraser mode: simple, but pulls the view away from the patch just erased.

## Design

- Pure function in shared code: given the character's screen point (or null), the map size, and the keep-clear rectangles, return
  `Hidden` or `Show(x, y, angle)`. Visible when the point is outside the map rect (with a small margin, as hysteresis, so it does
  not flicker). Position is the ray from the screen centre to the point, clamped into the safe rectangle (map rect minus the
  keep-clear areas, with an inset).
- Hidden when: no fix, character on-screen, or following is on (the camera is on its way to you).
- Android: Compose element above the map in `MistyScreen`, driven by the map's camera-move callback and the fix; tap calls `map.flyToMe()`.
- iPhone: SwiftUI overlay in `ContentView`/`MapView` using the same function through the shared module; tap sets `following = true`.
- Tests: unit tests for the function (each edge, corner, inside, margin, keep-clear, no fix, following).

## Left to the builder

Exact pointer size and opacity (match the existing round buttons), the margin value, and the exact keep-clear rectangles from the
current layouts.
