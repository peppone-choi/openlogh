# Phase 1: Entity Model Foundation - Research

**Researched:** 2026-04-05
**Domain:** JPA Entity Refactoring + Flyway Migration (Kotlin/Spring Boot)
**Confidence:** HIGH

## Summary

This phase transforms the OpenSamguk entity model into the LOGH domain model. The codebase currently uses Three Kingdoms terminology (General, City, Nation, Troop, Emperor, WorldState) with a 5-stat system (leadership, strength, intel, politics, charm). The target is LOGH terminology (Officer, Planet, Faction, Fleet, Sovereign, SessionState) with an 8-stat system that renames 2 existing stats and adds 3 new ones.

The scope is large but mechanically straightforward: rename 6 entity classes, rename/add DB columns via Flyway V28+, and propagate changes across ~250+ backend Kotlin files and ~30+ frontend TypeScript files. The highest risk area is the CQRS in-memory snapshot layer (`InMemoryWorldState.kt` and related files) which duplicates every entity field as a snapshot data class and must stay perfectly synchronized.

**Primary recommendation:** Execute as a single coordinated migration (V28) that renames all tables/columns and adds new stat fields atomically, followed by systematic Kotlin class + DTO renames, then frontend type updates. Do NOT attempt incremental per-entity migrations -- the entities are deeply cross-referenced.

## Current State Analysis

### Entity Inventory

#### General.kt -> Officer (250 references across backend)
- **Table:** `general` (34 columns + 3 JSONB)
- **5-stat fields:** `leadership`/`leadershipExp`, `strength`/`strengthExp`, `intel`/`intelExp`, `politics`/`politicsExp`, `charm`/`charmExp`
- **Resource fields (OpenSamguk terms):** `gold`, `rice`, `crew`, `crewType`, `train`, `atmos`
- **Item fields (OpenSamguk terms):** `weaponCode`, `bookCode`, `horseCode`, `itemCode`
- **Key FKs:** `nationId`, `cityId`, `troopId`, `worldId`, `userId`
- **Related entities:** `GeneralTurn`, `GeneralRecord`, `GeneralAccessLog`, `OldGeneral`
- **DTO:** `GeneralResponse`, `BestGeneralResponse` in ResponseDtos.kt

#### City.kt -> Planet (229 references across backend)
- **Table:** `city` (23 columns + 2 JSONB)
- **Resource fields:** `pop`/`popMax`, `agri`/`agriMax`, `comm`/`commMax`, `secu`/`secuMax`, `trust`, `trade`, `dead`, `def`/`defMax`, `wall`/`wallMax`
- **Key FKs:** `nationId`, `worldId`
- **Related:** `CityConst.kt` model, `MapService`, `CityService`
- **DTO:** `CityResponse` in ResponseDtos.kt

#### Nation.kt -> Faction (186 references across backend)
- **Table:** `nation` (22 columns + 2 JSONB)
- **Resource fields:** `gold`, `rice`, `bill`, `rate`, `rateTmp`, `tech`, `power`, `gennum`, `level`, `typeCode`
- **Key FKs:** `capitalCityId`, `chiefGeneralId`, `worldId`
- **Related entities:** `NationTurn`, `NationFlag`, `OldNation`, `Diplomacy`
- **DTO:** `NationResponse` in ResponseDtos.kt

#### Troop.kt -> Fleet (26 references across backend)
- **Table:** `troop` (6 columns + 1 JSONB)
- **Fields:** `leaderGeneralId`, `nationId`, `name`
- **DTO:** `TroopResponse` in ResponseDtos.kt

#### Emperor.kt -> Sovereign (13 references across backend)
- **Table:** `emperor` (34 columns + 2 JSONB)
- **Contains l5-l12 name/pic fields (hall of fame ranks)**
- **Related:** `UnificationService`, `CommandRegistry`, `ConstraintHelper`

#### WorldState.kt -> SessionState (99 references across backend)
- **Table:** `world_state` (12 columns + 2 JSONB)
- **Exists in BOTH game-app AND gateway-app** (separate entity classes)
- **Gateway:** `gateway/entity/WorldState.kt`, `gateway/repository/WorldStateRepository.kt`
- **Game-app:** `entity/WorldState.kt`, `repository/WorldStateRepository.kt`
- **DTO:** `WorldStateResponse` in both modules

### CQRS Snapshot Layer (Critical)

`InMemoryWorldState.kt` contains snapshot data classes that mirror every entity field:
- `GeneralSnapshot` -- mirrors all 34+ General fields
- `CitySnapshot` -- mirrors all City fields
- `NationSnapshot` -- mirrors all Nation fields
- `TroopSnapshot` -- mirrors all Troop fields
- `DiplomacySnapshot`, `GeneralTurnSnapshot`, `NationTurnSnapshot`

