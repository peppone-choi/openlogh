# Phase 10: Diplomacy and Scenario Data - Research

**Researched:** 2026-04-02
**Domain:** Diplomacy state machine parity, game-end conditions, scenario data validation
**Confidence:** HIGH

## Summary

Phase 10 is a verification-only phase. The diplomacy state machine (`DiplomacyService.kt`), unification logic (`UnificationService.kt`), and scenario data (84 JSON files) are already implemented. The work is to write parity tests that confirm these implementations match the legacy PHP behavior exactly.

The diplomacy system uses a 6-state model with string state codes matching PHP integer states (0=war, 1=declared, 2=neutral, 7=non-aggression, plus proposal states). The turn processing logic in `processDiplomacyTurn()` handles term decrement, state transitions (declaration->war, pact expiry), and proposal expiry. The legacy PHP has additional war-term logic based on casualty counts (`dead` field) that needs careful comparison.

Scenario data validation requires comparing 84 JSON files between `legacy-core/hwe/scenario/` and `backend/shared/src/main/resources/data/scenarios/`. The key difference: legacy uses 3-stat generals (leadership, strength, intel at indices 5-7) while opensamguk uses 5-stat (adds politics, charm at indices 8-9), shifting subsequent field positions. Automated diff tests must account for this structural difference.

**Primary recommendation:** Follow the established qa/parity/ golden value test pattern from DisasterParityTest. Extract diplomacy state transition rules and timer values from PHP source as golden values, validate unification trigger conditions, and build automated scenario JSON diff tests comparing 3-stat values at known array indices.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: Extract diplomacy state machine from legacy PHP, verify with golden value tests (reuse DisasterParityTest pattern)
- D-02: Verify ALL timer values -- ceasefire cooldowns, alliance minimum durations, war declaration limits
- D-03: Create DiplomacyParityTest.kt in qa/parity/ folder
- D-04: Map and test ALL game-end conditions (unification, wanderer timeout, deadline expiry)
- D-05: Create GameEndParityTest.kt as separate file
- D-06: Automate legacy PHP scenario file vs JSON diff -- parse and compare 3-stat values automatically
- D-07: Create ScenarioDataParityTest.kt in qa/parity/ folder
- D-08: 3-plan split -- Plan 1: diplomacy state/timers (DIPL-01,02), Plan 2: game-end (DIPL-03), Plan 3: scenario data (DATA-01~03)
- D-09: Wave 1: diplomacy+game-end parallel, Wave 2: scenario data sequential

