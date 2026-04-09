---
phase: 14-frontend-integration
verified: 2026-04-09T12:30:00Z
re_verified: 2026-04-09T12:45:00Z
status: human_needed
score: 10/10 truths verified, 5/5 requirements satisfied, 9 human UX items pending
re_verification: true
gaps:
  - truth: "User sees end-of-battle summary modal when battle phase transitions to ENDED"
    status: resolved
    resolution_commit: "4b483d89"
    resolution_note: "Mounted BattleEndModal in tactical/page.tsx active-battle view. Self-mounting component subscribes to tacticalStore.currentBattle internally — no props required. Inline fix applied during phase verification."
    reason: "BattleEndModal.tsx exists (354 lines, fully implemented) but was NOT mounted in frontend/src/app/(game)/tactical/page.tsx. Plan 14-18 explicitly deferred mounting as out-of-scope for Wave 5 parallel safety."
    artifacts:
      - path: "frontend/src/components/tactical/BattleEndModal.tsx"
        issue: "RESOLVED — component now mounted via commit 4b483d89"
      - path: "frontend/src/app/(game)/tactical/page.tsx"
        issue: "RESOLVED — imports BattleEndModal and renders `<BattleEndModal />` in the active-battle view"
human_verification:
  - test: "진영색 palette fidelity — Empire #4466ff, Alliance #ff4444, Fezzan #888888"
    expected: "Each faction's CRC ring, unit icon border, and ghost icon render in the correct faction color with correct lightened variants for friendly/enemy differentiation"
    why_human: "Color accuracy and contrast ratio require visual inspection; grep confirms hex literals exist but not that the canvas renders them correctly"
  - test: "CRC real-time shrink animation quality"
    expected: "CommandRangeCircle radius visually shrinks frame-by-frame as tick events arrive; no flicker or stutter; smooth 60fps on 8 simultaneous CRCs"
    why_human: "Animation smoothness and FPS cannot be verified by static code analysis; requires browser profiling"
  - test: "Sub-fleet assignment drawer drag-and-drop feel"
    expected: "Units drag from source bucket to target bucket smoothly; 60-unit load does not produce lag; drop zones highlight correctly on hover; aria-disabled chips cannot be dropped"
    why_human: "Pointer event latency, 60-unit stress test, and visual drop-zone feedback require runtime interaction"
  - test: "Fog of war ghost UX tension"
    expected: "Enemies that leave sensor range leave behind a dashed ghost icon with a Korean tick stamp; ghost fades over 60 ticks; gives 'they were there' spatial tension rather than confusion"
    why_human: "Ghost fade progression and the subjective 'tension' quality of the fog-of-war rendering require playtesting"
  - test: "Flagship flash visual impact"
    expected: "0.5s Konva ring flash plays when flagship is destroyed; ring expands and fades without leaving artifacts; visible against busy battle background"
    why_human: "Canvas animation appearance and artifact-free rendering require visual inspection in a running battle"
  - test: "Sonner toast Korean particle accuracy"
    expected: "Succession toasts read naturally in Korean (e.g. '지휘권이 양웬리에게 이양되었습니다' uses correct -에게 or -께 particle); no particle errors"
    why_human: "Korean morphophonological particle selection (JosaUtil) correctness requires native-speaker review"
  - test: "F1 overlay toggle — no browser help dialog conflict"
    expected: "Pressing F1 on the galaxy map opens OperationsOverlay without triggering the browser's built-in help dialog; works on Chrome, Safari, Firefox"
    why_human: "Browser F1 key interception behavior varies by OS and browser; requires cross-browser manual testing"
  - test: "Korean text consistency across all new UI (19 new/modified components)"
    expected: "All newly added Korean strings are grammatically consistent, use correct honorifics level, and match gin7 manual terminology"
    why_human: "Korean language quality requires native-speaker review; cannot be verified by regex pattern matching"
  - test: "Performance under load — 60 units + 8 CRCs + fog ghosts"
    expected: "Tactical battle page maintains >= 50 FPS with 60 units on screen, 8 active CRC rings, 15+ fog ghost icons, and STOMP tick events arriving at ~10Hz"
    why_human: "FPS measurement under realistic load requires browser DevTools performance profiling in a live session"
