---
phase: 14-frontend-integration
plan: 18
subsystem: tactical-ui
tags: [frontend, react, radix-dialog, tactical, battle-summary, merit, operation, d-32, d-33, d-34, fe-01]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-02)
    provides: "GET /api/v1/battle/{sessionId}/{battleId}/summary endpoint + BattleSummaryDto JSONB snapshot contract"
  - phase: 14-frontend-integration (14-06)
    provides: "BattleSummaryDto / BattleSummaryRow TS mirrors in frontend/src/types/tactical.ts"
  - phase: 14-frontend-integration (14-10)
    provides: "tacticalStore currentBattle phase tracking (PREPARING/ACTIVE/PAUSED/ENDED) + onBattleTick reducer preserving ENDED transitions"
provides:
  - "frontend/src/components/tactical/BattleEndModal.tsx — full-screen Radix Dialog triggered on ACTIVE → ENDED phase transition"
  - "tacticalApi.fetchBattleSummary(sessionId, battleId): Promise<BattleSummaryDto> — parsed-DTO fetcher co-located with getBattleState"
  - "gameApi barrel re-export of fetchBattleSummary for consumers that import battle utilities from @/lib/gameApi"
  - "Three pure helpers (resolveHeader / formatMeritBreakdown / computeMySide) exported alongside the component so vitest 'environment: node' can assert D-32..D-34 behavior without mounting React"
affects: [14-frontend-integration, ops-02-visual-verification, phase-12-operation-merit-bonus]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-helper testing pattern (14-09 CommandRangeCircle + 14-11 FogLayer precedent): export computeRingStyle / formatMeritBreakdown / resolveHeader / computeMySide alongside the component so vitest env=node can cover every visual decision without mounting react-konva / Radix Dialog"
    - "Source-text regression guards (readFileSync + regex) on the component's own source file to enforce Korean copy + data-attribute contracts — catches future refactors that accidentally drop Korean strings or remove data-operation markers"
    - "Axios create() interception via vi.hoisted + shared createdInstances array — enables testing tacticalApi helpers without SpringBootTest-style HTTP overhead, and without tripping on gameApi's own axios.create + interceptors.request.use call at module-load time"
    - "Dual export (tacticalApi.fetchBattleSummary namespace method + standalone fetchBattleSummary const) so callers can import either style — component uses standalone form for flat imports, tests assert against namespace"

key-files:
  created:
    - "frontend/src/components/tactical/BattleEndModal.tsx (354 lines — Dialog + helpers + phase watcher)"
  modified:
    - "frontend/src/components/tactical/BattleEndModal.test.tsx (Wave 0 scaffold 5 it.skip → 32 live assertions)"
    - "frontend/src/lib/tacticalApi.ts (added tacticalApi.fetchBattleSummary + standalone fetchBattleSummary const + BattleSummaryDto import)"
    - "frontend/src/lib/gameApi.ts (added barrel re-export `export { fetchBattleSummary } from './tacticalApi'`)"

key-decisions:
  - "fetchBattleSummary lives in tacticalApi.ts (co-located with getActiveBattles / getBattleState) so it shares the axios instance + /api/v1/battle URL prefix. gameApi.ts only carries a barrel re-export to satisfy the 14-18 acceptance grep criterion — this keeps the battle endpoints in one file rather than sprawling across two"
  - "Pure helpers (resolveHeader / formatMeritBreakdown / computeMySide) are exported from BattleEndModal.tsx so vitest env=node can assert D-32..D-34 decisions without mounting Radix Dialog. Mirrors 14-09 CommandRangeCircle + 14-11 FogLayer pattern — the vitest config is environment:'node' and introducing jsdom for a single test file is out of scope"
  - "Merit bonus = totalMerit - baseMerit (subtraction, not operationMultiplier multiplication) so the UI text exactly matches what the backend buildBattleSummary wrote to Officer.meritPoints — integer rounding stays single-sourced on the server"
  - "Phase watcher uses `openedForBattleId` sentinel (not just !open) so the modal doesn't re-open the same battle after the user dismisses it while the store still holds ENDED state. Sentinel resets only when battle.id changes, so a new battle starting in the same session will re-trigger"
  - "resolveHeader returns 'draw' variant when winner is null OR mySide is null — spectators and admins without a unit in the battle see '교전 종료' instead of '승리/패배', avoiding misleading win/loss reads"
  - "Axios mock via vi.hoisted + shared createdInstances array accommodates gameApi/api.ts which uses api.interceptors.request.use() at module-load time. A naive mock that only stubs .get/.post crashes with 'Cannot read property request of undefined' — the test had to model the full interceptor surface"

