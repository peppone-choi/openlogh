# Feature Research

**Domain:** Web-based MMO strategy game — gin7 (은하영웅전설 VII) mechanics rewrite
**Researched:** 2026-04-06
**Confidence:** HIGH (primary source: gin7 manual, REWRITE_PROMPT.md, ship_stats JSON, commands.json)

> This document covers **new features only** for the v2.0 gin7 rewrite milestone.
> Already-built systems (entity models, CP system, PositionCard registry, tick engine, galaxy map,
> rank system, scenario framework, chat, victory conditions) are excluded.

---

## Category Map

Features are organized by the 6 subsystems called out in the research brief:

| # | Category | Label |
|---|----------|-------|
| 1 | Ship class / subtype combat system | `[SHIP]` |
| 2 | Authority-card based command system | `[CMD]` |
| 3 | Real-time tactical space battle engine | `[BATTLE]` |
| 4 | Planet economy & arsenal auto-production | `[ECON]` |
| 5 | Faction AI for large-scale strategy | `[AI]` |
| 6 | Retro tactical UI (dot icons, 3D grid) | `[UI]` |

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features gin7 MMO players assume exist. Missing these = product feels broken/unplayable.

| Feature | Category | Why Expected | Complexity | Builds On Existing? | Notes |
|---------|----------|--------------|------------|----------------------|-------|
| 11-class ship unit system (전함/순양함/구축함 etc.) | `[SHIP]` | Core unit of combat — without typed ships there is no game | HIGH | YES — ShipClass(11종), ShipSubtype(22종) enums exist; needs ShipUnit entity + stats wiring | ship_stats_empire/alliance JSON already has 88 subtypes per faction; need DB entity + service layer |
| Ship subtype combat stats (armor/shield/beam/gun/missile per subtype) | `[SHIP]` | Fleet combat damage would be uniform without per-subtype stats | HIGH | YES — JSON data exists; needs ShipStatService to load and apply | front/side/rear armor differentiation is a gin7 hallmark |
| Flagship system (기함) — unique named + generic rank-based | `[SHIP]` | Every officer needs a flagship; it is their identity & survives destruction | MEDIUM | YES — flagship_code field on Officer; needs FlagshipEntity + purchase flow | Named flagships (히페리온, 바르바로사 등) are high-value items; generic ones auto-assigned by rank |
| Ground unit system (육전대 — 3 types) | `[SHIP]` | Planet capture is impossible without landing troops | MEDIUM | YES — GroundUnitType(3종) enum exists; needs GroundUnit entity | ground_unit_stats.json covers 11 unit types |
| 81-command gin7 command system (realtime, not turn-queued) | `[CMD]` | Current registry runs 93 Three Kingdoms commands — product is broken for LOGH | HIGH | YES — PositionCard(82종), CommandGroup(7종), CP system all built; needs 81 command implementations replacing the 93 Three Kingdoms ones | commands.json fully defines all 81; this is the single biggest backlog item |
| PositionCard gating enforcement (커맨드 실행 전 직무카드 검증) | `[CMD]` | Cards are meaningless unless they gate actual commands | MEDIUM | YES — CommandGating infrastructure exists; needs wiring to new 81 commands | Currently gating Three Kingdoms commands |
| Real-time command execution (no turn queue) | `[CMD]` | gin7 is realtime MMO — turn reservation UI is a Three Kingdoms artifact | MEDIUM | YES — tick engine runs at 1s/24 game-sec; waitTime + duration per command already in commands.json | Replaces existing turn-reservation queue |
| Tactical battle grid (2D star-system map with unit icons) | `[BATTLE]` | Without a visual tactical layer the space-battle is invisible | HIGH | YES — TacticalBattle entity + BattleTriggerService exist; needs full engine | Core of the game experience |
| Energy allocation system (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR 6-channel, sum=100) | `[BATTLE]` | gin7's energy panel is the player's primary tactical lever | HIGH | YES — EnergyAllocation(6채널) enum exists; needs real-time allocation state + combat effect wiring | Real-time slider UI feeds into per-tick damage/defense calculations |
| Weapon system (beam/gun/missile with range & supply consumption) | `[BATTLE]` | No weapon diversity = no tactical decisions | HIGH | YES — ship stats JSON has beam/gun/missile damage + consumeSupply per subtype | Missiles are supply-limited (critical supply pressure mechanic) |
| Formation system (방추/함종별/혼성/삼열) | `[BATTLE]` | gin7 has 4 formations with attack/defense/speed trade-offs | MEDIUM | YES — Formation(4종) enum exists; needs formation bonus service | Determines combat modifier stack |
| Command Range Circle (커맨드레인지서클) | `[BATTLE]` | Unique gin7 mechanic — expands over time, resets on order, commander stat determines rate | HIGH | YES — CommandRange type exists; needs real-time state per fleet unit | Key differentiator from generic RTS; builds directly on command stat |
| Planet auto-production (조병창 자동생산) | `[ECON]` | Without auto-production, fleets cannot be rebuilt after losses | HIGH | NEW entity needed — Arsenal/Shipyard production queue | Runs on tick; produces ship units + ground units; continues until planet changes ownership |
| Planet resource system (population/production/commerce/security/approval/ orbital_defense/fortress) | `[ECON]` | Fields exist on Planet entity but are not driven by any economic logic | MEDIUM | YES — all fields exist on Planet; needs EconomyTickService | Current logic is Three Kingdoms-based |
| Tax & budget cycle (세금 90일마다, 진영 자금) | `[ECON]` | Without a money cycle the faction treasury makes no sense | MEDIUM | YES — Faction.funds exists; needs TaxService with quarterly schedule | Tax collected at game turns 1/1, 4/1, 7/1, 10/1 |
| Faction treasury split (제국: 군무성+통수본부, 동맹: 국방예산) | `[ECON]` | Empire has dual budget — unified treatment is historically wrong | MEDIUM | YES — Faction entity; needs BudgetAllocation sub-entity | Empire faction_type='empire' determines split |
| Retro dot-icon unit display (△기함, □전함, ◇구축함 with faction color) | `[UI]` | gin7 visual identity — immediately signals this is an LOGH game | MEDIUM | YES — React Konva galaxy map exists; needs tactical canvas layer | Empire=#4466ff, Alliance=#ff4444; must not use 3D models for unit icons |
| Tactical map info panel (진영명/UC일자/작전명/사령관+계급/공적/물자) | `[UI]` | Players need situational awareness in battle | MEDIUM | NEW frontend component | Right-side panel with star-system minimap in orange/black |
| Energy allocation slider panel (6-channel, realtime) | `[UI]` | Primary tactical input for the player during battle | MEDIUM | NEW frontend component | Must update energy allocation state via WebSocket in real-time |
| Strategic game screen (직무권한카드 탭 + 8-stat panel + chat) | `[UI]` | Main out-of-battle interface — currently shows Three Kingdoms UI | HIGH | YES — entities all exist; entirely new frontend layout | Replaces city-basic-card, general-basic-card, turn-reservation UI |

