# Feature Landscape: Open LOGH

**Domain:** Multiplayer organizational-hierarchy space strategy game (browser-based, persistent world)
**Researched:** 2026-03-28
**Reference sources:** gin7 manual (via feature-checklist.md + feature-audit.md), EVE Online, Foxhole, Crusader Kings 3, OGame, Travian, Hades Star design analysis

---

## Framing: What Kind of Game This Is

Open LOGH is not OGame (solo resource grind), not EVE Online (sandbox economy), and not Crusader Kings (single-player political sim). It is closest to **gin7 (銀河英雄伝説VII)** — a persistent browser MMO where the core loop is:

> "I am an officer inside a military hierarchy. I use command authority delegated to me to act on behalf of my faction. My rank determines what I can do. My competence determines how well I do it."

This framing drives every feature classification below. Features that reinforce this loop are table stakes or differentiators. Features that dilute it — making the game feel like a generic 4X or idle builder — are anti-features.

---

## Table Stakes

Features users expect. Missing = product feels incomplete or broken.

### Session & Identity

| Feature                                           | Why Expected                                                                       | Complexity | Notes                                                      |
| ------------------------------------------------- | ---------------------------------------------------------------------------------- | ---------- | ---------------------------------------------------------- |
| Session creation with scenario selection          | Every persistent MMO offers this                                                   | Low        | P0 — already partially exists                              |
| Join existing session, pick faction               | Standard entry flow                                                                | Low        | P0                                                         |
| Character creation with stat allocation           | Expected in any RPG-adjacent MMO                                                   | Medium     | P0 — original + generated characters                       |
| Persistent offline presence                       | Travian, OGame, Foxhole all do this; players expect world to continue without them | Medium     | P0 — CP keeps recovering offline; character stays in world |
| Character location state (planet / fleet / space) | Contextualizes every other action                                                  | Low        | P0                                                         |

**Dependency:** Session system must exist before any character system. Character must exist before rank/org system.

### Rank & Promotion Ladder

| Feature                                                 | Why Expected                                                                                         | Complexity | Notes                                                   |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------- |
| Named rank tiers with caps per tier                     | Every military/guild hierarchy game has this (EVE director tiers, CK3 vassal ranks, WoW guild ranks) | Low        | P0 — 11 ranks, per-tier caps                            |
| Merit-based promotion path (merit points)               | Players expect progress to be earned; arbitrary promotions feel unfair                               | Medium     | P0 — 공적 포인트 (merit points)                         |
| Human-controlled promotion/demotion by authority holder | Standard in all org-hierarchy games                                                                  | Medium     | P0 — personnel command chain                            |
| Auto-promotion for lower ranks to prevent bottleneck    | Without this, low-rank players feel stuck and quit; seen in EVE auto-role grants                     | Medium     | P1 — every 30 game-days                                 |
| Rank stripping on rank change                           | Prevents privilege accumulation bugs; expected side-effect of org change                             | Low        | P0 — all duty cards except personal/fleet/fief stripped |

**Dependency:** Rank system requires character system. Promotion requires merit point accumulation.

### Organizational Authority (Duty Card System)

| Feature                                       | Why Expected                                                                                       | Complexity | Notes                                                                             |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------- | ---------- | --------------------------------------------------------------------------------- |
| Role-gated command access                     | The EVE corporation role system, guild officer permissions — players expect authority to be scoped | High       | P0 — 직무권한카드 is the core differentiator but also a table stake for the genre |
| Appointment / dismissal of subordinates       | Standard in org-hierarchy games                                                                    | Medium     | P0                                                                                |
| Proposal/order chain (bottom-up and top-down) | Without this, large orgs become uncoordinated; essential for fun at scale                          | High       | P1 — suggestion acceptance probability based on affinity + rank                   |
| Concurrent role holding (겸임)                | High-rank players need flexibility; EVE directors hold multiple roles                              | Low        | P1                                                                                |

**Dependency:** Duty cards require character + rank. Proposal system requires duty cards.

### Galaxy Map & Territory

