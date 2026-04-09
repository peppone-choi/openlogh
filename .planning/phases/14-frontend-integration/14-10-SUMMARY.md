---
phase: 14-frontend-integration
plan: 10
subsystem: frontend
tags: [react-konva, zustand, tactical-ui, command-hierarchy, multi-crc, fog-of-war-scaffold, succession-scaffold, battle-map-layers]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-06)
    provides: "TacticalBattle.attackerHierarchy / defenderHierarchy fields + CommandHierarchyDto + extended TacticalUnit (maxCommandRange)"
  - phase: 14-frontend-integration (14-08)
    provides: "R3F removal — BattleMap is now the sole tactical renderer"
  - phase: 14-frontend-integration (14-09)
    provides: "CommandRangeCircle rewrite with hierarchy-aware props (cx/cy/currentRadius/maxRadius/side/isMine/isCommandable/isHovered/isSelected)"
provides:
  - "frontend/src/lib/commandChain.ts — pure helpers findVisibleCrcCommanders + findAlliesInMyChain (D-01 / D-18) as the single source of truth for 'which commanders does the logged-in officer see/command'"
  - "Extended tacticalStore.onBattleTick reducer that merges per-tick attackerHierarchy + defenderHierarchy from BattleTickBroadcast (D-21) and initialises fog/succession bookkeeping slots (lastSeenEnemyPositions, activeSuccessionFleetIds, activeFlagshipDestroyedFleetIds) for 14-11 / 14-14 to fill"
  - "BattleMap.tsx 5-layer restructure per UI-SPEC Section A + Section E — background → fog-ghosts → command-range → units → succession-fx, with multi-CRC rendering driven by hierarchy instead of selectedUnit"
  - "computeBattleMapVisibleCommanders pure helper exported from BattleMap.tsx for unit tests under node vitest environment"
  - "Live tacticalStore.hierarchy.test.ts (17 tests) + BattleMap.test.tsx (11 tests) replacing the Wave 0 it.skip scaffolds"
affects: [14-11, 14-12, 14-13, 14-14, 14-15, 14-16, 14-17]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure helper + Konva wrapper split: findVisibleCrcCommanders + computeBattleMapVisibleCommanders live in pure modules so tests can assert decisions without mounting react-konva under vitest's node environment"
    - "Layer-id source-text regression guard: BattleMap.test.tsx reads its own source file and asserts the 5 Konva Layer ids appear in the correct order. Mirrors the same pattern 14-09 used for its CommandRangeCircle animation removal assertion"
    - "Bookkeeping slot pattern for cross-plan reducer hand-off: 14-10 owns the initialisation + preservation semantics of lastSeenEnemyPositions / activeSuccessionFleetIds / activeFlagshipDestroyedFleetIds, 14-11 / 14-14 own the update logic — a single store slice avoids the race hazard of each plan writing a parallel slice"
    - "Hierarchy source-of-truth centralisation: BattleMap pulls currentBattle from useTacticalStore rather than receiving a hierarchy prop from tactical/page.tsx — future fog (14-11) + succession (14-14) layers subscribe to the same slice without BattleMap becoming a prop-relay"

key-files:
  created:
    - frontend/src/lib/commandChain.ts
  modified:
    - frontend/src/stores/tacticalStore.ts
    - frontend/src/stores/tacticalStore.hierarchy.test.ts
    - frontend/src/components/tactical/BattleMap.tsx
    - frontend/src/components/tactical/BattleMap.test.tsx

