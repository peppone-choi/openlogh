---
phase: 14
plan: 14
subsystem: frontend-integration
tags: [fe-03, gating, proposal, tooltip, infopanel, tactical-unit-icon]
requires:
  - 14-06-PLAN.md (TacticalUnitDto hierarchy fields)
  - 14-10-PLAN.md (commandChain.ts findVisibleCrcCommanders + findAlliesInMyChain)
provides:
  - canCommandUnit pure gating function
  - commandStore.createProposal action
  - FE-03 gating visual states (gold border, tooltip, disabled buttons)
  - InfoPanel command-relation badge (top of panel)
affects:
  - command-execution-panel behavior (now gated by selectedUnit + myHierarchy props)
  - TacticalUnitIcon rendering (new isUnderMyCommand prop)
  - InfoPanel rendering (new selectedUnitCommandRelation prop)
  - BattleMap units layer (per-unit gating lookup)
tech-stack:
  added: []
  patterns:
    - Pure gating functions return { allowed, reason, message } tuple (mirrors 14-10 commandChain)
    - aria-disabled for gated buttons (vs native disabled for hard-disabled) so clicks still fire for Shift+click proposal path
    - Radix Tooltip + sr-only span for RTL textContent assertions without portal mounting
    - afterEach(cleanup) + @vitest-environment jsdom docblock for React component tests
key-files:
  created:
    - frontend/src/lib/canCommandUnit.ts
  modified:
    - frontend/src/lib/canCommandUnit.test.ts (scaffold flipped to 7 live tests)
    - frontend/src/components/game/command-execution-panel.tsx
    - frontend/src/components/game/command-execution-panel.gating.test.tsx (scaffold flipped to 3 live tests)
    - frontend/src/components/game/command-execution-panel.proposal.test.tsx (scaffold flipped to 4 live tests)
    - frontend/src/stores/commandStore.ts (createProposal action + CreateProposalPayload type)
    - frontend/src/components/tactical/TacticalUnitIcon.tsx (isUnderMyCommand prop + gold outline shapes + MY_COMMAND_GOLD export)
    - frontend/src/components/tactical/InfoPanel.tsx (selectedUnitCommandRelation prop + resolveCommandRelationBadge helper + top-of-panel badge; landed via 14-16 commit absorption — see deviations)
    - frontend/src/components/tactical/BattleMap.tsx (canCommandUnit import + hoisted mySide/myHierarchy useMemo + per-unit isUnderMyCommand wiring)
decisions:
  - "canCommandUnit returns { allowed, reason, message } instead of boolean so the tooltip can switch copy between OUT_OF_CHAIN and JAMMED reasons"
  - "Gated buttons use aria-disabled (not native disabled) so clicks still fire and can branch on event.shiftKey; hard-disabled states (CP, cooldown, !enabled) keep native disabled"
  - "commandStore.createProposal signature is (requesterOfficerId, commandCode, payload) — deviates from plan's 2-arg signature because the existing submitProposal pattern is 2-arg with generalId as first param; matches pattern and avoids a global store slot for myOfficerId"
  - "Radix Tooltip content wraps the button in a span with sr-only gating copy so RTL can assert via textContent without opening the portal (env=node jsdom quirk)"
  - "Gold border (MY_COMMAND_GOLD #f59e0b) exported from TacticalUnitIcon.tsx as single source of truth — InfoPanel uses the same hex at 0.2 alpha background"
  - "canCommandUnit rule priority matches 14-RESEARCH Section 12: (1) null hierarchy → OUT_OF_CHAIN, (2) jammed + am-commander → JAMMED, (3) am-commander → allowed, (4) sub-fleet + target in memberFleetIds → allowed, (5) else OUT_OF_CHAIN"
  - "BattleMap hoists mySide + myHierarchy into useMemo hooks so the units map doesn't recompute the side lookup N times per tick"
  - "Tests use afterEach(cleanup) to prevent Radix Tooltip DOM bleed-through between cases (getAllByRole multiple-match errors without it)"
metrics:
  duration: "~1h 10min"
  completed: 2026-04-09
  task_count: 3
  file_count: 8
  commits:
    - 0131bb42 feat(14-14): canCommandUnit pure gating function (FE-03)
    - 2d7dd48b feat(14-14): FE-03 gating + Shift+click proposal path (D-09, D-10)
    - a1440ab9 feat(14-14): gold "my-command" border + BattleMap gating wiring (D-11)
