# Phase 2: Numeric Type Safety - Context

**Gathered:** 2026-03-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Audit and guard all arithmetic operations on entity fields so that Short overflow, float-to-int truncation, and integer division produce identical numeric results to legacy PHP. This phase covers TYPE-01 (30+ Short/SMALLINT fields), TYPE-02 (100+ float-to-int truncation patterns), and TYPE-03 (integer division divergence). No new game features or logic changes — purely ensuring numeric fidelity.

</domain>

<decisions>
## Implementation Decisions

### Guard Placement Strategy (Short Overflow — TYPE-01)
- **D-01:** Place `coerceIn` guards at domain boundaries — inside service methods where computed values are assigned back to entity fields. Not at entity setter level (masks bugs) and not at every arithmetic site (too fragile, 100+ locations).
- **D-02:** Follow the existing pattern established in `ItemService.kt`: `general.injury = (general.injury - item.value).coerceIn(0, 80).toShort()`. This is the proven codebase convention.
- **D-03:** Each Short field gets domain-appropriate bounds (e.g., stats 0-100, crew 0-MAX, train 0-110, atmos 0-150). Bounds must be derived from legacy PHP behavior, not assumed.
- **D-04:** Audit scope: all 50+ Short fields across General (30), City (7), Nation (4+), Diplomacy, Emperor, Event, GeneralTurn entities.

