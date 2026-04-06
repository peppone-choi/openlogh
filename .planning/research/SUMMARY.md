# Project Research Summary

**Project:** Open LOGH — gin7 (은하영웅전설 VII) 게임 로직 전면 재작성
**Domain:** Web-based Multiplayer Strategy MMO (Spring Boot 3 + Next.js 15)
**Researched:** 2026-04-06
**Confidence:** HIGH

## Executive Summary

Open LOGH is a web-based multiplayer strategy MMO that faithfully re-implements gin7 (은하영웅전설 VII, BOTHTEC 2004) as an online game. The core value is "organisation simulation" — players join as officers in either the Galactic Empire or the Free Planets Alliance, operate within a strict military hierarchy enforced by a position-card authority system (직무권한카드), and cooperate (or compete) within their faction to achieve victory. The existing codebase (forked from OpenSamguk) already contains substantial infrastructure — tick engine, WebSocket event pipeline, STOMP channels, entity model, CP system, PositionCard registry, scenario framework — but the game logic layer is still Three Kingdoms (삼국지) throughout. This rewrite replaces that logic with gin7 mechanics without disturbing the architectural skeleton.

The recommended approach is a sequential 7-phase rewrite that mirrors the dependency graph of gin7 systems: (1) strip out Three Kingdoms and establish the ship unit foundation, (2) build the 81-command gin7 authority system, (3) implement the real-time tactical battle engine, (4) add the economy and AI layers, and (5) rebuild the frontend. The stack requires only 3 new dependencies (`@react-three/postprocessing`, `hibernate-types-60`, `@radix-ui/react-slider`) — everything else reuses the existing, confirmed-working technology. No new renderer, no new game engine, no GraphQL — the existing Spring Boot/Kotlin + Next.js/TypeScript stack is sufficient for all planned features.

The critical risk is the layered nature of the legacy codebase: Three Kingdoms artifacts (commands, economy logic, battle engine, authority fallbacks) are woven through every service. Two patterns in particular must be eliminated early or they will undermine the entire gin7 system: the `officerLevel >= 5` authority bypass in `CommandExecutor.kt` (which makes PositionCards irrelevant), and the disconnected `runMonthlyPipeline()` in `TickEngine.kt` (which means the economy system will never fire). Both of these are confirmed code-level issues, not theoretical risks. The migration sequence must treat "remove legacy" and "wire new system" as inseparable steps — partial substitution leaves the codebase in a broken intermediate state that is harder to debug than either extreme.

---

## Key Findings

### Recommended Stack

The existing stack is production-grade and correct for this domain. Zero architectural changes to the multi-JVM process model (gateway-app + game-app), WebSocket STOMP pipeline, or database layer are required. The only stack additions are three narrow libraries that fill specific capability gaps: bloom/glow post-processing for the 3D battle view, JSONB column support for arsenal production queues and command state, and an accessible range slider for the energy allocation panel.

**Core technologies (existing — do not replace):**
- Spring Boot 3 / Kotlin 2.1.0: Backend framework — already in production, all new services extend it
- Spring WebSocket + STOMP: Real-time event delivery — existing channels (`/topic/world/{id}/events`, `/topic/world/{id}/tactical-battle/{id}`) are extended, not replaced
- PostgreSQL 16 + Flyway (V1–V44 done): Persistent state — new work starts at V45
- Redis 7: Session cache and connected-client tracking only — tactical battle state stays in-memory (ConcurrentHashMap), never Redis
- Next.js 15 + React 19 + Zustand: Frontend — Zustand stores batch WebSocket tick events to avoid per-event re-renders
- React Konva: 2D tactical map (dot-style unit icons) — already used for galaxy map, correct tool for 2D battle grid
- React Three Fiber + Drei + Three.js 0.183: 3D close-combat view — existing `SeasonEffects.tsx` particle pattern is directly reusable

**New additions (3 only):**
- `@react-three/postprocessing ^2.16`: Bloom glow on BEAM laser lines in 3D battle view — wraps proven postprocessing library, compatible with RTF 9.5.0
- `com.vladmihalcea:hibernate-types-60:2.21.1`: `@Type(JsonType)` for JSONB columns (arsenal queues, command state) — required for Hibernate 6 / Spring Boot 3
- `@radix-ui/react-slider ^1.2`: Energy allocation panel sliders — consistent with existing Radix UI usage

### Expected Features