| Feature                                          | Why Expected                                                 | Complexity | Notes                                      |
| ------------------------------------------------ | ------------------------------------------------------------ | ---------- | ------------------------------------------ |
| Persistent star map with faction territory       | OGame, Travian, EVE all have this; it is the strategic arena | High       | P0 — 80 star systems, 100LY grid           |
| Planet management (population, economy, defense) | Any territory-control game needs governable nodes            | Medium     | P0                                         |
| Fog of war / scouting (색적)                     | Hiding enemy positions is expected in any strategy game      | Medium     | P1                                         |
| Territory capture mechanics                      | Without capture, there is no strategic progress              | High       | P0 — planet occupation post-processing     |
| Victory conditions visible to all                | Players need to know what they are working toward            | Low        | P0 — capital capture + territory reduction |

**Dependency:** Map requires session. Planet management requires territory. Fleet movement requires map.

### Fleet Management

| Feature                                                     | Why Expected                                                                          | Complexity | Notes                              |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------- | ---------- | ---------------------------------- |
| Multiple fleet types (combat / patrol / transport / ground) | Foxhole has supply, combat, and logistics roles; players expect unit specialization   | Medium     | P0/P1                              |
| Fleet movement between star systems                         | The most basic strategic action                                                       | Medium     | P0 — warp command                  |
| Morale system with threshold                                | Fleet combat games always have morale; below threshold = non-combat                   | Medium     | P0 — morale < 20 = combat disabled |
| Crew/troop quality tiers                                    | EVE has crew skills; quality tiers add depth without bloat                            | Medium     | P1                                 |
| Fuel/supply constraint on movement                          | OGame uses deuterium; Foxhole uses fuel; limits mindless blob movement                | Medium     | P1 — warp requires 항속 ≥ 100      |
| Fleet size limits per grid                                  | Prevents one mega-stack dominating all engagements; grid cap is standard in map games | Low        | P1                                 |

**Dependency:** Fleet management requires character location state. Fleet commands require duty cards.

### Command Point (CP) System

| Feature                                    | Why Expected                                                                               | Complexity | Notes                          |
| ------------------------------------------ | ------------------------------------------------------------------------------------------ | ---------- | ------------------------------ |
| Action resource that regenerates over time | OGame, Travian, every browser strategy game uses this pacing mechanism                     | Medium     | P0 — 5 min real = 2 game hours |
| CP recovery continues offline              | Without this, players feel punished for sleep; Travian proved this model works             | Low        | P0                             |
| CP cost differentiates action weight       | High-cost actions (coup, defection) feel weighty; cheap actions (move, scout) feel routine | Low        | P0                             |

**Without CP dual-split (PCP/MCP):** This is the table stake version. The _dual_ split (political vs military) is a differentiator (see below).

**Dependency:** CP system requires character. CP dual-split requires character class (military/political).

### Real-Time Fleet Combat (Tactical Layer)

| Feature                                         | Why Expected                                                                                            | Complexity | Notes                 |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ---------- | --------------------- |
| Combat triggers when enemy fleets meet          | Expected in any space strategy game                                                                     | High       | P0                    |
| Player-controlled combat commands during battle | Passive auto-resolve is table stakes minimum; active commands are the tactical layer this game promises | Very High  | P0 — the tactical RTS |
| Formation system                                | Any fleet combat sim has formations; LOGH source material is built around them                          | High       | P0 — 7 formations     |
| Retreat mechanic                                | Without retreat, battles are too punishing; players expect an escape option                             | Medium     | P0 — 철퇴 (withdraw)  |
| Battle log / replay visibility                  | Players expect to understand what happened; EVE battle reports are famous                               | Medium     | P0                    |
| AI takes over for offline commanders            | Without this, offline players lose battles instantly and quit; Foxhole, OGame both learned this         | High       | P1                    |

**Dependency:** Tactical combat requires fleet system. WebSocket connection required for real-time.

### Communication

| Feature                    | Why Expected                                                                            | Complexity | Notes |
| -------------------------- | --------------------------------------------------------------------------------------- | ---------- | ----- |
| In-game mail system        | Every persistent MMO has async communication; EVE mail is critical for org coordination | Medium     | P0    |
| Location-scoped chat       | Chat among fleetmates / planet co-inhabitants is universal expectation                  | Low        | P0    |
| Faction-internal messaging | Without faction comms, there is no coordination; this is the meta-game glue             | Medium     | P0/P1 |

---

## Differentiators

Features that set Open LOGH apart. Not universally expected, but create the identity of the game.

