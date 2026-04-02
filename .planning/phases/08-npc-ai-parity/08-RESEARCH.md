# Phase 8: NPC AI Parity - Research

**Researched:** 2026-04-02
**Domain:** NPC AI decision engine parity (GeneralAI.php vs GeneralAI.kt/NationAI.kt)
**Confidence:** HIGH

## Summary

Phase 8 is a **verification and correction** phase, not a greenfield implementation. The Kotlin AI (GeneralAI.kt ~3743 lines, NationAI.kt ~443 lines) already ports most of the legacy PHP AI (GeneralAI.php ~4293 lines). The work is: (1) systematically compare each do*() method between PHP and Kotlin, (2) identify divergences, (3) fix them, (4) lock golden values.

The most critical divergence already identified is the **diplomacy AI** (do선전포고, do불가침제의) which REQUIREMENTS.md flags as "completely different." Beyond diplomacy, there are structural differences in how the Kotlin code organizes nation-level AI (split into a separate NationAI.kt class) vs PHP (everything in GeneralAI.php). The NationAI.kt implementation has its own decision logic that does NOT match PHP's chooseNationTurn() priority-based dispatch.

**Primary recommendation:** Fix the three confirmed structural divergences first (calcDiplomacyState, do선전포고, NationAI.kt architecture), then systematically verify each do*() method with golden value tests following the Phase 5/6/7 pattern.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 3-plan split -- Plan 1: military AI, Plan 2: domestic/economy AI, Plan 3: diplomacy/personnel/wanderer AI
- **D-02:** PHP manual tracing for golden values (same pattern as Phase 5/6/7)
- **D-03:** PHP-based diplomacy rewrite -- PHP do선전포고()/do불가침제의() precision read, Kotlin diplomacy logic rewritten to match
- **D-04:** PHP method-level 1:1 comparison -- PHP GeneralAI.php single-class methods mapped to Kotlin GeneralAI.kt + NationAI.kt
- **D-05:** Golden value 1-2 per do*() method + branch-point unit tests
- **D-06:** Extend existing test files (GeneralAITest.kt, NationAITest.kt, NpcAiParityTest.kt)

### Claude's Discretion
- Plan 3-split internal method assignment boundaries
- PHP code reading order and catalog format
- Golden value fixture game state design (seed, general/city/nation combinations)
- Branch-point test condition selection
- NationAI.kt diplomacy refactoring scope

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-01 | Verify NPC military AI decision trees match legacy GeneralAI.php (40+ do*() methods) | Full do*() method catalog below; military methods mapped to Plan 1 |
| AI-02 | Fix NPC diplomacy AI to match legacy behavior (currently "completely different") | Divergence analysis section documents 3 critical diplomacy differences |
| AI-03 | Verify NPC recruitment/personnel decisions match legacy | Personnel methods (발령/포상/몰수) mapped to Plan 3; categorizeNationGeneral divergence documented |
| AI-04 | Verify NPC strategic priorities (attack, defend, develop) match legacy | Priority dispatch divergence documented; chooseGeneralTurn/chooseNationTurn flow differences catalogued |
</phase_requirements>

## Architecture Patterns

### PHP vs Kotlin Structure Mapping

PHP has a single `GeneralAI.php` class with ALL AI logic. Kotlin splits this into:

| PHP | Kotlin | Status |
|-----|--------|--------|
| `GeneralAI.php::chooseGeneralTurn()` | `GeneralAI.kt::chooseGeneralTurn()` | Exists, flow differences |
| `GeneralAI.php::chooseNationTurn()` | `GeneralAI.kt::chooseNationTurn()` | Exists, BUT also `NationAI.kt::decideNationAction()` |
| `GeneralAI.php::chooseInstantNationTurn()` | `GeneralAI.kt::chooseInstantNationTurn()` | Exists |
| `GeneralAI.php::do*()` (general actions) | `GeneralAI.kt::do*()` | ~50 methods mapped |
| `GeneralAI.php::do*()` (nation actions) | `GeneralAI.kt::doNationAction()` dispatch | Pattern differs but covers same actions |
| `GeneralAI.php::calcDiplomacyState()` | `GeneralAI.kt::calcDiplomacyState()` | **DIVERGENT** -- different logic |
| `GeneralAI.php::categorizeNationGeneral()` | No direct equivalent | **MISSING** -- Kotlin does not classify generals into npcWar/npcCivil/userWar/userCivil |
| `GeneralAI.php::categorizeNationCities()` | Inline in `decideAndExecute()` | Partial -- missing `dev` and `important` scoring |
| `AutorunGeneralPolicy` | `NpcGeneralPolicy` | Exists, priority lists differ |
| `AutorunNationPolicy` | `NpcNationPolicy` | Exists, priority lists differ |

