---
phase: 22-upstream-bugfix-sync
plan: 22-03-EconomyService-schedule-split
milestone: v2.2
subsystem: engine.economy
tags: [upstream-port, bugfix, npc-economy, event-schedule, api-contract, tdd]
requirements: [US-03]
upstream_commit: a7a19cc3cd5b3fa5a7c8720484d289fc55845adc
wave: 2
dependency_graph:
  requires:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt (legacy stubs at preUpdateMonthly/postUpdateMonthly/processIncomeEvent/processSemiAnnualEvent)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt (Phase 4 partial, no salary outlay)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/event/EventAction.kt (EventActionContext.params: Map<String, Any>)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt (calls preUpdateMonthly + postUpdateMonthly each month)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt (calls gin7EconomyService.processMonthly each month)
    - backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt (parses ["ProcessIncome", "<resource>"] → params["resource"])
  provides:
    - EconomyService.processIncomeEvent(world, resource: String) — canonical 2-arg signature
    - EconomyService.processSemiAnnualEvent(world, resource: String) — canonical 2-arg signature
    - EconomyService.processWarIncomeEvent(world) — public test entry point
    - @Deprecated 1-arg overloads bridging EventServiceTest + legacy callers
    - EconomyService.preUpdateMonthly explicit no-op KDoc (legacy parity guard)
    - EconomyService.postUpdateMonthly explicit no-op KDoc (legacy parity guard)
    - ProcessIncomeAction reads context.params["resource"], defaults "gold", Error on invalid
    - ProcessSemiAnnualAction same per-resource pass-through + Error guard
    - EconomyServiceScheduleTest: 16 tests locking the API contract
  affects:
    - Future Phase 4 Gin7EconomyService salary/upkeep wire-up — must respect per-resource schedule
    - Any imported OpenSamguk-style scenario JSON with ["ProcessIncome", "gold"] events
tech_stack:
  added: []
  patterns:
    - Java reflection-based test for new method signatures (file compiles against pre/post API)
    - Mockito.verify() invoked through reflection on the EconomyService mock
    - @Deprecated 1-arg bridge with ReplaceWith hint for migration
    - require()-based resource literal validation at the EconomyService boundary
    - Upstream-verbatim KDoc citing legacy PHP source files + drain-bug history
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/EconomyServiceScheduleTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/economy/ProcessIncomeAction.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/economy/ProcessSemiAnnualAction.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/EventServiceTest.kt
decisions:
  - Resource literals "gold"/"rice" preserved on the wire matching upstream + LOGH's existing ScenarioService legacy event JSON parser. Internal mapping to faction.funds/faction.supplies happens inside the service. Documented in KDoc + test header.
  - 1-arg processIncomeEvent / processSemiAnnualEvent overloads retained as @Deprecated bridges defaulting to "gold" — preserves EventServiceTest + any LOGH-internal call sites without forcing a sweeping migration.
  - Pipeline target = legacy EconomyService stubs (NOT Gin7EconomyService) because the upstream a7a19cc3 fix is shaped around the legacy event-driven flow. Gin7EconomyService.processMonthly handles tax revenue + planet growth (no salary outlay yet) and the upstream port has no Gin7 equivalent to mirror.
  - Test uses Java reflection for the new 2-arg signature so the file compiles against both the pre-port (1-arg) and post-port (2-arg) APIs — RED state shows NoSuchMethodException, GREEN shows behavior assertions. Pattern reused from FactionAIBillFormulaTest's reflection-based private-method anchor (22-01).
  - 24-tick drain test deferred (per port_context guidance): LOGH doesn't exhibit the upstream drain bug today because no salary outlay code runs per tick. Instead, the 16 test cases lock the API contract on the action layer + service stub boundary so any future Phase 4 implementation MUST respect the legacy-correct per-resource schedule from day one.
  - processWarIncomeEvent added as a public no-op stub matching upstream's test entry point — LOGH has no war-income calculation today so the body is a debug log; the API surface satisfies the upstream contract test.
metrics:
  duration_sec: 839
  duration_human: ~14m
  tasks: 4
  files_created: 1
  files_modified: 4
  tests_added: 16
  tests_pass: 64
  completed: 2026-04-10T06:36:08Z
commits:
  - bbbecd9c: test(22-03) RED — 16-test EconomyServiceScheduleTest, 14 fail on pre-port API
  - 5c2e1c5b: fix(22-03) GREEN — port per-resource event schedule + KDoc + Action pass-through
---

# Phase 22 Plan 03: EconomyService per-resource event schedule split — Summary

