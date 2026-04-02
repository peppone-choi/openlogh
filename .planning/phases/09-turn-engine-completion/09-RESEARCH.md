# Phase 9: Turn Engine Completion - Research

**Researched:** 2026-04-02
**Domain:** Turn pipeline stub implementation, step ordering parity, disaster/event verification
**Confidence:** HIGH

## Summary

Phase 9 fills the 4 remaining stub methods in TurnService.kt (checkWander, updateOnline, checkOverhead, updateGeneralNumber) and verifies that turn step ordering and disaster/event probabilities match the legacy PHP implementation. Research reveals a **critical ordering mismatch** in the postUpdateMonthly section: Kotlin currently calls checkWander -> triggerTournament -> registerAuction -> updateGeneralNumber, but legacy PHP calls checkWander -> updateGeneralNumber -> refreshNationStaticInfo -> checkEmperior -> triggerTournament -> registerAuction -> SetNationFront. This must be corrected in Plan 1.

All 4 stubs have clear legacy PHP source with straightforward Kotlin translation. The disaster/boom system is already implemented in EconomyService.processDisasterOrBoom() and closely matches the legacy RaiseDisaster.php -- verification in Plan 2 needs to compare probabilities, RNG seed strings, state codes, and affectRatio formulas.

**Primary recommendation:** Implement the 4 stubs using direct PHP-to-Kotlin translation, fix the postUpdateMonthly call ordering, then verify disaster probabilities and turn step ordering with golden value tests.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: 4 stubs all implemented with legacy PHP logic -- updateOnline, checkOverhead, checkWander, updateGeneralNumber
- D-02: checkWander() uses CommandExecutor -- builds `해산` Command, checks `hasFullConditionMet()`, then calls `run()`
- D-03: Turn step ordering assertion -- document legacy postUpdateMonthly() call order and 1:1 compare with Kotlin
- D-04: Probability + effect verification -- extract legacy disaster probabilities by month and assert via golden values
- D-05: 2-plan split -- Plan 1: stub implementation + unit tests; Plan 2: ordering assertion + disaster golden value verification
- D-06: PHP manual tracing -- read PHP code, compute expected values by hand, lock as golden values
- D-07: Extend existing test files first -- prefer adding to existing test structure over new files

### Claude's Discretion
- Stub method implementation details (PHP to Kotlin DB query patterns)
- Golden value fixture game state (seed, city/nation combinations)
- updateOnline vs TrafficSnapshotStep overlap handling
- checkOverhead JVM environment adaptation scope
- Ordering assertion test structure

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TURN-01 | Implement checkWander() -- wander nation dissolution after 2 years | Legacy PHP at func_gamerule.php:445-467 fully traced; `해산.kt` command exists; needs CommandExecutor integration |
| TURN-02 | Implement updateOnline() -- per-tick online count snapshot | Legacy PHP at func.php:1205-1248 fully traced; GeneralAccessLogRepository and TrafficService exist |
| TURN-03 | Implement checkOverhead() -- runaway process guard | Legacy PHP at func.php:1103-1116 fully traced; simple formula: `round(turnterm^0.6 * 3) * refreshLimitCoef` |
| TURN-04 | Implement updateGeneralNumber() -- refresh nation static info | Legacy PHP at func_gamerule.php:174-186 fully traced; nation.gennum update + cache refresh |
| TURN-05 | Verify turn step ordering matches legacy daemon.ts | Critical ordering mismatch found in postUpdateMonthly calls; pipeline step order verified correct (17 steps 100-1700) |
| TURN-06 | Verify disaster/event trigger probabilities match legacy | RaiseDisaster.php fully traced; Kotlin EconomyService.processDisasterOrBoom() comparison shows minor differences to verify |
</phase_requirements>

## Standard Stack

No new libraries needed. All work uses existing project infrastructure.

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit Jupiter | 5.x (Spring Boot 3.4.2) | Unit/parity tests | Already used in all phase tests |
| AssertJ | 3.x (Spring Boot) | Fluent assertions | Already used in TurnPipelineParityTest |
| Mockito | 5.x (Spring Boot) | Mock dependencies | Already used in TurnServiceTest |
| H2 Database | 2.x | In-memory test DB | Already configured in application-test.yml |

## Architecture Patterns

