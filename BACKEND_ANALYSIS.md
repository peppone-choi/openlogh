# OpenLOGH Backend - Comprehensive Code Analysis

## Directory Structure

```
/Users/apple/Desktop/openlogh/backend/game-app/src/main/kotlin/com/openlogh/
├── entity/                 # JPA Entity models (Officer, Fleet, Planet, etc.)
├── service/                # Spring Services (25 service files)
├── repository/             # Spring Data JPA Repositories
├── controller/             # REST Controllers
├── engine/                 # Game engine logic
│   ├── war/               # Battle/warfare system
│   ├── ai/                # AI logic
│   ├── modifier/          # Stat modifiers
│   ├── trigger/           # Event triggers
│   └── turn/              # Turn processing
├── model/                 # Data models (ShipClass, ArmType, etc.)
├── command/               # Command execution
├── dto/                   # Data Transfer Objects
├── websocket/             # WebSocket support
├── util/                  # Utilities
├── config/                # Configuration
└── bootstrap/             # Application startup
```

---

## Core Entity Models

### 1. Officer Entity (represents a player character/general)

**File**: `entity/Officer.kt`
**Key Fields**:

- `id`: Unique identifier
- `sessionId`: World/game session ID
- `userId`: User account ID (nullable)
- `name`: Officer name
- `factionId`: Which faction they belong to
- `planetId`: Home planet
- `fleetId`: Fleet they command
- `locationState`: "planet" or other locations

**Stats** (8-stat system):

- `leadership`: 통솔 (troop morale, max fleet size)
- `command`: 지휘 (command ability)
- `intelligence`: 정보 (intel/espionage/scouting)
- `politics`: 정치 (citizen support)
- `administration`: 운영 (planet governance)
- `mobility`: 기동 (fleet movement/tactics)
- `attack`: 공격 (attack command)
- `defense`: 방어 (defense command)

**Combat Stats**:

- `ships`: Current fleet size (crew equivalent)
- `shipClass`: Short code (0-5 mapping to ArmType)
- `training`: Training level (Short)
- `morale`: Morale (Short)
- `injury`: Injury level (Short)
- `supplies`: Resources/rice

**Special Abilities**:

- `specialCode`: Primary special ability code (e.g., "필살")
- `special2Code`: Secondary special ability code
- `specAge`, `spec2Age`: Ages when abilities unlock

**Resources**:

- `funds`: Personal gold/money
- `supplies`: Military supplies

**Command Points**:

- `commandPoints`: Action points for giving orders
- `commandEndTime`: When current command ends

**Meta Data**:

- `meta`: JSON JSONB field for flexible data
- `penalty`: Penalties map
- `lastTurn`: Last turn actions

---

### 2. Fleet Entity (military unit)

**File**: `entity/Fleet.kt`
**Key Fields**:

- `id`: Fleet ID
- `sessionId`: World ID
- `leaderGeneralId`: Commander officer ID
- `factionId`: Faction ownership
- `parentFleetId`: Hierarchical parent (can nest fleets)
- `fleetType`: "fleet", "division", "patrol", "transport", "ground", "garrison"
- `name`: Fleet name
- `planetId`: Current location planet
- `gridX`, `gridY`: Tactical battle grid position (nullable)

**Ship Composition** (combat units):

- `battleships`: 전함 count
- `cruisers`: 순양함 count
- `destroyers`: 구축함 count
- `carriers`: 항공모함 count
- `assaultShips`: 양륙함 (ground assault)
- `groundTroops`: 육전대 (ground forces)
- `transports`: 수송함 (supply/logistics)
- `hospitalShips`: 병원선 (medical)

**Fleet State**:

- `morale`: Fleet morale (Short)
- `training`: Fleet training (Short)
- `supplies`: Fleet supply level (Int)
- `formation`: Current formation ("spindle", etc.)
- `fleetState`: State code (Short)

