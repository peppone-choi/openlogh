# Phase 5: Modifier Pipeline - Research

**Researched:** 2026-04-01
**Domain:** Modifier pipeline parity -- item, special ability, officer rank effects on domestic commands
**Confidence:** HIGH

## Summary

Phase 5 addresses the completeness and correctness of the modifier pipeline for domestic commands. The existing Kotlin implementation has a well-structured 6-stage modifier collection (nation type -> personality -> war special -> domestic special -> items -> officer level) and a functional `applyDomesticModifiers()` fold pipeline. However, research uncovered a **critical actionCode mismatch bug** between what domestic commands pass as `actionCode` and what `SpecialModifiers` expects to match.

The DomesticCommand base class passes short-form actionKeys (e.g., "농업", "상업", "수비") to `DomesticUtils.applyModifier()`, but `SpecialModifiers` checks long-form actionCodes (e.g., "농지개간", "상업투자", "수비강화"). This means domestic specials like 농업, 상업, 보수, 발명, 건축 **never fire** for their intended DomesticCommand subclasses. NationTypeModifiers already handle both forms via `in listOf("농업", "농지개간")`, OfficerLevelModifier uses only short-form, and PersonalityModifiers use action-specific forms ("징병", "모병", "단련"). The fix must standardize actionCode matching across all modifier sources.

The legacy-core/ PHP source directory is **not present locally** (no git submodule configured), so PHP triggerCall verification must rely on the existing Kotlin implementation's inline comments and the actionCode patterns already established in NationTypeModifiers (which appear to have been audited against legacy). The CONTEXT.md decision D-01 to do "PHP 전수 읽기" will require cloning or re-attaching legacy-core/ first.

**Primary recommendation:** Fix the actionCode mismatch in SpecialModifiers to match both short and long forms (like NationTypeModifiers already does), then write golden value tests for each modifier source x actionCode combination to verify stacking behavior matches legacy.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** legacy-core/ triggerCall 사용처 전수 읽기 후 모디파이어 카탈로그 작성, Kotlin 코드와 비교하여 갭 발견
- **D-02:** 모디파이어 단위 golden value 테스트 + 대표 커맨드 3~5개 통합 파이프라인 테스트 모두 수행
- **D-03:** 레거시 PHP triggerCall 순서 확인, getModifiers() 순서 일치 검증, 복합 스태킹 golden value 테스트
- **D-04:** DomesticContext(onCalcDomestic) 주요 범위, stat/war 모디파이어 오류도 발견 즉시 수정

### Claude's Discretion
- PHP triggerCall 카탈로그 작성 시 세부 정리 형식
- 단위 테스트 클래스 분할 전략 (모디파이어 소스별 vs 커맨드별)
- 통합 테스트 대상 커맨드 선정 (대표 3~5개)
- golden value 추출 시 PHP 코드 수동 추적 vs 패턴 기반 추출

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MOD-01 | Complete item modifier effects on domestic commands (H3) | ItemModifiers.MiscItem.onCalcDomestic() already implements domesticSuccess, domesticSabotageSuccess, domesticSupplySuccess/Score, recruit triggerType. Need golden value tests to verify correctness. StatItem (weapons/books/horses) has no domestic effect by design (stat-only). |
| MOD-02 | Complete special ability effects on domestic commands (H4) | **CRITICAL BUG FOUND:** SpecialModifiers domestic specials use long-form actionCodes ("농지개간", "상업투자") but commands pass short-form ("농업", "상업"). Domestic specials never fire. Must fix actionCode matching. |
| MOD-03 | Complete officer rank modifier effects on domestic commands (H5) | OfficerLevelModifier.onCalcDomestic() uses short-form actionCodes ("농업", "상업", etc.) which match DomesticCommand.actionKey. Appears correct. Need golden value tests vs legacy level-set thresholds. |
| MOD-04 | Verify modifier stacking/priority matches legacy behavior | Pipeline order verified: nationType -> personality -> warSpecial -> domesticSpecial -> items(4) -> officerLevel. Must verify this matches legacy triggerCall order via PHP source audit. Multi-source stacking golden value tests needed. |
</phase_requirements>