### Existing Turn Pipeline Architecture
```
TurnService.processTick(worldId)
  |
  +-- [per-tick, before monthly loop]
  |     updateOnline(world)          <-- STUB (TURN-02)
  |     checkOverhead(world)         <-- STUB (TURN-03)
  |
  +-- [monthly loop: while now >= nextTurnAt]
  |     executeGeneralCommandsUntil()
  |     recalculateCitySupply()
  |     eventService.dispatchEvents(PRE_MONTH)  [step 200]
  |     economyService.preUpdateMonthly()        [step 300]
  |     resetStrategicCommandLimits()            [step ~1400]
  |     advanceMonth()                           [step 400]
  |     turnPipeline.execute()                   [steps 500-1700]
  |     |
  |     +-- [post-pipeline: postUpdateMonthly]
  |           checkWander(world)            <-- STUB (TURN-01)
  |           triggerTournament(world)       [already implemented]
  |           registerAuction(world)         [already implemented]
  |           updateGeneralNumber(world)     <-- STUB (TURN-04)
  |
  +-- [after loop]
        tournamentService.processTournamentTurn()
```

### CRITICAL: Ordering Mismatch (TURN-05)

**Legacy PHP postUpdateMonthly() order** (func_gamerule.php:423-441):
```
1. checkWander($rng)                    // line 424-426 (conditional: year >= startyear+2)
2. updateGeneralNumber()                // line 427
3. refreshNationStaticInfo()            // line 428
4. checkEmperior()                      // line 430
5. triggerTournament($rng)              // line 432
6. registerAuction($rng)                // line 434
7. SetNationFront(foreach nation)       // line 436-441
```

**Current Kotlin order** (TurnService.kt:360-380):
```
1. checkWander(world)
2. triggerTournament(world)        // WRONG: should be after updateGeneralNumber + checkEmperior
3. registerAuction(world)          // WRONG: should be after updateGeneralNumber + checkEmperior
4. updateGeneralNumber(world)      // WRONG: should be #2
```

**Missing calls in Kotlin:**
- `checkEmperior()` -- emperor legitimacy check (may be handled by UnificationCheck step 1600)
- `SetNationFront()` -- war front recalculation (may be handled by WarFrontRecalc step 1300)

**Action required:** Reorder the 4 post-pipeline calls to match legacy, and verify that checkEmperior and SetNationFront are covered by pipeline steps 1600 and 1300 respectively.

### Legacy Per-Tick Flow (TurnExecutionHelper.php:415-421)
```
1. checkDelay()                    // server delay check (no Kotlin equivalent needed)
2. updateOnline()                  // STUB -- per-tick online count
3. CheckOverhead()                 // STUB -- refresh limit recalculation
4. [monthly loop begins]
```

### Pattern: Stub Implementation
All 4 stubs follow the same pattern -- private methods in TurnService.kt with `@Suppress("UNUSED_PARAMETER")` and TODO comments. Fill the bodies with legacy-matching logic.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Wander nation dissolution | Custom dissolution logic | `CommandExecutor` + `해산` command | Legacy uses `buildGeneralCommandClass('che_해산')` -- reuse existing command system |
| Online count grouping | Manual SQL aggregation | `GeneralAccessLogRepository` queries | Repository already has world-level access log queries |
| Nation gennum refresh | Manual count per nation | Existing `generals.groupingBy { nationId }.eachCount()` | Pattern already used in StrategicLimitResetStep (line 1108-1111) |

## Common Pitfalls

### Pitfall 1: postUpdateMonthly Ordering
**What goes wrong:** Calling updateGeneralNumber after triggerTournament/registerAuction means those functions see stale gennum values.
**Why it happens:** The current Kotlin code was written with updateGeneralNumber last (as a "cleanup" step), but legacy PHP calls it second.
**How to avoid:** Fix ordering in Plan 1 before writing any verification tests. Tests in Plan 2 should assert the exact call order.
**Warning signs:** Nation gennum values differ between Kotlin and legacy golden snapshots.

### Pitfall 2: checkWander Conditional Guard
**What goes wrong:** checkWander is called unconditionally, but legacy has a year guard: `if ($admin['year'] >= $admin['startyear'] + 2)`.
**Why it happens:** The guard is in postUpdateMonthly(), not inside checkWander() itself.
**How to avoid:** The year >= startYear + 2 check must be in the calling site (TurnService), NOT inside the checkWander method.
**Warning signs:** Wander nations dissolving in the first 2 years of a game.

