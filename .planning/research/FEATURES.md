# Feature Landscape: Legacy Parity Verification

**Domain:** Three Kingdoms turn-based strategy game (PHP-to-Kotlin/Next.js port)
**Researched:** 2026-03-31
**Overall confidence:** HIGH (based on codebase analysis + existing parity analysis docs)

---

## Table Stakes

Features that must match legacy PHP exactly or gameplay breaks. These are the core game systems where even small formula differences cascade into completely different game outcomes. Prioritized by gameplay impact.

### Tier 1: Battle-Affecting Systems (gameplay-breaking if wrong)

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Battle damage formulas** | Wrong damage = wrong winner = wrong game progression | High | ~85% match | C7 (battle XP) and C8 (defender injury) partially fixed; WarUnitTrigger system still incomplete |
| **War special abilities (WarUnitTrigger)** | 14+ abilities are no-ops in combat: 위압, 저격, 의술, 격노, 돌격지속, 부상무효, 반계, 필살강화 | High | ~30% match | Only stat modifiers work; runtime battle-phase triggers are entirely unimplemented |
| **Dex/Tech level calculations** | Wrong dex = wrong hit/dodge = wrong battle outcomes | Low | 100% match | getDexLevel, getDexLog, getTechLevel all verified via BattleParityTest |
| **CrewType combat coefficients** | Unit type matchups determine battle balance | Medium | ~95% match | 7 armTypes, 30+ CrewTypes defined in enum; attack/defence coefs present but phase/init triggers incomplete |
| **Battle order and phase loop** | Determines who fights whom and how many rounds | High | ~90% match | BattleEngine phase loop exists; maxPhase from CrewType.speed; defender sorting by battleOrder |
| **Battle aftermath** | Death, capture, injury, city occupation logic | Medium | ~80% match | WarAftermath exists; C8 (defender injury) fixed; capital relocation fixed (C10) |
| **무쌍 kill count scaling** | Hardcoded killnum=0 means 무쌍 never scales with kills | Low | BROKEN | Must pass RankColumn.killnum through StatContext |

### Tier 2: Command System (player actions must match)

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **55 general commands** | Every player action; wrong formula = wrong resource/stat delta | High | ~80% match | 19/21 CRITICAL fixed; C2 (물자조달 formula) still open; H3/H4 modifier gaps |
| **38 nation commands** | Nation leader actions affect all members | High | ~85% match | C16/C17/C18 (백성동원/이호경식/감축) rewritten; C19 (research values) fixed; C20 (diplomacy checks) added |
| **Command constraints** | Pre-conditions gate what actions are allowed | Medium | ~90% match | ConstraintChain system exists; 7 diplomacy constraints added (C20); some constraint gaps remain |
| **Command cost/cooldown** | postReqTurn determines action tempo | Low | ~85% match | H14: 6 commands had wrong cooldowns; 4 fixed (급습40, 의병100, 수몰20, 허보20) |
| **Domestic modifier pipeline** | Items, specials, nation type affect domestic commands | High | ~60% match | H3/H4/H5: onCalcDomestic modifiers, getDomesticExpLevelBonus, CriticalScoreEx all incomplete |
| **Command visibility** | Some commands should be hidden from UI | Low | Fixed | C21: 5 commands now have canDisplay=false |

### Tier 3: Economy System (resource balance)

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **City gold/rice income** | Wrong income = wrong resource balance = wrong game pace | Medium | ~90% match | EconomyFormulaParityTest covers calcCityGoldIncome/calcCityRiceIncome; constants verified |
| **Tax/bill rate calculations** | getBill(), getOutcome() determine nation revenue | Medium | ~90% match | Nation level thresholds match; bill/rate formulas tested |
| **Population growth** | basePopIncreaseAmount=5000; growth formulas | Low | ~90% match | Constant matches legacy; formula coverage in EconomyFormulaParityTest |
| **Disaster/boom events** | Random events affect city economy each turn | Medium | ~80% match | DisasterAndTradeStep exists; RaiseDisasterAction implemented; exact trigger probabilities need verification |
| **Supply route BFS** | Cities cut off from supply lose resources | Medium | ~85% match | EconomyService.updateCitySupplyState uses BFS; adjacency via MapService |
| **Auction system** | System rice/gold auctions with NPC pricing | Medium | ~85% match | registerAuction in TurnService uses java.util.Random (non-deterministic -- should use DeterministicRng) |
| **Semi-annual income events** | Bi-annual income distribution to generals | Medium | ~80% match | ProcessIncomeAction, ProcessSemiAnnualAction exist; salary formula needs verification |

