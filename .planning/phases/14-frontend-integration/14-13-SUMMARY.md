---
phase: 14-frontend-integration
plan: 13
subsystem: ui
tags: [react, dnd-kit, tactical, sub-fleet, drawer, command-dispatch, websocket, korean-ui]

# Dependency graph
requires:
  - phase: 14-frontend-integration
    provides: "TacticalBattleDto carries commandHierarchies, SubFleetDto (14-01/14-06); @dnd-kit/core + @dnd-kit/utilities installed (14-07); commandChain helpers findVisibleCrcCommanders (14-10); canReassignUnit pure gating helper + SubFleetUnitChip (14-12)"
  - phase: 09
    provides: "AssignSubFleet / ReassignUnit TacticalCommand backend handlers (CMD-05)"
provides:
  - "SubFleetAssignmentDrawer component — 10 bucket drop-zone UI for sub-fleet assignment"
  - "createDragEndHandler pure helper — dispatches AssignSubFleet / ReassignUnit via publishWebSocket"
  - "Drawer test suite (24 tests) covering Korean copy, dnd-kit-only regression guard, and pure handler routing"
affects: [14-14 (FE-03 gating drives chip disabled state), 14-17 (drawer may need composition with F1 overlay), 14-18 (e2e + validation)]

# Tech tracking
tech-stack:
  added: []  # dnd-kit installed earlier in 14-07
  patterns:
    - "Pure drop handler exported alongside component for node-env vitest testing (matches 14-09/14-10 pattern)"
    - "Source-text regression for Korean copy + library lock (14-10 precedent)"
    - "DndContext owns onDragEnd, closestCenter collision strategy, PointerSensor(distance=4) + KeyboardSensor"

key-files:
  created:
    - "frontend/src/components/tactical/SubFleetAssignmentDrawer.tsx"
  modified:
    - "frontend/src/components/tactical/SubFleetAssignmentDrawer.test.tsx (Wave 0 stubs → 24 live tests)"
    - ".planning/phases/14-frontend-integration/deferred-items.md (appended 14-13 out-of-scope findings + 14-16 entries resolved)"

key-decisions:
  - "Collapse PAUSED phase → ACTIVE for gating — TacticalBattle.phase includes PAUSED but canReassignUnit helper only enumerates PREPARING/ACTIVE/ENDED; same CMD-05 rules apply in PAUSED as ACTIVE so we collapse at the drawer edge."
  - "'direct' (전계 사령관 직할) bucket dispatches ReassignUnit with subFleetCommanderId=null — matches backend ReassignUnit contract where null means 'return to fleet commander direct control'."
  - "Empty placeholder slots (slot-0..slot-7) are drop no-ops — player must first have a commander occupy the slot. Prevents phantom AssignSubFleet commands to non-existent commanders."
  - "createDragEndHandler exported as a pure function taking (sessionId, myOfficerId) so it can be unit-tested with a mock DragEndEvent under node-env vitest (no DOM, no component mount)."
  - "Do NOT mount the drawer component in tests — vitest environment is 'node' and dnd-kit depends on pointer/keyboard APIs. Follow 14-09/14-10 pattern: pure helper unit tests + source-text regression for rendering contract."

patterns-established:
  - "Pure drop-handler export: `createDragEndHandler(sessionId, officerId): (event: DragEndEvent) => void` — enables isolated testing of WebSocket dispatch logic without component mounting."
  - "Source-text regression for Korean UI copy: `expect(source).toContain('분함대 편성')` — catches copy drift without runtime rendering."

requirements-completed: [FE-02]

# Metrics
duration: 8min
completed: 2026-04-09
---

# Phase 14 Plan 13: Sub-fleet assignment drawer component (FE-02) Summary

**SubFleetAssignmentDrawer with @dnd-kit DndContext, 10 bucket drop-zones, and pure createDragEndHandler dispatching AssignSubFleet / ReassignUnit via publishWebSocket.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-09T11:38:25Z
- **Completed:** 2026-04-09T11:46:48Z
- **Tasks:** 1 (TDD RED → GREEN)
- **Files created:** 1 (drawer)
- **Files modified:** 2 (test + deferred-items.md)