### Pitfall 3: checkWander Uses officer_level=12 (PHP) vs officerLevel=20 (Kotlin)
**What goes wrong:** PHP selects `officer_level = 12` for chiefs, but Kotlin uses 20 for chiefs.
**Why it happens:** OpenSamguk extended the officer level system from 12 to 20.
**How to avoid:** The `해산` command's `BeLord()` constraint already handles this correctly -- it checks for the chief officer level in the Kotlin scale. The checkWander query should use the Kotlin-scale chief level, NOT the PHP value 12.
**Warning signs:** `BeLord()` constraint returns `Pass` for Kotlin `officerLevel=20` but would fail for PHP literal 12.

### Pitfall 4: updateOnline Scope vs TrafficSnapshotStep
**What goes wrong:** Duplicating online tracking logic between updateOnline (per-tick) and TrafficSnapshotStep (step 700, per-month).
**Why it happens:** Both touch online/traffic data but serve different purposes.
**How to avoid:** updateOnline updates `world.meta["online_user_cnt"]` and `world.meta["online_nation"]` per-tick (live data). TrafficSnapshotStep records historical traffic snapshots per-month. They are complementary, not overlapping.
**Warning signs:** Double-counting or overwriting traffic snapshots.

### Pitfall 5: Disaster RNG Seed String Mismatch
**What goes wrong:** Kotlin uses `"disaster"` but legacy PHP uses `"disater"` (typo in RaiseDisaster.php line 28).
**Why it happens:** The legacy PHP has a typo in the RNG seed string.
**How to avoid:** For RNG parity, Kotlin MUST use the same typo `"disater"` as the PHP seed. Check if the current Kotlin code uses `"disaster"` and correct to match PHP.
**Warning signs:** Different disaster patterns for the same game state/seed.

### Pitfall 6: Disaster startYear+3 vs Kotlin missing guard
**What goes wrong:** Legacy PHP skips disasters if `startYear + 3 > year` (3-year grace period), but this guard may be missing in Kotlin.
**Why it happens:** The guard is in the Event Action handler (RaiseDisaster.php line 38), not in postUpdateMonthly.
**How to avoid:** Verify Kotlin EconomyService.processDisasterOrBoom() has the same `startYear + 3 > year` early return.
**Warning signs:** Disasters occurring in the first 3 years of a scenario.

## Code Examples

### checkWander Implementation Pattern
```kotlin
// Source: legacy-core/hwe/func_gamerule.php lines 445-467
private fun checkWander(world: WorldState) {
    // Guard: only after startYear + 2 (called from postUpdateMonthly with this check)
    val startYear = world.config["startYear"] as? Int ?: return
    if (world.currentYear.toInt() < startYear + 2) return

    val worldId = world.id.toLong()
    // Find chief generals (officerLevel = chief level) of wander nations (level = 0)
    val wanderers = generalRepository.findByWorldId(worldId)
        .filter { general ->
            val nation = nationRepository.findById(general.nationId).orElse(null)
            nation != null && nation.level.toInt() == 0 && general.officerLevel.toInt() == 20
        }

    for (wanderer in wanderers) {
        // Build and execute che_해산 command via CommandExecutor
        val cmd = commandRegistry.create("해산", wanderer, buildCommandEnv(world))
        if (cmd.hasFullConditionMet()) {
            // Legacy: push log "초반 제한후 방랑군은 자동 해산됩니다."
            cmd.run(rng)
            cmd.setNextAvailable()
        }
    }
    // Legacy: refreshNationStaticInfo after dissolution
}
```

### updateOnline Implementation Pattern
```kotlin
// Source: legacy-core/hwe/func.php lines 1205-1248
private fun updateOnline(world: WorldState) {
    val worldId = world.id.toLong()
    // Get current turn start time for recent access filtering
    val nations = nationRepository.findByWorldId(worldId)
    val nationNames = mutableMapOf<Long, String>(0L to "재야")
    nations.forEach { nationNames[it.id] = it.name }

    // Count online generals (accessed since last turn)
    val recentLogs = generalAccessLogRepository.findByWorldId(worldId)
    // ... filter by lastRefresh >= turnStartTime
    val onlineCount = recentLogs.size

    // Group by nation, sort by count desc
    val onlineByNation = recentLogs.groupBy { it.nationId }
        .entries.sortedByDescending { it.value.size }

    val onlineNationNames = onlineByNation.map { (nationId, _) ->
        "【${nationNames[nationId] ?: "unknown"}】"
    }

    // Update world meta
    world.meta["online_user_cnt"] = onlineCount
    world.meta["online_nation"] = onlineNationNames.joinToString(", ")
}
```

