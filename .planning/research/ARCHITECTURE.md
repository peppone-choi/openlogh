# Architecture Patterns: Parity Verification in Multi-Module Spring Boot Port

**Domain:** Legacy PHP game engine parity verification
**Researched:** 2026-03-31
**Confidence:** HIGH (based on direct codebase analysis of existing patterns)

## Recommended Architecture

Parity verification in OpenSamguk is already partially implemented across three tiers. The architecture should formalize and complete this three-tier approach, organizing tests by verification scope (formula, behavior, integration) rather than by module location.

### Three-Tier Parity Verification Model

```
Tier 1: Formula Parity (Unit)
  Same input -> same numeric output
  No DB, no Spring context, pure functions
  Location: backend/game-app/src/test/kotlin/com/opensam/qa/parity/

Tier 2: Behavioral Parity (Integration)
  Same game state + command -> same state mutations
  In-memory harness (InMemoryTurnHarness), mocked repositories
  Location: backend/game-app/src/test/kotlin/com/opensam/engine/

Tier 3: System Parity (E2E)
  Same user action -> same visible outcome (legacy vs new)
  Playwright against running legacy PHP + new system
  Location: frontend/e2e/parity/
```

### Component Boundaries

| Component | Responsibility | Communicates With | Parity Scope |
|-----------|---------------|-------------------|--------------|
| `qa/parity/*ParityTest` | Formula-level verification (math matches PHP) | Direct class instantiation, LiteHashDRBG | Individual formulas, coefficients, thresholds |
| `qa/GoldenValueTest` | Regression lock on known-good outputs | Direct command execution, LiteHashDRBG | Locked numeric outputs that must never drift |
| `engine/GoldenSnapshotTest` | Multi-entity turn snapshot determinism | InMemoryTurnHarness, TurnService | Full turn cycle state after processing |
| `engine/DeterministicReplayParityTest` | CQRS turn replay produces canonical output | InMemoryTurnProcessor, InMemoryWorldState | Turn queue consumption, state transitions |
| `engine/FormulaParityTest` | Cross-domain formula verification | EconomyService (reflected private methods), BattleEngine | Economy income, battle determinism |
| `test/InMemoryTurnHarness` | Test infrastructure: in-memory world simulation | All repositories (mocked), CommandExecutor, TurnService | Not a test itself; enables Tier 2 tests |
| `e2e/parity/*` | Visual + API parity against running systems | Playwright, HTTP APIs (legacy + new) | Page content, command execution effects |
| `e2e/parity/parity-helpers.ts` | E2E test infrastructure: auth, snapshots, delta checking | REST APIs, Playwright Page | Not a test itself; enables Tier 3 tests |
| `e2e/parity/parity-config.ts` | Command catalog with safety flags + page routes | Static config consumed by E2E tests | Defines WHAT to test (55 general + 38 nation commands, 36 pages) |

### Data Flow for Comparison Testing

**Tier 1 (Formula Parity) -- No external dependencies:**
```
PHP source comment (reference)
      |
      v
Hardcoded expected values in test
      |
      v
Kotlin function under test
      |
      v
assertEquals(expected, actual)
```

Pattern: Each test class documents the legacy PHP file and line number in comments (e.g., `@DisplayName("che_훈련 - legacy che_훈련.php:89")`). The test encodes the expected formula behavior as hardcoded assertions. The PHP source is NOT imported at runtime -- `legacy-core/` is gitignored and serves only as a human-readable reference.

**Tier 2 (Behavioral Parity) -- InMemoryTurnHarness:**
```
Setup: create entities (General, City, Nation, WorldState)
      |
      v
InMemoryTurnHarness wires mocked repositories
      |
      v
Queue commands (queueGeneralTurn / queueNationTurn)
      |
      v
TurnService.processWorld(world) or InMemoryTurnProcessor.process()
      |
      v
Read back entity state from in-memory stores
      |
      v
Assert against golden snapshot (expected state data class)
```

Pattern: `InMemoryTurnHarness` provides a fully wired `TurnService` and `CommandExecutor` with in-memory repository implementations (using Mockito `when().thenAnswer()`). Tests create a world scenario, queue commands, process a turn, and compare the resulting state against a hardcoded `Snapshot` data class. Determinism is guaranteed by `LiteHashDRBG` (seeded DRBG replacing `java.util.Random`).