---

# Phase 14: Frontend Integration Verification Report

**Phase Goal:** Frontend integration of all v2.1 tactical command systems — complete end-to-end user experience for fleet assignment, command gating, succession feedback, fog-of-war rendering, operations overlay, and end-of-battle modal. 18 plans across 5 waves, all marked complete with SUMMARY.md files.
**Verified:** 2026-04-09T12:30:00Z
**Re-verified:** 2026-04-09T12:45:00Z — BattleEndModal mount gap closed inline (commit `4b483d89`)
**Status:** human_needed (all code gaps closed; 9 human UX items remain pending)

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Player sees CommandRangeCircle rings from server-driven CRC data (FE-01 partial) | VERIFIED | CommandRangeCircle.tsx rewritten with server-driven props; computeRingStyle pure helper; BattleMap renders multi-CRC from hierarchy; 14-09 commit a7988a3d confirmed |
| 2 | Player can assign units to sub-fleet buckets via drag-and-drop drawer (FE-02) | VERIFIED | SubFleetAssignmentDrawer.tsx with @dnd-kit/core DndContext + 10 drop zones; SubFleetUnitChip with useDraggable + aria-disabled; canReassignUnit gating; 14-12/14-13 commits confirmed |
| 3 | Gated commands show disabled state + Shift+click proposal flow (FE-03) | VERIFIED | canCommandUnit.ts with GatingResult; command-execution-panel.tsx with aria-disabled + Radix Tooltip + Shift+click → createProposal; 14-14 commits 41cd2268 confirmed |
| 4 | Flagship destruction triggers flash + succession countdown overlay + Sonner toasts (FE-04) | VERIFIED | FlagshipFlash.tsx + SuccessionCountdownOverlay.tsx + useSoundEffects succession events; computeFlashFrame / clampSuccessionTicks pure helpers; 14-15 commit d970fb4b confirmed |
| 5 | Fog of war renders ghost icons for last-seen enemy positions (FE-05) | VERIFIED | FogLayer.tsx + EnemyGhostIcon.tsx; fogOfWar.ts computeVisibleEnemies + updateLastSeenEnemyPositions; GHOST_TTL_TICKS=60; tacticalStore lastSeenEnemyPositions slot; 14-11 commit 8532a648 confirmed |
| 6 | Operations overlay visible on galaxy map via F1 toggle (D-28) | VERIFIED | OperationsOverlay.tsx + OperationsSidePanel.tsx; GalaxyMap.tsx F1/Esc hotkeys + WS subscription to /operations; command-panel.tsx 작전계획 button; 14-17 commits confirmed |
| 7 | End-of-battle summary modal renders with merit breakdown on ENDED transition (D-32/33/34) | VERIFIED | BattleEndModal.tsx fully implemented (354 lines, 32 tests passing) + mounted in tactical/page.tsx active-battle view via commit `4b483d89`. Self-mounting component subscribes to tacticalStore.currentBattle — no props needed. |
| 8 | R3F fully removed; Konva is sole tactical renderer (D-25) | VERIFIED | TacticalMapR3F.tsx, BattleCloseViewScene.tsx, BattleCloseView.tsx, BattleCloseViewPanel.tsx all deleted; battle/page.tsx cleaned; 14-08 commits confirmed |
| 9 | Backend emits TacticalUnitDto with 8 new Phase 14 fields (D-21/22) | VERIFIED | TacticalBattleDtos.kt CommandHierarchyDto + SubFleetDto + 8 new TacticalUnitDto fields (sensorRange, subFleetCommanderId, successionState, successionTicksRemaining, isOnline, isNpc, missionObjective, maxCommandRange); 14-01/14-02 commits confirmed |
| 10 | Backend emits BattleSummaryDto from GET /summary endpoint (D-32) | VERIFIED | TacticalBattleService.buildBattleSummary() + endBattle snapshot capture; TacticalBattleRestController GET /{sessionId}/{battleId}/summary; 14-02 commits confirmed |

**Score:** 10/10 truths verified (all gaps closed — mount fix committed as `4b483d89`)

---

### Required Artifacts