key-decisions:
  - "[Phase 14-10]: findVisibleCrcCommanders returns a typed VisibleCommander[] (officerId + officerName + isMine + isCommandable + side + flagshipFleetId) rather than a raw officerId[] — BattleMap needs isMine for the gold hint ring and isCommandable for future gating visuals, and inlining the mapping keeps the component rendering pure"
  - "[Phase 14-10]: activeCommander is treated as an alias for fleetCommander in the 'am I the top of the chain' check — after Reinhardt delegates to Kircheis via a DelegateCommand, Kircheis should see every sub-fleet CRC even though hierarchy.fleetCommander still reads 1000. Verified via a dedicated test case"
  - "[Phase 14-10]: onBattleTick uses nullish-coalesce fallback to previous hierarchy when the broadcast carries `null` — a null field on the wire means 'unchanged' (not 'cleared'), matching how 14-01's BattleTickBroadcast.fromEngine omits the hierarchy on ticks where nothing changed for broadcast efficiency"
  - "[Phase 14-10]: activeFlagshipDestroyedFleetIds is pruned by wall-clock millis (Date.now() > expiresAt) rather than tick number — the FX layer (14-14) runs at animation framerate, not tick rate, so a 500ms Konva easing can outlive a single tick boundary and needs its own timing reference"
  - "[Phase 14-10]: clearBattle resets all Phase 14 bookkeeping slots so the next battle starts with a clean slate — 14-11 and 14-14 do not need to add their own cleanup hook, which is one fewer place for a leak across battle switches"
  - "[Phase 14-10]: BattleMap keeps its existing prop signature (units, myOfficerId, selectedUnitId, onSelectUnit, width, height) and pulls the hierarchy from tacticalStore internally — avoids rippling a signature change through tactical/page.tsx while still centralising hierarchy access for the fog / succession layers"
  - "[Phase 14-10]: computeBattleMapVisibleCommanders is exported from BattleMap.tsx for the test file instead of being inlined — BattleMap.test.tsx runs in node environment (react-konva can't mount there without jsdom) so component-level DOM assertions are replaced by pure-helper assertions + source-text regression guards"
  - "[Phase 14-10]: Layer ids are bare string literals (`id=\"command-range\"`) rather than constants — the source-text regression guard uses plain grep-style string.includes() assertions which is both more robust than a react-konva runtime introspection and more honest about what the plan's acceptance criteria actually tests"

patterns-established:
  - "Pure-helper-first tactical UI testing: any hierarchy-derived render decision goes through a pure module (commandChain.ts, BattleMap.tsx's computeBattleMapVisibleCommanders) so vitest can assert in node env without jsdom"
  - "Layer-ordering source-text regression guard: the cheapest way to pin Konva layer order under vitest env=node is `expect(source.indexOf(id1)).toBeLessThan(source.indexOf(id2))` — no Stage mount, no Konva introspection"
  - "Cross-plan reducer bookkeeping slot ownership: the plan that introduces a store slot owns init + preservation; downstream plans own the update logic. Eliminates the race hazard of each plan writing its own slice during parallel waves"
  - "Nullish-coalesce broadcast merge: BattleTickBroadcast fields nullable at the wire level mean 'unchanged'; reducer uses `data.field ?? prev.field ?? null` fallback chain for minimum server bandwidth + maximum frontend state stability"

requirements-completed: [FE-01, FE-03]

# Metrics
duration: ~15 min
completed: 2026-04-09
---

# Phase 14 Plan 14-10: BattleMap layer restructure + tacticalStore hierarchy reducer Summary

**5-layer BattleMap (background → fog-ghosts → command-range → units → succession-fx) rendering multi-CRC from hierarchy instead of selectedUnit, backed by a pure commandChain.ts source-of-truth for FE-01/FE-03/D-01/D-18, plus a tacticalStore.onBattleTick reducer that merges per-tick CommandHierarchyDto snapshots and initialises fog-of-war + succession bookkeeping slots for 14-11 / 14-14 to fill.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-09T11:10:15Z
- **Completed:** 2026-04-09T11:25:00Z (approx)
- **Tasks:** 2 (Task 1 = commandChain.ts + tacticalStore reducer + hierarchy test; Task 2 = BattleMap 5-layer restructure + multi-CRC + test)
- **Source files touched:** 5 (1 created + 4 modified)

## Accomplishments

- **`frontend/src/lib/commandChain.ts` (new, 164 lines).** Two pure functions expose the "which CRCs does the logged-in officer see" / "which allies share my command chain" answers as a single source of truth:
  - `findVisibleCrcCommanders(myOfficerId, hierarchy, units, side)` — returns `VisibleCommander[]` with `{officerId, officerName, isMine, isCommandable, side, flagshipFleetId}`. Fleet commander / active commander sees own ring plus every sub-fleet ring (all commandable); sub-fleet commander sees only their own ring; any other officer sees nothing. Handles null hierarchy and negative officer id gracefully.
  - `findAlliesInMyChain(myOfficerId, hierarchy, units, side)` — returns the alive same-side units that share a command chain with the logged-in officer, aggregating either the full side (for the fleet commander) or just a sub-fleet's `memberFleetIds` (for a sub-fleet commander). 14-11 uses this to aggregate sensor coverage across a command chain per D-18.
