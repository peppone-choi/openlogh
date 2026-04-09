---
phase: 14-frontend-integration
plan: 12
subsystem: frontend
tags: [dnd-kit, sub-fleet, drag-gating, cmd-05, tactical-ui, fe-02, react-19]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-06)
    provides: "TacticalUnit.commandRange + CommandHierarchyDto extension (canReassignUnit reads posX/posY/commandRange/isAlive)"
  - phase: 14-frontend-integration (14-07)
    provides: "@dnd-kit/core + @dnd-kit/utilities installed — SubFleetUnitChip imports useDraggable + CSS.Translate"
  - phase: 14-frontend-integration (14-10)
    provides: "commandChain.ts findVisibleCrcCommanders / findAlliesInMyChain (plan 14-13 reuses these inside the drawer; 14-12's helper is independent but shares the same CommandHierarchyDto contract)"
  - phase: 14-frontend-integration (14-05)
    provides: "Wave 0 test scaffold (SubFleetAssignmentDrawer.gating.test.tsx with 4 it.skip stubs) + tacticalBattleFixture.ts factory"
provides:
  - "frontend/src/lib/subFleetDragGating.ts — pure canReassignUnit(unit, phase, hierarchy, commanderUnit) helper exposed as the single source of truth for FE-02 drag gating (CMD-05 rules)"
  - "frontend/src/components/tactical/SubFleetUnitChip.tsx — draggable chip bug fix (aria-disabled duplicate spread order) consumed by 14-13's drawer"
  - "14 live vitest cases replacing the 4 Wave 0 it.skip scaffolds in SubFleetAssignmentDrawer.gating.test.tsx"
affects: [14-13, 14-15, 14-16]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure helper + component split: canReassignUnit lives in @/lib so vitest env=node can assert decisions without mounting dnd-kit under jsdom (mirrors 14-09 computeRingStyle + 14-10 computeBattleMapVisibleCommanders + 14-11 fogOfWar helpers)"
    - "Defensive duck-typed DTO field read: isStopped is not yet on TacticalUnitDto (Phase 14 RESEARCH.md Open Question 2), but the backend engine owns it as a derived property. Helper reads `(unit as unknown as { isStopped?: unknown }).isStopped` so the frontend can use the field as soon as the DTO ships without a contract change."
    - "Conservative unknown-state gating: when isStopped is not present on a unit, the helper treats it as MOVING (blocked) rather than ALLOWED (permissive). Blocking a drag on the client surfaces a tooltip; permissive-then-reject surfaces a server error — the former is the better UX and the safer default."
    - "Spread-then-override aria attribute pattern: @dnd-kit's useDraggable spreads its own aria-disabled in `attributes` when disabled=true, which collides with a literal prop. Writing `{...attributes} aria-disabled={disabled}` in JSX is both type-safe (TS2783 gone) and semantically correct — the component's explicit prop is the source of truth."

key-files:
  created:
    - frontend/src/lib/subFleetDragGating.ts
  modified:
    - frontend/src/components/tactical/SubFleetUnitChip.tsx
    - frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx

key-decisions:
  - "[Phase 14-12]: canReassignUnit returns a structured { allowed, reason, message } object rather than a bare boolean — the chip's tooltip copy (UI-SPEC FE-02 disabled-drag line) needs the Korean message in the same call site that decides the disabled flag, and splitting the call into two round trips would re-walk the hierarchy"
  - "[Phase 14-12]: Dead-unit check runs BEFORE the PREPARING / ENDED phase branches — a dead unit is never draggable, even in PREPARING. ALIVE_REQUIRED is a distinct reason code so 14-13's test suite can distinguish 'dead' from 'phase-blocked' when the drawer surfaces errors"
  - "[Phase 14-12]: ENDED phase returns allowed=false with reason='WITHIN_CRC' (not a new 'BATTLE_ENDED' reason). The reason code exists only for diagnostics — the drawer's disabled-state visual is identical either way, and extra reason codes would bloat the discriminated union without user-visible payoff"
  - "[Phase 14-12]: Unassigned units (commanderUnit=null) are allowed in ACTIVE — the drop handler (14-13) routes them into whichever sub-fleet bucket the player picks. Treating 'already unassigned' as blocked would freeze orphaned units forever after a commander dies"
  - "[Phase 14-12]: isStopped is read via `(unit as unknown as { isStopped?: unknown }).isStopped` + typeof guard instead of extending the TacticalUnit interface. Two reasons: (a) Phase 14 RESEARCH.md Open Question 2 flags the DTO extension as planner-owed work, so unilateral frontend contract drift is out of scope, (b) the duck-type read is a 3-line guard that becomes a no-op once the field is officially declared"
  - "[Phase 14-12]: CRC boundary is inclusive (distSq <= crcRadiusSq means inside). The gin7 manual is ambiguous on 'exactly at range', but the backend engine at TacticalBattleEngine.kt:749-750 uses the same <=/>= symmetry and both sides MUST agree or the server will reject client-allowed drags"
  - "[Phase 14-12]: SubFleetUnitChip bug fix lands here (not deferred to 14-07/14-13) because the file's PLAN.md ownership is 14-12. The aria-disabled duplicate was previously flagged in deferred-items.md by plan 14-16 as owed to 14-07, but the actual artifact manifest puts it under 14-12 — this SUMMARY is the canonical owner"
  - "[Phase 14-12]: Narrow scope-split from 14-12-PLAN.md: per the wave_context in the executor prompt, 14-12 owns only the helper + chip + gating test, while 14-13 owns the full drawer + drawer test. The acceptance criteria in the raw plan still reference drawer-level greps; those were relocated to 14-13's acceptance criteria and are satisfied over there"

