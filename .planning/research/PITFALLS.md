# Domain Pitfalls: PHP-to-Kotlin Legacy Parity Verification

**Domain:** Three Kingdoms strategy game -- PHP (devsam/core) to Kotlin (Spring Boot) port
**Researched:** 2026-03-31
**Confidence:** HIGH (based on direct codebase analysis of 200+ source files)

---

## Critical Pitfalls

Mistakes that cause silent behavioral divergence, wrong game outcomes, or require rewrites.

---

### Pitfall 1: Float-to-Int Truncation Accumulation (The 0.99 Decay Problem)

**What goes wrong:** PHP's `intval()` and Kotlin's `.toInt()` both truncate toward zero, but the accumulated rounding error over many turns produces different game states because the intermediate precision paths differ.

**Why it happens:** The codebase has 100+ instances of `(value * factor).toInt()` in `EconomyService.kt` alone. PHP performs all arithmetic in 64-bit float (C `double`), then truncates. Kotlin does the same, but the operator precedence and intermediate widening can differ subtly. When `city.agri` is a Kotlin `Int` (32-bit), the expression `(city.agri * 0.99).toInt()` promotes to Double then truncates. PHP's `$agri` is a native PHP integer (64-bit on 64-bit systems), so `intval($agri * 0.99)` may preserve different precision at the boundaries.

**Concrete evidence in codebase:**
- `EconomyService.kt:282-286` -- Five city stats decay by `* 0.99` every semi-annual tick
- `EconomyService.kt:339-347` -- General gold/rice decay by `* 0.97` or `* 0.99`
- `EconomyService.kt:355-364` -- Nation treasury decay by `* 0.95`, `* 0.97`, or `* 0.99`
- `EconomyService.kt:487-499` -- Disaster damage: `* 0.9` for cities, `* 0.95` for generals

**Consequences:** After 100 turns, a city with `agri=10000` decays differently by 1-3 units between PHP and Kotlin due to accumulated truncation. This compounds across all cities (60+ per map) and all stats (5 per city). Over a full game (500+ turns), the economic state diverges noticeably.

**Prevention:**
1. Write parity tests that run the same decay sequence for 200 iterations on both PHP and Kotlin and compare final values
2. For each `(x * factor).toInt()` pattern, verify whether the legacy PHP uses `intval()`, `floor()`, `round()`, or implicit cast -- they are NOT interchangeable
3. Consider using `kotlin.math.truncate()` explicitly to document intent

**Detection:** Compare economy snapshots (city stats, general resources, nation treasury) between legacy and Kotlin after 50+ turn simulations. Any drift > 0 is a bug.

**Phase:** Command/Economy parity verification phase

---

### Pitfall 2: Short (SMALLINT) Overflow in Kotlin Entity Fields

**What goes wrong:** PHP uses unbounded integers. The Kotlin entities use `Short` (range -32768 to 32767) for many fields that PHP treats as regular integers. Arithmetic on these fields can silently overflow or wrap around.

**Why it happens:** The database schema maps many columns to `SMALLINT` (correct for storage), and the Kotlin entities mirror this as `Short`. But PHP never has this constraint -- its integer type is 64-bit. When Kotlin code does arithmetic like `(g.betray + cnt).toShort()` or `(general.atmos * 0.95).toInt().toShort()`, if the intermediate result exceeds Short range, `.toShort()` silently truncates the high bits.

**Concrete evidence in codebase:**
- `General.kt` -- 30+ fields typed as `Short`: leadership, strength, intel, politics, charm, train, atmos, injury, age, belong, betray, officerLevel, crewType, dedLevel, expLevel, etc.
- `EconomyService.kt:498-499` -- `general.atmos = (general.atmos * 0.95).toInt().toShort()` -- safe for atmos (max 100) but the pattern is replicated without guards
- `EventActionService.kt:44` -- `g.betray = (g.betray + cnt).toShort()` -- betray can increment unboundedly
- `EventActionService.kt:699` -- `g.age = (g.age + 1).toShort()` -- age increments each year
- `NationAI.kt:188-189` -- `nation.rateTmp = newRateTmp.toShort()` and `nation.bill = newBill.toShort()`

