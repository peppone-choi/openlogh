# Roadmap: OpenSamguk Legacy Parity

## Overview

This roadmap delivers complete legacy parity between the OpenSamguk Kotlin/Next.js implementation and the devsam/core PHP codebase. Phases are ordered by strict dependency: foundational determinism fixes first, then numeric type safety, then individual game systems (battle, modifiers, economy), then composite systems (commands, NPC AI, turn pipeline), then diplomacy/scenario data verification, and finally frontend display parity. Every phase produces observable, testable parity improvements.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Deterministic Foundation** - Replace non-deterministic RNG, add exception logging, fix turn ordering tiebreakers
- [ ] **Phase 2: Numeric Type Safety** - Audit Short overflow, float-to-int truncation, and integer division divergence
- [ ] **Phase 3: Battle Framework and Core Triggers** - Implement WarUnitTrigger framework, battle XP, and top-priority combat abilities
- [ ] **Phase 4: Battle Completion** - Implement remaining combat triggers, verify battle formulas and siege mechanics
- [ ] **Phase 5: Modifier Pipeline** - Complete item, special ability, and officer rank modifier effects on domestic commands
- [ ] **Phase 6: Economy Parity** - Verify all economic formulas (tax, trade, supply, population, city development, salary)
- [ ] **Phase 7: Command Parity** - Verify all 93 commands produce identical results to legacy PHP
- [ ] **Phase 8: NPC AI Parity** - Verify military AI, fix diplomacy AI, verify recruitment and strategic priorities
- [ ] **Phase 9: Turn Engine Completion** - Implement stub methods, verify turn step ordering and event triggers
- [ ] **Phase 10: Diplomacy and Scenario Data** - Verify diplomacy state machine and scenario initial conditions
- [ ] **Phase 11: Frontend Display Parity** - Verify game dashboard, general detail, nation management, and battle log display

## Phase Details

### Phase 1: Deterministic Foundation
**Goal**: All game execution is deterministic, observable, and correctly ordered -- enabling reliable parity verification for all subsequent phases
**Depends on**: Nothing (first phase)
**Requirements**: FOUND-01, FOUND-02, FOUND-03, FOUND-04, FOUND-05
**Success Criteria** (what must be TRUE):
  1. Running the same turn with the same seed produces identical game state every time (no java.util.Random remaining in game logic)
  2. LiteHashDRBG Kotlin output matches PHP SHA-512 RNG output byte-for-byte for the same seed
  3. Engine exceptions are logged with context instead of silently swallowed (all 20+ catch blocks emit log entries)
  4. Entity processing order within a turn tick is deterministic regardless of database query order
  5. RandUtil.choice() on a single-element list behaves identically to PHP array_rand on a single-element array
**Plans**: 2 plans

Plans:
- [x] 01-01-PLAN.md — RNG determinism: replace java.util.Random, fix RandUtil.choice() single-element, extend parity tests
- [x] 01-02-PLAN.md — Observability and ordering: add logging to 16 silent catch blocks, add turn sort tiebreakers

### Phase 2: Numeric Type Safety
**Goal**: All arithmetic operations on entity fields produce the same numeric results as legacy PHP, preventing silent overflow and truncation divergence
**Depends on**: Phase 1
**Requirements**: TYPE-01, TYPE-02, TYPE-03
**Success Criteria** (what must be TRUE):
  1. No Short field can silently wrap past 32767/-32768 -- all 30+ fields have coerceIn guards with domain-appropriate bounds
  2. A 200-turn economic simulation produces the same cumulative values in Kotlin and PHP (no truncation drift)
  3. Integer division in Kotlin matches PHP intdiv() behavior for all tested positive and negative dividend/divisor combinations
**Plans**: 2 plans

Plans:
- [x] 02-01-PLAN.md — Short overflow guards (coerceIn on all .toShort() sites), integer division parity verification, Wave 0 test scaffolds
- [x] 02-02-PLAN.md — Rounding normalization (Math.round/roundToInt audit), 200-turn golden snapshot integration test

### Phase 3: Battle Framework and Core Triggers
**Goal**: The WarUnitTrigger framework is operational and the four highest-impact battle abilities produce correct combat outcomes
**Depends on**: Phase 2
**Requirements**: BATTLE-01, BATTLE-05, BATTLE-06, BATTLE-09, BATTLE-10, BATTLE-11, BATTLE-12
**Success Criteria** (what must be TRUE):
  1. WarUnitTrigger hooks fire at the correct battle phases (pre-attack, post-damage, post-round) matching legacy trigger points
  2. Intimidation (위압), sniping (저격), battle healing (전투치료), and rage (격노) triggers produce the same battle outcome deltas as legacy PHP for identical inputs
  3. Generals gain combat experience (C7) from battles at the same rate as legacy PHP
  4. The 무쌍 modifier reads killnum from runtime rank data instead of returning hardcoded 0.0
