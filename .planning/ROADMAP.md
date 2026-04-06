# Roadmap: Open LOGH (오픈 은하영웅전설)

## Overview

Open LOGH transforms the OpenSamguk (Three Kingdoms) web game engine into a faithful recreation of gin7 (Legend of the Galactic Heroes VII). The journey begins by converting the existing 5-stat entity model to gin7's 8-stat system with LOGH domain naming, then builds the real-time tick engine that replaces turn-based processing. From there, each phase delivers a complete game subsystem in dependency order: command points, position cards, organizational structure, galaxy map, rank/personnel, scenarios, strategic commands, tactical combat, faction politics, communications, victory conditions, and a purpose-built LOGH frontend. NPC AI and balancing run as the final phase once all systems are testable together. Frontend work is interleaved throughout -- each backend system phase includes its corresponding UI requirements.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Entity Model Foundation** - Convert 5-stat to 8-stat system, rename entities to LOGH domain, DB migrations V28+
- [ ] **Phase 2: Real-time Tick Engine** - Replace turn-based processing with 1-second server tick at 24x game speed
- [ ] **Phase 3: Command Point System** - Implement dual PCP/MCP pools with independent recovery and cross-use penalty
- [ ] **Phase 4: Position Card & Command Authority** - 77 position cards, command groups, authority gating, suggestion system
- [ ] **Phase 5: Organization & Fleet Structure** - 6 unit types with crew slots, population-military linkage
- [ ] **Phase 6: Galaxy Map & Planet Model** - 80 star systems, route network, territory zones, fortress systems, galaxy map UI
- [ ] **Phase 7: Rank, Merit & Personnel** - 11-tier rank ladder, merit points, promotion/demotion, appointment authority
- [ ] **Phase 8: Scenario & Character System** - 10 scenarios, custom character creation, original character selection, scenario events
- [ ] **Phase 9: Strategic Commands** - Operation planning, logistics, production, supply chain commands
- [ ] **Phase 10: Tactical Combat (RTS)** - WebSocket real-time fleet battle, energy allocation, formations, fortress guns
- [ ] **Phase 11: Faction Politics & Diplomacy** - Empire autocracy, Alliance democracy, Fezzan NPC systems, coups, elections
- [ ] **Phase 12: Communication & Session Lifecycle** - Mail, chat, victory conditions, session restart, rankings

## Phase Details

### Phase 1: Entity Model Foundation
**Goal**: All game entities use LOGH domain terminology with the gin7 8-stat system, and the database schema reflects the new model
**Depends on**: Nothing (first phase)
**Requirements**: CHAR-01, CHAR-02, CHAR-03
**Success Criteria** (what must be TRUE):
  1. Officer entity has 8 stats (leadership, command, intelligence, politics, administration, mobility, attack, defense) and the old 5-stat fields are removed
  2. Stats are grouped into PCP-relevant (politics, administration, intelligence) and MCP-relevant (leadership, command, mobility, attack, defense) at the model level
  3. Rank field supports 11 tiers (0-10) with separate Empire/Alliance title resolution
  4. All entity names in code and DB use LOGH domain terms (Officer, Planet, Faction, Fleet, SessionState) per the CLAUDE.md mapping table
  5. Flyway migrations V28+ apply cleanly on the existing schema, and the game-app boots without errors
**Plans:** 8 plans

Plans:
- [x] 01-01-PLAN.md — V28 Flyway migration (table/column/index renames + new stat columns)
- [x] 01-02-PLAN.md — OfficerStat enum (PCP/MCP grouping) + RankTitle resolver (11-tier)
- [x] 01-03-PLAN.md — Core entity renames (General->Officer, City->Planet, Nation->Faction, Troop->Fleet, Emperor->Sovereign, WorldState->SessionState)
- [x] 01-04-PLAN.md — Gateway SessionState + auxiliary entity renames (turns, flags, old records, logs)
- [x] 01-05-PLAN.md — CQRS snapshot layer update (InMemoryWorldState, SnapshotEntityMapper, Loader, Persister, Ports)
- [x] 01-06-PLAN.md — Repository + Service layer renames
- [x] 01-07-PLAN.md — Command system (93 commands), war engine, turn engine, DTOs, controllers
- [x] 01-08-PLAN.md — Frontend types, API client, stores, components, pages