### checkOverhead Implementation Pattern
```kotlin
// Source: legacy-core/hwe/func.php lines 1103-1116
private fun checkOverhead(world: WorldState) {
    val turnterm = world.tickSeconds.toDouble()
    // Legacy formula: round(turnterm^0.6 * 3) * refreshLimitCoef
    val refreshLimitCoef = (world.config["refreshLimitCoef"] as? Number)?.toInt() ?: 10
    val nextRefreshLimit = kotlin.math.round(
        Math.pow(turnterm, 0.6) * 3
    ).toInt() * refreshLimitCoef

    val currentRefreshLimit = (world.meta["refreshLimit"] as? Number)?.toInt() ?: 0
    if (nextRefreshLimit != currentRefreshLimit) {
        world.meta["refreshLimit"] = nextRefreshLimit
    }
}
```

### updateGeneralNumber Implementation Pattern
```kotlin
// Source: legacy-core/hwe/func_gamerule.php lines 174-186
private fun updateGeneralNumber(world: WorldState) {
    val worldId = world.id.toLong()
    val generals = generalRepository.findByWorldId(worldId)
    val nations = nationRepository.findByWorldId(worldId)

    // Count generals per nation (exclude npc=5 wanderers)
    val genCountByNation = generals
        .filter { it.npcState.toInt() != 5 && it.nationId > 0 }
        .groupingBy { it.nationId }
        .eachCount()

    for (nation in nations) {
        if (nation.id == 0L) continue
        nation.gennum = genCountByNation[nation.id] ?: 0
    }
    nationRepository.saveAll(nations)
    // Legacy: refreshNationStaticInfo() -- in Kotlin this means the updated
    // nation entities are already saved, cache is implicitly refreshed
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x + AssertJ 3.x |
| Config file | `backend/game-app/src/test/resources/application-test.yml` |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.TurnServiceTest" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TURN-01 | checkWander dissolves wander nations after 2 years | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*checkWander*"` | Extend TurnServiceTest.kt |
| TURN-02 | updateOnline updates online count per tick | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*updateOnline*"` | Extend TurnServiceTest.kt |
| TURN-03 | checkOverhead recalculates refreshLimit | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*checkOverhead*"` | Extend TurnServiceTest.kt |
| TURN-04 | updateGeneralNumber refreshes nation gennum | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*updateGeneralNumber*"` | Extend TurnServiceTest.kt |
| TURN-05 | Turn step ordering matches legacy | unit | `./gradlew :game-app:test --tests "*TurnPipelineParityTest*"` | TurnPipelineParityTest.kt exists |
| TURN-06 | Disaster probabilities match legacy | unit | `./gradlew :game-app:test --tests "*DisasterParityTest*"` | New or extend EconomyIntegrationParityTest.kt |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.TurnServiceTest" -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Phase gate:** Full suite green before verification

### Wave 0 Gaps
- None critical -- existing test infrastructure (TurnServiceTest.kt, TurnPipelineParityTest.kt, EconomyIntegrationParityTest.kt) covers all needs. Tests will be added to existing files per D-07.

## Key Findings

### 1. Disaster RNG Seed Typo (HIGH confidence)
Legacy PHP RaiseDisaster.php line 28 uses `'disater'` (missing 's') as the RNG seed context string. Current Kotlin EconomyService.kt line 707 uses `"disaster"`. This is a **confirmed parity divergence** that must be checked -- if the intent is exact RNG parity, the seed must match the PHP typo.

### 2. Disaster Month Mapping (HIGH confidence)
Legacy PHP processes disasters only at months 1, 4, 7, 10 (quarterly). The `boomingRate` map:
- Month 1: 0% boom chance (always disaster if triggered)
- Month 4: 25% boom chance
- Month 7: 25% boom chance  
- Month 10: 0% boom chance (always disaster if triggered)

Kotlin uses `when (month) { 4, 7 -> 0.25; else -> 0.0 }` which means months 1 and 10 have boomRate=0 and no boom is possible. This matches legacy.

### 3. Disaster 3-Year Grace Period (HIGH confidence)
Legacy PHP: `if ($startYear + 3 > $year) return;` -- no disasters in first 3 years.
Need to verify Kotlin has the same guard in processDisasterOrBoom().

### 4. SabotageInjury Formula Comparison (HIGH confidence)
Legacy PHP SabotageInjury (func.php:2169-2194):
- 30% injury chance per general in affected city
- Injury amount: `rng.nextRangeInt(1, 16)` (PHP range is inclusive: 1-16)
- crew *= 0.98, atmos *= 0.98, train *= 0.98
- injury capped at 80

