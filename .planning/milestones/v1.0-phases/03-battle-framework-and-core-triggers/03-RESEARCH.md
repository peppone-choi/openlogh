# Phase 3: Battle Framework and Core Triggers - Research

**Researched:** 2026-04-01
**Domain:** Battle trigger system, combat ability parity, battle experience (C7), modifier pipeline fix
**Confidence:** HIGH

## Summary

Phase 3 implements a WarUnitTrigger framework for runtime battle-phase hooks and four core battle abilities (intimidation, sniping, battle healing, rage), plus fixes the hardcoded killnum in the musang modifier and verifies battle experience (C7) parity. The codebase already has extensive battle infrastructure: `BattleEngine.kt` with a full combat phase loop, `BattleTrigger.kt` with an interface and 30+ existing trigger implementations, and `BattleTriggerContext` with pre-built state fields for all four target abilities (snipe, heal, rage, intimidated).

The key finding is that **substantial trigger implementations already exist** in `BattleTrigger.kt` -- `Che위압Trigger`, `Che저격Trigger`, `Che의술Trigger`, and `Che격노Trigger` are already coded with non-trivial logic. However, the CONTEXT.md decision D-01 mandates a NEW `WarUnitTrigger` interface separate from `BattleTrigger`. The existing `BattleTrigger` implementations need to be evaluated for parity accuracy, then the new framework must be introduced alongside or replacing them. The TODO comments in `SpecialModifiers.kt` document the exact legacy parameters for each trigger.

**Primary recommendation:** Introduce the `WarUnitTrigger` interface per D-01, implement the four triggers with legacy-exact parameters from the TODO comments, verify existing `Che*Trigger` implementations match legacy behavior (fix where needed), add `killnum` to `StatContext`, and write golden-value parity tests for each trigger.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** WarUnitTrigger is a NEW independent interface, separate from ActionModifier. ActionModifier continues to handle stat modifications only. Each trigger (IntimidationTrigger, SnipingTrigger, BattleHealTrigger, RageTrigger) is a separate class implementing WarUnitTrigger. BattleEngine calls trigger lists at the correct battle phases (pre-attack, post-damage, post-round) matching legacy trigger points.
- **D-02:** Add `killnum: Double` field to StatContext. ModifierService reads killnum from the General entity (general.killNum) and passes it into StatContext when constructing the context. This replaces the current `val killnum = 0.0` hardcode in SpecialModifiers.kt.
- **D-03:** Both unit and integration tests, with unit tests prioritized. Each trigger gets its own test class with fixed-seed RNG to make probabilistic outcomes deterministic. PHP input/output golden values verify parity. Full-battle integration simulation tests are deferred to Phase 4.
- **D-04:** Implement both XP calculation AND level-up formula verification. The full pipeline (battle participation -> XP gain -> level-up -> stat growth) must be verified against legacy PHP. GeneralMaintenanceService already has level-up logic -- this phase verifies the XP feeding into it is correct and the combined pipeline produces identical outcomes.

### Claude's Discretion
- WarUnitTrigger hook method signatures (exact parameter types, return values) -- researcher should determine from legacy PHP trigger points
- Trigger registration mechanism (Spring DI collection vs manual registry) -- planner decides based on existing patterns
- Whether BattleTriggerContext needs new fields beyond what already exists

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| BATTLE-01 | Implement WarUnitTrigger framework for runtime battle-phase hooks | New interface per D-01; BattleEngine already has trigger hook points at onBattleInit, onPreCritical, onPostDamage etc.; new interface needs pre-attack, post-damage, post-round hooks |
| BATTLE-05 | Implement intimidation (위압) trigger (시도/발동) | Existing `Che위압Trigger` in BattleTrigger.kt; TODO in SpecialModifiers says prob=0.4; existing impl uses onBattleInit with no probability check -- needs parity fix |
| BATTLE-06 | Implement sniping (저격) trigger (시도/발동) | Existing `Che저격Trigger`; TODO says prob=0.5 minDamage=20 maxDamage=40; existing impl uses onBattleInit with `nextBool(0.5)` and `nextInt(20, 41)` -- parameters match |
| BATTLE-09 | Implement battle healing (전투치료) trigger (시도/발동) | Existing `Che의술Trigger` on onPostDamage with `nextBool(0.4)`, damage*0.7, injury=0; TODO says needs che_전투치료시도 + che_전투치료발동 pattern |
| BATTLE-10 | Implement rage (격노) trigger (시도/발동) | Existing `Che격노Trigger` with onPostCritical/onPostDodge/onDamageCalc; TODO says dynamic warPower = 1 + 0.2 * activatedSkillCount |
| BATTLE-11 | Implement battle experience (C7) | `WarUnitGeneral` already has `pendingLevelExp`/`pendingStatExp` fields; `BattleEngine.resolveBattle()` already accumulates damage/50 exp; `applyResults()` writes to general; needs parity verification |
| BATTLE-12 | Fix musang modifier to read killnum from runtime rank data | `SpecialModifiers.kt` line 380: `val killnum = 0.0` hardcode; D-02 says add killnum to StatContext; general.meta["rank"]["killnum"] is the source |
</phase_requirements>