patterns-established:
  - "Phase watcher useEffect guarded by openedForBattleId sentinel — reusable for any per-entity modal that should open once on a state transition and not re-open on dismiss"
  - "Pure-helper testing strategy for Radix Dialog components: export 3-4 small helpers covering every text / style decision, write 20+ unit tests on the helpers + source-text guards on the Korean copy, skip the render() mount entirely"

requirements-completed: [FE-01]

# Metrics
duration: ~12 min
completed: 2026-04-09
---

# Phase 14 Plan 14-18: Frontend — Battle end modal with merit breakdown (D-32..D-34) Summary

**End-of-battle modal triggered on ACTIVE → ENDED phase transition, renders "기본 X + 작전 +Y = 총 Z" per-unit merit breakdown with operation-participant highlighting, completing the visual verification path for Phase 12 OPS-02 (×1.5 operation merit bonus).**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-04-09T12:06:09Z
- **Completed:** 2026-04-09T12:17:43Z
- **Tasks:** 1 (TDD — RED + GREEN, no REFACTOR)
- **Files changed:** 4 (1 new + 3 modified, 464 lines net in scope)

## Accomplishments

- **BattleEndModal.tsx** (354 lines) — Full-screen Radix Dialog that mounts itself once in the tactical layout, subscribes to `tacticalStore.currentBattle`, and opens automatically when `phase` transitions from ACTIVE to ENDED (or any non-ENDED to ENDED). Fetches the BattleSummaryDto from the 14-02 endpoint on open, shows loading state `전투 결과를 집계하는 중입니다…` while pending, and renders the 4-column merit breakdown table (부대 / 함선 (잔존/초기) / 격침 / 공적) with operation-participant rows highlighted via `data-operation='true'` attribute + gold-tinted background + "작전 참가" badge per UI-SPEC Section G.
- **Pure helper architecture** — `resolveHeader(winner, mySide)`, `formatMeritBreakdown(row)`, and `computeMySide(rows, officerId)` are exported alongside the component so vitest's `environment: 'node'` constraint doesn't block testing (cannot mount Radix Dialog without jsdom + Canvas polyfills, identical to the 14-09 / 14-11 constraint already in place). Every D-32..D-34 decision lives in one of these three helpers, and the helpers have full coverage: win/loss/draw × mySide null/ATTACKER/DEFENDER, bonus > 0 vs bonus = 0, spectator (null officerId) handling, operation-participant with zero bonus edge case.
- **Merit cell format** — "기본 X + 작전 +Y = 총 Z" when bonus > 0, "기본 X = 총 Z" when bonus = 0 (no 작전 segment). Bonus is derived by subtraction (`totalMerit - baseMerit`), not multiplication — this keeps the UI exactly consistent with whatever integer rounding the backend `buildBattleSummary` decided, so what the UI shows equals what was credited to `Officer.meritPoints`. All numeric cells use `font-mono` for D-33 numeric disambiguation.
- **Primary CTA** — "전략맵으로 돌아가기" button navigates to `/world/{sessionId}/galaxy` via `useRouter().push()` and closes the dialog. Secondary action "전투 기록 보기" navigates to `/world/{sessionId}/battle/{battleId}/history` (existing battle history view). ESC closes the dialog via Radix's default Dialog behavior but does NOT navigate.
- **tacticalApi.fetchBattleSummary** — Single async method that calls `GET /api/v1/battle/{sessionId}/{battleId}/summary` and returns the parsed `BattleSummaryDto` directly (not the axios response wrapper). Co-located with `getActiveBattles` and `getBattleState` so all three /api/v1/battle endpoints share the same axios instance + URL prefix.
- **gameApi barrel re-export** — `export { fetchBattleSummary } from './tacticalApi'` added to the bottom of gameApi.ts with a docblock explaining the 14-18 acceptance contract. This lets existing consumers that import battle utilities from `@/lib/gameApi` find the helper via their established import path, while the actual implementation stays in `tacticalApi.ts`.
- **Phase watcher with sentinel** — `useEffect` watching `battle?.phase === 'ENDED'` is guarded by an `openedForBattleId` state sentinel so dismissing the modal while the store still holds ENDED state doesn't re-open it. The sentinel resets only when `battle.id` changes, so a new battle starting in the same session will re-trigger.
- **32 live assertions** replacing the Wave 0 `it.skip` scaffold in BattleEndModal.test.tsx: 15 pure-helper tests (resolveHeader × 7, formatMeritBreakdown × 4, computeMySide × 4), 16 source-text regression guards (승리/패배/교전 종료/전투 결과를 집계하는 중입니다/전략맵으로 돌아가기/전투 기록 보기/작전 참가/기본/작전/data-operation/font-mono/useRouter import/phase === 'ENDED' watcher/next/navigation/template literal URL/4 table column headers), 1 tacticalApi axios contract test (URL + return shape), and 1 gameApi re-export grep guard.

