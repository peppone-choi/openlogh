---
phase: 14-frontend-integration
plan: 16
subsystem: ui
tags: [react, konva, tactical, status-markers, npc, mission-objective, d-35, d-36, d-37]

# Dependency graph
requires:
  - phase: 14-frontend-integration
    provides: "14-01 (TacticalUnitDto.isOnline/isNpc/missionObjective), 14-06 (TacticalUnit TS type with Phase 14 fields), 14-10 (BattleMap 5-layer restructure)"
provides:
  - "TacticalUnitIcon ● / ○ / 🤖 status markers (D-35) driven by unit.isOnline / unit.isNpc"
  - "computeStatusMarker() pure helper exporting shape + color + tooltip decisions for isolated testing"
  - "InfoPanel NPC mission objective rows (D-36) gated on selectedUnit.isNpc && selectedUnit.missionObjective"
  - "resolveMissionObjectiveLabel() pure helper mapping CONQUEST/DEFENSE/SWEEP → 점령/방어/소탕 with forward-compat fallback"
  - "InfoPanel selectedUnit prop — surfaces context for non-myOfficer selections"
  - "BattleMap dashed NPC mission target line (D-37) with clampMissionLineEnd viewport clip helper"
  - "Optional targetStarSystemId?: number field on TacticalUnit (scaffolded for future backend wiring)"