## Architecture Patterns

### Existing Battle Trigger Architecture

The codebase already has a mature trigger system. Understanding what exists is critical before adding the new WarUnitTrigger interface.

**Current BattleTrigger interface** (`BattleTrigger.kt` lines 102-130):
```kotlin
interface BattleTrigger {
    val code: String
    val priority: Int
    fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPreCritical(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPostCritical(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPostDodge(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPostMagic(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onMagicFail(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onInjuryCheck(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext = ctx
}
```

**Current trigger collection** (`BattleEngine.kt` lines 685-691):
```kotlin
internal fun collectTriggers(unit: WarUnit): List<BattleTrigger> {
    if (unit !is WarUnitGeneral) return emptyList()
    return listOfNotNull(
        BattleTriggerRegistry.get(unit.general.specialCode),
        BattleTriggerRegistry.get(unit.general.special2Code),
    ).sortedBy { it.priority }
}
```

**Current trigger invocation** in `executeCombatPhase()` (lines 698-880):
- PRE triggers: `attackerTriggers.onPreCritical`, `defenderTriggers.onPreDodge`, both `onPreMagic`
- Critical roll -> POST: `attackerTriggers.onPostCritical`
- Dodge roll -> POST: `defenderTriggers.onPostDodge`
- Magic roll -> POST: `attackerTriggers.onPostMagic`, `defenderTriggers.onPostMagic`
- Damage calc: both sides `onDamageCalc`
- Post damage: both sides `onPostDamage`
- Injury check: `attackerTriggers.onInjuryCheck`

**Current BattleTriggerContext** already has fields for all four target abilities:
- `snipeActivated`, `snipeWoundAmount`, `snipeImmune` -- sniping
- `healAmount` -- healing
- `rageDamageStack`, `rageActivationCount`, `rageExtraPhases` -- rage
- `intimidated`, `intimidatePhasesRemaining` -- intimidation
- `criticalDisabled`, `dodgeDisabled`, `magicDisabled` -- suppression states

### Recommended WarUnitTrigger Architecture (per D-01)

The new `WarUnitTrigger` interface should map to legacy PHP trigger points. Based on the existing `BattleTrigger` hooks and the CONTEXT.md D-01 specification of "pre-attack, post-damage, post-round":

```kotlin
// New file: WarUnitTrigger.kt
interface WarUnitTrigger {
    val code: String
    val priority: Int
    
    // Legacy: getBattleInitSkillTriggerList -- once per engagement start
    fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    
    // Legacy: getBattlePhaseSkillTriggerList -- per phase, before attack roll
    fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    
    // Legacy: after damage applied, before next phase
    fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    
    // Legacy: after all phases of one engagement, before next defender
    fun onPostRound(ctx: BattleTriggerContext): BattleTriggerContext = ctx
}
```

**Registration:** Follow existing `BattleTriggerRegistry` singleton pattern (manual registry, not Spring DI). This matches project convention -- `SpecialModifiers` uses a manual `mapOf`, `BattleTriggerRegistry` uses a `listOf(...).associateBy { it.code }`.