**Energy Allocation** (tactical battle channels, sum = 100):

- `energyBeam`: 빔 에너지
- `energyGun`: 포탄 에너지
- `energyShield`: 방어막 에너지
- `energyEngine`: 엔진 에너지
- `energySensor`: 센서 에너지
- `energyWarp`: 워프 에너지 (retreat/jump)

**Methods**:

- `totalCombatShips()`: Excluding flagship
- `totalShips()`: Including all units
- `combatPower()`: Weighted combat rating

---

### 3. Planet Entity (territory/system)

**File**: `entity/Planet.kt`
**Key Fields**:

- `id`: Planet ID
- `sessionId`: World ID
- `name`: Planet name
- `mapPlanetId`: Map reference ID
- `level`: Planet tier (1-3)
- `factionId`: Controlling faction
- `supplyState`: Supply status
- `frontState`: Front/border status

**Population & Economy**:

- `population`, `populationMax`: Current/max population
- `production`, `productionMax`: Industry (ship/supply production)
- `commerce`, `commerceMax`: Trade/economy
- `security`, `securityMax`: Law and order
- `approval`: Citizen approval (Float)
- `tradeRoute`: Trade route value

**Defense**:

- `orbitalDefense`, `orbitalDefenseMax`: Orbital defense level
- `fortress`, `fortressMax`: Ground fortress level
- `garrisonSet`: Garrison strength

**State**:

- `state`: Current state code
- `region`: Region ID
- `term`: Occupation term
- `fiefOfficerId`: Officer managing the fief

**Flex Data**:

- `conflict`: JSON map for conflict/battle data
- `meta`: General metadata

---

## Battle System (engine/war/)

### 1. BattleEngine (`BattleEngine.kt`)

**Purpose**: Turn-based combat resolution between officers and cities

**Key Function**: `resolveBattle(attacker, defenders, city, rng)`

- Attacker (WarUnitGeneral) vs Multiple Defenders (List<WarUnit>) + City Defense
- Returns `BattleResult` with winner/damage dealt

**Battle Flow**:

1. **Initialization Phase**: Collect special triggers from both sides
2. **Combat Loop** (up to 200 phases):
    - Critical hit check
    - Dodge check
    - Magic/strategy activation
    - Damage calculation (complex formula with:
        - Ship class advantage coefficients
        - DEX (skill level) differences
        - Training & morale multipliers
        - Critical/dodge/magic effects)
    - Damage application
    - Morale/training adjustments
3. **City Defense** (if attacker wins vs all defenders):
    - Similar combat loop against city defenses
4. **Results**: Damage dealt, battle won, city occupied

**Key Constants**:

- `ARM_PER_PHASE = 500.0`: Armor damage per phase base

**Special Trigger System**:

- Officers have `specialCode` and `special2Code`
- Triggers are registered in `BattleTriggerRegistry`
- Triggers modify battle context at various phases

---

### 2. Battle Context & Triggers (`BattleTrigger.kt`)

**BattleTriggerContext**: Mutable state passed through battle phases

- `battleLogs`: String log of actions
- `criticalChanceBonus`, `dodgeChanceBonus`: Probability modifiers
- `magicActivated`, `magicChanceBonus`, `magicDamageMultiplier`: Strategy effects
- `attackMultiplier`, `defenceMultiplier`: Damage scaling
- `snipeActivated`, `counterDamageRatio`: Special effects
- `moraleBoost`: Morale change
- `injuryImmune`: Immunity to wounds

**BattleTrigger** (abstract base):
Lifecycle hooks called during battle phases:

- `onBattleInit`: Initial setup
- `onPreCritical`, `onPostCritical`: Critical hit modifiers
- `onPreDodge`: Dodge chance setup
- `onPreMagic`, `onPostMagic`, `onMagicFail`: Strategy/magic system
- `onDamageCalc`: Final damage calculation
- `onPostDamage`: After damage applied
- `onInjuryCheck`: Injury application