Ported the upstream commit `a7a19cc3` `fix: NPC 국가/장수 금 증발 버그 수정` event-schedule contract into LOGH's `EconomyService`, splitting `processIncomeEvent` and `processSemiAnnualEvent` to take a `resource: String` parameter, wiring `ProcessIncomeAction`/`ProcessSemiAnnualAction` to read `context.params["resource"]`, adding `processWarIncomeEvent` test entry point, and replacing the TODO comments on `preUpdateMonthly`/`postUpdateMonthly` with explicit no-op KDoc citing the legacy PHP source + the upstream 12x drain bug it prevents. Closes US-03.

## Pipeline Investigation (Task 1)

LOGH does **not** exhibit the upstream 12x drain bug today, but the API contract still had to be ported so the future Phase 4 wire-up cannot reintroduce it. The investigation traced every relevant call path:

| Call site | Today's behavior | Source |
|---|---|---|
| `InMemoryTurnProcessor.process` (line 68) | Calls `economyService.preUpdateMonthly(session)` every month | InMemoryTurnProcessor.kt |
| `InMemoryTurnProcessor.process` (line 117) | Calls `economyService.postUpdateMonthly(session)` every month | InMemoryTurnProcessor.kt |
| `EconomyService.preUpdateMonthly` | **No-op stub** (Phase 4 TODO before 22-03; explicit no-op KDoc after) | EconomyService.kt:91 → :103 |
| `EconomyService.postUpdateMonthly` | **No-op stub** (Phase 4 TODO before 22-03; explicit no-op KDoc after) | EconomyService.kt:101 → :125 |
| `TickEngine.runMonthlyPipeline` (line 185) | Calls `gin7EconomyService.processMonthly(world)` | TickEngine.kt |
| `Gin7EconomyService.processMonthly` | Handles **tax revenue + approval adjustment + planet growth** only on tax months 1/4/7/10 — **no salary outlay** | Gin7EconomyService.kt |
| `EconomyPreUpdateStep` (TurnStep, order 300) | `shouldSkip = true` — never executes | EconomyPreUpdateStep.kt |
| `EconomyPostUpdateStep` (TurnStep, order 1000) | Pipeline-step path, calls `postUpdateMonthly` (no-op stub) | EconomyPostUpdateStep.kt |
| `ProcessIncomeAction` (Spring `@Component`, actionType="process_income") | Wired through `EventActionRegistry`, called by `EventService.dispatchEvents` when scenario events with `process_income` fire | ProcessIncomeAction.kt |
| `ScenarioService.parseAction` (line 1149-1156) | Already populates `params["resource"] = "gold"\|"rice"` from legacy `["ProcessIncome", "<res>"]` form | ScenarioService.kt |
| **Scenario JSON** (`backend/shared/src/main/resources/data/scenarios/scenario_logh_*.json`) | **Zero references** to `ProcessIncome` / `ProcessSemiAnnual` events today | grep across 10 scenario files |

**Conclusion:** LOGH is in classification **(C)** from the plan: legacy `EconomyService` is fully stubbed; `Gin7EconomyService` handles only tax+growth without salary outlay; the action layer + scenario parser are wired but no production scenario JSON references the events yet. The drain bug therefore cannot manifest today — but the upstream contract MUST be ported so when Phase 4 implements the salary outlay (either inside `Gin7EconomyService.processMonthly` or by routing through `EconomyService.processIncomeEvent`), the per-resource event-driven schedule is structurally enforced.

**Patch target:** Legacy `EconomyService` (the stubs). Rationale: upstream's fix is shaped around the legacy event-driven flow, and `ScenarioService` already emits `params["resource"]` correctly for that path. Patching `Gin7EconomyService` instead would mean rewriting the action layer to bypass `EconomyService`, expanding scope. The current plan keeps the legacy seam intact so the future Phase 4 Gin7 wire-up only needs to fill in the body of `processIncomeEvent(world, resource)` and the entire chain — scenario parser → action layer → service — already enforces the legacy-correct schedule.

## What changed

### `EconomyService.kt`

**`preUpdateMonthly`** — TODO comment replaced with KDoc citing `hwe/func_gamerule.php:189` and the upstream a7a19cc3 fix. Body remains a single-line comment explicitly stating the no-op invariant. Documents that income/salary MUST flow through scheduled events only and warns Phase 4 implementers never to add income calls here.

