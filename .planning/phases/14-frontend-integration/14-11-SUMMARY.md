---
phase: 14-frontend-integration
plan: 11
subsystem: ui
tags: [konva, react, fog-of-war, tactical-ui, zustand, sensor-range, hierarchy-vision]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-01)
    provides: "TacticalUnitDto.lastSeenPositionByEnemyFaction + lastSeenTick + sensorRange (sensorRange shipped opportunistically by 14-01, refined by 14-03)"
  - phase: 14-frontend-integration (14-03)
    provides: "Backend per-tick TacticalUnit.sensorRange recomputed from SensorRangeFormula (D-19)"
  - phase: 14-frontend-integration (14-05)
    provides: "Wave 0 scaffolds: tacticalStore.fog.test.ts + FogLayer.test.tsx with it.skip placeholders + tacticalBattleFixture.ts factory"
  - phase: 14-frontend-integration (14-06)
    provides: "Frontend TS mirror of CommandHierarchyDto + SubFleetDto + 8 optional TacticalUnit Phase 14 fields incl. sensorRange"
  - phase: 14-frontend-integration (14-09)
    provides: "BattleMap 5-layer restructure with empty fog-ghosts Layer slot at id='fog-ghosts' (carved during 14-09 work, finalized in 14-10)"
  - phase: 14-frontend-integration (14-10)
    provides: "tacticalStore.lastSeenEnemyPositions field + activeSuccessionFleetIds + activeFlagshipDestroyedFleetIds slots; commandChain.ts findAlliesInMyChain helper; BattleMap multi-CRC + hierarchy merging in onBattleTick"
provides:
  - "Pure fogOfWar.ts helpers: computeVisibleEnemies, updateLastSeenEnemyPositions, ghostOpacity, GhostEntry interface, GHOST_TTL_TICKS=60, GHOST_OPACITY_MAX=0.4, GHOST_OPACITY_MIN=0.15, GHOST_OPACITY_RAMP_START=30"
  - "tacticalStore.onBattleTick fog-of-war integration — computes visible enemies via hierarchy + sensorRange every tick and upserts/prunes lastSeenEnemyPositions per D-17/D-20"
  - "EnemyGhostIcon component — dashed-stroke triangle/square with Korean tick stamp ('{n}틱 전' + ' · 정보 노후' for stale)"
  - "FogLayer component — store-driven render of EnemyGhostIcon per stale entry, skipping currently-visible enemies (no double-render with live units)"
  - "BattleMap fog-ghosts Layer wired with <FogLayer myOfficerId scaleX scaleY /> per UI-SPEC Section E ordering"
  - "13 fog reducer tests + 27 FogLayer/EnemyGhostIcon tests (40 total, 100% pass)"
affects: [14-12, 14-13, 14-14, 14-15, 14-16, 14-17, 14-18]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure fog reducer pattern: hierarchy resolution delegated to commandChain.findAlliesInMyChain (single source of truth from 14-10), distance check layered on top"
    - "Konva component test pattern (mirrors 14-09 CommandRangeCircle): no react-konva mount under environment:'node' — instead test pure helpers + source-text regression guards on the .tsx file + compile-time prop interface contracts"
    - "Structural typing for fog records: GhostEntry (defined in fogOfWar.ts) and LastSeenEnemyRecord (defined in tacticalStore.ts by 14-10) share the exact same shape, so they're assignment-compatible without an explicit conversion or shared module"
    - "Sibling-safe BattleMap insertion: Task 2 only added an import + replaced the fog-ghosts Layer body — left the 5-layer ordering, props, hierarchy memoization, and CRC logic from 14-10 untouched"

key-files:
  created:
    - "frontend/src/lib/fogOfWar.ts"
    - "frontend/src/components/tactical/FogLayer.tsx"
    - "frontend/src/components/tactical/EnemyGhostIcon.tsx"
  modified:
    - "frontend/src/stores/tacticalStore.ts"
    - "frontend/src/stores/tacticalStore.fog.test.ts"
    - "frontend/src/components/tactical/FogLayer.test.tsx"
    - "frontend/src/components/tactical/BattleMap.tsx"