**49 Built-in Triggers** (Korean special abilities):

- **Attack**: 필살(instant death), 격노(rage), 돌격(charge), 저격(snipe)
- **Defense**: 방어(defense), 철벽(ironwall), 견고(solid)
- **Strategy**: 신산(tactics), 반계(counter), 화공(fire), 귀모(necromancy)
- **Utility**: 회피(dodge), 기습(ambush), 매복(ambush), 반격(counterattack)
- **Ship-type**: 8 "che\_" triggers (선제사격=preemptive fire, 방어력증가=defense+, etc.)
- **Additional**: 심공, 결사, 용병, 연사, 사기진작, etc.

---

### 3. WarUnit Models (`WarUnit.kt`)

**WarUnit** (abstract base class):

```
hp, maxHp, supplies, training, morale, injury
getBaseAttack(), getBaseDefence()
continueWar(): ContinueWarResult
calcBattleOrder(): Double
applyResults()
```

**WarUnitGeneral** (Officer commanding fleet):

- Wraps `Officer` entity + faction tech level
- HP = ship count
- Stats derived from officer stats + ship class
- `getBaseAttack()`: Intelligence or Command stat (ship-class dependent)
- `getBaseDefence()`: Based on crew factor (hp / 233.33 + 70)
- `continueWar()`: Checks hp > 0, morale > 20, supplies adequate
- `consumeRice()`: Supply drain based on damage dealt & ship class
- `getDexForArmType()`: Skill for specific ship class (dex1-5)

**WarUnitCity** (Planet defending):

- Wraps `Planet` entity
- HP = orbitalDefense \* 10
- Attack/Defense based on orbital_defense + fortress
- Training = year-based with fortress bonus
- Morale = training-based

---

### 4. War Formula (`WarFormula.kt`)

**Tech Scaling**:

- `getTechLevel(tech)`: clamped to 0-12
- `getTechAbil(tech)`: tech level \* 25 (attack bonus)
- `getTechCost(tech)`: 1.0 + level \* 0.15 (supply cost multiplier)

**DEX (Skill Level) System**:

- 26 thresholds: [350, 1375, 3500, 7125, ..., 1275975]
- `getDexLevel(dex)`: Count how many thresholds exceeded (0-26)
- `getDexLog(dex1, dex2)`: Advantage multiplier = 1.0 + (level_diff / 55.0)

**Damage Formula** (in `runPhase`):

```
rawAtk = baseAttack * ctx.attackMultiplier * attackCoef * defenceCoef *
         dexFactor * critMult * (training/100) * (morale/100)
finalDmg = max(1, rawAtk - defBase * 0.3)
(if dodged: finalDmg = 0)
(if magic: finalDmg += baseAtk * 0.3 * magicDamageMultiplier)
```

---

### 5. BattleService (`BattleService.kt`)

**Purpose**: Spring service managing battle orchestration

**Key Functions**:

- `processBattles(world)`: Main battle loop
    - Finds armed officers on planets
    - Groups by planet
    - Runs auto-battles for opposing factions
    - Handles occupation & aftermath
- `executeBattle(attacker, city, world)`: Single battle execution
    - Creates WarUnit instances
    - Calls BattleEngine.resolveBattle()
    - Handles city occupation consequences
    - Updates officer data
- `handleCityOccupation()`: Post-victory logic
    - Transfer planet to attacker faction
    - Reduce city stats (approval, production)
    - Check capital/nation destruction
- `handleCapitalRelocation()`: Move capital to largest remaining city
- `handleNationDestruction()`: Eliminate faction
    - Release all officers (factionId = 0)
    - Drop resources
    - Clear diplomacy relations
    - Archive in OldFaction table

**Dependencies**: Repositories, EventService, DiplomacyService, ModifierService

---

### 6. BattleResult Data Class