- **`frontend/src/stores/tacticalStore.ts` extended with Phase 14 bookkeeping slots.**
  - New exported types: `LastSeenEnemyRecord` (fog ghost snapshot), `FlagshipDestroyedRecord` (flash expiry entry).
  - New state slots: `lastSeenEnemyPositions: Record<number, LastSeenEnemyRecord>`, `activeSuccessionFleetIds: number[]`, `activeFlagshipDestroyedFleetIds: FlagshipDestroyedRecord[]`.
  - `onBattleTick` now merges `data.attackerHierarchy ?? prevBattle.attackerHierarchy ?? null` (and same for defender) per D-21, preserves the fog/succession bookkeeping verbatim across ticks, and prunes expired flash entries by wall-clock millis.
  - `clearBattle` resets all three Phase 14 slots alongside the existing cleanup.
- **`frontend/src/components/tactical/BattleMap.tsx` restructured from 3 layers to 5.**
  - Layer order per UI-SPEC Section E: `background` → `fog-ghosts` → `command-range` → `units` → `succession-fx`, each with an explicit `id=` string literal for the source-text regression guard.
  - CRC layer now multi-renders `visibleCommanders.map((cmd) => <CommandRangeCircle … />)` instead of the pre-14-10 `selectedUnit && selectedUnit.commandRange > 0 && …` single-CRC gate.
  - Hierarchy read from `useTacticalStore((s) => s.currentBattle)` so the fog (14-11) and succession (14-14) layers can subscribe to the same slice without BattleMap becoming a prop-relay.
  - Exported `computeBattleMapVisibleCommanders` pure helper handles side resolution + hierarchy lookup so BattleMap.test.tsx can assert decisions without mounting react-konva under `environment: 'node'`.
  - Existing `BattleMapProps` signature preserved (`units`, `myOfficerId`, `selectedUnitId`, `onSelectUnit`, `width`, `height`) — `tactical/page.tsx` call site unchanged.
  - `selectedUnit` still drives the inner-ring glow on the matching CRC via `isSelected={selectedUnit?.fleetId === cmd.flagshipFleetId}` — clicking a friendly commander still feels like "focus the selected CRC".
- **`frontend/src/stores/tacticalStore.hierarchy.test.ts` (485 lines, 17 cases)** replaces the 4 Wave 0 `it.skip` stubs with real assertions:
  - Store reducer: `onBattleTick` merges attackerHierarchy + defenderHierarchy, falls back to previous hierarchy when broadcast field is null, preserves `lastSeenEnemyPositions`, preserves `activeSuccessionFleetIds`, prunes expired `activeFlagshipDestroyedFleetIds`, initialises bookkeeping slots to empty at store creation, `clearBattle` resets all three slots.
  - `findVisibleCrcCommanders`: empty for unknown officer id, empty for negative id, empty for null hierarchy, fleet commander sees self + all sub-commanders, sub-fleet commander sees only self, active commander (post-delegation) is treated as fleet commander.
  - `findAlliesInMyChain`: fleet commander aggregates all alive side allies (dead units filtered), sub-fleet commander limits to own `memberFleetIds`, plain officer returns empty.
- **`frontend/src/components/tactical/BattleMap.test.tsx` (220 lines, 11 cases)** replaces the 3 Wave 0 `it.skip` stubs with real assertions:
  - Source-text regression guard: all 5 layer ids present, ordered `background` → `fog-ghosts` → `command-range` → `units` → `succession-fx`, the forbidden `selectedUnit && selectedUnit.commandRange > 0` single-CRC gate is absent, `findVisibleCrcCommanders` imported from `@/lib/commandChain`, `visibleCommanders.map` wiring present.
  - `computeBattleMapVisibleCommanders` pure helper: empty for missing officer, empty for officer not in hierarchy, fleet commander sees 3 CRCs when they have 2 sub-commanders, defender officer reads `defenderHierarchy` (side resolution works), sub-fleet commander sees only own CRC, empty when both hierarchies are null.

## Task Commits

**Due to a parallel Wave 3 race, all 5 source files for Plan 14-10 landed under sibling commits rather than a dedicated 14-10 commit.** See "Deviations" section below. The committed state matches the plan's acceptance criteria byte-for-byte.