key-decisions:
  - "[Phase 14]: Plan 14-11: fogOfWar.ts delegates hierarchy resolution to commandChain.findAlliesInMyChain (14-10) instead of duplicating chain logic — prevents two definitions of D-18 vision rules from drifting. The plan suggested an inline helper; refactored after observing 14-10's commit landed cleanly."
  - "[Phase 14]: Plan 14-11: GhostEntry interface in fogOfWar.ts mirrors LastSeenEnemyRecord shape from tacticalStore.ts (defined by 14-10) — structural typing keeps them assignment-compatible across the file boundary without forcing a shared types module that would have created an import cycle (store ↔ lib)."
  - "[Phase 14]: Plan 14-11: FogLayer is NOT a Konva Layer wrapper — it returns a React fragment of EnemyGhostIcon nodes intended to be mounted INSIDE the existing <Layer id='fog-ghosts'> in BattleMap. Keeps BattleMap's 5-layer structure (carved by 14-10) authoritative; no nested Layers, no prop drilling for layer config."
  - "[Phase 14]: Plan 14-11: FogLayer/EnemyGhostIcon tests follow the 14-09 CommandRangeCircle pattern — no react-konva mount, only pure-helper assertions + source-text regression guards. environment:'node' would otherwise need jsdom + canvas polyfills which are not in the project config."
  - "[Phase 14]: Plan 14-11: ghost shape rule reuses TacticalUnitIcon's flagship/battleship→△ rule via unitType string check on the stored entry (not a separate isFlagship flag) — entries are minimal snapshots so we don't store the full TacticalUnit on the ghost. Both unitType literals are checked because backend can emit either."
  - "[Phase 14]: Plan 14-11: ghosts use fillEnabled={false} not fill={undefined} — react-konva treats undefined as a noop (Konva default fill is transparent already, but the prop validator complains). fillEnabled is the explicit Konva-native opt-out."
  - "[Phase 14]: Plan 14-11: EnemyGhostIcon stamp uses inline color literals #888888 (stroke) + #7a8599 (--muted-foreground stamp) instead of pulling from tacticalColors.ts because ghosts are intentionally side-neutral — surfacing them through tacticalColors.ts would imply they could carry faction tint, which D-17/UI-SPEC Section E explicitly forbids."

patterns-established:
  - "Pure fog reducer with delegated hierarchy: fogOfWar.computeVisibleEnemies imports findAlliesInMyChain from commandChain (single source of truth)"
  - "Source-text Konva test pattern: presentation components ship with a paired *.test.tsx that asserts visual contracts (dash arrays, color literals, Korean copy, layer ordering) via readFileSync + regex + compile-time prop interface destructure, NOT react-konva mount"
  - "Sibling-wave merge protocol: when multiple parallel executors touch the same shared file (tacticalStore.ts, BattleMap.tsx), the later commit absorbs the earlier executor's edits transparently as long as both make narrow, non-conflicting structural changes. 14-10's metadata commit picked up 14-11's onBattleTick fog wiring via this mechanism."
  - "Structural-typing handoff between waves: 14-10 defined LastSeenEnemyRecord in the store; 14-11 re-declared the identical shape as GhostEntry in fogOfWar.ts and relies on TypeScript structural typing — no shared types module needed, no import cycle"

requirements-completed: [FE-05]

# Metrics
duration: ~20 min
completed: 2026-04-09
---

# Phase 14 Plan 14-11: Frontend fog-of-war reducer + FogLayer rendering Summary

**Pure fog-of-war reducer (D-17/D-18/D-19/D-20) plus EnemyGhostIcon + FogLayer wired into BattleMap's fog-ghosts Layer slot — stale enemies render as dashed-outline ghosts with Korean tick stamps, hierarchy-shared sensor cones drive visibility, and double-rendering against live units is prevented by a visibility skip in the layer.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-09T11:10:24Z
- **Completed:** 2026-04-09T11:30:59Z
- **Tasks:** 2 (TDD: each task = test scaffold flip → live tests + production code in a single commit)
- **Files created:** 3 source (fogOfWar.ts, EnemyGhostIcon.tsx, FogLayer.tsx)
- **Files modified:** 4 (tacticalStore.ts via merged 14-10 metadata commit, tacticalStore.fog.test.ts scaffold→live, FogLayer.test.tsx scaffold→live, BattleMap.tsx FogLayer mount)

