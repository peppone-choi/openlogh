# Project Research Summary

**Project:** OpenSamguk Legacy Parity Verification
**Domain:** PHP-to-Kotlin game engine port -- systematic parity verification for a Three Kingdoms turn-based strategy game
**Researched:** 2026-03-31
**Confidence:** HIGH

## Executive Summary

OpenSamguk is a substantial port of a PHP-based Three Kingdoms strategy game (devsam/core) to a modern Spring Boot 3.4.2/Kotlin 2.1.0 + Next.js 16 stack. The existing codebase is already ~90% complete with 1136 backend tests and 237 frontend tests, including 8 dedicated parity test classes and a well-designed three-tier verification architecture (formula, behavioral, E2E). The challenge is not building from scratch but closing the remaining 10% of parity gaps, which are concentrated in battle special abilities (WarUnitTrigger at ~30% match), the modifier pipeline (H3/H4/H5 at ~60% match), and NPC AI diplomacy (H8 described as "completely different"). The existing test infrastructure -- InMemoryTurnHarness, LiteHashDRBG, golden snapshots, Playwright E2E parity specs -- provides a strong foundation to build on.

The recommended approach is a dependency-ordered verification campaign: fix foundational issues first (non-deterministic RNG, exception swallowing, turn ordering tiebreakers), then verify individual systems bottom-up (formulas, then commands, then composite systems like NPC AI and turn pipeline), and finally run full system parity via E2E tests. The stack needs only incremental additions -- jqwik for property-based testing and ArchUnit for architecture compliance -- not any fundamental changes. The existing testing patterns (legacy reference as comments, deterministic RNG, golden snapshots, command effect categorization) are sound and should be extended, not replaced.

The critical risks are: (1) float-to-int truncation accumulation causing economic drift over hundreds of turns, requiring multi-turn simulation comparison tests; (2) Short (SMALLINT) overflow in 30+ entity fields where PHP uses unbounded integers; (3) two remaining `java.util.Random()` instances that make affected code paths untestable for parity; and (4) the WarUnitTrigger system where 14+ battle abilities are no-ops, fundamentally changing combat outcomes. All four risks have clear mitigation paths documented in the research.

## Key Findings

### Recommended Stack

The existing stack (Spring Boot 3.4.2/Kotlin 2.1.0, Next.js 16, PostgreSQL 16, Redis 7) is mature and requires no changes. Stack research focused on tooling additions for parity verification. No new frontend dependencies are needed -- existing Playwright and Vitest are sufficient with configuration changes.

**Additions to adopt:**
- **jqwik 1.9.3 + jqwik-kotlin**: Property-based testing for formula parity -- generates thousands of random input combinations to catch edge cases in 93 commands where hand-picked test vectors miss PHP/Kotlin divergence (rounding, overflow, negative values)
- **ArchUnit 1.4.1**: Architecture compliance tests -- enforces gateway-app/game-app boundaries and repository pattern as executable tests, catching architectural drift
- **Testcontainers 2.0.x**: Real PostgreSQL integration tests -- current H2 compatibility mode misses PostgreSQL-specific behavior (JSON operators, ON CONFLICT, integer division semantics)
- **JUnit5 @Tag("parity")**: Separate parity test suite from unit suite for independent CI runs -- no library needed, built into JUnit5 Platform

**Explicitly rejected:** MockK (project uses Mockito consistently across 99 files), Kotest (JUnit5 deeply embedded), Cucumber/BDD (parity needs exact numeric assertions), Spring Cloud Contract (overkill for internal proxy), KotlinSnapshot (unmaintained, existing golden snapshot pattern is superior).

### Expected Features

The feature landscape is a parity verification matrix, not a greenfield feature set. Features are categorized by gameplay impact and current match rate.

**Must fix before launch (table stakes for parity):**
- **Battle XP system (C7)** -- generals do not gain experience from combat; entire progression is broken
- **WarUnitTrigger framework + top 4 abilities** (intimidation, snipe, rage, medicine) -- these fundamentally change battle outcomes; currently at ~30% match
- **checkWander stub** -- wander nations never dissolve; causes game state pollution
- **Non-deterministic RNG** -- 2 instances of java.util.Random(); blocks reproducibility and parity testing
- **Modifier pipeline (H3/H4/H5)** -- domestic commands do not account for items/specials; ~60% match