**Tier 3 (System Parity) -- Playwright E2E:**
```
Login to legacy PHP system (cookie auth)
      |                    |
      v                    v
Login to new system (JWT auth)
      |                    |
      v                    v
Navigate to page       Navigate to matching page
      |                    |
      v                    v
Extract text/structure   Extract text/structure
      |                    |
      v                    v
compareStructure(legacy, new) -> ParityCheckResult[]
      |
      v
assertParityMatches(section, results)
      |
      v
writeParityReport() -> JSON report + screenshots
```

Pattern: `parity-helpers.ts` provides authenticated sessions to both systems. `parity-config.ts` defines 36 page routes (legacy path <-> new route) with expected markers, plus 55 general and 38 nation commands with `safeToExecute` flags and `effectChecks` arrays. Tests snapshot general/city/nation state before and after command execution and use `hasGeneralEffectDelta()`, `hasCityEffectDelta()`, `hasNationEffectDelta()` to verify changes occurred.

## Patterns to Follow

### Pattern 1: Legacy Reference as Comment, Not Import

**What:** Document the exact PHP file, line number, and formula in test comments. Never import or parse PHP at runtime.

**When:** Every parity test.

**Why:** The `legacy-core/` directory is gitignored. Tests must be self-contained. The comment serves as an audit trail for human reviewers who want to verify the expected value was derived correctly.

**Example:**
```kotlin
@Nested
@DisplayName("che_훈련 - legacy che_훈련.php:89")
inner class TrainingParity {
    @Test
    fun `high leadership low crew gives high training score`() {
        // Legacy: score = clamp(round(leadership * 100 / crew * trainDelta), 0, maxTrain - train)
        val gen = createGeneral(leadership = 100, crew = 100, train = 0)
        // ...
        assertEquals(100, json["statChanges"]["train"].asInt())
    }
}
```

### Pattern 2: Deterministic RNG via LiteHashDRBG

**What:** Use `LiteHashDRBG.build(seed)` instead of `Random(seed)` for all game logic that needs randomness.

**When:** Any command, battle, or turn processing that involves probability.

**Why:** PHP's `mt_rand()` and Kotlin's `Random` produce different sequences for the same seed. `LiteHashDRBG` is a custom deterministic PRNG that ensures identical sequences across test runs, enabling golden value regression testing. Battle tests and command tests both rely on this for reproducibility.

**Example:**
```kotlin
val result = runBlocking { cmd.run(LiteHashDRBG.build("train_1")) }
```

### Pattern 3: Golden Snapshot Data Classes

**What:** Define immutable data classes that capture the minimal state needed to verify a scenario's outcome. Compare entire snapshots with `assertEquals()`.

**When:** Multi-entity scenarios (turn processing, battle resolution, economy cycles).

**Why:** Catches unintended side effects. If a formula change causes a downstream entity to drift, the snapshot comparison catches it even though the individual formula test might pass.

**Example:**
```kotlin
private data class Snapshot(
    val year: Int, val month: Int,
    val generals: List<GeneralState>,
    val nations: List<NationState>,
    val cities: List<CityState>,
)

assertEquals(expectedSnapshot(), runScenario())
```

### Pattern 4: Command Effect Categorization

**What:** Tag each command with which entity types it affects (`general`, `city`, `nation`) and whether it's safe to execute in automated testing.

**When:** Systematic command coverage.

**Why:** The `parity-config.ts` already does this for all 93 commands. This drives test generation -- if `effectChecks: ['city']`, the test must snapshot city state before/after.

**Example:**
```typescript
{
    actionCode: '농지개간',
    category: 'civil',
    safeToExecute: true,
    effectChecks: ['city'],  // Must check city state delta
}
```

### Pattern 5: Constraint System Parity as Unit Tests

**What:** Test each constraint (`NotBeNeutral`, `ReqGeneralGold`, `OccupiedCity`, etc.) in isolation with `Pass`/`Fail` boundary values.

**When:** Before testing commands that use those constraints.

**Why:** Commands compose constraints via `ConstraintChain`. If a constraint is wrong, every command using it is wrong. Testing constraints first isolates the root cause.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Running PHP Interpreter in Tests

**What:** Spawning a PHP process to execute legacy code and comparing output directly.

**Why bad:** Adds PHP runtime as a test dependency, fragile process management, environment setup complexity, slows CI.

**Instead:** Extract expected values from PHP code manually, encode as constants/golden values in Kotlin tests. The PHP code is the reference, not a runtime dependency.

