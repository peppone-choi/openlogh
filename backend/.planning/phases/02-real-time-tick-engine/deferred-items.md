# Deferred Items - Phase 02

## Pre-existing Test Compilation Errors (Domain Rename)

60+ test files have compilation errors from the OpenSamguk -> OpenLOGH domain rename. These are NOT caused by Phase 2 changes.

### Affected packages:
- `com.openlogh.command` (ArgSchemaValidationTest, CommandExecutorTest, ConstraintChainTest, GeneralMilitaryCommandTest, GeneralPoliticalCommandTest, NationCommandTest, NationDiplomacyStrategicCommandTest)
- `com.openlogh.engine` (DeterministicReplayParityTest, DiplomacyServiceTest, DuelSimulationTest, EconomyServiceTest, EdgeCaseTest, EventActionServiceTest, EventServiceTest, FormulaParityTest, GameplayIntegrationTest, GeneralMaintenanceServiceTest, GoldenSnapshotTest, InMemoryTurnHarnessIntegrationTest, NpcSpawnServiceTest, NumericParityGoldenTest, SpecialAssignmentServiceTest, TurnDaemonTest, TurnServiceTest, UnificationServiceTest, YearbookServiceTest)
- `com.openlogh.engine.ai` (GeneralAITest, NationAITest)
- `com.openlogh.engine.turn` (TurnCoordinatorIntegrationTest, TurnCoordinatorTest)
- `com.openlogh.engine.war` (BattleEngineWithPhasesTest, BattleFormulaMatrixTest, BattleServiceTest, BattleTriggerTest, FieldBattleTriggerTest, SiegeParityTest, SustainedChargeTriggerTest, WarAftermathTest)
- `com.openlogh.entity` (SelectPoolTest)
- `com.openlogh.qa` (ApiContractTest, BattleParityTest, CommandParityTest, DiplomacyParityTest, DisasterParityTest, EconomyEventParityTest, EconomyFormulaParityTest, EconomyIntegrationParityTest, GameEndParityTest, NpcAiParityTest)
- `com.openlogh.service` (AdminServiceTest, CityServiceTest, CommandServiceTest, FrontInfoServiceTest, GameEventServiceTest, GeneralServiceTest, HistoryServiceTest, InheritanceServiceTest, MessageServiceTest, NationServiceTest, RecordServiceTest, ScenarioServiceTest)

### Common issues:
- Old entity class names: General, City, Nation, GeneralTurn, NationTurn
- Old field names: worldId, generalId, nationId, capitalCityId, troopId
- Old method names: deleteByWorldId, findByWorldIdAndIsDeadFalse, etc.
- Type inference failures from renamed class references

### Recommendation:
A dedicated cleanup pass should rename all test files to use the new domain terms (Officer, Planet, Faction, OfficerTurn, FactionTurn, sessionId, officerId, factionId).