## Architecture Patterns

### Existing Modifier Pipeline Architecture

```
ModifierService.getModifiers(general, nation)
  1. NationTypeModifiers.get(nation.typeCode)     -- nation type
  2. PersonalityModifiers.get(general.personalCode) -- personality
  3. SpecialModifiers.get(general.specialCode)      -- war special
  4. SpecialModifiers.get(general.special2Code)     -- domestic special
  5. ItemModifiers.get(general.weaponCode)          -- weapon
  6. ItemModifiers.get(general.bookCode)            -- book
  7. ItemModifiers.get(general.horseCode)           -- horse
  8. ItemModifiers.get(general.itemCode)            -- misc item
  9. OfficerLevelModifier(officerLevel, nationLevel) -- officer rank

ModifierService.applyDomesticModifiers(modifiers, baseCtx)
  -> sequential fold: for (mod in modifiers) { ctx = mod.onCalcDomestic(ctx) }
```

### ActionCode Flow Through Commands

```
DomesticCommand subclass (e.g., che_농지개간)
  |-- actionKey = "농업"  (short form, used for modifier matching)
  |-- actionName = "농지 개간"  (display name)
  |
  +-> DomesticUtils.applyModifier(services, general, nation, actionKey, varType, baseValue)
      |
      +-> DomesticContext(actionCode = actionKey)  // actionCode = "농업"
          |
          +-> Each modifier.onCalcDomestic(ctx) checks ctx.actionCode
```

### Command-to-ActionKey Mapping (Complete)

| Command Class | actionKey / ACTION_KEY | statKey | Uses DomesticCommand? |
|---|---|---|---|
| che_농지개간 | "농업" | "intel" | Yes (DomesticCommand) |
| che_상업투자 | "상업" | "intel" | Yes (DomesticCommand) |
| che_성벽보수 | "성벽" | (default) | Yes (DomesticCommand) |
| che_수비강화 | "수비" | (default) | Yes (DomesticCommand) |
| che_치안강화 | "치안" | (default) | Yes (DomesticCommand) |
| che_기술연구 | "기술" | "intel" | No (standalone GeneralCommand) |
| che_주민선정 | "민심" | "leadership" | No (standalone GeneralCommand) |
| che_정착장려 | "인구" | "leadership" | No (standalone GeneralCommand) |
| che_징병 | "징병" | n/a | No (standalone GeneralCommand) |
| che_모병 | "모병" (via actionName) | n/a | No (extends che_징병) |
| che_물자조달 | "조달" | n/a | No (standalone GeneralCommand) |
| che_훈련 | n/a (no modifier calls) | n/a | No |
| che_사기진작 | n/a (no modifier calls) | n/a | No |
| che_단련 | n/a (no modifier calls) | n/a | No |
| che_헌납 | n/a (no modifier calls) | n/a | No |
| che_소집해제 | n/a (no modifier calls) | n/a | No |

### ActionCode Matching Across Modifier Sources

| Modifier Source | Matching Strategy | Works with Current actionKey? |
|---|---|---|
| NationTypeModifiers | Dual form: `in listOf("농업", "농지개간")` | YES |
| PersonalityModifiers | Direct form: "징병", "모병", "단련" | YES (for their commands) |
| SpecialModifiers (domestic) | Long form only: "농지개간", "상업투자", etc. | **NO -- MISMATCH** |
| SpecialModifiers (che_ variants) | Mixed: "농업"/"농지개간", "상업"/"상업투자", etc. | Partial -- some use dual, some single |
| ItemModifiers (MiscItem) | Direct: "계략", "조달", "징병", "모병", "징집인구" | YES (for their commands) |
| OfficerLevelModifier | Short form: "농업", "상업", "기술", etc. | YES |