### Tier 4: Turn Engine (processing order)

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Turn step ordering** | Wrong order = different game state each month | High | ~90% match | 17 TurnStep beans auto-collected; ordered 100-900+; matches legacy daemon.ts flow |
| **checkWander (wander dissolution)** | Wander nations never dissolve without this | Low | STUB | TurnService.checkWander() is empty TODO; wander nations should dissolve after foundYear+2 |
| **updateOnline** | Online count tracking per tick | Low | STUB | TurnService.updateOnline() is empty; low gameplay impact |
| **updateGeneralNumber** | Refresh nation general count | Low | STUB | TurnService.updateGeneralNumber() partially covered by resetStrategicCommandLimits |
| **checkOverhead** | Runaway process guard | Low | STUB | TurnService.checkOverhead() is empty; operational safety, not gameplay |
| **Monthly event dispatch** | Events fire at correct year/month | Medium | ~90% match | EventService.dispatchEvents with condition evaluation; PreMonthEventStep + MonthEventStep |
| **Tournament auto-trigger** | Random tournament scheduling | Low | ~90% match | triggerTournament delegates to TournamentService; 40% chance + pattern queue |

### Tier 5: NPC AI (game progression)

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Wanderer AI decisions** | NPC wanderers must join nations, found nations correctly | High | ~75% match | decideWandererAction exists; some constants fixed (C11-C14); detailed decision tree needs verification |
| **Military AI** | NPC attack target selection, troop movement | High | ~70% match | C13 (front condition) fixed; overall military decision tree is ~40 do*() methods |
| **Domestic AI** | NPC internal affairs priority | Medium | ~75% match | Priority ordering exists but detailed formula comparison incomplete |
| **Nation AI diplomacy** | NPC war declaration, peace proposals | High | ~60% match | H8: non-aggression logic "completely different"; C12 (declaration probability) fixed |
| **NPC spawn/respawn** | NPC general creation at correct times | Medium | ~80% match | NpcSpawnService, CreateManyNpcAction, RegNpcAction all exist |
| **Troop leader rally** | npcState=5 generals always rally | Low | 100% match | Hardcoded early return in GeneralAI.decideAndExecute |
| **AI personality/trait** | Personality affects NPC behavior branches | Medium | ~70% match | NpcPolicy exists; personality-based branching partially implemented |

### Tier 6: Diplomacy System

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Diplomacy state transitions** | War/peace/non-aggression state machine | Medium | ~85% match | DiplomacyService with 6 state codes; term constants match legacy |
| **7 diplomacy commands** | Declaration, ceasefire, non-aggression propose/accept/break | Medium | ~90% match | C20 fixed: all 7 commands now check diplomacy state |
| **Diplomacy turn processing** | Auto-transition states each turn (term decrement) | Medium | ~85% match | processDiplomacyTurn in DiplomacyStep |
| **War income** | Nations at war get modified income | Low | ~80% match | ProcessWarIncomeAction exists |

### Tier 7: Scenario and Game Data

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Scenario loading (84 scenarios)** | Initial game state must match legacy | Medium | ~95% match | 84+ scenario JSONs (legacy had 83 + 1 added); ScenarioService handles loading |
| **Map data (9 maps)** | City positions, connections, terrain | Low | ~95% match | 9 map JSONs (che, miniche, cr, chess, duel, etc.); legacy had 8 + duel added |
| **Officer rank system** | Nation level determines available ranks | Low | ~95% match | officer_ranks.json with 7 nation levels + special (황건) |
| **Item/equipment data** | Items affect stats, commands, battle | Low | ~90% match | items.json exists; ItemModifiers apply stat bonuses |
| **General pool** | NPC general stats (5-stat) | Low | ~90% match | general_pool.json; 5-stat extension (politics/charm) is intentional change |
| **Game constants** | Base values for all calculations | Low | ~95% match | game_const.json; C1 showed fallback values were wrong but JSON was correct |

### Tier 8: Frontend Display Parity