1. **Task 1 (commandChain.ts + tacticalStore hierarchy reducer + hierarchy test)** — committed under sibling hash `b5c87d84` (labelled `feat(14-09)`) due to sibling executor's broad `git add` behaviour during concurrent wave execution.
2. **Task 2 (BattleMap 5-layer restructure + BattleMap test)** — committed under sibling hash `03e8ef2d` (labelled `docs(14-09)`) for the same reason. Commit `03e8ef2d`'s body explicitly states "Sibling Wave 3 14-10 has already consumed the stable CommandRangeCircleProps API contract and landed the 5-layer BattleMap restructure — coordination via TODO(14-10) marker worked as designed", confirming the attribution issue was recognised at write time.

A dedicated 14-10 docs commit (this SUMMARY.md + STATE.md / ROADMAP.md updates) provides a canonical anchor for the plan.

## Files Created/Modified

### Created

- `frontend/src/lib/commandChain.ts` — 164 lines. `VisibleCommander` interface, `findVisibleCrcCommanders` pure helper, `findAlliesInMyChain` pure helper. Module has no side effects and no store access; every function is a pure `(myOfficerId, hierarchy, units, side) => result` shape so tests can assert decisions in isolation.

### Modified

- `frontend/src/stores/tacticalStore.ts` — +90 lines. Added `BattleSide` import, `LastSeenEnemyRecord` + `FlagshipDestroyedRecord` interfaces, 3 new state slots, initial values, reducer extension for hierarchy merge + bookkeeping preservation + expired-flash pruning, `clearBattle` reset for all three slots.
- `frontend/src/stores/tacticalStore.hierarchy.test.ts` — +485 / –32 lines. Full replacement of 4 Wave 0 `it.skip` stubs with 17 live vitest cases grouped into 3 describe blocks (store hierarchy merge, `findVisibleCrcCommanders`, `findAlliesInMyChain`).
- `frontend/src/components/tactical/BattleMap.tsx` — +120 / –20 lines. Added module-level KDoc block summarising the pre-14-10 → post-14-10 diff, `useTacticalStore` + `findVisibleCrcCommanders` + `BattleSide` imports, exported `computeBattleMapVisibleCommanders` pure helper, 5-layer Stage restructure, multi-CRC render in the command-range layer. The `FogLayer` import + mount inside the `fog-ghosts` layer is a sibling 14-11 injection riding on the same commit (see Deviations).
- `frontend/src/components/tactical/BattleMap.test.tsx` — +220 / –25 lines. Full replacement of 3 Wave 0 `it.skip` stubs with 11 live vitest cases (5 source-text regression + 6 `computeBattleMapVisibleCommanders` pure-helper cases).

## Decisions Made

See frontmatter `key-decisions` for the full 8-item list. Most load-bearing decisions:

1. **`VisibleCommander` typed return shape.** Returning a `{officerId, officerName, isMine, isCommandable, side, flagshipFleetId}` interface from `findVisibleCrcCommanders` rather than a raw `officerId[]` saves BattleMap an O(n) `units.find` per CRC + carries the `isMine` bit needed for the gold hint ring in 14-09's `CommandRangeCircle` without a second hierarchy walk.
2. **`activeCommander` is an alias for `fleetCommander` in the "am I the top" check.** After a Reinhardt-to-Kircheis delegation (14-09), `hierarchy.fleetCommander` still reads `1000` but `hierarchy.activeCommander = 2000`. Treating them as equivalent for the "see all sub-CRCs" branch means delegation works end-to-end without any hierarchy rewrite.
3. **Wall-clock prune for `activeFlagshipDestroyedFleetIds`.** The FX layer (14-14) runs at animation framerate (60fps Konva easing), so the flash lifecycle is measured in `Date.now()` millis, not tick count. Prune by `expiresAt > Date.now()` is the honest implementation.
4. **BattleMap prop signature preserved.** Not routing the hierarchy through `tactical/page.tsx` avoids a signature change ripple — fog (14-11) and succession (14-14) layers will read the same `useTacticalStore` slice directly, not through BattleMap props.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Parallel wave race: source files committed under sibling hashes**

