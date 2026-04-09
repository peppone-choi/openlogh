---
phase: 14-frontend-integration
plan: 15
subsystem: frontend
tags: [react-konva, zustand, tactical-ui, succession-fx, feedback-sounds, sonner-toasts, ui-overlay, html-layer]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-01)
    provides: "BattleTickEvent type union including FLAGSHIP_DESTROYED / SUCCESSION_STARTED / SUCCESSION_COMPLETED event codes"
  - phase: 14-frontend-integration (14-06)
    provides: "TacticalUnit.successionState + successionTicksRemaining optional fields; BattleTickEvent.type string-fallback union"
  - phase: 14-frontend-integration (14-10)
    provides: "tacticalStore.activeFlagshipDestroyedFleetIds + activeSuccessionFleetIds bookkeeping slots + onBattleTick hierarchy merge + BattleMap 5-layer restructure with empty succession-fx layer slot"
provides:
  - "frontend/src/components/tactical/FlagshipFlash.tsx — 0.5s Konva ring flash FX with computeFlashFrame pure helper for unit testing"
  - "frontend/src/components/tactical/SuccessionCountdownOverlay.tsx — HTML overlay pill rendering the '지휘 승계 중 — {N}틱' countdown, exports clampSuccessionTicks pure helper"
  - "Extended useSoundEffects.ts SoundType union with successionStart + flagshipDestroyed tunings"
  - "Extended tacticalStore.onBattleTick reducer that processes FLAGSHIP_DESTROYED / SUCCESSION_STARTED / SUCCESSION_COMPLETED events: adds flash records with wall-clock expiry, toggles activeSuccessionFleetIds, fires sonner.warning + sonner.success toasts with Korean UI copy, and fires the new succession sounds through playSoundEffect"
  - "BattleMap.tsx wiring — FlagshipFlash mounted inside the Layer id=\"succession-fx\" slot (one per activeFlagshipDestroyedFleetIds entry), SuccessionCountdownOverlay rendered as HTML sibling of the Stage (one per unit with successionState === 'PENDING_SUCCESSION')"
  - "frontend/src/stores/tacticalStore.succession.test.ts — 11 live vitest cases replacing the Wave 0 it.skip scaffold"
  - "frontend/src/components/tactical/FlagshipFlash.test.tsx — 30 live vitest cases replacing the Wave 0 it.skip scaffold (6 computeFlashFrame + 5 FlagshipFlash source-text guards + 5 clampSuccessionTicks + 6 SuccessionCountdownOverlay source-text guards + 8 BattleMap wiring guards)"
affects: [14-16, 14-17]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-helper-first FX testing: computeFlashFrame + clampSuccessionTicks live next to their components as pure exports so vitest env=node can assert visual/math decisions without mounting react-konva (mirrors 14-09 CommandRangeCircle + 14-11 FogLayer pattern)"
    - "Source-text regression guards for component wiring: FlagshipFlash.test.tsx reads BattleMap.tsx as a string and asserts <FlagshipFlash /> appears after id=\"succession-fx\" + <SuccessionCountdownOverlay /> appears after </Stage> — cheap, robust way to pin cross-component wiring under vitest node env"
    - "Sonner + useSoundEffects double-mock via vi.mock() at the top of the store test file — sonner mock exposes { warning, success, error, info } jest-fn set so the store's toast.warning/toast.success calls surface as assertable spies; useSoundEffects mock exposes playSoundEffect as a vi.fn so sound calls become observable without touching browser AudioContext"
    - "HTML-over-Konva overlay layering: SuccessionCountdownOverlay lives as an absolute-positioned sibling of the Stage (not inside Konva) so the monospaced countdown pill inherits native font rendering per UI-SPEC Section D Phase 2 — the pill is pointerEvents:none so it never competes with Konva picking"
    - "RAF + wall-clock decoupling for ephemeral FX: FlagshipFlash uses requestAnimationFrame for smoothness (60fps easing) but the store's pruning uses Date.now() millis (500ms deadline) — two clocks, one per-entry expiry slot, pruned on every tick so the Konva layer always has current data"