affects: [14-17, 14-18, v2.2-strategic-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Source-text regression guards + pure-helper unit tests under vitest env=node (14-09 pattern reused)"
    - "Status marker variant enum with NPC > offline > online priority dispatch"
    - "Parametric line clipping for off-viewport target hints"
    - "Append-only narrow edits in shared files to coexist with parallel Wave 4 sibling (14-14)"

key-files:
  created:
    - "frontend/src/components/tactical/TacticalUnitIcon.test.tsx"
    - "frontend/src/components/tactical/InfoPanel.test.tsx"
    - "frontend/src/components/tactical/BattleMap.missionLine.test.tsx"
  modified:
    - "frontend/src/components/tactical/TacticalUnitIcon.tsx"
    - "frontend/src/components/tactical/InfoPanel.tsx"
    - "frontend/src/components/tactical/BattleMap.tsx"
    - "frontend/src/types/tactical.ts"
    - "frontend/src/app/(game)/tactical/page.tsx"
    - ".planning/phases/14-frontend-integration/deferred-items.md"

key-decisions:
  - "Status marker variant priority: NPC > offline > online (D-35) — NPC takes priority so mixed online-NPC players still read as AI-controlled"
  - "Marker reads isOnline/isNpc from unit prop (not separate props) — matches the rest of the icon where every flag flows through the unit DTO"
  - "Main icon opacity remains gated on isAlive only — D-35 explicitly reserves opacity for destruction signalling, NOT online state"
  - "targetStarSystemId added as optional forward-compat field on TacticalUnit — backend DTO does not yet surface it, so the 'target system' row and dashed mission line are inactive by default (deferred for future backend plan)"
  - "Mission line rendered as first child of units Layer (not a new 6th layer) to stay narrow and coexist with 14-14/14-15 parallel siblings"
  - "clampMissionLineEnd uses parametric ray clipping so off-viewport targets produce a 'target is that way' hint anchored at the viewport edge instead of disappearing"
  - "resolveMissionObjectiveLabel falls back to raw enum string for unknown values — forward-compatible if backend adds new objectives"
  - "Extended InfoPanel with selectedUnit prop rather than deriving from myOfficerId — lets non-player selection surface NPC context"

patterns-established:
  - "Pure-helper pattern for Konva visual decisions: computeStatusMarker + resolveMissionObjectiveLabel + clampMissionLineEnd all exported from their component file for vitest env=node assertability"
  - "Source-text regression guards assert Korean copy + hex literals + DTO field reads without mounting react-konva"
  - "Parallel-wave narrow-edit pattern: separate test files (e.g. BattleMap.missionLine.test.tsx) to avoid merge conflicts with sibling plans touching the same component"

requirements-completed: [FE-03]

# Metrics
duration: 20min
completed: 2026-04-09
---

# Phase 14 Plan 14-16: Unit status markers + NPC mission objective Summary

**TacticalUnitIcon online/offline/NPC glyph markers, InfoPanel NPC mission objective rows, and BattleMap dashed mission target line scaffolding — all three D-35/D-36/D-37 frontend contracts satisfied.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-09T11:38:44Z
- **Completed:** 2026-04-09T11:59:00Z (approx — wall-clock via `git log` author-date on 50dcfc82)
- **Tasks:** 2
- **Files modified:** 9 (3 created, 6 modified)

## Accomplishments

- **D-35 — Status markers on every TacticalUnitIcon.** Online = filled green disc #10b981, offline = hollow gray ring #7a8599, NPC = 🤖 glyph #a78bfa. Shape is the primary discriminator so a deuteranope user reads the marker correctly without the color. NPC takes priority over online/offline so a mixed online-NPC player still reads as AI-controlled.
- **D-36 — InfoPanel NPC mission objective rows.** When an NPC unit with a populated `missionObjective` is selected, the panel appends "현재 목적: {missionLabel}" and (if `targetStarSystemId` is populated) "목표: {systemName}" rows beneath the standard battle metadata. Non-NPC selection keeps the panel compact.
- **D-37 — BattleMap dashed NPC mission target line.** Rendered as the first child of the units Layer with the NPC color #a78bfa, dash `[5, 5]`, strokeWidth 1, `listening={false}`. Gated on `isNpc && missionObjective && targetStarSystemId` — inactive by default because the backend does not yet surface `targetStarSystemId` (scaffolded for a future backend plan to flip on).
- **Three pure helpers exported for testing** — `computeStatusMarker` (TacticalUnitIcon), `resolveMissionObjectiveLabel` (InfoPanel), `clampMissionLineEnd` (BattleMap) — all asserted under vitest env=node without mounting react-konva, continuing the 14-09 pure-helper + source-text pattern.
- **45 tests passing** across 4 test files (11 TacticalUnitIcon + 13 InfoPanel + 10 BattleMap.missionLine + 11 existing BattleMap).
- **Coexistence with sibling parallel plans** (14-14 command-gating, 14-15 succession FX) — all edits were narrow append-only slices, no merge conflicts, sibling code landed cleanly alongside 14-16 with zero overlap in the same line ranges.

## Task Commits

Each task was committed atomically:

1. **Task 1: TacticalUnitIcon status marker + tooltip (TDD)** — `57939d6c` (feat)
2. **Task 2: InfoPanel NPC mission objective rows + BattleMap dashed target line** — `50dcfc82` (feat)

**Plan metadata:** _pending — added in final commit alongside STATE.md + ROADMAP.md update_

## Files Created/Modified

### Created

- `frontend/src/components/tactical/TacticalUnitIcon.test.tsx` — 11 tests for computeStatusMarker + source-text regression guards
- `frontend/src/components/tactical/InfoPanel.test.tsx` — 13 tests for resolveMissionObjectiveLabel + Korean copy + DTO reads
- `frontend/src/components/tactical/BattleMap.missionLine.test.tsx` — 10 tests for clampMissionLineEnd parametric clip + source-text dashed-line guard

### Modified

- `frontend/src/components/tactical/TacticalUnitIcon.tsx` — added computeStatusMarker + STATUS_MARKER_COLOR_* constants + marker render block with NPC-priority dispatch
- `frontend/src/components/tactical/InfoPanel.tsx` — added `selectedUnit` prop, `resolveMissionObjectiveLabel` helper, NPC mission objective rows with `useGalaxyStore.getSystem` for target system name resolution
- `frontend/src/components/tactical/BattleMap.tsx` — added `clampMissionLineEnd` helper, `missionLine` useMemo gated on NPC + objective + target system, dashed Line render as first child of units Layer
- `frontend/src/types/tactical.ts` — added optional `targetStarSystemId?: number | null` on TacticalUnit with deferred-backend-plan comment
- `frontend/src/app/(game)/tactical/page.tsx` — wired `selectedUnit` lookup into InfoPanel props
- `.planning/phases/14-frontend-integration/deferred-items.md` — logged out-of-scope 14-07 sibling typecheck errors (SubFleetAssignmentDrawer missing file + SubFleetUnitChip duplicate aria-disabled); both later fixed by sibling waves before 14-16 final verify

## Known Stubs

**None introduced by 14-16 itself**, but one field is intentionally inactive pending backend wiring:

- **`TacticalUnit.targetStarSystemId` — optional forward-compat field.** The backend `TacticalUnitDto` does NOT currently surface this field, so in production the "목표: {systemName}" row in InfoPanel and the dashed mission line on BattleMap never render. The scaffolding is in place so a future backend plan can enable both visuals by wiring `targetStarSystemId: Long?` through `TacticalBattleService.toUnitDto`. Documented inline in `frontend/src/types/tactical.ts` and tracked as a plan-completable deferred item — not a stub blocking 14-16's primary goal (D-35/D-36/D-37 contract satisfaction), since the objective enum label row does render correctly from the existing `missionObjective` field.

## Decisions Made

- **Marker priority: NPC > offline > online** (D-35). A mixed online-NPC player still reads as AI-controlled so players don't accidentally mistake the unit for a live commander.
- **Marker reads from unit prop, not separate props.** Matches the rest of the icon where every flag flows through the unit DTO. Avoids a third set of props cluttering the call site.
- **Main icon opacity unchanged.** D-35 explicitly reserves opacity for destruction signalling; gating opacity on isOnline would stomp the isAlive semantics.
- **targetStarSystemId added as optional forward-compat.** Minimal scaffolding in TacticalUnit so InfoPanel + BattleMap can flip on target-system visuals as soon as the backend wires the field through, with no frontend rewrite needed.
- **Mission line inside units Layer, not a new 6th layer.** Keeping narrow edits in the file so 14-14/14-15 parallel siblings don't collide. The Line is the first child of the Layer so it sits beneath icon nodes, and `listening={false}` prevents click interception.
- **Parametric ray clip for off-viewport targets.** Produces a stable "target is that way" hint anchored at the viewport edge when the target system is outside the tactical field — much better UX than silently omitting the line or drawing into empty space.
- **resolveMissionObjectiveLabel forward-compat fallback.** Returns the raw enum string if an unknown value arrives — lets the backend add new OperationObjective values without breaking the frontend.
- **Separate test files for BattleMap and InfoPanel mission-line work** (not extending existing BattleMap.test.tsx). Avoids merge races with 14-14/14-15 touching the same existing test file.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing DTO field] Added optional `targetStarSystemId` to TacticalUnit TS type**

