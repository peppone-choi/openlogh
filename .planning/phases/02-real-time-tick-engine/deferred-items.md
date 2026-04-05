# Deferred Items - Phase 02

## Pre-existing Test Compilation Errors (Phase 1 Rename Leftovers)

Discovered during 02-03 execution. 57 test files have compilation errors from Phase 1 entity renames that were not applied to test sources. These block `compileTestKotlin` entirely.

**Impact:** No test files can be compiled or run in game-app module.

**Root cause:** Phase 1 renamed entities (City->Planet, Nation->Faction, worldId->sessionId, etc.) but many test files still reference old names.

**Affected files (57):** See `./gradlew :game-app:compileTestKotlin` output for full list. Includes files in:
- `command/` (7 files)
- `engine/` (25+ files)
- `service/` (10+ files)
- `qa/parity/` (10+ files)
- `entity/` (1 file)

**Partial fixes applied in 02-03:**
- ScenarioServiceTest.kt: deleteByWorldId -> deleteBySessionId, City -> Planet
- NationServiceTest.kt: Nation -> Faction
- RecordServiceTest.kt: findByWorldId -> findBySessionId, worldId -> sessionId

**Recommendation:** Create a dedicated plan to batch-fix all test compilation errors before any further test-dependent work.