key-files:
  created:
    - frontend/src/components/tactical/FlagshipFlash.tsx
    - frontend/src/components/tactical/SuccessionCountdownOverlay.tsx
  modified:
    - frontend/src/hooks/useSoundEffects.ts
    - frontend/src/stores/tacticalStore.ts
    - frontend/src/stores/tacticalStore.succession.test.ts
    - frontend/src/components/tactical/BattleMap.tsx
    - frontend/src/components/tactical/FlagshipFlash.test.tsx

key-decisions:
  - "[Phase 14-15]: onBattleTick prunes expired FlagshipDestroyedRecord entries BEFORE walking data.events (not after) so a tick with zero events still cleans up stale flashes — matches the 14-10 pruning semantics but moved earlier in the pipeline to coexist with the new event-driven add path"
  - "[Phase 14-15]: FLAGSHIP_DESTROYED de-dup by fleetId with 'latest expiry wins' (not 'first expiry wins') — if the engine re-broadcasts the same fleetId on consecutive ticks the flash lifecycle resets rather than truncating. Keeps the ring visible for at least one more animation cycle"
  - "[Phase 14-15]: SUCCESSION_STARTED uses Sonner toast id='succ-{sourceUnitId}' for de-dup across re-broadcasts — the engine's per-tick retries can't cause duplicate toasts because Sonner collapses matching ids"
  - "[Phase 14-15]: SUCCESSION_COMPLETED semantics: targetUnitId is the OLD commander fleetId (removed from activeSuccessionFleetIds), sourceUnitId is the NEW commander fleetId. Matches the BattleTickEvent TypeScript doc comment in types/tactical.ts — any future engine rewiring must preserve this semantic pairing"
  - "[Phase 14-15]: JAMMING_ACTIVE intentionally NOT toasted in 14-15 — the comm-jam indicator is owned by 14-17's command proposal panel per UI-SPEC. The reducer's for-loop has a `continue` after each branch so unhandled event types fall through without side effects"
  - "[Phase 14-15]: playSoundEffect (the singleton, not the hook-wrapped playSound) is called from inside the store set(...) callback — this is safe because playSoundEffect is a pure function that reads localStorage + starts an AudioContext at call time. No React lifecycle coupling, no hook-call violations"
  - "[Phase 14-15]: FlagshipFlash's computeFlashFrame is exported as a named export alongside the component so the test file can import it without mounting react-konva. The component itself only calls the helper once per render (inside the function body, not memoized) — the RAF loop drives state updates, useState triggers re-renders, and the helper recomputes on each render. Pure, cheap, and testable in isolation"
  - "[Phase 14-15]: SuccessionCountdownOverlay.clampSuccessionTicks defaults NaN/Infinity/negative to 0 (not 'disable the overlay') — BattleMap already filters by successionState === 'PENDING_SUCCESSION' so the overlay only mounts for units actively counting down. The clamp is defence-in-depth against wire format drift"
  - "[Phase 14-15]: SuccessionCountdownOverlay rendered as HTML sibling of the Stage (inside the same relative-positioned wrapper div that already exists for the minimap overlays) — the monospaced countdown pill inherits native font rendering and CSS transitions, which would be fragile if built as a Konva Text/Rect primitive. Pointer-events:none so it never steals clicks from the canvas below"
  - "[Phase 14-15]: Rephrased the FlagshipFlash module KDoc to say 'no motion libraries' instead of 'no framer-motion' — the regression guard `expect(source).not.toMatch(/framer-motion/)` was tripping on my own forbidden-mention comment. 14-09's CommandRangeCircle rewrite had the same issue with 'Konva.Animation' and solved it identically"

patterns-established:
  - "RAF + wall-clock decoupling for FX: 60fps animation clock (RAF) + wall-clock expiry clock (Date.now()) — two clocks, one per-entry deadline, no coupling to tick rate"
  - "HTML overlay over Konva Stage for typography-critical feedback: anything involving monospaced countdowns, Korean labels, or CSS transitions goes as an absolute-positioned DOM sibling of the Stage; anything involving radial effects, gradients, or sub-tick animations stays in Konva"
  - "Sonner toast id convention for de-dup: `{kind}-{entityId}` so engine retries collapse cleanly. 14-15 uses `succ-{fleetId}` for succession start; future plans can follow the pattern without collision"

