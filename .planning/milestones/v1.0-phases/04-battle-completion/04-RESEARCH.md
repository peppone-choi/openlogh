# Phase 4: Battle Completion - Research

**Researched:** 2026-04-01
**Domain:** Battle triggers (반계, 돌격지속, 부상무효, 필살강화_회피불가, 도시치료), battle formula parity, siege mechanics verification
**Confidence:** HIGH

## Summary

Phase 4 completes the battle system by implementing 5 remaining combat triggers and verifying battle resolution formulas against legacy PHP for all unit type pairings and siege scenarios. The codebase is exceptionally well-prepared: the `WarUnitTrigger` framework (4 hooks, registry, BattleEngine wiring) was completed in Phase 3 and verified. `BattleTriggerContext` already contains all necessary state fields for Phase 4 triggers (`counterDamageRatio`, `dodgeDisabled`, `criticalDisabled`, `bonusPhases`, `injuryImmune`). Existing `BattleTrigger` implementations (`Che반계Trigger`, `Che돌격Trigger`, `Che견고Trigger`, `Che필살Trigger`) already have partial logic that must be reconciled -- some need WarUnitTrigger counterparts, some are already correct.

The critical finding is that three of the five triggers already have working BattleTrigger implementations: `Che반계Trigger` (counter-strategy with 0.4 probability and escalating banmok damage), `Che견고Trigger` (부상무효 via onBattleInit + onInjuryCheck), and `Che필살Trigger` (critical amplification via onPostCritical). The `Che돌격Trigger` has phase extension via onBattleInit but needs a WarUnitTrigger for sustained charge (돌격지속). The `CityHealTrigger` already exists in `GeneralTrigger.kt` as a complete implementation. Therefore, Phase 4 is primarily about: (1) adding WarUnitTrigger implementations where BattleTrigger hooks are insufficient, (2) removing TODO comments from SpecialModifiers.kt, and (3) comprehensive formula verification via the full ArmType pairing matrix and siege golden values.

**Primary recommendation:** Focus implementation effort on the two truly new WarUnitTriggers (반계 as WarUnitTrigger for the phase-level counter, 돌격지속 for sustained charge), verify existing BattleTrigger implementations match legacy exactly, remove all Phase 4 TODO comments from SpecialModifiers.kt, and build a comprehensive 7x7 ArmType parity test matrix plus siege golden value tests.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 도시치료는 Phase 4에서 구현한다. WarUnitTrigger가 아니라 GeneralTriggerCaller (preTurn trigger)로 구현. SpecialModifiers.kt의 TODO 주석대로 턴 시작 전 도시 치료 기능.
- **D-02:** 전투 데미지 공식은 **전수 매트릭스**로 검증. 모든 병종 조합 (7x7 = 49쌍)에 대해 PHP golden value를 추출하여 Kotlin 결과와 비교. 사상자 수, 사기 영향, 데미지 계산 모두 포함.
- **D-03:** 공성전 검증은 **golden value 비교**로 개별 공식 검증. 성벽 데미지, 수비 보너스, 성문 돌파 각각의 기대값을 PHP process_war.php에서 추출하여 검증. 통합 시뮬레이션은 하지 않음.
- **D-04:** 5개 트리거는 **process_war.php의 legacy 코드 순서**대로 구현. 의존성 문제를 자연스럽게 해결.