### Dual CP Split (PCP / MCP)

| Feature                                                    | Value Proposition                                                                                    | Complexity | Notes                   |
| ---------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ---------- | ----------------------- |
| Political Command Points (PCP) for org/personnel/espionage | Separates the "admiral" from the "politician" play styles; forces players to specialize or trade off | Medium     | P0 — gin7 core mechanic |
| Military Command Points (MCP) for fleet/combat ops         | Makes military actions feel scarce and meaningful, not spammable                                     | Medium     | P0                      |
| Cross-substitution at 2× cost                              | Lets generalists participate at a premium; no hard wall                                              | Low        | P1                      |
| CP-to-XP conversion (separate track for each CP type)      | CP spending becomes growth investment, not just consumption; stat growth feels earned                | Medium     | P1                      |

**Why differentiating:** OGame, Travian, and most browser strategy games use a single action-point pool. The PCP/MCP split forces players to define their role within the faction's hierarchy, which directly reinforces the "organizational simulation" core value. No comparable web game implements this.

### Duty Card Authority System (직무권한카드)

| Feature                                                             | Value Proposition                                                                                                                      | Complexity | Notes |
| ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ----- |
| Command access scoped to held duty cards (up to 16)                 | Not just "guild rank" — specific commands require specific cards. A fleet admiral can't do political espionage without the right card. | High       | P0    |
| Cards lost on rank change (except personal/fief/fleet cards)        | Rank change is a real organizational event with consequences, not just a number increment                                              | Low        | P0    |
| Cards delegated by specific authority holders (not just "the boss") | Fleet commander appoints staff; personnel bureau appoints officers — each role has a specific delegator                                | High       | P0    |
| 100+ distinct organizational positions per faction                  | Deep org chart creates genuine political depth; EVE has ~20 roles total                                                                | Very High  | P0/P1 |

**Why differentiating:** EVE's corporation role system has ~20 functional roles. This game has 100+ distinct positions with specific appointment chains. The depth of the organizational simulation is the entire point.

### 5-Law Rank Ladder (계급 래더 5법칙)

| Feature                                                                      | Value Proposition                                                                                                          | Complexity | Notes |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ---------- | ----- |
| Merit → Title (작위) → Decoration (훈장) → Influence → Stat total tiebreaker | Tiebreaking through multiple dimensions means rank advancement reflects varied forms of contribution, not just kill counts | Medium     | P1    |
| Influence system as 4th tiebreaker (social/political capital)                | Lets politically active players compete with combat-focused ones for rank                                                  | Medium     | P1    |
| Friendship/affinity (우호도) affects proposal acceptance                     | Relationship-building has mechanical consequence; alliances within the faction matter                                      | Medium     | P1    |

**Why differentiating:** Most MMO rank systems are flat (XP = rank). The 5-law ladder means the same merit total can rank differently based on social capital, decorations, and influence. This creates genuinely interesting political competition within a faction.

### Coup d'État System (6-stage chain)

| Feature                                               | Value Proposition                                                                                              | Complexity | Notes |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ---------- | ----- |
| 6-step coup chain: 반의 → 모의 → 설득 → 참가 → 반란   | Players can attempt internal regime change; direct source-material recreation of Oberstein/Reuentahl plotlines | Very High  | P2    |
| Counter-surveillance (사열) for detection             | Coup attempts can be detected and countered; creates espionage cat-and-mouse                                   | High       | P2    |
| Successful coup transfers control of all star systems | The stakes are faction-wide; this is a world-altering event                                                    | High       | P2    |

**Why differentiating:** No comparable browser MMO has an internal-faction coup mechanic. This is unique to LOGH game design and directly recreates one of the most memorable aspects of the source material. The high CP cost (PCP 640 per step) ensures it is rare and consequential.

### Espionage Command System (12+ commands)

| Feature                                                    | Value Proposition                                                                | Complexity | Notes |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------- | ---------- | ----- |
| Physical infiltration (잠입 공작) into enemy planets       | Agents must physically travel and infiltrate; creates spatial tension            | High       | P2    |
| Sabotage / propaganda (파괴/선동 공작)                     | Covert economic and political warfare; CK3 schemes but in a military-org context | High       | P2    |
| Fleet concealment / fake fleet display (통신방해/위장함대) | Information warfare on the strategic map; rare in browser strategy games         | High       | P2    |
| 4-step arrest chain (허가→명령→집행→처단)                  | Factional internal enforcement; players can purge traitors                       | Medium     | P1/P2 |