### Truncation Parity Approach (Float-to-Int — TYPE-02)
- **D-05:** Build a PHP↔Kotlin golden value comparison table for rounding edge cases: 0.5, 1.5, -0.5, -1.5, 2.5, large values near Short.MAX_VALUE. PHP `round()` uses half-away-from-zero; Kotlin `roundToInt()` uses half-to-even (banker's rounding); `.toInt()` truncates toward zero.
- **D-06:** Audit and normalize the inconsistent rounding in the codebase: `Math.round().toInt()` (Java Long-returning round), `roundToInt()` (Kotlin), `.toInt()` (truncation). Each call site must match the legacy PHP rounding behavior for that specific formula.
- **D-07:** Priority audit targets by volume: EconomyService.kt (20+ sites), GeneralAI.kt (30+ sites), BattleEngine.kt/BattleTrigger.kt (35+ sites combined), command files, NpcSpawnService.kt.

### Integer Division Handling (TYPE-03)
- **D-08:** Verify-and-document approach: Kotlin `/` on Int truncates toward zero, same as PHP `intdiv()` for positive values. Audit the 900+ division operations for negative-value edge cases rather than wrapping all divisions in a utility function.
- **D-09:** Add a `phpIntdiv()` utility only if audit reveals divisions that can receive negative dividends/divisors with different truncation behavior. Do not preemptively wrap all divisions.
- **D-10:** Document which entity fields can go negative (e.g., gold can be negative in some edge cases) and add targeted division guards for those paths only.

### Testing Strategy
- **D-11:** Primary verification: 200-turn economic simulation golden snapshot test. Run 200 turns in Kotlin with a fixed seed, compare key accumulator fields (nation gold/rice, city pop/agri/comm/secu/def/wall, general stats/gold/rice) against PHP-derived expected values.
- **D-12:** Supplementary: targeted unit tests for each rounding/overflow fix, covering the specific edge cases (boundary values, half-values for rounding, near-overflow arithmetic).
- **D-13:** jqwik property-based testing is optional — explore if Kotlin 2.1 compatibility is confirmed (STATE.md concern), but not required for phase success.

### Claude's Discretion
- Specific ordering of file audits within each requirement (TYPE-01, TYPE-02, TYPE-03) — prioritize by parity impact
- Whether to create a shared `NumericUtils.kt` for common guard patterns or inline guards at each site
- Granularity of golden value test assertions (per-field vs aggregate checksums)
- Whether `Math.round()` vs `roundToInt()` normalization should be a separate sub-plan or combined with truncation audit

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Entity Files (Short field audit targets)
- `backend/game-app/src/main/kotlin/com/opensam/entity/General.kt` — 30 Short fields (stats, exp, military, ages, state)
- `backend/game-app/src/main/kotlin/com/opensam/entity/City.kt` — 7 Short fields (level, supplyState, frontState, state, region, term)
- `backend/game-app/src/main/kotlin/com/opensam/entity/Nation.kt` — 4+ Short fields (bill, rate, rateTmp, secretLimit)
- `backend/game-app/src/main/kotlin/com/opensam/entity/Diplomacy.kt` — Short term field
- `backend/game-app/src/main/kotlin/com/opensam/entity/Emperor.kt` — Short year, month
- `backend/game-app/src/main/kotlin/com/opensam/entity/GeneralTurn.kt` — Short turnIdx

### High-Volume Truncation Sites
- `backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt` — 20+ `.toInt()` on float results (tax, trade, population, decay)
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` — 30+ `.toInt()` / `Math.round().toInt()` calls
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt` — 80 integer division operations
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` — 35 integer division operations
- `backend/game-app/src/main/kotlin/com/opensam/engine/GeneralMaintenanceService.kt` — `.toInt()` on ratio calculations
- `backend/game-app/src/main/kotlin/com/opensam/engine/NpcSpawnService.kt` — `Math.round()` on stat generation
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` — `ceil().toInt()`, `floor().toInt()` patterns

### Existing Guard Patterns (reference for consistency)
- `backend/game-app/src/main/kotlin/com/opensam/service/ItemService.kt` — `.coerceIn(0, 80).toShort()` pattern on injury/atmos/train
- `backend/game-app/src/main/kotlin/com/opensam/service/AuctionService.kt` — `.coerceAtLeast(1)` and `.coerceIn()` on market calculations
- `backend/game-app/src/main/kotlin/com/opensam/engine/GeneralMaintenanceService.kt` — `(value * ratio).toInt().coerceAtLeast(minValue).toShort()` pattern

### DB Schema (SMALLINT mapping)
- `backend/game-app/src/main/resources/db/migration/V1__core_tables.sql` — 54 SMALLINT columns
- `backend/game-app/src/main/resources/db/migration/V7__add_general_fields_fix_city_types.sql` — Additional SMALLINT fixes
- `backend/game-app/src/main/resources/db/migration/V23__add_politics_charm_exp.sql` — Politics/charm exp as SMALLINT

### Legacy PHP Reference
- `legacy-core/hwe/func.php` — Main game functions with PHP arithmetic patterns
- `legacy-core/hwe/process_war.php` — Battle formulas with PHP rounding
- `legacy-core/hwe/sammo/Command/General/` — General command arithmetic
- `legacy-core/hwe/sammo/Command/Nation/` — Nation command arithmetic

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `coerceIn`/`coerceAtLeast`/`coerceAtMost`: Already used in 50+ locations — established Kotlin idiom for bounds clamping
- `ItemService.kt` guard pattern: `.coerceIn(low, high).toShort()` — copy this pattern for consistency
- `LiteHashDRBGTest.kt` golden value test pattern — extend for numeric parity golden values
- `InMemoryTurnHarness`: Can drive the 200-turn simulation test without a database

### Established Patterns
- Short fields are stored as SMALLINT in PostgreSQL, mapped via JPA `Short` type
- Arithmetic is done by promoting Short to Int/Double, computing, then casting back with `.toShort()`
- Some sites already guard (ItemService, AuctionService), many do not (EconomyService, GeneralAI)
- Mixed rounding approaches: `Math.round()`, `roundToInt()`, `.toInt()` — no consistent convention

### Integration Points
- Entity field assignments in service/engine classes are the guard insertion points
- Test infrastructure: H2 in PostgreSQL mode for integration tests, `application-test.yml`
- Turn pipeline: `InMemoryWorldState` → `TurnPipeline` steps → `WorldStatePersister` is the simulation test path

</code_context>

<specifics>
## Specific Ideas

No specific requirements beyond matching legacy PHP numeric behavior. The key insight from codebase scouting: the inconsistent rounding approach (3 different methods across 100+ sites) is likely the highest-risk parity divergence source, more so than Short overflow which has natural bounds from game logic.

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 02-numeric-type-safety*
*Context gathered: 2026-03-31*