**Should fix (competitive parity):**
- **Remaining WarUnitTrigger abilities** (charge persist, injury immunity, enhanced critical, counter-strategy) -- lower gameplay impact but noticeable
- **NPC AI diplomacy refinement (H8/H9/H10)** -- NPCs function but with different diplomatic personality
- **Economy full audit** -- existing formulas ~90% correct; marginal differences

**Defer (post-launch):**
- **Frontend display parity** -- UI is intentionally different; only data accuracy matters
- **updateOnline/checkOverhead stubs** -- operational, not gameplay-affecting
- **Remaining 83 untested commands** -- expand coverage systematically post-launch

**Intentional divergences (do NOT fix):**
- 5-stat extension, PostgreSQL migration, JWT auth, CQRS engine, World = Profile model, Redis NPC tokens, Docker deployment

### Architecture Approach

Parity verification follows a three-tier model already partially implemented: Tier 1 (formula parity) uses pure function tests with hardcoded expected values from PHP reference comments; Tier 2 (behavioral parity) uses InMemoryTurnHarness for full command execution without database; Tier 3 (system parity) uses Playwright E2E comparing legacy PHP and new system side by side. The build order enforces a strict dependency chain: constraints first, then formulas, then commands, then composite systems (NPC AI, turn pipeline), then full system replay, and finally E2E.

**Major verification components:**
1. **qa/parity/ (8 test classes)** -- Formula-level verification: math matches PHP for battle, economy, commands, constraints, NPC AI, tech research, turn pipeline
2. **InMemoryTurnHarness** -- Test infrastructure enabling behavioral parity tests without database; wires mocked repositories with in-memory implementations
3. **Golden snapshot system** -- GoldenSnapshotTest and GoldenValueTest lock known-good outputs; catches unintended side effects when any formula changes
4. **LiteHashDRBG** -- SHA-512-based deterministic RNG matching legacy PHP's seeded randomness; foundational for all reproducible testing
5. **Playwright E2E parity** -- parity-config.ts catalogs all 93 commands with safety flags and 36 page routes; parity-helpers.ts provides dual-system auth and delta checking

### Critical Pitfalls

1. **Float-to-int truncation accumulation** -- 100+ instances of `(value * factor).toInt()` in EconomyService alone. PHP and Kotlin can produce different truncation results at boundaries. After 500+ turns, economic state diverges noticeably. **Avoid by:** writing multi-turn (200+ iteration) decay comparison tests for every `*.toInt()` pattern; verifying whether legacy uses `intval()`, `floor()`, `round()`, or implicit cast per formula.

2. **Short (SMALLINT) overflow** -- 30+ entity fields typed as `Short` where PHP uses unbounded integers. `(g.betray + cnt).toShort()` silently wraps at 32768. **Avoid by:** adding `coerceIn()` before every `.toShort()` conversion; for bounded fields (atmos 0-100, train 0-110), assert the game-logic bound.

3. **Non-deterministic RNG** -- `java.util.Random()` in TurnService.registerAuction and GeneralTrigger. These paths cannot be parity-tested or replayed. **Avoid by:** replacing with `DeterministicRng.create()` (pattern already established in GeneralAI.kt). This is a 5-minute fix that unblocks significant verification work.

4. **PHP array ordering vs Kotlin Map ordering** -- `groupBy {}` iteration order in economy processing depends on database query order, which differs between MariaDB and PostgreSQL. **Avoid by:** adding explicit `.sortedBy {}` after every `groupBy {}` where the iteration body has side effects.

5. **Exception swallowing hiding parity bugs** -- 20+ instances of `catch (_: Exception)` in engine code. Economy, AI, and event processing silently fail, producing wrong state with no error logged. **Avoid by:** adding `logger.error()` to every catch block before suppressing; in test mode, configure to rethrow.

## Implications for Roadmap

Based on combined research, the following 6-phase structure respects dependency ordering, groups related work, and addresses pitfalls proactively.

### Phase 1: Foundation Hardening
**Rationale:** Three foundational issues block all subsequent parity work. They are quick fixes with outsized impact. Nothing downstream can be reliably verified until these are resolved.
**Delivers:** Deterministic, observable, correctly-ordered game execution
**Addresses:** Non-deterministic RNG fix (2 locations), exception logging (20+ catch blocks), turn ordering tiebreaker (2 sort sites), LiteHashDRBG cross-language verification, RandUtil.choice() single-element fix
**Avoids:** Pitfall #3 (non-deterministic RNG), Pitfall #8 (exception swallowing), Pitfall #10 (turn ordering), Pitfall #11 (LiteHashDRBG byte-order), Pitfall #14 (choice() RNG consumption)
**Effort:** Low (1-2 days)