### NationAI.kt Dual Architecture Problem

**Critical finding:** Kotlin has TWO nation-level AI paths:
1. `GeneralAI.kt::chooseNationTurn()` -- follows PHP pattern (priority-based dispatch via `doNationAction()`)
2. `NationAI.kt::decideNationAction()` -- independent implementation with DIFFERENT logic

NationAI.kt does NOT match PHP at all:
- PHP `chooseNationTurn()` iterates `nationPolicy->priority`, calls `do*()` methods
- NationAI.kt has hardcoded war logic (`atWar ? random strategic cmd`), own `pickWarTarget()`, own `shouldConsiderNAP()`, own `adjustTaxAndBill()`

**Per D-04:** Kotlin file structure is NOT modified. But the planner must determine which code path is actually called at runtime (via TurnService) and ensure that path matches PHP behavior.

### Entry Point Flow Comparison

**PHP chooseGeneralTurn():**
```
1. updateInstance() [lazy init]
2. NPC message (npcmsg with probability)
3. defence_train = 80
4. do선양 (lord abdication check)
5. npcType==5 → do집합
6. Reserved command check
7. injury > cureThreshold → 요양
8. wanderer (npcType 2/3, nationId=0) → do거병
9. nationId=0 + can국가선택 → do국가선택 or do중립
10. npcType<2 + nationId=0 → reserved (재야유저)
11. Lord without capital → do건국/do방랑군이동/do해산
12. generalPolicy.priority iteration → do*()
13. fallback → do중립
```

**Kotlin chooseGeneralTurn():**
```
1. defence_train = 80
2. npcType==5 → doRally (no nationId=0 kill check!)
3. Reserved command check
4. injury > 0 → 요양 (threshold=0, PHP uses cureThreshold!)
5. NPC rise (npcType 2/3, nationId=0)
6. Wanderer → decideWandererAction
7. Lord without capital → simplified (random 건국/이동)
8. killTurn <= 5 → doDeathPreparation
9. Falls through to decideAndExecute()
```

**Key flow differences found:**
- **Injury threshold:** Kotlin uses `injury > 0` (line 3214), PHP uses `injury > cureThreshold` (default 10). This means Kotlin NPCs rest more aggressively.
- **npcType=5 death:** PHP sets `killturn=1` when npcType=5 + nationId=0. Kotlin does not.
- **Lord without capital:** PHP has structured do건국/do방랑군이동/do해산 with relYearMonth check. Kotlin has simplified random check.
- **NPC message:** PHP emits NPC messages with probability. Kotlin skips this entirely.
- **do선양:** PHP checks this BEFORE npcType=5. Kotlin does not have this in chooseGeneralTurn.
- **Priority fallback:** PHP iterates `generalPolicy.priority`. Kotlin's chooseGeneralTurn calls `decideAndExecute()` which has its own flow.

## Confirmed Divergences (Critical)

### Divergence 1: calcDiplomacyState() -- Completely Different Logic

**PHP (5-state):**
```
d평화=0, d선포=1, d징병=2, d직전=3, d전쟁=4
```
- Uses `diplomacy` table with `state` (0=war, 1=declared) and `term` (countdown)
- Early game (year <= startyear+2, month<=5): forced 평화 or 선포
- `term > 8` → 선포, `term > 5` → 징병, `term <= 5` → 직전
- Stores `last_attackable` in KVStorage for border-loss grace period (5 months)