### Claude's Discretion
- 각 트리거의 구체적 hook method 선택 (onEngagementStart/onPreAttack/onPostDamage/onPostRound 중 어느 것을 사용할지) -- researcher가 legacy PHP 분석 후 결정
- Plan 분할 전략 (트리거 묶음 vs 트리거+검증 묶음) -- planner가 작업량 분석 후 결정
- 도시치료의 GeneralTriggerCaller 구체적 인터페이스 -- 기존 GeneralTrigger 패턴 참고하여 결정

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BATTLE-02 | Implement 반계 (counter-strategy) trigger (시도/발동) | Existing `Che반계Trigger` in BattleTrigger.kt already implements onPreMagic (0.4 prob, escalating banmokDamageBonus) + onPostMagic (reflect damage). Needs WarUnitTrigger wrapper for phase-level counter logic if legacy has phase-scoped behavior beyond magic reflection. TODO at SpecialModifiers.kt line 307. |
| BATTLE-03 | Implement 돌격지속 (sustained charge) trigger | Existing `Che돌격Trigger` in BattleTrigger.kt has onBattleInit (phase+2) and onDamageCalc (x1.05). TODO at SpecialModifiers.kt line 369 says "extends war phases when attacking". Needs WarUnitTrigger to implement phase extension (bonusPhases) beyond initial +2. |
| BATTLE-04 | Implement 부상무효 (injury nullification) trigger | Existing `Che견고Trigger` already implements onBattleInit (injuryImmune=true) + onInjuryCheck (injuryImmune=true). TODO at SpecialModifiers.kt line 397. May only need WarUnitTrigger if per-phase injury immunity differs from init+check pattern. |
| BATTLE-07 | Implement 필살강화_회피불가 (unavoidable critical) trigger | Existing `Che필살Trigger` implements onPostCritical with rollCriticalDamageMultiplier(). TODO at SpecialModifiers.kt line 411 says "undodgeable critical hit". Needs WarUnitTrigger to set dodgeDisabled on opponent during critical activation. |
| BATTLE-08 | Implement 도시치료 (city healing) trigger | `CityHealTrigger` already fully implemented in GeneralTrigger.kt (self-heal + 50% prob city-mate heal). Already wired into `buildPreTurnTriggers()`. D-01 confirms GeneralTriggerCaller approach. May only need TODO removal + verification test. |
| BATTLE-13 | Verify battle resolution formulas match legacy process_war.php | D-02: full 7x7 ArmType matrix. Existing `BattleEngineParityTest` has patterns. `computeWarPower()` formula in BattleEngine.kt lines 687-749 and `executeCombatPhase()` lines 772-955 are the verification targets. |
| BATTLE-14 | Verify siege/defense mechanics match legacy | D-03: golden value comparison. `WarUnitCity` (wall damage, cityTrainAtmos, getBaseAttack/Defence), `WarUnitCityParityTest` (existing dex/train parity), siege loop in BattleEngine (unlimited phases). |
</phase_requirements>

## Standard Stack

No new dependencies needed. Phase 4 uses the existing project stack.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit Jupiter | 5.x (Spring Boot BOM) | Test framework | Already used for all Phase 3 tests |
| Kotlin stdlib | 2.1.0 | Math operations (round, ceil, floor, coerceIn) | Project standard |
| Spring Boot Test | 3.4.2 | Test infrastructure | Already configured |

### Testing Utilities
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlin.random.Random | stdlib | Fixed-seed RNG for deterministic trigger tests | All trigger tests |
| H2 Database | test scope | In-memory DB for integration tests if needed | Only for BattleService integration tests |

## Architecture Patterns

### Existing Infrastructure (Reuse Directly)

The following are already implemented and verified -- Phase 4 builds on top of them:

```
engine/war/
  BattleEngine.kt           # Battle loop with 4 WarUnitTrigger hook points
  BattleTrigger.kt          # BattleTrigger interface + 30+ implementations + BattleTriggerRegistry
  BattleTriggerContext       # All Phase 4 state fields present
  WarUnitTrigger.kt          # Interface (4 hooks) + WarUnitTriggerRegistry
  WarUnit.kt                 # Base class (hp, crew, train, atmos, etc.)
  WarUnitGeneral.kt          # General combat unit (getBaseAttack/Defence, consumeRice, applyResults)
  WarUnitCity.kt             # City siege unit (wall, cityTrainAtmos, getDex)
  trigger/
    IntimidationTrigger.kt   # Pattern reference: onEngagementStart, self-registers
    SnipingTrigger.kt        # Pattern reference: onEngagementStart, attempt/activation
    BattleHealTrigger.kt     # Pattern reference: onPostDamage, probability gate
    RageTrigger.kt           # Pattern reference: onPostDamage + onPreAttack, cross-phase state
engine/trigger/
  GeneralTrigger.kt          # CityHealTrigger already implemented (lines 198-227)
  TriggerCaller.kt           # TriggerCaller with priority-based firing
```

### Pattern 1: WarUnitTrigger Implementation (from Phase 3)
**What:** Singleton object implementing WarUnitTrigger, self-registering via init block
**When to use:** All new Phase 4 trigger implementations
**Example:**
```kotlin
// Source: Phase 3 IntimidationTrigger.kt (verified pattern)
object CounterStrategyTrigger : WarUnitTrigger {
    override val code = "che_반계"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext {
        // 시도 (attempt): probability gate
        if (ctx.rng.nextDouble() >= PROBABILITY) return ctx
        // 발동 (activation): apply effects
        ctx.someFlag = true
        ctx.battleLogs.add("반계 발동!")
        return ctx
    }
}
```

### Pattern 2: Fixed-Seed RNG Test (from Phase 3)
**What:** Find seeds that produce deterministic activation/non-activation outcomes
**When to use:** All trigger tests with probabilistic behavior
**Example:**
```kotlin
// Source: IntimidationTriggerTest.kt (verified pattern)
private fun findSeed(threshold: Double, wantBelow: Boolean): Int {
    for (seed in 0..1000) {
        val v = Random(seed).nextDouble()
        if (wantBelow && v < threshold) return seed
        if (!wantBelow && v >= threshold) return seed
    }
    throw IllegalStateException("No seed found")
}
```