---

### Differentiators (Competitive Advantage)

Features that set Open LOGH apart from generic space strategy MMOs. These directly serve the "organisation simulation" core value.

| Feature | Category | Value Proposition | Complexity | Builds On Existing? | Notes |
|---------|----------|-------------------|------------|----------------------|-------|
| Organisation simulation via PositionCard hierarchy | `[CMD]` | Lets players live the experience of Reinhard or Yang — issuing orders, proposing strategy, rising through ranks. No other web MMO does this at this fidelity | HIGH | YES — 82 cards, 7 groups, CommandGating all built | The reason this project exists; every other feature serves this |
| Cross-rank command proposal system (제안 시스템) | `[CMD]` | Junior officers can propose to superiors; superiors can reject/approve — mirrors gin7 org dynamics | HIGH | NEW — needs Proposal entity + approval workflow | Keeps lower-rank players engaged even when they lack direct authority |
| Faction-type political mechanics (쿠데타/귀족 vs 의회/선거 vs 차관/정보) | `[CMD]` | Each of the 3 factions (Empire/Alliance/Fezzan) has a distinct political minigame | HIGH | YES — CoupPhase, CouncilSeat, Election, FezzanLoan entities exist | Requires dedicated UI per faction type |
| Unique fortress mechanic (토르해머/가이에스하켄, ally-fire possible) | `[BATTLE]` | Fortress cannon that hits every unit in its firing lane including friendlies creates extreme tactical tension | HIGH | YES — FortressType(4종) enum exists | Overkill damage values (토르해머=10,000 vs battleship armor ~200) demand entirely different tactics |
| Detection/stealth system (색적 — SENSOR allocation + electronic warfare) | `[BATTLE]` | Fog-of-war driven by SENSOR energy creates real information asymmetry | HIGH | YES — DetectionInfo type, SENSOR channel exists | Detection precision varies by unit type and range; electronic warfare can block detection |
| Supply pressure (미사일 소비 — limited ammo forces fleet management) | `[BATTLE]` | Running out of missiles mid-battle is a catastrophic risk that demands logistics planning | MEDIUM | YES — ship stats have consumeSupply per weapon; needs supply tracking per fleet | Without this, battles become DPS races with no logistics dimension |
| Ground combat (육전대 강하 + 행성타입별 규칙) | `[BATTLE]` | Planet capture requires ground troops — naval supremacy alone is insufficient | HIGH | YES — GroundUnit infrastructure partially exists | Planet terrain type restricts which units can participate |
| Planet capture command set (항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동) | `[BATTLE]` | 6 capture options with different cost/reward trade-offs create meaningful choice | MEDIUM | NEW — needs PlanetCaptureService wired to TacticalBattle | Table in REWRITE_PROMPT.md defines exact approval/economy/defense effects per command |
| Fezzan loan system (차관 — failure = Fezzan ending) | `[ECON]` | Borrowing from Fezzan is a high-risk lever; default triggers a specific losing ending | MEDIUM | YES — FezzanLoan entity exists; needs loan cycle + default condition | Adds long-term economic risk that generic strategy MMOs lack |
| Population-based unit cap (인구 10억 = 함대 1 + 순찰대 6) | `[ECON]` | Territory loss has direct military consequence — very rare in web MMOs | MEDIUM | YES — Planet.population exists; needs PopulationCapService | Makes planet defence economically critical, not just strategic |
| 3D grid close-combat view (접근전 — React Three Fiber, proportional block size) | `[UI]` | Visual spectacle moment when fleets engage; block size = ship count makes losses visible | HIGH | YES — React Three Fiber/Drei deps already in package.json | Upper panel = encounter cinematic; lower panel = 3D tactical overview |
| Personality-driven AI officers (5 traits × stat weights) | `[AI]` | NPC officers behave as distinct personalities, not uniform bots | MEDIUM | YES — PersonalityTrait(5종), OfflinePlayerAIService exist; needs decision logic replacement | Offline players must feel like credible subordinates/rivals |

