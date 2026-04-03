# Phase 1: Deterministic Foundation - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace all non-deterministic RNG usage in game logic with the existing LiteHashDRBG system, add exception logging to silent catch blocks, and ensure deterministic entity processing order within turn ticks. This phase enables reliable parity verification for all subsequent phases.

</domain>

<decisions>
## Implementation Decisions

### RNG Replacement
- **D-01:** Replace `java.util.Random()` in `TurnService.registerAuction()` (line 1050) with a world-seeded `LiteHashDRBG` instance via the existing `RandUtil` pattern
- **D-02:** Replace `java.util.Random()` in `GeneralTrigger` (line 200) constructor parameter with `LiteHashDRBG`, injecting the world's RNG instance from the turn processing context
- **D-03:** Follow the established pattern from the 29 existing files that use `LiteHashDRBG`/`DeterministicRng` -- no new RNG infrastructure needed

### Exception Logging
- **D-04:** Add SLF4J `logger.warn()` or `logger.error()` calls to all exception-swallowing catch blocks in engine code, preserving existing behavior (don't change control flow)
- **D-05:** Focus on the 21 catch blocks in `TurnService.kt` first (highest parity impact), then address remaining engine files
- **D-06:** Log format: include exception message, stack trace reference, and contextual info (world ID, general ID, command type where applicable)

### Turn Ordering
- **D-07:** Sort entities by primary key (ID) before processing in turn steps to ensure deterministic order regardless of database query ordering
- **D-08:** Apply ordering at the `InMemoryTurnProcessor` / `TurnPipeline` level where entities are iterated, not at the repository/query level

### Cross-Language Verification
- **D-09:** Verify LiteHashDRBG parity using golden value tests with hardcoded expected outputs derived from PHP SHA-512 RNG execution -- extend existing `LiteHashDRBGTest.kt`
- **D-10:** Test vectors should cover: initial seed, sequential draws, large sequence (100+ draws), edge case seeds (0, MAX_LONG)

### Claude's Discretion
- Specific catch block triage order beyond TurnService (D-05 covers the priority; remaining files at Claude's judgment)
- Whether to add a `@Tag("parity")` annotation to new tests in this phase or defer to Phase 2
- RandUtil single-element list behavior fix approach (FOUND-05) -- verify PHP behavior first, then match

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### RNG System
- `backend/game-app/src/main/kotlin/com/opensam/engine/LiteHashDRBG.kt` -- Core deterministic RNG implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/DeterministicRng.kt` -- RNG factory/wrapper
- `backend/game-app/src/main/kotlin/com/opensam/engine/RandUtil.kt` -- RNG utility methods used throughout engine
- `backend/game-app/src/test/kotlin/com/opensam/engine/LiteHashDRBGTest.kt` -- Existing parity tests for RNG
- `backend/game-app/src/test/kotlin/com/opensam/engine/DeterministicRngTest.kt` -- RNG factory tests

### Files to Fix
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` -- Line 1050: java.util.Random in registerAuction()
- `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt` -- Line 200: java.util.Random constructor param

### Turn Processing
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/InMemoryTurnProcessor.kt` -- Turn processing pipeline (17 catch blocks)
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/TurnPipeline.kt` -- Ordered turn step chain
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnDaemon.kt` -- Turn scheduler

### Legacy Reference
- `legacy-core/hwe/func.php` -- PHP game functions with RNG usage patterns
- `legacy-core/src/daemon.ts` -- Turn daemon reference implementation

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `LiteHashDRBG`: Mature SHA-512 based DRBG already used by 29 files -- direct replacement target for java.util.Random
- `RandUtil`: Wrapper providing `nextFloat1()`, `nextRange()`, `nextRangeInt()`, `nextBool()`, `shuffle()` -- covers all random operations needed
- `DeterministicRng.create(...)`: Factory method for creating seeded RNG instances from world state
- `LiteHashDRBGTest.kt`: Existing golden value test pattern to extend

### Established Patterns
- Engine services receive RNG via constructor injection or method parameters (see GeneralAI.kt, BattleService.kt)
- Turn steps operate on `InMemoryWorldState` which provides access to world-level RNG seed
- Test infrastructure includes `InMemoryTurnHarness` for full behavioral testing without DB

### Integration Points
- `TurnService.registerAuction()` needs access to world's RNG (currently creates local java.util.Random)
- `GeneralTrigger` constructor needs LiteHashDRBG instead of java.util.Random default
- `InMemoryTurnProcessor` is the primary entity iteration point for ordering fixes

</code_context>

<specifics>
## Specific Ideas

No specific requirements -- this is a foundational infrastructure phase with clear technical objectives. Follow existing patterns established in the codebase.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 01-deterministic-foundation*
*Context gathered: 2026-03-31*
