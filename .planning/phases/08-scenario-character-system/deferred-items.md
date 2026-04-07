# Deferred Items - Phase 08

## Pre-existing Test Compilation Errors

Found during 08-01 Task 2 verification. These errors exist in test files unrelated to this plan:

1. **DetectionServiceTest.kt** (lines 164, 169, 174, 188, 189): `CommandRange` type mismatch and `commandRangeMax` unresolved reference
2. **TacticalBattleEngineTest.kt** (line 169): `ticksSinceStanceChange` unresolved reference
3. **TacticalBattleIntegrationTest.kt** (lines 76, 85, 86, 89): `CommandRange` type mismatch and `ticksSinceLastOrder` unresolved reference

These appear to be caused by a recent refactor of `CommandRange` from `Double` to a data class, and removal/rename of timing fields. Not caused by Plan 08-01 changes.