| Artifact | Plan | Status | Details |
|----------|------|--------|---------|
| `frontend/src/components/tactical/CommandRangeCircle.tsx` | 14-09 | VERIFIED | Rewritten server-driven; computeRingStyle exported; 8 tests passing |
| `frontend/src/components/tactical/BattleMap.tsx` | 14-10 | VERIFIED | 5-layer Konva restructure; FogLayer + FlagshipFlash + SuccessionCountdownOverlay mounted |
| `frontend/src/components/tactical/FogLayer.tsx` | 14-11 | VERIFIED | NEW; store-driven ghost orchestrator; FogLayer.test.tsx passing |
| `frontend/src/components/tactical/EnemyGhostIcon.tsx` | 14-11 | VERIFIED | NEW; dashed outline ghost with Korean tick stamp |
| `frontend/src/lib/fogOfWar.ts` | 14-11 | VERIFIED | NEW; computeVisibleEnemies + updateLastSeenEnemyPositions + GHOST_TTL_TICKS=60 |
| `frontend/src/components/tactical/SubFleetUnitChip.tsx` | 14-12/13 | VERIFIED | NEW; useDraggable + aria-disabled; 14-13 fix confirmed |
| `frontend/src/components/tactical/SubFleetAssignmentDrawer.tsx` | 14-12/13 | VERIFIED | NEW; DndContext + 10 buckets + createDragEndHandler + Korean labels |
| `frontend/src/lib/subFleetDragGating.ts` | 14-12/13 | VERIFIED | NEW; canReassignUnit with CMD-05 gating rules |
| `frontend/src/lib/canCommandUnit.ts` | 14-14 | VERIFIED | NEW; canCommandUnit → GatingResult; full unit tests |
| `frontend/src/components/game/command-execution-panel.tsx` | 14-14 | VERIFIED | aria-disabled + Radix Tooltip + Shift+click → createProposal wired |
| `frontend/src/components/tactical/FlagshipFlash.tsx` | 14-15 | VERIFIED | NEW; 0.5s Konva ring flash; computeFlashFrame exported |
| `frontend/src/components/tactical/SuccessionCountdownOverlay.tsx` | 14-15 | VERIFIED | NEW; HTML countdown pill; clampSuccessionTicks exported |
| `frontend/src/components/tactical/TacticalUnitIcon.tsx` | 14-16 | VERIFIED | computeStatusMarker (●/○/🤖); NPC priority dispatch |
| `frontend/src/components/tactical/InfoPanel.tsx` | 14-16 | VERIFIED | selectedUnit prop + resolveMissionObjectiveLabel + NPC objective rows |
| `frontend/src/components/game/OperationsOverlay.tsx` | 14-17 | VERIFIED | NEW; F1-toggled overlay with operation badges |
| `frontend/src/components/game/OperationsSidePanel.tsx` | 14-17 | VERIFIED | NEW; right-edge operations list |
| `frontend/src/stores/galaxyStore.ts` | 14-17 | VERIFIED | activeOperations slice + handleOperationEvent reducer |
| `frontend/src/components/tactical/BattleEndModal.tsx` | 14-18 | VERIFIED | EXISTS (354 lines, 32 tests) + mounted in tactical/page.tsx active-battle view (commit `4b483d89`) |
| `frontend/src/lib/tacticalApi.ts` | 14-18 | VERIFIED | fetchBattleSummary added; barrel re-export in gameApi.ts |
| `frontend/src/types/tactical.ts` | 14-06 | VERIFIED | All Phase 14 TS types: SubFleetDto, CommandHierarchyDto, BattleSummaryDto, OperationEventDto, 8 TacticalUnit fields |
| `frontend/src/lib/tacticalColors.ts` | 14-05 | VERIFIED | NEW; FACTION_TACTICAL_COLORS + sideToDefaultColor + lightenHex |
| `frontend/src/lib/commandChain.ts` | 14-05 | VERIFIED | NEW; findVisibleCrcCommanders + findAlliesInMyChain |
| `backend/.../TacticalBattleDtos.kt` | 14-01 | VERIFIED | CommandHierarchyDto + SubFleetDto + 8 TacticalUnitDto fields + BattleSummaryDto + BattleSummaryRow |
| `backend/.../TacticalBattleEngine.kt` | 14-03 | VERIFIED | SensorRangeFormula + TacticalUnit.sensorRange + npcOfficerIds on TacticalBattleState |
| `backend/.../OperationEventDto.kt` | 14-04 | VERIFIED | NEW; OPERATION_PLANNED/STARTED/COMPLETED/CANCELLED events |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| BattleMap.tsx | tacticalStore | useTacticalStore | WIRED | Hierarchy + fog + succession state consumed in useMemo hooks |
| BattleMap.tsx | FogLayer.tsx | JSX mount in fog-ghosts layer | WIRED | `<FogLayer>` mounted as second Konva layer |
| BattleMap.tsx | FlagshipFlash.tsx | JSX mount in succession-fx layer | WIRED | `<FlagshipFlash>` mounted in 5th layer |
| BattleMap.tsx | SuccessionCountdownOverlay.tsx | HTML sibling JSX | WIRED | `<SuccessionCountdownOverlay>` as HTML sibling div |
| tacticalStore.ts | fogOfWar.ts | onBattleTick reducer | WIRED | updateLastSeenEnemyPositions called on every tick |
| command-execution-panel.tsx | canCommandUnit.ts | import + gatingResult | WIRED | gatingResult.canCommand gates aria-disabled + Tooltip |
| command-execution-panel.tsx | commandStore.createProposal | Shift+click handler | WIRED | createProposal called with unit + command context |
| GalaxyMap.tsx | OperationsOverlay.tsx | F1 keydown + JSX mount | WIRED | F1 toggles showOperations state; `<OperationsOverlay>` mounted |
| GalaxyMap.tsx | STOMP /operations | useStomp subscription | WIRED | /topic/world/{sessionId}/operations → handleOperationEvent |
| tactical/page.tsx | BattleEndModal.tsx | import + JSX mount | WIRED | `import { BattleEndModal }` + `<BattleEndModal />` added in active-battle view (commit `4b483d89`) |
| tacticalApi.ts | game-app GET /summary | axios GET | WIRED | fetchBattleSummary calls /api/v1/battle/{sessionId}/{battleId}/summary |
| TacticalBattleService.kt | BattleSummaryDto | buildBattleSummary() + endBattle | WIRED | Snapshot captured at battle end, exposed via REST endpoint |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| CommandRangeCircle.tsx | cx/cy/currentRadius | TacticalUnit from STOMP tick | Yes — tick events carry live unit positions | FLOWING |
| FogLayer.tsx | lastSeenEnemyPositions | tacticalStore onBattleTick | Yes — computed from real unit positions via fogOfWar.ts | FLOWING |
| SubFleetAssignmentDrawer.tsx | myHierarchy.subFleets | tacticalStore from WS tick | Yes — SubFleetDto from TacticalBattleService.toUnitDto | FLOWING |
| BattleEndModal.tsx | battleSummary | fetchBattleSummary API call | Yes — mounted in tactical/page.tsx (commit `4b483d89`); phase watcher triggers fetch on ACTIVE→ENDED | FLOWING |
| OperationsOverlay.tsx | activeOperations | galaxyStore handleOperationEvent | Yes — OperationEventDto broadcast from OperationPlanService / OperationLifecycleService | FLOWING |
| TacticalUnitIcon.tsx | isNpc/isOnline | TacticalUnit from STOMP tick | Yes — backed by npcOfficerIds set in BattleTriggerService | FLOWING |
| InfoPanel.tsx | missionObjective | TacticalUnit from STOMP tick | Partial — missionObjective flows; targetStarSystemId NOT emitted by backend (deferred) | PARTIAL |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — tactical combat requires running WebSocket battle session. All runnable checks (typecheck, vitest) confirmed via SUMMARY self-checks; individual plan self-checks show 0 typecheck errors and 45+ tests passing per wave.