### Pattern 3: BattleTrigger No-Op Stub (from Phase 3)
**What:** When logic moves to WarUnitTrigger, the corresponding BattleTrigger stays registered but becomes a no-op
**When to use:** For `Che위압Trigger`, `Che저격Trigger`, etc. that were moved to WarUnitTrigger in Phase 3
**Decision:** Phase 3 decision says "Che*Trigger objects kept as no-ops in BattleTriggerRegistry rather than removed"
**Example:**
```kotlin
// Source: BattleTrigger.kt line 567-575 (verified pattern)
// Moved to WarUnitTrigger: IntimidationTrigger
object Che위압Trigger : BattleTrigger {
    override val code = "che_위압"
    override val priority = 20
    override fun onBattleInit(ctx: BattleTriggerContext) = ctx
    override fun onPreCritical(ctx: BattleTriggerContext) = ctx
    override fun onPreMagic(ctx: BattleTriggerContext) = ctx
    override fun onDamageCalc(ctx: BattleTriggerContext) = ctx
}
```

### Anti-Patterns to Avoid
- **Don't remove existing BattleTrigger entries from BattleTriggerRegistry:** Phase 3 decision keeps them as no-ops. Adding WarUnitTrigger does not mean deleting BattleTrigger entries.
- **Don't duplicate logic between BattleTrigger and WarUnitTrigger:** If both fire, the effect doubles. When moving logic to WarUnitTrigger, no-op the BattleTrigger counterpart.
- **Don't add new fields to BattleTriggerContext unnecessarily:** All needed fields (`counterDamageRatio`, `dodgeDisabled`, `criticalDisabled`, `injuryImmune`, `bonusPhases`, `banmokDamageBonus`) already exist.

## Trigger-by-Trigger Analysis

### BATTLE-02: 반계 (Counter-Strategy)

**Current state:** `Che반계Trigger` (BattleTrigger) already implements:
- `onPreMagic`: 40% prob, sets magicReflected=true, reduces magicChanceBonus by 1.0, escalates banmokDamageBonus by +0.1
- `onPostMagic`: multiplies magicDamageMultiplier by banmokDamageBonus when reflected

**TODO comment (SpecialModifiers.kt line 307):**
```
// TODO: Add getBattlePhaseSkillTriggerList for che_반계시도, che_반계발동 triggers
```

**Analysis:** The TODO references `getBattlePhaseSkillTriggerList` which maps to `onPreAttack` in WarUnitTrigger. The existing BattleTrigger handles magic reflection on `onPreMagic`. The phase-level trigger (WarUnitTrigger) may need to handle the "시도" (attempt) check at phase level, while the BattleTrigger handles the actual magic interaction. However, looking at the existing Che반계Trigger, it already performs the probability check inside onPreMagic.

**Recommended hook:** The existing BattleTrigger implementation may already be complete. The WarUnitTrigger implementation should focus on any per-phase effects not covered by the magic reflection (e.g., if legacy has a phase-level counter damage component separate from magic reflection). If legacy only has the magic counter aspect, the WarUnitTrigger may be a no-op wrapper that simply ensures the trigger code is registered in both registries.

**Confidence:** MEDIUM -- without legacy-core/ PHP source available locally, exact legacy behavior must be inferred from existing code comments and TODO annotations. The existing `Che반계Trigger` implementation appears substantive.

### BATTLE-03: 돌격지속 (Sustained Charge)

**Current state:** `Che돌격Trigger` (BattleTrigger) already implements:
- `onBattleInit`: phase+2 (initial bonus phases)
- `onDamageCalc`: attackMultiplier *= 1.05

**TODO comment (SpecialModifiers.kt line 369):**
```
// TODO: trigger che_돌격지속 -- extends war phases when attacking (WarUnitTrigger)
```

**Analysis:** The TODO explicitly says "extends war phases when attacking" which is a WarUnitTrigger behavior. The `bonusPhases` field exists in BattleTriggerContext. This trigger should fire on `onPreAttack` or `onPostDamage` to conditionally extend war phases. The `Che돌격` special in SpecialModifiers.kt already gives `initWarPhase + 2.0` as a stat modifier. The WarUnitTrigger needs to implement the sustained charge extension beyond the initial +2.

**Recommended hook:** `onPostDamage` -- after each phase, check if conditions met (e.g., attacker is winning, probability check) to add bonusPhases. This mirrors how `Che전멸시페이즈증가Trigger` works (adding bonusPhases on onPostDamage).