**`postUpdateMonthly`** — Same treatment for `hwe/func_gamerule.php:260`. Documents that semi-annual decay MUST flow through `ProcessSemiAnnualAction` only and that other post-month responsibilities (city supply state, faction-rank refresh, disaster/boom, trade-rate randomization) live in their own scheduled events / pipeline steps.

**`processIncomeEvent(world, resource: String)`** — NEW canonical 2-arg signature. Validates `resource ∈ {"gold", "rice"}` via `require()`. KDoc documents the per-resource schedule (gold→funds in Jan, rice→supplies in Jul), the upstream 12x drain bug, and the Phase 4 TODO. Body is a debug log (Phase 4 will fill in the salary outlay calculation).

**`processIncomeEvent(world: SessionState)`** — `@Deprecated` 1-arg bridge that defaults to `"gold"` (matches month 1 schedule). Retained for `EventServiceTest` compatibility and any LOGH-internal call sites that predate the port. `ReplaceWith` hint guides migration.

**`processSemiAnnualEvent(world, resource: String)`** — Same per-resource split, KDoc, and `require()` guard. KDoc cites `hwe/sammo/Event/Action/ProcessSemiAnnual.php::run($resource)`.

**`processSemiAnnualEvent(world: SessionState)`** — `@Deprecated` 1-arg bridge.

**`processWarIncomeEvent(world)`** — NEW public test entry point matching upstream's a7a19cc3 API surface. No-op stub today (LOGH has no war-income calculation; Phase 4 TODO).

### `ProcessIncomeAction.kt`

Reads `context.params["resource"]`, defaults to `"gold"` when missing (matches upstream parity behavior — a bare `["ProcessIncome"]` event still wires through the January gold path). Returns `EventActionResult.Error` without invoking the service when the literal is anything other than `"gold"` or `"rice"`. KDoc cites the legacy PHP source and lists the canonical scenario event forms.

### `ProcessSemiAnnualAction.kt`

Same per-resource pass-through + Error guard. KDoc lists the canonical scenario event forms.

### `EventServiceTest.kt`

Three `verify(economyService).processIncomeEvent(world)` / `processSemiAnnualEvent(world)` calls migrated to the new 2-arg form (`verify(...).processIncomeEvent(world, "gold")`) — clears the deprecation warnings without changing behavior since the bridge defaults to `"gold"` anyway. No assertion semantics changed.

### `EconomyServiceScheduleTest.kt` (NEW, 16 tests)

| Test | Purpose |
|---|---|
| `processIncomeEvent has resource overload after 22-03 port` | Locks new 2-arg signature exists via reflection |
| `processSemiAnnualEvent has resource overload after 22-03 port` | Locks new 2-arg signature exists via reflection |
| `processWarIncomeEvent test entry point exists` | Locks new 1-arg test entry point exists |
| `processIncomeEvent with funds resource does not throw` | Sanity check stub accepts "gold" |
| `processIncomeEvent with supplies resource does not throw` | Sanity check stub accepts "rice" |
| `processIncomeEvent rejects invalid resource literal` | `IllegalArgumentException` on "platinum" |
| `processSemiAnnualEvent rejects invalid resource literal` | `IllegalArgumentException` on "fuel" |
| `ProcessIncomeAction passes resource gold to economyService` | Action-layer pass-through (gold path) |
| `ProcessIncomeAction passes resource rice to economyService` | Action-layer pass-through (rice path) |
| `ProcessIncomeAction returns Error on invalid resource` | Action-layer guard, service NOT invoked |
| `ProcessIncomeAction defaults to gold when resource missing` | Empty params → "gold" default (upstream parity) |
| `ProcessSemiAnnualAction passes resource gold to economyService` | Symmetric Action-layer pass-through |
| `ProcessSemiAnnualAction passes resource rice to economyService` | Symmetric Action-layer pass-through |
| `ProcessSemiAnnualAction returns Error on invalid resource` | Symmetric Action-layer guard |
| `preUpdateMonthly does not invoke per-resource income processing` | No-op invariant — would crash on unstubbed mocks if income code ran |
| `postUpdateMonthly does not invoke per-resource semi-annual processing` | Symmetric no-op invariant |

All 16 PASS at GREEN (commit `5c2e1c5b`).

## TDD cycle