**Plans**: 4 plans

Plans:
- [x] 03-01-PLAN.md — WarUnitTrigger framework (interface, registry, BattleEngine integration) and killnum fix (StatContext.killnum, che_무쌍 reads from stat)
- [x] 03-02-PLAN.md — Four core triggers (IntimidationTrigger, SnipingTrigger, BattleHealTrigger, RageTrigger) as WarUnitTrigger implementations
- [x] 03-03-PLAN.md — Battle experience (C7) parity verification tests (exp calculation, stat routing, win/lose atmos, full pipeline)
- [x] 03-04-PLAN.md — Gap closure: wire missing onPreAttack hook in BattleEngine, add rage integration test

### Phase 4: Battle Completion
**Goal**: All remaining combat triggers are implemented and battle resolution formulas match legacy for all unit types, terrain, and siege scenarios
**Depends on**: Phase 3
**Requirements**: BATTLE-02, BATTLE-03, BATTLE-04, BATTLE-07, BATTLE-08, BATTLE-13, BATTLE-14
**Success Criteria** (what must be TRUE):
  1. Counter-strategy (반계), sustained charge (돌격지속), injury nullification (부상무효), unavoidable critical (필살강화_회피불가), and city healing (도시치료) triggers produce the same outcomes as legacy PHP
  2. Battle resolution formula (damage calculation, casualties, morale impact) matches legacy process_war.php for a test matrix of unit type pairings
  3. Siege and defense mechanics (wall damage, defender bonuses, gate breach) produce identical results to legacy for the same inputs
**Plans**: 2 plans

Plans:
- [ ] 04-01-PLAN.md — 5 combat triggers (반계, 돌격지속, 부상무효, 필살강화_회피불가, 도시치료) + BattleEngine bonusPhases/rageExtraPhases phase loop fix + TODO cleanup
- [ ] 04-02-PLAN.md — Battle formula 6x6 ArmType matrix golden value tests + siege mechanics golden value tests

### Phase 5: Modifier Pipeline
**Goal**: Item, special ability, and officer rank modifiers correctly affect domestic command outcomes, matching legacy stacking and priority behavior
**Depends on**: Phase 3
**Requirements**: MOD-01, MOD-02, MOD-03, MOD-04
**Success Criteria** (what must be TRUE):
  1. A domestic command executed by a general with an item produces the same modified result as legacy PHP (H3 parity)
  2. A domestic command executed by a general with a special ability produces the same modified result as legacy PHP (H4 parity)
  3. A domestic command executed by a general with an officer rank produces the same modified result as legacy PHP (H5 parity)
  4. When multiple modifiers stack (item + special + rank), the combined effect matches legacy priority and application order
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

### Phase 6: Economy Parity
**Goal**: All economic calculations (tax, trade, supply, population, city development, salary) produce identical values to legacy PHP over sustained gameplay
**Depends on**: Phase 2
**Requirements**: ECON-01, ECON-02, ECON-03, ECON-04, ECON-05, ECON-06
**Success Criteria** (what must be TRUE):
  1. Tax collection for a city with known population/development/rate produces the same gold as legacy PHP
  2. Trade income for a general with known stats/items in a city with known market produces the same result as legacy PHP
  3. Food consumption for an army of known size with known supply route produces the same depletion as legacy PHP
  4. Population growth/decline over 12 turns for a city with known conditions matches legacy PHP within zero tolerance
  5. Semi-annual salary distribution for a nation with known officer roster produces the same gold deductions as legacy PHP
**Plans**: TBD

Plans:
- [ ] 06-01: TBD
- [ ] 06-02: TBD

### Phase 7: Command Parity
**Goal**: All 93 registered commands (55 general + 38 nation) produce identical entity mutations, log messages, and resource changes as legacy PHP
**Depends on**: Phase 4, Phase 5, Phase 6
**Requirements**: CMD-01, CMD-02, CMD-03, CMD-04
**Success Criteria** (what must be TRUE):
  1. Each of 55 general commands, given the same pre-state, produces the same post-state entity mutations as legacy PHP
  2. Each of 38 nation commands, given the same pre-state, produces the same post-state entity mutations as legacy PHP
  3. Command constraint checks (cooldowns, resource costs, prerequisites) accept and reject the same inputs as legacy PHP
  4. Command result side effects (log messages, notification triggers, stat changes) match legacy PHP