**Kotlin (4-state enum):**
```
PEACE, DECLARED, IMMINENT, AT_WAR, RECRUITING
```
- Checks stateCode strings ("선전포고", "전쟁", "종전제의", "불가침")
- No early game forced peace
- No term-based state transitions
- No last_attackable grace period
- Has RECRUITING state (based on troop count) -- no PHP equivalent
- Has IMMINENT state (based on enemy troop ratio) -- no PHP equivalent

**Impact:** Every do*() method that checks diplomacy state will behave differently. This is the ROOT CAUSE of most AI divergences.

### Divergence 2: do선전포고 (War Declaration)

**PHP:**
- Guard: `officer_level < 12` (chief level is 12 in PHP)
- Guard: `dipState !== d평화` (only declare war in peacetime)
- Guard: `attackable` must be FALSE (no existing attack targets)
- Guard: no `frontCities` (no borders = need to create one)
- Guard: tech readiness via `TechLimit()`
- Resource calc: separates npcWarGenerals/npcCivilGenerals/userWarGenerals/userCivilGenerals (user resources counted at 50%)
- Trial probability: `(avgGold/reqGold + avgRice/reqRice + devRate) / 4) ^ 6`
- Target: prefers nations NOT in any diplomacy, fallback to war nations with probability
- Uses `isNeighbor()` global function for adjacency

**Kotlin:**
- Guard: `officerLevel < 20` (Kotlin uses 20 for chief)
- Guard: same peace check
- Guard: attackable AND hasEnemyNationTargets (different logic)
- Guard: `frontCities.isEmpty()` reversed! Kotlin requires front cities to exist, PHP requires them to NOT exist
- No TechLimit check
- Resource calc: does not separate war/civil generals (different averaging)
- Same probability formula but different devRate calculation
- Target: uses mapAdjacency for neighbor check
- Does not filter by existing diplomacy states

**Impact:** War declaration timing and target selection will diverge significantly.

### Divergence 3: do불가침제의 (Non-Aggression Proposal)

**PHP:**
- Based on `recv_assist` / `resp_assist` / `resp_assist_try` KVStorage (assistance tracking)
- Candidate nations are those who provided assistance
- Amount filter: `amount * 4 < income` → skip
- DiplomatMonth = `24 * amount / income`
- Cooldown: `resp_assist_try` within 8 months

**Kotlin:**
- Based on neighbor nations without existing pacts
- No assistance tracking at all
- Random 15% probability
- DiplomatMonth calculated from target.power / income (completely different metric)

**Impact:** Completely different decision logic. PHP proposes NAP to nations that helped; Kotlin proposes to random neighbors.

### Divergence 4: categorizeNationGeneral() Missing

**PHP** classifies nation generals into 7 categories used throughout AI decisions:
- `npcWarGenerals` (NPC with leadership >= minNPCWarLeadership)
- `npcCivilGenerals` (NPC with low leadership or dying)
- `userWarGenerals` (player generals who fought recently or have troops)
- `userCivilGenerals` (other player generals)
- `troopLeaders` (npcState=5 or troop leaders)
- `lostGenerals` (in non-supply cities)
- `chiefGenerals` (officerLevel > 4)

**Kotlin** does not have this categorization. Methods that need it either:
- Use simple filters inline (losing the war/civil distinction)
- Skip the categorization entirely

**Impact:** Assignment (발령), reward (포상), confiscation (몰수), and war declaration all use these categories in PHP. Kotlin's inline filters may produce different results.

### Divergence 5: Default Priority Lists

**PHP AutorunGeneralPolicy::$default_priority:**
```
NPC사망대비, 귀환, 금쌀구매, 출병, 긴급내정, 전투준비, 전방워프, NPC헌납, 징병, 후방워프, 전쟁내정, 소집해제, 일반내정, 내정워프
```

**Kotlin NpcGeneralPolicy.DEFAULT_GENERAL_PRIORITY:**
```
긴급내정, 전쟁내정, 징병, 전투준비, 출병, 전방워프, 후방워프, 내정워프, 귀환, 일반내정, 금쌀구매, NPC헌납, 소집해제, 중립
```