**Why differentiating:** Browser MMOs typically have zero espionage mechanics. The spy system adds an entire covert-action layer that rewards intelligence-stat investment and creates persistent information asymmetry.

### Dual-Mode Gameplay (Strategic Turn + Tactical RTS)

| Feature                                                                    | Value Proposition                                                                     | Complexity | Notes |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ---------- | ----- |
| Turn-based territory management (strategic layer)                          | Accessible to casual players; manageable time commitment                              | High       | P0    |
| Real-time WebSocket fleet battle (tactical layer)                          | High-skill ceiling for combat specialists; replicates LOGH's tactical depth           | Very High  | P0    |
| Energy allocation across 6 channels (BEAM/GUN/SHIELD×4/ENGINE/WARP/SENSOR) | Real-time resource allocation during combat; creates moment-to-moment decision-making | High       | P0/P1 |
| Command range (커맨드 레인지) as spatial authority                         | Admiral's influence radius shrinks under fire; physical presence in battle matters    | High       | P1    |

**Why differentiating:** Most browser strategy games use either auto-resolve or very simplified combat. A full RTS combat layer with energy management is extremely rare in browser MMOs. This is the hardest feature to build but the most distinctive.

### Faction Asymmetry (Empire vs Alliance)

| Feature                                                                 | Value Proposition                                                           | Complexity | Notes                |
| ----------------------------------------------------------------------- | --------------------------------------------------------------------------- | ---------- | -------------------- |
| Empire: fief system, nobility ranks, high-speed battleships (제국 전용) | Empire feels feudal and aristocratic; different political mechanics         | Medium     | P1/P2                |
| Alliance: democratic elections, council voting                          | Alliance feels republican; internal power derived from popular support      | Medium     | P2                   |
| Faction-exclusive ship classes                                          | Reinforces faction identity through capability differences                  | Low        | P0 — already in spec |
| Fezzan neutral faction with trade/diplomatic role                       | Third-faction intrigue; extracts strategic value from commercial neutrality | Medium     | P2                   |

**Why differentiating:** EVE's factions are cosmetic. Here, the two factions have structurally different political systems. Imperial players use feudal social mechanics (fief grants, aristocratic titles). Alliance players use democratic ones (elections, council proposals). Same game, two political worlds.

### NPC Original Characters with AI Behavior

| Feature                                             | Value Proposition                                                            | Complexity | Notes |
| --------------------------------------------------- | ---------------------------------------------------------------------------- | ---------- | ----- |
| Unselected canon LOGH characters run as AI officers | Reinhard, Yang, Kircheis et al. participate as NPCs if not chosen by players | Very High  | P1    |
| NPC behavior calibrated to character stats          | A high-leadership NPC commands better than a low-leadership one              | High       | P1    |

**Why differentiating:** No browser MMO uses licensed fictional characters as dynamic AI participants. This directly recreates the "play as Reinhard's subordinate" fantasy that is the game's core promise.

---

## Anti-Features

Features to deliberately NOT build, or to defer indefinitely.

### Anti-Feature 1: Pay-to-Win / Monetization Layer

| Anti-Feature                                                | Why Avoid                                                                                 | What to Do Instead                                                                           |
| ----------------------------------------------------------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Premium currency, speed-ups for CP, purchasable rank boosts | Destroys faction trust and rank ladder integrity; the game is explicitly open-source/free | Keep the project open-source and free. If hosting costs arise, cosmetic-only donations only. |

**Evidence:** The project.md explicitly lists "과금/상점 시스템 — Out of Scope." This is correct. Any monetization of the rank system collapses the core value proposition.

### Anti-Feature 2: Generic Base-Building / City Management UI

| Anti-Feature                                                                       | Why Avoid                                                                                 | What to Do Instead                                                                                                                                                              |
| ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Planet management as the primary gameplay loop (Travian/OGame style queue builder) | This reduces the game to "who clicks buildings first"; destroys organizational simulation | Planet management is a _consequence_ of holding territory and appointing governors. The player is an officer, not a city builder. Governor (총독) role delegates planet upkeep. |

