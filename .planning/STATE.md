---
gsd_state_version: 1.0
milestone: v2.1
milestone_name: 전술전 지휘체계 + AI
status: executing
stopped_at: Completed 14-10-PLAN.md (parallel Wave 3)
last_updated: "2026-04-09T11:27:27.633Z"
last_activity: 2026-04-09 -- Plan 14-10 completed in parallel Wave 3 (BattleMap layer restructure)
progress:
  total_phases: 22
  completed_phases: 17
  total_plans: 115
  completed_plans: 98
  percent: 86
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-07)

**Core value:** gin7 조직 시뮬레이션 — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행
**Current focus:** Phase 13 — ai

## Current Position

Phase: 14 (frontend-integration) — EXECUTING (Wave 3 parallel)
Plan: 14-10 complete (BattleMap 5-layer restructure + tacticalStore hierarchy reducer + commandChain.ts SoT) · 8 plans still incomplete
Status: Wave 3 in progress; 14-10 landed multi-CRC pipeline + fog/succession bookkeeping slots — 14-12/14-13/14-14 unblocked
Last activity: 2026-04-09 -- Plan 14-10 completed in parallel Wave 3 (BattleMap layer restructure)

Progress: [█████████░] 86%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: none yet
- Trend: -

*Updated after each plan completion*
| Phase 08 P01 | 9m | 2 tasks | 4 files |
| Phase 08 P02 | 18min | 2 tasks | 43 files |
| Phase 08 P03 | 7min | 2 tasks | 6 files |
| Phase 09 P01 | 7min | 2 tasks | 7 files |
| Phase 09 P02 | 8min | 2 tasks | 5 files |
| Phase 09 P03 | 6min | 2 tasks | 4 files |
| Phase 09 P04 | 6min | 2 tasks | 5 files |
| Phase 10 P01 | 2min | 2 tasks | 0 files |
| Phase 10 P02 | 2min | 2 tasks | 0 files |
| Phase 10 P03 | 2min | 2 tasks | 3 files |
| Phase 10 P04 | 3min | 2 tasks | 4 files |
| Phase 10 P05 | 6min | 2 tasks | 6 files |
| Phase 10 P06 | 5min | 2 tasks | 4 files |
| Phase 10 P07 | 5min | 1 tasks | 3 files |
| Phase 11 P01 | 5min | 2 tasks | 6 files |
| Phase 11 P02 | 7min | 1 tasks | 3 files |
| Phase 11 P03 | 10min | 2 tasks | 3 files |
| Phase 12 P01 | 8min | 2 tasks | 7 files |
| Phase 12 P02 | 8min | 2 tasks | 8 files |
| Phase 12 P03 | 13min | 2 tasks | 7 files |
| Phase 12 P04 | 10min | 3 tasks | 12 files |
| Phase 13-ai P01 | 6min | 2 tasks | 8 files |
| Phase 13-ai P02 | 9min | 2 tasks | 2 files |
| Phase 14-frontend-integration P05 | 8min | 2 tasks | 21 files |
| Phase 14 P07 | 12min | 1 tasks | 2 files |
| Phase 14 P04 | 13min | 1 tasks | 5 files |
| Phase 14 P01 | 120 | 2 tasks | 6 files |
| Phase 14 P02 | 25 min | 1 tasks | 3 files |
| Phase 14-frontend-integration P06 | 6min | 1 tasks | 4 files |
| Phase 14 P08 | 6min | 2 tasks | 9 files |
| Phase 14 P03 | 40min | 1 tasks | 4 files |
| Phase 14-frontend-integration P09 | 5min | 1 tasks | 4 files |
| Phase 14 P10 | 15min | 2 tasks | 5 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [v2.0-Phase 03]: BattleWebSocketController uses officerId in payload (not OfficerPrincipal)
- [v2.0-Phase 03]: isFlagship cleared on destruction before replacement promotion
- [v2.0-Phase 05]: UtilityScorer is pure object (no Spring DI)
- [v2.0-Phase 06]: Tactical icon rule: isFlagship -> triangle, all others -> square
- [v2.1-Roadmap]: Dual engine (TacticalBattleEngine + TacticalCombatEngine) must be resolved in Phase 8 before any hierarchy code
- [v2.1-Roadmap]: Command buffer pattern (ConcurrentLinkedQueue) replaces direct WebSocket-to-state mutation
- [v2.1-Roadmap]: TacticalAI must be pure function operating only on TacticalBattleState — no DB access
- [Phase 08]: TacticalCommand sealed class with 7 subtypes for exhaustive when() matching in command buffer drain
- [Phase 08]: CommandRange object reused from model/ package for TacticalUnit field unification
- [Phase 08]: Count-down cooldown pattern: stanceChangeTicksRemaining replaces ticksSinceStanceChange
- [Phase 08]: Two CommandHierarchy fields (attackerHierarchy/defenderHierarchy) per battle state for dual-side support
- [Phase 08]: buildCommandHierarchyStatic companion method pattern for test isolation without Spring context
- [Phase 08]: Officer.officerLevel used as rank proxy for succession queue ordering
- [Phase 09]: CommandPriority uses Comparable natural ordering with reversed officerId for seniority tiebreak
- [Phase 09]: CommandHierarchyService is pure object (no Spring DI) following UtilityScorer pattern
- [Phase 09]: CRC formula: maxRange=50+cmd*3, expansionRate=0.5+cmd/100 for tunable CRC behavior
- [Phase 09]: Self-commands always bypass CRC; HP<30% triggers AI retreat at 80% speed; 120-tick stuck limit triggers move-toward-commander
- [Phase 09]: Administrative commands (AssignSubFleet, ReassignUnit) bypass CRC -- organizational, not tactical
- [Phase 09]: TriggerJamming uses early-return bypass in applyCommand before unit lookup since jammer is enemy officer
- [Phase 09]: Jamming tick processing at step 5.7 (after destruction) so source-gone check sees current tick deaths
- [Phase 10]: All 10-01 artifacts (EnergyAllocation, Formation, BattlePhase, TacticalUnitState, TacticalBattle, V37 migration) pre-existed from v2.0 -- no code changes needed
- [Phase 10]: All 10-02 artifacts pre-existed from v2.0 -- TacticalBattleEngine, BattleTriggerService, FortressGunSystem fully implemented
- [Phase 10]: Most 10-03 artifacts pre-existed from v2.0 -- only REST controller and history query were missing
- [Phase 10]: Viewport 1000x600 matches GAME_W/GAME_H exactly -- no scaling needed
- [Phase 10]: SuccessionService follows pure object pattern (no Spring DI) consistent with CommandHierarchyService
- [Phase 10]: processSuccession at step 5.3 (after destruction, before ground battle) ensures vacancy state is current
- [Phase 10]: findNextSuccessor checks designatedSuccessor first, then successionQueue in rank order
- [Phase 10]: Command breakdown passes null commanderUnit to OutOfCrcBehavior: HP<30% retreat, healthy maintain velocity
- [Phase 11]: ThreatAssessor scoring formula: HP*40 + ships*20 + proximity*25 + attack*15 (0-100 scale)
- [Phase 11]: Pure object pattern for all tactical AI classes (no Spring DI)
- [Phase 11]: TacticalAI uses 4 energy presets (AGGRESSIVE/DEFENSIVE/BALANCED/EVASIVE) not continuous slider
- [Phase 11]: TacticalAIRunner follows pure object pattern (no Spring DI) consistent with all tactical AI classes
- [Phase 11]: Command breakdown uses TacticalAIRunner.triggerImmediateReeval instead of OutOfCrcBehavior for personality-based retreat
- [Phase 12]: Plan 12-01: V47 migration (not V45 — CONTEXT.md drift corrected by planner); V46__add_command_proposal.sql is current tip
- [Phase 12]: Plan 12-01: JSONB List<Long> participantFleetIds via @JdbcTypeCode(SqlTypes.JSON); round-trip test proves 10_000_000_000L preserves Long type through Jackson
- [Phase 12]: Plan 12-01: SpringBootTest repository tests MUST use classes = [OpenloghApplication::class] to avoid duplicate @SpringBootConfiguration with OpenloghApplicationTests$TestConfig
- [Phase 12]: Plan 12-01: Native JSONB @> query exists in OperationPlanRepository (PostgreSQL only); H2 tests MUST use findBySessionIdAndStatus + Kotlin-side filtering
- [Phase 12]: Plan 12-02: operationPlanService wired via optional nullable field on CommandServices + CommandExecutor to avoid breaking 8+ pre-existing CommandServices test construction sites (mirrors fleetRepository pattern)
- [Phase 12]: Plan 12-03: Step 0.6 mission objective read-through runs between Step 0.5 (processOutOfCrcUnits) and Step 0.7 (TacticalAIRunner.processAITick) — verified via both awk line check and unit test
- [Phase 12]: Plan 12-03: missionObjectiveByFleetId contains ALL fleets (participants + personality-defaulted); separate operationParticipantFleetIds set flags real OperationPlan membership for merit bonus filtering
- [Phase 12]: Plan 12-03: mockito-kotlin is NOT on :game-app classpath (build.gradle.kts:85) — all new Kotlin unit tests use plain org.mockito.Mockito.mock(Class::class.java) + when(...).thenReturn(...)
- [Phase 12]: Plan 12-03: BattleTriggerService loads BOTH ACTIVE and PENDING operations at init to eliminate the activation-vs-battle-trigger tick race (PENDING→ACTIVE can happen on same tick as buildInitialState)
- [Phase 12]: Plan 12-04: OperationLifecycleService at TickEngine Step 5.5 wraps activatePending + evaluateCompletion in @Transactional; runs BEFORE tacticalBattleService.processSessionBattles to ensure same-tick activation visibility (Mockito InOrder verifies)
- [Phase 12]: Plan 12-04: DEFENSE_STABILITY_TICKS=60; counter mutation MUST be persisted on every tick (else-branch save) so it crosses tick boundaries; otherwise the counter resets every tick and DEFENSE never completes
- [Phase 12]: Plan 12-04: TacticalBattleService.endBattle inline merit bonus uses participantSnapshot=state.operationParticipantFleetIds.toSet() captured BEFORE the unit loop (Blocker 4 race guard); first officer.meritPoints += accumulation path in codebase
- [Phase 12]: Plan 12-04: computeBaseMerit heuristic=(100*ships/maxShips).coerceAtLeast(10) on winning side, 0 otherwise; full survival on winning side = 100 exactly (test fixtures depend on this anchor)
- [Phase 12]: Plan 12-04: OperationMeritBonusTest must AopTestUtils.getTargetObject() the TacticalBattleService CGLIB proxy before reflecting on private activeBattles; Spring proxy subclasses inherit but hold null for instance fields
- [Phase 13-ai]: Plan 13-01: Strategic AI scorers (StrategicPowerScorer/FogOfWarEstimator/OperationTargetSelector/FleetAllocator) implemented as pure Kotlin objects under engine.ai.strategic, mirroring UtilityScorer pattern
- [Phase 13-ai]: Plan 13-01: compositeScore = ships*0.5 + commander*30 + defense*20 with SHIPS_PER_UNIT=300; commander score is averaged across stationed officers (not summed)
- [Phase 13-ai]: Plan 13-01: FleetAllocator uses greedy 1.3x SUPERIORITY_MARGIN; OperationTargetSelector applies AGGRESSIVE/DEFENSIVE *1.5 and CAUTIOUS *0.8 personality biases; DEFENSE_THREAT_RATIO=0.7
- [Phase 13-ai]: Plan 13-02: FactionAI constructor expanded with CommandExecutor + FleetRepository + OperationPlanRepository; atWar branch delegates to executeStrategicOperations()
- [Phase 13-ai]: Plan 13-02: Synthetic enemy Fleet(id=-officerId, currentUnits=officer.ships/300) constructed from enemy Officer.ships when enemy FleetRepository not queryable without N+1
- [Phase 13-ai]: Plan 13-02: StubCommandExecutor subclass pattern (mirrors CommandProposalServiceTest SuccessCommandExecutor) used because Mockito cannot stub Kotlin suspend functions without mockito-kotlin (Phase 12 D-17)
- [Phase 13-ai]: Plan 13-02: 삼국지 legacy strings (급습/의병모집/필사즉생/strategicCmdLimit) fully removed from FactionAI and NationAITest per D-08
- [Phase 14]: Plan 14-05: Wave 0 scaffold-first TDD — 15 Vitest stubs + 5 Playwright stubs + createFixtureBattle() factory created so every Wave 2-5 `<automated>` verify command points to an existing file (Nyquist compliance)
- [Phase 14]: Plan 14-05: no-r3f-imports regression guard runs in Wave 0 tolerance mode (scanner asserts Array.isArray only); plan 14-08 will flip to `expect(offenders).toHaveLength(0)` after R3F removal
- [Phase 14]: Plan 14-05: Fixture factory uses `as unknown as TacticalBattle` for hierarchy fields until plan 14-06 extends the TacticalBattle type; cast becomes redundant and removable then
- [Phase 14]: Plan 14-05: Replaced suggested `glob` npm import with Node built-in `fs.readdirSync + statSync` recursive walk — glob is not in frontend/package.json and adding it was out of scope
- [Phase 14]: Plan 14-07: @dnd-kit/core + @dnd-kit/utilities added (no @dnd-kit/sortable, no react-dnd) under React 19.2.3 — dep-add landed opportunistically via sibling commit 6315120a (14-05 Wave 0 scaffolds race); R3F removal deferred to 14-08 atomic step
- [Phase 14]: Plan 14-04: OperationEventDto.fromPlan guards null plan.id with IllegalStateException; broadcasts placed inside existing @Transactional methods (SimpMessagingTemplate flushes post-commit, matching TacticalBattleService pattern)
- [Phase 14]: Plan 14-01: CommandHierarchyDto renames engine fields (subCommanders→subFleets, commanderId→commanderOfficerId, unitIds→memberFleetIds) via fromEngine companion — stable frontend contract against engine refactors
- [Phase 14]: Plan 14-01: toUnitDto signature changed to (unit, state) — hierarchy-derived fields (subFleetCommanderId, successionState, isOnline, isNpc, missionObjective, sensorRange) computed inline per-tick, not stored on TacticalUnit
- [Phase 14]: Plan 14-01: npcOfficerIds tracked as state-level Set<Long> populated from Officer.npcState at buildInitialState — matches connectedPlayerOfficerIds pattern for O(1) NPC marker lookup (D-35)
- [Phase 14]: [Phase 14]: Plan 14-02: Reuse existing TacticalBattle.battleState JSONB column for endBattle unit snapshots — no new Flyway migration needed. Stores unitSnapshots + operationParticipantFleetIds keys; buildBattleSummary re-derives merit breakdown at read-time mirroring computeBaseMerit exactly.
- [Phase 14]: [Phase 14]: Plan 14-02: /api/v1/battle/{sessionId}/{battleId}/summary endpoint maps NoSuchElementException→404, IllegalArgumentException (session mismatch)→404 (mirrors getBattleState not-found semantics to prevent cross-session leak), IllegalStateException→409. Lightweight Mockito controller test pattern (no SpringBootTest) mirrors GeneralControllerTest.
- [Phase 14-frontend-integration]: Plan 14-06: new TacticalUnit Phase 14 fields are optional (?:) to allow phased backend rollout; string-fallback BattleTickEvent.type union preserves literal inference on 8 known values while accepting unknown codes; NOT re-exported through types/index.ts barrel (OpenSamguk legacy, no precedent); fixture shim (CommandHierarchyDto=unknown + as unknown as TacticalBattle cast) removed — 14-06 was the planned target for its removal
- [Phase 14]: Plan 14-08: Battle page (감찰부) BattleCloseView takeover removed outright — tactical rendering centralized on /tactical via BattleMap.tsx per D-26; 감찰부 stays a pure war-status admin page (D-08-01)
- [Phase 14]: Plan 14-08: no-r3f-imports test uses Node built-in fs.readdirSync walker (not globSync from glob pkg); package.json path math = join(__dirname, '..', '..', 'package.json') — 2 levels up, not 3 (D-08-02, D-08-03)
- [Phase 14]: Plan 14-08: @react-three/fiber + @react-three/drei + three + @types/three fully removed from frontend/package.json (48 transitive pkgs dropped from lockfile); regression guard flipped from Wave 0 tolerance to strict expect(offenders).toEqual([])
- [Phase 14]: Plan 14-03: TacticalUnit.sensorRange cached field recomputed per tick from SensorRangeFormula (internal object, D-19 anchors: base=150, min=30, max=500, slider=17, injury=0.7); toUnitDto reads cached field replacing 14-01 inline DetectionCapability-based formula
- [Phase 14]: Plan 14-03: Top-level computeSensorRange() wrapper exposes private SensorRangeFormula object to same-package tests without reflection; TDD RED+GREEN compressed into single commit (Wave 2 parallel-safe pattern from 14-01) to avoid wedging shared :game-app:compileTestKotlin on other executors
- [Phase 14-frontend-integration]: Plan 14-09: CommandRangeCircle rewritten as pure-props hierarchy-aware component. Exports computeRingStyle(side, state) pure helper so vitest env=node can assert all visual decisions (color, stroke, opacity, shadow, goldHint) without mounting react-konva. 19 tests run in 14ms.
- [Phase 14-frontend-integration]: Plan 14-09: D-03 regression guard uses readFileSync + regex on CommandRangeCircle.tsx source text to catch any literal mention of Konva.Animation / new Animation( / import Konva — stronger than behavioral test because it catches comment mentions and conditional-branch re-introductions. Had to rephrase my own REMOVED doc comment from 'new Konva.Animation(...)' to 'Konva imperative animation loop' after first GREEN run to avoid tripping the guard.
- [Phase 14-frontend-integration]: Plan 14-09: frontend/src/lib/tacticalColors.ts is the single source of truth for D-02 faction hex literals (empire #4466ff / alliance #ff4444 / fezzan #888888). Exports sideToDefaultColor + lightenHex(hex, lPercent) with an achromatic short-circuit so pure white/gray/black inputs stay idempotent. All future tactical components must import from this file — no inline hex literals.
- [Phase 14-frontend-integration]: Plan 14-09: BattleMap.tsx single call site migrated to new CommandRangeCircle props in the same commit (not left dangling) with a TODO(14-10) marker — this was the splice point sibling Wave 3 plan 14-10 needed to multi-render CRCs. 14-10 has since replaced the marker with the full 5-layer restructure; the API contract I established (cx/cy/currentRadius/maxRadius/side + isMine/isCommandable/isHovered/isSelected) is stable under sibling consumption.
- [Phase 14]: Plan 14-10: commandChain.ts (findVisibleCrcCommanders + findAlliesInMyChain) is the single source of truth for 'which commanders does the logged-in officer see/command' — BattleMap CRC layer + 14-11 fog-of-war + 14-13 canCommandUnit all call into it
- [Phase 14]: Plan 14-10: activeCommander (post-delegation) aliases fleetCommander in the 'am I top of chain' branch — Kircheis-after-delegation sees all sub-CRCs even though hierarchy.fleetCommander still reads 1000
- [Phase 14]: Plan 14-10: tacticalStore owns init+preservation of fog/succession bookkeeping slots (lastSeenEnemyPositions, activeSuccessionFleetIds, activeFlagshipDestroyedFleetIds); 14-11/14-14 own update logic — single slice avoids parallel-wave write races
- [Phase 14]: Plan 14-10: activeFlagshipDestroyedFleetIds pruned by wall-clock Date.now() expiresAt, not tick count — FX animations run at 60fps framerate, not tick rate, so 500ms easing outlives a tick boundary
- [Phase 14]: Plan 14-10: Task 1 + Task 2 source files landed under sibling commits b5c87d84 (14-09) + 03e8ef2d (14-09 docs) due to parallel Wave 3 git add race; 14-10-SUMMARY.md is the canonical attribution anchor. 03e8ef2d commit body explicitly acknowledges 14-10 ownership

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 8]: Exact field differences between TacticalCombatEngine.TacticalUnit and TacticalBattleEngine.TacticalUnit need a diff at kickoff
- [Phase 8]: OperationPlan entity design decision needed: new table vs enriched JSONB
- [Phase 14]: CRC rendering layer (Konva vs R3F) must be confirmed before UI work — resolved by D-25 (R3F removed); blocked pending plan 14-08 execution
- [Phase 14]: Pre-existing test failures logged to `.planning/phases/14-frontend-integration/deferred-items.md` — 7 failures in 3 files (command-select-form, game-dashboard, record-zone) unrelated to Wave 0 scope

## Session Continuity

Last session: 2026-04-09T11:27:27.625Z
Stopped at: Completed 14-10-PLAN.md (parallel Wave 3)
Resume file: None