### Claude's Discretion
- City/nation initial condition verification automation scope (general stats must be automated, rest at Claude's discretion)
- Scenario data diff tool implementation details
- Which PHP files to read for diplomacy state machine extraction

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DIPL-01 | Verify diplomacy state transitions match legacy (war, alliance, ceasefire conditions) | PHP state codes extracted: 0=war, 1=declared, 2=neutral, 7=non-aggression. Transition rules documented from `func_gamerule.php` lines 337-406 and 6 command files. DiplomacyService.kt already implements matching transitions. |
| DIPL-02 | Verify diplomacy timer/duration calculations match legacy | PHP timer constants: declaration term=24, war initial term=6, non-aggression term=60, proposal terms=12. Legacy turn processing decrements all terms by 1 per turn, with state-specific transitions at term=0. War term has additional casualty-based increment (dead/100/genCount, capped at 13). |
| DIPL-03 | Verify unification/game-end conditions match legacy | checkEmperior: isunited==0, exactly 1 active nation (level>0), all cities owned. checkWander: year >= startyear+2, dissolves wanderer nations. UnificationService.kt implements matching logic. |
| DATA-01 | Verify NPC general stats in all scenarios match legacy 3-stat values | Legacy general array indices 5/6/7 = leadership/strength/intel. Current array indices 5/6/7 match (indices 8/9 are opensamguk-only politics/charm). 84 scenario files, ~500 generals in largest scenario. |
| DATA-02 | Verify city initial conditions (population, development, defense) match legacy | Scenario city data in `cities` key. Current ScenarioService has CITY_LEVEL_INIT matching legacy CityConstBase::$buildInit. |
| DATA-03 | Verify scenario start conditions (year, month, nation relations) match legacy | startYear, nation array, diplomacy array directly comparable between legacy and current JSON files. |
</phase_requirements>

## Architecture Patterns

### Existing Parity Test Pattern (REUSE)
```
backend/game-app/src/test/kotlin/com/opensam/qa/parity/
  BattleParityTest.kt
  CommandParityTest.kt
  ConstraintParityTest.kt
  DisasterParityTest.kt
  EconomyCommandParityTest.kt
  EconomyEventParityTest.kt
  EconomyFormulaParityTest.kt
  EconomyIntegrationParityTest.kt
  NpcAiParityTest.kt
  TechResearchParityTest.kt
  TurnPipelineParityTest.kt
```

### Pattern: Golden Value Test (from DisasterParityTest)
**What:** Extract exact values from legacy PHP source, lock them as assertions in Kotlin tests
**When to use:** Verifying formulas, constants, state transitions
**Example:**
```kotlin
// Source: DisasterParityTest.kt pattern
@DisplayName("Diplomacy Parity")
class DiplomacyParityTest {
    @Nested
    @DisplayName("State Transition Constants")
    inner class StateTransitionConstants {
        @Test
        fun `war declaration term matches legacy`() {
            // Legacy: che_선전포고.php line 158: 'term' => 24
            assertThat(DiplomacyService.WAR_DECLARATION_TERM.toInt()).isEqualTo(24)
        }
    }
}
```

### Pattern: Source-Code Reading Assertion (from DisasterParityTest)
**What:** Read Kotlin source file in test and assert structural properties
**When to use:** Verifying internal implementation matches expected patterns
**Example:** DisasterParityTest reads EconomyService.kt to assert disasterEntries map contains correct state codes

### Pattern: Scenario Data Diff Test
**What:** Parse both legacy PHP JSON and current JSON files, compare field-by-field
**When to use:** DATA-01/02/03 -- bulk data verification across 84 files
**Example:**
```kotlin
@DisplayName("Scenario Data Parity")
class ScenarioDataParityTest {
    @Test
    fun `all scenario generals have matching 3-stat values`() {
        val legacyDir = File("../../legacy-core/hwe/scenario/")
        val currentDir = File("../../backend/shared/src/main/resources/data/scenarios/")
        // Parse each scenario, compare general[5]/[6]/[7] (leadership/strength/intel)
    }
}
```

### Anti-Patterns to Avoid
- **Sampling instead of exhaustive testing:** D-06 requires full 84-scenario coverage, not sampling
- **Comparing politics/charm (indices 8-9):** These are opensamguk extensions, not in legacy. Only compare indices 5-7.
- **Ignoring field position shift:** Current 5-stat format shifts subsequent fields by +2 compared to legacy 3-stat format

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON parsing | Custom JSON parser | Jackson ObjectMapper (already in project) | ScenarioData model already handles deserialization |
| File comparison | Manual file-by-file reading | Parameterized test over directory listing | 84 files need consistent comparison logic |
| State machine verification | Runtime state machine simulator | Golden value assertions on constants and source | This is a verification phase, not implementation |

## Common Pitfalls

### Pitfall 1: Legacy Diplomacy Has Bidirectional Rows
**What goes wrong:** PHP diplomacy table stores TWO rows per nation pair (me=A,you=B AND me=B,you=A). Commands update BOTH rows with `(me=%i AND you=%i) OR (me=%i AND you=%i)`.
**Why it happens:** Legacy uses a symmetric matrix. Kotlin DiplomacyService stores one row per relation and checks both directions in `isRelationBetween()`.
**How to avoid:** Test assertions must verify the Kotlin single-row model produces equivalent query results to PHP's two-row model.
**Warning signs:** Tests that check diplomacy by (srcNationId, destNationId) but miss the reverse direction.

### Pitfall 2: War Term Casualty Logic
**What goes wrong:** Legacy `func_gamerule.php` lines 337-349 have a war term increment based on `dead` (casualties) field: `term = valueFit(term + floor(dead/100/genCount), 0, 13)`. The Kotlin `processDiplomacyTurn()` does NOT appear to implement this casualty-based term extension.
**Why it happens:** The `dead` field tracking is a runtime accumulation from battle processing that feeds back into diplomacy term calculation.
**How to avoid:** This is a potential parity gap. The test should verify whether the Kotlin implementation handles war duration correctly. If the `dead`-based extension is missing, this must be flagged and may require implementation work.
**Warning signs:** War ends sooner in Kotlin than in PHP because term is not extended by casualty counts.

### Pitfall 3: Scenario General Name Differences
**What goes wrong:** Legacy and current scenarios may have different generals (e.g., legacy scenario_1010 general[0] is "소제1" while current is "영제"). Some generals were renamed or replaced in the opensamguk version.
**Why it happens:** The 5-stat migration also updated some general entries.
**How to avoid:** Match generals by name, not by array index. For generals that exist in BOTH files, compare the 3-stat values. Document any generals that were added/removed.
**Warning signs:** Array-index-based comparison fails because general ordering differs.

### Pitfall 4: Non-Aggression Acceptance Uses Absolute Date
**What goes wrong:** Legacy `che_불가침수락.php` calculates term as `reqMonth - currentMonth` (absolute month difference). The non-aggression pact has a deadline, not a fixed duration.
**Why it happens:** PHP non-aggression proposal includes `year` and `month` parameters specifying when the pact expires. Term is the remaining months until that date.
**How to avoid:** Verify that Kotlin's `acceptNonAggression()` correctly converts the year/month deadline to a term value, or that it uses a fixed duration equivalent.
**Warning signs:** Non-aggression pact durations are inconsistent between PHP and Kotlin.

### Pitfall 5: Ceasefire Sets State=2 (Neutral), NOT a Special Ceasefire State
**What goes wrong:** Assuming ceasefire is a separate state. In PHP, `che_종전수락.php` line 157-168 sets `state=2, term=0` -- returning to neutral. Same for `che_불가침파기수락.php`.
**Why it happens:** PHP uses state=2 as the "no relation" / neutral state. There is no separate ceasefire cooldown state.
**How to avoid:** Verify that Kotlin ceasefire acceptance kills the war relation (isDead=true) and does NOT create a new "ceasefire" state relation.

### Pitfall 6: Legacy Auto-Ceasefire When Both Sides' War Terms Reach 0
**What goes wrong:** Missing the automatic ceasefire logic in `func_gamerule.php` lines 364-389. When BOTH rows (me->you AND you->me) of a war (state=0) have term<=1, the war automatically ends with state=2.
**Why it happens:** This is a turn-processing side effect, not a command action. It's in `func_gamerule.php` outside any command class.
**How to avoid:** Verify whether Kotlin's `processDiplomacyTurn()` implements equivalent auto-ceasefire logic. The current implementation expires non-aggression and proposals but may not have the auto-ceasefire from war-term exhaustion.

## Code Examples

### Legacy PHP Diplomacy State Codes (Golden Values)
```
// Source: func_gamerule.php lines 393-406
state=0  ->  전쟁 (active war)
state=1  ->  선전포고 (war declaration, term=24)
state=2  ->  통상/중립 (neutral, no relation)
state=7  ->  불가침 (non-aggression pact)

// Turn processing state transitions:
term-- each turn (line 397: greatest(0, term-1))
state=7, term=0 -> state=2 (non-aggression expires to neutral)
state=1, term=0 -> state=0, term=6 (declaration becomes active war)

// War term has additional casualty-based increment:
// func_gamerule.php lines 337-349:
// term += floor(dead / 100 / genCount), capped at 13
```

### Legacy PHP Timer Constants (Golden Values)
```
// Source: extracted from command PHP files
War declaration term: 24 (che_선전포고.php line 158)
War initial term after declaration: 6 (func_gamerule.php line 406)
Non-aggression minimum proposal: 6 months ahead (che_불가침제의.php line 108)
Non-aggression acceptance term: reqMonth - currentMonth (che_불가침수락.php line 204)
Break non-aggression acceptance: state=2, term=0 (che_불가침파기수락.php line 149)
Ceasefire acceptance: state=2, term=0 (che_종전수락.php line 157)
Diplomatic message validity: max(30, turnterm*3) minutes (multiple files)
```

### Legacy PHP Unification Check (Golden Values)
```php
// Source: func_gamerule.php lines 696-762
function checkEmperior() {
    // Guard: isunited != 0 -> return (already unified)
    // Query: SELECT nation FROM nation WHERE level > 0 LIMIT 2
    // Guard: count != 1 -> return (not exactly one nation)
    // Guard: cityCnt != count(CityConst::all()) -> return (not all cities owned)
    // Action: isunited = 2, refreshLimit *= 100
    // Action: inheritance points, hall of fame, dynasty records
}
```

### Kotlin DiplomacyService State Code Mapping
```kotlin
// Source: DiplomacyService.kt lines 410-431
// PHP int -> Kotlin string:
0 -> "전쟁"           // AT_WAR
1 -> "선전포고"        // WAR_DECLARATION
2 -> ""               // neutral (no relation)
7 -> "불가침"          // NON_AGGRESSION
3 -> "불가침제의"      // proposal states (opensamguk extension)
4 -> "불가침파기제의"
5 -> "종전제의"
```

### Scenario General Field Mapping
```
Legacy 3-stat format (13-14 fields):
  [0] nation_code
  [1] name
  [2] picture_code
  [3] npc_state
  [4] (reserved/null)
  [5] leadership        <-- compare
  [6] strength          <-- compare
  [7] intel             <-- compare
  [8] (reserved, always 0)
  [9] birth_year
  [10] death_year
  [11] personal_code
  [12] special_code (nullable)
  [13] intro (optional)

Current 5-stat format (14-16 fields):
  [0] nation_code
  [1] name
  [2] picture_code
  [3] npc_state
  [4] (reserved/null)
  [5] leadership        <-- compare
  [6] strength          <-- compare
  [7] intel             <-- compare
  [8] politics          (opensamguk extension, skip)
  [9] charm             (opensamguk extension, skip)
  [10] (reserved)
  [11] birth_year
  [12] death_year
  [13] personal_code
  [14] special_code (nullable)
  [15] intro (optional)
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (via Spring Boot Test) |
| Config file | `backend/game-app/build.gradle.kts` (test dependencies) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.*ParityTest" -x bootJar` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x bootJar` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DIPL-01 | Diplomacy state transitions match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.DiplomacyParityTest" -x bootJar` | Wave 0 |
| DIPL-02 | Diplomacy timer/duration calculations match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.DiplomacyParityTest" -x bootJar` | Wave 0 |
| DIPL-03 | Unification/game-end conditions match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.GameEndParityTest" -x bootJar` | Wave 0 |
| DATA-01 | NPC general 3-stat values match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.ScenarioDataParityTest" -x bootJar` | Wave 0 |
| DATA-02 | City initial conditions match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.ScenarioDataParityTest" -x bootJar` | Wave 0 |
| DATA-03 | Scenario start conditions match legacy | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.ScenarioDataParityTest" -x bootJar` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.*ParityTest" -x bootJar`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x bootJar`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/DiplomacyParityTest.kt` -- covers DIPL-01, DIPL-02
- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/GameEndParityTest.kt` -- covers DIPL-03
- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/ScenarioDataParityTest.kt` -- covers DATA-01, DATA-02, DATA-03

## Potential Parity Gaps (Requiring Implementation)

These are areas where the test may FAIL, requiring code fixes before the test can pass:

### Gap 1: War Term Casualty Extension (MEDIUM risk)
**PHP:** `func_gamerule.php` lines 337-349 increment war term based on `dead` field (casualties). Formula: `term += floor(dead/100/genCount)`, capped at 13.
**Kotlin:** `processDiplomacyTurn()` only decrements term by 1. No casualty-based extension found.
**Impact:** Wars may end prematurely in Kotlin vs PHP.
**Action:** Test should verify this behavior. If missing, Plan 1 may need an implementation task.

### Gap 2: Auto-Ceasefire on War Term Exhaustion (MEDIUM risk)
**PHP:** `func_gamerule.php` lines 364-389 auto-ceasefire when BOTH sides of a war have term<=1 (bidirectional check).
**Kotlin:** `processDiplomacyTurn()` handles war=state transitions but may not implement the bidirectional auto-ceasefire.
**Impact:** Wars that should auto-end may persist in Kotlin.
**Action:** Test should verify. If missing, requires implementation.

### Gap 3: Non-Aggression Acceptance Uses Dynamic Term (LOW risk)
**PHP:** `che_불가침수락.php` calculates term from absolute date: `reqMonth - currentMonth`.
**Kotlin:** `acceptNonAggression()` uses fixed `NON_AGGRESSION_TERM = 60`.
**Impact:** Non-aggression pact durations may differ.
**Action:** Test should verify. The Kotlin implementation may be intentionally different if commands pass the term.

## Open Questions

1. **War term casualty extension**
   - What we know: PHP has `dead`-based term extension. Kotlin appears to lack it.
   - What's unclear: Is this already handled elsewhere in the turn pipeline?
   - Recommendation: Test for it; if missing, add implementation task to Plan 1.

2. **Scenario general identity matching**
   - What we know: Legacy has "소제1" where current has "영제" at same position in scenario_1010.
   - What's unclear: How many generals were renamed/replaced in the opensamguk version?
   - Recommendation: Match by name for generals present in BOTH files. Report unmatched generals as informational (not failures).

3. **Scenario city data format**
   - What we know: Scenario_1010 has a `cities` key with city data.
   - What's unclear: Exact field format of city arrays needs validation during implementation.
   - Recommendation: Parse a sample city entry from both legacy and current before writing the full diff test.

## Sources

### Primary (HIGH confidence)
- `legacy-core/hwe/sammo/Command/Nation/che_선전포고.php` -- war declaration command (term=24)
- `legacy-core/hwe/sammo/Command/Nation/che_불가침제의.php` -- non-aggression proposal (6-month minimum)
- `legacy-core/hwe/sammo/Command/Nation/che_불가침수락.php` -- non-aggression acceptance (dynamic term)
- `legacy-core/hwe/sammo/Command/Nation/che_불가침파기제의.php` -- break proposal
- `legacy-core/hwe/sammo/Command/Nation/che_불가침파기수락.php` -- break acceptance (state=2)
- `legacy-core/hwe/sammo/Command/Nation/che_종전제의.php` -- ceasefire proposal
- `legacy-core/hwe/sammo/Command/Nation/che_종전수락.php` -- ceasefire acceptance (state=2)
- `legacy-core/hwe/func_gamerule.php` lines 337-406 -- diplomacy turn processing
- `legacy-core/hwe/func_gamerule.php` lines 696-940 -- checkEmperior (unification)
- `backend/game-app/src/main/kotlin/com/opensam/engine/DiplomacyService.kt` -- current implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/UnificationService.kt` -- current implementation
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/DisasterParityTest.kt` -- test pattern reference

### Secondary (MEDIUM confidence)
- Scenario JSON file comparison (84 files examined by script)
- Field position mapping (verified by parsing actual JSON data)

## Metadata

**Confidence breakdown:**
- Diplomacy state machine: HIGH - all 7 PHP command files and turn processing logic read directly
- Game-end conditions: HIGH - checkEmperior function fully read, UnificationService compared
- Scenario data format: HIGH - field positions verified by parsing actual JSON data from both sources
- Parity gaps: MEDIUM - potential gaps identified but need test verification to confirm

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable domain, legacy code frozen)