### Relationship: WarUnitTrigger vs BattleTrigger

D-01 says WarUnitTrigger is separate from ActionModifier, but does NOT say it replaces BattleTrigger. The design decision is:

**Option A (recommended):** WarUnitTrigger is a companion to BattleTrigger. BattleTrigger handles fine-grained combat roll hooks (preCritical, preDodge, etc.), while WarUnitTrigger handles coarser battle-phase hooks (pre-attack, post-damage, post-round). BattleEngine calls BOTH trigger lists. The four target triggers (intimidation, sniping, healing, rage) are implemented as WarUnitTrigger. Existing Che*Trigger implementations in BattleTrigger.kt that overlap should be deprecated or removed if they duplicate WarUnitTrigger behavior.

**Key constraint:** The existing `Che위압Trigger`, `Che저격Trigger`, `Che의술Trigger`, `Che격노Trigger` objects ALREADY implement these abilities in the BattleTrigger interface. They need to be reconciled -- either:
1. Move their logic into new WarUnitTrigger implementations and remove from BattleTriggerRegistry, OR
2. Have WarUnitTrigger implementations delegate to them

Recommendation: Option 1 -- move the logic. The existing implementations are already registered and working but need parity verification anyway. Moving into WarUnitTrigger cleanly separates the concerns per D-01.

### Recommended Project Structure

```
engine/war/
  BattleEngine.kt           # Modified: add WarUnitTrigger hook calls
  BattleTrigger.kt          # Existing: keep BattleTrigger + existing triggers
  BattleTriggerContext.kt    # Extract from BattleTrigger.kt if needed
  WarUnitTrigger.kt          # NEW: interface + registry
  trigger/
    IntimidationTrigger.kt   # NEW: che_위압시도 + che_위압발동
    SnipingTrigger.kt        # NEW: che_저격시도 + che_저격발동
    BattleHealTrigger.kt     # NEW: che_전투치료시도 + che_전투치료발동
    RageTrigger.kt           # NEW: che_격노시도 + che_격노발동
```

### Anti-Patterns to Avoid

- **Extending ActionModifier for triggers:** D-01 explicitly forbids this. ActionModifier is for stat calculation only. Trigger side-effects (damage, healing, morale changes) belong in WarUnitTrigger.
- **Making triggers Spring beans:** The existing pattern uses object singletons registered in a manual registry. Do not break this pattern with `@Component` annotation and Spring collection injection.
- **Modifying BattleTriggerContext for killnum:** killnum goes in StatContext (D-02), not in BattleTriggerContext. The modifier pipeline runs BEFORE battle starts; BattleTriggerContext is for mid-battle state.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Deterministic RNG for tests | Custom RNG | `LiteHashDRBG.build(seed)` | Already built in Phase 1, proven parity |
| Battle trigger registration | Spring DI discovery | `BattleTriggerRegistry` pattern (manual map) | Existing pattern, predictable ordering |
| General stat construction | Manual field copying | Existing `StatContext` data class with `.copy()` | Already has all needed fields |
| Golden value comparison | Custom assertion helpers | JUnit `assertEquals` with explicit deltas | Project uses standard JUnit, no custom assertion framework |

## Common Pitfalls

### Pitfall 1: Double-Firing Existing Triggers
**What goes wrong:** If new WarUnitTrigger implementations coexist with existing Che*Trigger objects in BattleTriggerRegistry, the same ability fires twice (once from BattleTrigger, once from WarUnitTrigger).
**Why it happens:** The existing `Che위압Trigger`, `Che저격Trigger`, `Che의술Trigger`, `Che격노Trigger` are already registered in `BattleTriggerRegistry` and active.
**How to avoid:** When adding a WarUnitTrigger implementation for an ability, REMOVE the corresponding Che*Trigger from BattleTriggerRegistry. Or make them no-ops.
**Warning signs:** Trigger effects being doubled in test output (e.g., intimidation reducing atmos by 10 instead of 5).

