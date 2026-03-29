# Roadmap: Open LOGH (오픈 은하영웅전설)

## Overview

Open LOGH builds a browser-based persistent MMO where players act as military officers inside a faction hierarchy — earning rank, holding authority cards, issuing strategic commands, and commanding real-time fleet battles. The build order is inside-out: harden the existing backend subsystems first, then expand the feature surface. The dependency chain is strict — session foundation enables character/rank, which enables the CP-gated command system, which enables map and fleet operations, which enables tactical combat, which enables politics and espionage, which enables everything else. Ten phases deliver the complete gin7 organizational simulation experience.

## Phases

**Phase Numbering:**

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Session Foundation** - Working session creation, player-officer binding, and concurrency bug fixes
- [ ] **Phase 2: Character, Rank, and Organization** - Officer generation, 8-stat system, 11-rank ladder, and position card authority table
- [ ] **Phase 3: Strategic Tick and CP System** - Calibrated 24x game clock, dual PCP/MCP with correct offline recovery, and base strategic commands
- [ ] **Phase 4: Galaxy Map and Planet Management** - 80-star-system galaxy map, planet resource model, facilities, and territory capture
- [ ] **Phase 5: Fleet System and Logistics** - All fleet types, ship classes, supply chain, and fleet-level strategic commands
- [ ] **Phase 6: Tactical Combat Integration** - Wire BattleTrigger to tactical engine, frontend battle flow, and NPC AI for offline commanders
- [ ] **Phase 7: Personnel and Political Commands** - Promotion/demotion commands, appointment chain, proposal/order system, influence and friendship
- [ ] **Phase 8: Intelligence, Coup, and Political Economy** - Full espionage suite, coup state machine, political economy commands, and defection
- [ ] **Phase 9: Communication, Academy, and Faction Systems** - In-game mail, chat, academy, fief system, private funds, and misc systems
- [ ] **Phase 10: Victory Conditions, Session Lifecycle, and Scale** - Win/loss evaluation, session end flow, rankings, and scale hardening for 2,000 players

## Phase Details

### Phase 1: Session Foundation

**Goal**: Players can create and join game sessions, choose factions, and the backend is free of exploit-grade concurrency bugs
**Depends on**: Nothing (first phase)
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-06, SESS-07, SMGT-01, HARD-01, HARD-02
**Success Criteria** (what must be TRUE):

1. A player can create a new game session by selecting a scenario and choosing Empire or Alliance faction
2. A second player can join the same session, select a faction, and both players are bound to the session with separate officer slots
3. A logged-out player's character remains present in the game world and CP continues recovering while they are offline
4. Two concurrent command submissions cannot both succeed when only one CP unit is available (race condition eliminated)
5. Ending a tactical battle does not leak JVM threads (executor leak fixed)

**Plans:** 3 plans

Plans:

- [x] 01-01-PLAN.md — Fix concurrency bugs: Officer @Version optimistic locking (HARD-01) + tactical executor thread leak (HARD-02)
- [x] 01-02-PLAN.md — Session creation/join with faction ratio enforcement (SESS-01, SESS-02, SESS-03, SESS-06)
- [x] 01-03-PLAN.md — Offline CP recovery verification + re-entry rules + lobby UI enhancement (SMGT-01, SESS-07)

### Phase 2: Character, Rank, and Organization

**Goal**: Every officer has a persistent identity with 8 stats, a rank on the 11-tier ladder, and authority stored in the relational PositionCard table rather than JSONB
**Depends on**: Phase 1
**Requirements**: CHAR-01, CHAR-02, CHAR-03, CHAR-04, CHAR-05, CHAR-06, CHAR-07, CHAR-08, CHAR-09, CHAR-10, CHAR-11, CHAR-12, CHAR-13, CHAR-14, CHAR-15, RANK-01, RANK-02, RANK-03, RANK-04, RANK-05, RANK-06, RANK-07, RANK-08, RANK-09, RANK-10, RANK-11, RANK-12, RANK-13, RANK-14, ORG-01, ORG-02, ORG-03, ORG-06, ORG-08, PERS-06, HARD-03
**Success Criteria** (what must be TRUE):