### Anti-Pattern 2: Database-Dependent Parity Tests

**What:** Requiring a running PostgreSQL instance for parity formula tests.

**Why bad:** Parity is about math and logic, not database connectivity. DB tests are slow, flaky, and conflate schema issues with logic issues.

**Instead:** Use `InMemoryTurnHarness` for behavioral tests. Use direct class instantiation for formula tests. Reserve database for integration tests that verify persistence correctness (separate concern from parity).

### Anti-Pattern 3: Approximate Assertions for Exact Formulas

**What:** Using `assertEquals(expected, actual, tolerance)` when the formula should produce exact integer results.

**Why bad:** Legacy PHP uses integer arithmetic in many places. A tolerance of 0.01 might hide a rounding error that causes gameplay inconsistency.

**Instead:** Use exact `assertEquals()` for integer formulas. Use tolerance only for explicitly floating-point formulas (like `getDexLog()` which returns a Double).

### Anti-Pattern 4: Testing Only Happy Path

**What:** Testing only the normal case (e.g., training a general with adequate stats) without boundary conditions.

**Why bad:** Legacy parity bugs often lurk at boundaries -- max values, zero values, negative deltas, type overflows (Short vs Int).

**Instead:** The existing tests show the right approach: test at-max, at-zero, negative, overflow, and boundary-threshold cases for every formula.

### Anti-Pattern 5: Mixing Parity Tests with Feature Tests

**What:** Putting parity assertions in the same test class as new-feature or refactoring tests.

**Why bad:** Parity tests must never change unless the legacy reference changes. Feature tests change as features evolve. Mixing them makes it unclear which changes are intentional.

**Instead:** Keep `qa/parity/` separate from `engine/`, `command/`, and `service/` test directories. Parity tests are immutable once validated; other tests evolve.

## Build Order for Parity Verification

The dependency graph determines which systems must be verified first:

```
Phase 1: Foundation (no game-logic dependencies)
  |
  +-- Constraint system parity (ConstraintParityTest)
  |     All commands depend on constraints
  |
  +-- Formula constants (BattleParityTest thresholds, getDexLevel, getTechLevel)
  |     War engine and economy depend on these
  |
  +-- RNG determinism (LiteHashDRBG, DeterministicRngTest)
        All randomized logic depends on this

Phase 2: Individual Systems (depend on Phase 1)
  |
  +-- Economy formulas (EconomyFormulaParityTest, EconomyEventParityTest)
  |     Turn processing depends on economy
  |
  +-- Battle formulas (BattleParityTest, WarFormulaTest, WarUnitCityParityTest)
  |     War resolution depends on battle engine
  |
  +-- Command logic (CommandParityTest per command)
  |     Turn execution depends on command correctness
  |
  +-- Tech research (TechResearchParityTest)
        Nation progression depends on tech formulas

Phase 3: Composite Systems (depend on Phase 2)
  |
  +-- NPC AI (NpcAiParityTest)
  |     Depends on: constraints, commands, economy, battle
  |
  +-- Turn pipeline ordering (TurnPipelineParityTest)
  |     Depends on: all turn steps being correct individually
  |
  +-- Golden snapshots (GoldenSnapshotTest)
        Depends on: commands, economy, turn pipeline

Phase 4: Full System (depend on Phase 3)
  |
  +-- Deterministic replay (DeterministicReplayParityTest)
  |     Depends on: all turn processing, command execution, CQRS
  |
  +-- E2E parity (frontend/e2e/parity/)
        Depends on: running systems, all backend parity passing
```

**Why this order matters:**
- If constraint parity is wrong, every command test gives misleading results
- If formula parity is wrong, golden snapshots fail with cascading differences
- If individual commands are wrong, turn processing tests are undebuggable
- E2E parity should only run after all backend parity is confirmed

## Test Organization Across Modules

### Current Layout (already established)