### Pitfall 2: Probabilistic Tests Without Fixed Seeds
**What goes wrong:** Tests that check trigger activation pass/fail randomly.
**Why it happens:** Triggers like intimidation (prob=0.4) and sniping (prob=0.5) depend on RNG rolls.
**How to avoid:** D-03 mandates fixed-seed RNG. Use `LiteHashDRBG.build("specific_seed")` and pre-calculate which seeds produce activation vs. non-activation. The existing `BattleTriggerTest` uses `Random(42)`.
**Warning signs:** Tests passing locally but failing in CI, or vice versa.

### Pitfall 3: killnum Source Confusion
**What goes wrong:** Reading killnum from wrong location or wrong type.
**Why it happens:** killnum is stored in `general.meta["rank"]["killnum"]` as a nested map value (can be Number or null). Multiple places in codebase read it differently.
**How to avoid:** Follow the established pattern from `GeneralMaintenanceService.kt` line 204: `val killnum = readNumber(rank, "killnum")` where `rank = asMap(general.meta["rank"])`. The value is `Int` from meta, convert to `Double` for StatContext.
**Warning signs:** `ClassCastException` or `NullPointerException` when accessing nested meta map.

### Pitfall 4: Existing Che위압Trigger Has No Probability Check
**What goes wrong:** The current `Che위압Trigger.onBattleInit()` always fires (no `nextBool(0.4)` check), while the TODO says prob=0.4.
**Why it happens:** The existing implementation was a placeholder that always activates, not matching legacy behavior.
**How to avoid:** The new WarUnitTrigger implementation MUST include the 0.4 probability roll. Verify against legacy PHP trigger code.
**Warning signs:** Intimidation ALWAYS activating in tests instead of ~40% of the time.

### Pitfall 5: C7 Experience Already Partially Implemented
**What goes wrong:** Duplicating experience accumulation logic that already exists in BattleEngine.
**Why it happens:** `BattleEngine.resolveBattle()` already has C7 experience logic (lines 237-269): `attackerDamageDealtForExp / 50`, city capture +1000 exp, win/lose stat exp, atmos boost. `WarUnitGeneral.applyResults()` already writes pendingLevelExp/pendingStatExp to the general entity.
**How to avoid:** BATTLE-11 scope is VERIFICATION, not reimplementation. Write tests that confirm the existing C7 pipeline produces correct values, and fix any discrepancies found.
**Warning signs:** Tests showing correct exp values already -- meaning the work is verification, not implementation.

### Pitfall 6: Short Overflow in Trigger Effects
**What goes wrong:** Trigger effects that modify Short fields (atmos, train, injury) can overflow.
**Why it happens:** Phase 2 added coerceIn guards on entity field assignments, but in-battle modifications go through WarUnit int fields before being written back via applyResults().
**How to avoid:** All trigger modifications to atmos/train/injury must use `.coerceIn()` or `.coerceAtLeast(0)`. The existing code in `WarUnitGeneral.applyResults()` already applies coerceIn on the final write, but intermediate battle state should also be bounded.
**Warning signs:** Negative atmos values or injury > 80 in test output.

## Code Examples

### Existing Trigger Pattern (from BattleTrigger.kt)
```kotlin
// Source: BattleTrigger.kt lines 567-604
object Che위압Trigger : BattleTrigger {
    override val code = "che_위압"
    override val priority = 20
    override fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.intimidated = true
        ctx.dodgeDisabled = true
        ctx.criticalDisabled = true
        ctx.magicDisabled = true
        ctx.intimidatePhasesRemaining = 1
        if (ctx.defender is WarUnitGeneral) {
            ctx.defender.atmos = (ctx.defender.atmos - 5).coerceAtLeast(0)
        }
        ctx.battleLogs.add("위압 발동! 적이 위축되었다!")
        return ctx
    }
    // ... also onPreCritical, onPreMagic, onDamageCalc hooks
}
```

### killnum Access Pattern (from GeneralMaintenanceService.kt)
```kotlin
// Source: GeneralMaintenanceService.kt lines 202-204
val rank = asMap(general.meta["rank"])
val killnum = readNumber(rank, "killnum")
// readNumber safely casts: (map[key] as? Number)?.toInt() ?: 0
```