1. A player can select a canonical LOGH character (fixed stats) or generate a custom officer with distributed 8-stat points
2. An officer has a visible rank on the 11-tier ladder, and rank-gated commands are blocked for officers below the required rank
3. Promoting an officer via manual promotion resets merit points to 0, revokes current position cards, and the PositionCard table reflects the change (not officer.meta JSON)
4. The organization chart for both Empire and Alliance is navigable, showing all 100+ positions and their current holders
5. An officer's home planet is set so that if their flagship is destroyed they are automatically returned there

**Plans:** 8 plans

Plans:

- [x] 02-01-PLAN.md — HARD-03: PositionCard JSONB-to-relational migration (PositionCardService facade + 6 callsite migration + V39 backfill)
- [x] 02-02-PLAN.md — Officer entity extensions + character creation backend (8-stat validation, ScenarioService 8-stat parsing, CharacterController REST API)
- [x] 02-03-PLAN.md — Rank ladder wiring to PositionCardService + personnel authority migration + stat growth mechanics (CHAR-04 age, CHAR-05 exp)
- [ ] 02-04-PLAN.md — Organization API endpoints (OrgChartController, PositionCardController, DTOs)
- [ ] 02-05-PLAN.md — Frontend character selection/creation UI (8-stat allocator, origin selector, select-pool rewrite)
- [ ] 02-06-PLAN.md — Frontend officer profile (D-10 4-section layout) + org chart with live data (D-07/D-08 faction differentiation)
- [ ] 02-07-PLAN.md — Visual verification checkpoint (character selection, officer profile, org chart)
- [x] 02-08-PLAN.md — Character lifecycle (deletion, injury/treatment, death, cross-session inheritance, covert ops stat cap)

**UI hint**: yes

### Phase 3: Strategic Tick and CP System

**Goal**: The game clock runs at correct 24x acceleration, CP recovers on a real-time 5-minute schedule independent of turn ticks, and the fundamental movement and command-deployment loop works end to end
**Depends on**: Phase 2
**Requirements**: CP-01, CP-02, CP-03, CP-04, CP-05, OPS-01, OPS-02, CMD-01, CMD-02, CMD-04, CMD-05, LCMD-03
**Success Criteria** (what must be TRUE):

1. A player's CP balance increases every 5 real-time minutes while they are offline, proportional to their politics and administration stats
2. Issuing a warp navigation command costs MCP and moves a fleet between grids after the correct operation wait time elapses
3. A player can form a fleet unit and assign it to an operation plan; the operation transitions through SUBMITTED → EXECUTING → COMPLETED
4. CP experience feeds back into the correct stat group (PCP usage raises leadership/politics/administration/intelligence; MCP usage raises command/mobility/attack/defense)
5. Executing a command during an active tactical battle freezes CP recovery for the duration of that battle
   **Plans**: TBD

### Phase 4: Galaxy Map and Planet Management

**Goal**: The 80-star-system galaxy is fully navigable with visible grid coordinates, planet resources tick correctly, and capturing a planet triggers the 6-stage post-conquest process
**Depends on**: Phase 3
**Requirements**: MAP-01, MAP-02, MAP-03, MAP-04, MAP-05, MAP-06, MAP-07, MAP-08, MAP-09, MAP-10, MAP-11, MAP-12, MAP-13, MAP-14, LOGI-06, LOGI-07, LOGI-08
**Success Criteria** (what must be TRUE):

1. A player can open the galaxy map, see all 80 star systems on a 100-light-year grid, and identify grid types (space / star system / impassable)
2. A planet's population, production, commerce, security, approval, orbital defense, and fortress values are visible and change each turn tick
3. Ship production is only possible on planets with a shipyard facility, and newly produced ships go into the planet warehouse
4. After a planet is captured, the 6-stage post-conquest process executes (ownership transfer, garrison assignment, tax reset, facility handover, population reaction, approval adjustment)
5. The Fezzan neutral zone shows a penalty warning when a fleet attempts to enter its grid
   **Plans**: TBD
   **UI hint**: yes

### Phase 5: Fleet System and Logistics

**Goal**: All five fleet types can be formed and moved, the full supply chain from planet warehouse to fleet warehouse works, and all fleet-level strategic commands are executable
**Depends on**: Phase 4
**Requirements**: FLET-01, FLET-02, FLET-03, FLET-04, FLET-05, FLET-06, FLET-07, FLET-08, FLET-09, FLET-10, FLET-11, FLET-12, FLET-13, FLET-14, FLET-15, LOGI-01, LOGI-02, LOGI-03, LOGI-04, LOGI-05, LOGI-09, OPS-03, OPS-04, OPS-05, OPS-06, OPS-07, OPS-08, OPS-09, OPS-10, OPS-11, OPS-12, OPS-13, OPS-14, CMD-03, LCMD-01, LCMD-02, LCMD-04, LCMD-05
**Success Criteria** (what must be TRUE):