**Differences:** NPC사망대비 missing from Kotlin priority (handled separately). 귀환 and 금쌀구매 are early in PHP but late in Kotlin. 출병 is 4th in PHP but 5th in Kotlin. The ordering affects which action an NPC takes when multiple are available.

## do*() Method Catalog (Plan Split)

### Plan 1: Military AI (AI-01, AI-04)

| PHP Method | Kotlin Method | Status |
|------------|---------------|--------|
| `do출병()` L2706 | `doSortie()` L1824 | Guard logic differs (frontState check) |
| `do징병()` L2483 | `doRecruit()` L1647 | Exists, needs verification |
| `do전투준비()` L2653 | `doCombatPrep()` L1796 | Exists, needs verification |
| `do전방워프()` L2972 | `doWarpToFront()` L1873 | Exists, needs verification |
| `do후방워프()` L2866 | `doWarpToRear()` L1906 | Exists, needs verification |
| `do집합()` L3111 | `doRally()` L2630 | Exists, needs verification |
| `do소집해제()` L2684 | `doDismiss()` L2137 | Exists, needs verification |

### Plan 2: Domestic/Economy AI (AI-01, AI-04)

| PHP Method | Kotlin Method | Status |
|------------|---------------|--------|
| `do일반내정()` L2118 | `doNormalDomestic()` L1424 | Exists, complex weighted logic |
| `do긴급내정()` L2220 | `doUrgentDomestic()` L1532 | Exists, needs verification |
| `do전쟁내정()` L2253 | `doWarDomestic()` L1557 | Exists, needs verification |
| `do금쌀구매()` L2367 | `doTradeResources()` L2032 | Exists, needs verification |
| `doNPC헌납()` L2785 | `doDonate()/doNpcDedicate()` L2079/L3561 | Exists, needs verification |
| `do귀환()` L3095 | `doReturn()` L2014 | Exists, needs verification |
| `do내정워프()` L3022 | `doWarpToDomestic()` L1952 | Exists, needs verification |
| `chooseTexRate()` L4172 | `chooseTexRate()` L2912 | Exists, needs verification |
| `chooseGoldBillRate()` L4201 | `chooseGoldBillRate()` L2942 | Exists, needs verification |
| `chooseRiceBillRate()` L4248 | `chooseRiceBillRate()` L2985 | Exists, needs verification |

### Plan 3: Diplomacy/Personnel/Wanderer AI (AI-02, AI-03)

| PHP Method | Kotlin Method | Status |
|------------|---------------|--------|
| `do선전포고()` L1848 | `doDeclaration()` L1182 | **DIVERGENT** -- rewrite needed |
| `do불가침제의()` L1765 | `doNonAggressionProposal()` L1117 | **DIVERGENT** -- rewrite needed |
| `do천도()` L1976 | `doMoveCapital()` L1284 | Exists, simplified |
| `do부대전방발령()` L294 | `doTroopFrontAssignment()` L522 | Exists, needs verification |
| `do부대후방발령()` L399 | `doTroopRearAssignment()` L618 | Exists, needs verification |
| `do부대구출발령()` L488 | `doTroopRescueAssignment()` L658 | Exists, needs verification |
| `do유저장전방발령()` L817 | `doUserFrontAssignment()` L839 | Exists, needs verification |
| `do유저장후방발령()` L652 | `doUserRearAssignment()` L874 | Exists, needs verification |
| `do유저장내정발령()` L877 | `doUserDomesticAssignment()` L2426 | Exists, needs verification |
| `do유저장긴급포상()` L1224 | `doUserUrgentReward()` L2487 | Exists, needs verification |
| `do유저장포상()` L1312 | `doUserReward()` L981 | Exists, needs verification |
| `doNPC포상` | `doNpcReward()` L923 | Exists, needs verification |
| `doNPC몰수` | `doNpcConfiscation()` L1028 | Exists, needs verification |
| `do방랑군이동()` L3127 | `doWandererMove()` L3463 | Exists, needs verification |
| `do거병()` L3217 | `doRise()` L2174 | Exists, needs verification |
| `do국가선택()` L3334 | `doSelectNation()` L3398 | Exists, needs verification |
| `do건국()` L3302 | `doFoundNation()` L3372 | Exists, needs verification |
| `do해산()` L3290 | `doDisband()` L2647 | Exists, needs verification |
| `do선양()` L3320 | `doAbdicate()` L2661 | Exists, needs verification |
| `choosePromotion()` L3978 | `choosePromotion()` L2712 | Exists, needs verification |
| `chooseNonLordPromotion()` L3881 | `chooseNonLordPromotion()` L2842 | Exists, needs verification |
| `categorizeNationGeneral()` L3516 | **MISSING** | Need to add or inline |
| `categorizeNationCities()` L3469 | Partial (inline) | Need `dev`/`important` fields |
| `calcDiplomacyState()` L206 | `calcDiplomacyState()` L278 | **DIVERGENT** -- rewrite needed |

