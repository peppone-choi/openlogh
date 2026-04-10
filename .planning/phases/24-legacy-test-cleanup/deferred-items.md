# Phase 24 Deferred Items

Residual failures after the clean Java 21 gate:

```text
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :game-app:cleanTest :game-app:test --continue

1838 tests completed, 59 failed, 1 skipped
```

## BROKEN — 37 failures

These suites remain because of harness / wiring debt outside the economy cleanup lane:

| Failures | Suite |
|---:|---|
| 19 | `com.openlogh.command.CommandRegistryTest` |
| 5 | `com.openlogh.command.CommandExecutorTest` |
| 5 | `com.openlogh.engine.GameplayIntegrationTest` |
| 4 | `com.openlogh.integration.ScenarioPlayableIntegrationTest` |
| 2 | `com.openlogh.command.ArgSchemaValidationTest` |
| 2 | `com.openlogh.engine.RealtimeServiceTest` |

## OUT-OF-SCOPE — 22 failures

These failures are unrelated to Phase 23/24 economy parity cleanup:

| Failures | Suite |
|---:|---|
| 4 | `com.openlogh.service.OperationMeritBonusTest` |
| 2 | `com.openlogh.engine.InMemoryTurnHarnessIntegrationTest` |
| 2 | `com.openlogh.qa.parity.GameEndParityTest$InheritancePointAwards` |
| 2 | `com.openlogh.repository.OperationPlanRepositoryTest` |
| 1 | `com.openlogh.engine.DiplomacyServiceTest` |
| 1 | `com.openlogh.engine.ai.NpcPolicyTest` |
| 1 | `com.openlogh.engine.tactical.DetectionServiceTest` |
| 1 | `com.openlogh.qa.parity.DisasterParityTest$SabotageInjury` |
| 1 | `com.openlogh.service.GameEventServiceTest` |
| 1 | `com.openlogh.service.OfficerServiceTest` |
| 6 | `com.openlogh.service.OperationPlanServiceTest` |

## Phase 24 exit state

- No `COVERED` failures remain.
- No `OBSOLETE` failures remain.
- No `MIGRATABLE` failures remain.
- The remaining 59 failures are the post-cleanup deferred baseline.
