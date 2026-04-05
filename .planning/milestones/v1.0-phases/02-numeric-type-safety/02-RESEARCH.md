# Phase 2: Numeric Type Safety - Research

**Researched:** 2026-04-01
**Domain:** Kotlin/JVM numeric arithmetic parity with PHP 7.x integer/float behavior
**Confidence:** HIGH

## Summary

This phase targets three categories of numeric divergence between the Kotlin (JVM) implementation and the legacy PHP source: (1) Short/SMALLINT overflow on 70+ entity fields, (2) inconsistent float-to-int rounding across 100+ call sites using three different methods, and (3) integer division behavior differences for negative operands.

The codebase audit reveals 709 `.toInt()` calls across 43 engine files, 348 `.toShort()` calls across 60 files, 13 `Math.round()` calls, 90+ `roundToInt()` calls, and 265 `coerceIn/coerceAtLeast/coerceAtMost` guards already in place. The primary risk is not Short overflow (most fields have natural game-logic bounds) but the inconsistent rounding behavior -- `Math.round()` (Java, returns Long, half-up), `roundToInt()` (Kotlin, half-to-even/banker's), and `.toInt()` (truncation toward zero) are used interchangeably where PHP uses `(int)` cast (truncation) or `round()` (half-away-from-zero). Over 200 turns of economic simulation, these 1-value discrepancies compound.

**Primary recommendation:** Audit all rounding call sites by file priority (EconomyService > GeneralAI > BattleEngine > commands), normalize each to match the specific PHP behavior for that formula, and validate with a 200-turn golden snapshot test extending the existing `GoldenSnapshotTest` / `InMemoryTurnHarness` infrastructure.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Place `coerceIn` guards at domain boundaries -- inside service methods where computed values are assigned back to entity fields. Not at entity setter level and not at every arithmetic site.
- **D-02:** Follow the existing pattern established in `ItemService.kt`: `general.injury = (general.injury - item.value).coerceIn(0, 80).toShort()`.
- **D-03:** Each Short field gets domain-appropriate bounds derived from legacy PHP behavior, not assumed.
- **D-04:** Audit scope: all 50+ Short fields across General (30), City (7), Nation (4+), Diplomacy, Emperor, Event, GeneralTurn entities.
- **D-05:** Build a PHP-to-Kotlin golden value comparison table for rounding edge cases: 0.5, 1.5, -0.5, -1.5, 2.5, large values near Short.MAX_VALUE.
- **D-06:** Audit and normalize the inconsistent rounding: `Math.round().toInt()`, `roundToInt()`, `.toInt()`. Each call site must match legacy PHP behavior.
- **D-07:** Priority audit targets by volume: EconomyService.kt (20+), GeneralAI.kt (30+), BattleEngine.kt/BattleTrigger.kt (35+), command files, NpcSpawnService.kt.
- **D-08:** Verify-and-document approach for integer division. Kotlin `/` on Int truncates toward zero, same as PHP `intdiv()` for positive values. Audit for negative-value edge cases.
- **D-09:** Add `phpIntdiv()` utility only if audit reveals divisions that can receive negative dividends/divisors with different truncation behavior.
- **D-10:** Document which entity fields can go negative and add targeted division guards for those paths only.
- **D-11:** Primary verification: 200-turn economic simulation golden snapshot test with fixed seed.
- **D-12:** Supplementary: targeted unit tests for each rounding/overflow fix.
- **D-13:** jqwik property-based testing is optional -- not required for phase success.

### Claude's Discretion
- Specific ordering of file audits within each requirement
- Whether to create a shared `NumericUtils.kt` or inline guards at each site
- Granularity of golden value test assertions (per-field vs aggregate checksums)
- Whether `Math.round()` vs `roundToInt()` normalization should be separate or combined with truncation audit

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TYPE-01 | Audit and guard 30+ Short/SMALLINT entity fields against arithmetic overflow | Complete entity Short field inventory (70+ fields identified across 12 entities); existing guard pattern in ItemService.kt; domain bounds derivable from game logic |
| TYPE-02 | Audit 100+ float-to-int truncation patterns for PHP round() vs Kotlin roundToInt() divergence | Three distinct rounding methods identified across codebase (Math.round, roundToInt, .toInt); PHP rounding semantics documented; file-by-file call-site counts available |
| TYPE-03 | Verify integer division behavior matches legacy (PHP intdiv vs Kotlin / operator) | Kotlin and PHP both truncate toward zero for positive operands; negative-operand audit needed for gold (can go negative) and some BattleEngine calculations |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin stdlib | 2.1.0 | `coerceIn`, `coerceAtLeast`, `coerceAtMost`, `roundToInt` | Already used in 265+ locations; native Kotlin idiom for bounds clamping |
| kotlin.math | 2.1.0 | `round`, `ceil`, `floor`, `sqrt`, `roundToInt` | Standard math operations, no external dependency |
| JUnit Jupiter | 5.x (via spring-boot-starter-test) | Unit tests for rounding/overflow guards | Already configured in build.gradle.kts for all test files |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| AssertJ | via spring-boot-starter-test | Fluent assertions with `within()` tolerance | Already used in EconomyFormulaParityTest for numeric comparisons |
| Mockito | via spring-boot-starter-test | Service mocking for isolated unit tests | Already used extensively in existing test infrastructure |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Inline `coerceIn` guards | Custom value classes (e.g., `BoundedShort`) | Significant refactor of 70+ fields; overkill for this phase; violates "simplicity first" |
| Manual rounding audit | jqwik property-based testing | D-13 marks jqwik as optional; no jqwik dependency in build.gradle.kts; Kotlin 2.1 compatibility unverified |
| Per-site guards | Entity-level setter guards | D-01 explicitly rejects setter-level guards (masks bugs) |

**No installation needed:** All required libraries are already in the project's dependency tree.

## Architecture Patterns

### Recommended Audit Structure
```
backend/game-app/src/main/kotlin/com/opensam/
├── engine/
│   ├── EconomyService.kt          # Wave 1: 82 .toInt(), 6 .toShort() -- highest volume
│   ├── ai/GeneralAI.kt            # Wave 1: 180 .toInt(), 6 .toShort() -- highest volume
│   ├── war/BattleEngine.kt        # Wave 2: 23 .toInt() -- battle formulas
│   ├── war/BattleTrigger.kt       # Wave 2: 3 .toInt(), 10 coerce -- triggers
│   ├── war/BattleService.kt       # Wave 2: 31 .toInt(), 5 coerce
│   ├── war/WarAftermath.kt        # Wave 2: 27 .toInt(), 9 coerce
│   ├── war/WarUnitGeneral.kt      # Wave 2: 8 .toInt(), 6 coerce
│   ├── NpcSpawnService.kt         # Wave 3: 30 .toInt(), 34 .toShort()
│   ├── GeneralMaintenanceService.kt # Wave 3: 31 .toInt(), 7 .toShort()
│   ├── TurnService.kt             # Wave 3: 54 .toInt(), 13 .toShort()
│   └── StatChangeService.kt       # Wave 3: 6 .toInt(), 5 .toShort()
├── command/
│   ├── general/                    # Wave 4: domestic/military command files
│   └── nation/                     # Wave 4: nation command files
└── service/
    ├── ItemService.kt              # REFERENCE: existing guard pattern
    └── AuctionService.kt           # REFERENCE: existing guard pattern
```

### Pattern 1: Domain-Boundary Guard (D-02 pattern)
**What:** Apply `coerceIn` at the point where a computed value is written back to a Short entity field.
**When to use:** Every assignment to a Short entity field after arithmetic.
**Example:**
```kotlin
// Source: ItemService.kt line 127 (existing codebase pattern)
general.injury = (general.injury - item.value).coerceIn(0, 80).toShort()
general.atmos = (general.atmos + item.value).coerceIn(0, 150).toShort()
general.train = (general.train + item.value).coerceIn(0, 110).toShort()
```

### Pattern 2: PHP-Compatible Rounding
**What:** Standardize rounding to match PHP behavior for each formula context.
**When to use:** Every float-to-int conversion where the legacy PHP uses `(int)` cast or `round()`.
**Example:**
```kotlin
// PHP: (int)($value * 0.99) -- truncates toward zero
// Kotlin CORRECT: (value * 0.99).toInt()  -- same behavior
// Kotlin WRONG: (value * 0.99).roundToInt()  -- banker's rounding, differs at .5

// PHP: round($value) -- half-away-from-zero
// Kotlin CORRECT: kotlin.math.round(value).toInt()  -- half-to-even (DIFFERS at .5!)
// Kotlin CORRECT for PHP parity: use custom phpRound() for .5 cases
// In practice: PHP round(2.5)=3, Kotlin round(2.5)=2 (banker's)
```

### Pattern 3: Golden Snapshot Test Extension
**What:** Extend existing GoldenSnapshotTest to cover 200+ turns with economy processing enabled.
**When to use:** Primary verification for TYPE-01 and TYPE-02 combined effect.
**Example:**
```kotlin
// Source: GoldenSnapshotTest.kt pattern (existing test)
// Extend with: real EconomyService instead of mock, 200 turns, verify cumulative values
class NumericParityGoldenTest {
    @Test
    fun `200-turn simulation matches golden values`() {
        val harness = InMemoryTurnHarness() // needs real EconomyService
        // ... setup world, nations, cities, generals
        repeat(200) { harness.turnService.processWorld(world) }
        // Assert: nation gold/rice, city pop/agri/comm/secu/def/wall, general stats
    }
}
```

### Anti-Patterns to Avoid
- **Setter-level guards on entities:** Masks bugs by silently clamping. D-01 explicitly forbids this.
- **Blanket `roundToInt()` replacement:** Each call site must match its specific PHP counterpart. Some sites should use `.toInt()` (truncation), others need `round()`.
- **Wrapping all divisions in phpIntdiv():** D-09 says add utility only if audit reveals actual negative-operand divisions.
- **Changing Int fields (gold, rice, crew, experience, dedication, dex1-5) to Short:** These are already Int for good reason -- they can exceed Short range.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bounds clamping | Custom min/max wrappers | `coerceIn(low, high)` | Kotlin stdlib; already used 265+ times in codebase |
| PHP-compatible rounding | Complex rounding logic | `kotlin.math.round().toInt()` + edge-case table | Matches PHP `round()` for most values; only .5 cases diverge |
| Float-to-int truncation | Custom truncation function | `.toInt()` | Kotlin `.toInt()` truncates toward zero, same as PHP `(int)` cast |
| Golden value testing | Custom test harness | `InMemoryTurnHarness` + `GoldenSnapshotTest` pattern | Already exists and proven in Phase 1 |

**Key insight:** The codebase already has the right tools (coerceIn, InMemoryTurnHarness, GoldenSnapshotTest pattern). The work is auditing where they are missing, not building new infrastructure.

## Common Pitfalls

### Pitfall 1: Math.round() Returns Long, Not Int
**What goes wrong:** `Math.round(Double)` returns `Long` in Java/Kotlin. Calling `.toInt()` on it can silently truncate values > Int.MAX_VALUE.
**Why it happens:** 13 call sites use `Math.round(value).toInt()` -- the `.toInt()` hides the Long-to-Int narrowing.
**How to avoid:** Replace all `Math.round(x).toInt()` with `kotlin.math.round(x).toInt()` which returns Double, then `.toInt()` truncates. For the game's value ranges (< 100,000) this is safe.
**Warning signs:** Any `Math.round` import (`java.lang.Math`).

### Pitfall 2: roundToInt() Uses Banker's Rounding (Half-to-Even)
**What goes wrong:** `Double.roundToInt()` rounds 0.5 to 0 (nearest even), 1.5 to 2 (nearest even), 2.5 to 2 (nearest even). PHP `round()` rounds 0.5 to 1, 1.5 to 2, 2.5 to 3 (half-away-from-zero).
**Why it happens:** Kotlin follows IEEE 754 "round half to even" by default.
**How to avoid:** For formulas where PHP uses `round()`, the difference matters only at exact .5 values. Build the golden comparison table (D-05) to identify which sites have .5 inputs. For most game arithmetic (large multiplications, random values), exact .5 is vanishingly rare. Focus on formulas with known .5 outputs (e.g., `value / 2`, `value * 0.5`).
**Warning signs:** `roundToInt()` on results of division by 2 or multiplication by 0.5.

### Pitfall 3: PHP (int) Cast vs PHP round() Confusion
**What goes wrong:** PHP has two different truncation behaviors: `(int)$x` truncates toward zero (like Kotlin `.toInt()`), while `round($x)` does half-away-from-zero. The codebase uses `.toInt()`, `.roundToInt()`, and `Math.round()` without documenting which PHP behavior each mirrors.
**Why it happens:** During initial port, different developers chose different Kotlin methods without a convention.
**How to avoid:** For each call site, determine whether the legacy PHP uses `(int)` cast or `round()`. Map:
  - PHP `(int)$x` --> Kotlin `.toInt()` (both truncate toward zero)
  - PHP `round($x)` --> Kotlin `kotlin.math.round(x).toInt()` (close but diverges at .5)
  - PHP `intdiv($a, $b)` --> Kotlin `a / b` (both truncate toward zero for same-sign operands)
  - PHP `floor($x)` --> Kotlin `floor(x).toInt()` (both round toward negative infinity)
  - PHP `ceil($x)` --> Kotlin `ceil(x).toInt()` (both round toward positive infinity)

### Pitfall 4: Short Overflow is Silent in Kotlin
**What goes wrong:** `(40000).toShort()` silently wraps to -25536 in Kotlin. No exception, no warning.
**Why it happens:** Kotlin `.toShort()` simply takes the low 16 bits. If the Int value exceeds -32768..32767, it wraps.
**How to avoid:** Always `coerceIn(MIN, MAX)` before `.toShort()`. The game bounds are well within Short range for individual fields, but arithmetic (e.g., `crew * train` can reach 110 * 10000 = 1,100,000) must stay as Int until the final assignment.
**Warning signs:** `.toShort()` without a preceding `coerceIn` or `coerceAtLeast`/`coerceAtMost`.

### Pitfall 5: Cumulative Drift From Repeated Small Errors
**What goes wrong:** A 1-value rounding error per turn in city population growth compounds to a noticeable divergence after 200 turns.
**Why it happens:** Economy processing runs every turn for every city. `(city.pop * 0.99).toInt()` vs `.roundToInt()` differs by 1 for populations divisible by 100. Over 200 turns across 40+ cities, this accumulates.
**How to avoid:** The 200-turn golden snapshot test (D-11) is specifically designed to catch this. Run it after every rounding normalization to verify no regression.
**Warning signs:** Changing any `.toInt()` or `.roundToInt()` in EconomyService without re-running the golden snapshot.

### Pitfall 6: Negative Gold Division Edge Case
**What goes wrong:** Nation/general gold can go negative (debt). `(-7) / 2` is `-3` in both Kotlin and PHP (truncation toward zero), but `(-7).toDouble() / 2` followed by `.toInt()` vs `.roundToInt()` can diverge.
**Why it happens:** Most game values are positive, but gold and some intermediate calculations can be negative.
**How to avoid:** D-10 requires documenting which fields can go negative. For those paths, verify division behavior with negative test cases.
**Warning signs:** Division operations on `nation.gold`, `general.gold`, or intermediate `delta` variables that could be negative.

## Code Examples

### Complete Short Field Inventory with Domain Bounds

```
Entity: General (30 Short fields)
  npcState:       -1..9      (NPC type: -1=dead, 0=player, 1..9=NPC types)
  affinity:       0..150     (compatibility index)
  bornYear:       0..32767   (year values)
  deadYear:       0..32767   (year values)
  imageServer:    0..3       (server index)
  leadership:     0..100     (base stat)
  leadershipExp:  0..1000    (stat exp, may need wider bounds check)
  strength:       0..100     (base stat)
  strengthExp:    0..1000
  intel:          0..100     (base stat)
  intelExp:       0..1000
  politics:       0..100     (base stat, opensamguk extension)
  politicsExp:    0..1000
  charm:          0..100     (base stat, opensamguk extension)
  charmExp:       0..1000
  injury:         0..80      (existing guard in ItemService)
  officerLevel:   0..20      (0=wanderer, 20=ruler)
  crewType:       0..50      (troop type code)
  train:          0..110     (existing guard in ItemService)
  atmos:          0..150     (existing guard in ItemService)
  newmsg:         0..1       (boolean-like flag)
  makeLimit:      0..Short.MAX_VALUE  (crafting limit counter)
  killTurn:       nullable, -32768..32767  (countdown, can be negative during processing)
  blockState:     0..2       (block flag)
  dedLevel:       0..30      (existing calcDedLevel guard)
  expLevel:       0..255     (existing calcExpLevel guard)
  age:            0..120     (human age)
  startAge:       0..120
  belong:         0..12      (loyalty timer)
  betray:         0..10      (betrayal counter)
  specAge:        0..100     (special ability age)
  spec2Age:       0..100
  defenceTrain:   0..100     (defense training)
  tournamentState:0..3       (tournament participation)

Entity: City (6 Short fields)
  level:          0..5       (city level)
  supplyState:    0..1       (supply line status)
  frontState:     0..2       (front line status)
  state:          0..10      (city state code)
  region:         0..20      (region code)
  term:           0..Short.MAX_VALUE  (timer)

Entity: Nation (10 Short fields)
  bill:           0..200     (tax rate percentage)
  rate:           0..100     (trade rate)
  rateTmp:        0..100     (temporary rate)
  secretLimit:    0..20      (secret command limit)
  scoutLevel:     0..5       (scouting level)
  warState:       0..3       (war status flag)
  strategicCmdLimit: 0..72   (strategic command cooldown)
  surrenderLimit: 0..120     (surrender cooldown)
  level:          0..9       (nation level from NATION_LEVEL_THRESHOLDS)

Entity: Diplomacy (1 Short field)
  term:           0..Short.MAX_VALUE  (diplomacy timer)

Entity: Emperor (2 Short fields)
  year:           0..32767   (game year)
  month:          1..12      (game month)

Entity: WorldState (3 Short fields)
  id:             auto-increment (no arithmetic)
  currentYear:    0..32767   (game year)
  currentMonth:   1..12      (game month)

Entity: GeneralTurn, NationTurn (1-2 Short fields each)
  turnIdx:        0..20      (turn queue index)
  officerLevel:   0..20      (NationTurn only)

Entity: Event (1 Short field)
  priority:       0..100     (event priority)

Entity: YearbookHistory, WorldHistory, TrafficSnapshot (2 Short fields each)
  year/month:     same as WorldState

Entity: Tournament (3 Short fields)
  round:          0..10      (tournament round)
  bracketPosition: 0..64     (bracket slot)
  result:         0..3       (match result)

Entity: VoteCast (1 Short field)
  optionIdx:      0..10      (vote option)
```

### Rounding Method Comparison Table

```
Value    PHP (int)   PHP round()   Kotlin .toInt()   Kotlin roundToInt()   Kotlin Math.round().toInt()
------   ---------   -----------   ----------------  -------------------   --------------------------
 2.3        2           2              2                   2                        2
 2.5        2           3              2                   2 (BANKER'S!)            3 (Long->Int)
 2.7        2           3              2                   3                        3
 3.5        3           4              3                   4 (BANKER'S!)            4
-2.3       -2          -2             -2                  -2                       -2
-2.5       -2          -3             -2                  -2 (BANKER'S!)           -2 (DIFFERS from PHP!)
-2.7       -2          -3             -2                  -3                       -3
 0.5        0           1              0                   0 (BANKER'S!)            1

Key divergences at .5 boundaries:
  - PHP round(2.5) = 3, Kotlin roundToInt(2.5) = 2, Math.round(2.5) = 3
  - PHP round(-2.5) = -3, Kotlin roundToInt(-2.5) = -2, Math.round(-2.5) = -2
  - For PHP (int) cast, Kotlin .toInt() is the exact match (both truncate toward zero)
```

### Rounding Call Site Summary

```
Method                    Count   Files  PHP Equivalent          Match?
-----------------------   -----   -----  ---------------------   ------
.toInt()                  709     43     PHP (int) cast          YES -- exact match
.roundToInt()             90+     28     PHP round() (mostly)    PARTIAL -- diverges at .5
Math.round().toInt()      13      6      PHP round() (mostly)    PARTIAL -- diverges at -.5
kotlin.math.round().toInt() 1     1      PHP round()             PARTIAL -- same as roundToInt issue
ceil().toInt()            ~10     5      PHP ceil()              YES -- exact match
floor().toInt()           ~8      7      PHP floor()             YES -- exact match
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x (via spring-boot-starter-test) |
| Config file | `backend/build.gradle.kts` (useJUnitPlatform) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew test` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TYPE-01 | Short field coerceIn guards prevent overflow | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.NumericOverflowGuardTest" -x :gateway-app:test` | Wave 0 |
| TYPE-01 | Domain bounds match legacy PHP behavior | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.ShortFieldBoundsTest" -x :gateway-app:test` | Wave 0 |
| TYPE-02 | Rounding parity: PHP round() vs Kotlin | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.RoundingParityTest" -x :gateway-app:test` | Wave 0 |
| TYPE-02 | 200-turn economic simulation golden snapshot | integration | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.NumericParityGoldenTest" -x :gateway-app:test` | Wave 0 |
| TYPE-03 | Integer division negative operand verification | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.IntegerDivisionParityTest" -x :gateway-app:test` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/NumericOverflowGuardTest.kt` -- covers TYPE-01 overflow guards
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt` -- covers TYPE-02 rounding edge cases
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/IntegerDivisionParityTest.kt` -- covers TYPE-03 division verification
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/NumericParityGoldenTest.kt` -- covers TYPE-02 cumulative drift (extends GoldenSnapshotTest pattern)

### Existing Test Assets (reuse, not recreate)
- `GoldenSnapshotTest.kt` -- 1-turn golden snapshot with InMemoryTurnHarness (extend to 200 turns)
- `GoldenValueTest.kt` -- Command formula golden values (add numeric edge cases)
- `EconomyFormulaParityTest.kt` -- Economy formula parity with PHP (add rounding edge cases)
- `FormulaParityTest.kt` -- Battle/economy formula verification (add division edge cases)
- `InMemoryTurnHarness.kt` -- In-memory turn processing harness (use for 200-turn simulation)
- `LiteHashDRBGTest.kt` -- Golden value test pattern (reference for test structure)

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Unguarded `.toShort()` everywhere | `coerceIn().toShort()` at domain boundaries | Established by ItemService.kt (existing) | Prevents silent wrap-around; 265 existing guards, but many gaps |
| Mix of Math.round / roundToInt / .toInt | Each site explicitly matched to PHP equivalent | This phase | Eliminates cumulative rounding drift |
| No golden snapshot for numeric parity | 200-turn golden snapshot test | This phase (extends existing GoldenSnapshotTest) | Catches compound divergence |

**Deprecated/outdated:**
- `Math.round(value).toInt()`: Should be replaced with `kotlin.math.round(value).toInt()` to avoid unnecessary Long intermediate. Both have the same rounding behavior for the game's value ranges but the kotlin.math version avoids the Long->Int narrowing risk.

## Open Questions

1. **PHP rounding at exact .5 values -- frequency in practice**
   - What we know: PHP `round()` and Kotlin `roundToInt()` diverge at .5 boundaries. The golden comparison table is specified (D-05).
   - What's unclear: How often do game formulas actually produce exact .5 values? For most formulas involving random numbers and large multiplications, exact .5 is extremely rare.
   - Recommendation: Build the comparison table, but prioritize the formulas that are most likely to produce .5 values (division by 2, multiplication by 0.5). For formulas involving random values, the .5 divergence is negligible.

2. **InMemoryTurnHarness currently mocks EconomyService**
   - What we know: The existing `InMemoryTurnHarness` uses `mock(EconomyService::class.java)` -- economy is not actually processed during harness turns.
   - What's unclear: Can we inject a real EconomyService into the harness, or does it need significant wiring changes?
   - Recommendation: For the 200-turn golden test, either: (a) extend InMemoryTurnHarness to accept a real EconomyService, or (b) create a standalone simulation that drives EconomyService.processSemesterIncome() directly in a loop. Option (b) is simpler and aligns with "surgical changes."

3. **Legacy-core/ PHP source not present in working directory**
   - What we know: `legacy-core/` is referenced in CLAUDE.md as parity target but the directory appears empty or missing on this machine.
   - What's unclear: Whether the planner/implementer will have access to legacy PHP source for formula-by-formula comparison.
   - Recommendation: Use the existing parity test files (EconomyFormulaParityTest, BattleEngineParityTest) as the source of truth for PHP behavior. These already encode PHP formula expectations as golden values.

## Project Constraints (from CLAUDE.md)

- **Parity target**: `legacy-core/` PHP source is the single source of truth
- **Field naming**: Must follow core conventions (intel, crew, crewType, train, atmos, etc.)
- **TDD Gate**: Backend source changes must be accompanied by backend test changes (pre-commit hook)
- **Simplicity First**: Minimum code that solves the problem. No speculative abstractions.
- **Surgical Changes**: Touch only what is needed. Don't "improve" adjacent code.
- **Goal-Driven Execution**: Transform tasks into verifiable goals with specific checks.
- **Architecture**: Must maintain gateway-app + game-app split
- **DB**: PostgreSQL with SMALLINT columns mapped to Kotlin Short

## Sources

### Primary (HIGH confidence)
- Entity source files: `General.kt`, `City.kt`, `Nation.kt`, `Diplomacy.kt`, `Emperor.kt`, `GeneralTurn.kt`, `WorldState.kt` -- direct field inventory
- `ItemService.kt` lines 127-139 -- existing coerceIn guard pattern
- `GeneralMaintenanceService.kt` lines 285-286 -- existing scaledStatWithFloor pattern
- `EconomyService.kt` -- 82 `.toInt()` calls, 25 coerce guards (counted via grep)
- `GoldenSnapshotTest.kt`, `GoldenValueTest.kt` -- existing golden value test patterns
- `InMemoryTurnHarness.kt` -- existing in-memory turn processing infrastructure
- `EconomyFormulaParityTest.kt` -- existing PHP formula parity verification
- `build.gradle.kts` -- test framework configuration (JUnit Jupiter, no jqwik)

### Secondary (MEDIUM confidence)
- PHP rounding behavior: `round()` uses `PHP_ROUND_HALF_UP` by default (half-away-from-zero). Verified via PHP documentation.
- Kotlin `roundToInt()` behavior: IEEE 754 "round half to even" (banker's rounding). Verified via Kotlin stdlib documentation.
- Java `Math.round(double)` behavior: `(long)Math.floor(a + 0.5d)` -- rounds half-up for positive values. Verified via JDK documentation.

### Tertiary (LOW confidence)
- Domain bounds for Short fields: Derived from game logic observation and existing guards. Must be verified against legacy PHP source for each field individually during implementation.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project, patterns already established
- Architecture: HIGH - Entity fields and service patterns are concrete and auditable
- Pitfalls: HIGH - Rounding divergence behaviors are well-documented in language specifications
- Domain bounds: MEDIUM - Bounds derived from code observation, need legacy PHP verification per field

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable domain -- numeric behavior is language-level, not library-version dependent)