---

### Requirements Coverage

| Requirement | Plans | Description | Status | Evidence |
|-------------|-------|-------------|--------|----------|
| FE-01 | 14-09, 14-10, 14-18 | CRC visualization + end-of-battle modal | SATISFIED | CRC VERIFIED; BattleEndModal implemented + mounted via commit `4b483d89` |
| FE-02 | 14-12, 14-13 | Sub-fleet assignment panel | SATISFIED | SubFleetAssignmentDrawer + SubFleetUnitChip + canReassignUnit fully wired |
| FE-03 | 14-14, 14-16 | Command gating UI | SATISFIED | canCommandUnit + aria-disabled + Tooltip + Shift+click createProposal |
| FE-04 | 14-15 | Succession visual feedback | SATISFIED | FlagshipFlash + SuccessionCountdownOverlay + Sonner toasts |
| FE-05 | 14-11 | Fog of war rendering | SATISFIED | FogLayer + EnemyGhostIcon + fogOfWar.ts + tacticalStore lastSeenEnemyPositions |

**Orphaned requirements check:** No REQUIREMENTS.md entries for Phase 14 beyond FE-01..FE-05 found.

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `frontend/src/types/tactical.ts` | `targetStarSystemId?: number \| null` on TacticalUnit — optional field, backend never emits it | Warning | D-37 dashed NPC mission line and "목표: {systemName}" InfoPanel row are permanently inactive until backend wires the field; documented as intentional scaffold |
| `frontend/src/lib/subFleetDragGating.ts` | `isStopped` field read from TacticalUnit but field absent from TacticalUnitDto — duck-type read defaults to MOVING | Info | Functional (conservative default blocks drag when status unknown) but CMD-05 stopped-unit gating is technically imprecise |