---

### Anti-Features (Explicitly Exclude)

Features that appear useful but would harm the project's focus or fidelity.

| Feature | Category | Why Requested | Why Problematic | Alternative |
|---------|----------|---------------|-----------------|-------------|
| Turn-reservation queue (커맨드 예약) | `[CMD]` | Familiar from Three Kingdoms origin code | gin7 is realtime MMO; turn queues break the direct-command feel that is central to the PositionCard system | Real-time waitTime + duration per command (already defined in commands.json) |
| Three Kingdoms troop type (병종) combat | `[SHIP]` | Still in codebase | Wrong game universe; rock-paper-scissors troop counters contradict LOGH ship combat | Remove entirely — ship class system replaces it |
| Generic RTS unit production queue (click-to-build) | `[ECON]` | Common in strategy games | gin7 arsenals auto-produce without player micro; player controls fleet deployment not individual ship construction | Arsenal auto-production tick service (set-and-forget, not click-to-build) |
| Full 3D ship models for every unit in tactical map | `[UI]` | Looks impressive | Performance-prohibitive at 2,000 concurrent users; gin7 original uses dot/block icons which is part of its identity | Dot icons on 2D tactical canvas; 3D blocks only in close-combat view (proportional, no detailed models) |
| Per-ship HP tracking (개별 함선 HP) | `[BATTLE]` | Granularity seems realistic | gin7 operates at 유닛(300척) granularity; tracking 18,000 individual ships is computationally and UX-unworkable | Unit-level HP pool representing 300 ships; ship count decreases as HP depletes |
| Generic admin CRUD panel for game data | `[UI]` | Easy to build | Breaks immersion; players want an in-universe UI | Scenario selection screen + character creation screen matching gin7 visual style |
| Real-money microtransactions or gacha | `[SHIP]` | Common monetization | Would undermine the "earn rank through gameplay" progression which is the identity of the game | Merit-based flagship purchase using in-game evaluation points only |
| Multi-faction simultaneous player control | `[AI]` | "Let me play all sides" | Destroys the organisation simulation; you are one officer in one faction | One officer, one faction, one role — faction switching only possible via defection command (망명) |