---

# Phase 14 Plan 14: Frontend — Command gating UI + proposal path Summary

FE-03 permission gating wired end-to-end: canCommandUnit pure function + Radix tooltip + Shift+click proposal path + gold border on units under my command + InfoPanel command-relation badge. Backend double-check preserved (FE is advisory per D-12).

## Objective (Recap)

Implement permission gating UI for FE-03. Create `canCommandUnit` pure function, wire it into `command-execution-panel.tsx` with Radix tooltip + Shift+click proposal path, add gold border to TacticalUnitIcon, add command-relation badge to InfoPanel.

## Work Completed

### Task 1 — canCommandUnit pure function + 7 table tests

Commit: `0131bb42`

- Created `frontend/src/lib/canCommandUnit.ts` exporting the pure function `canCommandUnit(myOfficerId, myHierarchy, targetUnit) → GatingResult`.
- Returns `{ allowed, reason, message }` where reason ∈ `'OUT_OF_CHAIN' | 'JAMMED' | null`.
- Rule priority (mirrors 14-RESEARCH Section 12):
  1. `!myHierarchy` → `OUT_OF_CHAIN` with "지휘권 없음 — Shift+클릭으로 상위자에게 제안하기"
  2. am-fleet-commander + `commJammed` → `JAMMED` with "통신 방해 중 — 명령 발령 불가"
  3. am-fleet-commander OR am-activeCommander → allowed
  4. am-sub-fleet-commander + `targetUnit.fleetId ∈ mySubFleet.memberFleetIds` → allowed
  5. else → `OUT_OF_CHAIN`
- Flipped `canCommandUnit.test.ts` scaffold (4 `it.skip` stubs from 14-05) to 7 live table-driven branches: fleet commander, sub-commander in own subFleet, sub-commander out of subFleet, plain officer, commJammed on fleet commander, delegated activeCommander, null hierarchy (both null and undefined).

### Task 2 — commandStore.createProposal + gated command-execution-panel + tooltip + Shift+click

Commit: `2d7dd48b`

- **commandStore.ts:**
  - Added `createProposal(requesterOfficerId, commandCode, payload)` async action that wraps `proposalApi.submit` with the FE-03 gating payload (`{ targetOfficerId, superiorOfficerId, targetFleetId? }`). Refreshes `myProposals` on success.
  - Exported `CreateProposalPayload` type.
- **command-execution-panel.tsx:**
  - New optional props `selectedUnit`, `myHierarchy`, `superiorName`.
  - Wraps the whole list in `<TooltipProvider delayDuration={400}>`.
  - For each command button, calls `canCommandUnit(officerId, myHierarchy, selectedUnit)` and:
    - Hard-disabled states (cooldown, insufficient CP, `!enabled`, executing) keep the native `disabled` attribute so the browser suppresses clicks.
    - Gated-only state (`!gate.allowed` with no hard disable) uses `aria-disabled="true"` so `onClick` still fires and the handler can branch on `event.shiftKey`.
    - When gated, the button is wrapped in `<Tooltip><TooltipTrigger asChild>…<TooltipContent>지휘권 없음 — <kbd>Shift</kbd>+클릭으로 상위자에게 제안하기</TooltipContent></Tooltip>`.
    - Gated `onClick` dispatches `useCommandStore.getState().createProposal(officerId, cmd.actionCode, { targetOfficerId, superiorOfficerId, targetFleetId })` on Shift+click, then fires `toast.success("{cmd.name} 제안을 {superiorName}에게 발송했습니다.")`. Plain clicks are a no-op.
  - Backend double-check on execute path is unchanged — FE gating is purely advisory per D-12.
- **gating.test.tsx** (scaffold flipped): 3 live tests — disabled state via `aria-disabled`, tooltip copy "지휘권 없음" in DOM, Shift+click hint (kbd or literal "Shift+클릭" phrase).
- **proposal.test.tsx** (scaffold flipped): 4 live tests — createProposal payload shape, no-WebSocket path, Korean toast body, plain click is no-op.

### Task 3 — TacticalUnitIcon gold border + BattleMap wiring + InfoPanel badge

Commit: `a1440ab9` (TacticalUnitIcon + BattleMap). InfoPanel edits landed under commit `50dcfc82` (14-16) via parallel-wave commit absorption — see Deviations.