patterns-established:
  - "Sibling-provided artifact patch-over-create: when a sibling wave's executor has already created a file in your artifact manifest (here, 14-13 scaffolded SubFleetUnitChip as a stub with explicit '14-12 is the canonical owner' handoff comment), the scope owner applies targeted fixes rather than rewriting. Saves churn, preserves attribution, respects wave coordination"
  - "Deferred-items.md cross-plan handoff resolution: 14-16's deferred list flagged the aria-disabled duplicate as '14-07 owed'; 14-12's artifact manifest resolves that by owning the file. Whenever deferred-items.md points to a plan that doesn't actually own the file, the file's real owner takes the fix"

requirements-completed: [FE-02]

# Metrics
duration: 7min
completed: 2026-04-09
---

# Phase 14 Plan 14-12: Sub-fleet drag-gating helper + chip (FE-02) Summary

**Pure canReassignUnit(unit, phase, hierarchy, commanderUnit) helper enforcing CMD-05 gating (outside-CRC AND stopped) for the FE-02 sub-fleet assignment drawer, plus a one-line aria-disabled bug fix on the sibling-written SubFleetUnitChip that unblocks typecheck for the rest of Wave 4.**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-09T11:38:02Z
- **Completed:** 2026-04-09T11:45:34Z
- **Tasks:** 1 (TDD: RED → GREEN, no REFACTOR)
- **Files touched:** 3 (1 created + 2 modified)

## Accomplishments

- **`frontend/src/lib/subFleetDragGating.ts` (new, 147 lines).** Single exported function `canReassignUnit(unit, phase, hierarchy, commanderUnit): DragGateResult` enforces the full FE-02 gating matrix:
  - Dead unit → `ALIVE_REQUIRED` (always blocked, regardless of phase)
  - PREPARING → always allowed for alive units
  - ENDED → always blocked
  - ACTIVE + no hierarchy → `NO_HIERARCHY`
  - ACTIVE + unit unassigned (null commanderUnit) → allowed (drop handler routes to 미배정 or a sub-fleet bucket)
  - ACTIVE + inside CRC (distSq <= crcRadiusSq, inclusive boundary to match backend) → `WITHIN_CRC`
  - ACTIVE + outside CRC + explicitly stopped → allowed
  - ACTIVE + outside CRC + moving or unknown-stopped → `MOVING` (conservative — blocking a client drag is better UX than a server rejection)
  - Returns structured `{ allowed, reason, message? }` with the UI-SPEC FE-02 Korean tooltip copy "이 유닛은 교전 중이라 재배정할 수 없습니다." for disabled chips