### Phase 2: Real-time Tick Engine
**Goal**: The game world advances in real-time at 24x speed with 1-second server ticks instead of turn-based processing
**Depends on**: Phase 1
**Requirements**: ENG-01, ENG-02, ENG-03, ENG-04
**Success Criteria** (what must be TRUE):
  1. Server processes one tick per second, advancing game time by 24 seconds per tick
  2. 30 real-time hours equals exactly 1 game month, and the calendar advances accordingly
  3. Command points regenerate every 5 real-time minutes (7,200 game-seconds)
  4. Commands execute with real-time duration waits (not instant turn resolution) and players see countdown timers
  5. Multiple concurrent players in the same session experience consistent game time progression
**Plans:** 2/3 plans executed

Plans:
- [x] 02-01-PLAN.md — Flyway V30 migration + GameTimeConstants + SessionState entity update
- [ ] 02-02-PLAN.md — TickEngine core with TDD (tick advancement, month boundary, CP regen gating)
- [x] 02-03-PLAN.md — WebSocket tick broadcast + command duration integration

### Phase 3: Command Point System
**Goal**: Players spend PCP and MCP to execute commands, with independent pools, recovery, and cross-use at double cost
**Depends on**: Phase 2
**Requirements**: CMD-02, CMD-03
**Success Criteria** (what must be TRUE):
  1. Each officer has separate PCP (political) and MCP (military) pools that display correctly in the UI
  2. PCP and MCP recover independently every 5 real-time minutes, each to their respective maximum
  3. A player can use PCP for military commands (or MCP for political commands) at exactly 2x the normal cost
  4. Command execution correctly deducts from the appropriate pool, and insufficient points prevent execution with a clear error message
  5. CP pool sizes scale with rank (higher rank = larger pool)
**Plans**: TBD
**UI hint**: yes

### Phase 4: Position Card & Command Authority
**Goal**: Officers hold position cards that gate which commands they can execute, enabling the organizational simulation core
**Depends on**: Phase 3
**Requirements**: CMD-01, CMD-04, CMD-05
**Success Criteria** (what must be TRUE):
  1. All 77 position cards are defined with their associated command groups, and an officer's available commands change when their position card changes
  2. An officer without a required position card cannot execute restricted commands (authority gating works)
  3. Commands have real-time cooldowns (not turn-gated) that count down visibly to the player
  4. A lower-rank officer can submit a suggestion/proposal to a superior, and the superior can approve or reject it
  5. The command panel UI shows only commands available to the player's current position card
**Plans:** 2/4 plans executed

Plans:
- [x] 04-01-PLAN.md — PositionCard enum (77 cards), CommandGroup enum (7 groups), PositionCardRegistry, Flyway V32, Officer entity update
- [ ] 04-02-PLAN.md — Authority gating in CommandExecutor, real-time cooldowns (OffsetDateTime), command table filtering
- [x] 04-03-PLAN.md — Proposal entity + Flyway V33, ProposalService (submit/approve/reject), ProposalController REST endpoints
- [ ] 04-04-PLAN.md — Frontend command panel (card-filtered, grouped by command group), proposal panel UI, command store