Related mapper files that convert between entities and snapshots:
- `SnapshotEntityMapper.kt` -- entity-to-snapshot and snapshot-to-entity conversion
- `WorldStateLoader.kt` -- loads entities into snapshots
- `WorldStatePersister.kt` -- persists snapshots back to entities
- `JpaWorldPorts.kt`, `CachingWorldPorts.kt`, `InMemoryWorldPorts.kt`
- `WorldReadPort.kt`, `WorldWritePort.kt` -- port interfaces

### WarUnit Layer

`WarUnitGeneral.kt` copies stat fields from General into its own properties:
```kotlin
leadership = general.leadership.toInt()
strength = general.strength.toInt()
intel = general.intel.toInt()
```
The parent `WarUnit` class has its own `strength`, `intel`, `leadership` etc. fields used in battle calculations across 20+ battle/trigger test files.

## Required Changes

### 1. Stat System: 5-stat -> 8-stat

| Current Field | New Field | Change Type |
|---------------|-----------|-------------|
| `leadership` / `leadership_exp` | `leadership` / `leadership_exp` | **No change** |
| `strength` / `strength_exp` | `command` / `command_exp` | **Rename** |
| `intel` / `intel_exp` | `intelligence` / `intelligence_exp` | **Rename** |
| `politics` / `politics_exp` | `politics` / `politics_exp` | **No change** |
| `charm` / `charm_exp` | `administration` / `administration_exp` | **Rename** |
| (new) | `mobility` / `mobility_exp` | **Add** (default 50/0) |
| (new) | `attack` / `attack_exp` | **Add** (default 50/0) |
| (new) | `defense` / `defense_exp` | **Add** (default 50/0) |

**PCP stats** (political): leadership, politics, administration, intelligence
**MCP stats** (military): command, mobility, attack, defense

Model as a simple enum + companion constants:
```kotlin
enum class StatCategory { PCP, MCP }

enum class OfficerStat(val category: StatCategory) {
    LEADERSHIP(StatCategory.PCP),
    POLITICS(StatCategory.PCP),
    ADMINISTRATION(StatCategory.PCP),
    INTELLIGENCE(StatCategory.PCP),
    COMMAND(StatCategory.MCP),
    MOBILITY(StatCategory.MCP),
    ATTACK(StatCategory.MCP),
    DEFENSE(StatCategory.MCP),
}
```

### 2. Resource Field Renames (General/Officer)

| Current | New | DB Column |
|---------|-----|-----------|
| `gold` | `funds` | `funds` |
| `rice` | `supplies` | `supplies` |
| `crew` | `ships` | `ships` |
| `crewType` | `shipClass` | `ship_class` |
| `train` | `training` | `training` |
| `atmos` | `morale` | `morale` |

### 3. Item Field Renames (General/Officer)

| Current | New | DB Column |
|---------|-----|-----------|
| `weaponCode` | `flagshipCode` | `flagship_code` |
| `bookCode` | `equipCode` | `equip_code` |
| `horseCode` | `engineCode` | `engine_code` |
| `itemCode` | `accessoryCode` | `accessory_code` |

### 4. City -> Planet Field Renames

| Current | New | DB Column |
|---------|-----|-----------|
| `pop`/`popMax` | `population`/`populationMax` | `population`/`population_max` |
| `agri`/`agriMax` | `production`/`productionMax` | `production`/`production_max` |
| `comm`/`commMax` | `commerce`/`commerceMax` | `commerce`/`commerce_max` |
| `secu`/`secuMax` | `security`/`securityMax` | `security`/`security_max` |
| `trust` | `approval` | `approval` |
| `def`/`defMax` | `orbitalDefense`/`orbitalDefenseMax` | `orbital_defense`/`orbital_defense_max` |
| `wall`/`wallMax` | `fortress`/`fortressMax` | `fortress`/`fortress_max` |
| `trade` | `tradeRoute` | `trade_route` |

### 5. Nation -> Faction Field Renames

| Current | New | DB Column |
|---------|-----|-----------|
| `gold` | `funds` | `funds` |
| `rice` | `supplies` | `supplies` |
| `bill` | `taxRate` | `tax_rate` |
| `rate`/`rateTmp` | `conscriptionRate`/`conscriptionRateTmp` | `conscription_rate`/`conscription_rate_tmp` |
| `tech` | `techLevel` | `tech_level` |
| `power` | `militaryPower` | `military_power` |
| `gennum` | `officerCount` | `officer_count` |
| `level` | `factionRank` | `faction_rank` |
| `typeCode` | `factionType` | `faction_type` |
| `chiefGeneralId` | `chiefOfficerId` | `chief_officer_id` |
| `capitalCityId` | `capitalPlanetId` | `capital_planet_id` |