## Accomplishments

- **Pure fogOfWar.ts shipping the entire FE-05 reducer.** `computeVisibleEnemies` walks the hierarchy via `findAlliesInMyChain` (14-10), aggregates each ally's `sensorRange` cone, and returns the set of visible enemy fleetIds. `updateLastSeenEnemyPositions` upserts visible enemies, removes dead ones, retains stale ghosts (D-17), and prunes entries past `GHOST_TTL_TICKS`. `ghostOpacity` implements the D-20 0.4→0.15 linear ramp between tick ages 30 and 60.
- **`tacticalStore.onBattleTick` fog integration.** The reducer resolves the active hierarchy for the viewer's side, runs `computeVisibleEnemies`, then `updateLastSeenEnemyPositions`, and writes the result back into `lastSeenEnemyPositions`. Spectators (no `mySide`) get the previous map verbatim. Per parallel-wave merge protocol, this onBattleTick body landed via 14-10's metadata commit `278b7754` — 14-10 absorbed 14-11's structural edits transparently.
- **`EnemyGhostIcon.tsx` — pure dashed-outline icon with Korean tick stamp.** Triangle (RegularPolygon sides=3) for `flagship`/`battleship` unitType, square (Rect) for everything else — mirrors `TacticalUnitIcon`'s shape rule. Stroke is neutral gray `#888888` regardless of side per D-17. `dash={[3, 3]}`, `fillEnabled={false}`. Korean stamp text below: `${ticksAgo}틱 전` plus ` · 정보 노후` when stale (age > 30 ticks). Group is `listening={false}` so ghosts never steal click events from live units in Layer 4.
- **`FogLayer.tsx` — store-driven ghost orchestrator.** Subscribes to `currentBattle` + `lastSeenEnemyPositions`, resolves the active hierarchy + viewer side, calls `computeVisibleEnemies`, then `Object.entries(lastSeen).map`s into `EnemyGhostIcon` instances. Skips fleetIds that are currently visible (avoids double-render with the live `TacticalUnitIcon` in Layer 4). Memoized on `currentBattle` + `myOfficerId`.
- **BattleMap fog-ghosts Layer wired.** Imports `FogLayer`, mounts `<FogLayer myOfficerId={myOfficerId ?? -1} scaleX={scaleX} scaleY={scaleY} />` inside the existing `<Layer id="fog-ghosts">` slot from 14-10's 5-layer restructure. Layer is non-listening so click-through behavior is preserved.
- **40 tests (100% pass), all flipped from `it.skip` Wave 0 placeholders to live assertions.** 13 fog reducer tests (`tacticalStore.fog.test.ts`) cover D-17 stale preservation, D-18 hierarchy-shared vision (positive: sub-fleet member sensors flowing up; negative: sibling sub-fleet sightings NOT flowing across), D-19 sensorRange-only gating, D-20 dead-enemy removal, TTL prune, opacity ramp monotonicity, and exported constants. 27 FogLayer tests cover ghostOpacity contract (6), visible-vs-stale integration via pure helpers (2), EnemyGhostIcon source-text contracts (8: dashed stroke, Korean stamp, gray-only, listening=false, flagship triangle, fillEnabled=false, props), FogLayer source-text contracts (7: store hooks, fogOfWar import, visible-skip, side selector, defensive null guard, EnemyGhostIcon iteration, props), and BattleMap wiring guards (4: import, mount inside id="fog-ghosts" Layer, props pass-through, layer ordering between background and command-range per UI-SPEC Section E).

## Task Commits

1. **Task 1: fogOfWar.ts pure helpers + tacticalStore fog wiring + 13 reducer tests** — `5f2948aa` (feat)
2. **Task 2: EnemyGhostIcon + FogLayer + BattleMap mount + 27 component tests** — `27b1fa86` (feat)

Both commits use `--no-verify` per parallel Wave 3 protocol (siblings 14-09, 14-10 active concurrently).

**Plan metadata commit:** TBD (this SUMMARY.md + STATE.md + ROADMAP.md update)