1. A fleet commander can form a standard fleet (up to 60 units / 18,000 ships) and a patrol unit (3 units), each with the correct crew complement limits
2. A fleet that drops below fuel level 100 cannot execute a warp command; refueling via the supply command restores warp capability
3. Fleet morale falls when the commanding officer's leadership stat is low, and a fleet at morale 20 or below is flagged as combat-incapable
4. A logistics officer can allocate units from a planet warehouse to a fleet warehouse and then replenish a damaged fleet's ships from that stock
5. A fleet commander on a planet can remain there while their fleet operates in a different star system grid
   **Plans**: TBD

### Phase 6: Tactical Combat Integration

**Goal**: Strategic fleet encounters automatically trigger the real-time tactical battle engine, battle outcomes write back to strategic state, and offline commanders are covered by NPC AI
**Depends on**: Phase 5
**Requirements**: TAC-01, TAC-02, TAC-03, TAC-04, TAC-05, TAC-06, TAC-07, TAC-08, TAC-09, TAC-10, TAC-11, TAC-12, TAC-13, TAC-14, TAC-15, TAC-16, TAC-17, TAC-18, TAC-19, TAC-20, TAC-21, TAC-22, TAC-23, NPC-01, NPC-02, NPC-03, SMGT-02, HARD-05
**Success Criteria** (what must be TRUE):

1. When opposing fleets occupy the same grid in the strategic map, the game automatically opens a tactical battle room and both sides can navigate to /battle/{sessionCode}
2. A player in a tactical battle can issue movement, attack, formation, and retreat commands; the 2D top-down battle view renders all fleet units in real time
3. Energy allocation across all 6 channels (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) visibly affects combat performance during a live battle
4. When a battle ends, the result (ship losses, officer injuries, planet ownership change) is correctly reflected back in the strategic game state
5. An offline fleet commander's fleet is automatically controlled by NPC AI using the commander's stats as ability weights
   **Plans**: TBD
   **UI hint**: yes

### Phase 7: Personnel and Political Commands

**Goal**: The full promotion/demotion/appointment command chain works with correct PositionCard table writes, the proposal/order system has an HTTP/WebSocket surface, and influence and friendship systems are active
**Depends on**: Phase 6
**Requirements**: PCMD-01, PCMD-02, PCMD-03, PCMD-04, PCMD-05, PCMD-06, PCMD-07, PCMD-08, PCMD-09, PCMD-10, ORG-04, ORG-05, ORG-07, POL-14, POL-15, INFL-01, INFL-02, INFL-03, FRND-01, FRND-02, FRND-03, PERS-07
**Success Criteria** (what must be TRUE):

1. A personnel officer can promote an eligible officer, and the PositionCard table is atomically updated — concurrent demotions during in-flight commands are rejected via PESSIMISTIC_READ
2. A junior officer can submit a proposal to their superior; the acceptance probability reflects the current friendship level and character affinity between the two
3. A senior officer can issue a direct order to a subordinate, and the subordinate's order queue reflects it
4. An officer's influence point total is visible on their profile and changes correctly after attending a social function (banquet, hunting, conference)
5. The fief card (봉토카드) persists across promotions and demotions for Empire officers who hold one
   **Plans**: TBD
   **UI hint**: yes

### Phase 8: Intelligence, Coup, and Political Economy

**Goal**: The full espionage command suite is live, the coup state machine has explicit transitions and rollback, political economy commands work, and defection/retirement mechanics are implemented
**Depends on**: Phase 7
**Requirements**: POL-01, POL-02, POL-03, POL-04, POL-05, POL-06, POL-07, POL-08, POL-09, POL-10, POL-11, POL-12, POL-13, POL-16, INTL-01, INTL-02, INTL-03, INTL-04, INTL-05, INTL-06, INTL-07, INTL-08, INTL-09, INTL-10, INTL-11, INTL-12, INTL-13, INTL-14, INTL-15, COUP-01, COUP-02, COUP-03, COUP-04, COUP-05, COUP-06, PERS-03, PERS-04, PERS-05
**Success Criteria** (what must be TRUE):