### 6. Table Renames

| Current Table | New Table |
|---------------|-----------|
| `general` | `officer` |
| `general_turn` | `officer_turn` |
| `city` | `planet` |
| `nation` | `faction` |
| `nation_turn` | `faction_turn` |
| `nation_flag` | `faction_flag` |
| `troop` | `fleet` |
| `emperor` | `sovereign` |
| `world_state` | `session_state` |
| `old_general` | `old_officer` |
| `old_nation` | `old_faction` |

### 7. FK Column Renames (across multiple tables)

| Current Column | New Column | Tables Affected |
|----------------|------------|-----------------|
| `nation_id` | `faction_id` | officer, planet, fleet, faction_turn, faction_flag, diplomacy, rank_data |
| `city_id` | `planet_id` | officer |
| `troop_id` | `fleet_id` | officer |
| `general_id` | `officer_id` | officer_turn |
| `world_id` | `session_id` | all game entities |
| `leader_general_id` | `leader_officer_id` | fleet |
| `chief_general_id` | `chief_officer_id` | faction |
| `capital_city_id` | `capital_planet_id` | faction |
| `src_nation_id` | `src_faction_id` | diplomacy |
| `dest_nation_id` | `dest_faction_id` | diplomacy |

### 8. Rank System Addition

Current `officerLevel` is a Short (0-12 in OpenSamguk). LOGH needs:
- 11 tiers: 0 (Sub-Lieutenant/소위) to 10 (Reichsmarschall/원수)
- Faction-specific title resolution (Empire vs Alliance titles)
- No schema change needed for the tier value itself (already stored as `officer_level`)
- Add a `rank_tier` alias or use existing `officer_level` mapped to LOGH titles
- Title resolution should be a utility function, not a DB field

## Impact Analysis

### Backend Files by Category

| Category | Approx Files | Risk |
|----------|-------------|------|
| Entity classes | 11 files | HIGH - core data model |
| Repository interfaces | 10 files | MEDIUM - method signatures reference entity names |
| DTO/Response classes | 8 files | HIGH - API contract changes |
| Service layer | 25+ files | HIGH - business logic references field names |
| Command handlers | 60+ files | HIGH - every command reads/writes General/City/Nation |
| Engine (turn/economy/AI) | 30+ files | HIGH - stat calculations |
| CQRS snapshot layer | 8 files | CRITICAL - must mirror entity changes exactly |
| War/Battle engine | 15+ files | HIGH - uses strength/intel directly |
| Controllers | 10 files | MEDIUM - endpoint routing |
| Test files | 80+ files | HIGH - test fixtures use old field names |

### Frontend Files

| Category | Approx Files | Risk |
|----------|-------------|------|
| Type definitions (types/index.ts) | 1 file | HIGH - General/City/Nation interfaces |
| Game pages | 20+ files | MEDIUM - render stat names |
| API client (gameApi.ts) | 1 file | MEDIUM - endpoint URLs |
| Components | 10+ files | MEDIUM - display field names |
| Tutorial/mock data | 3+ files | LOW - static test data |

### Enum Type: nation_aux_key

The PostgreSQL enum `nation_aux_key` in V1 contains Korean strings for nation flags. This needs to become `faction_aux_key` with potentially updated values.

### Index Renames

All indexes from V1 use old names (e.g., `idx_general_world_id`). These should be renamed in the migration:
- `idx_general_world_id` -> `idx_officer_session_id`
- `idx_general_nation_id` -> `idx_officer_faction_id`
- `idx_general_city_id` -> `idx_officer_planet_id`
- `idx_general_user_id` -> `idx_officer_user_id`
- `idx_city_world_id` -> `idx_planet_session_id`
- `idx_city_nation_id` -> `idx_planet_faction_id`
- `idx_nation_world_id` -> `idx_faction_session_id`
- `idx_diplomacy_world_id` -> `idx_diplomacy_session_id`
- `idx_message_world_id` -> `idx_message_session_id`
- `idx_message_dest_id` -> `idx_message_dest_id` (unchanged)
- `idx_event_world_id` -> `idx_event_session_id`

## Recommended Approach

### Migration Strategy: Single Atomic Flyway V28