## Common Pitfalls

### Pitfall 1: Officer Level 12 vs 20

**What goes wrong:** PHP uses `officer_level == 12` for chief/lord checks. Kotlin uses `officerLevel >= 20`.
**Why it happens:** OpenSamguk extended the officer rank system from PHP's 12-level to a larger scale.
**How to avoid:** When tracing PHP conditions like `officer_level < 12`, map to the equivalent Kotlin threshold. Document the mapping explicitly.
**Warning signs:** Guard conditions that never trigger or always trigger.

### Pitfall 2: DiplomacyState Enum Mismatch

**What goes wrong:** PHP has 5 numeric states (0-4) with specific semantics. Kotlin has 5 enum values with different semantics.
**Why it happens:** Kotlin was written with a simplified model that does not track `term` countdown.
**How to avoid:** Rewrite calcDiplomacyState to use the PHP 5-state model (평화/선포/징병/직전/전쟁) or map correctly.
**Warning signs:** NPCs attacking too early (no 징병 preparation phase) or never attacking (stuck in wrong state).

### Pitfall 3: NationAI.kt Dead Code

**What goes wrong:** NationAI.kt may be called from some code path but its logic is completely different from PHP.
**Why it happens:** Kotlin split nation-level AI into a separate class with independent logic.
**How to avoid:** Trace the actual call chain from TurnService/TurnDaemon to determine which code path runs. If NationAI.kt is used, either redirect to GeneralAI.kt::chooseNationTurn() or rewrite NationAI.kt.
**Warning signs:** Nations declaring random wars, promoting incorrectly, or ignoring diplomatic context.

### Pitfall 4: Missing categorizeNationGeneral Categorization

**What goes wrong:** Methods that should behave differently for war vs civil generals treat all NPCs the same.
**Why it happens:** Kotlin uses simple npcState filters instead of the PHP categorization that considers combat history, troop status, and kill-turn countdown.
**How to avoid:** Either implement categorizeNationGeneral() as a shared utility or ensure each method that needs these categories recreates the PHP logic inline.
**Warning signs:** War generals being sent to domestic duty, civil generals being forced to front lines.

### Pitfall 5: RNG Seed Context String

**What goes wrong:** PHP uses `"GeneralAI"` for all AI RNG seeding. Kotlin uses `"GeneralAI"` for decideAndExecute but `"NationTurn"` for chooseNationTurn and `"GeneralTurn"` for chooseGeneralTurn.
**Why it happens:** Kotlin added separate entry points with different seed contexts.
**How to avoid:** If golden values are computed from PHP using the `"GeneralAI"` seed, the Kotlin code must use the same seed context string for the same decision path.
**Warning signs:** Golden value mismatches even when logic is correct.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| General categorization | Ad-hoc filters per method | Shared categorizeNationGeneral() utility | PHP uses it in 10+ methods; inconsistent inline filters cause divergence |
| Diplomacy state calculation | Simplified enum mapping | Full PHP 5-state model with term tracking | Root cause of most AI behavior divergences |
| Weighted random choice | New random selection | Existing `choiceByWeight()`/`choiceByWeightPair()` in GeneralAI.kt | Already ported, just verify parity with PHP `choiceUsingWeight()` |

## Code Examples

### Golden Value Test Pattern (from Phase 7)

