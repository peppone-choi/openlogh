# Requirements: OpenSamguk Legacy Parity

**Defined:** 2026-03-31
**Core Value:** Every game mechanic must produce identical outcomes to the legacy PHP implementation given the same inputs.

## v1 Requirements

Requirements for legacy parity milestone. Each maps to roadmap phases.

### Foundation

- [ ] **FOUND-01**: Replace java.util.Random() with LiteHashDRBG in TurnService.registerAuction() and GeneralTrigger
- [ ] **FOUND-02**: Verify LiteHashDRBG cross-language parity (Kotlin SHA-512 output matches PHP implementation)
- [ ] **FOUND-03**: Add logging to all exception-swallowing catch blocks in engine code (20+ locations)
- [ ] **FOUND-04**: Add turn ordering tiebreakers to prevent non-deterministic entity processing order
- [ ] **FOUND-05**: Fix RandUtil.choice() single-element bias (PHP array_rand vs Kotlin behavior)

### Type Safety

- [ ] **TYPE-01**: Audit and guard 30+ Short/SMALLINT entity fields against arithmetic overflow
- [ ] **TYPE-02**: Audit 100+ float-to-int truncation patterns for PHP round() vs Kotlin roundToInt() divergence
- [ ] **TYPE-03**: Verify integer division behavior matches legacy (PHP intdiv vs Kotlin / operator)

### Battle System

- [ ] **BATTLE-01**: Implement WarUnitTrigger framework for runtime battle-phase hooks
- [ ] **BATTLE-02**: Implement 반계 (counter-strategy) trigger (시도/발동)
- [ ] **BATTLE-03**: Implement 돌격지속 (sustained charge) trigger
- [ ] **BATTLE-04**: Implement 부상무효 (injury nullification) trigger
- [ ] **BATTLE-05**: Implement 위압 (intimidation) trigger (시도/발동)
- [ ] **BATTLE-06**: Implement 저격 (sniping) trigger (시도/발동)
- [ ] **BATTLE-07**: Implement 필살강화_회피불가 (unavoidable critical) trigger
- [ ] **BATTLE-08**: Implement 도시치료 (city healing) trigger
- [ ] **BATTLE-09**: Implement 전투치료 (battle healing) trigger (시도/발동)
- [ ] **BATTLE-10**: Implement 격노 (rage) trigger (시도/발동)
- [ ] **BATTLE-11**: Implement battle experience (C7) -- generals gain XP from combat
- [ ] **BATTLE-12**: Fix 무쌍 modifier to read killnum from runtime rank data instead of hardcoded 0.0
- [ ] **BATTLE-13**: Verify battle resolution formulas match legacy process_war.php
- [ ] **BATTLE-14**: Verify siege/defense mechanics match legacy

### Modifier Pipeline

- [ ] **MOD-01**: Complete item modifier effects on domestic commands (H3)
- [ ] **MOD-02**: Complete special ability effects on domestic commands (H4)
- [ ] **MOD-03**: Complete officer rank modifier effects on domestic commands (H5)
- [ ] **MOD-04**: Verify modifier stacking/priority matches legacy behavior

### Command Parity

- [ ] **CMD-01**: Verify all 55 general commands produce identical results to legacy PHP
- [ ] **CMD-02**: Verify all 38 nation commands produce identical results to legacy PHP
- [ ] **CMD-03**: Verify command constraint checks match legacy (cooldowns, resource costs, prerequisites)
- [ ] **CMD-04**: Verify command result side effects match legacy (entity mutations, log messages)

### Economy

- [ ] **ECON-01**: Verify tax collection formula matches legacy
- [ ] **ECON-02**: Verify trade income formula matches legacy
- [ ] **ECON-03**: Verify supply/food consumption formula matches legacy
- [ ] **ECON-04**: Verify population growth/decline formula matches legacy
- [ ] **ECON-05**: Verify city development formulas match legacy
- [ ] **ECON-06**: Verify semi-annual salary distribution matches legacy

### NPC AI

- [ ] **AI-01**: Verify NPC military AI decision trees match legacy GeneralAI.php (40+ do*() methods)
- [ ] **AI-02**: Fix NPC diplomacy AI to match legacy behavior (currently "completely different")
- [ ] **AI-03**: Verify NPC recruitment/personnel decisions match legacy
- [ ] **AI-04**: Verify NPC strategic priorities (attack, defend, develop) match legacy

### Turn Engine

- [ ] **TURN-01**: Implement checkWander() -- wander nation dissolution after 2 years
- [ ] **TURN-02**: Implement updateOnline() -- per-tick online count snapshot
- [ ] **TURN-03**: Implement checkOverhead() -- runaway process guard
- [ ] **TURN-04**: Implement updateGeneralNumber() -- refresh nation static info
- [ ] **TURN-05**: Verify turn step ordering matches legacy daemon.ts
- [ ] **TURN-06**: Verify disaster/event trigger probabilities match legacy

### Diplomacy

- [ ] **DIPL-01**: Verify diplomacy state transitions match legacy (war, alliance, ceasefire conditions)
- [ ] **DIPL-02**: Verify diplomacy timer/duration calculations match legacy
- [ ] **DIPL-03**: Verify unification/game-end conditions match legacy

### Scenario Data

- [ ] **DATA-01**: Verify NPC general stats in all scenarios match legacy 3-stat values
- [ ] **DATA-02**: Verify city initial conditions (population, development, defense) match legacy
- [ ] **DATA-03**: Verify scenario start conditions (year, month, nation relations) match legacy

### Frontend Display

- [ ] **FE-01**: Verify game dashboard displays all information present in legacy UI
- [ ] **FE-02**: Verify general detail page shows correct stats and calculated values
- [ ] **FE-03**: Verify nation management page shows correct aggregated data
- [ ] **FE-04**: Verify battle log display matches legacy format and content

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
| FOUND-01..05 | Phase 1 | Pending |
| TYPE-01..03 | Phase 2 | Pending |
| BATTLE-01..14 | Phase 3 | Pending |
| MOD-01..04 | Phase 4 | Pending |
| CMD-01..04 | Phase 5 | Pending |
| ECON-01..06 | Phase 6 | Pending |
| AI-01..04 | Phase 7 | Pending |
| TURN-01..06 | Phase 8 | Pending |
| DIPL-01..03 | Phase 9 | Pending |
| DATA-01..03 | Phase 10 | Pending |
| FE-01..04 | Phase 11 | Pending |

**Coverage:**
- v1 requirements: 56 total
- Mapped to phases: 56
- Unmapped: 0

---
*Requirements defined: 2026-03-31*
*Last updated: 2026-03-31 after initialization*
