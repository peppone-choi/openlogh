# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 -- Legacy Parity

**Shipped:** 2026-04-03
**Phases:** 11 | **Plans:** 29

### What Was Built
- Deterministic game engine with LiteHashDRBG replacing java.util.Random
- Complete battle system with 10 WarUnitTrigger combat abilities and golden-value locked formulas
- Full command parity: 93 commands (55 general + 38 nation) verified against legacy PHP
- NPC AI matching legacy GeneralAI.php across 35+ decision methods
- Turn engine with all stubs implemented, step ordering locked to legacy daemon.ts
- Diplomacy state machine, 81 scenario data sets, and frontend display parity for 28 pages

### What Worked
- Golden-value test approach: fixed-seed RNG + deterministic fixtures + exact JSON assertions proved highly effective for parity verification
- Phase dependency ordering (foundation -> types -> systems -> composites) prevented cascading rework
- Reading legacy PHP source directly (not trusting docs) caught multiple subtle divergences
- Plan execution velocity was consistent (~10min avg per plan)

### What Was Inefficient
- ROADMAP progress table got out of sync with actual phase completion status
- PROJECT.md Active requirements weren't updated after each phase (still showed stale items at milestone end)
- Some phases (5, 8, 9) showed "In Progress" in progress table despite being complete

### Patterns Established
- Golden-value parity testing pattern for formula verification
- `coerceIn` guards for all Short field assignments
- WarUnitTrigger hook pattern for battle ability extensibility
- Source-code assertion tests for ordering verification
- DiplomacyState enum with code: Int matching PHP constants

### Key Lessons
1. Golden-value tests are the most reliable parity verification -- comparative tests can hide proportional errors
2. Reading PHP source is non-negotiable; documentation and comments frequently diverge from actual behavior
3. RNG seed context matters: a single-character typo ("disater" vs "disaster") causes permanent divergence if fixed after world data is created
4. NPC AI has the most methods (~35) but individual methods are straightforward once the entry flow is correct

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Phases | Plans | Key Change |
|-----------|--------|-------|------------|
| v1.0 | 11 | 29 | Golden-value parity testing established |

### Top Lessons (Verified Across Milestones)

1. Code is the single source of truth for parity -- never trust docs or comments
2. Golden-value tests with fixed seeds are the gold standard for formula verification