```kotlin
@Test
fun `doSortie golden value -- war state with adjacent target`() {
    // Fixed game state
    val world = createWorld(year = 200, month = 3)
    world.config = mutableMapOf("hiddenSeed" to "golden_parity_seed")
    
    val general = createGeneral(
        id = 42, nationId = 1, cityId = 1,
        leadership = 80, strength = 70, intel = 50,
        crew = 3000, train = 95, atmos = 95,
        npcState = 2, officerLevel = 5,
    )
    
    // Setup mocked ports with fixed cities/nations/diplomacies
    setupWarState(nationId = 1, enemyNationId = 2)
    
    // PHP manual trace: with these inputs, do출병 returns "출병" with destCityId=5
    val result = ai.doSortie(ctx, rng, nationPolicy, attackable = true, warTargetNations)
    assertEquals("출병", result)
}
```

### calcDiplomacyState PHP-Parity Pattern

```kotlin
// PHP 5-state model
enum class DiplomacyState(val code: Int) {
    PEACE(0),       // d평화
    DECLARED(1),    // d선포
    RECRUITING(2),  // d징병
    IMMINENT(3),    // d직전
    AT_WAR(4),      // d전쟁
}

fun calcDiplomacyState(...): DiplomacyState {
    val yearMonth = year * 12 + month
    val startYearMonth = startYear * 12 + startMonth
    
    // Early game forced peace (PHP: yearMonth <= joinYearMonth(startyear+2, 5))
    if (yearMonth <= startYearMonth + 2 * 12 + 5) {
        return if (warTargets.isEmpty()) PEACE else DECLARED
    }
    
    // Term-based state (PHP: minWarTerm from diplomacy table)
    val minTerm = warDeclarations.minOfOrNull { it.term } ?: return PEACE
    return when {
        minTerm > 8 -> DECLARED
        minTerm > 5 -> RECRUITING
        else -> IMMINENT
    }
    // Plus AT_WAR override when attackable + onWar
}
```

## Existing Test Infrastructure

| File | Lines | Coverage |
|------|-------|----------|
| `GeneralAITest.kt` | 1081 | classifyGeneral, calcDiplomacyState, basic decision tests |
| `NationAITest.kt` | 271 | NationAI-specific tests (may need revision if NationAI is dead code) |
| `NpcAiParityTest.kt` | 417 | classifyGeneral parity, diplomacyState basic tests |
| `NpcPolicyTest.kt` | (exists) | Policy builder tests |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (via Spring Boot Test) |
| Config file | `backend/game-app/src/test/resources/application-test.yml` |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.ai.*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-01 | Military do*() golden values | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend |
| AI-02 | Diplomacy AI parity | unit | `./gradlew :game-app:test --tests "com.opensam.qa.parity.NpcAiParityTest"` | Exists, extend |
| AI-03 | Personnel/recruitment parity | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.NationAITest"` | Exists, extend |
| AI-04 | Strategic priority parity | unit | `./gradlew :game-app:test --tests "com.opensam.qa.parity.NpcAiParityTest"` | Exists, extend |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.ai.*" --tests "com.opensam.qa.parity.NpcAiParityTest" -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- None -- existing test files cover all requirements; golden value assertions are added to existing files per D-06.

## Sources

### Primary (HIGH confidence)
- `legacy-core/hwe/sammo/GeneralAI.php` -- full PHP AI source, 4293 lines, read directly
- `legacy-core/hwe/sammo/AutorunGeneralPolicy.php` -- PHP policy class, read directly
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` -- Kotlin AI, 3743 lines, read directly
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NationAI.kt` -- Kotlin nation AI, 443 lines, read directly
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NpcPolicy.kt` -- Kotlin policy, 367 lines, read directly

### Secondary (MEDIUM confidence)
- Prior phase patterns (Phase 5/6/7 CONTEXT.md) -- golden value and fix-on-discovery patterns confirmed working

## Metadata

**Confidence breakdown:**
- Divergence identification: HIGH -- direct source comparison of PHP and Kotlin
- do*() method catalog: HIGH -- grep-based exhaustive listing
- Fix strategy: HIGH -- follows established Phase 5/6/7 patterns
- Priority list ordering impact: MEDIUM -- effect on gameplay needs golden value verification

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable domain, code-only comparison)