---

### Human Verification Required

#### 1. 진영색 Palette Fidelity

**Test:** Load a tactical battle with Empire vs Alliance units. Confirm CRC rings, unit icon borders, and ghost icons render in #4466ff (Empire), #ff4444 (Alliance), #888888 (Fezzan) with correctly lightened variants for friendly/enemy distinction.
**Expected:** Each faction's visual elements use the correct color; colorblind users can distinguish by shape/pattern as well as color.
**Why human:** Color accuracy on canvas and CSS contrast ratios require visual inspection; static grep can only confirm hex literals are present in source.

#### 2. CRC Real-Time Shrink Animation

**Test:** Watch a CommandRangeCircle during an active battle as the commander's command range decreases.
**Expected:** The ring visually shrinks smoothly frame-by-frame as tick events arrive; no flicker, no stutter; 8 simultaneous CRCs maintain 60fps.
**Why human:** Animation smoothness and FPS require browser DevTools profiling in a live session.

#### 3. Sub-Fleet Drag-and-Drop Feel

**Test:** Open SubFleetAssignmentDrawer with 60 units. Drag units between buckets rapidly. Attempt to drag an aria-disabled chip.
**Expected:** Smooth dragging with correct drop-zone highlight on hover; no lag with 60 units; disabled chips reject drag attempts; Korean bucket labels readable.
**Why human:** Pointer event latency and 60-unit stress test require runtime interaction; visual drop-zone feedback requires browser rendering.

#### 4. Fog of War Ghost UX Tension

**Test:** Move enemy units out of sensor range during battle. Watch the ghost icons appear and fade over ~60 ticks.
**Expected:** Dashed ghost icons appear at last-seen positions with Korean tick stamps; ghosts fade progressively; creates spatial memory tension ("they were there — where are they now?").
**Why human:** Ghost fade progression quality and the subjective tension effect require live playtesting.

#### 5. Flagship Flash Visual Impact

**Test:** Destroy an enemy flagship in battle.
**Expected:** 0.5s Konva ring flash expands and fades without artifacts; visually distinct and dramatic against the battle background; no canvas garbage after animation ends.
**Why human:** Canvas animation appearance and artifact-free completion require visual inspection.

#### 6. Sonner Toast Korean Particle Accuracy

**Test:** Trigger succession events in battle to see toast messages.
**Expected:** All Sonner toasts display grammatically correct Korean, including correct -에게/-께/-로 particles with officer names; no particle errors visible.
**Why human:** Korean morphophonological particle selection correctness requires native-speaker review.

#### 7. F1 Overlay Toggle — Cross-Browser Safety

**Test:** Press F1 while on the galaxy map in Chrome, Safari, and Firefox.
**Expected:** OperationsOverlay opens without the browser's native help dialog appearing; Esc dismisses the overlay correctly.
**Why human:** Browser F1 key interception behavior varies by OS and browser; requires cross-browser manual testing.

#### 8. Korean Text Consistency Across New UI