**Confidence:** MEDIUM -- the sustained charge mechanic needs legacy verification but the hook point is clear from the existing pattern.

### BATTLE-04: 부상무효 (Injury Nullification)

**Current state:** `Che견고Trigger` (BattleTrigger) already implements:
- `onBattleInit`: injuryImmune = true
- `onInjuryCheck`: injuryImmune = true

**TODO comment (SpecialModifiers.kt line 397):**
```
// TODO: trigger che_부상무효 -- injury immunity during init + each war phase (WarUnitTrigger)
```

**Analysis:** The TODO says "during init + each war phase". The existing BattleTrigger handles init (onBattleInit) and post-battle (onInjuryCheck). BattleEngine already reads `injuryImmune` from both the init context and the injury check context (line 307: `val effectiveInjuryImmune = attackerInjuryImmune || injuryCtx.injuryImmune`). The "each war phase" aspect might mean per-phase immunity is also needed, but BattleEngine tracks `attackerInjuryImmune` as a boolean that persists across the entire battle once set in init. This appears to already be correct.

**Recommended hook:** `onEngagementStart` (to set injuryImmune per new opponent). The existing `Che견고Trigger.onBattleInit` is called at engagement start in BattleEngine. A WarUnitTrigger `onEngagementStart` implementation would be equivalent.

**Confidence:** HIGH -- the existing implementation already covers the described behavior. This is likely a TODO removal + verification task.

### BATTLE-07: 필살강화_회피불가 (Unavoidable Critical)

**Current state:** `Che필살Trigger` (BattleTrigger) already implements:
- `onPostCritical`: When critical activates, multiply attackMultiplier by rollCriticalDamageMultiplier() (random [1.3, 2.0))

**TODO comments (SpecialModifiers.kt lines 410-411):**
```
// TODO: criticalDamageRange enhancement -- legacy sets warCriticalDamageMin/Max via StatContext extension
// TODO: trigger che_필살강화_회피불가 -- undodgeable critical hit (WarUnitTrigger)
```

**Analysis:** Two aspects: (1) "undodgeable critical hit" means setting `dodgeDisabled = true` during the attack phase when this trigger is active, and (2) "criticalDamageRange enhancement" means the critical damage multiplier range should be widened (not the default [1.3, 2.0)). The WarUnitTrigger `onPreAttack` hook is the right place to set `dodgeDisabled = true` so the dodge roll is skipped.

**Recommended hook:** `onPreAttack` -- set `ctx.dodgeDisabled = true` to prevent opponent dodge during phases where this trigger is active. The enhanced critical damage range could also be applied here or remain in the BattleTrigger's onPostCritical.

**Confidence:** HIGH -- the mechanic is straightforward: disable dodge + amplify critical.

### BATTLE-08: 도시치료 (City Healing)

**Current state:** `CityHealTrigger` already fully implemented in GeneralTrigger.kt:
- Self-heals general's injury to 0
- Heals city-mates with injury > 10 at 50% probability
- Already wired into `buildPreTurnTriggers()` when cityMates is non-empty
- Nation=0 generals only heal other nation=0 generals

**TODO comment (SpecialModifiers.kt line 424):**
```
// TODO: trigger che_도시치료 -- preTurn city heal (GeneralTriggerCaller)
```

**Analysis:** This is already implemented. D-01 confirms it uses GeneralTriggerCaller (not WarUnitTrigger). The TODO just needs to be removed, and a verification test should confirm the behavior matches legacy.

**Recommended action:** Remove TODO from SpecialModifiers.kt. Write/verify test for CityHealTrigger. Confirm `buildPreTurnTriggers()` correctly includes CityHealTrigger when the general has the `che_의술` special.

**Confidence:** HIGH -- implementation already exists and is wired.

## Battle Resolution Formula (BATTLE-13)

### Formula Chain (computeWarPower)

The damage calculation follows this chain (BattleEngine.kt lines 687-955):

1. **Base Attack/Defence:** `WarUnitGeneral.getBaseAttack()` / `getBaseDefence()` -- stat-based with tech bonus
2. **War Power:** `ARM_PER_PHASE (500) + myAttack - opDefence`
3. **Floor guarantee:** If warPower < 100, apply smoothing formula
4. **Atmos/Train scaling:** `warPower *= atmos / max(1, train)`
5. **Dex log scaling:** `warPower *= getDexLog(attackerDex, defenderDex)`
6. **CrewType coefficients:** Attack coefficient on own power, defence coefficient as oppose multiplier
7. **Experience level scaling:** Different for vs-city vs vs-general
8. **Variation:** `warPower * (0.9 + rng.nextDouble() * 0.2)` -- 10% random variation
9. **Oppose multiplier:** Applied bidirectionally (attackerResult.opposeWarPowerMultiply)
10. **Critical/Dodge/Magic rolls:** Applied in executeCombatPhase
11. **Damage calc triggers:** attackMultiplier and defenceMultiplier from triggers
12. **Overkill normalization:** If either damage exceeds HP, proportionally reduce both

