# Phase 6: Economy Parity - Research

**Researched:** 2026-04-01
**Domain:** Economy formula verification and parity testing (EconomyService.kt vs legacy PHP)
**Confidence:** HIGH

## Summary

Phase 6 verifies that all economic calculations in the Kotlin codebase produce identical values to the legacy PHP implementation. The good news: the core implementation already exists. `EconomyService.kt` (878 lines) contains 7 major methods covering income, semi-annual processing, supply routing, nation level, disaster/boom, trade rate randomization, and yearly statistics. Two existing parity test files (`EconomyFormulaParityTest.kt`, `EconomyEventParityTest.kt`) already cover many golden-value scenarios for individual formulas (gold/rice/wall income, tax rate, war income, salary/bill, general/nation resource decay, population growth, infrastructure growth).

The remaining work falls into three categories: (1) deepening existing tests with PHP-verified golden values extracted from legacy-core source (which must be re-cloned per D-02), (2) adding coverage for economy-related commands (`che_군량매매`, `che_헌납`, domestic commands affecting economy), and (3) building a 24-turn integration simulation to detect multi-turn drift. The economy-related turn pipeline steps (EconomyPreUpdateStep, EconomyPostUpdateStep, DisasterAndTradeStep, YearlyStatisticsStep) also need execution-order verification against the legacy daemon.ts.

**Primary recommendation:** Clone legacy-core, extract exact PHP golden values for each formula, compare against existing Kotlin implementations, fix mismatches immediately (D-03), and add 24-turn integration simulation to catch cumulative drift.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Dual approach -- per-formula golden value unit tests + 24-turn (2 year) integration simulation with 4 semi-annual cycles. Same pattern as Phase 4/5.
- **D-02:** Re-clone legacy-core/ from devsam/core. Read PHP source directly for golden value extraction. Existing Kotlin comments/formulas alone are insufficient.
- **D-03:** Fix-on-discovery -- mismatches found during audit are fixed immediately with tests added. No deferred batch fixing.
- **D-04:** Full scope: EconomyService 6 formulas + disaster/boom/power + economy-related commands (che_무역/징수 etc.) with constraint and side-effect verification. Phase 7 handles non-economy commands only.

### Claude's Discretion
- Golden value extraction methodology from PHP code (manual trace specifics)
- Unit test class splitting strategy (per-formula vs functional grouping)
- Integration test scenario design (city count, nation count, initial conditions)
- Economy-related command identification and prioritization

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ECON-01 | Verify tax collection formula matches legacy | `calcCityGoldIncome`, `calcCityRiceIncome`, `calcCityWallIncome` + tax rate multiplier (`taxRate/20`) already implemented; need PHP golden values from `func_time_event.php:88-161` |
| ECON-02 | Verify trade income formula matches legacy | `che_군량매매` command exists with trade rate logic; needs golden value verification against legacy PHP trade command |
| ECON-03 | Verify supply/food consumption formula matches legacy | `updateCitySupply` implements BFS supply routing + unsupplied penalty (0.9 decay); needs legacy comparison from `func_time_event.php` |
| ECON-04 | Verify population growth/decline formula matches legacy | `processSemiAnnual` popRatio/growth logic exists; needs PHP golden values from `ProcessSemiAnnual.php` |
| ECON-05 | Verify city development formulas match legacy | Infrastructure growth via `genericRatio`, 0.99 decay, disaster/boom effects; all implemented, needs PHP value verification |
| ECON-06 | Verify semi-annual salary distribution matches legacy | `getBill`/`getDedLevel`/`processIncome` salary distribution exists; needs verification of edge cases (empty treasury, partial payment) |
</phase_requirements>

## Standard Stack

No new libraries needed. This phase is purely verification/testing using existing test infrastructure.

