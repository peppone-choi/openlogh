# Phase 1: Deterministic Foundation - Research

**Researched:** 2026-03-31
**Domain:** Deterministic RNG, exception observability, turn processing order (Spring Boot 3 / Kotlin)
**Confidence:** HIGH

## Summary

Phase 1 addresses five foundational requirements that ensure all game execution is deterministic, observable, and correctly ordered. The codebase already has a mature `LiteHashDRBG` / `RandUtil` / `DeterministicRng` system used by 29+ files. Only two files still use `java.util.Random`: `TurnService.registerAuction()` (line 1050) and `CityHealTrigger` in `GeneralTrigger.kt` (line 200). The fix is straightforward pattern-following.

Exception logging requires adding `logger.warn()`/`logger.error()` calls to 16 silent `catch (_: Exception)` blocks across 11 engine files (TurnService has 3, TurnDaemon has 2, RealtimeService has 3, plus one each in 8 other files). Many catch blocks in TurnService and InMemoryTurnProcessor already have proper logging -- only the `catch (_:` pattern blocks are silent.

Turn ordering needs tiebreakers in two locations: `TurnService.executeGeneralCommandsUntil()` (line 424) and `InMemoryTurnProcessor.executeGeneralCommandsUntil()` (line 210), both of which sort generals by `turnTime` but lack a secondary sort key for determinism when multiple generals share the same turn time.