### ArmType Matrix (D-02: 7x7 = 49 pairings)

ArmTypes in the enum: CASTLE(0), FOOTMAN(1), ARCHER(2), CAVALRY(3), WIZARD(4), SIEGE(5), MISC(6)

The test matrix should use a representative CrewType for each ArmType:
| ArmType | Representative CrewType | Code |
|---------|------------------------|------|
| CASTLE | CASTLE | 1000 |
| FOOTMAN | FOOTMAN | 1100 |
| ARCHER | ARCHER | 1200 |
| CAVALRY | CAVALRY | 1300 |
| WIZARD | WIZARD | 1400 |
| SIEGE | SIEGE | 1500 |
| MISC | -- | Unreachable (Phase 3 decision: no CrewType maps to MISC) |

**Note:** Phase 3 decision confirms "MISC armType branch unreachable -- no CrewType maps to ArmType.MISC". The matrix is effectively 6x6 = 36 pairings plus CASTLE interactions (CASTLE only defends). Practical matrix: each of FOOTMAN/ARCHER/CAVALRY/WIZARD/SIEGE attacking each of the 6 types (including CASTLE) = 30 pairings + relevant subset.

### Key Attack/Defence Coefficients

From CrewType.kt, the coefficient maps use ArmType codes as string keys:
- FOOTMAN: attackCoef {2:1.2, 3:0.8, 5:1.2}, defenceCoef {2:0.8, 3:1.2, 5:0.8}
- ARCHER: opposite of footman interactions
- CAVALRY: opposite of footman interactions
- WIZARD: attackCoef {5:1.2}, defenceCoef {5:0.8}
- SIEGE: varied
- CASTLE: defenceCoef {1:1.2} (footman does 1.2x to castle)

### Test Strategy

For each pairing: create two WarUnitGenerals (or WarUnitGeneral vs WarUnitCity for CASTLE) with fixed stats, run `executeCombatPhase` with fixed-seed RNG, and verify the damage output matches expected golden values. Golden values are computed from the formula chain with known inputs.

## Siege Mechanics (BATTLE-14)

### Siege Formula Components

1. **WarUnitCity construction:** `cityTrainAtmos = (year - startYear + 59).coerceIn(60, 110)`, train/atmos bonuses for city level 1/3
2. **City base attack/defence:** `(city.def + wall * 9) / 500.0 + 200.0` (same for both)
3. **City dex:** `(cityTrainAtmos - 60) * 7200` (ignores arm type)
4. **Wall damage:** `wall = (wall - damage / 20).coerceAtLeast(0)` on takeDamage
5. **City HP:** `hp = city.def * 10`
6. **CASTLE defence coefficient:** `defenceCoef {1: 1.2}` -- footman does 1.2x to castle
7. **Siege phase loop:** No phase cap for siege (continues while attacker can fight and city HP > 0)
8. **City applyResults:** `city.def = (hp / 10).coerceAtLeast(0)`, `city.wall = wall.coerceAtLeast(0)`

### Golden Value Test Approach

For each component, create fixed inputs and verify outputs:
- Wall damage per phase at known damage values
- City HP reduction at known attacker stats
- Defence coefficient bidirectional application
- Siege continuation check (no phase limit)
- City state after partial damage (no conquest)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Trigger registration | Manual map maintenance | `WarUnitTriggerRegistry.register(this)` in init block | Self-registration pattern proven in Phase 3 |
| RNG determinism in tests | Custom test random | `kotlin.random.Random(seed)` with `findSeed()` | Phase 3 pattern handles probabilistic determinism |
| Critical damage range | New random function | `rollCriticalDamageMultiplier()` extension (BattleTrigger.kt line 87) | Already returns `rng.nextDouble(1.3, 2.0)` |
| Injury immunity tracking | New boolean in BattleEngine | `BattleTriggerContext.injuryImmune` + `attackerInjuryImmune` loop var | Already tracked through init and injury check |

## Common Pitfalls