### Phase 2: Formula and Type Safety Audit
**Rationale:** Before verifying commands and battle logic, the underlying numeric types and conversion patterns must be correct. This phase fixes the substrate that all game logic sits on.
**Delivers:** Overflow-safe entity fields, verified truncation patterns, JUnit5 @Tag("parity") infrastructure, jqwik property-based test setup
**Addresses:** Short overflow audit (30+ fields), float-to-int truncation audit (100+ sites), groupBy iteration ordering, Testcontainers setup for PostgreSQL-specific SQL
**Avoids:** Pitfall #1 (truncation accumulation), Pitfall #2 (Short overflow), Pitfall #4 (Map ordering), Pitfall #5 (MariaDB vs PostgreSQL division)
**Uses:** jqwik 1.9.3 (new), ArchUnit 1.4.1 (new), Testcontainers 2.0.x (new)
**Effort:** Medium (3-5 days)

### Phase 3: Battle System Parity
**Rationale:** Battle is the highest-impact game system. WarUnitTrigger is at ~30% match -- the largest parity gap. Battle XP (C7) blocks general progression. The modifier pipeline (H3/H4/H5) must be correct before battle abilities can use it. This phase has the highest gameplay-impact-per-effort ratio.
**Delivers:** Working WarUnitTrigger framework with top 4 abilities, battle XP system, modifier pipeline, city.dead overflow protection
**Addresses:** WarUnitTrigger (14+ abilities, top 4 priority), Battle XP (C7), modifier pipeline (H3/H4/H5 onCalcDomestic, getDomesticExpLevelBonus, CriticalScoreEx), killnum through StatContext
**Avoids:** Pitfall #9 (SMALLINT overflow on city.dead), Pitfall #1 (float truncation in damage formulas)
**Effort:** High (7-10 days)

### Phase 4: Command and Economy Parity
**Rationale:** With battle and modifiers correct, the 93 commands can be verified systematically. Economy formulas are ~90% correct but need full audit. The modifier pipeline from Phase 3 unblocks domestic command parity (C2: resource procurement).
**Delivers:** Verified command parity for all 93 commands, economy formula full audit, semi-annual income, war income, disaster probabilities
**Addresses:** C2 (resource procurement formula), remaining command cooldowns (H14), constraint gaps, economy formula audit (Phase 5 of legacy plan), supply route BFS, auction determinism
**Avoids:** Pitfall #1 (truncation in economy formulas), Pitfall #7 (floor vs toInt on negative floats)
**Effort:** High (7-10 days)

### Phase 5: NPC AI and Turn Pipeline
**Rationale:** NPC AI depends on correct constraints, commands, economy, and battle -- all addressed in Phases 1-4. The turn pipeline depends on all individual steps being correct. This is the composite verification layer.
**Delivers:** Verified NPC AI diplomacy, military AI, wanderer AI, checkWander implementation, turn pipeline golden snapshots, deterministic replay verification
**Addresses:** H8 (non-aggression logic "completely different"), H9 (tax/officer simplification), H10 (reward calculation), checkWander stub, updateGeneralNumber, multi-turn golden snapshot expansion
**Avoids:** Pitfall #4 (iteration order in AI decisions), Pitfall #10 (turn processing order)
**Effort:** High (7-10 days)

### Phase 6: E2E and Frontend Parity
**Rationale:** E2E parity should only run after all backend parity is confirmed. Frontend display verification is lowest priority since the UI is intentionally different -- only data accuracy matters.
**Delivers:** Playwright visual regression baselines, all 36 page routes verified for data completeness, battle log formatting, map visual accuracy
**Addresses:** Frontend display parity (30+ pages), Playwright toHaveScreenshot configuration, parity-screenshots/ baseline updates
**Avoids:** Pitfall #12 (Korean text encoding in display)
**Effort:** Medium (3-5 days)

### Phase Ordering Rationale

