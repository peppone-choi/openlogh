# Requirements: OpenSamguk Legacy Parity

**Defined:** 2026-03-31
**Core Value:** Every game mechanic must produce identical outcomes to the legacy PHP implementation given the same inputs.

## v1 Requirements

Requirements for legacy parity milestone. Each maps to roadmap phases.

### Foundation

- [x] **FOUND-01**: Replace java.util.Random() with LiteHashDRBG in TurnService.registerAuction() and GeneralTrigger
- [x] **FOUND-02**: Verify LiteHashDRBG cross-language parity (Kotlin SHA-512 output matches PHP implementation)
- [x] **FOUND-03**: Add logging to all exception-swallowing catch blocks in engine code (20+ locations)
- [x] **FOUND-04**: Add turn ordering tiebreakers to prevent non-deterministic entity processing order
- [x] **FOUND-05**: Fix RandUtil.choice() single-element bias (PHP array_rand vs Kotlin behavior)

### Type Safety

- [x] **TYPE-01**: Audit and guard 30+ Short/SMALLINT entity fields against arithmetic overflow
- [x] **TYPE-02**: Audit 100+ float-to-int truncation patterns for PHP round() vs Kotlin roundToInt() divergence
- [x] **TYPE-03**: Verify integer division behavior matches legacy (PHP intdiv vs Kotlin / operator)

### Battle System

- [x] **BATTLE-01**: Implement WarUnitTrigger framework for runtime battle-phase hooks
- [x] **BATTLE-02**: Implement 반계 (counter-strategy) trigger (시도/발동)
- [x] **BATTLE-03**: Implement 돌격지속 (sustained charge) trigger
- [x] **BATTLE-04**: Implement 부상무효 (injury nullification) trigger
- [x] **BATTLE-05**: Implement 위압 (intimidation) trigger (시도/발동)
- [x] **BATTLE-06**: Implement 저격 (sniping) trigger (시도/발동)
- [x] **BATTLE-07**: Implement 필살강화_회피불가 (unavoidable critical) trigger
- [x] **BATTLE-08**: Implement 도시치료 (city healing) trigger
- [x] **BATTLE-09**: Implement 전투치료 (battle healing) trigger (시도/발동)
- [x] **BATTLE-10**: Implement 격노 (rage) trigger (시도/발동)
- [x] **BATTLE-11**: Implement battle experience (C7) -- generals gain XP from combat
- [x] **BATTLE-12**: Fix 무쌍 modifier to read killnum from runtime rank data instead of hardcoded 0.0
- [x] **BATTLE-13**: Verify battle resolution formulas match legacy process_war.php
- [x] **BATTLE-14**: Verify siege/defense mechanics match legacy

### Modifier Pipeline

- [ ] **MOD-01**: Complete item modifier effects on domestic commands (H3)
- [ ] **MOD-02**: Complete special ability effects on domestic commands (H4)
- [ ] **MOD-03**: Complete officer rank modifier effects on domestic commands (H5)
- [ ] **MOD-04**: Verify modifier stacking/priority matches legacy behavior

### Command Parity

- [x] **CMD-01**: Verify all 55 general commands produce identical results to legacy PHP
- [x] **CMD-02**: Verify all 38 nation commands produce identical results to legacy PHP
- [x] **CMD-03**: Verify command constraint checks match legacy (cooldowns, resource costs, prerequisites)
- [x] **CMD-04**: Verify command result side effects match legacy (entity mutations, log messages)

### Economy

- [x] **ECON-01**: Verify tax collection formula matches legacy
- [x] **ECON-02**: Verify trade income formula matches legacy
- [x] **ECON-03**: Verify supply/food consumption formula matches legacy
- [x] **ECON-04**: Verify population growth/decline formula matches legacy
- [x] **ECON-05**: Verify city development formulas match legacy
- [x] **ECON-06**: Verify semi-annual salary distribution matches legacy

### NPC AI

- [x] **AI-01**: Verify NPC military AI decision trees match legacy GeneralAI.php (40+ do*() methods)
- [x] **AI-02**: Fix NPC diplomacy AI to match legacy behavior (currently "completely different")
- [x] **AI-03**: Verify NPC recruitment/personnel decisions match legacy
- [x] **AI-04**: Verify NPC strategic priorities (attack, defend, develop) match legacy

### Turn Engine

- [x] **TURN-01**: Implement checkWander() -- wander nation dissolution after 2 years
- [x] **TURN-02**: Implement updateOnline() -- per-tick online count snapshot
- [x] **TURN-03**: Implement checkOverhead() -- runaway process guard
- [x] **TURN-04**: Implement updateGeneralNumber() -- refresh nation static info
- [x] **TURN-05**: Verify turn step ordering matches legacy daemon.ts
- [x] **TURN-06**: Verify disaster/event trigger probabilities match legacy

### Diplomacy

- [x] **DIPL-01**: Verify diplomacy state transitions match legacy (war, alliance, ceasefire conditions)
- [x] **DIPL-02**: Verify diplomacy timer/duration calculations match legacy
- [x] **DIPL-03**: Verify unification/game-end conditions match legacy

### Scenario Data

- [x] **DATA-01**: Verify NPC general stats in all scenarios match legacy 3-stat values
- [x] **DATA-02**: Verify city initial conditions (population, development, defense) match legacy
- [x] **DATA-03**: Verify scenario start conditions (year, month, nation relations) match legacy