```
backend/game-app/src/test/kotlin/com/opensam/
  qa/
    parity/                          # Tier 1: Formula parity
      BattleParityTest.kt            # War formulas vs PHP
      CommandParityTest.kt           # Command output vs PHP
      ConstraintParityTest.kt        # Constraint Pass/Fail vs PHP
      EconomyEventParityTest.kt      # Economy events vs PHP
      EconomyFormulaParityTest.kt    # Economy formulas vs PHP
      NpcAiParityTest.kt             # NPC AI decisions vs PHP
      TechResearchParityTest.kt      # Tech formulas vs PHP
      TurnPipelineParityTest.kt      # Step ordering vs daemon.ts
    ApiContractTest.kt               # API contract validation
    GoldenValueTest.kt               # Locked regression values
  engine/
    FormulaParityTest.kt             # Cross-domain formula checks
    GoldenSnapshotTest.kt            # Multi-turn golden snapshot
    DeterministicReplayParityTest.kt # CQRS replay canonical output
    war/
      BattleEngineParityTest.kt      # Battle engine vs PHP
      WarUnitCityParityTest.kt       # City unit formulas vs PHP
  command/
    CommandParityTest.kt             # Additional command parity
  test/
    InMemoryTurnHarness.kt           # Test infrastructure

frontend/e2e/parity/
  parity-config.ts                   # Command + page catalog
  parity-helpers.ts                  # Auth, snapshots, assertions
  01-main.spec.ts                    # Main page parity
  02-links.spec.ts                   # Navigation parity
  03-pages.spec.ts                   # All 36 pages parity
  04-commands.spec.ts                # General command execution parity
  05-nation-commands.spec.ts         # Nation command execution parity
```

### Recommended Completion

The existing structure is sound. Gaps to fill:

| Missing Coverage | Where to Add | Depends On |
|-----------------|--------------|------------|
| Diplomacy formula parity | `qa/parity/DiplomacyParityTest.kt` | ConstraintParityTest |
| Event/disaster formula parity | `qa/parity/DisasterParityTest.kt` | EconomyFormulaParityTest |
| Unification condition parity | `qa/parity/UnificationParityTest.kt` | NationSnapshot tests |
| Individual command parity (per-command) | `qa/parity/command/` subdirectory | CommandParityTest base patterns |
| Scenario data parity (NPC stats) | `qa/parity/ScenarioDataParityTest.kt` | Game data JSON |
| Frontend display parity (field presence) | `e2e/parity/06-display.spec.ts` | All page routes working |

## Scalability Considerations

| Concern | Current (8 parity tests) | At 93 commands covered | At full coverage |
|---------|--------------------------|------------------------|------------------|
| Test runtime | < 5 seconds | ~30 seconds (all unit) | ~2 minutes (unit+integration) |
| Test organization | Single `qa/parity/` dir | Needs subdirectories by domain | Needs test tags/categories |
| Golden value maintenance | Manual hardcoded values | Per-command golden files (JSON) | Automated golden value extraction |
| CI gating | All tests run together | Parity tests as separate Gradle task | Parity gate blocks non-parity changes |

**Recommendation for scale:** When command parity tests exceed 30 files, introduce a `qa/parity/command/` subdirectory with one test file per command, and use JUnit `@Tag("parity")` to enable running `./gradlew test --tests "*.qa.parity.*"` as a separate CI step.

## Key Architectural Decisions for Parity Verification

| Decision | Rationale |
|----------|-----------|
| **Comments-as-reference, not runtime PHP** | `legacy-core/` is gitignored; tests must be self-contained |
| **LiteHashDRBG for determinism** | Enables golden value testing across all randomized logic |
| **InMemoryTurnHarness for behavioral tests** | Avoids database dependency while testing full command execution |
| **Three-tier separation** | Formula bugs caught at Tier 1 before they cascade to Tier 2/3 |
| **parity-config.ts command catalog** | Single source of truth for what commands exist and their effect scope |
| **Snapshot delta checking** | `hasGeneralEffectDelta()` etc. catch unexpected side effects |
| **Build-order dependency chain** | Constraints -> Formulas -> Commands -> AI -> Pipeline -> Replay -> E2E |

## Sources

- Direct codebase analysis of `backend/game-app/src/test/kotlin/com/opensam/qa/parity/` (8 test files)
- Direct codebase analysis of `backend/game-app/src/test/kotlin/com/opensam/engine/` (golden snapshot, replay, formula tests)
- Direct codebase analysis of `backend/game-app/src/test/kotlin/com/opensam/test/InMemoryTurnHarness.kt`
- Direct codebase analysis of `frontend/e2e/parity/` (config, helpers, 5 spec files)
- Architecture document: `.planning/codebase/ARCHITECTURE.md`
- Structure document: `.planning/codebase/STRUCTURE.md`
- Project document: `.planning/PROJECT.md`

---

*Architecture research: 2026-03-31*