### Anti-Patterns to Avoid

- **Inconsistent actionCode conventions:** The root cause of MOD-02 bug. Each modifier source uses a different convention. Standardize on dual-form matching OR normalize actionCode at the pipeline entry point.
- **Testing modifiers in isolation without pipeline context:** A modifier may work in isolation but produce wrong results when stacked. Always test full pipeline stacking.
- **Modifying actionKey/actionCode strings in commands:** This would break other modifier sources. Fix at the modifier matching level, not the command level.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| ActionCode normalization | Per-modifier if/else chains for both forms | Add both forms to existing `in listOf()` patterns in SpecialModifiers (match NationTypeModifiers pattern) | NationTypeModifiers already solved this correctly |
| Golden value computation | Manual calculator | Trace through existing Kotlin pipeline with known inputs and lock expected outputs | PHP source unavailable locally; use existing Kotlin implementation as baseline after fixing known bugs |
| Test fixtures | Ad-hoc General/Nation creation per test | Reuse `createGeneral()` pattern from IntimidationTriggerTest / MusangKillnumTest | Consistent test setup |

## Common Pitfalls

### Pitfall 1: ActionCode Mismatch (ALREADY PRESENT)
**What goes wrong:** Domestic special modifiers (농업, 상업, 보수, 발명, 건축, etc.) never fire for their intended commands because actionCode doesn't match.
**Why it happens:** DomesticCommand.actionKey uses short-form ("농업") but SpecialModifiers.onCalcDomestic() checks long-form ("농지개간"). Inconsistent conventions across modifier sources.
**How to avoid:** Standardize all modifier actionCode checks to accept both forms, following NationTypeModifiers' existing pattern of `in listOf("농업", "농지개간")`.
**Warning signs:** A general with 농업 special gets no scoreMultiplier bonus when running 농지개간.

### Pitfall 2: Stacking Order Sensitivity
**What goes wrong:** Multipliers compound differently depending on application order. If legacy applies items before specials but Kotlin does the reverse, the final result diverges.
**Why it happens:** Multiplicative stacking is not commutative when mixed with additive operations (e.g., `successMultiplier += 0.1` vs `scoreMultiplier *= 1.2`).
**How to avoid:** Verify pipeline order against legacy triggerCall execution order. Golden value tests with multi-source stacking (nation type + special + item + officer) catch order sensitivity.
**Warning signs:** Tests pass for single-modifier scenarios but fail when multiple modifiers stack.

### Pitfall 3: Missing varType Coverage
**What goes wrong:** A modifier affects `costMultiplier` but not `scoreMultiplier`, or vice versa. The command applies modifiers for one varType but forgets another.
**Why it happens:** DomesticUtils.applyModifier() is called separately for each varType ("cost", "score", "success", "fail", "rice", "train", "atmos"). Each call creates a fresh DomesticContext -- modifiers must handle each varType independently.
**How to avoid:** Test each modifier for ALL varTypes it should affect, not just the primary one. Map modifier -> affected varTypes -> expected values.
**Warning signs:** Cost reduction works but score bonus doesn't, or vice versa.