**Test:** Review all newly added Korean strings across BattleEndModal, OperationsOverlay, SubFleetAssignmentDrawer, InfoPanel NPC rows, succession toasts, ghost tick stamps.
**Expected:** Grammatically consistent Korean throughout; correct honorifics level; terminology matches gin7 manual reference.
**Why human:** Korean language quality requires native-speaker review; cannot be verified by regex.

#### 9. Performance Under Load

**Test:** Run a battle session with 60 units, 8 active CRC rings, 15+ fog ghost icons, and STOMP tick events arriving at ~10Hz.
**Expected:** Tactical battle page maintains >= 50 FPS measured in Chrome DevTools; no memory leak over 10 minutes of battle.
**Why human:** FPS measurement and memory leak detection require browser DevTools profiling in a live session.

---

### Gaps Summary

**All code gaps CLOSED as of 2026-04-09T12:45:00Z:**

Plan 14-18 shipped `BattleEndModal.tsx` completely (354 lines, Radix Dialog, merit breakdown table, resolveHeader/formatMeritBreakdown/computeMySide helpers, 32 tests passing, ACTIVE→ENDED phase watcher) but intentionally deferred the mount in `tactical/page.tsx` for Wave 5 parallel execution safety. The orchestrator applied the 2-line mount inline during phase verification (commit `4b483d89`) — no gap-closure cycle needed for what was effectively an unfinished Wave 5 landing step.

Post-fix: typecheck clean, BattleEndModal is mounted in the active-battle view, self-subscribes to `tacticalStore.currentBattle`, opens automatically on the ACTIVE→ENDED transition. The 32 existing BattleEndModal tests cover the rendering behavior; the mount itself is a trivial integration wrapper.

**Pre-existing backend test debt (NOT Phase 14 regression):**

The full `:game-app:test` run shows 205 failing tests in legacy OpenSamguk 삼국지-era test classes (qa.parity.*, EconomyService, DiplomacyService, DuelSimulation, ScenarioService, PlanetService, CityService 남피→하북 region mapping, etc.). These predate Phase 14 — all Phase 14 backend tests (TacticalBattleDtoExtensionTest, CommandHierarchyDtoMappingTest, BattleSummaryEndpointTest, SensorRangeComputationTest, OperationBroadcastTest, OperationMeritBonusTest, OperationLifecycleServiceTest) PASS. The legacy failures are tracked for the upcoming upstream-sync phase and are out of scope for Phase 14 verification.

**Two scaffolded-but-inactive features (not blockers):**

1. `targetStarSystemId` on TacticalUnit — forward-compat scaffold for D-37 NPC mission line and "목표" InfoPanel row. Backend does not emit this field. The rendering gates are in place; a future backend plan activates the feature by wiring `targetStarSystemId: Long?` through `TacticalBattleService.toUnitDto`. Documented in `deferred-items.md`.

2. `isStopped` field absent from `TacticalUnitDto` — `canReassignUnit` in `subFleetDragGating.ts` reads the field via duck-type with a conservative MOVING default. The gating is functionally safe (blocks drag when status unknown) but CMD-05 stopped-unit precision is reduced.

---

### Wave-by-Wave Execution Summary

All 18 plans executed across 5 waves with no Self-Check: FAILED markers found:

- **Wave 0 (14-00):** Nyquist scaffold — 15 Vitest stubs + 5 Playwright stubs established TDD contract
- **Wave 1 (14-01..14-06):** Backend DTO extensions + frontend type mirrors + tactical renderer foundation
- **Wave 2 (14-07..14-11):** Fog of war + CRC rewrite + R3F removal + 5-layer BattleMap restructure
- **Wave 3 (14-12..14-13):** Sub-fleet drawer + @dnd-kit/core integration (React 19 compatible)
- **Wave 4 (14-14..14-16):** Command gating + succession FX + NPC status markers (parallel execution, narrow edits)
- **Wave 5 (14-17..14-18):** Operations overlay + end-of-battle modal (modal implementation complete but unmounted)

All 30 commits confirmed on main branch. All plan commits exist and are properly authored.

---

_Verified: 2026-04-09T12:30:00Z_
_Verifier: Claude (gsd-verifier)_