| Feature | Why Expected | Complexity | Parity Status | Notes |
|---------|--------------|------------|---------------|-------|
| **Game dashboard info** | Main screen must show all relevant data | Medium | ~85% match | game-dashboard.tsx delegates to multiple components |
| **General detail display** | All stats, equipment, rank, position visible | Medium | ~85% match | generals/[id]/page.tsx exists; 5-stat display is intentional extension |
| **Nation info display** | Finance, generals, cities, diplomacy | Medium | ~85% match | nation, nation-cities, nation-finance, nation-generals pages exist |
| **Battle log display** | Combat results readable and accurate | Medium | ~80% match | battle page + formatLog utility; color tags parsed |
| **Map display** | Interactive map with city ownership, troop positions | Medium | ~80% match | map/page.tsx with map-constants; visual parity screenshots exist |
| **Command UI** | All valid commands selectable with correct args | Medium | ~85% match | commands/page.tsx + command-panel component; ArgSchema system |
| **Ranking/Hall of Fame** | Rankings match legacy calculation | Low | ~90% match | best-generals, hall-of-fame pages; RankingService exists |
| **History/Yearbook** | Game history records match | Low | ~85% match | history page; YearbookService + WorldSnapshotStep |

---

## Differentiators

Features that OpenSamguk adds beyond legacy. These are intentional extensions -- not parity targets. Parity formulas must NOT use these where legacy does not.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **5-stat system** (politics/charm) | Richer general differentiation; legacy 3-stat formulas use only original 3 | Medium | Already implemented; parity formulas must ignore politics/charm |
| **CQRS turn engine** | In-memory processing with dirty tracking for performance | High | Already implemented; architectural improvement over legacy direct DB writes |
| **Multi-process architecture** | Gateway + Game JVM separation for scalability | High | Already implemented; legacy was single PHP process |
| **JWT/OAuth authentication** | Modern auth replacing legacy session-based | Medium | Already implemented; Kakao OAuth + OTP support |
| **WebSocket real-time updates** | Live turn/battle/diplomacy events without polling | Medium | Already implemented; STOMP/SockJS over 4 topic channels |
| **Battle simulator** | Test battle outcomes without real combat | Medium | BattleSimService + battle-simulator page; not in legacy |
| **Interactive tutorial** | 8-step guided tutorial for new players | Medium | Tutorial route group with mock data; not in legacy |
| **Modern SPA UI** | shadcn/ui + Zustand + responsive design | High | Complete replacement of PHP-rendered pages |
| **Deterministic RNG** | DeterministicRng/LiteHashDRBG for reproducible outcomes | Medium | Improvement over legacy; enables replay/debugging |
| **Docker deployment** | Containerized deployment with docker-compose | Low | opensamguk-deploy repo; legacy was bare-metal |
| **Admin game versions** | Multiple game-app versions can coexist | Low | AdminGameVersionController; legacy did not version game logic |
| **Duel map** | Additional map type not in legacy | Low | duel.json; 9th map added |

---

## Anti-Features

Features to deliberately NOT port from legacy. Either they are bad patterns, replaced by better alternatives, or unnecessary.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **MariaDB-specific SQL** | PostgreSQL is the target DB; MariaDB-isms would break | Use PostgreSQL-native SQL in Flyway migrations |
| **Session-based auth** | Less secure, does not scale across processes | Keep JWT/OAuth (already done) |
| **`select_npc_token` DB table** | Polling DB for NPC token is slow | Keep Redis-based NPC token (already done) |
| **Per-server PHP profiles** | Does not fit multi-process architecture | Keep World = Profile model (already done) |
| **PHP-style inline HTML** | Legacy mixes logic and presentation | Keep clean API + SPA separation |
| **Legacy func.php god-function** | 80KB monolith with all game logic mixed together | Keep domain-separated services (EconomyService, DiplomacyService, etc.) |
| **Legacy GeneralAI.php monolith** | 4,293 lines / 153KB in one file; unmaintainable | Keep split into GeneralAI + NationAI + NpcPolicy (but GeneralAI.kt at 3,741 lines still needs further splitting) |
| **Non-deterministic RNG** | Legacy uses mt_rand() everywhere; unreproducible | Keep DeterministicRng.create() pattern; FIX remaining java.util.Random() in TurnService.registerAuction and GeneralTrigger |
| **No test coverage** | Legacy has zero tests | Keep and expand existing 1136 backend + 237 frontend tests |
| **Direct DB writes during turn** | Legacy writes to DB mid-turn processing | Keep CQRS in-memory processing + bulk persist pattern |
| **Schema-level DB comparison** | Schemas intentionally differ (PostgreSQL vs MariaDB) | Verify at formula/logic level, not schema level |

---

## Feature Dependencies

Critical ordering constraints -- features that must be verified/fixed before others make sense.