**Consequences:** A general with `betray` that reaches 32768 wraps to -32768. The game logic checks `betray > X` for betrayal penalties -- a wrapped negative value would bypass all checks. Similarly, if `age` exceeds 32767 (impossible in practice but the pattern is dangerous for copied code).

**Prevention:**
1. Add `coerceIn()` before every `.toShort()` conversion: `(value).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()`
2. For fields with known game-logic bounds (atmos 0-100, train 0-110, injury 0-80), assert the bound: `(general.injury + amount).coerceAtMost(80).toShort()`
3. Audit every `.toShort()` call site (30+ in engine code) for overflow risk

**Detection:** Grep for `.toShort()` without a preceding `coerceIn` or `coerceAtMost`. Any bare `.toShort()` on an arithmetic expression is suspect.

**Phase:** Entity/type parity verification phase

---

### Pitfall 3: Non-deterministic RNG Breaking Reproducibility

**What goes wrong:** Two code paths use `java.util.Random()` (unseeded) instead of the deterministic `LiteHashDRBG`. Game outcomes from these paths are non-reproducible and cannot be parity-tested against PHP.

**Why it happens:** The project correctly implemented `LiteHashDRBG` (SHA-512-based DRBG) and `DeterministicRng` to match the legacy PHP's seeded randomness. But two locations bypassed this system, likely during rapid development.

**Concrete evidence in codebase:**
- `TurnService.kt:1050` -- `val rng = java.util.Random()` in `registerAuction()` -- auction amounts and timing are non-deterministic
- `GeneralTrigger.kt:200` -- `private val rng: java.util.Random = java.util.Random()` -- trigger outcomes (which affect gameplay) are non-deterministic
- Meanwhile, `GeneralAI.kt`, `BattleEngine.kt`, and `RandUtil.kt` correctly use `LiteHashDRBG`

**Consequences:**
1. Auction registration cannot be replay-tested -- same world state produces different auctions
2. General trigger outcomes (stat changes, special ability procs) vary between runs
3. Parity tests against PHP are impossible for these code paths since the RNG sequences differ
4. Debugging turn outcomes requires reproducing exact timing, which is impossible with unseeded RNG

**Prevention:**
1. Replace both `java.util.Random()` instances with `DeterministicRng.create(hiddenSeed, "auction", worldId, turnNumber)` and similar
2. Add a lint rule or code review checklist item: "no `java.util.Random` in game logic"
3. The pattern is already established -- `GeneralAI.kt` uses `DeterministicRng.create()` extensively

**Detection:** Grep for `java.util.Random` in game-app. Any hit outside test code is a bug.

**Phase:** Should be fixed immediately -- blocks all parity verification for affected paths

---

### Pitfall 4: PHP Array Ordering vs Kotlin Map Ordering

**What goes wrong:** PHP arrays maintain insertion order. Kotlin's `HashMap` does not. When game logic iterates over grouped entities (nations, cities, generals) and the iteration order affects outcomes (e.g., who gets processed first in resource distribution), the results diverge.

**Why it happens:** PHP's `array` is always an ordered map. In Kotlin, `groupBy {}` and `associateBy {}` return `LinkedHashMap` (insertion-ordered), BUT the insertion order depends on the order of the input list, which in turn depends on the database query order. JPA's `findAll()` returns results in database-internal order, which differs between MariaDB and PostgreSQL.

**Concrete evidence in codebase:**
- `EconomyService.kt:128-136` -- `cities.groupBy { it.nationId }`, `generals.groupBy { it.nationId }`, `generals.groupBy { it.cityId }` -- iteration order determines which nation processes income first
- `EconomyService.kt:257` -- `nations.associateBy { it.id }` -- used for lookups (safe) but also for iteration
- `GeneralAI.kt:1043-1045` -- `sortedByDescending { it.gold + it.rice }` -- sorting makes this safe, but only when explicitly sorted
- `TurnService.kt:424` -- `ports.allGenerals().map { ... }.sortedBy { it.turnTime }` -- correctly sorted for turn processing

