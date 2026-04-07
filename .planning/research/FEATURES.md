# Feature Landscape

**Domain:** Tactical command chain + AI for gin7-based web MMO strategy game
**Researched:** 2026-04-07
**Milestone:** v2.1 — Tactical Command Chain + AI
**Confidence:** HIGH (gin7 manual primary source, codebase verified)

## Table Stakes

Features that gin7 players expect. Missing = the "organizational simulation" core value is broken.

| Feature | Why Expected | Complexity | Dependencies | Notes |
|---------|--------------|------------|--------------|-------|
| Operation plan to tactical AI linkage | gin7 manual p.37-38: operation purpose (capture/defend/sweep) determines tactical behavior. Without it, AI units fight aimlessly. | Med | Existing `OperationPlanCommand`, `TacticalBattleEngine` | OperationPlanCommand stores plan in `nation.meta["operationPlan"]` but nothing reads it during battle. Bridge needed. |
| Unit command distribution (10 officers to 60 units) | gin7 manual p.46: units auto-assigned by priority (online > rank > evaluation > merit). This IS the organizational simulation. | High | `CrewSlotRole` (10 roles exist), `TacticalUnit`, `TacticalBattleState` | Most complex feature. Must handle 6 unit types with different crew counts (fleet=10, patrol=3, transport=3, ground=1, garrison=1, solo=0). |
| Command range circle mechanics | gin7 manual p.46-47: flagship CRC expands over time, resets to 0 on command issue. Only units inside CRC receive orders. | Med | `CommandRange` model exists with tick/reset/isInRange. `TacticalUnit.commandRange` fields exist. | Core model already built. Need: per-officer CRC (not just per-unit), CRC gating on command dispatch, UI circle visualization. |
| Command succession on flagship destruction | gin7 manual implies: flagship destroyed then command gap then next-rank auto-promotion. Current code does instant `maxByOrNull { it.ships }` which is wrong. | Med | `TacticalBattleEngine` line 248-258 (existing flagship_transfer logic) | Must change from "biggest ships" to "rank-based with 30-tick gap". Gap period = units on last command or AI autonomous. |
| Tactical AI for offline/NPC units | Without AI, offline player units and NPC fleets just sit still or do basic chase-closest-enemy. gin7 expects intelligent behavior. | High | `PersonalityTrait` (5 types exist), `UtilityScorer`, `TacticalBattleEngine.processMovement` | Current movement is hardcoded "chase closest enemy". Need mission-based + personality-based behavior. |
| Command range circle UI visualization | Players MUST see the CRC on the tactical map to understand why their commands are not reaching units. | Low | Frontend tactical map (SVG-based), WebSocket broadcast | Without visual feedback, the CRC mechanic is invisible and frustrating. |

## Differentiators

Features that go beyond gin7 baseline and make Open LOGH stand out. Not strictly expected but highly valued.

| Feature | Value Proposition | Complexity | Dependencies | Notes |
|---------|-------------------|------------|--------------|-------|
| Real-time command delegation/reassignment | gin7 manual p.46: commanders can reassign unit ownership mid-battle (target must be outside other CRC + stopped). Enables dynamic tactical reorganization. | Med | Unit command distribution, CRC mechanics | Condition checking (outside other CRC + stopped) is the tricky part. Elegant when it works -- lets players adapt to battlefield chaos. |
| Personality-driven tactical variety | AGGRESSIVE officers charge, CAUTIOUS ones maintain distance, DEFENSIVE ones hold position. Creates emergent "character" feel matching LOGH source material. | Med | `PersonalityTrait` weights exist, Tactical AI | Existing weight system for strategic AI can be extended to tactical decisions. Reinhard vs Yang feel. |
| Communication jamming effects | When comms are jammed, supreme commander cannot issue fleet-wide orders. Forces distributed decision-making. | Low | CRC mechanics, detection system | Simple flag on TacticalBattleState. High drama potential. |
| Concentrated/distributed attack strategies | AI chooses focus-fire (high command officers) vs spread damage (low command officers) based on personality and situation. | Low | Tactical AI, target selection | Current targeting is closest-enemy only. Adding strategic target selection is low-effort, high-impact. |
| Strategic AI auto-operation-planning | AI factions automatically create operation plans before tactical battles, giving NPC fleets purposeful behavior. | Med | `FactionAI`, `OperationPlanCommand` | Current FactionAI picks war actions randomly from a list. Needs intelligent operation plan creation. |
| Sub-fleet formation commands | Vice-commander and staff officers can issue independent formation/energy commands to their assigned units. | Med | Unit command distribution, CRC per officer | Multiplayer coordination feature -- each crew member manages their sub-fleet independently. |
| Battle replay with command chain visualization | Post-battle replay showing which officer commanded which units, CRC expansion/contraction, succession events. | High | All tactical features, frontend replay system | Excellent for learning and community sharing. Defer to later milestone. |