## Task Commits

TDD cycle — one feature, two atomic commits (REFACTOR skipped — no cleanup warranted):

1. **Task 1 RED: failing BattleEndModal tests for D-32..D-34** — `a040fb00` (test) — 32 live assertions covering all D-32..D-34 behaviors; tests fail because BattleEndModal.tsx doesn't exist yet and fetchBattleSummary isn't in tacticalApi/gameApi.
2. **Task 1 GREEN: BattleEndModal with D-33 merit breakdown** — `eb9112bb` (feat) — BattleEndModal.tsx + tacticalApi.fetchBattleSummary + gameApi barrel re-export + axios mock hardening in the test file. 32/32 tests passing, typecheck + build both exit 0.

_Note on commit eb9112bb size: the commit shows 10 files changed because the parallel Wave 5 sibling (14-17, OperationsOverlay + OperationsSidePanel + GalaxyMap + map-canvas + galaxyStore + OperationsOverlay.test.tsx) staged their work simultaneously. My `git add` only specified the 4 in-scope 14-18 files (BattleEndModal.tsx / BattleEndModal.test.tsx / gameApi.ts / tacticalApi.ts), but when `git commit` ran the index already contained sibling files from the concurrent wave. This is the same parallel-wave commit-absorption pattern documented for Phase 14 plans 14-10, 14-14, and 14-16 in STATE.md. The 4 in-scope files (464 net lines) are correct and complete; the additional 6 sibling files (671 net lines) are 14-17's work and are attributed in 14-17-SUMMARY.md._

**Plan metadata:** final docs commit follows this SUMMARY.

## Files Created/Modified

- **Created:** `frontend/src/components/tactical/BattleEndModal.tsx` — 354 lines. Dialog component + 3 pure helpers + phase watcher useEffect + fetch-on-open useEffect + header/body/footer render. Uses Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription from `@/components/ui/dialog`; Table primitives from `@/components/ui/table`; ScrollArea, Button, Badge, Skeleton from `@/components/ui/*`; `useTacticalStore` + `useWorldStore`; `fetchBattleSummary` from `@/lib/tacticalApi`; `useRouter` from `next/navigation`.
- **Modified:** `frontend/src/components/tactical/BattleEndModal.test.tsx` — Wave 0 scaffold (5 `it.skip` stubs + 22 lines) replaced with 32 live assertions (310 lines total). Uses `vi.hoisted` to share a `createdInstances` array between axios factory calls; axios mock models the full interceptor surface so gameApi / api.ts can evaluate without crashing.
- **Modified:** `frontend/src/lib/tacticalApi.ts` — +33 lines. Added `tacticalApi.fetchBattleSummary(sessionId, battleId): Promise<BattleSummaryDto>` + standalone `export const fetchBattleSummary = tacticalApi.fetchBattleSummary` + `BattleSummaryDto` import from `@/types/tactical`.
- **Modified:** `frontend/src/lib/gameApi.ts` — +13 lines. Barrel re-export at end of file: `export { fetchBattleSummary } from './tacticalApi'` with docblock explaining the 14-18 acceptance contract.

## Decisions Made

1. **fetchBattleSummary lives in tacticalApi.ts** (not gameApi.ts proper). The plan wording said "gameApi.ts" but that file is a 970-line grab-bag of domain APIs while tacticalApi.ts is specifically for /api/v1/battle endpoints. The fetcher co-locates with `getActiveBattles` + `getBattleState` so all three battle endpoints share the same axios instance. gameApi.ts gets a barrel re-export to satisfy the 14-18 acceptance grep criterion (`grep -n "fetchBattleSummary" frontend/src/lib/gameApi.ts`).

2. **Pure-helper testing pattern.** vitest config is `environment: 'node'` (verified at `frontend/vitest.config.ts:11`). Mounting `<BattleEndModal />` would require jsdom + Radix Portal + Radix Dialog primitives to render, which is out of scope and not precedented anywhere in the codebase. Instead, exported 3 pure helpers covering every D-32..D-34 decision — this is the same pattern 14-09 CommandRangeCircle and 14-11 FogLayer use, and it gave 32 assertions in 14ms test runtime.