1. An intelligence officer can issue an arrest order; the target officer (even if offline) enters an arrested state and cannot execute commands until released or executed
2. A coup leader can progress a coup attempt through PLANNING → ACTIVE → SUCCESS/FAILED/ABORTED transitions; if the attempt is arrested or the leader dies, the CoupAttempt entity transitions to ABORTED with all participant flags cleared
3. The faction treasurer can set budget allocations, tax rates, and tariff rates; planet income updates on the next turn tick to reflect the new rates
4. An officer can defect to the opposing faction; their address book is wiped, they are imprisoned at the enemy capital, and their former faction's command structure shows the vacancy
5. An infiltration agent placed on an enemy planet can execute sabotage, propaganda, and intelligence-gathering commands from that infiltrated location
   **Plans**: TBD

### Phase 9: Communication, Academy, and Faction Systems

**Goal**: In-game mail, location-scoped chat, the officer academy, the Imperial fief system, private funds, and all remaining faction utility systems are operational
**Depends on**: Phase 8
**Requirements**: COMM-01, COMM-02, COMM-03, COMM-04, COMM-05, COMM-06, ACAD-01, ACAD-02, ACAD-03, ACAD-04, FIEF-01, FIEF-02, FIEF-03, FIEF-04, FUND-01, FUND-02, MISC-01, MISC-02, MISC-03, MISC-04, MISC-05, MISC-06, PERS-01, PERS-02, PERS-08, PERS-09
**Success Criteria** (what must be TRUE):

1. An officer can send an in-game mail to another officer's personal address or duty address; the mailbox enforces the 120-message cap and undeliverable mail is rejected
2. Officers on the same grid see a location-scoped chat channel; officers on different grids cannot read each other's messages (no global faction chat)
3. An officer at the officer academy can attend a lecture to increase a stat; an officer with the instructor position can post a lecture that remains valid for 120 game-minutes
4. An Empire officer with a noble title can receive a fief planet; the fief card persists across rank changes and the planet is shown as a fief holding on the officer's profile
5. The session ranking board shows current standings by merit points, evaluation points, and fame points
   **Plans**: TBD
   **UI hint**: yes

### Phase 10: Victory Conditions, Session Lifecycle, and Scale Hardening

**Goal**: Win/loss conditions are evaluated every turn, session end triggers the full ranking and Hall of Fame flow, and the backend can sustain 2,000 concurrent players without STOMP thread exhaustion or N+1 query storms
**Depends on**: Phase 9
**Requirements**: VICT-01, VICT-02, VICT-03, VICT-04, VICT-05, SESS-04, SESS-05, SMGT-03, SMGT-04, HARD-04, OPS-15, CMD-06, CMD-07, CMD-08, LCMD-06, LCMD-07, PERS-10, PERS-11
**Success Criteria** (what must be TRUE):

1. Capturing the enemy capital star system immediately triggers a victory notification to all players and begins the session end sequence
2. When the game clock reaches the time limit, victory is awarded to the faction with greater total population; the session freezes and the ranking board becomes the active view
3. A completed session can be restarted from its initial scenario conditions; players from the previous session can register for the new run
4. An officer logged out in their home residence or a hotel cannot be killed in combat; an offline officer can still be arrested and moved through the personnel system
5. A load test with 2,000 simulated concurrent connections does not exhaust STOMP threads; the RabbitMQ relay handles fan-out without in-memory broker OOM
   **Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

| Phase                                                | Plans Complete | Status      | Completed |
| ---------------------------------------------------- | -------------- | ----------- | --------- |
| 1. Session Foundation                                | 0/3            | Planning    | -         |
| 2. Character, Rank, and Organization                 | 0/8            | Planning    | -         |
| 3. Strategic Tick and CP System                      | 0/TBD          | Not started | -         |
| 4. Galaxy Map and Planet Management                  | 0/TBD          | Not started | -         |
| 5. Fleet System and Logistics                        | 0/TBD          | Not started | -         |
| 6. Tactical Combat Integration                       | 0/TBD          | Not started | -         |
| 7. Personnel and Political Commands                  | 0/TBD          | Not started | -         |
| 8. Intelligence, Coup, and Political Economy         | 0/TBD          | Not started | -         |
| 9. Communication, Academy, and Faction Systems       | 0/TBD          | Not started | -         |
| 10. Victory Conditions, Session Lifecycle, and Scale | 0/TBD          | Not started | -         |