**Consequences:** When two nations with equal city counts are processed, PHP processes them in the order they were inserted into the database. PostgreSQL may return them in a different order. If income processing has side effects that depend on order (e.g., shared resource pools, market prices), results diverge.

**Prevention:**
1. Always add explicit `.sortedBy {}` after `groupBy {}` when iteration order matters
2. For entity iteration, sort by a deterministic key (entity ID, name, or creation time)
3. Verify that `allCities()`, `allGenerals()`, `allNations()` return entities in the same order as legacy PHP queries (typically by primary key ASC)
4. Test: Run economy processing twice with same input but different HashMap insertion order. If results differ, the code has an ordering dependency.

**Detection:** Review every `groupBy`/`associateBy` usage where the resulting map is iterated (not just looked up). If the iteration body has side effects or accumulates state, ordering matters.

**Phase:** Economy/turn parity verification phase

---

### Pitfall 5: MariaDB vs PostgreSQL Integer Division and Type Casting

**What goes wrong:** Legacy PHP used MariaDB, which performs implicit integer-to-float conversion in division. PostgreSQL does integer division by default (`5 / 2 = 2`, not `2.5`). Any raw SQL or JPA query ported from the legacy that relies on division will produce different results.

**Why it happens:** MariaDB's `/` operator returns a DECIMAL/DOUBLE result even for integer operands. PostgreSQL's `/` between integers returns an integer (truncated toward zero). The Kotlin codebase uses JPA/Hibernate with PostgreSQL, so any `@Query` annotations or native SQL must account for this.

**Concrete evidence in codebase:**
- `V26__create_records_table.sql:25-26` -- Uses `COALESCE((payload->>'year')::int, 0)` -- PostgreSQL-specific JSON casting (correct, but shows SQL is hand-written)
- Most game logic is in Kotlin (not SQL), which avoids this pitfall -- but any future migration of complex PHP SQL queries to native `@Query` will hit it
- MariaDB's `GROUP_CONCAT` is `STRING_AGG` in PostgreSQL
- MariaDB's `IFNULL` is `COALESCE` in PostgreSQL (already used correctly)