### Anti-Feature 3: Over-Surfaced Espionage

| Anti-Feature                                                  | Why Avoid                               | What to Do Instead                                                                                                             |
| ------------------------------------------------------------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Espionage success/fail notifications pushed to entire faction | Reveals spy identities; removes tension | Show results only to the involved parties and their direct chain of command. Notifications must be sparse and non-attributing. |

### Anti-Feature 4: Combat Auto-Resolve as Default

| Anti-Feature                                            | Why Avoid                                                                                                     | What to Do Instead                                                                                                            |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Battles resolve without player input as the normal path | Makes the tactical layer pointless; Hades Star explicitly avoided OGame's passive-fleet model for this reason | Auto-resolve only when ALL commanders are offline. When any commander is online, tactical combat should be offered/defaulted. |

### Anti-Feature 5: Single Global Chat

| Anti-Feature                              | Why Avoid                                                                         | What to Do Instead                                                                                                                      |
| ----------------------------------------- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| A global faction-wide real-time chat room | Destroys location relevance; kills the "I am physically at this planet" immersion | Scope chat strictly: same-spot chat, same-grid fleet chat, in-game mail for async. Global comms only via formal mail to duty addresses. |

### Anti-Feature 6: Instant Gratification Rank Skipping

| Anti-Feature                                | Why Avoid                                                                                                      | What to Do Instead                                                                                                           |
| ------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Paying or grinding shortcuts past rank caps | Invalidates the entire rank-cap system; EVE's "Director" problem where one player controls all org permissions | Enforce rank caps absolutely. The "발탁 (special promotion)" command exists but costs 4× normal PCP and still respects caps. |

### Anti-Feature 7: Unlimited Faction Switching

| Anti-Feature                              | Why Avoid                                                            | What to Do Instead                                                                                                                                                               |
| ----------------------------------------- | -------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Free, low-cost defection between factions | Destroys faction cohesion; players would just flip sides when losing | Defection (망명) costs 320 PCP, deletes the entire address book, forces detention in enemy capital, and blocks same-faction re-entry. This is correct and must remain punishing. |

### Anti-Feature 8: Complex UI Before Core Systems Exist

| Anti-Feature                                                                                                | Why Avoid                                                     | What to Do Instead                                                                                                                                   |
| ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Building elaborate 3D galaxy visualizations, character art, or UI polish before backend systems are working | Wastes development time; backend-first is the stated priority | Ship functional backend APIs for session/character/rank/CP first. The 3D model viewer already shows the risk — it exists but the game systems don't. |

---

## Feature Dependencies

```
Session creation
  └── Character creation
        └── Location state
              ├── CP system (PCP/MCP)
              │     └── Strategic commands (all 70+)
              │           ├── Personnel commands (require duty cards)
              │           ├── Fleet commands (require fleet assignment)
              │           ├── Espionage commands (require infiltration state)
              │           └── Coup commands (require 반의 state)
              ├── Rank system
              │     ├── Merit points (공적)
              │     ├── Rank ladder (5 laws)
              │     │     ├── Influence system
              │     │     └── Friendship system
              │     └── Duty card system
              │           ├── Organization chart (100+ positions)
              │           ├── Appointment/dismissal chain
              │           └── Proposal/order system
              └── Fleet system
                    ├── Fleet types (combat/patrol/transport/ground)
                    ├── Ship class specialization
                    ├── Morale + fuel constraints
                    └── Tactical combat (RTS)
                          ├── Formation system (7 types)
                          ├── Energy allocation (6 channels)
                          ├── Command range
                          └── Ground assault

Galaxy map
  ├── Requires: Session
  ├── Planet management → Requires: Territory capture
  ├── Fortress system → Requires: Planet management
  └── Victory conditions → Requires: Territory tracking

Communication
  ├── In-game mail → Requires: Character + duty addresses
  └── Location chat → Requires: Location state

NPC AI
  └── Requires: All character/fleet/command systems to exist first
```

---

## MVP Recommendation

**Build these first — in this order — or the game does not function:**