```
Modifier System (H3/H4/H5)
  |
  +---> Domestic Command Parity (C2: 물자조달)
  |       Modifiers must work before domestic command formulas can be fully verified
  |
  +---> Battle Special Abilities (WarUnitTrigger)
          Modifier stat context must be correct before battle triggers can use it

WarUnitTrigger System
  |
  +---> 무쌍 kill count (needs RankColumn.killnum in StatContext)
  +---> 반계 triggers (needs battle phase hook)
  +---> 위압/저격/의술/격노 (needs full trigger lifecycle)
  +---> 돌격지속 (needs phase extension logic)
  +---> 부상무효/필살강화 (needs injury/critical hooks)

Battle XP (C7)
  |
  +---> General stat progression
  |       Without battle XP, generals do not level up from combat
  |
  +---> NPC AI combat evaluation
          AI may mis-evaluate combat readiness if XP is missing

Turn Engine Stubs
  |
  +---> checkWander
  |       Wander nations accumulate forever without dissolution
  |
  +---> updateGeneralNumber
          Nation general counts may drift from actual

Economy Formulas
  |
  +---> Semi-annual income (ProcessSemiAnnualAction)
  |       Salary distribution to generals depends on income
  |
  +---> War income (ProcessWarIncomeAction)
          Wartime income modifiers depend on base income being correct

Deterministic RNG Fix
  |
  +---> Auction system (TurnService.registerAuction)
  |       Non-deterministic auctions = unreproducible game state
  |
  +---> GeneralTrigger (line 200)
          Non-deterministic triggers = unreproducible outcomes

Diplomacy Commands
  |
  +---> NPC AI Diplomacy (H8)
          NPC diplomacy decisions depend on correct state transitions
```

---

## Parity Verification Priority Matrix

Based on gameplay impact, current gap size, and dependency ordering.

### Priority 1: CRITICAL remaining issues (fix these first)

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **C7: Battle XP** | HIGH | Medium | Generals do not gain experience from combat; affects entire progression |
| **C2: 물자조달 formula** | HIGH | Medium | getDomesticExpLevelBonus + CriticalScoreEx not applied; needs modifier system review |
| **checkWander stub** | MEDIUM | Low | Wander nations never dissolve; game state pollution |
| **Non-deterministic RNG** | MEDIUM | Low | 2 remaining java.util.Random() instances break reproducibility |

### Priority 2: WarUnitTrigger system (largest remaining gap)

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **WarUnitTrigger framework** | HIGH | High | Prerequisite for all 14+ battle abilities |
| **위압 (intimidation)** | HIGH | Medium | Reduces opponent crew/atmos; changes battle dynamics significantly |
| **저격 (snipe)** | HIGH | Medium | Direct HP damage bypassing normal combat; powerful ability |
| **격노 (rage)** | MEDIUM | Medium | Accumulating warPower per phase; changes long battles |
| **의술 (medicine)** | MEDIUM | Medium | City healing + battle HP recovery |
| **돌격지속 (charge persist)** | MEDIUM | Low | Extends war phases for attacker |
| **부상무효 (injury immunity)** | LOW | Low | Prevents injury during battle |
| **필살강화 (enhanced critical)** | LOW | Low | Undodgeable critical hit |
| **반계 triggers** | LOW | Medium | Counter-strategy battle triggers |
| **무쌍 killnum** | MEDIUM | Low | Just needs RankColumn.killnum piped through StatContext |

### Priority 3: Modifier system gaps (H3/H4/H5)

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **onCalcDomestic pipeline** | HIGH | High | Items and specials do not affect domestic commands properly |
| **Stat getter with modifiers** | MEDIUM | Medium | Raw stats used instead of modified stats in some calculations |
| **getDomesticExpLevelBonus** | MEDIUM | Medium | Experience level bonus not applied to domestic output |

### Priority 4: NPC AI refinement

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **H8: Non-aggression logic** | HIGH | High | NPC diplomacy is "completely different" from legacy |
| **H9: Tax/officer simplification** | MEDIUM | Medium | NPC nation management differs |
| **H10: Reward calculation** | LOW | Medium | NPC resource distribution simplified |
| **Detailed military AI** | MEDIUM | High | 40+ do*() methods need individual verification |

### Priority 5: Economy and event verification

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **Economy formula full audit** | MEDIUM | High | Phase 5 of legacy plan not yet executed |
| **Event trigger probabilities** | MEDIUM | Medium | Disaster/boom exact probabilities need verification |
| **Semi-annual salary formulas** | LOW | Medium | Income distribution details |

### Priority 6: Frontend display verification