**Plans**: TBD

Plans:
- [ ] 07-01: TBD
- [ ] 07-02: TBD
- [ ] 07-03: TBD

### Phase 8: NPC AI Parity
**Goal**: NPC generals make the same strategic, tactical, and diplomatic decisions as legacy GeneralAI.php given the same game state
**Depends on**: Phase 7
**Requirements**: AI-01, AI-02, AI-03, AI-04
**Success Criteria** (what must be TRUE):
  1. NPC military AI selects the same action (attack target, defend city, develop) as legacy PHP for a set of reference game states
  2. NPC diplomacy AI proposes and responds to the same diplomatic actions as legacy PHP (fixing the "completely different" divergence)
  3. NPC recruitment and personnel management decisions (hire, fire, assign) match legacy PHP behavior
  4. NPC strategic priority ordering (when to attack vs. defend vs. develop) matches legacy PHP for early-game, mid-game, and late-game states
**Plans**: TBD

Plans:
- [ ] 08-01: TBD
- [ ] 08-02: TBD
- [ ] 08-03: TBD

### Phase 9: Turn Engine Completion
**Goal**: The turn pipeline executes all steps in the correct order with all stub methods implemented, and event/disaster triggers fire at legacy-matching probabilities
**Depends on**: Phase 8
**Requirements**: TURN-01, TURN-02, TURN-03, TURN-04, TURN-05, TURN-06
**Success Criteria** (what must be TRUE):
  1. Wander nations dissolve after 2 years of inactivity matching legacy checkWander behavior
  2. Turn step ordering matches legacy daemon.ts exactly (verified by golden snapshot comparison)
  3. Disaster and random event trigger probabilities match legacy PHP values (boom, drought, plague, etc.)
  4. updateOnline and updateGeneralNumber produce the same snapshots as legacy for the same game state
**Plans**: TBD

Plans:
- [ ] 09-01: TBD
- [ ] 09-02: TBD

### Phase 10: Diplomacy and Scenario Data
**Goal**: Diplomacy state transitions and game-end conditions match legacy, and all scenario initial conditions are verified against legacy data
**Depends on**: Phase 9
**Requirements**: DIPL-01, DIPL-02, DIPL-03, DATA-01, DATA-02, DATA-03
**Success Criteria** (what must be TRUE):
  1. Diplomacy state transitions (war declaration, alliance formation, ceasefire) trigger under the same conditions as legacy PHP
  2. Diplomacy timer/duration calculations match legacy PHP (ceasefire cooldowns, alliance minimum durations)
  3. Unification and game-end conditions trigger at the same point as legacy PHP
  4. NPC general stats in all 80+ scenarios match legacy 3-stat values (leadership, strength, intel)
  5. City initial conditions (population, development, defense) and scenario start conditions (year, month, nation relations) match legacy data files
**Plans**: TBD

Plans:
- [ ] 10-01: TBD
- [ ] 10-02: TBD

### Phase 11: Frontend Display Parity
**Goal**: The frontend displays all game information present in the legacy UI with correct data values
**Depends on**: Phase 7
**Requirements**: FE-01, FE-02, FE-03, FE-04
**Success Criteria** (what must be TRUE):
  1. The game dashboard shows all information fields present in the legacy dashboard (no missing data)
  2. The general detail page displays correct calculated stats matching backend values
  3. The nation management page shows correct aggregated data (total troops, cities, officers, treasury)
  4. Battle logs display the same combat events, damage values, and trigger activations as the legacy battle log format
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 11-01: TBD
- [ ] 11-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8 -> 9 -> 10 -> 11

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Deterministic Foundation | 0/2 | Planned | - |
| 2. Numeric Type Safety | 0/2 | Planned | - |
| 3. Battle Framework and Core Triggers | 0/4 | Planned | - |
| 4. Battle Completion | 0/2 | Planned | - |
| 5. Modifier Pipeline | 0/2 | Not started | - |
| 6. Economy Parity | 0/2 | Not started | - |
| 7. Command Parity | 0/3 | Not started | - |
| 8. NPC AI Parity | 0/3 | Not started | - |
| 9. Turn Engine Completion | 0/2 | Not started | - |
| 10. Diplomacy and Scenario Data | 0/2 | Not started | - |
| 11. Frontend Display Parity | 0/2 | Not started | - |