- **Found during:** Task 1 and Task 2 git staging
- **Issue:** Plan 14-10 is running in Wave 3 concurrently with 14-09 and 14-11. The sibling 14-09 executor's `git add` included (or raced against) my `commandChain.ts`, `tacticalStore.ts`, `tacticalStore.hierarchy.test.ts`, `BattleMap.tsx`, and `BattleMap.test.tsx` writes. When I ran my own `git add` + `git commit` for Task 1, the files were already committed under `b5c87d84` (labelled `feat(14-09)`) — the working tree diff was empty because my changes had already landed. Same for Task 2 under `03e8ef2d` (labelled `docs(14-09)`).
- **Fix:** Accepted the sibling attribution and documented the race in this SUMMARY. All 5 files match my intended plan output (verified via acceptance-criteria greps + 28/28 passing tests + `pnpm typecheck` + `pnpm build`). Commit `03e8ef2d`'s body explicitly acknowledges the 14-10 attribution ("Sibling Wave 3 14-10 has already consumed the stable CommandRangeCircleProps API contract and landed the 5-layer BattleMap restructure") so the record is consistent.
- **Files modified:** None additional (work was already in HEAD under sibling commits).
- **Verification:** `git log -1 -- frontend/src/lib/commandChain.ts` → `b5c87d84`; `git log -1 -- frontend/src/components/tactical/BattleMap.tsx` → `03e8ef2d`; all plan acceptance grep checks pass; 28/28 tests pass; `pnpm typecheck` exit 0; `pnpm build` exit 0.

**2. [Rule 3 - Blocking] Plan's suggested `useOfficerStore((s) => s.currentOfficerId ?? -1)` doesn't match the real officerStore shape**

- **Found during:** Task 2 BattleMap restructure
- **Issue:** Plan 14-10 Task 2 Step 2 suggested `const myOfficerId = useOfficerStore((s) => s.currentOfficerId ?? -1);` but `frontend/src/stores/officerStore.ts` exposes `myOfficer: Officer | null` (not `currentOfficerId`). Reading a non-existent field would throw at typecheck time.
- **Fix:** Kept BattleMap's existing `myOfficerId?: number` prop (already passed in by `tactical/page.tsx` as `myOfficer?.id`) and routed it through `computeBattleMapVisibleCommanders(myOfficerId ?? -1, …)`. Zero signature change on the call site, zero officerStore read inside BattleMap, same semantics as the plan intended.
- **Files modified:** `frontend/src/components/tactical/BattleMap.tsx` (committed under `03e8ef2d`)
- **Verification:** `pnpm typecheck` exit 0; BattleMap.test.tsx passes all 11 cases including 6 `computeBattleMapVisibleCommanders` unit tests with various officer id inputs.

**3. [Rule 3 - Blocking] Plan's BattleMap.test.tsx suggested react-testing-library mounts that fail under vitest `environment: 'node'`**

- **Found during:** Task 2 BattleMap test file
- **Issue:** Plan 14-10 Task 2 Step 4 suggested `render(<BattleMap width={1000} height={1000} />)` via `@testing-library/react`, but BattleMap renders react-konva which requires a DOM, and vitest runs in `environment: 'node'` by default. The plan itself anticipated this with a hint: "Testing Konva layer contents via @testing-library/react is fragile. Prefer to extract a pure `computeVisibleCommanders(battle, myOfficerId)` function (which already exists in commandChain.ts) and unit-test THAT."
- **Fix:** Exported `computeBattleMapVisibleCommanders` as a pure helper from BattleMap.tsx and wrote 6 pure-helper test cases + 5 source-text regression guard cases (read BattleMap.tsx as a string, assert layer ids appear in correct order, assert forbidden old gate is gone, assert expected imports present). No react-konva mount, no jsdom requirement.
- **Files modified:** `frontend/src/components/tactical/BattleMap.tsx`, `frontend/src/components/tactical/BattleMap.test.tsx` (committed under `03e8ef2d`)
- **Verification:** `pnpm test --run src/components/tactical/BattleMap.test.tsx` → 11 passed, 0 skipped.

**4. [Rule 3 - Scope-correct] 14-11 sibling injected `FogLayer` import into my BattleMap.tsx during Wave 3**

