# Deferred Items — Phase 01 Session Foundation

## Pre-existing Test Compilation Errors

**Found during:** Plan 03, Task 1
**Scope:** Out of scope (pre-existing, not caused by plan changes)

9 test files in `backend/game-app/src/test/` have compilation errors preventing full test suite execution:

1. `command/ArgSchemaValidationTest.kt` — Unresolved references: recruit, trade, foundNation
2. `command/GeneralMilitaryCommandTest.kt` — Unresolved references: 출병, 집합, type inference failures
3. `command/GeneralPoliticalCommandTest.kt` — Compilation errors
4. `command/NationCommandTest.kt` — Compilation errors
5. `command/NationDiplomacyStrategicCommandTest.kt` — Compilation errors
6. `command/NationResearchSpecialCommandTest.kt` — Compilation errors
7. `command/NationResourceCommandTest.kt` — Compilation errors
8. `engine/DiplomacyServiceTest.kt` — Compilation errors
9. `test/InMemoryTurnHarness.kt` — Compilation errors (affects 5+ dependent test files)

**Impact:** Cannot run `./gradlew :game-app:test` for full test suite. Individual tests require temporarily excluding broken files.
**Recommendation:** Fix or disable these tests in a dedicated maintenance task.
