# Phase 3: Battle Framework and Core Triggers - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement the WarUnitTrigger framework for runtime battle-phase hooks, implement the four highest-impact battle abilities (intimidation, sniping, battle healing, rage), fix the 무쌍 killnum hardcode, and implement battle experience (C7) with level-up verification. All trigger outputs must match legacy PHP (process_war.php) for identical inputs.

Requirements: BATTLE-01, BATTLE-05, BATTLE-06, BATTLE-09, BATTLE-10, BATTLE-11, BATTLE-12

</domain>

<decisions>
## Implementation Decisions

### Trigger Framework Architecture
- **D-01:** WarUnitTrigger is a NEW independent interface, separate from ActionModifier. ActionModifier continues to handle stat modifications only. Each trigger (IntimidationTrigger, SnipingTrigger, BattleHealTrigger, RageTrigger) is a separate class implementing WarUnitTrigger. BattleEngine calls trigger lists at the correct battle phases (pre-attack, post-damage, post-round) matching legacy trigger points.

### 무쌍 killnum Data Access
- **D-02:** Add `killnum: Double` field to StatContext. ModifierService reads killnum from the General entity (general.killNum) and passes it into StatContext when constructing the context. This replaces the current `val killnum = 0.0` hardcode in SpecialModifiers.kt.

### Testing Strategy
- **D-03:** Both unit and integration tests, with unit tests prioritized. Each trigger gets its own test class with fixed-seed RNG to make probabilistic outcomes deterministic. PHP input/output golden values verify parity. Full-battle integration simulation tests are deferred to Phase 4.

### Battle Experience (C7) Scope
- **D-04:** Implement both XP calculation AND level-up formula verification. The full pipeline (battle participation → XP gain → level-up → stat growth) must be verified against legacy PHP. GeneralMaintenanceService already has level-up logic -- this phase verifies the XP feeding into it is correct and the combined pipeline produces identical outcomes.

### Claude's Discretion
- WarUnitTrigger hook method signatures (exact parameter types, return values) -- researcher should determine from legacy PHP trigger points
- Trigger registration mechanism (Spring DI collection vs manual registry) -- planner decides based on existing patterns
- Whether BattleTriggerContext needs new fields beyond what already exists

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/process_war.php` -- Battle resolution logic (33KB), trigger points, damage formulas, C7 XP calculation
- `legacy-core/hwe/sammo/Command/General/` -- General commands with special ability references
- `legacy-core/hwe/func.php` -- Game utility functions including RNG, stat calculations

### Existing Kotlin Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt` -- Current battle loop, where trigger hooks need to be inserted
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` -- BattleTriggerContext data class with existing state fields
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` -- TODO comments for all 8 triggers with legacy parameters (probabilities, damage ranges)
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ActionModifier.kt` -- ActionModifier interface (stat-only, NOT to be extended for triggers per D-01)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnit.kt` -- War unit abstraction
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitGeneral.kt` -- General war unit with coerceIn guards (Phase 2)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarAftermath.kt` -- Post-battle processing
- `backend/game-app/src/main/kotlin/com/opensam/engine/GeneralMaintenanceService.kt` -- Level-up logic (verify C7 XP feeds correctly)

### Test Patterns
- `backend/game-app/src/test/kotlin/com/opensam/engine/FormulaParityTest.kt` -- Existing formula parity test pattern
- `backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt` -- PHP golden value comparison pattern
- `backend/game-app/src/test/kotlin/com/opensam/engine/ShortOverflowGuardTest.kt` -- Boundary value test pattern

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BattleTriggerContext` -- Already has state fields for snipe, injuryImmune, counter, heal, rage, intimidated, moraleBoost, phaseNumber, battleLogs
- `DeterministicRng` / `LiteHashDRBG` -- Phase 1 deterministic RNG for reproducible trigger probability rolls
- `WarUnit` / `WarUnitGeneral` / `WarUnitCity` -- Existing war unit hierarchy with coerceIn guards from Phase 2
- `TriggerCaller` / `TriggerEnv` -- Existing trigger infrastructure for GeneralTrigger (turn-level triggers)

### Established Patterns
- Strategy pattern for commands (BaseCommand + CommandRegistry) -- similar pattern applicable for WarUnitTrigger registration
- Constructor injection via Kotlin primary constructor for all services
- coerceIn guards on all Short field assignments (Phase 2)
- Korean-language class naming for game concepts (matches legacy PHP)

### Integration Points
- `BattleEngine.kt` -- Main insertion point for WarUnitTrigger hooks in the battle loop
- `ModifierService.getModifiers()` -- Where killnum needs to be added to StatContext
- `WarAftermath.kt` -- Where battle XP (C7) results get applied to generals

</code_context>

<specifics>
## Specific Ideas

- SpecialModifiers.kt TODO comments contain exact legacy parameters: 위압 prob=0.4, 저격 prob=0.5 minDamage=20 maxDamage=40, etc.
- BattleTriggerContext already has the state fields needed -- triggers should read/write these fields
- Each trigger has 시도(attempt)/발동(activation) pattern in legacy -- must preserve this two-phase structure

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

*Phase: 03-battle-framework-and-core-triggers*
*Context gathered: 2026-04-01*