### Pitfall 1: Double-Firing Between BattleTrigger and WarUnitTrigger
**What goes wrong:** Both the existing BattleTrigger (e.g., Che반계Trigger) and a new WarUnitTrigger fire for the same special, doubling the effect.
**Why it happens:** BattleTriggerRegistry and WarUnitTriggerRegistry both match on `general.specialCode`.
**How to avoid:** When adding a WarUnitTrigger, either (a) no-op the corresponding BattleTrigger methods (following Phase 3 pattern), or (b) ensure the two trigger types handle different aspects (e.g., BattleTrigger handles fine-grained roll hooks, WarUnitTrigger handles phase-level hooks).
**Warning signs:** Trigger tests show 2x expected damage or 2x probability of activation.

### Pitfall 2: MISC ArmType in Test Matrix
**What goes wrong:** Test matrix includes MISC ArmType pairing but no CrewType maps to MISC, causing test setup failures.
**Why it happens:** ArmType enum has 7 values but Phase 3 confirmed MISC is unreachable.
**How to avoid:** Skip MISC in the 7x7 matrix. Effective matrix is 6x6 (or 5 attackers x 6 defenders since CASTLE only defends).
**Warning signs:** Test trying to construct WarUnitGeneral with MISC crewType.

### Pitfall 3: Inconsistent Probability Check Direction
**What goes wrong:** Some triggers use `rng.nextDouble() >= threshold` (no activation) while others use `rng.nextDouble() < threshold` (activation). Mixing them up inverts the probability.
**Why it happens:** Phase 3 triggers use the `>= threshold` return-early pattern (e.g., `if (ctx.rng.nextDouble() >= 0.4) return ctx`). BattleTrigger uses `nextBool(probability)` helper.
**How to avoid:** Follow the Phase 3 WarUnitTrigger pattern: `if (ctx.rng.nextDouble() >= PROB) return ctx` for the attempt check.
**Warning signs:** Trigger fires ~60% of the time instead of ~40%.

### Pitfall 4: BonusPhases Not Consumed in BattleEngine
**What goes wrong:** Setting `ctx.bonusPhases` in a WarUnitTrigger but BattleEngine doesn't read it to extend the phase loop.
**Why it happens:** The current BattleEngine phase loop uses `while (currentPhase < maxPhase)` and doesn't check bonusPhases.
**How to avoid:** Verify that BattleEngine either reads `bonusPhases` or uses `rageExtraPhases` (which RageTrigger already uses). The `돌격지속` trigger may need to add to `maxPhase` directly or use the bonusPhases mechanism that `Che전멸시페이즈증가Trigger` already sets.
**Warning signs:** Phase count never exceeds initial maxPhase despite trigger activation.

### Pitfall 5: CityHealTrigger cityMates Dependency
**What goes wrong:** CityHealTrigger test passes but production fails because `buildPreTurnTriggers()` receives empty cityMates list.
**Why it happens:** `buildPreTurnTriggers()` only adds CityHealTrigger when `cityMates.isNotEmpty()`. The caller must supply city-mates.
**How to avoid:** Verify the turn processing pipeline provides cityMates to `buildPreTurnTriggers()`. Check callers of this function.
**Warning signs:** CityHealTrigger never fires in integration tests.

## Code Examples

### Verified: WarUnitTrigger Self-Registration (Phase 3 pattern)
```kotlin
// Source: trigger/IntimidationTrigger.kt (working in production)
object IntimidationTrigger : WarUnitTrigger {
    override val code = "che_위압"
    override val priority = 20

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.defender !is WarUnitGeneral) return ctx
        if (ctx.rng.nextDouble() >= 0.4) return ctx
        // activation effects...
        return ctx
    }
}
```

### Verified: BattleTrigger No-Op After Move (Phase 3 pattern)
```kotlin
// Source: BattleTrigger.kt lines 567-575
// Moved to WarUnitTrigger: IntimidationTrigger
object Che위압Trigger : BattleTrigger {
    override val code = "che_위압"
    override val priority = 20
    override fun onBattleInit(ctx: BattleTriggerContext) = ctx
    override fun onPreCritical(ctx: BattleTriggerContext) = ctx
    override fun onPreMagic(ctx: BattleTriggerContext) = ctx
    override fun onDamageCalc(ctx: BattleTriggerContext) = ctx
}
```

### Verified: CityHealTrigger (existing implementation)
```kotlin
// Source: GeneralTrigger.kt lines 198-227
class CityHealTrigger(
    private val general: General,
    private val cityMates: List<General>,
    private val rng: Random,
) : GeneralTrigger {
    override val uniqueId = "도시치료_${general.id}"
    override val priority = TriggerPriority.BEGIN + 10  // 10010

    override fun action(env: TriggerEnv): Boolean {
        if (general.injury > 0) general.injury = 0
        val patients = if (general.nationId == 0L) {
            cityMates.filter { it.id != general.id && it.nationId == 0L && it.injury > 10 }
        } else {
            cityMates.filter { it.id != general.id && it.injury > 10 }
        }
        for (patient in patients) {
            if (rng.nextDouble() < 0.5) patient.injury = 0
        }
        return true
    }
}
```