## Anti-Features

Features to explicitly NOT build in this milestone.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Player-controlled individual ship movement | gin7 is about commanding fleets, not micromanaging ships. Individual ship control breaks the organizational simulation core value. | Units move as groups under officer command. Player gives high-level orders (advance, retreat, hold). |
| Complex formation editor | Tempting to let players design custom formations. Adds massive complexity for marginal gameplay value. | Use the 4 existing formations (wedge/by-class/mixed/three-column). Personality can bias formation choice. |
| AI learning/ML-based tactical decisions | ML models are unpredictable, hard to debug, and overkill for this game's scale. | Use utility-scoring approach (extending existing `UtilityScorer` pattern). Deterministic, debuggable, personality-driven. |
| Real-time voice/video communication | Scope creep. Text chat + command system is sufficient for tactical coordination. | Existing ChatService (3 scopes) + command chain is the communication mechanism. |
| Per-tick CRC recalculation with physics | Simulating radio wave propagation, interference, etc. Over-engineering a simple radius check. | Linear expansion rate based on command stat. Reset to 0 on order. Binary in/out check. Already implemented correctly in `CommandRange.kt`. |
| Automated unit type conversion mid-battle | Allowing fleets to split/merge during tactical combat. Breaks gin7's pre-battle organization model. | Organization is locked at battle start. Only command reassignment (which officer controls which units) changes during battle. |

## Feature Dependencies

```
Operation Plan --> Tactical AI Linkage
  |
  v
Unit Command Distribution (10 officers --> 60 units)
  |
  +---> Command Range Circle (per-officer CRC gating)
  |       |
  |       +---> CRC UI Visualization
  |       |
  |       +---> Real-time Command Delegation (requires CRC + stopped check)
  |
  +---> Command Succession (flagship destruction --> 30-tick gap --> auto-promote)
  |
  +---> Tactical AI Behaviors (offline/NPC units)
          |
          +---> Personality-driven Variety
          |
          +---> Concentrated/Distributed Attack
          |
          +---> Communication Jamming

Strategic AI Enhancement (FactionAI auto-planning)
  |
  v
Operation Plan --> Tactical AI Linkage (feeds into above chain)
```

**Critical path:** Unit Command Distribution must come first -- CRC, succession, and tactical AI all depend on knowing which officer controls which units.

## Detailed Feature Specifications

### 1. Operation Plan to Tactical Battle Integration

**gin7 manual (p.37-38):** Operation plans define 3 purposes:
- **Capture (占領作戦):** Capture enemy star system. Tactical AI: advance aggressively toward planet.
- **Defend (防衛作戦):** Hold own star system for a period. Tactical AI: hold position, prioritize survival.
- **Sweep (掃討作戦):** Destroy enemy units in a grid. Tactical AI: hunt and pursue enemy fleets. Only proposable against solo ships.

**What exists:**
- `OperationPlanCommand` stores plan in `nation.meta["operationPlan"]` with name, scale, year, month
- `OperationCancelCommand` exists
- `TacticalBattleService.startBattle()` creates battles but has no operation plan awareness
- Operation plan duration: 30 in-game days, then expires (gin7 p.38)

**What's needed:**
- Enrich operation plan data: add `purpose` (CAPTURE/DEFEND/SWEEP), `targetStarSystemId`, `assignedFleetIds`
- `TacticalBattleState` needs `operationPurpose` field
- `BattleTriggerService` must check if fleets are part of an active operation when triggering battle
- Tactical AI reads `operationPurpose` to set base behavior mode
- Merit point bonus for operation-aligned actions (gin7: bonus points for acting within operation plan)
- Separate roles for plan creation vs issuing (gin7 p.38: planner and issuer are different positions)

**Complexity:** Medium. Data flow extension, not new system.

### 2. Unit Command Distribution

**gin7 manual (p.46):** On tactical battle entry, units auto-assigned to crew members by priority:
1. Online player characters
2. Higher rank
3. More evaluation points
4. More merit points

**What exists:**
- `CrewSlotRole` enum: 10 roles (COMMANDER through ADJUTANT)
- `UnitCrew` entity links officers to fleet crew slots
- `TacticalUnit` has `officerId` but this is the fleet commander, not per-unit commander
- No concept of "sub-fleet" or per-unit officer assignment in tactical battle

**What's needed:**
- New model: `TacticalCommandAssignment` mapping each unit index (0-59) to a commanding officer
- Distribution algorithm: sort officers by priority, round-robin assign units
- COMMANDER gets unassigned remainder (direct command)
- Each officer's assigned units obey only that officer's CRC
- WebSocket commands must include `officerId` to validate authority over target units
- Frontend: show which units belong to which officer (color coding or grouping)
- Different distribution for different unit types: fleet (10 officers, 60 units), patrol (3 officers, 3 units), etc.