```kotlin
data class BattleResult(
    val attackerWon: Boolean,
    val cityOccupied: Boolean,
    val attackerDamageDealt: Int,
    val defenderDamageDealt: Int,
)
```

---

### 7. War Aftermath (`WarAftermath.kt`)

**WarBattleOutcome**: Detailed battle report

- `attacker`, `defenders`: Officer records
- `logs`: Battle log strings
- `conquered`: Whether city taken
- `reports`: Detailed unit casualty reports

**WarAftermathInput**: Post-battle data

- Battle outcome + full world state (all factions/cities/officers)
- Config + time context

**WarAftermathOutcome**:

- Diplomacy deltas (e.g., -10 to loser)
- Conquest result (nation collapsed?)

**resolveWarAftermath()**: Update tech, handle conquest consequences

---

## Model System (model/)

### ShipClass Enum (`ShipClass.kt`)

**ArmType** (6 types):

1. `CASTLE` (0): City defense
2. `FOOTMAN` (1): 보병 - basic infantry
3. `ARCHER` (2): 궁병 - ranged
4. `CAVALRY` (3): 기병 - cavalry
5. `WIZARD` (4): 귀병 - mystical/magic
6. `SIEGE` (5): 차병 - siege/artillery

**ShipClass Examples** (30+ variants):

**Footman Class** (보병 variants):

- `FOOTMAN(1100)`: Basic 100/150 stats
- `CHEONGJU(1101)`: 청주병 - Regional variant
- `MARINE(1102)`: 수병 - Naval variant
- `RATTAN(1105)`: 등갑병 - Armor variant
- etc.

**Each ShipClass has**:

- `code`: Unique integer ID
- `armType`: Which type
- `attack`, `defence`, `speed`, `avoid`: Stats
- `magicCoef`: Magic effectiveness
- `riceCost`: Supply cost per phase
- `attackCoef[opponent]`: Advantage vs other types (map)
- `defenceCoef[opponent]`: Defense vs types
- `reqTech`, `reqCityNames`, `reqRegionNames`: Tech requirements

**Methods**:

- `getAttackCoef(opponent)`: Lookup advantage
- `getDefenceCoef(opponent)`: Lookup defense
- `isValidForNation()`: Check if nation can build this unit
- `pickScore(tech)`: Rating for AI unit selection

---

## Service Architecture (service/)

25 service files providing game logic:

**Core Services**:

1. `OfficerService`: Officer management
2. `FactionService`: Faction operations
3. `PlanetService`: Planet management
4. `FleetService`: Fleet composition
5. `WorldService`: World/session management

**Battle & Combat**: 6. `BattleService` ← Uses BattleEngine 7. `GameEventService`: Event broadcasting

**Economy & Maintenance**: 8. `EconomyService`: Resource generation 9. `OfficerMaintenanceService`: Officer aging, injuries

**Turn System**: 10. `TurnService`: Turn processing 11. `TurnDaemon`: Background turn worker

**Diplomacy & Politics**: 12. `DiplomacyService`: Alliance/war declarations 13. `OfficerRankService`: Rank management

**Content**: 14. `PublicCachedMapService`: Map caching 15. `GameConstService`: Game constants 16. `ScenarioService`: Scenario selection

**Account & Admin**: 17. `AuthService`: Authentication 18. `AccountService`: Account management 19. `AdminService`: Admin functions

**Other**: 20. `MessageService`: Inter-officer messages 21. `HistoryService`: Game history logging 22. `RecordService`: Record tracking 23. `RankingService`: Rankings 24. `InheritanceService`: Legacy system 25. `SelectPoolService`: Recruitment pool

---

## Naming Conventions

### Kotlin Naming:

- **Classes**: PascalCase (Officer, Fleet, BattleEngine)
- **Functions**: camelCase (resolveBattle, executeCommand)
- **Constants**: UPPER_SNAKE_CASE (ARM_PER_PHASE)
- **Private properties**: `_name` or just `private val name`