### Test Pattern with Fixed RNG (from BattleTriggerTest.kt)
```kotlin
// Source: BattleTriggerTest.kt lines 38-54
private fun makeCtx(
    attacker: WarUnit? = null,
    defender: WarUnit? = null,
    rng: Random = Random(42),
    phaseNumber: Int = 0,
    isVsCity: Boolean = false,
): BattleTriggerContext {
    val a = attacker ?: WarUnitGeneral(createGeneral())
    val d = defender ?: WarUnitGeneral(createGeneral(id = 2))
    return BattleTriggerContext(
        attacker = a, defender = d, rng = rng,
        phaseNumber = phaseNumber, isVsCity = isVsCity,
    )
}
```

### StatContext killnum Integration (D-02 target)
```kotlin
// In SpecialModifiers.kt, current code (line 380):
val killnum = 0.0  // TODO: hardcoded

// After D-02, reads from StatContext:
val killnum = stat.killnum  // New field on StatContext

// StatContext addition:
data class StatContext(
    // ... existing fields ...
    var killnum: Double = 0.0,  // NEW: from general.meta["rank"]["killnum"]
)
```

### Battle Experience (C7) Existing Implementation
```kotlin
// Source: BattleEngine.kt lines 237-269
// Per-phase level exp: damage/50; defenders get 0.8x multiplier
attacker.pendingLevelExp += attackerDamageDealtForExp / 50
for ((defUnit, damageReceived) in defenderDamageDealtForExp) {
    defUnit.pendingLevelExp += (damageReceived / 50 * 0.8).toInt()
}
// City capture: +1000 exp
if (cityOccupied) {
    attacker.pendingLevelExp += 1000
}
// Win/lose stat exp (+1 based on armType) and atmos boost
if (attackerWon && attacker.isAlive) {
    attacker.atmos = (attacker.atmos * 1.1).toInt().coerceAtMost(100)
    attacker.pendingStatExp += 1
}
```

### WarUnitGeneral.applyResults() C7 Pipeline
```kotlin
// Source: WarUnitGeneral.kt lines 147-172
fun applyResults() {
    general.crew = hp.coerceAtLeast(0)
    // ...
    if (pendingLevelExp > 0) {
        general.experience += pendingLevelExp
    }
    if (pendingStatExp > 0) {
        val unitCrewType = CrewType.fromCode(crewType)
        when (unitCrewType?.armType) {
            ArmType.WIZARD -> general.intelExp = (general.intelExp + pendingStatExp).coerceIn(0, 1000).toShort()
            ArmType.SIEGE -> general.leadershipExp = (general.leadershipExp + pendingStatExp).coerceIn(0, 1000).toShort()
            ArmType.MISC -> { /* all three */ }
            else -> general.strengthExp = (general.strengthExp + pendingStatExp).coerceIn(0, 1000).toShort()
        }
    }
}
```

## Trigger Parameter Reference (from TODO comments)

These are the exact parameters documented in `SpecialModifiers.kt` TODO comments:

| Trigger | Parameter | Value | Source |
|---------|-----------|-------|--------|
| 위압 (intimidation) | probability | 0.4 | SpecialModifiers.kt line 402 |
| 위압 | effect | reduces opponent crew/atmos | SpecialModifiers.kt line 402 |
| 저격 (sniping) | probability | 0.5 | SpecialModifiers.kt line 406 |
| 저격 | minDamage | 20 | SpecialModifiers.kt line 406 |
| 저격 | maxDamage | 40 | SpecialModifiers.kt line 406 |
| 전투치료 (battle healing) | pattern | 시도 + 발동 | SpecialModifiers.kt line 426 |
| 격노 (rage) | warPower formula | 1 + 0.2 * activatedSkillCount('격노') | SpecialModifiers.kt line 430 |
| 격노 | pattern | 시도 + 발동, accumulating per phase | SpecialModifiers.kt line 431 |

## Existing Implementation Gap Analysis