### Frontend Display

- [x] **FE-01**: Verify game dashboard displays all information present in legacy UI
- [x] **FE-02**: Verify general detail page shows correct stats and calculated values
- [x] **FE-03**: Verify nation management page shows correct aggregated data
- [x] **FE-04**: Verify battle log display matches legacy format and content

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Testing Infrastructure

- **TEST-01**: Add jqwik property-based testing for formula edge cases
- **TEST-02**: Add ArchUnit architecture compliance guards
- **TEST-03**: Add Testcontainers for real PostgreSQL integration tests
- **TEST-04**: Implement golden snapshot JSON files for command expected values

### Security Hardening

- **SEC-01**: Restrict CORS to actual frontend domain(s)
- **SEC-02**: Add JWT validation on WebSocket connections
- **SEC-03**: Add authentication for internal endpoints
- **SEC-04**: Move JWT storage from localStorage to httpOnly cookies

### Performance

- **PERF-01**: Optimize turn processing for worlds with 500+ generals
- **PERF-02**: Add database query optimization for hot paths

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| New game mechanics beyond legacy | Core value is parity, not innovation |
| Mobile app | Web-only until parity complete |
| Chat system redesign | Keep legacy behavior |
| AuthService deduplication | Refactoring, not parity -- separate initiative |
| PHP runtime integration | Parity verified by code comparison, not runtime PHP execution |
| Visual/CSS parity | Functional parity only -- UI is already modernized |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| FOUND-01 | Phase 1: Deterministic Foundation | Complete |
| FOUND-02 | Phase 1: Deterministic Foundation | Complete |
| FOUND-03 | Phase 1: Deterministic Foundation | Complete |
| FOUND-04 | Phase 1: Deterministic Foundation | Complete |
| FOUND-05 | Phase 1: Deterministic Foundation | Complete |
| TYPE-01 | Phase 2: Numeric Type Safety | Complete |
| TYPE-02 | Phase 2: Numeric Type Safety | Complete |
| TYPE-03 | Phase 2: Numeric Type Safety | Complete |
| BATTLE-01 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-05 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-06 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-09 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-10 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-11 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-12 | Phase 3: Battle Framework and Core Triggers | Complete |
| BATTLE-02 | Phase 4: Battle Completion | Complete |
| BATTLE-03 | Phase 4: Battle Completion | Complete |
| BATTLE-04 | Phase 4: Battle Completion | Complete |
| BATTLE-07 | Phase 4: Battle Completion | Complete |
| BATTLE-08 | Phase 4: Battle Completion | Complete |
| BATTLE-13 | Phase 4: Battle Completion | Complete |
| BATTLE-14 | Phase 4: Battle Completion | Complete |
| MOD-01 | Phase 5: Modifier Pipeline | Pending |
| MOD-02 | Phase 5: Modifier Pipeline | Pending |
| MOD-03 | Phase 5: Modifier Pipeline | Pending |
| MOD-04 | Phase 5: Modifier Pipeline | Pending |
| ECON-01 | Phase 6: Economy Parity | Complete |
| ECON-02 | Phase 6: Economy Parity | Complete |
| ECON-03 | Phase 6: Economy Parity | Complete |
| ECON-04 | Phase 6: Economy Parity | Complete |
| ECON-05 | Phase 6: Economy Parity | Complete |
| ECON-06 | Phase 6: Economy Parity | Complete |
| CMD-01 | Phase 7: Command Parity | Complete |
| CMD-02 | Phase 7: Command Parity | Complete |
| CMD-03 | Phase 7: Command Parity | Complete |
| CMD-04 | Phase 7: Command Parity | Complete |
| AI-01 | Phase 8: NPC AI Parity | Complete |
| AI-02 | Phase 8: NPC AI Parity | Complete |
| AI-03 | Phase 8: NPC AI Parity | Complete |
| AI-04 | Phase 8: NPC AI Parity | Complete |
| TURN-01 | Phase 9: Turn Engine Completion | Complete |
| TURN-02 | Phase 9: Turn Engine Completion | Complete |
| TURN-03 | Phase 9: Turn Engine Completion | Complete |
| TURN-04 | Phase 9: Turn Engine Completion | Complete |
| TURN-05 | Phase 9: Turn Engine Completion | Complete |
| TURN-06 | Phase 9: Turn Engine Completion | Complete |
| DIPL-01 | Phase 10: Diplomacy and Scenario Data | Complete |
| DIPL-02 | Phase 10: Diplomacy and Scenario Data | Complete |
| DIPL-03 | Phase 10: Diplomacy and Scenario Data | Complete |
| DATA-01 | Phase 10: Diplomacy and Scenario Data | Complete |
| DATA-02 | Phase 10: Diplomacy and Scenario Data | Complete |
| DATA-03 | Phase 10: Diplomacy and Scenario Data | Complete |
| FE-01 | Phase 11: Frontend Display Parity | Complete |
| FE-02 | Phase 11: Frontend Display Parity | Complete |
| FE-03 | Phase 11: Frontend Display Parity | Complete |
| FE-04 | Phase 11: Frontend Display Parity | Complete |

**Coverage:**
- v1 requirements: 56 total
- Mapped to phases: 56
- Unmapped: 0

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-03-31 after roadmap creation*