requirements-completed: [FE-04]

# Metrics
duration: ~12 min
completed: 2026-04-09
---

# Phase 14 Plan 14-15: Succession feedback FX (FE-04) Summary

**Wave 4 parallel executor. Wires the succession lifecycle's visual/audio/textual feedback into the tactical UI: tacticalStore.onBattleTick now processes FLAGSHIP_DESTROYED / SUCCESSION_STARTED / SUCCESSION_COMPLETED events, firing 0.5s Konva ring flashes (FlagshipFlash), HTML countdown pills (SuccessionCountdownOverlay), Sonner warning/success toasts with Korean UI-SPEC copy, and two new synth sound effects — all driven by the same `activeFlagshipDestroyedFleetIds` / `activeSuccessionFleetIds` bookkeeping slots 14-10 set up as empty placeholders.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-04-09T11:37:20Z
- **Completed:** 2026-04-09T11:49:14Z
- **Tasks:** 2 (Task 1 = store reducer + sounds + succession test; Task 2 = FX components + BattleMap wiring + component test)
- **Source files touched:** 7 (2 created + 5 modified)
- **Tests added:** 41 (11 store reducer + 30 FlagshipFlash/Overlay), all live, 0 skipped
- **Total tests passing in affected suite:** 128/128 (BattleMap + tacticalStore.hierarchy + tacticalStore.fog + tacticalStore.succession + CommandRangeCircle + FogLayer + FlagshipFlash)

## Accomplishments

### Store reducer (Task 1)

- **`frontend/src/hooks/useSoundEffects.ts`** extended with two new entries in `FREQUENCIES`:
  - `successionStart`: triangle 330→277→220 Hz, 600ms total (descending "tension" motif).
  - `flagshipDestroyed`: sawtooth 110→82→55 Hz, 650ms total (deep "impact" thud).
  - `SoundType` union exported for downstream components to reference.
- **`frontend/src/stores/tacticalStore.ts` onBattleTick reducer** now walks `data.events` and processes the three new event types per D-13..D-16:
  - `FLAGSHIP_DESTROYED` (sourceUnitId = affected fleet):
    - Adds a `FlagshipDestroyedRecord { fleetId, expiresAt: now + 500 }` to `activeFlagshipDestroyedFleetIds`, de-duplicating by fleetId (latest expiry wins so re-broadcasts reset the lifecycle).
    - Fires `playSoundEffect('flagshipDestroyed')`.
  - `SUCCESSION_STARTED` (sourceUnitId = affected fleet):
    - Adds `sourceUnitId` to `activeSuccessionFleetIds` (skip if already present).
    - Fires `playSoundEffect('successionStart')`.
    - Fires `toast.warning('기함 격침 — ' + detail + ', 30틱 후 지휘권이 승계됩니다.', { id: 'succ-' + sourceUnitId, duration: 6000 })` — id-based de-dup collapses engine retries.
  - `SUCCESSION_COMPLETED` (targetUnitId = OLD commander, sourceUnitId = NEW commander):
    - Removes `targetUnitId` from `activeSuccessionFleetIds`.
    - Fires `toast.success('지휘 인수 — ' + detail + '가 지휘권을 인수했습니다.', { duration: 4000 })`.
  - `JAMMING_ACTIVE` intentionally ignored (14-17 owns the comm-jam indicator per UI-SPEC).
- **Expired flash pruning** runs BEFORE the event loop so a tick with zero new events still cleans up stale entries (matches 14-10 semantics, moved earlier in the pipeline).
- **Sonner + playSoundEffect imports** added at module load so all three side effects (toast, sound, state mutation) fire from the same reducer pass.

### Store test replacement (Task 1)

- **`frontend/src/stores/tacticalStore.succession.test.ts`** replaces 4 Wave 0 `it.skip` stubs with 11 live vitest cases:
  - FLAGSHIP_DESTROYED expiresAt arithmetic + pruning on next tick (2 cases)
  - SUCCESSION_STARTED / SUCCESSION_COMPLETED bookkeeping add+remove (2 cases)
  - Multiple FLAGSHIP_DESTROYED events accumulate in a single tick + de-dup by fleetId (2 cases)
  - Sonner warning toast fires once per SUCCESSION_STARTED with `id='succ-{fleetId}'` (1 case)
  - Sonner success toast fires on SUCCESSION_COMPLETED with new commander detail (1 case)
  - `playSoundEffect` called with `'flagshipDestroyed'` + `'successionStart'` (2 cases)
  - Unrelated events (JAMMING_ACTIVE, DAMAGE) do NOT touch succession state or fire toasts (1 case)