Kotlin (EconomyService.kt:803-808):
- `rng.nextDouble() >= 0.3` (30% check)
- `rng.nextInt(1, 17)` (Kotlin range: 1 inclusive, 17 exclusive = 1-16, matches PHP)
- crew *= 0.98, atmos *= 0.98 (coerceIn 0..150), train *= 0.98 (coerceIn 0..110)
- injury capped at 80

**Potential difference:** PHP calls `$general->onCalcStat($general, 'injuryProb', $injuryProb)` which could modify the 0.3 probability via modifiers. Kotlin hardcodes 0.3. This may or may not be a parity issue depending on whether any modifier actually changes injuryProb.

### 5. postUpdateMonthly Ordering Fix Required (HIGH confidence)
The corrected Kotlin order must be:
```
1. checkWander(world)           // conditional: year >= startYear + 2
2. updateGeneralNumber(world)   // updates nation.gennum
3. // refreshNationStaticInfo -- implicit in JPA save
4. // checkEmperior -- verify if UnificationCheck (step 1600) covers this
5. triggerTournament(world)
6. registerAuction(world)
7. // SetNationFront -- verify if WarFrontRecalc (step 1300) covers this
```

### 6. daemon.ts Is Not the Turn Logic (MEDIUM confidence)
The legacy `daemon.ts` is just a process manager that calls `proc.php` via HTTP. The actual turn logic is in `TurnExecutionHelper.php` which calls `preUpdateMonthly()`, `turnDate()`, `postUpdateMonthly()`. The Kotlin pipeline step order in TurnPipelineParityTest.kt already documents this correctly.

## Open Questions

1. **checkEmperior() coverage**
   - What we know: Legacy calls checkEmperior after updateGeneralNumber in postUpdateMonthly
   - What's unclear: Whether UnificationCheck step (1600) already covers this, or if a separate call is needed post-pipeline
   - Recommendation: Compare checkEmperior PHP logic with UnificationService.kt; if covered, document; if not, add to post-pipeline calls

2. **SetNationFront() coverage**
   - What we know: Legacy calls SetNationFront for all active nations after registerAuction
   - What's unclear: Whether WarFrontRecalc step (1300) already covers this
   - Recommendation: Compare SetNationFront PHP with WarFrontRecalcStep; if covered, document; if not, add

3. **updateOnline per-tick necessity**
   - What we know: Legacy updates online_user_cnt and online_nation per-tick; Kotlin has TrafficSnapshotStep (700) per-month
   - What's unclear: Whether the per-tick update is functionally important or just informational
   - Recommendation: Implement to match legacy, even if effect is primarily cosmetic (world.meta storage)

4. **Disaster injuryProb modifier**
   - What we know: PHP calls `onCalcStat('injuryProb', 0.3)` which could modify the probability
   - What's unclear: Whether any special ability actually modifies injuryProb
   - Recommendation: Verify in legacy special ability definitions; if none modify it, the hardcoded 0.3 is correct

## Sources

### Primary (HIGH confidence)
- `legacy-core/hwe/func_gamerule.php` lines 174-186, 260-441, 445-467 -- updateGeneralNumber, postUpdateMonthly, checkWander
- `legacy-core/hwe/func.php` lines 87-90, 1103-1116, 1205-1248 -- refreshNationStaticInfo, CheckOverhead, updateOnline
- `legacy-core/hwe/sammo/TurnExecutionHelper.php` lines 415-517 -- per-tick flow and monthly loop
- `legacy-core/hwe/sammo/Event/Action/RaiseDisaster.php` -- disaster/boom probability and effect logic
- `legacy-core/hwe/func.php` lines 2169-2194 -- SabotageInjury formula
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` -- current stub locations and pipeline flow
- `backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt` lines 699-837 -- current disaster implementation
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/TurnPipelineParityTest.kt` -- existing pipeline order tests

### Secondary (MEDIUM confidence)
- `backend/game-app/src/main/kotlin/com/opensam/command/general/해산.kt` -- dissolution command implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/OnlineOverheadStep.kt` -- current no-op placeholder

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new libraries needed, all existing
- Architecture: HIGH -- stubs are clearly defined, legacy code fully traced
- Pitfalls: HIGH -- 6 specific pitfalls identified from code comparison, especially ordering mismatch
- Disaster parity: MEDIUM -- need to verify RNG seed typo impact and injuryProb modifier

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable -- legacy code is frozen, Kotlin stubs are well-defined)