- **Found during:** Task 2 (InfoPanel + BattleMap wiring)
- **Issue:** Plan required "목표: {systemName}" InfoPanel row + dashed mission line on BattleMap, both needing a target star system ID on the unit. The plan explicitly says "targetStarSystemId is NOT currently in TacticalUnitDto — if research confirms the need, surface it as an optional field on the unit." I verified the backend DTO (backend/game-app/.../TacticalBattleDtos.kt) and confirmed the field is indeed absent. Two options: (a) omit the row + line entirely, or (b) scaffold the optional field on the TS type so the rendering code is ready for a future backend plan.
- **Fix:** Chose (b) — added `targetStarSystemId?: number | null` to TacticalUnit with a deferred-plan comment explaining the backend wiring is still pending. Rendering code gates on the field being populated so it's inactive in production today but flips on automatically once the backend wires it through.
- **Files modified:** `frontend/src/types/tactical.ts`, `frontend/src/components/tactical/InfoPanel.tsx`, `frontend/src/components/tactical/BattleMap.tsx`
- **Verification:** Typecheck passes, all 45 scoped tests pass, the "현재 목적" row (which does not require the target field) renders correctly from the existing `missionObjective` field.
- **Committed in:** 50dcfc82 (Task 2 commit)

**2. [Rule 3 - Blocking] Added useGalaxyStore import to BattleMap**

