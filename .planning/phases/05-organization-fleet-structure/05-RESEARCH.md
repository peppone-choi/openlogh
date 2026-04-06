# Phase 5 Research: Organization & Fleet Structure

## Discovery Level: 0 (Skip)

All work follows established codebase patterns. No new external dependencies needed.

## Current State Analysis

### Fleet Entity (Current)
The current `Fleet` entity is a minimal OpenSamguk leftover with only:
- `id`, `sessionId`, `leaderOfficerId`, `factionId`, `name`, `meta` (jsonb), `createdAt`

It has no concept of:
- Unit type (fleet vs patrol vs transport vs ground vs garrison vs solo)
- Crew slots (only tracks a single `leaderOfficerId`)
- Ship composition (unit count, ship count per class)
- Population linkage for formation caps

### Officer Entity (Current)
- Has `fleetId` field linking officer to a fleet (0 = no fleet)
- Has `ships`, `shipClass`, `training`, `morale` fields (legacy per-officer ship tracking from OpenSamguk)
- These per-officer ship fields will need to coexist during transition; the new unit-level composition replaces them

### Planet Entity (Current)
- Has `population` (Int) field -- used for the population-to-military linkage formula
- Population unit is likely "10,000 people" based on OpenSamguk conventions (population=100 = 1 million)

### CQRS Snapshot Layer
- `FleetSnapshot` mirrors the simple Fleet entity
- `InMemoryWorldState` has `fleets: MutableMap<Long, FleetSnapshot>`
- All snapshot types need updating when Fleet entity changes

### Frontend Types
- Legacy `Troop` interface with type alias `Fleet = Troop`
- `TroopWithMembers`, `TroopMemberInfo` exist
- Need full replacement with proper unit type system

## Design Decisions

### Unit Type Enum
Six types per gin7 manual:
1. **FLEET** - 60 units (18,000 ships), 10 crew slots
2. **PATROL** - 3 units (900 ships), 3 crew slots
3. **TRANSPORT** - 23 units (6,900 ships), 3 crew slots
4. **GROUND** - 6 units (1,800 ships), 1 crew slot
5. **GARRISON** - 0 ship units + 10 infantry units, 1 crew slot
6. **SOLO** - 1 flagship only, 0 crew slots (commander only)

### Crew Slot Roles
Fleet (10 slots):
- COMMANDER (사령관) - 1
- VICE_COMMANDER (부사령관) - 1
- CHIEF_OF_STAFF (참모장) - 1
- STAFF_OFFICER (참모) - 6
- ADJUTANT (부관) - 1

Patrol/Transport (3 slots):
- COMMANDER - 1
- VICE_COMMANDER - 1
- ADJUTANT - 1

Ground/Garrison (1 slot):
- COMMANDER - 1

Solo (0 extra slots):
- Commander is implicit (the officer themselves)

### DB Schema Changes (Flyway V34)
Add to `fleet` table:
- `unit_type` VARCHAR(20) NOT NULL DEFAULT 'FLEET'
- `max_units` INT NOT NULL DEFAULT 60
- `current_units` INT NOT NULL DEFAULT 0
- `max_crew` INT NOT NULL DEFAULT 10
- `planet_id` BIGINT (nullable, for garrison home planet)

New `unit_crew` join table:
- `id` BIGSERIAL PK
- `session_id` BIGINT NOT NULL
- `fleet_id` BIGINT NOT NULL FK
- `officer_id` BIGINT NOT NULL FK
- `slot_role` VARCHAR(30) NOT NULL
- `assigned_at` TIMESTAMPTZ NOT NULL DEFAULT NOW()
- UNIQUE(fleet_id, slot_role, officer_id)

### Population Linkage Formula
Per gin7 manual:
- 1 billion population (인구 10억) = 1 fleet or 1 transport fleet
- 1 billion population = 6 patrols or 6 ground forces
- Garrison: 1 per planet (not population-linked)
- Solo: unlimited (just an officer with their flagship)

Formula per faction (sum of all faction planets' population):
- `maxFleets = totalPopulation / 1_000_000_000` (or population units equivalent)
- `maxTransports = totalPopulation / 1_000_000_000`  
- `maxPatrols = (totalPopulation / 1_000_000_000) * 6`
- `maxGround = (totalPopulation / 1_000_000_000) * 6`

Note: Need to confirm population unit scale. If `population` field stores in units of 10,000, then 1 billion = population value of 100,000.

### Ship Composition per Unit Type
All combat ship units = 300 ships per unit:
- Fleet: 60 units x 300 = 18,000 ships
- Patrol: 3 units x 300 = 900 ships
- Transport: 20 transport units + 3 escort units = 6,900 ships
- Ground: 3 landing ships + 3 infantry units = varies
- Garrison: 10 infantry units (no ships, ground only)
- Solo: 1 flagship

## Implementation Approach

### Plan 01: DB Migration + Domain Model (Wave 1)
- Flyway V34 migration (alter fleet table + create unit_crew table)
- UnitType enum with composition constants
- CrewSlotRole enum
- Update Fleet entity with new fields
- UnitCrew entity for crew assignments
- Update FleetSnapshot and InMemoryWorldState

### Plan 02: Services + Population Linkage (Wave 2, depends on 01)
- FormationCapService (population-based formation limits)
- UnitCrewService (crew slot assignment/removal)
- Update FleetService with unit type awareness
- Update FleetRepository with new queries
- Update TroopController -> UnitController rename + new endpoints

### Plan 03: Frontend Unit Management UI (Wave 2, depends on 01)
- Replace legacy Troop types with proper Unit types
- Unit list component showing composition per type
- Crew roster display
- Formation cap display linked to population

### Plan 04: Integration Verification (Wave 3, depends on 02 + 03)
- Checkpoint: verify unit creation for all 6 types
- Verify crew slot limits enforced
- Verify population linkage caps