### Core (Already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit Jupiter | 5.x (Spring Boot managed) | Test framework | Project standard |
| AssertJ | 3.x (Spring Boot managed) | Fluent assertions | Already used in EconomyFormulaParityTest |
| Mockito | 5.x (Spring Boot managed) | Mock repositories | Already used in existing economy tests |
| JUnit Params | 5.x | Parameterized/CsvSource tests | Already used for golden value tables |

### Supporting (Already in project)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| DeterministicRng | internal | Seeded RNG for disaster/trade rate | Already used in processDisasterOrBoom |
| InMemoryTurnHarness | internal test | Multi-turn simulation | 24-turn integration test |

**Installation:** None required -- all dependencies already present.

## Architecture Patterns

### Existing Test Structure
```
backend/game-app/src/test/kotlin/com/opensam/
  qa/parity/
    EconomyFormulaParityTest.kt    -- per-formula golden values (EXTEND)
    EconomyEventParityTest.kt      -- semi-annual/disaster events (EXTEND)
  test/
    InMemoryTurnHarness.kt         -- multi-turn simulation harness
  engine/
    EconomyServiceTest.kt          -- unit tests
```

### Pattern 1: Golden Value Unit Test (Established in Phase 4/5)
**What:** Fixed inputs -> PHP-computed expected value -> hardcoded assertion
**When to use:** Every individual formula
**Example (existing pattern):**
```kotlin
@ParameterizedTest
@CsvSource(
    "10000, 500, 1000, 80, 500, 1000, 0, false, 1, 20",
    // ... more rows
)
fun `gold income golden values`(pop: Int, comm: Int, ...) {
    // seed entities, run service method, compare against legacy formula
    val expected = legacyCityGoldIncome(pop, comm, ...)
    val expectedTaxed = (expected * taxRate / 20).toInt()
    assertThat(actual).isCloseTo(expectedTaxed, within(1))
}
```

### Pattern 2: Legacy Formula Helper (Established)
**What:** Kotlin re-implementation of PHP formula as test helper for golden value computation
**When to use:** Each economy formula needs a `legacy*()` helper that mirrors the PHP exactly
**Example (existing):**
```kotlin
private fun legacyCityGoldIncome(pop: Int, comm: Int, commMax: Int, ...): Double {
    // Mirrors func_time_event.php:88-104 exactly
    if (commMax == 0) return 0.0
    val trustRatio = trust / 200.0 + 0.5
    var income = pop.toDouble() * comm / commMax * trustRatio / 30
    // ...
    return income
}
```

### Pattern 3: 24-Turn Integration Simulation (New for this phase)
**What:** Run 24 turns (2 game years) through economy processing, verify cumulative state
**When to use:** Detect drift from compounding rounding errors or step ordering issues
**Key details:**
- Must include 4 semi-annual cycles (months 1,7 x 2 years)
- Must include disaster/boom processing
- Must track population, resources, infrastructure drift over time
- Use DeterministicRng with fixed seed for reproducibility

### Pattern 4: Economy Command Verification
**What:** Test economy commands (che_군량매매, che_헌납, domestic commands) for full behavior parity
**When to use:** Each economy-related command
**Key details:**
- Test constraint checks (ReqCityTrader, SuppliedCity, etc.)
- Test side effects (gold/rice delta, nation tax, experience/dedication gains)
- Test trade rate interaction (city.trade field)

### Anti-Patterns to Avoid
- **Comparative testing only:** Do NOT compare Kotlin vs Kotlin (e.g., "income at tax 20 is double income at tax 10"). Always compare against absolute PHP golden values.
- **Ignoring integer truncation:** PHP `intval()` truncates toward zero; Kotlin `.toInt()` also truncates. But `round()` behavior differs. Watch for `.toInt()` on intermediate calculations.
- **Testing formulas in isolation without pipeline context:** Economy formulas interact through turn pipeline ordering. Step 300 (pre-update) runs before Step 1000 (post-update) before Step 1100 (disaster). Verify this ordering matches legacy daemon.ts.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multi-turn simulation | Custom turn loop | InMemoryTurnHarness | Already handles entity lifecycle, step ordering, persistence |
| Deterministic randomness | Custom seed management | DeterministicRng.create() | Already supports hiddenSeed + domain + year/month composite keys |
| Entity snapshot/restore | Manual field copying | toSnapshot()/toEntity() | Established pattern, handles all entity fields correctly |
| Mock wiring | Per-test mock setup | Existing wireRepos() pattern from EconomyFormulaParityTest | Handles cityRepository, nationRepository, generalRepository, save callbacks |