- **Mocks** sonner + `@/hooks/useSoundEffects` via `vi.mock()` BEFORE the store import so the store's top-level imports resolve to the mocks (matches 14-11 fog test pattern).

### FX components (Task 2)

- **`frontend/src/components/tactical/FlagshipFlash.tsx`** (new, 99 lines):
  - Konva `<Group>` with two `<Circle>` children (outer white shockwave + inner echo ring) positioned at (cx, cy). `listening={false}` so clicks fall through to units below.
  - `computeFlashFrame(progress: number): { radius, opacity }` pure helper:
    - radius = 24 + 72 * progress (24 → 96 across the lifecycle)
    - opacity = 0.9 * (1 - progress) (0.9 → 0 ease-out)
    - Clamps progress to [0, 1] for defensive behaviour.
  - `useEffect` + `requestAnimationFrame` loop drives the 500ms animation (default `durationMs = 500`). Cleanup cancels RAF on unmount.
  - `onComplete` callback fires at p=1 so BattleMap can prune the store entry (future optimization — not currently wired; store prunes by wall-clock).
  - No full-screen `<Rect />` — scoped to (cx, cy) per D-14.
  - No motion libraries — pure Konva primitives + RAF.
- **`frontend/src/components/tactical/SuccessionCountdownOverlay.tsx`** (new, 87 lines):
  - HTML `<div>` (NOT Konva) — renders a 120×32 pill with gold `#f59e0b` border, monospaced countdown, Korean UI copy "지휘 승계 중" + "{N}틱".
  - Absolute-positioned via `left: screenX - 60, top: screenY - 48` so the pill centers above the unit icon.
  - `pointerEvents: 'none'` + `userSelect: 'none'` so the pill never blocks clicks or text selection on the canvas.
  - `role='status'` + `aria-label={\`지휘 승계 중, ${display}틱 남음\`}` for screen readers.
  - `clampSuccessionTicks(n)` pure helper clamps to [0, 30], floors fractionals, returns 0 for NaN / ±Infinity.
  - Wrapped in `React.memo` so re-renders only fire when screenX/screenY/ticksRemaining change.

### BattleMap wiring (Task 2)

- **`frontend/src/components/tactical/BattleMap.tsx`** additions (in coordination with the sibling 14-16 mission-line work that landed between my Read and Edit):
  - Imports `FlagshipFlash` + `SuccessionCountdownOverlay`.
  - Reads `activeFlagshipDestroyedFleetIds` from `useTacticalStore` via a dedicated selector.
  - `Layer id="succession-fx"` (previously an empty 14-10 placeholder) now maps over `activeFlagshipDestroyedFleetIds` and renders one `<FlagshipFlash cx={unit.posX * scaleX} cy={unit.posY * scaleY} />` per entry. Uses `units.find((u) => u.fleetId === entry.fleetId)` to resolve the unit for position lookup — returns null if the unit is no longer in the battle.
  - After `</Stage>` but inside the same relative-positioned wrapper div, filters `units` by `isAlive && successionState === 'PENDING_SUCCESSION' && successionTicksRemaining != null` and renders one `<SuccessionCountdownOverlay />` per match.
  - The 5-layer Konva structure from 14-10 is preserved verbatim — I only filled the succession-fx slot and added the HTML sibling render path. 14-11's FogLayer and 14-16's mission-line work are untouched.

### FlagshipFlash test replacement (Task 2)