- **Found during:** Task 2 git staging
- **Issue:** After my initial BattleMap.tsx Write landed a pure-placeholder `<Layer listening={false} id="fog-ghosts">{/* 14-11 fills */}</Layer>`, sibling 14-11's executor wrote back an injection that replaced the placeholder with `<FogLayer myOfficerId={…} scaleX={…} scaleY={…} />` plus `import { FogLayer } from './FogLayer';` at the top. This is exactly the wave coordination the plan's `<wave_context>` block described: "14-11 will plug FogLayer into your 'fog-ghosts' layer slot."
- **Fix:** Accepted the injection because (a) it's explicit Wave 3 coordination, (b) `FogLayer.tsx` + `EnemyGhostIcon.tsx` exist as untracked files that `pnpm typecheck` resolves successfully, and (c) reverting it would break 14-11's delivery contract. The file committed under `03e8ef2d` carries the FogLayer injection; my SUMMARY owns the layer-ordering + hierarchy pipeline; 14-11's SUMMARY will own the fog component wiring.
- **Files modified:** None by me (sibling injection landed before my re-stage).
- **Verification:** `pnpm typecheck` exit 0; `pnpm build` exit 0 (Next.js static prerender completed through to the route listing legend).

**5. [Rule 2 - Missing Critical] Plan's `CommandRangeCircle` prop names didn't match 14-09's final rewrite (plan wrote `x`/`y`/`radius`, 14-09 committed `cx`/`cy`/`currentRadius`)**

- **Found during:** Task 2 BattleMap multi-CRC rendering
- **Issue:** Plan 14-10 Task 2 Step 3 showed the multi-CRC call as `<CommandRangeCircle cx={cx} cy={cy} currentRadius={currentRadius} maxRadius={maxRadius} side={cmd.side} isMine={cmd.isMine} isCommandable={cmd.isCommandable} />` — which happens to be the correct final 14-09 prop shape. But the component had a separate pre-14-09 shape at Wave 0 time, so the plan text was prospective on 14-09's rewrite landing first. Since 14-09 committed before my Task 2, the call site matches the final rewrite and `isSelected` is additionally wired for the focused-CRC glow.
- **Fix:** Added `isSelected={selectedUnit?.fleetId === cmd.flagshipFleetId}` alongside the other props so clicking a friendly commander lights up that specific CRC. Not a strict "missing critical" — more "plan optional field I added for UX parity with the old single-CRC selected glow".
- **Files modified:** `frontend/src/components/tactical/BattleMap.tsx`
- **Verification:** 14-09's `CommandRangeCircleProps.isSelected?: boolean` is already declared in the rewrite; no new deps; typecheck clean.

---

**Total deviations:** 5 auto-fixed (3 Rule 3 - Blocking, 1 Rule 3 - Scope-correct, 1 Rule 2 - Missing Critical)

**Impact on plan:** None — every deviation preserved the plan's intent. The 2 attribution deviations (1 + 4) are wave-race artifacts that do not affect code correctness; the 2 test-infrastructure deviations (2 + 3) are scope-correct alternatives to plan text that could not compile against the real codebase; the 1 prop-parity deviation (5) is a UX bonus.

## Issues Encountered

- **Parallel wave race on shared files.** Three Wave 3 siblings (14-09, 14-10, 14-11) all touched `frontend/src/components/tactical/BattleMap.tsx` and two of them (14-09, 14-10) touched `frontend/src/stores/tacticalStore.ts`. Broad `git add` calls from sibling executors picked up my files and committed them under non-14-10 hashes. This matches the guidance in `<parallel_execution>` about using `--no-verify`, but the broader issue is that file-path-based staging during parallel waves is fundamentally racy. Deferred consideration: either scope files to agents more strictly, or require `git add -p`-style per-hunk staging.
- **No dedicated 14-10 source commit.** The closest I can offer as a canonical 14-10 anchor is this SUMMARY.md + the state artifacts, which will be committed under a `docs(14-10)` message.

## User Setup Required

None — plan is pure frontend code with no service configuration changes.

## Next Phase Readiness