- **Phase 1 before everything:** Non-deterministic RNG and exception swallowing literally make parity bugs invisible. Fixing these first ensures all subsequent work produces reliable, observable results.
- **Phase 2 before Phase 3-4:** Type safety (Short overflow) and numeric conversion patterns (truncation) are substrate issues. If the entity types are wrong, command and battle tests give misleading results.
- **Phase 3 before Phase 4:** The modifier pipeline (H3/H4/H5) is a dependency for domestic commands (C2). Battle must be correct before commands that trigger combat can be verified.
- **Phase 4 before Phase 5:** NPC AI executes commands and evaluates economy/battle. AI parity is meaningless if the commands it executes produce wrong results.
- **Phase 5 before Phase 6:** E2E tests exercise the entire stack. If backend parity fails, E2E failures are undebuggable noise.
- **Grouping logic:** Each phase targets one tier of the architecture (foundation -> types -> battle system -> command/economy system -> composite AI/pipeline -> full system).

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 3 (Battle System):** WarUnitTrigger is the largest parity gap at ~30% match. The 14+ battle abilities each have unique trigger mechanics (phase hooks, damage modifiers, state accumulators). Each ability needs individual comparison against the legacy PHP implementation in `legacy-core/`.
- **Phase 5 (NPC AI):** NPC diplomacy is described as "completely different" from legacy (H8). The military AI has 40+ `do*()` methods needing individual verification. This phase will require extensive `legacy-core/` reference reading.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** Well-defined fixes (replace java.util.Random, add logging, add sort tiebreaker). No ambiguity.
- **Phase 2 (Type Safety):** Mechanical audit work (grep for .toShort(), add coerceIn). Pattern is clear from existing codebase.
- **Phase 4 (Command/Economy):** Established parity test patterns from CommandParityTest and EconomyFormulaParityTest. Scale existing patterns to cover 83 more commands.
- **Phase 6 (E2E):** Playwright visual regression is well-documented; parity-config.ts already catalogs all pages and commands.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All recommendations are incremental additions to a mature stack. Compatibility verified against existing Spring Boot BOM and JUnit5. No speculative choices. |
| Features | HIGH | All findings based on direct codebase inspection and existing project analysis documents (legacy-parity.analysis.md, CONCERNS.md). Parity percentages are from the project's own audit. |
| Architecture | HIGH | Three-tier parity model already partially implemented with 8 test classes. Patterns extracted from existing code, not theoretical. Build-order dependency chain derived from actual code dependencies. |
| Pitfalls | HIGH | All 14 pitfalls documented with concrete file paths and line numbers from the codebase. Evidence-based, not hypothetical. PHP/Kotlin behavioral differences verified against language documentation. |

**Overall confidence:** HIGH

### Gaps to Address

- **WarUnitTrigger implementation detail:** The 14+ battle abilities need individual PHP-to-Kotlin translation. Research identified the gap but did not specify the exact trigger mechanics for each ability. Each ability will need legacy-core/ PHP reference during Phase 3 implementation.
- **Multi-turn economic drift magnitude:** Research identified truncation accumulation as critical but did not quantify the actual drift. Phase 2 should include a simulation test that measures divergence over 200+ turns to determine if the differences are gameplay-significant or cosmetic.
- **NPC AI decision tree completeness:** The 40+ `do*()` methods in military AI need individual comparison. Research identified the gap but did not enumerate which methods are correct vs. which diverge.
- **Semi-annual salary formula:** Identified as needing verification but no specific analysis of how Kotlin and PHP implementations differ.
- **Disaster/boom trigger probabilities:** Identified as ~80% match but exact probability values not compared against PHP.
- **jqwik Kotlin 2.1 compatibility:** jqwik-kotlin 1.9.3 documented for Kotlin 2.0; minor version bump should be compatible but verify with smoke test.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis of 200+ source files in `backend/game-app/src/`
- Project analysis documents: `docs/03-analysis/legacy-parity.analysis.md`, `.planning/codebase/CONCERNS.md`, `.planning/codebase/ARCHITECTURE.md`
- Existing parity test suite: `qa/parity/` (8 test classes), `engine/` (golden snapshot, replay, formula tests), `frontend/e2e/parity/` (5 spec files)
- Command registry: `CommandRegistry.kt` (55 general + 38 nation = 93 registered commands)

### Secondary (MEDIUM confidence)
- [jqwik User Guide 1.9.3](https://jqwik.net/docs/current/user-guide.html) -- property-based testing capabilities
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) -- architecture compliance testing
- [Testcontainers for Java](https://java.testcontainers.org/) -- PostgreSQL integration test containers
- [Playwright Visual Comparisons](https://playwright.dev/docs/test-snapshots) -- screenshot regression testing

### Tertiary (LOW confidence)
- PHP behavioral edge cases (intval on strings, array_rand on single elements) -- verified against PHP manual but not tested cross-language with this project's specific data

---
*Research completed: 2026-03-31*
*Ready for roadmap: yes*