- **`frontend/src/components/tactical/FlagshipFlash.test.tsx`** replaces 3 Wave 0 `it.skip` stubs with 30 live vitest cases across 4 describe blocks:
  - **computeFlashFrame pure helper** (7 cases): initial frame (p=0 → radius=24, opacity=0.9), terminal frame (p=1 → radius=96, opacity=0), halfway frame (p=0.5 → radius=60, opacity=0.45), negative clamp, >1 clamp, monotonic radius growth, monotonic opacity decay.
  - **FlagshipFlash source-text guards** (5 cases): imports Group/Circle from react-konva, no `framer-motion` mention, `requestAnimationFrame` + `cancelAnimationFrame` loop, `durationMs = 500` default, `listening={false}`, no `<Rect />` (no full-screen fallback), Group positioned at `x={cx} y={cy}`, `FlagshipFlashProps` type compile check.
  - **clampSuccessionTicks pure helper** (5 cases): values in range, >30 clamp, <0 clamp, fractional floor, NaN/Infinity → 0.
  - **SuccessionCountdownOverlay source-text guards** (6 cases): Korean copy "지휘 승계 중" + "{display}틱", `#f59e0b` border hex, no react-konva import (HTML-only), absolute positioning via `left: screenX`, `top: screenY`, `pointerEvents: 'none'`, `SuccessionCountdownOverlayProps` type compile check.
  - **BattleMap wiring guards** (7 cases): FlagshipFlash import path, SuccessionCountdownOverlay import path, `<FlagshipFlash />` mounted inside `id="succession-fx"` Layer, `activeFlagshipDestroyedFleetIds` store read, `<SuccessionCountdownOverlay />` rendered AFTER `</Stage>`, `successionState === 'PENDING_SUCCESSION'` filter present, Layer id="succession-fx" retains `listening={false}`.

## Task Commits

1. **Task 1 (useSoundEffects + tacticalStore reducer + succession test)** — `202ec542`: `feat(14-15): onBattleTick processes succession events + feedback sounds`
   - Files: `frontend/src/hooks/useSoundEffects.ts`, `frontend/src/stores/tacticalStore.ts`, `frontend/src/stores/tacticalStore.succession.test.ts`
   - 3 files changed, 360 insertions(+), 26 deletions(-)

2. **Task 2 (FlagshipFlash + SuccessionCountdownOverlay + BattleMap wiring + FlagshipFlash test)** — `8f246a2b`: `feat(14-15): FlagshipFlash + SuccessionCountdownOverlay + BattleMap wiring`
   - Files: `frontend/src/components/tactical/FlagshipFlash.tsx` (new), `frontend/src/components/tactical/SuccessionCountdownOverlay.tsx` (new), `frontend/src/components/tactical/BattleMap.tsx`, `frontend/src/components/tactical/FlagshipFlash.test.tsx`
   - 4 files changed, 557 insertions(+), 21 deletions(-)

## Files Created/Modified

### Created

- **`frontend/src/components/tactical/FlagshipFlash.tsx`** (99 lines) — Konva Group/Circle ring flash with `computeFlashFrame` pure helper and `FlashFrame` / `FlagshipFlashProps` exports. `durationMs` defaults to 500. `useEffect` + RAF animation loop.
- **`frontend/src/components/tactical/SuccessionCountdownOverlay.tsx`** (87 lines) — HTML `<div>` absolute-positioned pill with `clampSuccessionTicks` pure helper and `SuccessionCountdownOverlayProps` exports. `React.memo`-wrapped. role='status' for a11y.

### Modified

- **`frontend/src/hooks/useSoundEffects.ts`** (+22 lines) — `SoundType` union extended with `'successionStart' | 'flagshipDestroyed'`; `FREQUENCIES` record extended with two new tone sequences. `SoundType` now exported for downstream type-safe references.
- **`frontend/src/stores/tacticalStore.ts`** (+90 lines, ~26 lines refactored) — new imports (`toast` from sonner, `playSoundEffect` from `@/hooks/useSoundEffects`), `FLAGSHIP_FLASH_DURATION_MS` const, onBattleTick event-processing loop handling 3 new event types with toast + sound side effects.
- **`frontend/src/stores/tacticalStore.succession.test.ts`** (+200 lines / -30 lines) — Full replacement of 4 Wave 0 `it.skip` stubs with 11 live vitest cases. Mocks sonner + useSoundEffects via `vi.mock()` + `vi.mocked(...).mockClear()` per-test.
- **`frontend/src/components/tactical/BattleMap.tsx`** (+50 lines) — FlagshipFlash + SuccessionCountdownOverlay imports, `activeFlagshipDestroyedFleetIds` store selector, Layer `id="succession-fx"` populated with FlagshipFlash render loop, HTML sibling render block after `</Stage>` iterating units filtered by `successionState === 'PENDING_SUCCESSION'`.
- **`frontend/src/components/tactical/FlagshipFlash.test.tsx`** (+240 lines / -25 lines) — Full replacement of 3 Wave 0 `it.skip` stubs with 30 live vitest cases across 4 describe blocks (pure helpers + source-text guards + BattleMap wiring assertions).