**Primary recommendation:** Follow existing `DeterministicRng.create()` pattern for RNG replacement, add SLF4J logging to all 16 silent catch blocks, add `.thenBy { it.id }` tiebreakers to both general sorting locations, and fix `RandUtil.choice()` to consume RNG state on single-element lists to match PHP `array_rand` behavior.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Replace `java.util.Random()` in `TurnService.registerAuction()` (line 1050) with a world-seeded `LiteHashDRBG` instance via the existing `RandUtil` pattern
- **D-02:** Replace `java.util.Random()` in `GeneralTrigger` (line 200) constructor parameter with `LiteHashDRBG`, injecting the world's RNG instance from the turn processing context
- **D-03:** Follow the established pattern from the 29 existing files that use `LiteHashDRBG`/`DeterministicRng` -- no new RNG infrastructure needed
- **D-04:** Add SLF4J `logger.warn()` or `logger.error()` calls to all exception-swallowing catch blocks in engine code, preserving existing behavior (don't change control flow)
- **D-05:** Focus on the 21 catch blocks in `TurnService.kt` first (highest parity impact), then address remaining engine files
- **D-06:** Log format: include exception message, stack trace reference, and contextual info (world ID, general ID, command type where applicable)
- **D-07:** Sort entities by primary key (ID) before processing in turn steps to ensure deterministic order regardless of database query ordering
- **D-08:** Apply ordering at the `InMemoryTurnProcessor` / `TurnPipeline` level where entities are iterated, not at the repository/query level
- **D-09:** Verify LiteHashDRBG parity using golden value tests with hardcoded expected outputs derived from PHP SHA-512 RNG execution -- extend existing `LiteHashDRBGTest.kt`
- **D-10:** Test vectors should cover: initial seed, sequential draws, large sequence (100+ draws), edge case seeds (0, MAX_LONG)

### Claude's Discretion
- Specific catch block triage order beyond TurnService (D-05 covers the priority; remaining files at Claude's judgment)
- Whether to add a `@Tag("parity")` annotation to new tests in this phase or defer to Phase 2
- RandUtil single-element list behavior fix approach (FOUND-05) -- verify PHP behavior first, then match

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FOUND-01 | Replace java.util.Random() with LiteHashDRBG in TurnService.registerAuction() and GeneralTrigger | Exact locations identified: TurnService.kt:1050, GeneralTrigger.kt:200. Established DeterministicRng.create() pattern documented. |
| FOUND-02 | Verify LiteHashDRBG cross-language parity (Kotlin SHA-512 output matches PHP implementation) | Existing LiteHashDRBGTest.kt has golden vector tests. Research identifies gaps: needs 100+ sequential draws, edge case seeds (0, MAX_LONG), and nextFloat1/nextLegacyInt coverage. |
| FOUND-03 | Add logging to all exception-swallowing catch blocks in engine code (20+ locations) | Full inventory: 16 silent `catch (_: Exception)` blocks across 11 files. TurnService has 21 total catch blocks (18 already logged, 3 silent). InMemoryTurnProcessor has 17 catches all already logged. |
| FOUND-04 | Add turn ordering tiebreakers to prevent non-deterministic entity processing order | Two locations: TurnService.kt:424 and InMemoryTurnProcessor.kt:210 both sort by turnTime only. Add `.thenBy { it.id }` secondary sort. |
| FOUND-05 | Fix RandUtil.choice() single-element bias (PHP array_rand vs Kotlin behavior) | Critical finding: `nextLegacyInt(0)` short-circuits to return 0 without consuming RNG bytes. PHP array_rand likely consumes RNG state even for single-element arrays. Fix: consume one RNG draw when size == 1 to maintain stream synchronization. |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3 | 3.x (project-set) | Application framework | Already in use, provides DI/IoC |
| SLF4J + Logback | Spring Boot managed | Logging facade | Standard Spring Boot logging, already used across engine |
| JUnit 5 | Spring Boot managed | Testing | Already in use for LiteHashDRBGTest, RandUtilTest |
| Kotlin stdlib | Project-set | Language runtime | `sortedWith`, `compareBy`, `thenBy` for sorting |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| H2 | Test runtime | In-memory test DB | Already configured in build.gradle.kts for tests |

### Alternatives Considered
None -- this phase uses only existing project dependencies. No new libraries needed.

## Architecture Patterns

### Recommended Project Structure
No new files/directories needed. Changes are surgical modifications to existing files:
```
backend/game-app/src/main/kotlin/com/opensam/engine/
  TurnService.kt               # Fix: RNG replacement (line 1050), catch block logging (3 blocks)
  RandUtil.kt                   # Fix: choice() single-element behavior
  trigger/GeneralTrigger.kt     # Fix: RNG replacement (line 200)
  TurnDaemon.kt                 # Fix: catch block logging (2 blocks)
  RealtimeService.kt            # Fix: catch block logging (3 blocks)
  EconomyService.kt             # Fix: catch block logging (1 block)
  EventService.kt               # Fix: catch block logging (1 block)
  EventActionService.kt         # Fix: catch block logging (1 block)
  GeneralMaintenanceService.kt  # Fix: catch block logging (1 block)
  SpecialAssignmentService.kt   # Fix: catch block logging (1 block)
  ai/GeneralAI.kt               # Fix: catch block logging (1 block)
  modifier/InheritBuffModifier.kt # Fix: catch block logging (1 block)
  war/WarAftermath.kt           # Fix: catch block logging (1 block)
  turn/cqrs/memory/InMemoryTurnProcessor.kt  # Fix: sort tiebreaker (line 210)
backend/game-app/src/test/kotlin/com/opensam/engine/
  LiteHashDRBGTest.kt           # Extend: golden value parity tests
  RandUtilTest.kt               # Add: single-element choice test
```

### Pattern 1: DeterministicRng.create() for World-Scoped RNG
**What:** Factory method that creates a seeded LiteHashDRBG instance from a hidden seed plus contextual tags
**When to use:** Whenever game logic needs random numbers -- the seed ensures determinism
**Example:**
```kotlin
// Source: TurnService.kt lines 617-619 (existing pattern)
val generalHiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
val rng = DeterministicRng.create(
    generalHiddenSeed, "generalCommand", general.id, world.currentYear, world.currentMonth, actionCode
)
```

### Pattern 2: RandUtil Wrapper for Game Random Operations
**What:** `RandUtil(rng)` wraps `LiteHashDRBG` and provides game-level random operations
**When to use:** When you need `nextFloat1()`, `nextRangeInt()`, `shuffle()`, `choice()`, `nextBool(prob)`
**Example:**
```kotlin
// Source: RandUtil.kt (existing API)
val randUtil = RandUtil(rng as LiteHashDRBG)
val result = randUtil.nextRangeInt(1, 10)  // inclusive range
val item = randUtil.choice(items)           // random selection
val shuffled = randUtil.shuffle(list)       // deterministic shuffle
```

### Pattern 3: Exception Logging with Context
**What:** Catch blocks log with SLF4J including exception message, stack trace, and game-contextual info
**When to use:** Every catch block that previously swallowed exceptions
**Example:**
```kotlin
// Source: TurnService.kt line 309 (existing good pattern to follow)
} catch (e: Exception) {
    logger.error("executeGeneralCommandsUntil failed for world {}: {}", worldId, e.message, e)
}

// For blocks that are try-expression fallbacks (value resolution), use warn level:
val startYear = try {
    scenarioService.getScenario(world.scenarioCode).startYear
} catch (e: Exception) {
    logger.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
    world.currentYear.toInt()
}
```

### Pattern 4: Deterministic Sort with Tiebreaker
**What:** Sort entities by primary criterion then by ID for determinism
**When to use:** Any place entities are sorted for iteration in turn processing
**Example:**
```kotlin
// Current (non-deterministic when turnTime ties):
val generals = state.generals.values.sortedBy { it.turnTime }

// Fixed (deterministic):
val generals = state.generals.values.sortedWith(compareBy<GeneralSnapshot> { it.turnTime }.thenBy { it.id })
```

### Anti-Patterns to Avoid
- **Creating new `java.util.Random()`:** Always use `DeterministicRng.create()` or accept an `LiteHashDRBG`/`kotlin.random.Random` parameter
- **Suppressing exceptions silently:** Every `catch` block must at minimum `logger.warn()` with the exception message
- **Sorting by single field:** When entities may share the same sort key value, always add a deterministic tiebreaker (typically `.thenBy { it.id }`)
- **Changing control flow in catch blocks:** D-04 explicitly says preserve existing behavior -- only add logging, don't alter what happens after the catch

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Deterministic RNG | Custom PRNG wrapper | `DeterministicRng.create()` + `RandUtil` | Already SHA-512 parity-verified against PHP, 29+ files depend on it |
| Exception logging | Custom logging framework | SLF4J `LoggerFactory.getLogger()` | Standard Spring Boot pattern, already used in TurnService/InMemoryTurnProcessor |
| Stable sorting | Manual sort comparator | `sortedWith(compareBy { ... }.thenBy { ... })` | Kotlin stdlib, well-tested, readable |

## Common Pitfalls

### Pitfall 1: RNG State Desynchronization on Single-Element choice()
**What goes wrong:** `RandUtil.choice()` calls `nextLegacyInt(0)` which returns 0 without consuming RNG bytes. If PHP's `array_rand` consumes RNG state for single-element arrays, subsequent random calls produce different results between PHP and Kotlin.
**Why it happens:** `LiteHashDRBG.nextLegacyInt(max=0)` short-circuits at line 110-111 with `return 0` before calling `nextIntBits()`.
**How to avoid:** Add explicit single-element guard in `choice()`: when `items.size == 1`, return `items[0]` but still consume one RNG draw (call `rng.nextLegacyInt(1)` or similar no-op draw to advance state). Verify against PHP behavior first.
**Warning signs:** Golden value tests pass individually but turn replay produces different game state after a command that calls `choice()` on a single-candidate list.

### Pitfall 2: Forgetting thenBy in InMemoryTurnProcessor
**What goes wrong:** The fix is applied to `TurnService.executeGeneralCommandsUntil()` but missed in `InMemoryTurnProcessor.executeGeneralCommandsUntil()`. Both paths are used (JPA path vs in-memory path).
**Why it happens:** There are two parallel implementations of the same logic.
**How to avoid:** Fix both locations together. Search for `sortedBy { it.turnTime }` across the codebase.
**Warning signs:** Tests pass in one code path but fail in the other.

### Pitfall 3: Changing Control Flow in Catch Blocks
**What goes wrong:** While adding logging, the developer also changes what the catch block does (e.g., adding `return` or rethrowing) which breaks legacy parity.
**Why it happens:** Natural instinct to "fix" the error handling while touching it.
**How to avoid:** D-04 is explicit: preserve existing behavior. Only add `logger.warn()`/`logger.error()` calls. The `catch (_: Exception)` becomes `catch (e: Exception)` with a log statement, nothing else changes.
**Warning signs:** Tests that depend on silent fallback behavior start failing.

### Pitfall 4: Not Verifying RNG Consumption Count in Parity Tests
**What goes wrong:** A golden value test verifies individual RNG outputs match but doesn't verify that the same number of RNG draws were consumed.
**Why it happens:** Testing "does nextLegacyInt(100) return X" without checking how many bytes were consumed from the internal buffer.
**How to avoid:** Test vectors should include sequential draws that depend on cumulative state. A 100+ draw sequence test (D-10) catches this.
**Warning signs:** Individual draw tests pass but sequence tests fail at draw N+1.

### Pitfall 5: Missing Logger Declaration in Files
**What goes wrong:** Some files may not have a `logger` field declared. Adding `logger.warn()` without checking causes compile errors.
**Why it happens:** Not all engine files have logging configured yet.
**How to avoid:** Check each file for an existing `private val logger = LoggerFactory.getLogger(...)` declaration before adding log calls. Add one if missing.
**Warning signs:** Compile errors after adding catch block logging.

## Code Examples

### RNG Replacement in TurnService.registerAuction()
```kotlin
// BEFORE (TurnService.kt line 1050):
val rng = java.util.Random()

// AFTER:
val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
val rng = RandUtil(
    DeterministicRng.create(hiddenSeed, "registerAuction", world.currentYear, world.currentMonth) as LiteHashDRBG
)
// Then replace rng.nextDouble() with rng.nextFloat1()
// and rng.nextInt(n) with rng.nextRangeInt(0, n - 1) or rng.rng.nextLegacyInt((n-1).toLong()).toInt()
```

### RNG Replacement in CityHealTrigger
```kotlin
// BEFORE (GeneralTrigger.kt line 200):
class CityHealTrigger(
    private val general: General,
    private val cityMates: List<General>,
    private val rng: java.util.Random = java.util.Random(),
) : GeneralTrigger {

// AFTER:
class CityHealTrigger(
    private val general: General,
    private val cityMates: List<General>,
    private val rng: kotlin.random.Random,  // must be LiteHashDRBG, injected from caller
) : GeneralTrigger {
```

### Silent Catch Block Fix
```kotlin
// BEFORE (TurnService.kt line 776):
val startYear = try {
    scenarioService.getScenario(world.scenarioCode).startYear
} catch (_: Exception) {
    world.currentYear.toInt()
}

// AFTER:
val startYear = try {
    scenarioService.getScenario(world.scenarioCode).startYear
} catch (e: Exception) {
    logger.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
    world.currentYear.toInt()
}
```

### Sort Tiebreaker Fix
```kotlin
// BEFORE (TurnService.kt line 424):
val generals = ports.allGenerals().map { it.toEntity() }.sortedBy { it.turnTime }

// AFTER:
val generals = ports.allGenerals().map { it.toEntity() }
    .sortedWith(compareBy<com.opensam.entity.General> { it.turnTime }.thenBy { it.id })

// BEFORE (InMemoryTurnProcessor.kt line 210):
val generals = state.generals.values.sortedBy { it.turnTime }

// AFTER:
val generals = state.generals.values.sortedWith(
    compareBy<GeneralSnapshot> { it.turnTime }.thenBy { it.id }
)
```

### RandUtil.choice() Single-Element Fix
```kotlin
// BEFORE (RandUtil.kt line 81-87):
fun <T> choice(items: List<T>): T {
    if (items.isEmpty()) {
        throw IllegalArgumentException()
    }
    val keyIdx = rng.nextLegacyInt((items.size - 1).toLong()).toInt()
    return items[keyIdx]
}

// AFTER (consume RNG state even for single-element to match PHP array_rand):
fun <T> choice(items: List<T>): T {
    if (items.isEmpty()) {
        throw IllegalArgumentException()
    }
    if (items.size == 1) {
        // PHP array_rand on single-element array still consumes RNG state.
        // We must advance RNG to maintain stream synchronization.
        rng.nextLegacyInt(1L)  // consume one draw, result is always 0
        return items[0]
    }
    val keyIdx = rng.nextLegacyInt((items.size - 1).toLong()).toInt()
    return items[keyIdx]
}
```
**Note on FOUND-05:** The exact PHP behavior should be verified first (D-discretion). The fix above is the most likely correct approach: call `nextLegacyInt(1)` which forces a 1-bit draw. If PHP's `array_rand` on a single-element array does NOT consume RNG state, then the current Kotlin code is already correct and FOUND-05 may need to be re-evaluated. The key verification: run PHP code `$rng = new \sammo\LiteHashDRBG("test"); $a = $rng->nextLegacyInt(100); array_rand(["x"]); $b = $rng->nextLegacyInt(100);` and compare with/without the `array_rand` call.

### Extended LiteHashDRBG Parity Test
```kotlin
// Extend LiteHashDRBGTest.kt with edge case vectors
@Test
fun `100 sequential draws match php vectors`() {
    val rng = LiteHashDRBG.build("parity-test-seed")
    val expected = listOf(/* 100 values from PHP execution */)
    val actual = (1..100).map { rng.nextLegacyInt(1000) }
    assertEquals(expected, actual)
}

@Test
fun `edge case seeds produce correct sequences`() {
    // Zero seed
    val rng0 = LiteHashDRBG.build("0")
    // ... verify against PHP output

    // Empty string seed
    val rngEmpty = LiteHashDRBG.build("")
    // ... verify against PHP output
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `java.util.Random()` for game logic | `LiteHashDRBG` (SHA-512 DRBG) | Already migrated (29 files) | Only 2 files remain to fix |
| Silent exception swallowing | SLF4J structured logging | Partially done (InMemoryTurnProcessor) | 16 silent blocks remain |
| Single-field entity sorting | Multi-field deterministic sorting | Needs implementation | Both TurnService and InMemoryTurnProcessor affected |

## Open Questions

1. **PHP array_rand single-element RNG consumption**
   - What we know: Kotlin `nextLegacyInt(0)` does NOT consume RNG bytes. PHP behavior is uncertain without the legacy-core source.
   - What's unclear: Whether PHP's custom `LiteHashDRBG::choice()` (or its equivalent wrapper around `array_rand`) consumes state for single-element arrays.
   - Recommendation: The CONTEXT.md marks this as Claude's discretion. Implement the guard (consume one draw for single-element), add a test, and tag it `@Tag("parity")` for later PHP verification. If PHP is later confirmed to NOT consume state, the fix can be reverted. Consuming state is the safer default -- it's easier to remove a draw than to add one later.

2. **CityHealTrigger caller injection**
   - What we know: `CityHealTrigger` currently has `rng: java.util.Random = java.util.Random()` as a default parameter. The caller (presumably in trigger building code) needs to pass a `LiteHashDRBG` instance instead.
   - What's unclear: The exact call site that constructs `CityHealTrigger` and whether it has access to the world RNG context.
   - Recommendation: Search for `CityHealTrigger(` instantiation sites and ensure the world-scoped RNG is threaded through.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Spring Boot managed) |
| Config file | `backend/game-app/build.gradle.kts` (test deps) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.LiteHashDRBGTest' --tests 'com.opensam.engine.RandUtilTest' --tests 'com.opensam.engine.DeterministicRngTest' --no-daemon` |
| Full suite command | `cd backend && ./gradlew test --no-daemon` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FOUND-01 | No java.util.Random in game logic | unit | `./gradlew :game-app:test --tests 'com.opensam.engine.LiteHashDRBGTest' --no-daemon` + grep verification | Existing (extend) |
| FOUND-02 | LiteHashDRBG cross-language parity | unit | `./gradlew :game-app:test --tests 'com.opensam.engine.LiteHashDRBGTest' --no-daemon` | Existing (extend) |
| FOUND-03 | Exception logging in catch blocks | manual-only | `grep -rn 'catch (_:' backend/game-app/src/main/kotlin/com/opensam/engine/` should return 0 results | N/A |
| FOUND-04 | Turn ordering tiebreakers | unit | `./gradlew :game-app:test --tests 'com.opensam.engine.DeterministicReplayParityTest' --no-daemon` | Existing (verify) |
| FOUND-05 | RandUtil.choice() single-element | unit | `./gradlew :game-app:test --tests 'com.opensam.engine.RandUtilTest' --no-daemon` | Existing (extend) |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.*' --no-daemon`
- **Per wave merge:** `cd backend && ./gradlew test --no-daemon`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `LiteHashDRBGTest.kt` -- needs 100+ sequential draw test, edge case seed tests (FOUND-02)
- [ ] `RandUtilTest.kt` -- needs single-element `choice()` test (FOUND-05)
- [ ] No new test files needed -- existing test infrastructure covers all requirements

## Project Constraints (from CLAUDE.md)

- **Simplicity First:** Minimum code that solves the problem. No speculative features.
- **Surgical Changes:** Touch only what you must. Match existing style.
- **Parity Target:** `legacy-core/` PHP source (not available in repo, but patterns established in existing Kotlin code).
- **Field Naming:** Follow core conventions (`intel` not `intelligence`, `crew`/`crewType`/`train`/`atmos`).
- **Testing:** `./verify pre-commit` and `./verify ci` are the verification commands.
- **Multi-Process Architecture:** `gateway-app` + versioned `game-app` JVMs.
- **Backend Stack:** Spring Boot 3 (Kotlin), PostgreSQL 16, Redis 7.
- **Development method:** Karpathy Method -- think before coding, simplicity first, surgical changes, goal-driven execution.

## Sources

### Primary (HIGH confidence)
- `backend/game-app/src/main/kotlin/com/opensam/engine/LiteHashDRBG.kt` -- Full RNG implementation reviewed, line-by-line analysis of nextLegacyInt short-circuit behavior
- `backend/game-app/src/main/kotlin/com/opensam/engine/RandUtil.kt` -- Full utility API reviewed, choice() single-element behavior confirmed
- `backend/game-app/src/main/kotlin/com/opensam/engine/DeterministicRng.kt` -- Factory pattern reviewed
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` -- All 21 catch blocks enumerated, RNG usage at line 1050 confirmed
- `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt` -- CityHealTrigger RNG usage at line 200 confirmed
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt` -- Sort location at line 210, 17 catch blocks (all already logged)
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/TurnPipeline.kt` -- 1 catch block (already logged with logger.error)
- `backend/game-app/src/test/kotlin/com/opensam/engine/LiteHashDRBGTest.kt` -- Existing golden vector tests reviewed
- `backend/game-app/src/test/kotlin/com/opensam/engine/RandUtilTest.kt` -- Existing shuffle/choice tests reviewed

### Secondary (MEDIUM confidence)
- [PHP array_rand documentation](https://www.php.net/manual/en/function.array-rand.php) -- PHP behavior for single-element arrays (RNG consumption uncertain)

### Tertiary (LOW confidence)
- PHP `array_rand` single-element RNG consumption: Cannot verify without legacy-core PHP source. Fix approach is conservative (consume state to match expected PHP behavior). Tagged for validation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all existing patterns confirmed by code review
- Architecture: HIGH -- surgical changes to identified files, established patterns to follow
- Pitfalls: HIGH -- all issues discovered through direct code analysis (not speculation)
- FOUND-05 fix approach: MEDIUM -- PHP behavior needs verification, but conservative approach (consume RNG state) is defensible

**Research date:** 2026-03-31
**Valid until:** 2026-04-30 (stable -- foundational infrastructure unlikely to change)