Use `ALTER TABLE ... RENAME TO` and `ALTER TABLE ... RENAME COLUMN` in a single V28 migration. PostgreSQL handles renames as metadata-only operations (no data rewrite), making this fast even on large tables.

**Order within V28:**
1. Rename tables first (general->officer, city->planet, nation->faction, etc.)
2. Rename FK columns (nation_id->faction_id, city_id->planet_id, etc.)
3. Rename stat columns (strength->command, intel->intelligence, charm->administration)
4. Rename resource columns (gold->funds, rice->supplies, etc.)
5. Add new stat columns with defaults (mobility, attack, defense + their _exp columns)
6. Rename indexes
7. Rename enum type (nation_aux_key -> faction_aux_key)

**Why single migration:** All entity classes must be updated simultaneously because they cross-reference each other (Officer.factionId, Planet.factionId, Fleet.leaderOfficerId). A partial migration would leave Hibernate unable to map any entity.

### Code Change Order

1. **Wave 0 -- Migration + Core Entities:**
   - Write V28 migration SQL
   - Rename entity classes: General->Officer, City->Planet, Nation->Faction, Troop->Fleet, Emperor->Sovereign, WorldState->SessionState
   - Update @Table annotations and @Column names
   - Add new stat fields (mobility, attack, defense + exp)

2. **Wave 1 -- Supporting Entities + Repositories:**
   - Rename GeneralTurn->OfficerTurn, NationTurn->FactionTurn, NationFlag->FactionFlag
   - Rename OldGeneral->OldOfficer, OldNation->OldFaction
   - Update all Repository interfaces
   - Rename GeneralRecord->OfficerRecord, GeneralAccessLog->OfficerAccessLog

3. **Wave 2 -- CQRS Snapshot Layer:**
   - Update InMemoryWorldState.kt (GeneralSnapshot->OfficerSnapshot, CitySnapshot->PlanetSnapshot, etc.)
   - Update SnapshotEntityMapper.kt
   - Update WorldStateLoader.kt, WorldStatePersister.kt
   - Update all port interfaces and implementations

4. **Wave 3 -- DTOs + Service Layer:**
   - Update ResponseDtos.kt (GeneralResponse->OfficerResponse, etc.)
   - Update all other DTO files
   - Update service layer (GeneralService->OfficerService, CityService->PlanetService, etc.)
   - Add OfficerStat enum with PCP/MCP categorization

5. **Wave 4 -- Commands + Engine:**
   - Update all command handlers (60+ files)
   - Update war engine (WarUnitGeneral->WarUnitOfficer, battle formulas)
   - Update AI, economy, turn services
   - Add rank title resolution utility

6. **Wave 5 -- Controllers + Frontend:**
   - Update controller classes
   - Update frontend types/index.ts
   - Update frontend pages and components
   - Update API client

### Dex Fields Strategy

The 5 `dex1`-`dex5` fields in General map to the 5-stat system as "aptitude" values. With 8 stats, we need `dex1`-`dex8`. However, this is a game mechanic change that may deserve its own phase. For now:
- Keep `dex1`-`dex5` as-is
- Add `dex6`, `dex7`, `dex8` for the 3 new stats
- Document that dex mapping order needs to be defined (which dex maps to which stat)

## Common Pitfalls

### Pitfall 1: Hibernate Entity Scan Mismatch
**What goes wrong:** After renaming classes, Spring Boot's entity scan may not find them if package structure changes.
**How to avoid:** Keep all renamed entities in the same `com.openlogh.entity` package. Only rename the class, not the package.

### Pitfall 2: JPQL/HQL Queries Use Entity Names
**What goes wrong:** `@Query` annotations reference entity class names (e.g., `from General g`). These break silently at runtime.
**How to avoid:** Search for all `@Query` annotations and update entity references. The `GeneralRepository` has a JPQL query using `from General g`.

### Pitfall 3: CQRS Layer Desync
**What goes wrong:** Snapshot data classes get out of sync with entities, causing data loss on persist.
**How to avoid:** Update snapshot classes and mapper in the same commit. Run the full test suite (which exercises the turn engine snapshot round-trip).

### Pitfall 4: Frontend API Contract Break
**What goes wrong:** Frontend TypeScript types expect old field names (strength, intel, charm) but API now returns new names.
**How to avoid:** Update frontend types simultaneously with backend DTOs. Consider temporary backward-compat aliases in JSON serialization if phased rollout needed.

### Pitfall 5: WarUnit Base Class Has Own Stat Fields
**What goes wrong:** `WarUnit` (parent class) has `strength`, `intel`, `leadership` as its own fields separate from General. Renaming General.strength to General.command does not automatically rename WarUnit.strength.
**How to avoid:** Rename WarUnit stat fields too (strength->command, intel->intelligence). This affects 20+ battle test files.