### Phase 5: Organization & Fleet Structure
**Goal**: Military units are organized into 6 distinct types with correct crew slots and population-based formation limits
**Depends on**: Phase 4
**Requirements**: ORG-01, ORG-02, ORG-03, ORG-04, ORG-05, ORG-06
**Success Criteria** (what must be TRUE):
  1. Fleet (60 units, 10 crew), Patrol (3 units, 3 crew), Transport (23 units, 3 crew), Ground (6 units, 1 crew), Garrison (10 infantry, 1 crew), and Solo (1 flagship) unit types all function correctly
  2. Crew slot assignments work: fleet commander, vice-commander, chief of staff, 6 staff officers, and adjutant for fleets; reduced slots for other unit types
  3. Population of 1 billion enables formation of 1 fleet or transport fleet, and 6 patrols or ground forces
  4. Losing a high-population planet reduces the formation cap, and excess units cannot be newly formed until population recovers
  5. The unit management UI displays unit composition, crew roster, and ship counts per unit type
**Plans**: TBD
**UI hint**: yes

### Phase 6: Galaxy Map & Planet Model
**Goal**: Players navigate and manage 80 star systems connected by routes, with territory visualization and fortress mechanics
**Depends on**: Phase 5
**Requirements**: GAL-01, GAL-02, GAL-03, GAL-04
**Success Criteria** (what must be TRUE):
  1. All 80 star systems from docs/star_systems.json are loaded with correct positions, and route connections are navigable
  2. The galaxy map UI clearly shows Empire (blue), Alliance (green), and Fezzan (yellow) territory zones with current ownership
  3. Fleet movement follows route connections with travel time based on distance and engine speed
  4. Iserlohn and Geiersburg display as fortress systems with special defense values, garrison slots, and fortress gun capabilities
  5. Planet detail view shows all resource fields (population, production, commerce, security, approval, orbital defense, fortress, trade route)
**Plans**: TBD
**UI hint**: yes

### Phase 7: Rank, Merit & Personnel
**Goal**: Officers progress through an 11-tier rank ladder via merit points, with rank determining authority, CP pools, and appointment eligibility
**Depends on**: Phase 6
**Requirements**: CHAR-04, CHAR-07
**Success Criteria** (what must be TRUE):
  1. New players start at sub-lieutenant (rank 0) and can be promoted through all 11 tiers up to Reichsmarschall/Fleet Admiral
  2. Merit points accumulate from combat, command execution, and mission success, and are visible to the player
  3. Promotion occurs when merit threshold is met and a superior (or system for top ranks) approves; demotion occurs on sufficient demerit
  4. Rank determines maximum CP pool size, eligible position cards, and appointable subordinate positions
  5. The personnel management UI shows rank, merit progress, promotion eligibility, and chain of command
**Plans**: TBD
**UI hint**: yes

### Phase 8: Scenario & Character System
**Goal**: Players select from 10 historical scenarios and create or choose characters to begin gameplay
**Depends on**: Phase 7
**Requirements**: CHAR-05, CHAR-06, SCN-01, SCN-02, SCN-03
**Success Criteria** (what must be TRUE):
  1. All 10 scenarios (UC795.9 through UC799.4) are selectable at world creation, each with correct initial star system ownership and fleet dispositions
  2. Players can create a custom character by allocating points across the 8 stats, choosing a faction, and starting at sub-lieutenant
  3. Players can select an available original character (per scenario roster), inheriting that character's stats and starting position
  4. Scenario-specific events fire at the correct game time (coups, civil wars, special battles) matching the scenarios_detail.md specifications
  5. The scenario selection UI shows scenario description, timeline, faction balance, and available original characters
**Plans**: TBD
**UI hint**: yes

### Phase 9: Strategic Commands
**Goal**: Officers can execute the full range of strategic commands for territory management, logistics, production, and military operations
**Depends on**: Phase 8
**Requirements**: TAC-05
**Success Criteria** (what must be TRUE):
  1. Domestic commands (develop production, invest commerce, strengthen security, conscript, train) affect planet stats over time with visible progress
  2. Logistics commands move supplies and ships between planets via transport fleets along valid routes
  3. Military strategic commands (deploy fleet, recall, assemble, occupy) move units on the galaxy map with real-time travel
  4. Intelligence commands (espionage, reconnaissance, communication jamming) provide information or disrupt enemy operations
  5. The strategic command UI shows available commands filtered by position card, costs, cooldowns, and execution progress