---

## Feature Dependencies

```
[ShipUnit entity + stats]
    └──required by──> [Tactical battle engine]
    └──required by──> [Arsenal auto-production]
    └──required by──> [Fleet management UI]

[gin7 81-command implementations]
    └──required by──> [PositionCard gating enforcement]
    └──required by──> [Real-time command execution]
    └──required by──> [Strategic game screen — card tab]

[PositionCard gating enforcement]
    └──required by──> [Organisation simulation / proposal system]
    └──required by──> [Faction political mechanics]

[Tactical battle engine (grid + units)]
    └──required by──> [Energy allocation system]
    └──required by──> [Formation system]
    └──required by──> [Command Range Circle]
    └──required by──> [Detection/stealth system]
    └──required by──> [Supply pressure mechanic]
    └──required by──> [Fortress cannon mechanic]
    └──required by──> [Ground combat]
    └──required by──> [Planet capture command set]

[Planet resource system (EconomyTickService)]
    └──required by──> [Arsenal auto-production]
    └──required by──> [Tax & budget cycle]
    └──required by──> [Population-based unit cap]
    └──required by──> [Fezzan loan system]

[Tax & budget cycle]
    └──required by──> [Faction treasury split (Empire dual budget)]

[Tactical battle engine]
    └──required by──> [Dot-icon tactical UI (2D)]
    └──required by──> [Energy allocation panel (UI)]
    └──required by──> [3D close-combat view (React Three Fiber)]

[Flagship system]
    └──enhances──> [Command Range Circle] (flagship stat drives expansion rate)
    └──enhances──> [Tactical UI] (flagship icon is distinct △ in battle)

[Personality-driven AI]
    └──requires──> [gin7 command system] (AI executes same 81 commands)
    └──requires──> [Fleet management / ShipUnit] (AI needs units to command)

[Detection system]
    └──conflicts with──> [Full 3D models] (dot icons required for detection ambiguity to work visually)
```

### Dependency Notes

- **ShipUnit requires before battle engine:** You cannot compute damage without subtype stats. ShipUnit entity creation is the hard prerequisite for all combat work.
- **81-command system requires before political mechanics:** PositionCard gating on faction-specific political commands (쿠데타, 선거, 차관) cannot function until the 81-command implementations replace the current Three Kingdoms 93 commands.
- **EconomyTickService before arsenal:** Arsenal auto-production is a special case of the economy tick; planet resource logic must be solid before layering auto-production on top.
- **2D tactical grid before 3D close-combat:** The 3D close-combat view (React Three Fiber) is a cosmetic layer on top of the 2D tactical state. Build 2D battle logic first; 3D is a frontend-only enhancement that reads the same battle state.
- **Detection conflicts with 3D ship models:** If every unit has a detailed 3D model, detection ambiguity (you cannot tell enemy unit type until SENSOR range is high enough) is impossible to represent visually. Dot icons with type revealed only at close range is the correct UX.