**Complexity:** High. Touches battle initialization, command dispatch, CRC, AI, and frontend.

### 3. Command Range Circle Mechanics

**gin7 manual (p.46-47):**
- CRC radius expands over time toward max (determined by flagship capability)
- Expansion rate based on officer's "command" (指揮) stat
- Resets to 0 every time a command is issued
- Units outside CRC max range: continue current command, halt when command ends
- Units with low morale (panicking): CRC has no effect (cannot receive orders)
- Solo ships have no CRC
- Commands take 0-20 seconds to process after being issued (gin7 p.47)

**What exists:**
- `CommandRange` model: has `tick()`, `resetOnCommand()`, `isInRange()`, `isInMaxRange()` -- all correct
- `TacticalUnit.commandRange`, `commandRangeMax`, `ticksSinceLastOrder` fields
- `TacticalBattleEngine.updateCommandRange()` updates per tick -- but applies to units, not per-officer

**What's needed:**
- Shift CRC from per-unit to per-officer (each of the 10 crew members has their own CRC)
- New model: `OfficerTacticalState` with CRC, position (tied to flagship unit position), assigned unit indices
- Command dispatch: validate target unit is within issuing officer's CRC before accepting command
- Command processing delay: 0-20 tick delay before command takes effect (gin7 p.47)
- Panicking units (morale below threshold): exempt from CRC commands
- Frontend: draw CRC circle centered on each officer's flagship position

**Complexity:** Medium. Core model exists. Main work is per-officer separation and command gating.

### 4. Command Succession

**gin7 manual implied + v2.1 scope doc:**
- Flagship destroyed -> 30-tick command gap (no CRC, units autonomous)
- After gap: next officer by rank auto-inherits supreme command
- Sub-fleet commander destroyed -> their units revert to fleet commander's direct control
- All commanders destroyed -> command chain collapse, each unit runs independent AI

**What exists:**
- `TacticalBattleEngine` lines 248-258: on flagship destruction, picks replacement by `maxByOrNull { it.ships }` (WRONG -- should be rank-based)
- `BattleTickEvent("flagship_transfer")` event type exists
- `InjuryEvent` created on flagship destruction

**What's needed:**
- Replace ship-count selection with rank-based selection (matching gin7 priority: rank > evaluation > merit)
- Add 30-tick `commandGapRemaining` counter per side to `TacticalBattleState`
- During gap: no new commands accepted for that side, units execute last command or fall to AI
- After gap: promote next officer, reset their CRC, broadcast succession event
- Sub-fleet commander loss: simpler -- reassign their units to COMMANDER
- Total collapse detection: all officers with command role destroyed -> set `isCommandCollapsed` flag

**Complexity:** Medium. Straightforward state machine but must integrate with CRC and AI systems.

### 5. Real-time Command Delegation/Reassignment

**gin7 manual (p.46):** Commanders can change unit ownership mid-battle. Conditions:
- Target unit must NOT be inside another flagship's CRC
- Target unit must be fully stopped (no active command being executed)

**What exists:** Nothing -- no mid-battle unit reassignment mechanism.

**What's needed:**
- New WebSocket command type: `REASSIGN_UNIT` with params (unitIndex, newOfficerId)
- Validation: check unit is outside all other officer CRCs, check unit velocity is zero
- Update `TacticalCommandAssignment` mapping
- Broadcast reassignment event to all battle participants
- Frontend: drag-and-drop or select-and-assign UI for unit reassignment

**Complexity:** Medium. Dependent on CRC and command distribution being complete.

### 6. Tactical AI Behaviors

**What exists:**
- `PersonalityTrait` (5 types: AGGRESSIVE, DEFENSIVE, BALANCED, POLITICAL, CAUTIOUS)
- `PersonalityWeights` with stat multipliers per trait
- `UtilityScorer` for strategic command selection
- `TacticalBattleEngine.processMovement()`: hardcoded "move toward closest enemy, maintain optimal range"

**What's needed -- Behavior layers (evaluated per tick for each AI-controlled unit):**

**Layer 1: Mission behavior (from operation purpose)**
- CAPTURE: move toward planet position, engage blockers, prioritize advance
- DEFEND: hold position near planet, engage approaching enemies, do not pursue
- SWEEP: actively hunt enemy units, pursue retreating enemies
- NO_MISSION: default balanced behavior

**Layer 2: Personality modifiers**
- AGGRESSIVE: lower retreat threshold (HP<10% instead of 20%), prefer close range, energy bias to BEAM/GUN
- DEFENSIVE: higher retreat threshold (HP<30%), prefer long range, energy bias to SHIELD
- CAUTIOUS: avoid engagement unless numeric advantage, prefer missile range, high SENSOR allocation
- POLITICAL: no tactical modifier (political officers rarely command tactically)
- BALANCED: no modifier, use mission behavior as-is