**Plans**: TBD
**UI hint**: yes

### Phase 10: Tactical Combat (RTS)
**Goal**: Fleet engagements resolve in real-time via WebSocket with energy allocation, formations, and fortress guns
**Depends on**: Phase 9
**Requirements**: TAC-01, TAC-02, TAC-03, TAC-04
**Success Criteria** (what must be TRUE):
  1. When fleets meet on the same route or system, a real-time battle instance starts and all participants receive WebSocket battle events
  2. Commanders can allocate energy across 6 systems (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) and changes take effect within 1-2 ticks
  3. Fleet formation selection (wedge, by-class, mixed, three-column) visibly affects combat performance (attack/defense/speed modifiers)
  4. Fortress guns (Thor Hammer at Iserlohn, Geiersburg Haken) fire at fleets within range, dealing massive damage with appropriate cooldown
  5. The tactical battle UI shows fleet positions, damage in real-time, energy allocation controls, formation selector, and battle outcome resolution
**Plans**: TBD
**UI hint**: yes

### Phase 11: Faction Politics & Diplomacy
**Goal**: Each faction operates under its unique political system with coups, elections, loans, and inter-faction diplomacy
**Depends on**: Phase 10
**Requirements**: FAC-01, FAC-02, FAC-03, FAC-04
**Success Criteria** (what must be TRUE):
  1. Empire faction has autocratic governance: the sovereign appoints/dismisses officers, nobility hierarchy affects authority, and coup mechanics can trigger regime change
  2. Alliance faction has democratic governance: supreme council elections occur periodically, council votes affect policy, and political maneuvering matters
  3. Fezzan operates as NPC faction: offers loans to both factions, trades intelligence, maintains neutrality until story events trigger intervention
  4. Fezzan debt mechanics work: factions can take loans, failure to repay triggers escalating penalties up to Fezzan ending (Fezzan domination)
  5. The faction politics UI shows governance structure, current leadership, political status, and available political actions per faction type
**Plans**: TBD
**UI hint**: yes

### Phase 12: Communication, NPC AI & Session Lifecycle
**Goal**: Players communicate in-game, NPC officers act autonomously with personality, and sessions have clear victory/end conditions
**Depends on**: Phase 11
**Requirements**: NPC-01, NPC-02, NPC-03, VIC-01, VIC-02, VIC-03, VIC-04
**Success Criteria** (what must be TRUE):
  1. NPC officers make decisions based on personality traits (aggressive/cautious/political/etc.) that produce observably different behavior patterns
  2. When a player goes offline, their character continues acting via NPC AI with behavior consistent with the player's historical patterns
  3. Victory triggers correctly: capital capture ends the game, reducing enemy to 3 or fewer systems ends the game, and time limit (UC801.7.27) triggers population comparison
  4. Session end produces a 4-tier evaluation (decisive/limited/local/defeat) with rankings for all participants
  5. A completed session can be restarted with a new scenario selection, preserving player accounts but resetting game state
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> ... -> 12

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Entity Model Foundation | 0/8 | Planning complete | - |
| 2. Real-time Tick Engine | 2/3 | In Progress|  |
| 3. Command Point System | 0/TBD | Not started | - |
| 4. Position Card & Command Authority | 2/4 | In Progress|  |
| 5. Organization & Fleet Structure | 0/TBD | Not started | - |
| 6. Galaxy Map & Planet Model | 0/TBD | Not started | - |
| 7. Rank, Merit & Personnel | 0/TBD | Not started | - |
| 8. Scenario & Character System | 0/TBD | Not started | - |
| 9. Strategic Commands | 0/TBD | Not started | - |
| 10. Tactical Combat (RTS) | 0/TBD | Not started | - |
| 11. Faction Politics & Diplomacy | 0/TBD | Not started | - |
| 12. Communication, NPC AI & Session Lifecycle | 0/TBD | Not started | - |