---

## MVP Definition

This milestone is a full rewrite, not a greenfield MVP. The following phasing applies within the v2.0 milestone.

### Phase A — Remove Three Kingdoms, Wire Ship System (unblock everything)

- [ ] Delete 93 Three Kingdoms commands from CommandRegistry — unblocks all command work
- [ ] Create ShipUnit entity (session_id FK, fleet_id FK, ship_class, subtype, current_count, stats snapshot) — unblocks battle engine
- [ ] Load ship_stats JSON into ShipStatRepository — unblocks combat calculations
- [ ] Flagship entity + rank-based auto-assignment — unblocks fleet creation
- [ ] GroundUnit entity (3 types from ground_unit_stats.json) — unblocks planet capture
- [ ] Remove Three Kingdoms frontend components (city-basic-card, general-basic-card, turn queue UI)

### Phase B — Command System (organisation simulation backbone)

- [ ] Implement all 81 gin7 commands in CommandRegistry, wired to PositionCard gating
- [ ] Real-time execution pipeline (waitTime countdown → execute → result broadcast via WebSocket)
- [ ] Proposal system entity + approval workflow (제안 시스템)
- [ ] Strategic game screen frontend (직무카드 탭 + 8-stat panel + chat)

### Phase C — Battle Engine (core gameplay loop)

- [ ] Tactical battle grid: 2D Konva canvas, dot icons per unit type, faction colours
- [ ] Energy allocation state per fleet (real-time BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR sliders)
- [ ] Weapon damage engine: beam (range-optimal), gun, missile (supply-consuming)
- [ ] Formation bonuses service (4 formations)
- [ ] Command Range Circle: expand per tick, reset on order issue, commander.command stat rate
- [ ] Detection/stealth: SENSOR allocation → detection radius; unit type + range → precision
- [ ] Supply tracking per fleet (missiles deplete, affect combat power)
- [ ] Fortress cannon: firing lane calculation, ally-fire possible
- [ ] Ground combat: landing turn, terrain restriction, garrison vs landing unit combat
- [ ] Planet capture command set (6 options with differential effects on approval/economy/defense)
- [ ] 3D close-combat view (React Three Fiber): block units + encounter cinematic panel

### Phase D — Economy & AI

- [ ] EconomyTickService: planet resource tick (population, production, commerce, security, approval)
- [ ] Arsenal auto-production: per-planet ship/ground unit queue, tick-driven
- [ ] Tax cycle: quarterly collection, faction treasury credit
- [ ] Empire dual budget split (군무성 / 통수본부)
- [ ] Population-based unit cap enforcement
- [ ] Fezzan loan system: borrow, quarterly interest, default → Fezzan ending trigger
- [ ] Personality-driven AI: replace Three Kingdoms decision logic with stat-weighted, trait-modified command selection
- [ ] Faction-level AI: automatic budget allocation + personnel decisions for NPC factions

### Future Consideration (v2.1+)