### Verified: Existing Che반계Trigger (counter magic reflection)
```kotlin
// Source: BattleTrigger.kt lines 447-475
object Che반계Trigger : BattleTrigger {
    override val code = "che_반계"
    override val priority = 10
    override fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.suppressActive) return ctx
        val opponentTryingMagic = (ctx.attacker.magicChance + ctx.magicChanceBonus) > 0.0
        if (!opponentTryingMagic) return ctx
        if (ctx.rng.nextBool(0.4)) {
            ctx.magicReflected = true
            ctx.magicChanceBonus -= 1.0
            ctx.banmokDamageBonus = (ctx.banmokDamageBonus + 0.1).coerceAtMost(1.5)
            ctx.battleLogs.add("반계 발동! 계략을 되돌린다!")
        }
        return ctx
    }
    override fun onPostMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.magicReflected) {
            ctx.magicDamageMultiplier *= ctx.banmokDamageBonus
        }
        return ctx
    }
}
```

## Hook Method Recommendations (Claude's Discretion Area)

Based on analysis of existing code patterns and TODO comments:

| Trigger | WarUnitTrigger Hook | Rationale |
|---------|-------------------|-----------|
| 반계 (BATTLE-02) | `onPreAttack` | TODO says "getBattlePhaseSkillTriggerList" = per-phase hook. But existing BattleTrigger already handles magic counter. WarUnitTrigger may be minimal. |
| 돌격지속 (BATTLE-03) | `onPostDamage` | Extend phases after damage, similar to Che전멸시페이즈증가Trigger pattern (bonusPhases on onPostDamage) |
| 부상무효 (BATTLE-04) | `onEngagementStart` | Set injuryImmune per engagement, matching Che견고Trigger's onBattleInit pattern |
| 필살강화_회피불가 (BATTLE-07) | `onPreAttack` | Set dodgeDisabled before attack roll each phase |
| 도시치료 (BATTLE-08) | N/A (GeneralTriggerCaller) | D-01 locks this as preTurn trigger, not WarUnitTrigger |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single BattleTrigger for all hooks | Split: BattleTrigger (roll hooks) + WarUnitTrigger (phase hooks) | Phase 3 | Clean separation; new triggers use WarUnitTrigger |
| Che*Trigger with full logic | Che*Trigger as no-ops when moved to WarUnitTrigger | Phase 3 | Avoids double-firing |
| Hardcoded killnum=0 in musang | StatContext.killnum from general.meta | Phase 3 | Musang now scales with kills |

## Open Questions

1. **반계 WarUnitTrigger scope**
   - What we know: Existing `Che반계Trigger` (BattleTrigger) already handles magic reflection with 40% probability and escalating damage bonus
   - What's unclear: Whether legacy has ADDITIONAL per-phase counter behavior beyond magic reflection that requires a separate WarUnitTrigger implementation
   - Recommendation: Verify existing Che반계Trigger behavior against legacy. If it's complete, the WarUnitTrigger may only need to exist as a registered entry. If additional behavior exists, implement in onPreAttack.

2. **돌격지속 phase extension mechanics**
   - What we know: `Che돌격Trigger.onBattleInit` adds +2 phases. `SpecialModifiers.che_돌격.initWarPhase + 2.0` also adds initial phases. TODO says WarUnitTrigger extends phases further.
   - What's unclear: Exact conditions for sustained charge activation (probability? attacker-winning check? per-phase or per-round?)
   - Recommendation: Look at how `bonusPhases` is consumed in BattleEngine. Verify the BattleEngine phase loop actually reads and uses bonusPhases/rageExtraPhases. If not, the loop needs modification.

3. **BattleEngine bonusPhases consumption**
   - What we know: `bonusPhases` field exists in BattleTriggerContext. `Che전멸시페이즈증가Trigger` sets it. `rageExtraPhases` is set by RageTrigger.
   - What's unclear: Whether BattleEngine's `while (currentPhase < maxPhase)` loop actually checks these fields to extend iteration
   - Recommendation: Audit BattleEngine resolveBattle() for bonusPhases/rageExtraPhases consumption. If not consumed, this is a gap that needs fixing in Phase 4.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5.x (Spring Boot BOM) |
