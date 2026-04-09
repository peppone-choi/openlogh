# Deferred Items — Phase 12

Out-of-scope issues discovered during execution but not caused by plan changes.
These are pre-existing failures in unrelated code and are not blockers for Phase 12.

## Plan 12-01 (2026-04-09)

### Pre-existing test failures (207 tests, unrelated to operation_plan)

Full `./gradlew :game-app:test` run produced `1829 tests completed, 207 failed, 1 skipped`.
All failures are in files that predate this plan and are unrelated to OperationPlan/MissionObjective/OperationStatus.

Example failing tests:
- `com.openlogh.service.PlanetServiceTest.canonicalRegionForDisplay maps 남피 to 하북 code` — asserts on Three Kingdoms legacy city names (남피/하북) that are being rewritten
- `com.openlogh.service.ScenarioServiceTest.initializeWorld seeds cities with approval 50` — legacy scenario seed data
- `com.openlogh.service.ScenarioServiceTest.spawnScenarioNpcGeneralsForYear spawns delayed NPC on due year` — legacy NPC spawn logic

**Root cause:** These are remnants from the OpenSamguk (Three Kingdoms) → OpenLOGH rewrite in progress. They assert on Chinese city names like 남피 (Nampi) / 하북 (Habuk) that no longer exist in the LOGH domain.

**Verification that they are pre-existing (not caused by Plan 12-01):**
- `git log` shows `PlanetServiceTest.kt` and `CityServiceTest.kt` were last touched in commits `8ab11cfc` (initial LOGH transform) and `2e113181` (engine replacement), both well before Plan 12-01.
- My only changes to `backend/game-app/` are additive: new files under `engine/tactical/ai/MissionObjective.kt` (extended enum), `model/OperationStatus.kt`, `entity/OperationPlan.kt`, `repository/OperationPlanRepository.kt`, `resources/db/migration/V47__create_operation_plan.sql`, and 2 test files.
- Running only plan-12-01-targeted tests produces `BUILD SUCCESSFUL` with 7/7 passing.

**Scope decision:** Out of scope for Plan 12-01 (per execute-plan deviation rules scope boundary). These failures belong to the broader legacy cleanup effort and should be addressed by a dedicated cleanup plan (likely in a future milestone) or by the phases that are actively rewriting those modules.