## Accomplishments

- **Drawer shell**: `SubFleetAssignmentDrawer.tsx` renders a `ResponsiveSheet` containing a `DndContext` with 10 bucket drop-zones. Renders `null` gracefully when no battle is active or the logged-in officer is not on the map.
- **Bucket layout**: Dynamic `buildBuckets(hierarchy, sideUnits)` helper produces up to 8 commander buckets (real sub-fleets up to 8 + empty slot placeholders), followed by the `direct` (전계 사령관 직할) bucket and the `unassigned` (미배정 유닛) pool. Korean labels: 부사령관, 참모장, 참모 1-6, 전계, 미배정.
- **Pure drop handler**: `createDragEndHandler(sessionId, myOfficerId)` exported as a named export. Returns a pure `(DragEndEvent) => void` that:
  - No-ops when `over` is null, `active.data.current` is missing, or the target is an empty placeholder slot.
  - Dispatches `AssignSubFleet` with `subFleetCommanderId={N}` when dropped on a `sub-{N}` bucket.
  - Dispatches `ReassignUnit` with `subFleetCommanderId=null` when dropped on `unassigned` or `direct` bucket.
  - Fires a Korean sonner acknowledgement toast after publish.
- **Gating integration**: Per-chip gating consumes `canReassignUnit` helper from 14-12. Looks up each unit's current sub-fleet commander via `commanderUnitFor(unit)` and passes `disabled={!gate.allowed}` + `disabledReason={gate.message}` into `SubFleetUnitChip`.
- **Phase collapse**: `TacticalBattle.phase === 'PAUSED'` is collapsed to `'ACTIVE'` at the drawer edge so the CMD-05 rules apply consistently in paused battles.
- **Test suite**: `SubFleetAssignmentDrawer.test.tsx` flipped from 4 Wave 0 `it.skip` stubs to 24 live passing tests covering:
  - Korean copy regression for every UI-SPEC Section B label (9 tests).
  - dnd-kit-only regression guard (4 tests, forbidden `react-dnd` import).
  - Command dispatch source-text regression (4 tests).
  - Pure `createDragEndHandler` behavior for 7 DragEndEvent permutations including AssignSubFleet routing, ReassignUnit routing, "direct" bucket mapping, placeholder-slot no-op, missing over, and missing active.data.

## Task Commits

Each step was committed atomically (--no-verify per parallel wave):

1. **Task 1 RED — failing drawer tests** → `bb3ca278` (test)
2. **Task 1 GREEN — drawer + handler + deferred items** → `163f04ce` (feat)

Total: 2 commits for the TDD RED → GREEN cycle. No refactor step needed (GREEN passed all assertions on first run).

## Files Created/Modified

- **Created** `frontend/src/components/tactical/SubFleetAssignmentDrawer.tsx` (307 lines) — drawer shell + DndContext + buildBuckets + createDragEndHandler + Bucket droppable subcomponent.
- **Modified** `frontend/src/components/tactical/SubFleetAssignmentDrawer.test.tsx` (225 insertions, 20 deletions) — replaced Wave 0 stubs with live test suite.
- **Modified** `.planning/phases/14-frontend-integration/deferred-items.md` (41 insertions) — appended 14-13 out-of-scope findings and marked 14-16's SubFleetAssignmentDrawer/UnitChip entries as resolved.

## Decisions Made