## Decisions Made

See frontmatter `key-decisions` for the full 10-item list. Most load-bearing decisions:

1. **Sonner toast id de-dup convention: `succ-{fleetId}`.** Engine re-broadcasts of the same SUCCESSION_STARTED event will collapse into a single toast instead of stacking. Future plans can adopt the `{kind}-{entityId}` pattern without collision.

2. **FLAGSHIP_DESTROYED de-dup by fleetId with "latest expiry wins".** If the engine re-broadcasts the same fleetId on consecutive ticks the flash lifecycle resets rather than truncating — keeps the ring visible for at least one more animation cycle, which feels more correct for a re-broadcast than an abrupt cut.

3. **HTML over Konva for typography-critical feedback.** SuccessionCountdownOverlay is an HTML `<div>` because the monospaced countdown pill needs native font rendering and CSS transitions. Konva Text/Rect primitives would be fragile. The pill is `pointer-events: none` so it never competes with canvas picking.

4. **RAF + wall-clock decoupling.** FlagshipFlash's RAF loop runs at 60fps for smooth easing; the store prunes entries by `Date.now() > expiresAt`. Two clocks, one per-entry expiry slot. This matches 14-10's documented decision that flash lifecycles are measured in millis, not tick count.

5. **JAMMING_ACTIVE intentionally not toasted in 14-15.** The comm-jam indicator belongs to 14-17's command proposal panel per UI-SPEC. The reducer's for-loop has a `continue` after each branch so unhandled event types fall through without side effects.

6. **Pure helpers exported for vitest env=node.** `computeFlashFrame` + `clampSuccessionTicks` are pure named exports alongside their components so tests can assert all visual/math decisions without mounting react-konva. Same pattern 14-09 (CommandRangeCircle) and 14-11 (FogLayer) established.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Source-text regression guard tripped on my own forbidden-mention comment**