**Must have (table stakes — game is broken without these):**
- 11-class ship unit system (전함/순양함/구축함 etc.) with 88 per-faction subtypes — ship_stats JSON already exists, ShipUnit entity needed
- 81-command gin7 authority system replacing 93 Three Kingdoms commands — commands.json fully defines all 81 with CP costs and wait/duration times
- Real-time tactical battle grid: 2D Konva canvas with dot-style unit icons (△기함, □전함, ◇구축함), faction colors (Empire #4466ff, Alliance #ff4444)
- Energy allocation system: 6-channel BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR sliders, sum must equal 100, real-time WebSocket update
- Planet auto-production (조병창): tick-driven ship/ground unit queue — no player micro, set-and-forget
- Weapon system: beam (range-optimal), gun, missile (supply-consuming, critical constraint)
- Strategic game screen frontend: PositionCard tab + 8-stat panel + chat — replaces Three Kingdoms city/general cards

**Should have (competitive differentiators):**
- Command Range Circle (커맨드레인지서클): expands per tick, resets on order, rate = commander stat — unique gin7 mechanic
- Cross-rank proposal system (제안): junior officers propose to superiors for approval — org simulation backbone
- Detection/stealth via SENSOR allocation: fog-of-war creates information asymmetry
- Ground combat + 6 planet capture options (항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동) with differential effects
- Fortress cannon mechanic (토르해머): fires across entire lane including friendlies — extreme tactical tension
- Personality-driven AI (5 trait types, stat-weighted utility scoring) — offline players behave as credible subordinates
- Fezzan loan system: borrow funds with quarterly interest; default triggers Fezzan ending condition
- 3D close-combat view (React Three Fiber): proportional unit blocks + beam effects + explosion particles

**Defer to v2.1+:**
- Full scenario data for all 10 scenarios (data-heavy, mechanics must work first)
- Dedicated political faction UI (쿠데타 UI, 의회 선거 UI, 페잔 정보 UI)
- Balance pass (fleet sizes, economic rates, CP costs) — requires playtest data

**Explicit anti-features (never implement):**
- Turn-reservation queue (삼국지 artifact) — gin7 uses real-time wait+duration, not queuing
- Per-ship HP tracking — gin7 operates at unit granularity (300 ships/unit)
- Full 3D ship models for every unit — performance-prohibitive at 2,000 concurrent; dot icons are gin7 identity
- Click-to-build production queues — gin7 arsenals auto-produce, no micro

### Architecture Approach

The existing multi-JVM architecture (gateway-app:8080 → game-app:9001+) with ConcurrentHashMap-based in-memory tactical battle state is the correct pattern. The TickEngine's 1-second tick (= 24 game-seconds) already handles all periodic processing; gin7 systems plug into it via `runMonthlyPipeline()` (currently wired to nothing — this is Pitfall 3). Tactical battle state must remain in JVM memory (never Redis) to avoid serialisation overhead on the 1-second tick cycle. Frontend WebSocket events must be batched in Zustand stores (not applied as direct React state) to avoid per-tick re-renders.

**Major components (new or significantly modified):**
1. `Gin7CommandRegistry` — 81 gin7 commands in 7 group packages, replaces `CommandRegistry` via Spring `@Primary`
2. `ShipUnit` entity (V46 migration) — 300-ship unit with class, subtype, missile stock, flagship flag; linked to Fleet via FK
3. `ShipStatRegistry` — loads ship_stats_empire/alliance JSON into memory; injected into TacticalBattleService at battle init
4. `Gin7EconomyService` — replaces Three Kingdoms `EconomyService`; wired into `TickEngine.runMonthlyPipeline()` for tax, fleet maintenance, Fezzan interest
5. Extended `TacticalBattleEngine` — adds missile weapon system, detection matrix, 5-phase tactical turn, ground battle state
6. New WebSocket channels: `/app/battle/{sessionId}/{battleId}/energy`, `/formation`, `/retreat`, `/attack-target`, `/planet-conquest`; topic channels separated per `battleId` (not session-wide)
7. Frontend tactical UI — split canvas: RTF 3D (close-combat view top) + React Konva 2D (tactical grid bottom); lazy-loaded to avoid dual WebGL context memory spike

**Build order (dependency-driven):**
Entity → Repository → Service → Command → Controller; tactical engine after ShipUnit; economy after command system; frontend after each backend system stabilises.

### Critical Pitfalls

1. **CommandRegistry mass test destruction** — 93 Three Kingdoms commands are referenced by dozens of tests by string key. Strategy: register gin7 stub commands first, update `CommandParityTest` expected set to gin7 81-command list (RED), then implement to GREEN. Never delete before stubs exist.

2. **`officerLevel >= 5` authority bypass persists** — confirmed at 7+ locations in `CommandExecutor.kt`, `CommandService.kt`, `BattleService.kt`, etc. This bypass makes PositionCards irrelevant. Must be fully removed after gin7 81-command `requiredCards` mapping is complete. Verification: `grep -r "officerLevel >= 5"` must return 0.

3. **`TickEngine.runMonthlyPipeline()` is disconnected** — confirmed via code inspection: line 126-136 has TODO comment, `EconomyService.processMonthly()` is never called from the tick. Any economy implementation will silently do nothing until this wire is connected. Wire it as the first task of Phase 5.

4. **Missile system absent from TacticalBattleEngine** — only BEAM and GUN channels implemented; missile has no handler and `suppliesRemaining` is not tracked on TacticalUnit. Without this, planet bombardment commands have no resource constraint. Implement missile weapon system before planet conquest commands.

5. **`BattleEngine.kt` (Three Kingdoms) and `TacticalBattleEngine.kt` coexist** — two battle engines create routing ambiguity during migration. Plan the deletion list at Phase 1 start; do not allow both to route production traffic simultaneously.

6. **Ship subtype stats hardcoded in TacticalBattleEngine** — `BEAM_BASE_DAMAGE = 30.0`, `GUN_BASE_DAMAGE = 40.0` are constants; 88-subtype JSON stats are never injected into TacticalUnit. All subtypes fight with identical power. Fix at Phase 2 alongside ShipUnit entity creation.

7. **STOMP battle channel not scoped to battleId** — broadcasting all tactical events on the session-wide channel sends every battle's events to all 2,000 clients. Redesign channels to `/topic/world/{sessionId}/tactical-battle/{battleId}` (already the correct topic path in code, confirm subscription management on frontend).

---

## Implications for Roadmap

Based on the dependency graph surfaced by all four research files, a 7-phase roadmap is recommended. Phases 1–3 are strictly sequential (each unblocks the next). Phases 4 and 5 can be parallelised partially (battle engine and economy are independent). Phase 6 (AI) requires Phase 3's command system. Phase 7 (frontend) can be developed incrementally alongside each backend phase.

### Phase 1: Three Kingdoms Removal + Ship Unit Foundation
**Rationale:** Every downstream system depends on gin7 entities and a clean command registry. Partial cleanup leaves two coexisting game universes, making all subsequent development unreliable. This is the prerequisite phase for everything.
**Delivers:** `ShipUnit` entity (V45–V46 migrations), `ShipStatRegistry`, `Gin7CommandRegistry` shell (stub implementations), deletion of 93 Three Kingdoms commands and `BattleEngine.kt`/`FieldBattleService.kt`, deletion of Three Kingdoms frontend components
**Addresses:** FEATURES.md Phase A items (ship unit system, ground unit entity, flagship entity)
**Avoids:** Pitfall 1 (test destruction via stub-first TDD), Pitfall 5 (two battle engines coexisting)

### Phase 2: gin7 81-Command System
**Rationale:** The PositionCard authority hierarchy is the core product value. All other game actions are commands; none work correctly until the 81-command system replaces the Three Kingdoms registry.
**Delivers:** All 81 gin7 commands implemented and PositionCard-gated, real-time execution pipeline (waitTime → execute → WebSocket broadcast), `officerLevel >= 5` bypass fully removed, strategic game screen frontend (직무카드 탭 + 8-stat panel)
**Addresses:** FEATURES.md Phase B items (command system, proposal system, strategic UI)
**Avoids:** Pitfall 2 (authority bypass), standard verification: `grep "officerLevel >= 5"` = 0 results
**Research flag:** Needs phase-level research for the 7 command group implementations (operations 16, personal 15, commander 8, logistics 6, personnel 10, politics 12, intelligence 14) — complex domain-specific logic, not standard patterns

### Phase 3: Real-Time Tactical Battle Engine
**Rationale:** Battle is the core gameplay loop. The energy allocation, formation, detection, missile, and planet capture systems all depend on a working tactical engine. The 2D tactical grid must be solid before the 3D close-combat view is added.
**Delivers:** 2D Konva tactical grid with dot-style unit icons, energy allocation WebSocket real-time state, weapon damage engine (beam/gun/missile + supply tracking), formation bonuses, Command Range Circle, detection/stealth, fortress cannon, ground combat, 6 planet capture commands, 3D close-combat view (React Three Fiber + `@react-three/postprocessing`)
**Addresses:** FEATURES.md Phase C items
**Avoids:** Pitfall 4 (missile system before planet capture), Pitfall 6 (STOMP channels scoped per battleId), Pitfall 7 (88 subtype stats injected from ShipStatRegistry, not hardcoded constants)
**Uses stack:** `@react-three/postprocessing` (bloom), React Konva (2D grid), `@radix-ui/react-slider` (energy sliders)
**Research flag:** Needs research for ground combat terrain rules (행성타입별 지상전 규칙) and 5-phase tactical turn structure — gin7 manual is primary source but implementation choices need validation

### Phase 4: Economy System
**Rationale:** Planet economy and arsenal auto-production provide the strategic resource loop that gives battles meaning. Fezzan loan system adds long-term risk. This phase is independent of Phase 3 and can be developed in parallel once Phase 2 is complete.
**Delivers:** `Gin7EconomyService` wired into `TickEngine.runMonthlyPipeline()`, planet resource tick, arsenal auto-production (tick-driven), quarterly tax cycle, Empire dual budget split (군무성/통수본부), Fezzan loan + default ending trigger, population-based unit cap, `hibernate-types-60` JSONB for arsenal queues
**Addresses:** FEATURES.md Phase D economy items
**Avoids:** Pitfall 3 (wire `runMonthlyPipeline()` as first task — confirmed currently disconnected), economy-in-command-handler antipattern (all periodic processing goes through tick, never command handlers)
**Research flag:** Standard tick integration pattern — no research needed; the TickEngine integration point is confirmed in codebase

### Phase 5: AI System
**Rationale:** AI officers must use the same 81-command system as human players. This phase requires Phase 2 complete. Faction-level AI provides credible NPC factions for single-faction sessions.
**Delivers:** Personality-driven AI (5 trait types × stat-weighted utility scoring, not behavior trees), replacing Three Kingdoms AI decision logic; faction-level AI (budget allocation + personnel decisions, round-robin per tick); NPC AI (100-tick batch scheduling); AI integrated into TickEngine via 10-tick/100-tick intervals
**Addresses:** FEATURES.md Phase D AI items
**Avoids:** NPC AI O(n) per tick performance trap — slot-based scheduling groups NPCs, processes 1 group per tick

### Phase 6: Frontend Integration and Polish
**Rationale:** Frontend can be developed incrementally alongside backend phases, but a dedicated integration pass is needed to ensure the full strategic game screen, all WebSocket subscriptions, and the retro UI aesthetic are coherent.
**Delivers:** Complete strategic game screen (직무권한카드 탭, 8-stat panel, proposal system UI, chat), tactical battle UI (split 2D/3D canvas, linked energy sliders, command range circle display, detection fog overlay), galaxy map enhanced with fleet position and movement range highlight, retro monospace font (Space Mono via `next/font/google`), Three Kingdoms frontend component removal verified
**Avoids:** Pitfall 8 (React Three Fiber + Konva dual mount — lazy loading required), UX pitfall: linked slider auto-rebalancing, command wait/duration display in both real-time seconds and game-time format

### Phase 7: Scenario Data and Balance
**Rationale:** Mechanics must be proven before populating scenario initial data. Balance tuning requires playtest data.
**Delivers:** Full initial data for all 10 scenarios (인물 배치 + 이벤트 트리거), balance adjustments to ship stats / CP costs / economic rates based on playtesting, dedicated faction political UI (쿠데타, 선거, 페잔 정보)
**Addresses:** FEATURES.md v2.1+ deferred items
**Research flag:** Scenario data is data engineering (gin7 manual reference), not code research — no research-phase needed

### Phase Ordering Rationale

- **Phases 1 → 2 → 3 are strictly sequential:** ShipUnit entity is required before tactical combat can calculate damage; 81-command registry is required before PositionCard gating can function; 2D tactical grid must work before 3D is layered on
- **Phase 4 can overlap Phase 3:** Economy tick integration is independent of battle engine
- **Phase 5 requires Phase 2 complete:** AI must execute gin7 commands via the same validated path as humans
- **Phase 6 overlaps all:** Frontend can be built incrementally against each API as it stabilises, with a final integration pass
- **Phase 7 is truly last:** Balance tuning requires a playable game to test against

### Research Flags

Phases needing deeper research during planning:
- **Phase 2 (Commands):** 81-command specification per group needs per-command implementation design — complex domain logic, reference: `commands.json` + `docs/REWRITE_PROMPT.md`. Recommend `/gsd:research-phase` before planning Phase 2 tasks.
- **Phase 3 (Battle):** Ground combat terrain rules and 5-phase tactical turn structure have implementation ambiguities not fully resolved in current documentation. Recommend research on gin7 manual section covering 육전대 지상전 and 전술전 5단계.

Phases with standard patterns (no research needed):
- **Phase 1:** Entity migration and registry cleanup — well-documented Spring Boot patterns, confirmed codebase structure
- **Phase 4:** Economy tick wiring — TickEngine integration point is confirmed and documented in code comments
- **Phase 5:** Utility AI scoring — straightforward Kotlin implementation, no library research needed
- **Phase 6:** Frontend components — existing patterns in codebase, all libraries confirmed
- **Phase 7:** Scenario data — data entry from gin7 manual, not engineering research

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Existing stack verified via direct package.json + build.gradle.kts inspection; 3 new additions verified against peer dependency compatibility |
| Features | HIGH | Primary sources: `commands.json` (81 commands), `ship_stats_empire/alliance.json` (88 subtypes), `docs/REWRITE_PROMPT.md` (gin7 spec), `docs/reference/gin4ex_wiki.md` |
| Architecture | HIGH | Direct code inspection of CommandRegistry.kt, TickEngine.kt, TacticalBattleEngine.kt, TacticalBattleService.kt, EconomyService.kt, Fleet.kt |
| Pitfalls | HIGH | All 7 critical pitfalls confirmed via line-level code inspection — not theoretical risks |

**Overall confidence:** HIGH

### Gaps to Address

- **Ground combat terrain rules:** REWRITE_PROMPT.md defines 6 capture commands and their effects on approval/economy/defense, but the exact per-planet-type terrain restrictions for 육전대 강하 are not fully codified. Address during Phase 3 planning via gin7 manual cross-reference.
- **Ship subtype stat balance:** 88 subtypes × 2 factions = 176 stat sets exist in JSON but have never been playtested in the web MMO context. The data is trusted as correct per gin7 original, but scaling to web MMO economics may require adjustment. Flag for Phase 7 balance pass.
- **Concurrent session performance:** The 2,000 concurrent user target is stated in CLAUDE.md. The `CommandExecutor.buildConstraintEnv()` O(n) query bottleneck (Pitfall: confirmed) and O(n²) tactical unit distance calculation become critical above 200 users/session and 20 units/battle respectively. Performance validation should be planned before Phase 3 production deployment.
- **Flyway V45+ migration sequencing:** Three Kingdoms column removals involve FK dependency chains. Each migration file must verify `pg_constraint` dependencies before issuing DROP COLUMN. This is a procedural gap, not a knowledge gap — enforce via migration review checklist.

---

## Sources

### Primary (HIGH confidence)
- `docs/REWRITE_PROMPT.md` — gin7 manual synthesis, weapon tables, planet capture table, economy rules, UI specifications
- `backend/shared/src/main/resources/data/commands.json` — 81-command canonical definition with CP costs, wait times, durations
- `backend/shared/src/main/resources/data/ship_stats_empire.json` / `ship_stats_alliance.json` — 88-subtype combat stats per faction
- `backend/shared/src/main/resources/data/ground_unit_stats.json` — 11 ground unit type stats
- Direct codebase inspection: `CommandRegistry.kt`, `CommandExecutor.kt`, `TickEngine.kt`, `TacticalBattleEngine.kt`, `TacticalBattleService.kt`, `EconomyService.kt`, `Fleet.kt`, `BattleEngine.kt`
- `frontend/package.json`, `backend/game-app/build.gradle.kts` — confirmed current package versions
- `CLAUDE.md` — domain mapping, stat definitions, ship classes, rank system, architecture decisions

### Secondary (MEDIUM confidence)
- `@react-three/postprocessing` pmndrs GitHub — RTF 9.x + Three.js 0.160+ compatibility via package peer deps
- `com.vladmihalcea:hibernate-types-60` docs — Hibernate 6 / Spring Boot 3 compatibility via package naming convention
- `docs/reference/unit_composition.md` — fleet/patrol/transport/landing force composition rules
- `docs/reference/gin4ex_wiki.md` — gin4 EX reference (gin7 base mechanics)
- `.planning/PROJECT.md` — current milestone scope and already-built system inventory

### Tertiary (LOW confidence)
- gin7 ground combat terrain rules: partially specified in REWRITE_PROMPT.md, full rules require gin7 manual reference — validate during Phase 3 planning
- Web MMO performance at 2,000 concurrent users: theoretical scaling analysis based on code patterns, not load-tested

---
*Research completed: 2026-04-06*
*Ready for roadmap: yes*