**Layer 3: Threat assessment**
- Calculate local threat ratio: (enemy ships in range) / (friendly ships in range)
- Threat > 2.0: consider retreat regardless of personality
- Morale < 30: force retreat (gin7 rule: morale 20 below = combat ineffective, already in engine)
- HP < personality-adjusted threshold: retreat

**Layer 4: Automatic adjustments**
- Energy allocation: shift based on range to nearest enemy (far=ENGINE, mid=BEAM, close=GUN+SHIELD)
- Formation: select based on personality + situation (AGGRESSIVE=wedge, DEFENSIVE=three-column)
- Stance: COMBAT when enemies nearby, NAVIGATION when moving to objective
- Target selection: AGGRESSIVE=focus weakest enemy, CAUTIOUS=focus most threatening, DEFENSIVE=focus closest

**Complexity:** High. This is the largest single feature. Use utility-scoring approach (extending existing `UtilityScorer` pattern) rather than behavior trees for consistency with codebase.

### 7. Strategic AI Enhancement for Tactical Battle Entry

**What exists:**
- `FactionAI.decideNationAction()`: picks war actions randomly (`급습`, `의병모집`, `필사즉생`)
- `OfficerAI.decideAndExecute()`: large decision tree for NPC officers (ported from legacy PHP)
- No connection between strategic AI and operation planning

**What's needed:**
- `FactionAI`: when at war, create operation plans with appropriate purpose based on situation analysis
  - Losing territory: DEFEND operations for threatened systems
  - Strong position: CAPTURE operations for weak enemy systems
  - Enemy solo ships raiding: SWEEP operations
- `OfficerAI`: when assigned to operation, move toward target system (existing movement logic can be reused)
- Fleet composition selection: AI picks appropriate fleet types for operation (ground troops for CAPTURE, etc.)

**Complexity:** Medium. Extends existing AI infrastructure, no new systems needed.

## MVP Recommendation

**Prioritize in this order:**

1. **Unit Command Distribution** -- Foundation that everything else depends on. Without knowing which officer controls which units, CRC/succession/AI cannot function.

2. **Command Range Circle (per-officer)** -- The core mechanic that makes organizational simulation feel real in tactical combat. Already partially implemented.

3. **Operation Plan to Tactical Linkage** -- Gives AI units a purpose. Without this, AI is aimless.

4. **Tactical AI Behaviors** -- Makes NPC/offline units feel alive. Depends on 1-3.

5. **Command Succession** -- Important for drama and gameplay consequence, but works as a simple fallback without it (current code just picks replacement immediately).

6. **Real-time Command Delegation** -- Nice to have, enhances multiplayer coordination, but the game functions without it.

**Defer to future milestone:**
- Battle replay visualization: High effort, low urgency.
- Communication jamming: Cool feature but not core. Add after base chain works.
- Strategic AI auto-planning: NPC factions can use random operations temporarily.

## Sources

- gin7 Official Manual (101 pages PDF) -- pages 37-38 (operation plans), 45-47 (tactical battle, command range circle, unit command) -- PRIMARY SOURCE, HIGH confidence
- [Chain of command in cooperative agents for RTS games](https://link.springer.com/article/10.1007/s40692-018-0119-8) -- hierarchical AI command patterns
- [Hierarchical control of multi-agent RL in RTS games](https://www.sciencedirect.com/science/article/abs/pii/S0957417421010897) -- multi-level decision making
- [Total War Shogun 2 Morale System](https://shogun2-encyclopedia.com/how_to_play/052_enc_manual_battle_conflict_morale.html) -- command radius, morale break, officer death mechanics
- [Total War Leadership/Morale](https://totalwarwarhammer.fandom.com/wiki/Leadership) -- general death causing army rout, succession mechanics
- [Behavior Trees for Modern Game AI](https://www.wayline.io/blog/rediscovering-behavior-trees-ai-tool) -- BT vs utility scoring for tactical AI
- [Discovering optimal strategy through behavior tree evolution](https://link.springer.com/article/10.1007/s10479-021-04225-7) -- BT-based tactical combat AI
- [Designing AI for Turn-Based Strategy Games](https://www.gamedeveloper.com/design/designing-ai-algorithms-for-turn-based-strategy-games) -- utility scoring architecture
- Existing codebase analysis: `CommandRange.kt`, `TacticalBattleEngine.kt`, `PersonalityTrait.kt`, `UtilityScorer.kt`, `OfficerAI.kt`, `FactionAI.kt`, `CrewSlotRole.kt` -- HIGH confidence