## Common Pitfalls

### Pitfall 1: legacy-core/ Not Present
**What goes wrong:** Research and golden value extraction cannot proceed without PHP source
**Why it happens:** D-02 explicitly says legacy-core needs re-cloning; directory confirmed missing
**How to avoid:** First task of any plan MUST be cloning legacy-core from devsam/core
**Warning signs:** `ls legacy-core/` returns "No such file or directory"

### Pitfall 2: PHP Integer Truncation vs Kotlin
**What goes wrong:** PHP `(int)($value)` and Kotlin `value.toInt()` both truncate toward zero, but intermediate `round()` calls differ
**Why it happens:** Phase 2 documented: `kotlin.math.round` uses banker's rounding at .5 boundaries; PHP `round()` rounds half away from zero
**How to avoid:** Use explicit `.toInt()` (truncation) matching PHP `intval()`, and `kotlin.math.round()` only where PHP uses `round()`. Phase 2 decision accepted this divergence.
**Warning signs:** Off-by-one errors at boundary values (e.g., values ending in .5)

### Pitfall 3: Semi-Annual Ordering (Decay Before Growth)
**What goes wrong:** Population/infrastructure values don't match legacy
**Why it happens:** Legacy ProcessSemiAnnual.php applies 0.99 decay to ALL cities FIRST (line 75-82), then applies growth only to supplied nation cities. Current Kotlin code already handles this correctly, but tests must verify the ordering.
**How to avoid:** Integration tests should verify: `net_value = value * 0.99 * (1 + genericRatio)` for supplied cities
**Warning signs:** Infrastructure values consistently ~1% too high or too low

### Pitfall 4: EconomyPreUpdateStep shouldSkip=true
**What goes wrong:** Double-execution of income processing
**Why it happens:** EconomyPreUpdateStep is marked `shouldSkip=true` because TurnService.processWorld() calls preUpdateMonthly() directly before advanceMonth(). This is for legacy ordering parity.
**How to avoid:** Do NOT change shouldSkip. Verify that TurnService ordering matches legacy daemon.ts.
**Warning signs:** Nation gold/rice values double what's expected

### Pitfall 5: Nation Type Modifier Application on Income
**What goes wrong:** Gold/rice multipliers not applied or applied incorrectly
**Why it happens:** NationTypeModifiers has 25+ nation types with various goldMultiplier, riceMultiplier, popGrowthMultiplier values
**How to avoid:** Test at least 3-4 nation types (상인=gold*1.2, 농업국=rice*1.2/pop*1.05, 도적=gold*0.9, neutral=no modifier)
**Warning signs:** Income correct for default nations but wrong for specialized nation types

### Pitfall 6: Trade Command Exchange Fee and Rate
**What goes wrong:** che_군량매매 produces wrong gold/rice delta
**Why it happens:** Trade involves `city.trade / 100.0` rate AND `env.exchangeFee` tax. The buy/sell asymmetry and fee calculation are subtle.
**How to avoid:** Extract exact PHP formula from legacy-core trade command, create golden values for buy and sell directions
**Warning signs:** Trade amounts off by the fee percentage

## Code Examples

### Key EconomyService Methods and Their Legacy Sources