- **14-11 (fog-of-war FogLayer + EnemyGhostIcon)** — **already landed in parallel.** 14-11's `fogOfWar.ts` helpers + `tacticalStore.fog.test.ts` are in commit `5f2948aa`; `FogLayer.tsx` + `EnemyGhostIcon.tsx` are in the working tree and have been injected into BattleMap.tsx's `fog-ghosts` layer. 14-11's own SUMMARY will own the component wiring; mine owns the layer slot + `lastSeenEnemyPositions` store bookkeeping.
- **14-12 (sub-fleet drawer)** — unblocked. The drawer reads `currentBattle.{attacker,defender}Hierarchy.subFleets` which is now merged per-tick by `onBattleTick`.
- **14-13 (canCommandUnit gating)** — unblocked. `findAlliesInMyChain` is the pure helper for the "is this unit in my chain?" check. 14-13 will wrap it in a `canCommandUnit(fleetId)` predicate + PositionCard gate.
- **14-14 (succession FX)** — unblocked. `activeSuccessionFleetIds` + `activeFlagshipDestroyedFleetIds` store slots are initialised + preserved by 14-10; 14-14 will plug the update logic into `onBattleTick` via event-type switching on `BattleTickEvent.type === 'FLAGSHIP_DESTROYED' / 'SUCCESSION_STARTED' / 'SUCCESSION_COMPLETED'`. The `succession-fx` Konva layer is ready to receive `<FlagshipFlash />` / `<SuccessionRing />` instances.
- **14-15 (NPC/online markers)** — unblocked. `TacticalUnit.isOnline` + `isNpc` already on the type per 14-06; TacticalUnitIcon in the `units` layer is the mount point.
- **14-16 (operation overlay)** — unblocked. Galaxy map (not BattleMap) is the mount point per UI-SPEC Section F, so no BattleMap layer touches needed.
- **14-17 (command proposal panel)** — unblocked. Uses commandChain.ts for the "which superior should this proposal route to" lookup.

### Blockers / concerns

- **Attribution accuracy.** Anyone running `git blame` on `BattleMap.tsx` or `commandChain.ts` will see 14-09 hashes even though the semantic owner is 14-10. This SUMMARY is the authoritative remediation. Consider a future cleanup commit that does a `git mv` / `git rebase` exercise, but that is out of scope for 14-10.

## Known Stubs

- `Layer id="fog-ghosts"` contents are owned by 14-11 (already injected in working tree as of this writing).
- `Layer id="succession-fx"` contents are owned by 14-14 (empty placeholder with an inline `{/* intentional: filled in 14-14 */}` comment pointing to the plan).

Neither is a "stub that prevents the plan goal" — both are explicit hand-off points per the wave coordination contract. 14-10's goal is the layer restructure + hierarchy pipeline + multi-CRC, all of which are live.

---

*Phase: 14-frontend-integration*
*Plan: 10*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `frontend/src/lib/commandChain.ts` exists on disk (verified via `ls -la`)
- [x] `grep -c "export function findVisibleCrcCommanders" frontend/src/lib/commandChain.ts` → 1
- [x] `grep -c "export function findAlliesInMyChain" frontend/src/lib/commandChain.ts` → 1
- [x] `grep -c 'attackerHierarchy' frontend/src/stores/tacticalStore.ts` → 4 (≥ 2)
- [x] `grep -c 'lastSeenEnemyPositions' frontend/src/stores/tacticalStore.ts` → 6 (≥ 2)
- [x] `grep -c 'activeSuccessionFleetIds' frontend/src/stores/tacticalStore.ts` → 4 (≥ 2)
- [x] `grep -c 'activeFlagshipDestroyedFleetIds' frontend/src/stores/tacticalStore.ts` → 5 (≥ 2)
- [x] `grep -c 'id="fog-ghosts"' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'id="command-range"' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'id="succession-fx"' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'id="units"' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'id="background"' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'visibleCommanders.map' frontend/src/components/tactical/BattleMap.tsx` → 1
- [x] `grep -c 'findVisibleCrcCommanders' frontend/src/components/tactical/BattleMap.tsx` → 5
- [x] `! grep -q 'selectedUnit && selectedUnit\.commandRange' frontend/src/components/tactical/BattleMap.tsx` — forbidden gate absent
- [x] `pnpm typecheck` exit 0
- [x] `pnpm test --run src/components/tactical/BattleMap.test.tsx src/stores/tacticalStore.hierarchy.test.ts` → 28/28 pass (11 + 17, 0 skipped, 0 failed)
- [x] `pnpm build` exit 0 (Next.js static prerender reached route listing legend without errors)
- [x] Commit `b5c87d84` exists and contains `commandChain.ts` + `tacticalStore.ts` + `tacticalStore.hierarchy.test.ts` (verified via `git log -1 --pretty=format:'%h %s' -- <file>`)
- [x] Commit `03e8ef2d` exists and contains `BattleMap.tsx` + `BattleMap.test.tsx` (verified via same command)