**Additional SQL dialect differences to watch:**
- MariaDB `REPLACE INTO` = PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`
- MariaDB boolean: `0/1` integers vs PostgreSQL native `BOOLEAN`
- MariaDB `AUTO_INCREMENT` vs PostgreSQL `GENERATED ALWAYS AS IDENTITY` or `SERIAL`
- MariaDB `LIMIT offset, count` vs PostgreSQL `LIMIT count OFFSET offset`
- MariaDB case-insensitive string comparison (default collation) vs PostgreSQL case-sensitive

**Consequences:** A ported SQL query that divides integer columns (`SELECT gold / count FROM ...`) silently produces wrong results. Multiplied across all turn-processing queries, this causes economic drift.

**Prevention:**
1. For any raw SQL: cast one operand to `DOUBLE PRECISION` or `NUMERIC` before division: `CAST(gold AS DOUBLE PRECISION) / count`
2. Prefer Kotlin-side arithmetic over SQL-side arithmetic for game logic
3. When porting legacy PHP SQL queries, run them on both MariaDB and PostgreSQL with the same data and compare results
4. Use JPA criteria queries or Kotlin code instead of native SQL wherever possible

**Detection:** Grep for `@Query` annotations containing `/` (division) on integer columns.

**Phase:** Any phase that adds new SQL queries or ports legacy PHP SQL

---

### Pitfall 6: PHP's `intval()` on String vs Kotlin's Strict Parsing

**What goes wrong:** PHP's `intval("123abc")` returns `123`. Kotlin's `"123abc".toInt()` throws `NumberFormatException`. PHP's `intval("")` returns `0`. Kotlin's `"".toInt()` throws. PHP's `intval(null)` returns `0`. Kotlin's `null?.toInt()` is `null`.

**Why it happens:** PHP is dynamically typed and coerces strings to numbers by extracting the leading numeric portion. Kotlin is strictly typed. When legacy game data (JSON payloads, scenario files, query parameters) contains mixed-type values, the conversion behavior diverges.

**Concrete evidence in codebase:**
- `EventActionService.kt:642-656` -- `(it.meta["generalId"] as? Number)?.toLong() ?: 0L` -- defensive casting with fallback, correct pattern
- `EventActionService.kt:778` -- `(params["npcType"] as? Number)?.toShort() ?: 2` -- fallback to default, correct pattern
- `EventService.kt:56-57` -- `(condition["year"] as? Number)?.toShort() ?: return false` -- early return on missing, correct pattern
- The codebase generally handles this well, but any NEW code that parses legacy data must follow the same defensive pattern

**Consequences:** If a scenario JSON file has `"leadership": "95"` (string) instead of `"leadership": 95` (number), PHP reads it fine via `intval()`. Kotlin's `as? Number` returns null, and the fallback may assign a wrong default value.

**Prevention:**
1. Always use the established pattern: `(value as? Number)?.toXxx() ?: defaultValue`
2. For string values that might contain numbers: `value?.toString()?.toIntOrNull() ?: defaultValue`
3. Validate all scenario JSON files at load time -- throw if required fields are missing rather than silently defaulting
4. Add JSON schema validation for scenario data files

**Detection:** Grep for `as? Number` without a `?: ` fallback. Grep for `.toInt()` or `.toLong()` without `OrNull` when parsing external data.

**Phase:** Scenario/data parity verification phase

---

## Moderate Pitfalls

---

### Pitfall 7: PHP `(int)` Cast vs Kotlin `.toInt()` on Negative Floats Near Zero

**What goes wrong:** Both PHP and Kotlin truncate toward zero, so `(int)(-0.7)` = `0` in both. But PHP's `floor()` returns `-1.0` while Kotlin's `floor(-0.7)` also returns `-1.0`. The pitfall is when ported code uses `.toInt()` where the legacy used `floor()` or vice versa -- they differ for negative numbers.

**Why it happens:** The codebase mixes `floor()`, `ceil()`, `round()`, and bare `.toInt()` across different formula files. In `EconomyService.kt`, most conversions use `.toInt()` (truncation). In `BattleTrigger.kt` and `ItemModifiers.kt`, `floor()` is used. If the legacy PHP used `floor()` for a formula but the Kotlin port uses `.toInt()`, negative intermediate values produce different results.

**Concrete evidence:**
- `EconomyService.kt:168` -- `(totalGoldIncome * taxRate / 20).toInt()` -- truncation
- `TurnService.kt:1218` -- `kotlin.math.floor(accessLog.refreshScoreTotal * 0.99).toInt()` -- explicit floor
- `BattleTrigger.kt` -- uses `floor()` for damage calculations
- `WarAftermath.kt` -- uses both `floor()` and `round()`

**Prevention:**
1. For each formula, verify: does PHP use `intval()` (truncate), `floor()`, `ceil()`, or `round()`?
2. Document the conversion function used per formula in a comment
3. Test with negative intermediate values (possible when tax rate > 20, causing negative growth ratios)

**Detection:** Find formulas where the intermediate value can be negative and check which rounding/truncation function is used.

**Phase:** Economy/battle formula parity verification

---

### Pitfall 8: Exception Swallowing Hiding Parity Bugs

**What goes wrong:** Broad `catch (_: Exception)` blocks silently suppress errors that would reveal parity bugs. A `NumberFormatException`, `ArithmeticException`, or `NullPointerException` during turn processing is swallowed, and the turn completes with wrong state.

**Why it happens:** PHP's error handling is permissive -- many operations that throw in Kotlin just return `null` or `0` in PHP. The Kotlin port added catch-all blocks to mimic PHP's permissiveness, but this hides genuine bugs.

**Concrete evidence in codebase:**
- 20+ instances of `catch (_: Exception)` in engine code alone (see CONCERNS.md)
- `ScenarioService.kt:140,347` -- map load failure silently falls back to "che" map
- `GeneralAI.kt:114` -- entire AI decision silently fails
- `EconomyService.kt:462,680` -- economy processing silently fails
- `EventService.kt:79` -- event processing silently fails
- `EventActionService.kt:841` -- event action silently fails

**Consequences:** A parity bug in an economy formula throws an exception, which is swallowed. The city's income is silently zero for that turn. The game continues with wrong state, but no error is logged. Diagnosing the divergence requires removing catch blocks to find the source.

**Prevention:**
1. Add `logger.error("...", e)` to every catch block BEFORE suppressing
2. In development/testing, configure catch blocks to rethrow
3. Replace `catch (_: Exception)` with specific exception types where possible
4. Add metrics/counters for suppressed exceptions so silent failures are visible

**Detection:** Run turn processing with full DEBUG logging and count error/warn messages. Any suppressed exception is a potential parity bug.

**Phase:** Should be addressed before parity verification begins -- otherwise parity bugs are invisible

---

### Pitfall 9: `city.dead` as SMALLINT Overflow on Large Battles

**What goes wrong:** `city.dead` is typed as `SMALLINT` in the database (V1 migration, line 76) which means max value 32767. In large battles, dead counts can exceed this. PHP's unbounded integers handle this; PostgreSQL's SMALLINT silently truncates or throws.

**Why it happens:** Legacy MariaDB may have also used SMALLINT but PHP never noticed because the value was held in PHP memory as a regular integer and only written to the DB when it fit. The Kotlin entity mirrors the DB type as `Short` or `Int`.

**Concrete evidence:**
- `V1__core_tables.sql:76` -- `dead SMALLINT NOT NULL DEFAULT 0`
- `EconomyService.kt:260-268` -- `city.dead` is read and used for war income calculation: `nation.gold += (city.dead / 10)`. Large battles could generate dead > 32767.

**Prevention:**
1. Verify the actual maximum `dead` value possible from battle calculations
2. If it can exceed 32767, migrate to `INTEGER`
3. Add `coerceAtMost(Short.MAX_VALUE.toInt())` before storing

**Detection:** Log or assert on `city.dead` values after battle resolution.

**Phase:** Battle parity verification phase

---

### Pitfall 10: Turn Processing Order Sensitivity

**What goes wrong:** The order in which generals execute their turns within a single game tick affects outcomes (a general who moves first gets the resource/city first). If PHP and Kotlin sort generals differently for turn execution, the entire game diverges.

**Why it happens:** The codebase sorts generals by `turnTime` (`TurnService.kt:424`, `InMemoryTurnProcessor.kt:210`). But what happens when multiple generals have the same `turnTime`? In PHP, the tiebreaker depends on the DB query's implicit ordering (typically by primary key in MariaDB). In Kotlin, `sortedBy { it.turnTime }` uses a stable sort, but the pre-sort order depends on JPA's result set ordering from PostgreSQL.

**Concrete evidence:**
- `TurnService.kt:424` -- `ports.allGenerals().map { it.toEntity() }.sortedBy { it.turnTime }` -- sorts by turnTime only
- `InMemoryTurnProcessor.kt:210` -- `state.generals.values.sortedBy { it.turnTime }` -- same pattern
- No secondary sort key (e.g., `.sortedBy { it.turnTime }.thenBy { it.id }`) to break ties deterministically

**Prevention:**
1. Add a deterministic tiebreaker to all general-ordering sorts: `.sortedWith(compareBy<General> { it.turnTime }.thenBy { it.id })`
2. Verify the legacy PHP's tiebreaker (likely database primary key order)
3. Write tests with multiple generals having identical `turnTime` and verify execution order matches legacy

**Detection:** In test scenarios, create 3+ generals with identical `turnTime` and verify processing order matches legacy PHP behavior.

**Phase:** Turn engine parity verification phase

---

### Pitfall 11: LiteHashDRBG Byte-Order and Bit-Width Parity

**What goes wrong:** The custom `LiteHashDRBG` implements SHA-512-based deterministic RNG to match the legacy PHP implementation. Any byte-order mismatch (little-endian vs big-endian in seed construction), bit-width difference (53-bit max), or off-by-one in the rejection sampling loop causes the entire RNG sequence to diverge.

**Why it happens:** The implementation is a custom cross-language RNG protocol. Both the PHP and Kotlin sides must produce identical byte sequences for identical seeds. The Kotlin side uses `ByteOrder.LITTLE_ENDIAN` explicitly (`LiteHashDRBG.kt:27`), which must match the PHP side's `pack('V', $stateIdx)` (little-endian unsigned 32-bit).

**Concrete evidence:**
- `LiteHashDRBG.kt:26-31` -- Seed construction: `seed.toByteArray(UTF_8) + LE_INT32(stateIdx)` then SHA-512
- `LiteHashDRBG.kt:102-125` -- `nextLegacyInt()` uses rejection sampling with bit masks
- `LiteHashDRBG.kt:127-135` -- `nextFloat1()` divides by `2^53` for [0,1] range
- `RandUtil.kt:56-57` -- `shuffle()` uses Fisher-Yates with `nextLegacyInt((cnt - srcIdx - 1).toLong())` -- if the range calculation is off by 1, every shuffle diverges

**Consequences:** If the RNG sequence diverges even by one call, ALL subsequent random decisions (battle outcomes, NPC AI choices, event triggers) diverge. This is the single highest-amplification parity bug possible.

**Prevention:**
1. Write cross-language unit tests: generate 10000 values from both PHP and Kotlin with the same seed, compare every single value
2. Test edge cases: `nextLegacyInt(0)`, `nextLegacyInt(1)`, `nextLegacyInt(MAX_INT)`
3. Test `nextFloat1()` boundary: verify both PHP and Kotlin return values in exactly [0.0, 1.0]
4. Test `shuffle()` with known inputs and verify identical output order
5. Never modify `LiteHashDRBG` without running the full cross-language test suite

**Detection:** Run the same seed through both implementations and compare the first 1000 outputs. Any difference indicates a parity break.

**Phase:** Foundation -- must be verified before any other game logic parity testing

---

## Minor Pitfalls

---

### Pitfall 12: String Encoding and Comparison (Korean Text)

**What goes wrong:** PHP string comparison is byte-based by default. Kotlin string comparison is Unicode-aware. Korean characters in NFC vs NFD normalization compare as equal in Kotlin but may not in PHP (or vice versa, depending on PHP version and locale settings).

**Prevention:** Normalize all Korean strings to NFC before comparison. Verify that scenario file encoding (UTF-8) is consistent between legacy and Kotlin data files.

**Phase:** Scenario data parity

---

### Pitfall 13: Timestamp Precision (PHP `time()` vs Kotlin `OffsetDateTime`)

**What goes wrong:** PHP's `time()` returns seconds since epoch (integer). Kotlin uses `OffsetDateTime` with nanosecond precision. When game logic compares timestamps or calculates durations, the precision difference can cause off-by-one-second errors.

**Concrete evidence:** All entities use `OffsetDateTime` (`General.kt:154-157`, `Auction.kt:59-62`). Legacy PHP uses integer timestamps. Turn timing calculations that check "has enough time passed" may trigger one tick early or late.

**Prevention:** When porting time-comparison logic, truncate to seconds: `turnTime.truncatedTo(ChronoUnit.SECONDS)`.

**Phase:** Turn engine parity

---

### Pitfall 14: PHP `array_rand()` vs Kotlin `RandUtil.choice()` Selection Bias

**What goes wrong:** PHP's `array_rand()` returns a random key (0-based index). The Kotlin `RandUtil.choice()` implementation uses `nextLegacyInt((items.size - 1).toLong())` which generates values in `[0, size-1]` inclusive. This matches PHP's behavior. However, if `items.size == 1`, this calls `nextLegacyInt(0)` which returns `0` -- correct, but it still consumes an RNG value, which PHP's `array_rand()` on a single-element array does NOT (PHP returns the only key without calling `mt_rand`).

**Consequences:** After a `choice()` call on a single-element list, the Kotlin RNG has advanced one position while PHP's has not. All subsequent random values diverge.

**Prevention:** Add a fast path in `RandUtil.choice()`: `if (items.size == 1) return items[0]` without calling `nextLegacyInt()`. Verify this matches the legacy PHP behavior.

**Detection:** Trace RNG call counts for identical game scenarios. Any divergence in call count indicates this issue.

**Phase:** RNG parity verification (foundational)

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|---|---|---|
| RNG Foundation | #11 LiteHashDRBG byte-order, #14 choice() on single element | Cross-language unit tests with identical seeds |
| Turn Engine | #10 General ordering tiebreaker, #3 Non-deterministic RNG | Add secondary sort key, replace java.util.Random |
| Economy Formulas | #1 Float truncation accumulation, #4 Map iteration order | Multi-turn simulation comparison tests |
| Battle Resolution | #1 Float truncation in damage, #9 SMALLINT overflow on dead | Snapshot-based battle replay comparison |
| NPC AI | #3 Non-deterministic triggers, #4 Decision iteration order | Deterministic RNG for all AI paths, sorted inputs |
| Scenario Loading | #6 String-to-number coercion, #12 Korean text encoding | Schema validation, NFC normalization |
| Entity/Type Layer | #2 Short overflow, #5 SQL dialect differences | Audit all .toShort() calls, test boundary values |
| Command Parity (93 commands) | #1 Formula truncation, #8 Exception swallowing | Per-command snapshot tests, remove catch-alls in dev |
| All Phases | #8 Exception swallowing hiding bugs | Enable strict error logging before parity testing |

## Priority Order for Fixing

1. **Immediate** (blocks all parity work):
   - #3 Replace `java.util.Random()` -- 2 locations, 5 minutes
   - #8 Add logging to catch blocks -- systematic but quick
   - #11 Verify LiteHashDRBG cross-language parity -- foundational

2. **Before economy/battle parity**:
   - #1 Audit all `.toInt()` truncation patterns against legacy `intval()`/`floor()`/`round()`
   - #2 Audit all `.toShort()` for overflow guards
   - #10 Add tiebreaker to general sort ordering

3. **Before full game simulation**:
   - #4 Verify iteration order for all `groupBy`/`associateBy` in turn processing
   - #14 Verify `RandUtil.choice()` single-element behavior

4. **Ongoing**:
   - #5 SQL dialect review for any new native queries
   - #6 Defensive parsing for any new data ingestion
   - #13 Timestamp precision for any new time-comparison logic

## Sources

- Direct codebase analysis of `EconomyService.kt`, `BattleEngine.kt`, `LiteHashDRBG.kt`, `RandUtil.kt`, `TurnService.kt`, `GeneralTrigger.kt`, `General.kt`, `WarUnitGeneral.kt`, `EventActionService.kt`, and 30+ additional engine files
- [PHP intval() Manual](https://www.php.net/manual/en/function.intval.php)
- [Kotlin Number Types Documentation](https://kotlinlang.org/docs/numbers.html)
- [Kotlin truncate() API](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.math/truncate.html)
- [PHP Floating Point Numbers Manual](https://www.php.net/manual/en/language.types.float.php)
- [MariaDB Type Conversion Documentation](https://mariadb.com/kb/en/type-conversion/)
- [Java HashMap Iteration Order Analysis](https://peterchng.com/blog/2022/06/17/what-iteration-order-can-you-expect-from-a-java-hashmap/)
- [LinkedHashMap Java Documentation](https://docs.oracle.com/javase/7/docs/api/java/util/LinkedHashMap.html)
- Project CONCERNS.md (known tech debt and fragile areas)

---

*Pitfalls audit: 2026-03-31*