| Method | Legacy PHP Source | What It Does |
|--------|------------------|--------------|
| `calcCityGoldIncome()` | func_time_event.php:88 | pop * comm/commMax * trustRatio/30 * secuBonus * officerBonus * capitalBonus |
| `calcCityRiceIncome()` | func_time_event.php:106 | pop * agri/agriMax * trustRatio/30 * secuBonus * officerBonus * capitalBonus |
| `calcCityWallIncome()` | func_time_event.php:124 | def * wall/wallMax / 3 * secuBonus * officerBonus * capitalBonus |
| `processIncome()` | ProcessIncome.php | Per-nation income collection + salary distribution |
| `processSemiAnnual()` | ProcessSemiAnnual.php | 0.99 decay -> pop growth -> infra growth -> trust adj -> resource decay |
| `getBill()` | func_converter.php:668 | ceil(sqrt(ded)/10) * 200 + 400 |
| `updateCitySupply()` | func_time_event.php | BFS from capital, unsupplied penalty, trust<30 neutral conversion |
| `updateNationLevel()` | UpdateNationLevel.php | Threshold-based level-up with gold/rice reward |
| `processDisasterOrBoom()` | RaiseDisaster.php | Month-dependent probability, secu-ratio effect scaling |
| `randomizeCityTradeRate()` | RandomizeCityTradeRate.php | Level-based probability, range 95-105 |
| `processYearlyStatistics()` | checkStatistic | Power = (resource + tech + cityPower + statPower + dexPower + expDed) / 10 |

### Economy-Related Commands to Verify

| Command | File | Economy Relevance |
|---------|------|-------------------|
| `che_군량매매` | command/general/che_군량매매.kt | Trade gold<->rice with trade rate and exchange fee |
| `che_헌납` | command/general/che_헌납.kt | Donate gold/rice from general to nation treasury |
| `che_농지개간` | command/general/che_농지개간.kt | Domestic: increases city.agri (affects rice income) |
| `che_상업투자` | command/general/che_상업투자.kt | Domestic: increases city.comm (affects gold income) |
| `che_치안강화` | command/general/che_치안강화.kt | Domestic: increases city.secu (affects income multiplier) |
| `che_수비강화` | command/general/che_수비강화.kt | Domestic: increases city.def (affects wall income) |
| `che_성벽보수` | command/general/che_성벽보수.kt | Domestic: increases city.wall (affects wall income) |
| `che_포상` | command/nation/che_포상.kt | Nation: distribute gold/rice to a general |
| `che_몰수` | command/nation/che_몰수.kt | Nation: confiscate gold/rice from a general |
| `che_물자원조` | command/nation/che_물자원조.kt | Nation: send resources to another nation |
| `che_증축` | command/nation/che_증축.kt | Nation: upgrade city level (affects popMax, income capacity) |
| `che_감축` | command/nation/che_감축.kt | Nation: downgrade city level |

### Turn Pipeline Economy Step Ordering

| Step | Order | Class | When |
|------|-------|-------|------|
| EconomyPreUpdate | 300 | EconomyPreUpdateStep | SKIPPED (handled by TurnService directly) |
| YearlyStatistics | 800 | YearlyStatisticsStep | Month == 1 only |
| EconomyPostUpdate | 1000 | EconomyPostUpdateStep | Every turn |
| DisasterAndTrade | 1100 | DisasterAndTradeStep | Every turn |

### Existing Test Coverage Summary