- **Found during:** Task 2 (BattleMap mission line)
- **Issue:** Mission line needs the target star system's position to draw a directional hint; `useGalaxyStore.getSystem` is the existing lookup.
- **Fix:** Added `import { useGalaxyStore } from '@/stores/galaxyStore';` and subscribed to `getSystem`.
- **Files modified:** `frontend/src/components/tactical/BattleMap.tsx`
- **Verification:** Typecheck passes.
- **Committed in:** 50dcfc82 (Task 2 commit)

**3. [Rule 3 - Blocking] Deferred out-of-scope sibling typecheck errors**

- **Found during:** Task 1 typecheck pass
- **Issue:** `SubFleetAssignmentDrawer.test.tsx` (missing component file) and `SubFleetUnitChip.tsx` (duplicate `aria-disabled` attribute) broke `pnpm typecheck`. Both files are owned by 14-07, NOT 14-16.
- **Fix:** Logged to `deferred-items.md` per scope boundary rule. Both errors were subsequently fixed by sibling Wave 3/4 plans (14-12, 14-13) landing before my Task 2 typecheck run, so my final verification was clean.
- **Files modified:** `.planning/phases/14-frontend-integration/deferred-items.md`
- **Verification:** Final `pnpm typecheck` after Task 2 exits 0.
- **Committed in:** 57939d6c (Task 1 commit, deferred-items.md entry)

---

**Total deviations:** 3 auto-fixed (1 missing-critical, 2 blocking)
**Impact on plan:** All auto-fixes necessary. The `targetStarSystemId` deviation is minor forward-compat scaffolding — the plan explicitly anticipated this case and gave planner discretion. No scope creep.

## Issues Encountered

- **Sibling Wave 4 file races.** BattleMap.tsx was touched by 14-14 (command-gating additions: `canCommandUnit` import + `myHierarchy`/`mySide` useMemo) between my Task 2 commit and final verification. All 14-14 additions were in line ranges separate from my mission-line additions, and the re-run of `pnpm test --run BattleMap InfoPanel TacticalUnitIcon` after 14-14 landed still passed 45/45. No code change needed — the parallel-wave narrow-edit pattern worked as intended.
- **14-15 also extended BattleMap.tsx** (FlagshipFlash + SuccessionCountdownOverlay wiring) during my execution window. Same outcome — disjoint line ranges, no conflict.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **D-35/D-36/D-37 contracts satisfied on the frontend.** Status markers visible on every unit; NPC mission objective rows render when an NPC unit is selected; dashed mission line scaffolding is ready for backend `targetStarSystemId` wiring.
- **Future backend plan** should add `targetStarSystemId: Long?` to `TacticalUnitDto` (derive from `OperationPlan.targetStarSystemId` for operation participants, or from internal AI target for personality-driven NPCs). No frontend changes needed — the optional TS field and rendering gates are already in place.
- **Wave 4 parallelism validated.** 14-14 + 14-15 + 14-16 all landed cleanly on the same shared files (TacticalUnitIcon, InfoPanel, BattleMap) with narrow append-only edits.

## Self-Check: PASSED

Verified files exist:
- FOUND: frontend/src/components/tactical/TacticalUnitIcon.tsx
- FOUND: frontend/src/components/tactical/TacticalUnitIcon.test.tsx
- FOUND: frontend/src/components/tactical/InfoPanel.tsx
- FOUND: frontend/src/components/tactical/InfoPanel.test.tsx
- FOUND: frontend/src/components/tactical/BattleMap.tsx
- FOUND: frontend/src/components/tactical/BattleMap.missionLine.test.tsx
- FOUND: frontend/src/types/tactical.ts (targetStarSystemId added)
- FOUND: frontend/src/app/(game)/tactical/page.tsx (selectedUnit wired)

Verified commits exist:
- FOUND: 57939d6c (feat(14-16): add D-35 status markers to TacticalUnitIcon)
- FOUND: 50dcfc82 (feat(14-16): NPC mission objective + BattleMap mission target line)

Verified tests pass:
- 45/45 tests passing across TacticalUnitIcon + InfoPanel + BattleMap.missionLine + BattleMap
- Typecheck exits 0

---
*Phase: 14-frontend-integration*
*Completed: 2026-04-09*