| Trigger | Existing Code | Gap |
|---------|--------------|-----|
| 위압 `Che위압Trigger` | Always fires, no 0.4 prob check; sets intimidated=true, dodgeDisabled, criticalDisabled, magicDisabled, atmos-5 | Missing probability roll; need to verify atmos reduction amount matches legacy |
| 저격 `Che저격Trigger` | onBattleInit with nextBool(0.5), nextInt(20,41), moraleBoost+=20 | Parameters match TODO; but fires on BattleInit only when `newOpponent=true` -- need to verify this matches legacy trigger point |
| 전투치료 `Che의술Trigger` | onPostDamage with nextBool(0.4), damage*0.7, injury=0 | Need to verify 0.4 probability and 0.7 damage reduction match legacy; need 시도/발동 two-phase structure |
| 격노 `Che격노Trigger` | onPostCritical(0.5 prob), onPostDodge(0.25 prob), onDamageCalc with rollCriticalDamageMultiplier | Need to verify dynamic warPower formula: 1+0.2*count matches legacy; existing uses rng critDamage multiplier instead |
| C7 experience | BattleEngine lines 237-269, WarUnitGeneral.applyResults() | Needs parity verification: damage/50 formula, 0.8x defender multiplier, +1000 city capture, win/lose stat exp amounts |
| 무쌍 killnum | SpecialModifiers.kt line 380: `val killnum = 0.0` | Straightforward: add `killnum` field to StatContext, populate from general.meta["rank"]["killnum"] |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter (via Spring Boot Test) |
| Config file | `backend/game-app/build.gradle.kts` (JUnit platform) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.war.*Trigger*" --no-daemon` |
| Full suite command | `cd backend && ./gradlew :game-app:test --no-daemon` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| BATTLE-01 | WarUnitTrigger hooks fire at correct battle phases | unit | `./gradlew :game-app:test --tests "*.WarUnitTriggerTest" -x` | Wave 0 |
| BATTLE-05 | Intimidation trigger matches legacy (prob=0.4, atmos reduction) | unit | `./gradlew :game-app:test --tests "*.IntimidationTriggerTest" -x` | Wave 0 |
| BATTLE-06 | Sniping trigger matches legacy (prob=0.5, damage 20-40) | unit | `./gradlew :game-app:test --tests "*.SnipingTriggerTest" -x` | Wave 0 |
| BATTLE-09 | Battle healing trigger matches legacy (시도/발동 pattern) | unit | `./gradlew :game-app:test --tests "*.BattleHealTriggerTest" -x` | Wave 0 |
| BATTLE-10 | Rage trigger matches legacy (accumulating per phase) | unit | `./gradlew :game-app:test --tests "*.RageTriggerTest" -x` | Wave 0 |
| BATTLE-11 | Battle experience (C7) parity with legacy | unit | `./gradlew :game-app:test --tests "*.BattleExperienceParityTest" -x` | Wave 0 |
| BATTLE-12 | Musang reads killnum from runtime rank data | unit | `./gradlew :game-app:test --tests "*.MusangKillnumTest" -x` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.war.*" --no-daemon`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test --no-daemon`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `WarUnitTriggerTest.kt` -- covers BATTLE-01 (framework hook verification)
- [ ] `IntimidationTriggerTest.kt` -- covers BATTLE-05
- [ ] `SnipingTriggerTest.kt` -- covers BATTLE-06
- [ ] `BattleHealTriggerTest.kt` -- covers BATTLE-09
- [ ] `RageTriggerTest.kt` -- covers BATTLE-10
- [ ] `BattleExperienceParityTest.kt` -- covers BATTLE-11
- [ ] `MusangKillnumTest.kt` -- covers BATTLE-12

Existing test infrastructure is mature (50+ test files in engine/). `BattleTriggerTest.kt` provides the established pattern for trigger unit tests. `BattleEngineParityTest.kt` provides the pattern for full-battle parity tests.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single BattleTrigger interface for all abilities | BattleTrigger + new WarUnitTrigger (phase-level hooks) | Phase 3 | Separates fine-grained roll hooks from coarser phase hooks |
| killnum hardcoded to 0.0 | Read from general.meta["rank"]["killnum"] via StatContext | Phase 3 | Musang special ability scales with actual kill count |
| C7 experience assumed correct | Verified against legacy golden values | Phase 3 | Ensures battle XP pipeline produces identical outcomes |