### Pitfall 6: nation_aux_key Enum Rename
**What goes wrong:** PostgreSQL enum types cannot be renamed with simple ALTER TYPE RENAME; the enum values contain Korean text that may also need updating.
**How to avoid:** Use `ALTER TYPE nation_aux_key RENAME TO faction_aux_key;` (supported in PostgreSQL 10+). Leave enum values unchanged for now -- they are game mechanic strings, not entity names.

### Pitfall 7: Gateway Module Has Separate WorldState
**What goes wrong:** `gateway-app` has its own `WorldState.kt` entity and `WorldStateRepository.kt` that map to the same `world_state` table. If only game-app entities are renamed, gateway breaks.
**How to avoid:** Rename BOTH gateway-app and game-app WorldState entities to SessionState simultaneously.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Spring Boot Test |
| Config file | `backend/game-app/build.gradle.kts` (JUnit platform) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ENT-01 | Entity classes renamed correctly | integration | `./gradlew :game-app:test --tests "com.openlogh.engine.GameplayIntegrationTest"` | Existing |
| ENT-02 | 8-stat system fields present | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.StatChangeServiceTest"` | Existing (needs update) |
| ENT-03 | Flyway migration runs cleanly | integration | `./gradlew :game-app:test --tests "com.openlogh.service.WorldServiceTest"` | Existing |
| ENT-04 | CQRS snapshot round-trip works | integration | `./gradlew :game-app:test --tests "com.openlogh.engine.InMemoryTurnHarnessIntegrationTest"` | Existing |
| ENT-05 | Battle engine uses new stat names | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.war.*"` | Existing (needs update) |
| ENT-06 | Frontend types compile | unit | `cd frontend && pnpm build` | N/A (build check) |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test` (quick)
- **Per wave merge:** `cd backend && ./gradlew test` (full backend)
- **Phase gate:** Full backend test suite green + frontend build clean

### Wave 0 Gaps
- None -- existing test infrastructure covers all requirements. Tests themselves need field name updates as part of the work.

## Open Questions

1. **Dex field mapping to 8 stats**
   - What we know: Currently dex1-dex5 map to the 5 stats as aptitude values
   - What's unclear: Which dex index maps to which new stat? Are dex6-dex8 needed immediately?
   - Recommendation: Add dex6-dex8 columns now, defer mapping logic to a later phase

2. **nation_aux_key enum values**
   - What we know: Enum values are in Korean and reference game mechanics (e.g., 'can_국기변경')
   - What's unclear: Should these values also be updated to LOGH terminology?
   - Recommendation: Rename the type to `faction_aux_key` but keep values unchanged. Update values in a later phase when game mechanics are LOGH-ified.

3. **GenNum field semantics**
   - What we know: `gennum` on Nation tracks count of generals in that nation
   - What's unclear: Is this auto-calculated or manually maintained?
   - Recommendation: Rename to `officerCount` regardless -- it's a field name change, not a logic change.

4. **Officer level range**
   - What we know: OpenSamguk uses 0-12, LOGH defines 0-10 (11 tiers)
   - What's unclear: Does any existing logic depend on levels > 10?
   - Recommendation: Keep the field as Short, update any hardcoded level range constants in a later phase.

5. **Frontend deployment coordination**
   - What we know: API field names change breaks the frontend
   - What's unclear: Is this deployed with downtime or rolling update?
   - Recommendation: Deploy backend + frontend together. This is a breaking API change by design.

## Sources

### Primary (HIGH confidence)
- Direct code reading of all entity files in `backend/game-app/src/main/kotlin/com/openlogh/entity/`
- Direct code reading of V1 through V27 migration files
- CLAUDE.md Domain Mapping tables (authoritative for field name mappings)

### Secondary (MEDIUM confidence)
- Grep-based impact analysis across 250+ backend files and 50+ frontend files
- PostgreSQL documentation for ALTER TABLE RENAME (metadata-only operation confirmed)

## Metadata

**Confidence breakdown:**
- Entity current state: HIGH - read every entity file directly
- Required renames: HIGH - defined in CLAUDE.md domain mapping tables
- Impact scope: HIGH - systematic grep across entire codebase
- Migration strategy: HIGH - PostgreSQL RENAME is well-documented, non-destructive
- Test coverage: MEDIUM - existing tests cover key paths but all need field name updates

**Research date:** 2026-04-05
**Valid until:** 2026-05-05 (stable -- entity model unlikely to change externally)
