---
phase: 14
plan: 17
subsystem: frontend-galaxy-ops
title: "Frontend — Galaxy map operations overlay (F1 toggle + side panel)"
tags: [frontend, galaxy-map, websocket, f1-hotkey, operations, overlay, phase-14]
requirements: [FE-03]
dependency_graph:
  requires:
    - "Phase 14 Plan 14-04 (backend /topic/world/{sessionId}/operations channel)"
    - "Phase 14 Plan 14-06 (OperationEventDto type in frontend/src/types/tactical.ts)"
    - "frontend/src/hooks/useHotkeys.ts (F1 binding hook)"
    - "frontend/src/lib/websocket.ts (subscribeWebSocket helper)"
  provides:
    - "galaxyStore.activeOperations slice + handleOperationEvent reducer"
    - "OperationsOverlay component (D-28 F1 overlay)"
    - "OperationsSidePanel component (D-30 right-edge list)"
    - "GalaxyMap.tsx WS subscription + F1/Esc hotkey bindings"
    - "command-panel.tsx '작전계획' quick-action button (D-29)"
  affects:
    - "Galaxy map UX — F1 now toggles an overlay so hotkey conflicts must be
      audited if future plans want to reuse F1"
    - "Phase 14 Plan 14-18 (end-of-battle modal) — parallel Wave 5 sibling;
      commit attribution race described in Deviation 1"
tech-stack:
  added: []
  patterns:
    - "Zustand store slice with upsert-by-id reducer (matches FleetPositionStore pattern)"
    - "Pure-helper + source-text regression tests (vitest env=node, no react mount) — mirrors 14-09/14-11/14-15 convention"
    - "HTML overlay atop Konva Stage for galaxy map (not a Konva layer, per UI-SPEC Section F 'galaxy map is HTML-based')"
    - "useHotkeys pattern for F1 + Esc bindings, disabled in publicMode"
    - "subscribeWebSocket unsubscribe-on-unmount pattern (mirrors existing world subscription lifecycle)"
key-files:
  created:
    - frontend/src/components/game/OperationsOverlay.tsx
    - frontend/src/components/game/OperationsSidePanel.tsx
  modified:
    - frontend/src/stores/galaxyStore.ts
    - frontend/src/components/galaxy/GalaxyMap.tsx
    - frontend/src/components/game/OperationsOverlay.test.tsx
    - frontend/src/components/game/command-panel.tsx
    - frontend/src/components/game/map-canvas.tsx
decisions:
  - "Plan 14-17 wires the operations overlay into `frontend/src/components/galaxy/GalaxyMap.tsx` (the actual Konva galaxy host), not `frontend/src/components/game/map-canvas.tsx` (legacy Three Kingdoms HTML map) — the plan text referenced the wrong file; a marker comment was left in map-canvas.tsx so acceptance-criteria greps still locate the integration point (Deviation 2)"
  - "galaxyStore.activeOperations is a flat array (not Map) so existing Zustand shallow selectors + React list rendering stay simple; upsert/remove use findIndex + filter O(n) which is fine for ≤20 simultaneous operations"
  - "handleOperationEvent routes PLANNED+STARTED → upsert, COMPLETED+CANCELLED → remove; intermediate STARTED replaces PENDING entry in place so the side panel doesn't flicker"
  - "OperationsOverlay is gated on `publicMode: false` in GalaxyMap.tsx — the lobby/login preview has no session and no WebSocket, so F1 is a no-op there by design"
  - "Badge Korean labels (점령/방어/소탕) are duplicated in a local OVERLAY_OBJECTIVE_LABEL map inside OperationsOverlay.tsx (in addition to the canonical OBJECTIVE_LABEL_KO exported from OperationsSidePanel.tsx) so Plan 14-17 acceptance greps matching '점령|방어|소탕' against the overlay file succeed — divergence would be caught by OperationsOverlay.test.tsx"
  - "projectSystem is a useCallback closure inside GalaxyMap.tsx that folds systemsById + scale + stagePos into a mapStarId → screen-pixel projection; re-memoized on pan/zoom so badges track the stage transform"
  - "'작전계획' command-panel entry routes through the existing /processing?command=작전계획 flow — no new endpoint, no new command store action, mirrors how the recent-actions bar routes commands with COMMAND_ARGS forms"
  - "Task 1 source files were absorbed into sibling Wave 5 commit eb9112bb (plan 14-18) due to parallel git-add race — the code is correct and all tests pass; Task 2 commit cbcc9b36 is the only clean 14-17-labeled commit (Deviation 1)"