- [ ] Full scenario initial data for all 10 scenarios (인물 배치 + 이벤트 트리거) — data-heavy, can iterate after mechanics work
- [ ] Faction political full UI (쿠데타 UI, 의회 선거 UI, 페잔 정보 UI) — mechanics can launch before dedicated UI
- [ ] Balance pass (fleet sizes, economic rates, CP costs) — requires playtest data

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Remove Three Kingdoms commands + ShipUnit entity | HIGH | MEDIUM | P1 |
| 81 gin7 commands (command system) | HIGH | HIGH | P1 |
| Tactical battle grid (2D dot icons) | HIGH | HIGH | P1 |
| Energy allocation (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) | HIGH | MEDIUM | P1 |
| Planet resource tick + arsenal auto-production | HIGH | MEDIUM | P1 |
| Strategic game screen frontend | HIGH | HIGH | P1 |
| Weapon system (beam/gun/missile + supply pressure) | HIGH | MEDIUM | P1 |
| Formation bonuses | MEDIUM | LOW | P2 |
| Command Range Circle | MEDIUM | MEDIUM | P2 |
| Detection/stealth | MEDIUM | HIGH | P2 |
| Flagship system | MEDIUM | MEDIUM | P2 |
| Ground combat + planet capture commands | MEDIUM | HIGH | P2 |
| Fortress cannon mechanic | MEDIUM | MEDIUM | P2 |
| Tax & budget cycle | MEDIUM | LOW | P2 |
| Personality-driven AI | MEDIUM | MEDIUM | P2 |
| 3D close-combat view (React Three Fiber) | HIGH | HIGH | P2 |
| Proposal system (제안) | HIGH | MEDIUM | P2 |
| Fezzan loan system | MEDIUM | LOW | P2 |
| Population-based unit cap | LOW | LOW | P2 |
| Faction AI (full auto) | LOW | HIGH | P3 |
| Political UI (쿠데타/선거/페잔) | MEDIUM | HIGH | P3 |
| Full scenario data (10 scenarios) | HIGH | HIGH | P3 |

**Priority key:**
- P1: Must have — game is broken/unplayable without it
- P2: Should have — game works but feels incomplete
- P3: Nice to have — defer until P1+P2 complete and playtested

---

## Competitor / Reference Analysis

| Feature | gin4 EX (单机, 턴제) | Generic Space MMO | Open LOGH Approach |
|---------|---------------------|-------------------|--------------------|
| Ship types | 8종, no subtypes | Varies, usually 3-5 | 11종 × I–VIII subtypes per faction, full stats per subtype |
| Command system | 25 strategic commands, turn-based | Cooldown-based skills | 81 commands, PositionCard-gated, realtime wait+duration |
| Battle | Turn-based 12-turn structure | Auto-resolve or simple RTS | Realtime tick, energy allocation, 6-channel player control |
| Economy | Manual 내정 commands | Click-to-build queues | Auto-production tick, no micro; tax cycle is macro-level |
| AI officers | Personality types, stat-weighted | Generic bots | gin7 personalityTrait system + offline player AI |
| UI style | DOS-era dot icons + colour blocks | Modern 3D or isometric | Faithful dot icons (gin7 retro identity) + optional 3D close-combat layer |
| Org simulation | Proposal system (제안), ranks | None — individual play | Full PositionCard hierarchy, cross-rank proposal/approval |

---

## Sources

- `docs/REWRITE_PROMPT.md` — Primary specification (gin7 manual synthesis, weapon tables, planet capture table, economy rules, UI specifications) — HIGH confidence
- `backend/shared/src/main/resources/data/commands.json` — 81-command canonical definition with CP costs, wait times, durations — HIGH confidence
- `backend/shared/src/main/resources/data/ship_stats_empire.json` — 88 empire subtype stats — HIGH confidence
- `backend/shared/src/main/resources/data/ship_stats_alliance.json` — 88 alliance subtype stats — HIGH confidence
- `backend/shared/src/main/resources/data/ground_unit_stats.json` — 11 ground unit type stats — HIGH confidence
- `docs/reference/unit_composition.md` — Fleet/patrol/transport/landing force composition rules — HIGH confidence
- `docs/reference/gin4ex_wiki.md` — gin4 EX reference (gin7 base mechanics) — HIGH confidence
- `.planning/PROJECT.md` — Current milestone scope and already-built system inventory — HIGH confidence
- `CLAUDE.md` — Domain mapping, stat definitions, ship classes, rank system — HIGH confidence

---

*Feature research for: Open LOGH — gin7 web MMO game logic rewrite*
*Researched: 2026-04-06*