| Test Class | Tests | Coverage Area | Gaps |
|------------|-------|---------------|------|
| EconomyFormulaParityTest | ~25+ | Gold/rice/wall income, tax rate, war income, salary/bill | Need PHP-verified golden values (currently uses Kotlin formula mirrors) |
| EconomyEventParityTest | ~15+ | Semi-annual decay/growth, population, infrastructure, resource decay | Need disaster/boom, trade rate randomization, nation level tests |
| EconomyServiceTest | unit tests | Basic service behavior | Need turn pipeline integration |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x (Spring Boot managed) |
| Config file | `backend/game-app/build.gradle.kts` (useJUnitPlatform) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.Economy*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| ECON-01 | Tax collection formula | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest*CityGoldIncome*"` | Exists (extend with PHP golden values) |
| ECON-02 | Trade income formula | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest*Trade*"` | Partial (che_군량매매 needs dedicated parity test) |
| ECON-03 | Supply/food consumption | unit | `./gradlew :game-app:test --tests "*.EconomyEventParityTest*Supply*"` | Needs new tests |
| ECON-04 | Population growth/decline | unit | `./gradlew :game-app:test --tests "*.EconomyEventParityTest*SemiAnnual*"` | Exists (extend) |
| ECON-05 | City development formulas | unit + integration | `./gradlew :game-app:test --tests "*.EconomyEventParityTest*"` | Exists (extend with disaster/boom) |
| ECON-06 | Semi-annual salary | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest*Salary*"` | Exists (extend) |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.Economy*" -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] Clone `legacy-core/` from devsam/core (required for PHP golden value extraction)
- [ ] `EconomyCommandParityTest.kt` -- covers ECON-02 (trade), economy command constraints/side-effects
- [ ] `EconomyIntegrationParityTest.kt` -- covers 24-turn simulation (D-01 integration requirement)
- [ ] Supply route parity tests in EconomyEventParityTest -- covers ECON-03

## Open Questions

1. **Legacy-core clone access**
   - What we know: D-02 requires re-cloning from `https://storage.hided.net/gitea/devsam/core`
   - What's unclear: Whether the git remote is still accessible from the current machine
   - Recommendation: First task should attempt clone; if blocked, use existing Kotlin comments/formula mirrors as fallback (with LOW confidence marking)

2. **exchangeFee source value**
   - What we know: `che_군량매매` uses `env.exchangeFee` for trade tax calculation
   - What's unclear: What CommandEnv.exchangeFee is set to (need to check legacy default)
   - Recommendation: Read CommandEnv and game_const.json during implementation

3. **Disaster probability table completeness**
   - What we know: `processDisasterOrBoom` has month-based entries (1,4,7,10) and boom rate (4,7=25%)
   - What's unclear: Whether disaster probability per-city formula (`0.06 - secuRatio * 0.05`) exactly matches PHP
   - Recommendation: Verify against RaiseDisaster.php after legacy-core clone

## Sources

### Primary (HIGH confidence)
- `EconomyService.kt` (878 lines) -- complete implementation read, all 7 methods documented
- `EconomyFormulaParityTest.kt` (~550 lines) -- existing golden value tests with legacy PHP source annotations
- `EconomyEventParityTest.kt` (~300+ lines) -- existing semi-annual/event parity tests
- `NationTypeModifiers.kt` (222 lines) -- 25+ nation type income modifiers
- Turn step classes (EconomyPreUpdateStep, EconomyPostUpdateStep, DisasterAndTradeStep, YearlyStatisticsStep) -- all read
- Economy command files (che_군량매매, che_헌납, DomesticCommand base class) -- all read
- Phase 5 CONTEXT.md -- confirmed fix-on-discovery and PHP full-read patterns

### Secondary (MEDIUM confidence)
- Legacy PHP file references in test comments (func_time_event.php line numbers, ProcessSemiAnnual.php, func_converter.php) -- referenced but not directly verified (legacy-core not present)

### Tertiary (LOW confidence)
- Specific formula coefficients from CONTEXT.md `<specifics>` section -- plausible but need PHP verification after clone

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries, all testing infrastructure exists
- Architecture: HIGH -- established golden value test pattern from Phase 4/5, existing test scaffolding
- Pitfalls: HIGH -- based on direct code reading and Phase 2 decisions (rounding, truncation)
- Economy formulas: MEDIUM -- implementation exists and tests exist, but PHP golden value verification pending legacy-core clone

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable domain -- game formulas don't change)