1. **Session + Character + Rank (P0 core)** — Players cannot exist without these. The rank system is the skeleton of every other system.
2. **Duty Card System (P0)** — Without duty cards, no one can issue commands. This is the authority layer that gates everything.
3. **CP System — dual PCP/MCP (P0)** — Without CP, no commands can be executed. Implement with offline recovery from day one.
4. **Galaxy Map + Planet + Fleet Movement (P0)** — Without a map and fleets that can move, there is no strategic game.
5. **Fleet Combat (P0 tactical layer)** — The promised RTS layer. Without it, the game is just a menu simulator.
6. **Strategic Commands — Operations + Personnel + Logistics (P0/P1)** — The 70+ commands are the actual gameplay. Prioritize warp, recon, formation, assign, supplement.
7. **Communication (P0)** — Mail and location chat must exist before players can coordinate.

**Defer with explicit rationale:**

| Feature                            | Defer Until | Reason                                                                                             |
| ---------------------------------- | ----------- | -------------------------------------------------------------------------------------------------- |
| Coup d'état system                 | Phase 4+    | Requires full organizational simulation to be meaningful; a coup with 10 players is just a scuffle |
| Full espionage suite (12 commands) | Phase 4+    | Requires physical infiltration state, which requires location system + planet facilities           |
| Fief / title system (Empire)       | Phase 3+    | Faction asymmetry is differentiated but not day-1 required                                         |
| NPC AI for offline commanders      | Phase 3     | Technically hard; tackle after combat engine is stable                                             |
| Democratic elections (Alliance)    | Phase 4+    | Requires active player base to make elections meaningful                                           |
| Auction / tournament               | Phase 5+    | Nice-to-have, not load-bearing                                                                     |
| 3D tactical visualization          | Phase 5+    | 2D Konva top-down view (10.22) is sufficient and already specified                                 |

---

## Complexity Reference

| Category                 | Features     | Implementation Complexity | Risk                                     |
| ------------------------ | ------------ | ------------------------- | ---------------------------------------- |
| Session / Character      | ~15 features | Low-Medium                | Low                                      |
| Rank / Personnel         | ~14 features | Medium                    | Medium (rank cap enforcement)            |
| Duty Card / Org          | ~8 features  | High                      | High (100+ positions, delegation chains) |
| CP System                | ~5 features  | Low                       | Low                                      |
| Galaxy Map / Planet      | ~14 features | High                      | Medium                                   |
| Fleet System             | ~15 features | High                      | Medium                                   |
| Logistics                | ~9 features  | Medium                    | Low                                      |
| Strategic Commands (70+) | ~70 features | Very High                 | High (command interaction surface)       |
| Tactical Combat (RTS)    | ~23 features | Very High                 | High (real-time WebSocket at scale)      |
| Espionage                | ~15 features | High                      | Medium (state machine for infiltration)  |
| Coup d'état              | ~6 features  | High                      | High (world-altering consequences)       |
| Communication            | ~6 features  | Medium                    | Low                                      |
| Political / Influence    | ~16 features | Medium                    | Low                                      |
| NPC AI                   | ~3 features  | Very High                 | High                                     |

---

## Sources

- gin7 manual analysis via `/Users/apple/Desktop/openlogh/docs/feature-checklist.md` and `feature-audit.md` (HIGH confidence — primary source)
- [EVE Online Fleet Command Guide — EVE University Wiki](https://wiki.eveuniversity.org/Fleet_Command_Guide) (HIGH confidence)
- [EVE Online Corporation Roles — Support](https://support.eveonline.com/hc/en-us/articles/203217712-Roles-Listing) (HIGH confidence)
- [Crusader Kings 3 Intrigue System — PC Gamer](https://www.pcgamer.com/crusader-kings-3-ck3-intrigue-hooks/) (MEDIUM confidence — design patterns, not direct analogue)
- [Foxhole Logistics Design — Official Wiki](https://foxhole.fandom.com/wiki/Community_Guides/Logistics) (MEDIUM confidence — logistics chain patterns)
- [Hades Star vs OGame Design Philosophy — Hades Star FAQ](https://hadesstar.com/faq.html) (MEDIUM confidence — anti-feature rationale)
- [Asynchronous MMO Persistence — Game Developer](https://www.gamedeveloper.com/game-platforms/analysis-asynchronicity-in-game-design) (MEDIUM confidence — offline design patterns)
- [OGame Fleet Management 2025](https://ogame.life/ogame/blog/ogame-fleet-management-for-2025-top-strategies/) (LOW confidence — genre context only)