- **TacticalUnitIcon.tsx:**
  - New optional prop `isUnderMyCommand?: boolean` (default false).
  - Exported `MY_COMMAND_GOLD = '#f59e0b'` as single source of truth.
  - When `isUnderMyCommand=true`, renders a 2px gold outline BEHIND the main △/□ body shape. Uses `fillEnabled={false}` + `listening={false}` so the indicator never interferes with click picking or damage-opacity logic.
  - Layer order: selection ring → gold "my command" outline → main fill → letter label → status marker. Coexists cleanly with 14-16's D-35 status marker in the same Group.
- **InfoPanel.tsx** (landed via 14-16 commit absorption):
  - New optional props `selectedUnitCommandRelation`, `friendlyOtherOfficerName`, `friendlyOtherOfficerRank`.
  - New `resolveCommandRelationBadge` pure helper: `'self' | 'subordinate'` → "내 지휘권 하 유닛" (gold `#f59e0b` 0.2 alpha), `'friendly-other'` → "{officerName} 지휘권 ({rank})" (faction `#4466ff` 0.2 alpha), `'enemy'` → "적 부대" (destructive `#ef4444` 0.2 alpha).
  - Badge rendered at the TOP of the panel body (before first InfoRow) with `data-testid="command-relation-badge"`.
- **BattleMap.tsx:**
  - Imported `canCommandUnit` from `@/lib/canCommandUnit`.
  - Hoisted `mySide` + `myHierarchy` into `useMemo` hooks so the units map can cheaply look up the gating function per render without recomputing the side→hierarchy resolution N times per tick.
  - In the units render loop, computes `isUnderMyCommand` per unit with short-circuit for dead + enemy units (gating only runs on live allies).
  - Passes `isUnderMyCommand` through to `TacticalUnitIcon`.

## Verification

- `cd frontend && pnpm typecheck` → exit 0 (clean)
- `cd frontend && pnpm test --run canCommandUnit command-execution-panel` → **14 tests passed** (7 canCommandUnit + 3 gating + 4 proposal)
- Individual test files:
  - `src/lib/canCommandUnit.test.ts` → 7 passed
  - `src/components/game/command-execution-panel.gating.test.tsx` → 3 passed
  - `src/components/game/command-execution-panel.proposal.test.tsx` → 4 passed

### Acceptance Criteria

| Criterion | Status |
| --- | --- |
| `test -f frontend/src/lib/canCommandUnit.ts` | PASS |
| `grep "export function canCommandUnit"` → 1 match | PASS |
| `grep "JAMMED"` in canCommandUnit.ts | PASS |
| `grep "OUT_OF_CHAIN"` in canCommandUnit.ts | PASS |
| `grep "지휘권 없음"` in canCommandUnit.ts | PASS |
| `pnpm test --run canCommandUnit` → 7 tests pass | PASS |
| `grep "createProposal"` in commandStore.ts → ≥ 2 matches | PASS (3 matches: type, impl, doc) |
| `grep "canCommandUnit"` in command-execution-panel.tsx → 1 match | PASS |
| `grep "Tooltip"` in command-execution-panel.tsx → ≥ 1 match | PASS (4 imports + wrapper) |
| `grep "shiftKey"` in command-execution-panel.tsx → ≥ 1 match | PASS (2 matches) |
| `grep "createProposal"` in command-execution-panel.tsx → ≥ 1 match | PASS |
| `grep "지휘권 없음"` in command-execution-panel.tsx → 1 match | PASS |
| `pnpm test --run command-execution-panel.gating` → exit 0 | PASS |
| `pnpm test --run command-execution-panel.proposal` → exit 0 | PASS |
| `pnpm typecheck` → exit 0 | PASS |
| `grep "isUnderMyCommand"` in TacticalUnitIcon.tsx → ≥ 2 matches | PASS (5 matches: prop decl, destructure, 2 render guards, doc) |
| `grep "#f59e0b"` in TacticalUnitIcon.tsx → 1 match | PASS (via MY_COMMAND_GOLD constant) |
| `grep "selectedUnitCommandRelation"` in InfoPanel.tsx → ≥ 2 matches | PASS (5 matches) |
| `grep "내 지휘권 하 유닛"` in InfoPanel.tsx → 1 match | PASS |
| `grep "적 부대"` in InfoPanel.tsx → 1 match | PASS |
| `grep "canCommandUnit"` in BattleMap.tsx → ≥ 1 match | PASS (2 matches: import + call site) |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocker] InfoPanel TS2339 error on `targetSystem?.name`**

