# Custom Constellations

**Status:** Proposed (SkyLuz fork feature). Author: Aether (Digital Fleet), 2026-09-16.
Grounded in the codebase as of master `b93d83af` — every symbol below was verified in source.

## Goal

Let users draw their own asterisms/constellations on the live sky: a **"+" affordance** enters a
draw mode where tapping stars links them into strokes; the result is saved, listed in a
"My constellations" library, rendered like any IAU figure, and exportable/importable as JSON.

Non-goals: cloud sync (never), custom deep-sky art, renaming IAU figures.

## The key insight

Everything needed already exists and is reactive:

- `CatalogModel.kt:74` — `data class Figure(val owner: CelestialObjectId, val strokes: List<List<RaDec>>)`.
  A constellation **is** named strokes of RA/Dec points.
- `CatalogRepository.kt:99` — `figures(kind, culture = "iau"): Flow<List<Figure>>`.
- `CatalogLayer.kt:60` — `scenes()` combines `layerObjects` + `figures` → re-renders the moment
  the figures flow re-emits (the same mechanism data packs use, D79).

So user constellations are **runtime rows** in a small custom-figure store surfaced through the
same `figures()` pipeline with `culture = "skyluz_custom"`. No renderer changes at all for MVP.

## Design

### 1. Inverse projection (pure, `:render:api`)

`SkyProjection` (D21) exposes `worldToScreen`; add the inverse:

```kotlin
fun screenToDirection(xPx: Float, yPx: Float): Vector3?  // unit geocentric, null if invalid
```

Straightforward NDC un-projection against the same `viewProjection` matrix (invert the 2×2-ish
screen mapping, un-divide by w, un-multiply; reject behind-eye cases like `worldToScreen` does).
Pure CPU, unit-testable with zero GL — add `SkyProjectionTest.screenToDirection_roundTrip`
(for random cameras, `worldToScreen(screenToDirection(p)) == p` within 1e-3 px).

### 2. Draw mode (app module)

`MapViewModel` already computes `camera.lineOfSight → RaDec` (line ~301). Draw mode:

- New `ConstellationDrawViewModel` holding the stroke under construction.
- Gesture: in draw mode, taps are captured by a Compose overlay (pointerInput detectTapGestures)
  instead of identify. Each tap → `screenToDirection` → `RaDec.fromGeocentricVector`.
- **Snap:** query the catalog's star layer for the nearest star within an angular tolerance
  (angle from tap direction; tolerance ≈ `min(2.5% of fovDeg, 3°)`). Snap to the star's exact
  RaDec — this is what makes drawn figures re-render rock-solid at any zoom, time, or location.
  If nothing is within tolerance, record the raw RaDec (free point, e.g. drawing a triangle
  between faint stars).
- Tap on the first point of the current stroke (within tolerance) **closes the stroke**; taps
  append to the open stroke otherwise. Undo = drop last point (long-press). A subtle polyline
  preview renders via the existing figure pipeline (draft Figure pushed into the flow).
- Save: name dialog (required, unique per user store) → persisted.

### 3. Storage (new small Room store in `:data`, or DataStore + JSON)

MVP: a dedicated `custom_figures` Room table in `:data` (id, name, created, visible, strokes
JSON blob in the exact `iau.json` shape). Exposed via a `CustomFigureRepository` in
`:core:catalog` (pure interface, like the rest). The constellations `CatalogLayer` mapping
merges `catalog.figures(kind)` with `customRepo.figures()` via `combine` —
`CatalogLayers.kt` wires it; visible toggling is a WHERE clause, so the existing
layers visibility plumbing applies unchanged.

### 4. Library screen ("My constellations")

Compose screen reachable from the layers sheet: list (name, star count, created), per-item
visibility toggle, delete, export → `saf` file picker writing `{culture: "skyluz_custom",
constellations: [...]}` (same shape as `source-data/constellations/iau.json`), import =
reverse. Export/import is the share story; no server, no accounts, files are portable data.

### 4b. "Constellations" tab: starter challenges + Find game (the app's center)

A first-class tab (bottom bar, center position) with two sections:

**Starter challenges** ("constellations to find"): ship-with-the-app challenges
(`assets/challenges/skyluz_starter.json`), each anchored to a REAL catalog field:

```json
{
  "id": "challenge/turtle",
  "name": "Turtle",
  "bonus": {"id": "challenge/turtle_crown", "name": "Turtle with a crown"},
  "center": {"ra": 88.8, "dec": 7.4},   // real field, e.g. Betelgeuse region
  "radiusDeg": 20,                       // camera slew target + example fit
  "example": "assets/challenges/art/turtle.webp",
  "solution": {"strokes": [[[88.8, 7.4], [84.0, -1.2], ...]]},
  "toleranceDeg": 2.0
}
```

- Tap "Start" → the map slews to `center` (existing search fly-to path), draw mode activates
  with the challenge's tolerance; the example picture floats as a dismissible overlay card
  (webp drawn from the catalog icon style — Turtle 🐢, crowned variant 👑, Canaille 🐕).
- The player taps stars; **snap is biased to the challenge's solution stars** (a tap within
  `toleranceDeg` of a solution vertex counts as that vertex — forgiving, kid-friendly), but
  any star may be used (free drawing allowed, like real sky cultures).
- Completion = strokes cover all solution vertices (snap-count) → confetti + saved into
  "My constellations" with the challenge's art attached. Bonus challenge unlocks on completion.

**Find mode (the real-constellation game):** pick a target from the IAU set (or get a random
one within the current view): the map shows the field WITHOUT the figure lines; the player
taps the stars they think belong; scoring compares each tap against the IAU strokes' vertices
(same `toleranceDeg` semantics — hits / misses / completeness %), then reveals the real figure
with a score card. Reuses exactly the draw-mode primitives (tap→direction, snap, stroke
collection); the only new logic is scoring against `catalog.figures(kind)` data. Streaks and
a per-culture "found N/89" counter live in the tab.

### 5. Modules & purity

- Inverse projection → `:render:api` (pure, testable).
- Repository interface + merge → `:core:catalog` (pure) + `:data` (Room).
- UI → `:app` (Compose). No `android.*` in pure modules (konsist gate D20 holds).

## MVP slice order

1. `screenToDirection` + round-trip test (pure — can land first).
2. `custom_figures` store + repo + figures merge (+ tests).
3. Draw-mode overlay + VM + snap (+ tests with a fake camera).
4. Library screen + export/import.
5. (later) share as image, multiple cultures, skyculture packs from Stellarium data.

## Risks

- **Tap-vs-pan conflict** in draw mode: draw mode freezes the camera (manual mode), so taps
  are unambiguous; re-enable motion on exit. Simplest correct UX.
- **Fuzzy region near screen edges / behind viewer:** `screenToDirection` returns null →
  ignore tap (same contract as `worldToScreen`).
- Catalog star query latency: figures() already handles thousands of strokes; a user adding
  dozens is noise. No perf gate needed beyond existing smoke tests.