**RED (commit `bbbecd9c`):** Created `EconomyServiceScheduleTest.kt` with 16 tests using Java reflection for the new 2-arg signature so the file compiles against both pre-port and post-port APIs. 14 of 16 fail with the right reasons:
- 8x `NoSuchMethodException` (the `processIncomeEvent(SessionState, String)` overload doesn't exist yet)
- 4x `AssertionFailedError` (the existence-check `assertNotNull` calls fail; the action-layer Error-result tests fail because the actions don't validate `resource` yet)

The 2 passing tests are the no-op invariants on `preUpdateMonthly`/`postUpdateMonthly`, which already hold under LOGH's pre-port stub status.

**GREEN (commit `5c2e1c5b`):** Added 2-arg overloads to `EconomyService` with `require()`-based resource validation, retained 1-arg `@Deprecated` bridges defaulting to `"gold"`, added `processWarIncomeEvent` no-op stub, replaced TODO comments on `preUpdateMonthly`/`postUpdateMonthly` with explicit no-op KDoc, updated both action classes to read `params["resource"]` with default + Error guard, migrated 3 `EventServiceTest` verify calls to the new signature. All 16 tests pass; existing `EventServiceTest` (21 tests) + 22-01 + 22-02 tests still pass.

## Verification

| Suite | Tests | Failures | Provenance |
|---|---|---|---|
| `EconomyServiceScheduleTest` | 16 | 0 | NEW (22-03) |
| `FactionAIBillFormulaTest` | 6 | 0 | 22-01 (regression) |
| `OfficerAIDonateGateTest` | 4 | 0 | 22-02 (regression) |
| `EventServiceTest` | 21 | 0 | existing (regression — 3 verifies migrated) |
| `TacticalBattleDtoExtensionTest` | 5 | 0 | Phase 14 (regression) |
| `BattleSummaryEndpointTest` | 3 | 0 | Phase 14 (regression) |
| `SensorRangeComputationTest` | 5 | 0 | Phase 14 (regression) |
| `OperationBroadcastTest` | 4 | 0 | Phase 14 (regression) |
| **Total** | **64** | **0** | |

All 64 tests across 8 suites pass. Two `BUILD SUCCESSFUL` runs confirmed:
- `:game-app:test --tests EconomyServiceScheduleTest --tests FactionAIBillFormulaTest --tests OfficerAIDonateGateTest --tests EventServiceTest` — 47/47 pass
- `:game-app:test --tests TacticalBattleDtoExtensionTest --tests BattleSummaryEndpointTest --tests SensorRangeComputationTest --tests OperationBroadcastTest` — 17/17 pass
- Combined run captured all 64 in a single `BUILD SUCCESSFUL` for definitive metrics.

## Domain mapping applied

| Upstream (com.opensam) | LOGH (com.openlogh) | Notes |
|---|---|---|
| `Nation` | `Faction` | preserved via existing entity rename |
| `nation.gold` | `faction.funds` | wire literal stays "gold" for legacy event JSON compat |
| `nation.rice` | `faction.supplies` | wire literal stays "rice" |
| `nation.bill` | `faction.taxRate` | not touched in 22-03 (22-01 owns FactionAI) |
| `WorldState` | `SessionState` | LOGH-native |
| `processIncome` (private) | `processIncomeEvent` (public) | LOGH renamed during initial port; 22-03 keeps the public surface |
| `processSemiAnnual` (private) | `processSemiAnnualEvent` (public) | same |
| `processWarIncomeEvent` | `processWarIncomeEvent` | NEW, matches upstream verbatim |

The literal `"gold"` / `"rice"` is intentionally preserved at the wire layer to keep imported legacy event JSON working without translation. Internal mapping to `faction.funds` / `faction.supplies` happens inside the service body (Phase 4 implementation responsibility).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Backward compat] Retained 1-arg overloads as @Deprecated bridges**
- **Found during:** Task 3 (GREEN)
- **Issue:** Plan implied a hard signature change to 2-arg; this would have broken 3 `EventServiceTest` verify calls and any LOGH-internal call sites that predate the port.
- **Fix:** Added `@Deprecated` 1-arg bridges defaulting to `"gold"` (the month 1 schedule). Migrated the 3 EventServiceTest verifies to the new 2-arg form to clear warnings. Production action layer calls go through the canonical 2-arg signature.
- **Files modified:** EconomyService.kt, EventServiceTest.kt
- **Commit:** 5c2e1c5b

**2. [Rule 1 — Test-shape choice] Reflection-based test calls instead of direct calls**
- **Found during:** Task 2 (RED) — first compile attempt failed because direct 2-arg calls didn't compile against the pre-port API.
- **Issue:** TDD RED requires the test file to compile so the commit isn't broken. Direct calls to `processIncomeEvent(world, "gold")` produce a compile error in RED because the 2-arg overload doesn't exist yet.
- **Fix:** Wrapped all 2-arg calls in `Method.invoke()` reflection helpers. The file compiles against both pre/post-port APIs; RED fails at runtime with `NoSuchMethodException`, GREEN succeeds. Pattern mirrors `FactionAIBillFormulaTest`'s reflection-based private-method anchor (22-01) and `OfficerAIDonateGateTest`'s `invokeDoDonate` pattern (22-02).
- **Files modified:** EconomyServiceScheduleTest.kt (rewritten before RED commit)
- **Commit:** bbbecd9c

### Authentication gates

None.

## Quantitative impact

LOGH does not exhibit the upstream 12x drain bug today (no salary outlay code runs per tick), so there is no measurable "before/after" runtime improvement. The impact of 22-03 is **structural**: it makes the bug structurally impossible for the future Phase 4 implementation to reintroduce.

| Invariant | Pre-22-03 | Post-22-03 |
|---|---|---|
| `EconomyService.processIncomeEvent` signature | 1-arg, ignores resource | 2-arg canonical + 1-arg @Deprecated bridge |
| `processIncomeEvent` resource validation | None (any literal accepted) | `require()` enforces "gold" or "rice" |
| `ProcessIncomeAction` resource pass-through | Drops `params["resource"]` on the floor | Reads, defaults "gold", validates, passes through |
| `ProcessIncomeAction` invalid resource handling | Calls service unconditionally | Returns Error without invoking service |
| `EconomyService.preUpdateMonthly` documentation | TODO comment | Explicit no-op KDoc citing legacy + upstream a7a19cc3 |
| `EconomyService.postUpdateMonthly` documentation | TODO comment | Explicit no-op KDoc citing legacy + upstream a7a19cc3 |
| `processWarIncomeEvent` test entry point | Missing | Public stub (matches upstream API surface) |
| Test coverage on per-resource contract | 0 | 16 tests (RED→GREEN locked) |

## Next phase readiness

- **Phase 22 fully complete.** All three plans (22-01 FactionAI bill formula, 22-02 OfficerAI doDonate gate, 22-03 EconomyService schedule split) have landed cleanly. US-01, US-02, US-03 closed.
- **Phase 4 (Gin7EconomyService) handoff.** When Phase 4 implements salary outlay, the body of `processIncomeEvent(world, resource)` is the only insertion point. The action layer + scenario parser already enforce the legacy-correct per-resource schedule (`["ProcessIncome", "gold"]` in month 1, `["ProcessIncome", "rice"]` in month 7). Phase 4 must NOT call the legacy stubs from `Gin7EconomyService.processMonthly` directly — that would re-couple the buggy "every-month outlay" path. Documented in the new KDoc on the legacy stubs.
- **Deferred items.** None added to `deferred-items.md`. Pre-existing 205 legacy 삼국지 test failures remain out of scope per Phase 22 boundary.
- **24-tick drain integration test.** Deferred to Phase 4 once `Gin7EconomyService` gains salary outlay. The test scaffold (`EconomyServiceScheduleTest.newServiceWithMocks()` helper + `createWorld(month = N)` helper) is already in place and Phase 4 can extend it with a `processFactionEconomy(world, ticks=24)` integration test that asserts NPC funds drain < 10%.

## Self-Check: PASSED

- FOUND: backend/game-app/src/test/kotlin/com/openlogh/engine/EconomyServiceScheduleTest.kt
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt (modified — 2-arg overloads + KDoc + processWarIncomeEvent)
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/economy/ProcessIncomeAction.kt (modified — params["resource"] pass-through)
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/economy/ProcessSemiAnnualAction.kt (modified — params["resource"] pass-through)
- FOUND: backend/game-app/src/test/kotlin/com/openlogh/engine/EventServiceTest.kt (modified — 3 verifies migrated)
- FOUND: bbbecd9c (RED commit, git log)
- FOUND: 5c2e1c5b (GREEN commit, git log)
- 64/64 tests passing across EconomyServiceScheduleTest + 22-01/22-02 regression + EventServiceTest + 4 Phase 14 tactical suites
- No regression in any tactical or economy suite
- Known Stubs: `processIncomeEvent(world, resource)`, `processSemiAnnualEvent(world, resource)`, and `processWarIncomeEvent(world)` are all log-only stubs by design — Phase 4 will fill them in. Documented in KDoc; tests lock the API contract.

---
*Phase: 22-upstream-bugfix-sync*
*Plan: 22-03 (EconomyService per-resource event schedule split)*
*Completed: 2026-04-10T06:36:08Z*