- **Found during:** Task 3 typecheck before committing Task 2
- **Issue:** 14-16's uncommitted InfoPanel code referenced `targetSystem?.name`, but `StarSystem` type defines `{ nameKo, nameEn }` (no generic `name` field). `pnpm typecheck` failed and blocked Task 2 verification.
- **Fix:** Changed to `targetSystem?.nameKo ?? targetSystem?.nameEn ?? null` with a comment noting the 14-14 Rule 3 auto-fix and the reason for preferring Korean display name.
- **Files modified:** `frontend/src/components/tactical/InfoPanel.tsx`
- **Commit:** landed under 14-16 commit absorption (50dcfc82) — see deviation #3 below.

**2. [Rule 2 — Missing critical functionality] createProposal signature deviation**

- **Found during:** Task 2 design
- **Issue:** Plan spec prescribed `createProposal(commandCode, { targetOfficerId, superiorOfficerId, targetFleetId })` with no requester id. But `proposalApi.submit(generalId, data)` requires the requester id, and the store has no global slot for `myOfficerId`.
- **Fix:** Added `requesterOfficerId` as first parameter: `createProposal(requesterOfficerId, commandCode, payload)`. Matches the existing `submitProposal(generalId, data)` pattern in the same store.
- **Files modified:** `frontend/src/stores/commandStore.ts`
- **Commit:** 2d7dd48b

**3. [Wave 4 parallel coordination] InfoPanel edits landed under 14-16 commit**

- **Found during:** Task 3 commit step
- **Issue:** Sibling plan 14-16 was executing in parallel. When 14-16 wrote its final InfoPanel state (including its NPC mission rows AND my already-applied `selectedUnitCommandRelation`/`resolveCommandRelationBadge`/badge render), its `git add` + commit picked up the whole working-copy state of the file. My InfoPanel edits are on main attributed to `50dcfc82 feat(14-16): NPC mission objective + BattleMap mission target line`.
- **Fix:** Explicitly documented the split in the Task 3 commit body (a1440ab9) and in this SUMMARY. No code rewrite needed — the content is correct on main. Same pattern as 14-10 attribution across 14-09 commits documented in STATE.md.
- **Files affected:** `frontend/src/components/tactical/InfoPanel.tsx`
- **Commits:** 50dcfc82 (14-16, absorbed 14-14 badge code), a1440ab9 (14-14, TacticalUnitIcon + BattleMap only)

### Discoveries

- **Vitest environment = `node` requires per-file `// @vitest-environment jsdom` docblock** for any React component test using `@testing-library/react`. The canCommandUnit pure-function test runs fine in node; the gating and proposal tests both declare jsdom at the top.
- **Radix Tooltip in jsdom generates duplicate button matches** unless tests call `cleanup()` in `afterEach`. First draft hit `Found multiple elements with the role "button"` until I added the import + hook.
- **`fireEvent.click` on native-disabled buttons is a no-op** in jsdom (matches browser behavior). This is why the gated button uses `aria-disabled` instead of `disabled`. The plain-click no-op test (proposal test #4) asserts this protocol holds.
- **Button accessible name comes from `aria-label`, not inner text**. When the button is gated, `aria-label="지휘권 없음 — Shift+클릭으로 상위자에게 제안하기"` so `getByRole('button', { name: /공격 지정/ })` misses — the RTL query must use `/지휘권 없음/`.

## Authentication Gates

None.

## Self-Check

- [x] `frontend/src/lib/canCommandUnit.ts` → FOUND
- [x] Commit `0131bb42` exists in `git log --oneline`
- [x] Commit `2d7dd48b` exists in `git log --oneline`
- [x] Commit `a1440ab9` exists in `git log --oneline`
- [x] canCommandUnit test → 7 passed
- [x] command-execution-panel.gating.test → 3 passed
- [x] command-execution-panel.proposal.test → 4 passed
- [x] `pnpm typecheck` → exit 0
- [x] TacticalUnitIcon exports `MY_COMMAND_GOLD`
- [x] BattleMap imports `canCommandUnit`
- [x] InfoPanel contains `selectedUnitCommandRelation` prop (landed via 14-16)

## Self-Check: PASSED