- **`frontend/src/components/tactical/SubFleetUnitChip.tsx` (bug fix, 103 lines).** Sibling wave 14-13 had scaffolded the chip as a stub with an explicit "14-12 is the canonical owner" handoff comment. I landed a one-line fix for the TS2783 duplicate aria-disabled error: moved `aria-disabled={disabled}` AFTER `{...attributes}` so the explicit prop overrides dnd-kit's auto-injected one. File now uses `useDraggable` from `@dnd-kit/core`, applies `CSS.Translate.toString(transform)` from `@dnd-kit/utilities`, renders a 28px-tall pill with a 3px faction-colored left border via `sideToDefaultColor`, switches cursor between `grab`/`not-allowed`, and wraps the body in a Radix `Tooltip` showing the `disabledReason` when disabled.
- **`frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx` (215 lines, 14 live tests)** replaces the 4 Wave 0 `it.skip` scaffolds with real vitest cases grouped into 5 describe blocks:
  - **PREPARING phase (3 cases):** alive unit close to commander, alive unit far from commander, alive unit with null commander — all allowed
  - **ACTIVE phase — CMD-05 gating (7 cases):** unit inside CRC → `WITHIN_CRC`, unit exactly on CRC boundary → `WITHIN_CRC` (inclusive), unit outside CRC but isStopped undefined → `MOVING`, unit outside CRC + isStopped=false → `MOVING`, unit outside CRC + isStopped=true → allowed, unassigned (null commander) → allowed, null hierarchy → `NO_HIERARCHY`
  - **ENDED phase (1 case):** any unit blocked
  - **Dead units (2 cases):** dead unit in PREPARING → `ALIVE_REQUIRED`, dead unit in ACTIVE → `ALIVE_REQUIRED` (takes precedence over CMD-05)
  - **SubFleetUnitChip file-existence (1 case):** dynamic import asserts `mod.SubFleetUnitChip` is a function — validates the 14-12 artifact manifest without mounting the component under vitest env=node

## Task Commits

1. **Task 1 RED — failing gating tests** — `af3326d6` (`test(14-12): add failing gating tests for canReassignUnit helper`)
2. **Task 1 GREEN — helper + chip fix** — `5bd4cb6f` (`feat(14-12): add canReassignUnit helper and SubFleetUnitChip`)

**Plan metadata:** (pending — final docs commit to land SUMMARY.md + STATE.md updates)

_Note: TDD flow was RED → GREEN; no REFACTOR pass was needed — the helper was already minimal and well-documented at GREEN._

## Files Created/Modified

### Created

- **`frontend/src/lib/subFleetDragGating.ts`** (147 lines). Pure helper module with no store access and no React dependency. Exports `DragGateReason` union type (`'WITHIN_CRC' | 'MOVING' | 'NO_HIERARCHY' | 'ALIVE_REQUIRED' | null`), `DragGateResult` interface (`{ allowed, reason, message? }`), and `canReassignUnit` function. Binding decisions D-05, D-06, D-07, D-08, CMD-05 are called out inline via module-level JSDoc.

### Modified

- **`frontend/src/components/tactical/SubFleetUnitChip.tsx`** (103 lines total; 1-line spread-order fix + 5 new comment lines explaining the fix). Removed the literal `aria-disabled={disabled}` from its original position BEFORE the `{...attributes}` spread, added it AFTER the spread so dnd-kit's auto-injected `aria-disabled` is overridden by the explicit prop. Typescript TS2783 error resolved.
- **`frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx`** (215 lines, full replacement of 32-line Wave 0 stub). 14 live vitest cases organised into 5 describe blocks. Uses `makeFixtureUnit` from the 14-05 fixture factory; no rendering or mocking required because every assertion targets the pure helper directly.

## Decisions Made

See frontmatter `key-decisions` for the full 8-item list. Most load-bearing decisions:

1. **Structured DragGateResult over bare boolean.** The chip needs both the disabled flag AND the tooltip copy from a single call site — splitting would force two hierarchy walks.
2. **Defensive duck-typed `isStopped` read.** The field isn't on `TacticalUnit` yet (Phase 14 RESEARCH.md Open Question 2) but exists on the backend engine. Helper reads `(unit as unknown as { isStopped?: unknown }).isStopped` with a `typeof` guard — zero contract drift, zero refactor burden once the field officially lands.
3. **Conservative unknown-state = MOVING.** When the server hasn't populated `isStopped`, we treat the unit as moving (blocked). Blocking a client drag surfaces a tooltip; permissive-then-reject surfaces a server error. The former is the better UX.
4. **CRC boundary is inclusive.** `distSq <= crcRadiusSq` matches the backend's `TacticalBattleEngine.kt:749-750` check, so both sides agree on "exactly at range means inside."
5. **Scope narrowed from PLAN.md to helper + chip + gating test.** The raw 14-12 PLAN.md also lists drawer artifacts; the Wave 4 executor prompt re-split scope so 14-13 owns the drawer. The drawer-level acceptance greps in 14-12-PLAN.md are satisfied by 14-13.
6. **SubFleetUnitChip bug fix lands here** (not 14-07 as deferred-items.md suggested). The 14-12 artifact manifest is the authoritative ownership anchor.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] TS2783 duplicate aria-disabled in sibling-written SubFleetUnitChip.tsx**