- **Found during:** Task 2 test run
- **Issue:** My FlagshipFlash.tsx KDoc said `"Konva-native (no framer-motion) — uses requestAnimationFrame"` but the test file asserts `expect(flagshipFlashSource).not.toMatch(/framer-motion/)`. The test tripped on my own comment string because the regression guard didn't distinguish code from comments.
- **Fix:** Rephrased the comment to `"Konva-native animation (no motion libraries) — uses requestAnimationFrame"`. No behavioural change, just comment wording. Test is now accurate (asserts the absence of the literal string `framer-motion` anywhere in the source — which is what the plan's intent actually is).
- **Files modified:** `frontend/src/components/tactical/FlagshipFlash.tsx`
- **Verification:** 30/30 FlagshipFlash tests pass after rephrase. This exact failure mode was encountered by 14-09 (CommandRangeCircle.tsx had to rephrase "new Konva.Animation(...)" to "Konva imperative animation loop" for the same reason) — precedent documented in 14-09-SUMMARY.md.
- **Commit:** Included in Task 2 commit `8f246a2b`.

**2. [Rule 3 - Scope-correct] Merged sibling 14-16 BattleMap.tsx edits that landed mid-execution**

- **Found during:** Task 2 BattleMap edit
- **Issue:** When I first read BattleMap.tsx in the initial parallel read batch, it had the 14-10 structure without 14-16's changes. By the time I went to edit the imports block, sibling Wave 4 plan 14-16 had landed its mission-line helper (`clampMissionLineEnd`) + `useGalaxyStore` import + additional prop wiring. The Edit tool flagged "File has been modified since read". I re-read the file, confirmed 14-16's additions were purely additive (new helper, new import, new prop — no overlap with my succession-fx layer or my HTML sibling render path), and re-applied my edits on top.
- **Fix:** Re-read BattleMap.tsx, added my imports (`FlagshipFlash`, `SuccessionCountdownOverlay`) alongside 14-16's existing imports, added my `activeFlagshipDestroyedFleetIds` selector alongside the existing `currentBattle` selector, and replaced the `succession-fx` Layer placeholder with my render loop. The HTML sibling render block after `</Stage>` is entirely new code with no overlap risk.
- **Files modified:** `frontend/src/components/tactical/BattleMap.tsx`
- **Verification:** 128/128 tests pass across BattleMap + all related test files (including 14-10's layer-ordering regression guard, 14-16's clampMissionLineEnd helper tests, and my new FlagshipFlash wiring guards). No merge conflicts, no lost work from either side.
- **Commit:** Included in Task 2 commit `8f246a2b`.

**3. [Rule 3 - Scope-correct] Deferred out-of-scope typecheck errors to deferred-items.md per sibling plans**

- **Found during:** Task 2 `pnpm typecheck` + `pnpm build` runs
- **Issue:** `pnpm typecheck` fails on `frontend/src/components/tactical/InfoPanel.tsx:111` (`TS2339: Property 'name' does not exist on type 'StarSystem'`). `pnpm build` fails at the TypeScript phase on the same line. Neither file is in my scope — InfoPanel is owned by sibling Wave 4 plan 14-16, and the StarSystem type is owned by a galaxy store refactor elsewhere in the wave.
- **Fix:** Per the GSD scope boundary ("only auto-fix issues DIRECTLY caused by the current task's changes"), I did not touch InfoPanel.tsx. Confirmed my own file set (`tacticalStore.ts`, `useSoundEffects.ts`, `FlagshipFlash.tsx`, `SuccessionCountdownOverlay.tsx`, `BattleMap.tsx`, `tacticalStore.succession.test.ts`, `FlagshipFlash.test.tsx`) typecheck cleanly — zero TS errors in the files I own. Plan 14-13's deferred-items.md entry from earlier in Wave 4 already logs this InfoPanel issue under `## Pre-existing typecheck/test breaks (found during 14-13 execution, Wave 4)` with 14-16 attributed as the likely owner, so no new deferred-items entry is needed.
- **Files modified:** None.
- **Verification:** `git status --short` confirms only my 4 Task 2 files are staged; `pnpm test --run` across the 7 related test files shows 128/128 pass; the only pnpm build failure is on InfoPanel.tsx which is sibling-owned.

---

**Total deviations:** 3 auto-fixed (1 Rule 1 - Bug, 2 Rule 3 - Scope-correct)

**Impact on plan:** None. All three deviations preserved plan intent. Deviation 1 was a comment rephrase with zero behavioural change. Deviation 2 was a parallel-wave merge that preserved both 14-15 and 14-16 intent without conflict. Deviation 3 is the standard sibling-error sceptic-pattern: my own files typecheck cleanly, and the one failing file (sibling 14-16) is already logged by an earlier Wave 4 plan.

## Issues Encountered

- **Parallel wave mid-edit file drift.** BattleMap.tsx was edited by sibling plan 14-16 between my initial Read and my first Edit call, triggering the Edit tool's "file has been modified since read" safeguard. This is working as designed — the safeguard caught a race I would otherwise have clobbered. Re-read → merge-aware edit → clean commit. Same coordination pattern that 14-10 and 14-11 had to navigate in Wave 3.
- **Aggregate `pnpm typecheck` / `pnpm build` non-green due to sibling InfoPanel.tsx error.** Not a regression from 14-15; 14-13's deferred-items entry already attributes it to 14-16's galaxy store refactor. My file set typechecks cleanly in isolation. The full-project green light will arrive when 14-16 lands its StarSystem.name fix.

## User Setup Required

None — plan is pure frontend code with no service configuration changes. No new npm deps (sonner already installed, Konva already in the dep tree, React hooks already wired).

## Next Phase Readiness

- **14-16 (status markers + NPC mission objective)** — already landed in parallel alongside my work. My succession-fx layer + HTML overlays do not conflict with 14-16's mission-line helper in BattleMap.tsx; both are additive slots that the 14-10 layer restructure explicitly left open.
- **14-17 (command proposal panel / comm-jam indicator)** — unblocked. 14-15 intentionally did NOT toast `JAMMING_ACTIVE` events per UI-SPEC; the reducer's `for` loop has a fall-through slot where 14-17 can plug in its own comm-jam indicator logic. `BattleTickEvent.type === 'JAMMING_ACTIVE'` still reaches `state.recentEvents` so any UI subscribed to the event log can react.
- **Succession feedback is end-to-end ready.** Backend (14-01 dto + engine events) → store (14-10 slots + 14-15 reducer) → Konva layer (14-15 FlagshipFlash) → HTML overlay (14-15 SuccessionCountdownOverlay) → Sonner toasts (14-15 Korean UI copy) → audio (14-15 new sounds). A future integration test or manual QA pass with a live battle can verify the chain flows cleanly.

### Blockers / concerns

- **None blocking 14-15.** The sibling InfoPanel.tsx typecheck error (14-16 ownership) is the only non-green signal in the affected area, and it's already tracked in deferred-items.md under a prior Wave 4 entry.

## Known Stubs

None. Every feature FE-04 requires is live:
- Ring flash: mounted + animated + pruned.
- Countdown overlay: mounted + filtered + screen-positioned.
- Toasts: fire with Korean copy on start + completion.
- Sounds: play on start + destroyed.
- Reducer: processes all 3 relevant event types (JAMMING_ACTIVE intentionally deferred to 14-17 per plan).

The only placeholder-adjacent piece is the `onComplete` callback on FlagshipFlash — the component accepts it but BattleMap doesn't currently wire it, because the store's wall-clock prune does the cleanup already. Leaving it as an optional prop keeps the API open for a future optimization without stubbing anything today.

---

*Phase: 14-frontend-integration*
*Plan: 15*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `test -f frontend/src/components/tactical/FlagshipFlash.tsx` — created
- [x] `test -f frontend/src/components/tactical/SuccessionCountdownOverlay.tsx` — created
- [x] `grep -c 'successionStart' frontend/src/hooks/useSoundEffects.ts` → ≥ 1
- [x] `grep -c 'flagshipDestroyed' frontend/src/hooks/useSoundEffects.ts` → ≥ 1
- [x] `grep -c 'FLAGSHIP_DESTROYED' frontend/src/stores/tacticalStore.ts` → ≥ 1
- [x] `grep -c 'SUCCESSION_STARTED' frontend/src/stores/tacticalStore.ts` → ≥ 1
- [x] `grep -c 'SUCCESSION_COMPLETED' frontend/src/stores/tacticalStore.ts` → ≥ 1
- [x] `grep -c '기함 격침' frontend/src/stores/tacticalStore.ts` → 1
- [x] `grep -c '지휘 인수' frontend/src/stores/tacticalStore.ts` → 1
- [x] `grep -c '지휘 승계 중' frontend/src/components/tactical/SuccessionCountdownOverlay.tsx` → 1
- [x] `grep -c 'FlagshipFlash' frontend/src/components/tactical/BattleMap.tsx` → ≥ 1
- [x] `grep -c 'SuccessionCountdownOverlay' frontend/src/components/tactical/BattleMap.tsx` → ≥ 1
- [x] `pnpm test --run src/stores/tacticalStore.succession.test.ts` → 11/11 pass
- [x] `pnpm test --run src/components/tactical/FlagshipFlash.test.tsx` → 30/30 pass
- [x] `pnpm test --run` across 7 related tactical test files → 128/128 pass (BattleMap + tacticalStore.hierarchy + tacticalStore.fog + tacticalStore.succession + CommandRangeCircle + FogLayer + FlagshipFlash)
- [x] All 14-15 source files typecheck cleanly in isolation (unrelated InfoPanel.StarSystem.name error is 14-16-owned, already logged in deferred-items.md)
- [x] Commit `202ec542` exists and contains Task 1 files (useSoundEffects.ts, tacticalStore.ts, tacticalStore.succession.test.ts)
- [x] Commit `8f246a2b` exists and contains Task 2 files (FlagshipFlash.tsx, SuccessionCountdownOverlay.tsx, BattleMap.tsx, FlagshipFlash.test.tsx)