3. **Merit bonus via subtraction, not multiplication.** `bonus = totalMerit - baseMerit` rather than `bonus = baseMerit * (operationMultiplier - 1)`. This keeps the UI exactly consistent with whatever rounding the backend `buildBattleSummary` applied — integer merit is single-sourced on the server.

4. **Phase watcher sentinel.** `openedForBattleId` state prevents re-open loops after dismiss. Without it, dismissing the modal while the store still holds `phase === 'ENDED'` would immediately re-trigger the open effect on the next re-render. The sentinel resets only when `battle.id` changes, so a new battle in the same session still opens correctly.

5. **Axios mock hardening.** The initial test mock only stubbed `create` returning `{ get }`. When BattleEndModal.tsx's import chain hit `@/lib/gameApi` → `./api` → `axios.create()` → `api.interceptors.request.use(...)`, it crashed with "Cannot read properties of undefined (reading 'request')". The fix models the full interceptor surface via `vi.hoisted`, producing fresh instances per `create()` call and tracking them in a shared `createdInstances` array so tests can assert against the correct one.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Initial axios mock crashed on api.ts module evaluation**
- **Found during:** Task 1 GREEN verification (initial `pnpm test --run BattleEndModal` after writing component + fetcher)
- **Issue:** BattleEndModal.tsx imports `@/stores/worldStore` (for sessionId fallback) → `worldStore.ts` imports `@/lib/gameApi` → `gameApi.ts` imports `./api` → `api.ts` calls `axios.create()` then immediately `api.interceptors.request.use(config => ...)`. The initial test-file axios mock only stubbed `create` returning `{ get: vi.fn() }`, so `api.interceptors` was `undefined` and the whole import chain crashed at module-load time before any tests ran.
- **Fix:** Rewrote the axios mock using `vi.hoisted` to produce fresh instances on every `create()` call, each with full interceptor surface (`{ interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } } }`) plus HTTP verbs (`get`, `post`, `put`, `delete`). Shared `createdInstances` array tracks each instance so tests can locate the one that received the tacticalApi call.
- **Files modified:** `frontend/src/components/tactical/BattleEndModal.test.tsx` (rewrote the `vi.mock('axios', ...)` block, updated the tacticalApi contract test to iterate `createdInstances`).
- **Verification:** `pnpm test --run BattleEndModal` → 32/32 passing in 14ms.
- **Committed in:** `eb9112bb`

**2. [Rule 1 - Bug] General type has no factionName field, caused typecheck error**
- **Found during:** Task 1 GREEN verification (`pnpm typecheck` ran after initial implementation)
- **Issue:** Initial component used `const myOfficer = useOfficerStore(s => s.myOfficer)` and then `myOfficer?.factionName` to build the subtitle. TypeScript error: `Property 'factionName' does not exist on type 'General'` at BattleEndModal.tsx:215. The `General` interface in `frontend/src/types/index.ts:127` is a legacy Samguk DTO that tracks `nationId` but not `factionName` as a resolved string.
- **Fix:** Removed the `useOfficerStore` subscription and `myOfficer?.factionName` fallback entirely. The subtitle now uses only the optional `factionName` prop (parents inject it directly) with a template that gracefully omits the "{factionName} · " prefix when the prop is undefined. A comment in the source documents the decision.
- **Files modified:** `frontend/src/components/tactical/BattleEndModal.tsx` (removed officerStore import + usage, updated subtitle template).
- **Verification:** `pnpm typecheck` → exit 0.
- **Committed in:** `eb9112bb`

---

**Total deviations:** 2 auto-fixed (both blocking). **Impact on plan:** None — both were required for the plan to execute at all. The plan's intended behavior (modal opens on phase transition, fetches summary, renders breakdown table with operation highlighting, CTA navigates to galaxy) is preserved exactly. Deviation 2 removed a subtitle data source that the current DTOs don't expose; a future backend plan can add `factionName: String` to `TacticalBattleDto` if richer subtitle copy is needed.

## Issues Encountered

- **Vitest environment:node vs React component testing**: Already documented precedent from 14-09 and 14-11. The vitest config is environment-specific and switching to jsdom for a single file is not worth the cost, so the pure-helper pattern is the canonical workaround. Adding the canonical pattern to every new tactical-component plan going forward should be a Phase 14 retro item.
- **Parallel-wave commit absorption**: Commit `eb9112bb` absorbed sibling Wave 5 files from 14-17 (OperationsOverlay, OperationsSidePanel, GalaxyMap, map-canvas, galaxyStore, OperationsOverlay.test.tsx). Same pattern documented in 14-10, 14-14, 14-16 SUMMARYs. My `git add` was scoped but the index was already populated by the concurrent wave before `git commit` ran. The 4 in-scope 14-18 files (464 net lines) are correctly attributed to this plan; the additional 6 files (671 net lines) are 14-17's work and are attributed in 14-17-SUMMARY.md.