- **PAUSED → ACTIVE collapse**: `canReassignUnit` from 14-12 enumerates `'PREPARING' | 'ACTIVE' | 'ENDED'` but `TacticalBattle.phase` includes `'PAUSED'`. Paused battles share CMD-05 gating with ACTIVE (player shouldn't get free reassignment during a pause), so the drawer collapses the phase at the boundary.
- **'direct' bucket → ReassignUnit(null)**: Dropping a unit into the fleet commander's 전계 (direct control) bucket is semantically "return to fleet commander" — the backend `ReassignUnit` command uses `subFleetCommanderId=null` to mean "direct fleet commander control", so the drawer uses the same null sentinel for both `unassigned` and `direct` targets.
- **Empty slot no-op**: Placeholder slots (`slot-0`..`slot-7`) for commander positions not yet filled are drop no-ops. This prevents the drawer from dispatching `AssignSubFleet` to commander IDs that don't exist yet. The player must first have a commander assigned to the slot before they can drag units into it.
- **Pure handler export for testability**: `createDragEndHandler` is exported as a named function that takes `(sessionId, myOfficerId)` and returns the bound `(DragEndEvent) => void`. This lets vitest test the dispatch logic without mounting the component (which would require jsdom + dnd-kit pointer shims the project doesn't have).
- **No component mounting**: The vitest env is `'node'` and dnd-kit's DndContext needs window/pointer APIs not available in node. Following the 14-09 / 14-10 precedent, tests cover the contract via (1) pure helper testing and (2) source-text regression for Korean copy and import guards.

## Deviations from Plan

**1. [Rule 3 - Blocking] Stub subFleetDragGating.ts + SubFleetUnitChip.tsx coordination with sibling 14-12**
- **Found during:** Task 1 (drawer implementation)
- **Issue:** 14-12 owns `frontend/src/lib/subFleetDragGating.ts` and `SubFleetUnitChip.tsx` but was running in parallel wave 4 and had not committed its GREEN at the time I started writing the drawer. My drawer imports from both files; without them, tests and typecheck would fail.
- **Fix:** Created minimal working versions of both files so the drawer could compile. Before my commit landed, 14-12 committed the canonical `subFleetDragGating.ts` (commit `5bd4cb6f`) and kept `SubFleetUnitChip.tsx` intact (I had already created a valid initial version that 14-12 subsequently patched to fix a TS2783 aria-disabled issue). The drawer now consumes the canonical 14-12 exports.
- **Files modified (temporary stubs):** frontend/src/lib/subFleetDragGating.ts (now owned by 14-12), frontend/src/components/tactical/SubFleetUnitChip.tsx (now owned by 14-12)
- **Verification:** Both sibling files land in `5bd4cb6f feat(14-12): add canReassignUnit helper and SubFleetUnitChip`. My drawer commit `163f04ce` only touches `SubFleetAssignmentDrawer.tsx` + test + deferred-items.
- **Committed in:** `163f04ce` (drawer only); sibling files committed in `5bd4cb6f`

**2. [Rule 2 - Missing critical] "direct" bucket drop target added to handler**
- **Found during:** Task 1 (testing createDragEndHandler routing)
- **Issue:** The plan's task specification only enumerates `unassigned` and `sub-{N}` drop targets for the pure handler. However, the plan ALSO specifies a `direct` (전계 사령관 직할) bucket in the bucket layout. Without routing for `direct`, dropping a unit on the fleet commander's direct-control bucket would silently no-op.
- **Fix:** Treat `direct` the same as `unassigned` — dispatch `ReassignUnit` with `subFleetCommanderId=null`. This matches backend semantics (null = fleet commander direct control).
- **Files modified:** SubFleetAssignmentDrawer.tsx (createDragEndHandler)
- **Verification:** Added a dedicated test ("dispatches AssignSubFleet with subFleetCommanderId=null when dropped on 'direct' bucket (전계)") — passing.
- **Committed in:** `163f04ce`

**3. [Rule 2 - Missing critical] Empty placeholder slot drop no-op**
- **Found during:** Task 1 (bucket layout)
- **Issue:** The plan pads up to 8 commander slots with empty labels like `'부사령관'`, `'참모장'`, etc. Without an explicit no-op in the drop handler, dragging onto an empty slot would fall through to the `sub-` prefix check, fail to parse a valid Number, and either dispatch a garbage command or throw.
- **Fix:** In createDragEndHandler, explicitly treat any target ID that starts with `slot-` (or fails to match the known prefixes) as a no-op. Verified with a dedicated test.
- **Files modified:** SubFleetAssignmentDrawer.tsx (createDragEndHandler)
- **Verification:** New test "is a no-op when dropped on an empty placeholder slot (slot-N)" passing; `expect(publishWebSocket).not.toHaveBeenCalled()`.
- **Committed in:** `163f04ce`

---

**Total deviations:** 3 auto-fixed (1 blocking coordination, 2 missing critical)
**Impact on plan:** All deviations were small and necessary. Deviation 1 is a pure parallel-wave coordination artifact that 14-12's landing resolves cleanly. Deviations 2 and 3 tighten the drop handler's contract to match the bucket layout the plan itself specifies. No scope creep.

## Issues Encountered

- **Pre-existing Vitest failures (out of scope):** 12 tests fail in 5 files, none in 14-13 scope. Breakdown logged to `deferred-items.md`:
  - 7 × `command-execution-panel.*.test.tsx` typecheck + runtime failures → owned by 14-14 (selectedUnit prop rewire)
  - 1 × `InfoPanel.tsx` TS2339 `StarSystem.name` → owned by 14-16 (status markers)
  - 4 × legacy 삼국지 → LOGH test format (record-zone, game-dashboard, command-select-form) → pre-existing since 14-05
- **Sibling wave interleaving:** 14-12 shipped its canonical `subFleetDragGating.ts` + `SubFleetUnitChip.tsx` mid-execution. No merge conflict — my file contents matched the 14-12 canonical version closely enough that the sibling wave's patch (TS2783 aria-disabled fix on the chip) applied cleanly.

## Self-Check: PASSED

### File existence

- `frontend/src/components/tactical/SubFleetAssignmentDrawer.tsx` — FOUND
- `frontend/src/components/tactical/SubFleetUnitChip.tsx` — FOUND (owned by 14-12, used by 14-13)
- `frontend/src/components/tactical/SubFleetAssignmentDrawer.test.tsx` — FOUND (24 live tests)
- `frontend/src/lib/subFleetDragGating.ts` — FOUND (owned by 14-12)

### Commit existence

- `bb3ca278` (test RED) — FOUND in git log
- `163f04ce` (feat GREEN) — FOUND in git log

### Acceptance criteria

- `grep "분함대 편성" SubFleetAssignmentDrawer.tsx` → 1 match — PASS
- `grep "부사령관" SubFleetAssignmentDrawer.tsx` → 3 matches — PASS
- `grep "참모장" SubFleetAssignmentDrawer.tsx` → 3 matches — PASS
- `grep "전계" SubFleetAssignmentDrawer.tsx` → 3 matches — PASS
- `grep "미배정" SubFleetAssignmentDrawer.tsx` → 4 matches — PASS
- `grep "useDraggable" SubFleetUnitChip.tsx` → 4 matches — PASS
- `grep "useDroppable" SubFleetAssignmentDrawer.tsx` → 2 matches — PASS
- `grep "createDragEndHandler" SubFleetAssignmentDrawer.tsx` → 2 matches (export + use) — PASS
- `grep "publishWebSocket" SubFleetAssignmentDrawer.tsx` → 3 matches — PASS
- `grep "AssignSubFleet" SubFleetAssignmentDrawer.tsx` → 6 matches — PASS
- `grep "ReassignUnit" SubFleetAssignmentDrawer.tsx` → 8 matches — PASS
- `! grep "react-dnd" SubFleetAssignmentDrawer.tsx` (D-05 guard) → 0 matches — PASS
- `pnpm test --run SubFleetAssignmentDrawer` exit 0 → 24/24 drawer + 14/14 gating = 38/38 PASS
- `pnpm typecheck` in 14-13 scope files → 0 errors — PASS

## Next Phase Readiness

- 14-13 ships the drawer UI surface that FE-02 requires; players can now visually assign fleets to sub-commanders during PREPARING and (subject to CMD-05 gating) during ACTIVE.
- 14-14 (FE-03 command gating rewrite) depends on nothing from 14-13 — can continue in parallel.
- 14-17 (F1 operation overlay) will eventually want to compose the drawer with the overlay but no shared state.
- 14-18 (e2e + validation) will exercise the drawer via Playwright — the `data-testid` attributes (`bucket-{id}`, `sub-fleet-unit-chip-{fleetId}`) are in place for targeting.

---
*Phase: 14-frontend-integration*
*Plan: 13*
*Completed: 2026-04-09*