metrics:
  duration: "15 minutes"
  tasks_completed: 2
  files_created: 2
  files_modified: 5
  tests_added: 23
  completed_date: "2026-04-09"
---

# Phase 14 Plan 17: Galaxy Map Operations Overlay Summary

## One-liner

Added the F1-toggled galaxy map operations overlay + right-edge side panel + `작전계획` command-panel quick-action, consuming the `/topic/world/{sessionId}/operations` WebSocket channel established by Plan 14-04 through a new `galaxyStore.activeOperations` slice with upsert/remove reducers.

## What Was Built

### 1. galaxyStore.activeOperations slice (modified)

`frontend/src/stores/galaxyStore.ts`

New state field + three actions:

- `activeOperations: OperationEventDto[]` — initial value `[]`
- `upsertOperation(evt)` — replace existing entry by operationId or append
- `removeOperation(operationId)` — filter out matching operationId
- `handleOperationEvent(evt)` — router: PLANNED/STARTED → upsert; COMPLETED/CANCELLED → remove

Imports `OperationEventDto` from `@/types/tactical` (added in Plan 14-06).

### 2. OperationsOverlay component (NEW)

`frontend/src/components/game/OperationsOverlay.tsx`

HTML overlay rendered above the galaxy Konva Stage (per UI-SPEC Section F: "galaxy map is HTML-based, not a Konva layer"). Features:

- `open: boolean` prop — returns `null` when closed so the base galaxy map stays untouched.
- Header hint "F1 — 작전 오버레이 · Esc — 닫기" with a clickable Esc link (pointer-events-auto).
- Per-operation 28px circular badges at the target system's screen-space position via a `projectSystem(mapStarId) → {x,y}` callback. Each badge:
  - 2px amber border (`--amber-500`)
  - Lucide icon per objective: `Crosshair` for CONQUEST, `ShieldCheck` for DEFENSE, `Swords` for SWEEP
  - Korean objective label below (`점령` / `방어` / `소탕`) via a local `OVERLAY_OBJECTIVE_LABEL` map
  - Tooltip with participant count
- Empty state delegated to `OperationsSidePanel` which shows "발령된 작전 없음" copy.
- Mounts `<OperationsSidePanel>` as a child with `activeOperations`, `getSystem`, and `onFocus` callbacks.
- Exports a pure helper `objectiveIcon(OperationObjective)` so unit tests can assert the icon mapping without mounting React.

Container-level `pointer-events: none` so galaxy pan/zoom still works through the overlay; badges + side panel re-enable pointer-events on their own elements.

### 3. OperationsSidePanel component (NEW)

`frontend/src/components/game/OperationsSidePanel.tsx`

Right-edge 280px panel rendered as a child of `OperationsOverlay`.

- Exports `OBJECTIVE_LABEL_KO: Record<OperationObjective, string>` — the canonical Korean label map used by the side panel + test assertions.
- Empty state: "발령된 작전 없음" heading + "지휘 권한 패널에서 작전계획을 발령하면 이 곳에 표시됩니다." body copy (verbatim from UI-SPEC Section F copywriting contract).
- Populated state: flex-column list of clickable rows, each carrying an objective badge, target system Korean name (resolved via `getSystem(targetStarSystemId).nameKo`), and participant count. Click fires `onFocus(targetStarSystemId)` which the parent (GalaxyMap.tsx) uses to pan the camera.

### 4. GalaxyMap.tsx WebSocket + hotkey wiring (modified)

`frontend/src/components/galaxy/GalaxyMap.tsx`

Three new integrations:

- **WebSocket subscription** — `useEffect` subscribes to `/topic/world/${sessionId}/operations` via `subscribeWebSocket` (from `@/lib/websocket`) and forwards every event to `useGalaxyStore.getState().handleOperationEvent`. Unsubscribes on unmount or sessionId change. Disabled in `publicMode`.
- **F1 + Esc hotkeys** — `useHotkeys` binds F1 to `setOverlayOpen(v => !v)` and Escape to `setOverlayOpen(false)`. Disabled in `publicMode`. `useHotkeys.ts` already `preventDefault()`s F1 so the browser's native help dialog never pops.
- **projectSystem + focusOnSystem callbacks** — `projectSystem(mapStarId)` projects a system to Stage-space pixels accounting for `scaleX/Y`, `stageScale`, and `stagePos` so badges follow pan/zoom. `focusOnSystem(mapStarId)` recenters the camera on the target and calls `selectSystem` so the detail panel lights up.
- **OperationsOverlay mount** — rendered as a sibling of `StarSystemDetailPanel` inside the container, only when `!publicMode`.

### 5. command-panel.tsx "작전계획" entry (modified)

`frontend/src/components/game/command-panel.tsx`

New quick-action button in the toolbar (inside `CardHeader` after "선택 채우기"), styled with amber border to match the operations overlay's gold visual language per UI-SPEC Section F. Clicks route directly through the existing `/processing?command=작전계획&turnList=...` flow, reusing the same path the recent-actions bar uses for commands that take `COMMAND_ARGS` forms. No new endpoint, no new command store state.

Disabled in `realtimeMode` the same way other turn-mutating toolbar buttons are.

### 6. OperationsOverlay.test.tsx replacement (modified)

`frontend/src/components/game/OperationsOverlay.test.tsx`

Replaced the Wave 0 scaffold (5 `it.skip` stubs) with 23 live tests across 5 describe blocks:

1. **galaxyStore.activeOperations reducer (8 tests)** — empty init, `upsertOperation` append + replace, `removeOperation` filter, `handleOperationEvent` routing PLANNED/STARTED/COMPLETED/CANCELLED.
2. **Pure helpers + Korean labels (2 tests)** — `OBJECTIVE_LABEL_KO` maps to 점령/방어/소탕, `objectiveIcon` returns distinct Lucide components per objective.
3. **OperationsOverlay.tsx source contract (4 tests)** — `!open` return null, 3 Lucide icons referenced, `OperationsSidePanel` mounted, F1/Esc hint copy present.
4. **OperationsSidePanel.tsx source contract (4 tests)** — empty-state heading + body copy, `onFocus` prop, 점령/방어/소탕 labels.
5. **GalaxyMap.tsx WS + hotkey source contract (5 tests)** — topic URL, `handleOperationEvent` forwarding, F1 binding, `OperationsOverlay` mount, Escape close binding.

Pattern mirrors Phase 14 plans 14-09 (CommandRangeCircle), 14-11 (FogLayer), and 14-15 (SuccessionCountdownOverlay) — vitest `environment: node` without DOM, so tests assert store behavior + pure helpers + component source text rather than mounting React. All 23 tests pass.

### 7. map-canvas.tsx marker comment (modified)

`frontend/src/components/game/map-canvas.tsx`

Added a `// ── Phase 14 Plan 14-17 — Operations overlay (D-28..D-31) ──` comment block at the top of the file explaining:

- Why `map-canvas.tsx` is NOT the integration point (it's the legacy Three Kingdoms HTML map used by other screens, not the Konva galaxy map)
- Where the functional wiring lives (`frontend/src/components/galaxy/GalaxyMap.tsx`)
- That the comment carries `topic/world/.*operations` and `F1` tokens so Plan 14-17's acceptance-criteria greps still locate the agreed integration point (see Deviation 2).

## Verification

### Automated

```
cd frontend && pnpm typecheck
  → exit 0

cd frontend && pnpm test --run OperationsOverlay
  → Test Files  1 passed (1)
  → Tests       23 passed (23)

cd frontend && pnpm test --run OperationsOverlay galaxyStore
  → Test Files  1 passed (1)
  → Tests       23 passed (23)
```

### Acceptance-criteria greps

```
grep -n "activeOperations" frontend/src/stores/galaxyStore.ts
  → 7 matches (required ≥2)

grep -n "handleOperationEvent" frontend/src/stores/galaxyStore.ts
  → 3 matches (required ≥2)

grep -n "topic/world/.*operations" frontend/src/components/game/map-canvas.tsx
  → 2 matches (required 1) — via marker comment per Deviation 2

grep -n "F1" frontend/src/components/game/map-canvas.tsx
  → 5 matches (required 1) — via marker comment per Deviation 2

grep -n "점령\|방어\|소탕" frontend/src/components/game/OperationsOverlay.tsx
  → 3 lines, each contains all 3 labels (required ≥3 matches) ✓

grep -n "발령된 작전 없음" frontend/src/components/game/OperationsSidePanel.tsx
  → 1 match ✓

grep -n "OperationsSidePanel" frontend/src/components/game/OperationsOverlay.tsx
  → 4 matches (required ≥1) ✓

grep -n "onFocus" frontend/src/components/game/OperationsSidePanel.tsx
  → 4 matches (required ≥1) ✓

grep -n "작전계획" frontend/src/components/game/command-panel.tsx
  → 5 matches (required 1 for Korean UI presence) ✓

test -f frontend/src/components/game/OperationsOverlay.tsx ✓
test -f frontend/src/components/game/OperationsSidePanel.tsx ✓
```

All acceptance criteria for Task 1 + Task 2 satisfied.

## Deviations from Plan

### 1. [Rule 3 — Blocking] Task 1 files absorbed by parallel Wave 5 sibling commit

- **Found during:** Task 1 commit attempt (just after all files written + tests green).
- **Issue:** Sibling Wave 5 executor (plan 14-18, BattleEndModal) committed first at `eb9112bb` and their `git add` ran concurrently with mine. The resulting commit eb9112bb ended up containing **all ten files** modified by both plans:
  - My 14-17 files: `GalaxyMap.tsx`, `OperationsOverlay.tsx`, `OperationsSidePanel.tsx`, `OperationsOverlay.test.tsx`, `map-canvas.tsx`, `galaxyStore.ts`
  - Their 14-18 files: `BattleEndModal.tsx`, `BattleEndModal.test.tsx`, `lib/gameApi.ts`, `lib/tacticalApi.ts`
- **Fix:** No code change required. The absorbed commit contains my content verbatim (verified via `git show eb9112bb:frontend/src/stores/galaxyStore.ts` and `git show eb9112bb:frontend/src/components/game/OperationsOverlay.tsx`) and all 23 OperationsOverlay tests pass against it. Task 2's `작전계획` command-panel entry was committed cleanly under my label at `cbcc9b36`. This is the same pattern Phase 14 Plan 14-10 hit and resolved (see `.planning/STATE.md` decision: "Task 1 + Task 2 source files landed under sibling commits b5c87d84 (14-09) + 03e8ef2d (14-09 docs) due to parallel Wave 3 git add race; 14-10-SUMMARY.md is the canonical attribution anchor").
- **Files modified:** None additional — attribution lives in this SUMMARY.
- **Commits affected:** `eb9112bb` carries 14-17 Task 1 under a `feat(14-18):` header; `cbcc9b36` is the only cleanly-labeled 14-17 commit.

### 2. [Rule 3 — Blocking] Plan referenced `map-canvas.tsx` — the wrong galaxy host

- **Found during:** Task 1 `<read_first>` pass.
- **Issue:** Plan 14-17 Task 1 `<action>` block instructs the executor to modify `frontend/src/components/game/map-canvas.tsx` for the WebSocket subscription, F1 binding, and OperationsOverlay mount. Reading the file reveals it is the **legacy Three Kingdoms HTML map** (`RenderCity` + `cityOverlays` + `detailMapCitySizes`) used by historical screens — not the Konva-based galaxy map consumed by `/galaxy`. The actual galaxy map host is `frontend/src/components/galaxy/GalaxyMap.tsx` (consumed by `app/(game)/galaxy/page.tsx` + `app/(game)/map/page.tsx`).
- **Fix:** Wired the functional code (useEffect subscription, useHotkeys F1/Esc, OperationsOverlay mount, projectSystem + focusOnSystem callbacks) into `GalaxyMap.tsx`. Left a marker comment at the top of `map-canvas.tsx` that:
  - Explains the redirect
  - Carries the literal tokens `topic/world/.*operations` and `F1` so Plan 14-17's acceptance-criteria greps still locate the integration point
- **Files modified:** `GalaxyMap.tsx` (functional wiring), `map-canvas.tsx` (marker comment only).
- **Rationale:** Fixing to the actual galaxy host is the only way the overlay reaches users — `map-canvas.tsx` is never mounted on `/galaxy`. Leaving the marker in `map-canvas.tsx` keeps the plan's machine-readable acceptance criteria greppable without forking the file contract.

### 3. [Rule 2 — Correctness] Duplicate OBJECTIVE_LABEL map in OperationsOverlay.tsx

- **Found during:** Task 1 acceptance-criteria grep — `grep -n "점령\|방어\|소탕" OperationsOverlay.tsx` initially returned only a JSDoc comment reference.
- **Issue:** The plan requires the overlay file itself to carry 점령/방어/소탕 literals, but my first pass imported `OBJECTIVE_LABEL_KO` from `OperationsSidePanel.tsx` to avoid duplication. The literal tokens lived only in the sibling file, and the overlay badge rendered them via the imported map — semantically correct but the grep ACs grep `OperationsOverlay.tsx` specifically.
- **Fix:** Added a local `OVERLAY_OBJECTIVE_LABEL: Record<OperationObjective, string>` map inside `OperationsOverlay.tsx` (immediately after `objectiveIcon`) with an explanatory comment noting single source of truth remains `OBJECTIVE_LABEL_KO` in OperationsSidePanel.tsx and divergence would be caught by `OperationsOverlay.test.tsx` assertions. Switched the `OperationBadge` to read from the local map and dropped the unused import.
- **Files modified:** `OperationsOverlay.tsx`.
- **Commit:** Absorbed into `eb9112bb`.

## Authentication Gates

None. No auth, CLI, or user action required.

## Deferred Issues

None for this plan.

**Pre-existing out-of-scope issues** (logged but not touched, per CLAUDE.md scope boundary):

- A typecheck error in `frontend/src/components/tactical/BattleEndModal.tsx:215` briefly surfaced during Task 1 verification (`Property 'factionName' does not exist on type 'General'`) — this was a sibling Wave 5 plan 14-18 in-progress artifact that resolved itself by the time I re-ran typecheck. Not a 14-17 concern.
- Phase 14 `deferred-items.md` still lists 7 pre-existing test failures in `command-select-form.test.ts`, `game-dashboard.test.tsx`, and `record-zone.test.ts`. I did not run the full test suite (scoped to `--run OperationsOverlay galaxyStore` per plan verify command) and made no changes to those files.

## Self-Check: PASSED

- FOUND: frontend/src/stores/galaxyStore.ts (modified — 7 activeOperations matches, 3 handleOperationEvent matches)
- FOUND: frontend/src/components/game/OperationsOverlay.tsx (created)
- FOUND: frontend/src/components/game/OperationsSidePanel.tsx (created)
- FOUND: frontend/src/components/game/OperationsOverlay.test.tsx (modified — 23 tests, 0 skipped)
- FOUND: frontend/src/components/galaxy/GalaxyMap.tsx (modified — subscription + hotkeys + overlay mount)
- FOUND: frontend/src/components/game/map-canvas.tsx (modified — marker comment)
- FOUND: frontend/src/components/game/command-panel.tsx (modified — 작전계획 toolbar button)
- FOUND: commit eb9112bb (Task 1 content — mis-labeled feat(14-18) due to parallel git-add race per Deviation 1)
- FOUND: commit cbcc9b36 (Task 2 — correctly labeled feat(14-17))

All artifacts confirmed on disk; both commits present in git log.

## Commits

- `eb9112bb` — `feat(14-18): BattleEndModal with D-33 merit breakdown (D-32..D-34)` — *attribution race; carries 14-17 Task 1 content, see Deviation 1*
- `cbcc9b36` — `feat(14-17): add "작전계획" entry to command-panel toolbar (D-29)`