| Item | Impact | Effort | Rationale |
|------|--------|--------|-----------|
| **Data completeness per page** | LOW | Medium | 30+ game pages exist; need to verify all info fields present |
| **Battle log formatting** | LOW | Low | Color tags and message format |
| **Map visual accuracy** | LOW | Medium | City positions, connections rendering |

---

## Parity Testing Strategy

### Existing Coverage

| Test Suite | What It Verifies | Gap |
|------------|-----------------|-----|
| `CommandParityTest` | ~10 command formulas (훈련, 징병, 상업투자, etc.) | ~83 commands untested |
| `BattleParityTest` | warPower, dexLevel, attack/defence, critical, dodge | WarUnitTrigger, battle aftermath |
| `EconomyFormulaParityTest` | Income formulas, nation level | Decay, supply chain, salary |
| `EconomyEventParityTest` | Economy event actions | Disaster probabilities |
| `NpcAiParityTest` | AI decision tree basics | 40+ do*() methods detail |
| `ConstraintParityTest` | 11 constraint types | Command-specific constraints |
| `TurnPipelineParityTest` | 17 step ordering | Step internals |
| `TechResearchParityTest` | Research formulas | Research prerequisites |

### Recommended Testing Additions

| Test Type | Purpose | Complexity | Priority |
|-----------|---------|------------|----------|
| **Tag all parity tests `@Tag("parity")`** | Enable separate CI runs | Trivial | Immediate |
| **Expand command parity to all 93** | 83 untested commands need golden value tests | High | Priority 1 |
| **WarUnitTrigger integration tests** | Verify battle abilities fire correctly in BattleEngine | High | Priority 2 |
| **jqwik property-based formula tests** | Catch rounding/overflow edge cases in economy/battle | Medium | Priority 3 |
| **Multi-turn golden snapshots** | War scenario, economy cycle, NPC turn regression | Medium | Priority 4 |
| **Playwright visual regression** | Use existing `parity-screenshots/` baselines | Medium | Priority 5 |

---

## MVP Parity Recommendation

The game is already substantially implemented (90% match rate per existing analysis). To reach production-ready parity:

**Prioritize (must-fix before launch):**
1. **C7: Battle experience system** -- without this, general progression is broken
2. **WarUnitTrigger framework + top 4 abilities** (위압, 저격, 격노, 의술) -- these 4 abilities fundamentally change battle outcomes
3. **checkWander stub** -- prevents wander nation pollution; quick fix
4. **Non-deterministic RNG fix** -- 2 instances of java.util.Random(); quick fix
5. **C2: 물자조달 + modifier pipeline** (H3/H4/H5) -- domestic commands do not fully account for items/specials

**Defer (fix post-launch or in parallel):**
- **Remaining WarUnitTrigger abilities** (돌격지속, 부상무효, 필살강화, 반계): lower gameplay impact
- **NPC AI diplomacy refinement** (H8/H9/H10): NPCs will still function, just with different diplomatic personality
- **Economy full audit**: existing formulas are ~90% correct; differences are marginal
- **Frontend display parity**: UI is intentionally different; only data accuracy matters
- **updateOnline/checkOverhead stubs**: operational, not gameplay-affecting

**Do NOT fix (intentional changes):**
- 5-stat extension, PostgreSQL migration, JWT auth, CQRS engine, World = Profile model, Redis NPC tokens

---

## Sources

- Codebase analysis: `backend/game-app/src/main/kotlin/com/opensam/` (engine, command, entity, service directories)
- Parity analysis: `docs/03-analysis/legacy-parity.analysis.md` (90.5% CRITICAL match rate, 19/21 resolved)
- Parity plan: `docs/01-plan/features/legacy-parity.plan.md` (10-phase verification plan)
- Command registry: `CommandRegistry.kt` (55 general + 38 nation = 93 registered commands)
- Turn pipeline: 17 TurnStep beans in `engine/turn/steps/`
- Battle engine: `engine/war/` (BattleEngine, WarFormula, WarUnit*, BattleTrigger, WarAftermath)
- Special modifiers: `engine/modifier/SpecialModifiers.kt` (14+ TODO items for WarUnitTrigger)
- Parity tests: `qa/parity/` (8 test classes)
- Concerns audit: `.planning/codebase/CONCERNS.md`
- Architecture: `.planning/codebase/ARCHITECTURE.md`
- Confidence: HIGH -- all findings based on direct codebase inspection and existing project analysis documents

---

*Feature landscape analysis: 2026-03-31*