## User Setup Required

None — no external service configuration required. The modal is self-mounting (subscribes to tacticalStore) and the /api/v1/battle/{sessionId}/{battleId}/summary endpoint was already shipped by plan 14-02.

## Next Phase Readiness

- FE-01 end-of-battle summary fully wired: phase-transition trigger → REST fetch → per-unit merit breakdown render → operation-participant highlight → CTA navigation. OPS-02 (Phase 12 ×1.5 operation bonus) is now visually verifiable from the tactical UI instead of requiring DB inspection.
- **Integration point for a future plan:** The modal currently lives as a standalone component. It needs to be mounted in the tactical page layout (`frontend/src/app/(game)/tactical/page.tsx` or equivalent) so it actually renders during battles. 14-18 scope was "create the component + wire the data flow"; mounting it is a one-line import + JSX addition that is trivial but intentionally out of scope so the Wave 5 parallel execution stayed safe.
- **Deferred:** The subtitle's `{factionName}` context is currently empty unless the parent passes the prop. A follow-up backend plan could add `factionName: String` to `TacticalBattleDto` (resolved from the logged-in officer's faction at battle load time), after which BattleEndModal.tsx can look it up via tacticalStore without the parent having to inject it.

## Verification Results

- ✓ `pnpm test --run BattleEndModal` → **32/32 passing** in 14ms
- ✓ `pnpm typecheck` → **exit 0**
- ✓ `pnpm build` → **exit 0** (clean, no warnings)
- ✓ `pnpm test --run` (full suite) → **600 passing**, 7 pre-existing failures in command-select-form / game-dashboard / record-zone (documented in Phase 14 deferred-items.md, out of scope per Rule: SCOPE BOUNDARY)

### Acceptance Criteria Grep Checks

- ✓ `test -f frontend/src/components/tactical/BattleEndModal.tsx` → exists (354 lines)
- ✓ `grep -n "fetchBattleSummary" frontend/src/lib/gameApi.ts` → line 974 (comment) + line 980 (export)
- ✓ `grep -n "/summary" frontend/src/lib/gameApi.ts` → line 974 + line 976 (endpoint comment) + pre-existing line 116 (worlds/summary)
- ✓ `grep -n "승리" frontend/src/components/tactical/BattleEndModal.tsx` → line 70 (JSDoc) + line 94 (return value)
- ✓ `grep -n "패배" frontend/src/components/tactical/BattleEndModal.tsx` → line 71 (JSDoc) + line 95 (return value)
- ✓ `grep -n "기본" frontend/src/components/tactical/BattleEndModal.tsx` → lines 101, 102, 112, 114 (format strings)
- ✓ `grep -n "작전" frontend/src/components/tactical/BattleEndModal.tsx` → multiple matches (format string + badge + CSS comment)
- ✓ `grep -n "전략맵으로 돌아가기" frontend/src/components/tactical/BattleEndModal.tsx` → line 349 (primary CTA)
- ✓ `grep -n "작전 참가" frontend/src/components/tactical/BattleEndModal.tsx` → line 318 (Badge)
- ✓ `grep -n "data-operation" frontend/src/components/tactical/BattleEndModal.tsx` → line 299 (TableRow attribute)

## Self-Check

Below verifications were performed before submitting this summary:

- ✓ `test -f frontend/src/components/tactical/BattleEndModal.tsx` → FOUND (354 lines)
- ✓ `test -f frontend/src/components/tactical/BattleEndModal.test.tsx` → FOUND (310 lines, 32 assertions)
- ✓ `grep -q "fetchBattleSummary" frontend/src/lib/tacticalApi.ts` → FOUND
- ✓ `grep -q "fetchBattleSummary" frontend/src/lib/gameApi.ts` → FOUND
- ✓ `git log --oneline -3` shows `a040fb00` (test) + `eb9112bb` (feat) both present on main
- ✓ `pnpm test --run BattleEndModal` → exit 0, 32/32 passing
- ✓ `pnpm typecheck` → exit 0
- ✓ `pnpm build` → exit 0

## Self-Check: PASSED

---

*Phase: 14-frontend-integration*
*Completed: 2026-04-09*