## Open Questions

1. **What is the exact legacy PHP trigger point for intimidation probability?**
   - What we know: TODO says prob=0.4, existing Che위압Trigger fires deterministically (no prob check)
   - What's unclear: Whether the 0.4 probability is per-engagement or per-phase in legacy PHP
   - Recommendation: The SpecialModifiers TODO says "시도" (attempt), implying per-engagement. Implement as per-engagement (onBattleInit equivalent) with 0.4 probability roll. Legacy-core/ is not available for verification, but the TODO comment is the closest documented source.

2. **Should existing Che*Trigger objects be removed or made into no-ops?**
   - What we know: They are registered in BattleTriggerRegistry and actively called by BattleEngine
   - What's unclear: Whether other code depends on their presence in the registry
   - Recommendation: Replace them with no-op stubs (empty method bodies) rather than removing from registry, to avoid breaking any code that checks `BattleTriggerRegistry.get("che_위압") != null`. Move real logic to WarUnitTrigger implementations.

3. **Exact rage warPower formula: multiplicative or additive?**
   - What we know: TODO says `warPower = 1 + 0.2 * activatedSkillCount('격노')`. Existing Che격노Trigger uses `rollCriticalDamageMultiplier()` (rng range 1.3-2.0) instead.
   - What's unclear: Whether the TODO or the existing implementation is more accurate to legacy
   - Recommendation: The TODO was written from legacy PHP analysis; the existing implementation looks like a different interpretation. Follow the TODO: `1 + 0.2 * count` as a multiplier. Write tests for both and let parity testing determine correctness.

## Environment Availability

Step 2.6: SKIPPED (no external dependencies identified). This phase is purely backend Kotlin code changes and tests. All required tools (JVM 17, Gradle 8.12, JUnit) are confirmed working -- test suite passed during research.

## Sources

### Primary (HIGH confidence)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt` -- Full battle loop, trigger invocation points, C7 experience accumulation
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` -- BattleTrigger interface, all 30+ existing trigger implementations, BattleTriggerRegistry
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTriggerContext` (in BattleTrigger.kt) -- All state fields for battle phase tracking
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` -- TODO comments with exact legacy parameters for all 4 target triggers + killnum hardcode
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ActionModifier.kt` -- StatContext data class (where killnum field goes)
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ModifierService.kt` -- Where StatContext is constructed with general data
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitGeneral.kt` -- pendingLevelExp/pendingStatExp, applyResults() C7 pipeline
- `backend/game-app/src/main/kotlin/com/opensam/engine/GeneralMaintenanceService.kt` -- Level-up formula (calcExpLevel, calcDedLevel), killnum access pattern
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleTriggerTest.kt` -- Existing trigger test pattern
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleEngineParityTest.kt` -- Full battle parity test pattern
- `.planning/phases/03-battle-framework-and-core-triggers/03-CONTEXT.md` -- Locked decisions D-01 through D-04

### Secondary (MEDIUM confidence)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarAftermath.kt` -- Post-battle processing (not directly modified in this phase)
- `backend/game-app/src/test/kotlin/com/opensam/engine/FormulaParityTest.kt` -- General formula parity test pattern

### Tertiary (LOW confidence)
- Legacy PHP source (`legacy-core/`) is NOT available on disk (directory not found). The TODO comments in SpecialModifiers.kt are the proxy source for legacy parameters. The CONTEXT.md canonical_refs list `legacy-core/hwe/process_war.php` but it cannot be verified directly.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All existing code patterns are well-established and verified by running tests
- Architecture: HIGH - BattleTrigger system is mature with 30+ implementations; WarUnitTrigger design follows established patterns
- Pitfalls: HIGH - Based on direct code reading showing existing implementation gaps and potential double-fire issues
- C7 experience: MEDIUM - Implementation appears correct from code reading but legacy source unavailable for direct comparison
- Trigger parameters: MEDIUM - Based on TODO comments (not direct PHP source verification since legacy-core/ is unavailable)

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable domain -- game engine code does not change rapidly)