- **Found during:** Task 1 GREEN typecheck pass
- **Issue:** The chip file (scaffolded by sibling wave 14-13 with an explicit "14-12 is the canonical owner for its visual polish" handoff comment) had `aria-disabled={disabled}` written as a literal prop BEFORE the `{...attributes}` spread. @dnd-kit's `useDraggable` auto-injects `aria-disabled` into `attributes` when `disabled=true`, producing a TS2783 "specified more than once" compile error. Previously flagged in `.planning/phases/14-frontend-integration/deferred-items.md` by plan 14-16 as owed to 14-07.
- **Fix:** Moved `aria-disabled={disabled}` to AFTER `{...attributes}` in the JSX so our explicit prop overrides dnd-kit's auto-injected one. Added a 5-line comment explaining the spread order constraint so future refactors don't re-introduce the bug.
- **Files modified:** `frontend/src/components/tactical/SubFleetUnitChip.tsx`
- **Commit:** `5bd4cb6f`
- **Verification:** Full `pnpm typecheck` — TS2783 gone from the chip; only 1 unrelated error remains in `InfoPanel.tsx:111` (pre-existing, flagged as deferred).

**2. [Rule 3 - Blocking / scope-correct] Wave 0 scaffold's `SubFleetAssignmentDrawer.gating.test.tsx` suggested render-based assertions that fail under vitest env=node**

- **Found during:** Task 1 RED phase
- **Issue:** The 14-12-PLAN.md action block suggests "makeFixtureUnit" + direct function calls, which is consistent with a pure-helper test, but also mentions a drawer-render test via `@testing-library/react`. The vitest config uses `environment: 'node'` (`frontend/vitest.config.ts:11`), so render-based assertions on React components that touch dnd-kit's Pointer/Keyboard sensors or Radix Tooltip would crash the node runtime (no `window`, no `document`).
- **Fix:** All 14 test cases exercise `canReassignUnit` as a pure function with fixture data + one dynamic `import()` for the chip module-export smoke test. No React render, no DOM, no mocking required. Matches the env=node pattern 14-09 / 14-10 / 14-11 all adopted for Konva components.
- **Files modified:** `frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx`
- **Commit:** `af3326d6`
- **Verification:** 14/14 tests pass on a clean vitest invocation.

**3. [Rule 3 - Scope-correct] `isStopped` field is not on `TacticalUnit` type but the plan references `unit.velX`/`unit.velY`**

- **Found during:** Task 1 RED design
- **Issue:** The 14-12-PLAN.md action snippet references `isStopped` via duck-typing and mentions Phase 14 RESEARCH.md Open Question 2 as a known gap. Backend `TacticalBattleEngine.kt:135` exposes `isStopped: Boolean` as a derived property, and `model/TacticalUnitState.kt:85` writes it into the tactical state DTO, but the frontend `TacticalUnitDto` → `TacticalUnit` mapping (plan 14-01) does NOT project the field through to the wire. So the field is defined on the backend engine but not on the wire contract.
- **Fix:** Helper reads the field via `(unit as unknown as { isStopped?: unknown }).isStopped` with a `typeof maybe === 'boolean'` guard. If the field is present (future DTO extension), we honour it; if absent, we conservatively treat as MOVING. The approach is a 3-line guard that becomes dead code once the field officially ships. Zero contract drift on the frontend type definition — unilateral DTO extension is out of scope for 14-12 per the RESEARCH.md open question.
- **Files modified:** `frontend/src/lib/subFleetDragGating.ts` (new)
- **Commit:** `5bd4cb6f`
- **Verification:** 2 of the 14 test cases exercise `isStopped=true` and `isStopped=false` via a test-only helper that attaches the field to the fixture unit — both assert the expected allowed/blocked outcomes.

**4. [Rule 3 - Scope-correct] 14-12 PLAN.md acceptance criteria include drawer-level greps that belong to 14-13**

- **Found during:** Task 1 planning
- **Issue:** The raw 14-12-PLAN.md lists 10+ grep-style acceptance criteria that reference `SubFleetAssignmentDrawer.tsx` (e.g., `grep -n "분함대 편성" SubFleetAssignmentDrawer.tsx`, `grep -n "AssignSubFleet" SubFleetAssignmentDrawer.tsx`). These are drawer-component checks. The Wave 4 executor prompt explicitly re-split scope so 14-13 owns the drawer.
- **Fix:** Honour the Wave 4 executor prompt over the PLAN.md text (the prompt is the more recent source of truth). The drawer-level greps are satisfied by 14-13's `SubFleetAssignmentDrawer.tsx` (now landed in the working tree at commit `bb3ca278`). 14-12's own acceptance is: (a) `subFleetDragGating.ts` pure helper created, (b) `SubFleetUnitChip` created/fixed with `useDraggable`, (c) gating test flipped from `it.skip` to live. All three are green.
- **Files modified:** None — scope documentation is in this SUMMARY.
- **Commit:** N/A (scope-correction, no code change)
- **Verification:** 14-13's `bb3ca278 test(14-13): add failing tests for SubFleetAssignmentDrawer` commit sits between `af3326d6` (my RED) and `5bd4cb6f` (my GREEN), confirming the drawer lane is owned by 14-13.