### Pitfall 4: Consumable Items with Domestic Effects
**What goes wrong:** ConsumableItem implements ActionModifier but has NO onCalcDomestic override (it's a data class with defaults). Items like che_계략_이추 have `domesticSabotageSuccess` in their `stat` map, but ConsumableItem ignores stat maps.
**Why it happens:** Only MiscItem reads the `stat` map in onCalcDomestic(). ConsumableItem items with domestic stat entries silently do nothing.
**How to avoid:** Audit all consumable items with `stat` entries that include domestic-related keys. Either move domestic logic to ConsumableItem or ensure these items are classified as MiscItem.
**Warning signs:** Using 이추(계략 성공률 +20%p) has no effect on 계략 success rate.

### Pitfall 5: 징집인구 Special ActionCode
**What goes wrong:** The "징집인구" action code is used exclusively by che_징병 special to zero out population loss (`scoreMultiplier = 0.0`). It's a special pseudo-actionCode, not a real command.
**Why it happens:** Legacy PHP uses triggerCall with different actionKeys for different varTypes within the same command. che_징병 uses "징병" for cost/rice/train/atmos but "징집인구" for score.
**How to avoid:** Preserve "징집인구" as-is. It correctly matches in SpecialModifiers (che_징병 special) and ItemModifiers (recruit triggerType). Don't try to normalize it.
**Warning signs:** Attempting to unify actionCodes accidentally breaks the population-reduction nullification.

## Code Examples

### Pattern 1: How DomesticUtils.applyModifier Routes to Pipeline (Existing)

```kotlin
// Source: DomesticUtils.kt lines 90-124
fun applyModifier(
    services: CommandServices?,
    general: General, nation: Nation?,
    actionKey: String, varType: String, baseValue: Double
): Double {
    val modifiers = modifierService.getModifiers(general, nation)
    val baseCtx = when (varType) {
        "cost" -> DomesticContext(costMultiplier = baseValue, actionCode = actionKey)
        "score" -> DomesticContext(scoreMultiplier = baseValue, actionCode = actionKey)
        "success" -> DomesticContext(successMultiplier = baseValue, actionCode = actionKey)
        // ... other varTypes
    }
    val modified = modifierService.applyDomesticModifiers(modifiers, baseCtx)
    return when (varType) {
        "cost" -> modified.costMultiplier
        "score" -> modified.scoreMultiplier
        // ... extract matching field
    }
}
```

### Pattern 2: Fix for SpecialModifiers ActionCode Mismatch (Proposed)

```kotlin
// BEFORE (broken):
"농업" to object : ActionModifier {
    override fun onCalcDomestic(ctx: DomesticContext) = if (ctx.actionCode == "농지개간")
        ctx.copy(scoreMultiplier = ctx.scoreMultiplier * 1.2) else ctx
},

// AFTER (fixed, matching NationTypeModifiers pattern):
"농업" to object : ActionModifier {
    override fun onCalcDomestic(ctx: DomesticContext) = if (ctx.actionCode in listOf("농업", "농지개간"))
        ctx.copy(scoreMultiplier = ctx.scoreMultiplier * 1.2) else ctx
},
```

### Pattern 3: Golden Value Unit Test (Following Phase 4 Pattern)

```kotlin
// Source: Pattern from OfficerLevelModifierTest.kt + MusangKillnumTest.kt
@Test
fun `농업 special with 농지개간 action gives scoreMultiplier x1_2`() {
    val modifier = SpecialModifiers.get("농업")!!
    val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "농업")
    val result = modifier.onCalcDomestic(ctx)
    assertThat(result.scoreMultiplier).isEqualTo(1.2)
}

@Test
fun `농업 special with non-matching action returns unchanged`() {
    val modifier = SpecialModifiers.get("농업")!!
    val ctx = DomesticContext(scoreMultiplier = 1.0, actionCode = "상업")
    val result = modifier.onCalcDomestic(ctx)
    assertThat(result.scoreMultiplier).isEqualTo(1.0)
}
```

### Pattern 4: Stacking Golden Value Test

```kotlin
@Test
fun `왕도 nation + 농업 special + officerLevel 20 stack correctly for 농업 action`() {
    val modifiers = listOf(
        NationTypeModifiers.get("che_왕도")!!,   // scoreMultiplier *= 1.15
        SpecialModifiers.get("농업")!!,            // scoreMultiplier *= 1.2 (after fix)
        OfficerLevelModifier(20, 5),              // scoreMultiplier *= 1.05
    )
    val baseCtx = DomesticContext(scoreMultiplier = 1.0, actionCode = "농업")
    val result = ModifierService().applyDomesticModifiers(modifiers, baseCtx)

    // 1.0 * 1.15 * 1.2 * 1.05 = 1.449
    assertThat(result.scoreMultiplier).isCloseTo(1.449, Offset.offset(0.001))
}
```

## Critical Bug Analysis

### BUG-01: SpecialModifiers Domestic ActionCode Mismatch

**Severity:** HIGH -- domestic specials never fire for DomesticCommand subclasses
**Affected modifiers (13 domestic specials):**

| Special | Checks actionCode | Command Passes | Result |
|---------|------------------|----------------|--------|
| 농업 | "농지개간" | "농업" | NEVER FIRES |
| 상업 | "상업투자" | "상업" | NEVER FIRES |
| 보수 | "수비강화", "성벽보수" | "수비" or "성벽" | NEVER FIRES |
| 발명 | "기술연구" | "기술" | NEVER FIRES |
| 건축 | "수비강화", "성벽보수" | "수비" or "성벽" | NEVER FIRES |
| 인덕 | "주민선정", "정착장려" | "민심" or "인구" | NEVER FIRES |
| 의술 | "요양" | n/a (no 요양 command yet) | N/A |
| 치료 | "요양" | n/a | N/A |
| 등용 | "등용" | n/a (no 등용 command yet) | N/A |
| 정치 | (no actionCode check) | any | WORKS (unconditional) |
| 훈련_특기 | "훈련" | n/a (no modifier call in 훈련) | N/A |
| 모병_특기 | "모병", "징병" | "징병" or "모병" | WORKS |
| 징수 | "물자조달" | "조달" | NEVER FIRES |

**Also affected -- che_ variant specials:**

| Special | Checks actionCode | Command Passes | Result |
|---------|------------------|----------------|--------|
| che_인덕 | "민심","주민선정","인구","정착장려" | "민심" or "인구" | PARTIAL (short forms work) |
| che_발명 | "기술","기술연구" | "기술" | PARTIAL (short form works) |
| che_경작 | "농업","농지개간" | "농업" | WORKS (dual form) |
| che_상재 | "상업","상업투자" | "상업" | WORKS (dual form) |
| che_축성 | "성벽","성벽보수" | "성벽" | WORKS (dual form) |
| che_수비 | "수비","수비강화" | "수비" | WORKS (dual form) |
| che_통찰 | "치안","치안강화" | "치안" | WORKS (dual form) |

**Root cause:** The che_ variants were written AFTER NationTypeModifiers and adopted dual-form matching. The original domestic specials were written BEFORE this convention was established.

**Fix:** Add short-form actionCodes to the 13 original domestic specials' matching conditions, following the che_ variant pattern. This is a one-line change per modifier.

### BUG-02: 징수 Special Checks "물자조달" but Command Uses "조달"

**Severity:** MEDIUM -- 징수 special costMultiplier * 0.8 never fires
**Fix:** Change to `ctx.actionCode in listOf("조달", "물자조달")`

## Completeness Audit: Modifier Sources x Domestic varTypes

### Which modifier sources affect which DomesticContext fields?

| Modifier Source | costMultiplier | scoreMultiplier | successMultiplier | failMultiplier | riceMultiplier | trainMultiplier | atmosMultiplier |
|---|---|---|---|---|---|---|---|
| NationTypeModifiers | YES (conditional) | YES | YES (conditional) | - | - | - | - |
| PersonalityModifiers | YES (conditional) | YES | YES (conditional) | - | - | - | - |
| SpecialModifiers (domestic) | YES (징수/정치) | YES (농업/상업/보수/발명/인덕/건축/모병) | YES (의술/치료/등용) | - | - | YES (훈련) | - |
| SpecialModifiers (che_ domestic) | YES (인덕/발명/경작/상재/축성/수비/통찰/귀병/보병/궁병/기병/공성) | YES (인덕/발명/경작/상재/축성/수비/통찰/징병) | YES (인덕/발명/경작/상재/축성/수비/통찰/귀모/신산/은둔) | - | - | YES (징병: set 70) | YES (징병: set 84) |
| ItemModifiers (MiscItem) | - | YES (domesticSupplyScore) | YES (domesticSuccess/domesticSabotageSuccess/domesticSupplySuccess) | - | - | YES (recruit: set 70) | YES (recruit: set 84) |
| OfficerLevelModifier | - | YES (x1.05 conditional) | - | - | - | - | - |

## Modifier Stacking Order Verification

Current Kotlin order (from ModifierService.getModifiers()):
1. Nation type
2. Personality
3. War special (specialCode)
4. Domestic special (special2Code)
5. Item: weapon
6. Item: book
7. Item: horse
8. Item: misc
9. Officer level

This order needs to be verified against legacy PHP triggerCall execution order. The CONTEXT.md D-03 decision locks this as a required verification step. Without legacy-core/ present, this must be done when legacy source is available.

**Key observation:** Since all domestic modifiers use multiplicative stacking (multiply existing field value), the order within pure multipliers is commutative. Order only matters when mixing additive and multiplicative operations:
- Most: `scoreMultiplier = scoreMultiplier * 1.2` (multiplicative -- order doesn't matter)
- Some: `successMultiplier = successMultiplier + 0.1` (additive -- order matters if mixed with multiplicative)
- Rare: `trainMultiplier = 70.0` (absolute set -- order critical, last writer wins)

The "absolute set" pattern (trainMultiplier=70, atmosMultiplier=84) in che_징병 special and recruit items is the most order-sensitive. If both fire, last one wins -- which with current order would be the item (step 5-8) overwriting the special (step 4). If legacy applies them in the same order, this is correct.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 + AssertJ |
| Config file | `backend/game-app/build.gradle.kts` (testImplementation) |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.*"` |
| Full suite command | `cd backend && ./gradlew :game-app:test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MOD-01 | Item modifiers affect domestic commands | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.ItemDomesticModifierTest" -x` | Wave 0 |
| MOD-02 | Special ability modifiers affect domestic commands | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.SpecialDomesticModifierTest" -x` | Wave 0 |
| MOD-03 | Officer rank modifiers affect domestic commands | unit | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.OfficerLevelModifierTest" -x` | EXISTS (partial) |
| MOD-04 | Stacking/priority matches legacy | integration | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.ModifierStackingParityTest" -x` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.*" -x`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `SpecialDomesticModifierTest.kt` -- golden values for all 13 original domestic specials + che_ variants (MOD-02)
- [ ] `ItemDomesticModifierTest.kt` -- golden values for MiscItem domestic effects (MOD-01)
- [ ] `ModifierStackingParityTest.kt` -- multi-source stacking golden values (MOD-04)
- [ ] Extend existing `OfficerLevelModifierTest.kt` with domestic score bonus per-action tests (MOD-03)

## Recommended Integration Test Commands (Claude's Discretion)

Best representative commands for pipeline integration tests (covering diverse modifier interactions):

1. **che_농지개간** -- DomesticCommand subclass, actionKey="농업", statKey="intel". Tests score/cost/success modifiers through DomesticCommand.run().
2. **che_징병** -- Standalone GeneralCommand, actionKey="징병". Tests cost/rice/train/atmos/score(징집인구) modifiers. Unique "absolute set" pattern for train/atmos.
3. **che_기술연구** -- Standalone GeneralCommand, ACTION_KEY="기술". Tests score/success/fail modifiers. OfficerLevelModifier has TECH_LEVELS for this action.
4. **che_주민선정** -- Standalone GeneralCommand, ACTION_KEY="민심". Tests score/success/fail modifiers with leadership stat. Different OfficerLevelModifier level set (POP_LEVELS).
5. **che_물자조달** -- Standalone GeneralCommand, actionKey="조달". Tests success/fail/score modifiers with unique base ratios (0.1 success, 0.3 fail).

## Legacy Source Availability

The `legacy-core/` directory is **not present** in the working tree. No git submodule is configured. The CONTEXT.md references specific PHP files:
- `legacy-core/hwe/func.php` -- triggerCall function
- `legacy-core/hwe/sammo/Command/General/` -- 55 general commands
- `legacy-core/hwe/sammo/ActionSpecialDomestic.php`
- `legacy-core/hwe/sammo/ActionItem.php`
- `legacy-core/hwe/sammo/Trigger/OfficerLevel.php`

**Impact on D-01:** The PHP 전수 읽기 audit requires legacy-core/ to be cloned. The planner must include a task to clone or link `https://storage.hided.net/gitea/devsam/core` into `legacy-core/` before the audit can proceed.

**Mitigation:** NationTypeModifiers appears to have been written with both actionCode forms, suggesting a prior audit was done. The che_ variant specials in SpecialModifiers also use dual-form matching. The original domestic specials are the only ones with single-form matching, suggesting they were written before the convention was established. This gives MEDIUM confidence that the dual-form fix will achieve parity even without PHP verification.

## Open Questions

1. **Legacy triggerCall execution order**
   - What we know: Kotlin uses nationType -> personality -> warSpecial -> domesticSpecial -> items(4) -> officerLevel
   - What's unclear: Whether legacy PHP triggerCall iterates in this exact order
   - Recommendation: Clone legacy-core/, read func.php triggerCall to confirm. If order matches, lock it. If not, reorder getModifiers().

2. **Consumable items with domestic stat entries**
   - What we know: ConsumableItem (e.g., che_계략_이추) has `stat: {domesticSabotageSuccess: 0.2}` in items.json but ConsumableItem class has no onCalcDomestic override
   - What's unclear: Whether legacy PHP applies these consumable domestic effects
   - Recommendation: Check if consumables are single-use items activated before command execution (in which case the stat is informational only) or if they persist as modifiers

3. **Commands without modifier calls that should have them**
   - What we know: che_훈련, che_사기진작, che_단련 make no DomesticUtils.applyModifier() calls
   - What's unclear: Whether legacy PHP applies modifiers to these commands
   - Recommendation: Check during PHP audit. The 훈련_특기 special checks `actionCode == "훈련"` suggesting legacy DOES apply modifiers to training

## Sources

### Primary (HIGH confidence)
- Direct code reading: `ModifierService.kt`, `ActionModifier.kt`, `SpecialModifiers.kt`, `ItemModifiers.kt`, `OfficerLevelModifier.kt`, `NationTypeModifiers.kt`, `PersonalityModifiers.kt`
- Direct code reading: `DomesticCommand.kt`, `DomesticUtils.kt`, all `che_*.kt` command files
- Direct code reading: `CommandExecutor.kt` -- modifier injection in hydrateCommandForConstraintCheck()
- Existing tests: `OfficerLevelModifierTest.kt`, `MusangKillnumTest.kt`, `IntimidationTriggerTest.kt`, `FormulaParityTest.kt`

### Secondary (MEDIUM confidence)
- Phase 3/4 CONTEXT.md decisions on golden value testing pattern
- NationTypeModifiers dual-form matching as evidence of prior legacy audit
- items.json stat map entries for MiscItem domestic effects

### Tertiary (LOW confidence)
- Legacy PHP triggerCall order -- inferred from Kotlin code comments, not verified against PHP source (legacy-core/ not available)
- Consumable item domestic effect behavior -- unclear from code alone

## Metadata

**Confidence breakdown:**
- ActionCode mismatch bug: HIGH -- directly verified by reading SpecialModifiers vs DomesticCommand actionKey values
- Modifier pipeline architecture: HIGH -- complete code reading of all modifier and command files
- Stacking order: MEDIUM -- Kotlin order verified, legacy PHP order not yet confirmed
- Item modifier completeness: MEDIUM -- MiscItem patterns verified, consumable behavior unclear
- Pitfalls: HIGH -- derived from direct code analysis of actual runtime behavior

**Research date:** 2026-04-01
**Valid until:** 2026-05-01 (stable codebase, no external dependency changes expected)