_Note: Task 1's tacticalStore.ts edits actually landed in HEAD via 14-10's metadata commit `278b7754` (which absorbed the same structural diff while finalizing 14-10's hierarchy work) — see "Issues Encountered" below for details._

## Files Created/Modified

### Created
- `frontend/src/lib/fogOfWar.ts` (148 lines) — Pure helpers: `GhostEntry` interface, `GHOST_TTL_TICKS`/`GHOST_OPACITY_MAX`/`GHOST_OPACITY_MIN`/`GHOST_OPACITY_RAMP_START` constants, `computeVisibleEnemies`, `updateLastSeenEnemyPositions`, `ghostOpacity`. Imports `findAlliesInMyChain` from `@/lib/commandChain` (14-10).
- `frontend/src/components/tactical/EnemyGhostIcon.tsx` (94 lines) — Pure presentation. Triangle/square shape, dashed gray stroke, Korean tick stamp, fillEnabled={false}, listening={false}.
- `frontend/src/components/tactical/FogLayer.tsx` (96 lines) — Store-driven render of EnemyGhostIcon per stale enemy entry. Memoized on currentBattle + myOfficerId.

### Modified
- `frontend/src/stores/tacticalStore.ts` — `onBattleTick` extended to compute `mySide`, resolve `myHierarchy`, call `computeVisibleEnemies` + `updateLastSeenEnemyPositions`, and write back into `lastSeenEnemyPositions`. Imports `computeVisibleEnemies` + `updateLastSeenEnemyPositions` from `@/lib/fogOfWar`. (Body landed via 14-10's metadata commit; see Issues.)
- `frontend/src/stores/tacticalStore.fog.test.ts` — Wave 0 scaffold (4 `it.skip`) replaced with 13 live vitest assertions covering D-17/D-18/D-19/D-20.
- `frontend/src/components/tactical/FogLayer.test.tsx` — Wave 0 scaffold (4 `it.skip`) replaced with 27 live vitest assertions across 4 describe blocks.
- `frontend/src/components/tactical/BattleMap.tsx` — Added `import { FogLayer } from './FogLayer'`; replaced the placeholder body of `<Layer id="fog-ghosts">` with `<FogLayer myOfficerId={myOfficerId ?? -1} scaleX={scaleX} scaleY={scaleY} />`. (Wiring landed via 14-10's metadata commit.)

## Decisions Made

See frontmatter `key-decisions` — 7 decisions captured. Most notable:

1. **Hierarchy resolution delegated to `commandChain.findAlliesInMyChain` from 14-10**, not duplicated inline. Refactored mid-execution after 14-10's `commandChain.ts` landed: prevents two definitions of D-18 vision rules from drifting.
2. **`GhostEntry` (fogOfWar.ts) and `LastSeenEnemyRecord` (tacticalStore.ts from 14-10) intentionally share the exact same shape** without a shared module — structural typing keeps them assignment-compatible without forcing a store↔lib import cycle.
3. **Konva component tests use the 14-09 source-text pattern**, not react-konva mount. The vitest config is `environment: 'node'` and adding jsdom + canvas polyfills was out of scope. 27 FogLayer tests still cover every visual contract (dashed stroke, Korean text, layer ordering, color literals, fillEnabled, listening) via `readFileSync` + regex + compile-time prop interface destructure.
4. **`fillEnabled={false}` instead of `fill={undefined}`** on EnemyGhostIcon shapes — Konva-native opt-out, doesn't trip the prop validator.
5. **EnemyGhostIcon shape uses unitType string check** (`flagship` || `battleship`) rather than reading `isFlagship`, because the stored `GhostEntry` is a minimal snapshot (no isFlagship field) — keeps the ghost record small and side-effect-free.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking → Refactor] Inlined `findAlliesInMyChain` early, then refactored to import from 14-10 once it landed**

- **Found during:** Task 1 (writing fogOfWar.ts)
- **Issue:** The plan's `<read_first>` block told me to read `frontend/src/lib/commandChain.ts` "after 14-10" — but at the moment of Task 1 implementation, `commandChain.ts` did not yet exist on disk (14-10 hadn't committed). Calling a function from an unwritten module would have crashed the test run immediately.
- **Fix:** Inlined a self-contained `computeAlliedOfficerIdsInMyChain` + `resolveAlliesInMyChain` helper inside `fogOfWar.ts` for the first iteration. Mid-execution (after observing `commandChain.ts` had landed via parallel sibling commit `b5c87d84`'s diff), refactored `fogOfWar.ts` to import `findAlliesInMyChain` from `@/lib/commandChain` directly. Re-ran fog tests under the new import → 13/13 still pass because both implementations honor D-18.
- **Files modified:** `frontend/src/lib/fogOfWar.ts` (inlined → refactored to import)
- **Verification:** `pnpm test --run src/stores/tacticalStore.fog.test.ts` exits 0 with all 13 tests passing both before and after the refactor; `pnpm typecheck` exits 0 after the import landed.
- **Committed in:** `5f2948aa` (final form, Task 1 commit)

**2. [Rule 3 - Blocking] FogLayerProps shape simplified vs. plan**

- **Found during:** Task 2 (writing FogLayer.tsx)
- **Issue:** The plan's pseudocode for FogLayer assumed it would read `currentOfficerId` from `useOfficerStore` — but the actual `officerStore` exposes `myOfficer: Officer | null`, not `currentOfficerId: number | null`. Wiring `myOfficer?.id` deep inside FogLayer would have created an indirect officer-store dependency on the tactical render path, which the plan's authors did not intend (BattleMap.tsx already takes `myOfficerId` as a prop).
- **Fix:** Changed `FogLayerProps` to take `myOfficerId: number` as an explicit prop. BattleMap passes `myOfficerId ?? -1` directly. This makes FogLayer pure-prop and store-only (no officerStore dependency), and matches how BattleMap uses `myOfficerId` for the multi-CRC computation.
- **Files modified:** `frontend/src/components/tactical/FogLayer.tsx`, `frontend/src/components/tactical/BattleMap.tsx` (uses the prop directly)
- **Verification:** `pnpm test --run src/components/tactical/FogLayer.test.tsx` 27/27 pass; the FogLayer prop interface contract test asserts `myOfficerId: number` exactly.
- **Committed in:** `27b1fa86` (Task 2 commit)

**3. [Rule 3 - Blocking] Konva test strategy adapted to vitest `environment: 'node'`**

- **Found during:** Task 2 (writing FogLayer.test.tsx)
- **Issue:** The plan's pseudocode for FogLayer.test.tsx imported `@testing-library/react` and tried to mount `<Stage><Layer><FogLayer/></Layer></Stage>`. Under the project's `environment: 'node'` vitest config (vitest.config.ts), react-konva fails to load because Canvas and DOM globals are absent. The 14-09 CommandRangeCircle plan hit the same issue and switched to a source-text + pure-helper strategy.
- **Fix:** Followed 14-09's CommandRangeCircle.test.tsx pattern verbatim: 27 tests across 4 describe blocks using `readFileSync` + regex source guards + compile-time prop interface checks + pure-helper assertions on `ghostOpacity` / `computeVisibleEnemies` / `updateLastSeenEnemyPositions`. No `@testing-library/react` import, no Stage mount, zero canvas requirement.
- **Files modified:** `frontend/src/components/tactical/FogLayer.test.tsx`
- **Verification:** `pnpm test --run src/components/tactical/FogLayer.test.tsx` 27/27 pass under vanilla `environment: 'node'`.
- **Committed in:** `27b1fa86` (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 Rule 3 - Blocking — all wave-coordination or env-config adaptations)
**Impact on plan:** None — all three preserve the plan's intent. Deviation 1 was a temporary scaffolding step that resolved cleanly when 14-10 landed. Deviations 2 and 3 are environmental adaptations matching pre-existing project conventions (officerStore shape and vitest config) that the plan author did not have visibility into.

## Issues Encountered

**Sibling-wave merge absorbing my structural edits:** Task 1's `tacticalStore.ts` onBattleTick body and Task 2's `BattleMap.tsx` import + FogLayer mount were both edited via the Edit tool BEFORE I committed Task 1, then 14-10's metadata commit `278b7754` landed during my work and absorbed both structural diffs into 14-10's commit transparently. After the absorption, `git diff HEAD -- frontend/src/stores/tacticalStore.ts` showed nothing because my on-disk edits matched HEAD. This is the expected behavior of the parallel-wave protocol: 14-10's planner explicitly carved both files for 14-11 to plug into, and the merge was non-conflicting because 14-10 only touched hierarchy merging in onBattleTick while 14-11 only added the fog computation block above the return statement. My task commits `5f2948aa` and `27b1fa86` therefore only contain the NEW files (fogOfWar.ts, EnemyGhostIcon.tsx, FogLayer.tsx) plus the test scaffold flips — the structural store/BattleMap edits are already in 14-10's commit. Self-check verification (below) confirms all four expected behaviors are present in the final HEAD.

**No pre-existing test interference:** The 7 pre-existing Vitest failures noted in the environment_gotchas (in `command-select-form`, `game-dashboard`, `record-zone`) are in unrelated files. Scoped test runs (`tacticalStore.fog.test.ts` + `FogLayer.test.tsx`) avoided them entirely.

## User Setup Required

None — pure frontend TypeScript / React / Konva code with no external service configuration.

## Next Phase Readiness

- **14-12 (succession countdown overlay)** — unblocked. `tacticalStore.activeSuccessionFleetIds` is already initialized by 14-10; 14-12 just needs to populate it from BattleTickEvent.SUCCESSION_STARTED events and read it from a new SuccessionCountdown component mounted in Layer 5.
- **14-13 (canCommandUnit gating)** — unblocked. `findAlliesInMyChain` from `@/lib/commandChain` (14-10) is now a stable dependency of both FE-03 (gating) and FE-05 (fog) — single source of truth for D-18 vision/command-chain rules.
- **14-14 (FlagshipFlash)** — unblocked. The Layer 5 succession-fx slot pattern is now established (mirrors what 14-11 did to fog-ghosts in Layer 2). `tacticalStore.activeFlagshipDestroyedFleetIds` already prunes per-tick in onBattleTick.
- **14-18 (end-of-battle modal)** — unaffected by 14-11; remains gated on its own fixture work.

### Blockers / concerns

- **None** for 14-11's own scope.
- **Konva test environment:** the source-text test pattern works for visual contract assertions but cannot exercise actual Konva render side effects. If Phase 14 wants higher-fidelity visual snapshots, switching `frontend/vitest.config.ts` to `environment: 'jsdom'` + adding canvas polyfills would be needed. Logged as optional improvement, not a 14-11 blocker.

---

*Phase: 14-frontend-integration*
*Plan: 11*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `frontend/src/lib/fogOfWar.ts` exists — verified
- [x] `frontend/src/components/tactical/FogLayer.tsx` exists — verified
- [x] `frontend/src/components/tactical/EnemyGhostIcon.tsx` exists — verified
- [x] `frontend/src/stores/tacticalStore.fog.test.ts` exists (scaffold flipped to live) — verified
- [x] `frontend/src/components/tactical/FogLayer.test.tsx` exists (scaffold flipped to live) — verified
- [x] `.planning/phases/14-frontend-integration/14-11-SUMMARY.md` exists — this file
- [x] Commit `5f2948aa` (Task 1) exists in `git log --oneline --all`
- [x] Commit `27b1fa86` (Task 2) exists in `git log --oneline --all`
- [x] `fogOfWar.ts` exports `computeVisibleEnemies` (line 51), `updateLastSeenEnemyPositions` (line 87), `ghostOpacity` (line 134), `GHOST_TTL_TICKS = 60` (line 31) — all verified via grep
- [x] `BattleMap.tsx` imports `FogLayer` (line 35) and mounts `<FogLayer ...>` inside `<Layer id="fog-ghosts">` (line 204-209) — verified via grep
- [x] `pnpm typecheck` exits 0 — verified
- [x] `pnpm test --run src/stores/tacticalStore.fog.test.ts src/components/tactical/FogLayer.test.tsx` → 40 passed / 40 total — verified
- [x] 13 fog reducer tests + 27 FogLayer/EnemyGhostIcon tests = 40 total, all green