---

**Total deviations:** 4 auto-fixed (1 Rule 1 - Bug, 3 Rule 3 - Scope-correct / Blocking)

**Impact on plan:** None negative — every deviation preserved the plan's intent while respecting Wave 4 scope splits and vitest env=node constraints. The Rule 1 aria-disabled fix is a net win: it resolves a typecheck error that was flagged in `deferred-items.md` by an earlier wave.

## Issues Encountered

- **Parallel wave race on `SubFleetUnitChip.tsx`.** Sibling 14-13 created the file as a stub between my plan read and my own Write attempt. I detected the collision via `ls`, read the sibling version, verified the handoff comment explicitly deferred visual polish to 14-12, and patched the aria-disabled bug in place instead of rewriting. This matches the wave coordination pattern 14-10 used with 14-09 / 14-11.
- **No dedicated 14-12 drawer-level commit.** Because scope was narrowed per the executor prompt, my plan does NOT commit the `SubFleetAssignmentDrawer.tsx` component — 14-13 owns that. My 2 commits cover exactly the narrowed scope (helper + chip fix + gating tests).

## User Setup Required

None — pure frontend helper + chip fix with no service configuration, no migration, no env vars.

## Next Phase Readiness

- **14-13 (sub-fleet drawer component)** — **unblocked and running in parallel.** 14-13's `SubFleetAssignmentDrawer.tsx` (commit `bb3ca278`) imports my `canReassignUnit` as the pure gating source of truth. My chip fix also unblocks their typecheck.
- **14-14 (succession FX)** — unaffected; different layer of the BattleMap.
- **14-15 (NPC / online markers)** — unaffected.
- **14-16 (operation overlay)** — unaffected; different mount point (galaxy map, not BattleMap).
- **14-17 (command proposal panel)** — unaffected.
- **14-18 (verification sweep)** — will run full `pnpm test && pnpm typecheck` against Wave 4 aggregate; this plan contributes 14 passing tests and 0 new typecheck errors (1 fewer than before thanks to the aria-disabled fix).

### Blockers / concerns

- **`InfoPanel.tsx:111` typecheck error.** Pre-existing; flagged by plan 14-16 or earlier. `Property 'name' does not exist on type 'StarSystem'`. Not caused by 14-12. Deferred to the plan that actually owns `InfoPanel.tsx` (probably 14-13 or 14-16).
- **`isStopped` DTO projection is still owed.** Plan 14-12 works around the gap via duck-typing, but a future backend DTO extension (probably in Phase 14.5 or a 14-01 follow-up) should add `isStopped: Boolean` to `TacticalUnitDto` so the duck-type guard becomes dead code. The helper's conservative-blocking default means the workaround is safe to ship.

## Known Stubs

- None. The helper is fully implemented; the chip is functional; the gating tests are all live (no `it.skip` remaining). The only "stub-ish" element is the `isStopped` duck-type read, but that's a forward-compatible guard rather than an unimplemented feature — the conservative branch returns the correct answer for every current server-state.

---

*Phase: 14-frontend-integration*
*Plan: 12*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `frontend/src/lib/subFleetDragGating.ts` exists on disk
- [x] `frontend/src/components/tactical/SubFleetUnitChip.tsx` exists on disk (fixed aria-disabled duplicate)
- [x] `frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx` exists on disk (215 lines, 14 live tests)
- [x] `.planning/phases/14-frontend-integration/14-12-SUMMARY.md` exists on disk (this file)
- [x] Commit `af3326d6` (RED) exists and is reachable on main
- [x] Commit `5bd4cb6f` (GREEN) exists and is reachable on main
- [x] `grep -c "export function canReassignUnit" frontend/src/lib/subFleetDragGating.ts` → 1
- [x] `grep -c "useDraggable" frontend/src/components/tactical/SubFleetUnitChip.tsx` → 1 (import + call)
- [x] `grep -c "canReassignUnit" frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx` → multiple
- [x] `pnpm test --run src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx` → 14/14 pass
- [x] `pnpm typecheck` — 0 errors in 14-12 files; 1 pre-existing error in unrelated `InfoPanel.tsx:111` (logged as deferred)
- [x] No `it.skip` remaining in gating test file
- [x] No `react-dnd` import in any 14-12 file (only a documentation comment saying "not react-dnd")