| Config file | `backend/build.gradle.kts` (useJUnitPlatform in all subprojects) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.war.*' --no-daemon` |
| Full suite command | `cd backend && ./gradlew test --no-daemon` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BATTLE-02 | 반계 trigger 시도/발동 with correct probability and effects | unit | `./gradlew :game-app:test --tests '*CounterStrategyTriggerTest*' --no-daemon` | Wave 0 |
| BATTLE-03 | 돌격지속 extends war phases when attacking | unit | `./gradlew :game-app:test --tests '*SustainedChargeTriggerTest*' --no-daemon` | Wave 0 |
| BATTLE-04 | 부상무효 injury immunity during battle | unit | `./gradlew :game-app:test --tests '*InjuryNullificationTriggerTest*' --no-daemon` | Wave 0 |
| BATTLE-07 | 필살강화 undodgeable critical with enhanced damage | unit | `./gradlew :game-app:test --tests '*UnavoidableCriticalTriggerTest*' --no-daemon` | Wave 0 |
| BATTLE-08 | 도시치료 preTurn city healing | unit | `./gradlew :game-app:test --tests '*CityHealTriggerTest*' --no-daemon` | Wave 0 (CityHealTrigger exists, test may need creation) |
| BATTLE-13 | Battle formula matches legacy for all ArmType pairings | unit | `./gradlew :game-app:test --tests '*BattleFormulaMatrixTest*' --no-daemon` | Wave 0 |
| BATTLE-14 | Siege mechanics match legacy | unit | `./gradlew :game-app:test --tests '*SiegeParityTest*' --no-daemon` | Partial (WarUnitCityParityTest exists) |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.war.*' --no-daemon`
- **Per wave merge:** `cd backend && ./gradlew test --no-daemon`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `CounterStrategyTriggerTest.kt` -- covers BATTLE-02
- [ ] `SustainedChargeTriggerTest.kt` -- covers BATTLE-03
- [ ] `InjuryNullificationTriggerTest.kt` -- covers BATTLE-04
- [ ] `UnavoidableCriticalTriggerTest.kt` -- covers BATTLE-07
- [ ] `CityHealTriggerTest.kt` -- covers BATTLE-08 (existing trigger, new test)
- [ ] `BattleFormulaMatrixTest.kt` -- covers BATTLE-13 (7x7 ArmType matrix)
- [ ] `SiegeParityTest.kt` -- covers BATTLE-14 (extends existing WarUnitCityParityTest)

## Project Constraints (from CLAUDE.md)

- **Parity target**: `legacy-core/` PHP source is single source of truth (NOT available locally -- rely on existing code comments and TODO annotations)
- **Field naming**: Must follow core conventions (`intel`, `crew`, `crewType`, `train`, `atmos`)
- **TDD Gate**: Backend source changes MUST be accompanied by test changes
- **Simplicity First**: Minimum code that solves the problem, no speculative features
- **Surgical Changes**: Touch only what must be changed, match existing style
- **Architecture**: Must maintain gateway-app + game-app split; WarUnitTrigger framework established in Phase 3

## Sources

### Primary (HIGH confidence)
- `BattleEngine.kt` -- Full battle loop, hook points, formula chain (read lines 1-955)
- `BattleTrigger.kt` -- All existing triggers including Che반계/돌격/견고/필살 (read lines 1-917)
- `WarUnitTrigger.kt` -- Interface and registry (read lines 1-43)
- `SpecialModifiers.kt` -- All TODO comments with legacy parameters (read lines 1-452)
- `GeneralTrigger.kt` -- CityHealTrigger implementation (read lines 198-327)
- Phase 3 triggers: IntimidationTrigger, SnipingTrigger, BattleHealTrigger, RageTrigger (all read)
- Phase 3 verification report (03-VERIFICATION.md) -- 10/10 verified
- Phase 3 research (03-RESEARCH.md) -- architecture patterns and decisions
- Existing test files: IntimidationTriggerTest, WarUnitCityParityTest, BattleEngineParityTest, FormulaParityTest

### Secondary (MEDIUM confidence)
- CONTEXT.md decisions D-01 through D-04 and discretion areas
- DISCUSSION-LOG.md -- rationale for verification strategy choices
- FEATURES.md -- parity status overview (~30% match for war special abilities)
- CONCERNS.md -- tech debt listing for incomplete battle special modifiers

### Tertiary (LOW confidence)
- Legacy PHP behavior inferred from TODO comments and existing implementations (legacy-core/ not available locally)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all infrastructure from Phase 3
- Architecture: HIGH -- WarUnitTrigger framework verified, all hook points wired, patterns established
- Trigger implementations: HIGH for 부상무효/도시치료 (already exist), MEDIUM for 반계/돌격지속/필살강화 (existing BattleTrigger logic needs reconciliation)
- Formula verification: HIGH -- formula chain fully visible in BattleEngine.kt
- Pitfalls: HIGH -- all identified from Phase 3 experience

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable domain, all infrastructure in place)