### Korean Game Terms (transliteration):

- 통솔 = leadership
- 지휘 = command
- 정보 = intelligence
- 정치 = politics
- 운영 = administration
- 기동 = mobility
- 공격 = attack
- 방어 = defense
- 함대 = fleet
- 행성 = planet
- 진영 = faction
- 제독 = officer/admiral
- 함종 = ship class
- 전투 = battle/combat

### Package Structure Pattern:

```
com.openlogh.{module}
├── entity/        (JPA models)
├── repository/    (Data access)
├── service/       (Business logic)
├── controller/    (HTTP/REST endpoints)
├── dto/          (Data transfer objects)
└── model/        (Value objects, enums)
```

---

## Import Patterns

**Framework**:

```kotlin
import jakarta.persistence.*
import org.springframework.stereotype.Service
import org.springframework.data.jpa.repository.JpaRepository
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
```

**Standard Library**:

```kotlin
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import java.time.OffsetDateTime
```

**Custom Imports** (within package):

```kotlin
import com.openlogh.entity.Officer
import com.openlogh.engine.war.BattleEngine
import com.openlogh.model.ShipClass
import com.openlogh.repository.OfficerRepository
```

---

## Coding Style Observations

1. **Data Classes**: Used for DTOs and immutable value objects

    ```kotlin
    data class BattleResult(val a: Boolean, val b: Boolean, ...)
    ```

2. **Enum Extensions**:
    - Enums with companion objects for lookups
    - `associateBy` for reverse lookup maps

3. **Abstract Classes**:
    - WarUnit is abstract with open functions
    - Concrete implementations (WarUnitGeneral, WarUnitCity)

4. **Mutable Collections**:
    - JSONB fields use `MutableMap<String, Any>`
    - Battle logs use `MutableList<String>`

5. **Compat Aliases**:
    - Officer/Planet use constructor params for old field names
    - Mapping in `init` block (worldId → sessionId, etc.)

6. **Magic Numbers**: Thresholds, multipliers in functions
    - DEX_THRESHOLDS array (26 values)
    - Damage formula constants (0.3, 0.8, 1.5x multipliers)

7. **Extension Functions**:
    ```kotlin
    fun resolveShipClass(code: Short): ShipClass { ... }
    var officer.rice: Int
      get() = supplies
      set(value) { supplies = value }
    ```

---

## Key Files Reference

| File             | Purpose                  | Size      |
| ---------------- | ------------------------ | --------- |
| Officer.kt       | Player character entity  | 300 lines |
| Fleet.kt         | Military unit entity     | 140 lines |
| Planet.kt        | Territory entity         | 147 lines |
| BattleEngine.kt  | Combat resolution        | 238 lines |
| BattleTrigger.kt | Special abilities        | 351 lines |
| WarUnit.kt       | Combat unit abstractions | 179 lines |
| ShipClass.kt     | Unit types & stats       | 529 lines |
| BattleService.kt | Battle orchestration     | 268 lines |
| WarFormula.kt    | Damage calculations      | 29 lines  |
| WarAftermath.kt  | Post-battle handling     | 131 lines |

---

## Database Schema Hints

**Core Tables**:

- `officer`: Officers/generals
- `fleet`: Fleets
- `planet`: Planets/territories
- `faction`: Factions/nations
- `session_state`: World state

**JSON Columns**:

- `officer.meta`: Flexible officer data
- `officer.penalty`: Penalties
- `officer.lastTurn`: Last turn actions
- `fleet.meta`: Fleet metadata
- `planet.meta`: Planet metadata
- `planet.conflict`: Battle data

**Foreign Keys**:

- officer.faction_id → faction.id
- officer.planet_id → planet.id
- officer.fleet_id → fleet.id
- fleet.leader_officer_id → officer.id
- planet.faction_id → faction.id
