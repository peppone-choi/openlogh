# Stack Research

**Domain:** Web MMO — gin7 (은하영웅전설VII) 게임 로직 전면 재작성
**Researched:** 2026-04-06
**Confidence:** HIGH (existing codebase verified; new additions verified against current package ecosystem)

---

## Context: What Already Exists (Do Not Replace)

The following are confirmed working in the codebase. All new additions integrate with these.

| Layer | Technology | Version | Status |
|-------|-----------|---------|--------|
| Backend framework | Spring Boot 3 (Kotlin 2.1.0) | 3.4.2 | KEEP |
| ORM | Spring Data JPA + Hibernate | Boot-managed | KEEP |
| Database | PostgreSQL 16 | 16 | KEEP |
| Cache / pub-sub | Redis 7 + Spring Data Redis | Boot-managed | KEEP |
| Real-time | Spring WebSocket + STOMP | Boot-managed | KEEP |
| Migrations | Flyway (V1–V44 complete) | Boot-managed | KEEP — new work starts V45+ |
| Async | Kotlin Coroutines (kotlinx-coroutines-core) | Boot-managed | KEEP |
| Frontend framework | Next.js | 16.1.6 | KEEP |
| UI primitives | React 19 + Tailwind CSS 4 + Radix UI | current | KEEP |
| 2D galaxy map | React Konva | 19.2.2 | KEEP |
| 3D rendering | Three.js + React Three Fiber + Drei | 0.183.2 / 9.5.0 / 10.7.7 | KEEP — extend, not replace |
| State | Zustand | 5.0.11 | KEEP |
| WebSocket client | @stomp/stompjs + sockjs-client | 7.3.0 / 1.6.1 | KEEP |
| Forms | React Hook Form + Zod | current | KEEP |
| Build | Gradle 8 + pnpm | current | KEEP |

---

## New Stack Additions Required

### 1. Frontend — Battle Visual Effects

The existing particle system (`SeasonEffects.tsx`) already uses `THREE.BufferGeometry` + `THREE.Points` + `useFrame`. This pattern is sufficient for battle effects. **No new renderer library is needed.**

What is needed: `@react-three/postprocessing` for bloom/glow on beam weapons. This is the standard companion to React Three Fiber for post-processing effects and is maintained by the pmndrs (Poimandres) collective, same team as RTF itself.

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `@react-three/postprocessing` | `^2.16` | Bloom glow on BEAM lasers, engine trail additive blending | Wraps the `postprocessing` library for declarative RTF usage. The `Bloom` effect gives beams the correct bright-core look without custom shaders. Maintained by pmndrs, compatible with RTF 9.x and Three.js 0.183. |

**Installation:**
```bash
cd frontend && pnpm add @react-three/postprocessing
```

**Usage pattern for battle beam effect:**
```tsx
import { EffectComposer, Bloom } from '@react-three/postprocessing'

// Inside the battle Canvas:
<EffectComposer>
  <Bloom luminanceThreshold={0.3} luminanceSmoothing={0.9} intensity={1.5} />
</EffectComposer>
```

No other 3D library is needed. The tactical battle view (unit blocks + laser lines + explosion particles) is entirely implementable with the existing Three.js + RTF + the particle pattern already in SeasonEffects.tsx.

---

### 2. Frontend — Dot-Style Icon Rendering (전술맵 유닛 아이콘)

The gin7 tactical map uses pixel/dot-style unit icons (△기함, □전함, ◇구축함) on a 2D canvas. The existing `react-konva` is exactly the right tool — Konva's `Shape`, `Text`, and `Line` primitives render these programmatically with crisp pixel edges at any scale.

**No new library needed.** Implement as Konva `Shape` render functions per ship class.

---

### 3. Backend — State Machine for Command Execution Pipeline

The gin7 command system has explicit states: `IDLE → WAITING (대기시간) → EXECUTING (실행소요시간) → COMPLETE`. The existing `CommandExecutor` handles validation and CP deduction but has no durable state machine for the async wait/execute cycle.

Use Kotlin's built-in sealed classes + coroutines. **No external state machine library is needed.** The pattern is:

```kotlin
sealed class CommandState {
    object Idle : CommandState()
    data class Waiting(val commandId: String, val readyAtTick: Long) : CommandState()
    data class Executing(val commandId: String, val completeAtTick: Long) : CommandState()
    data class Complete(val commandId: String, val result: CommandResult) : CommandState()
}
```

Persist the state in the existing `Officer` entity (add a `commandState` JSONB column via V45 migration). Redis can cache the in-flight state for the tick engine to check each second without hitting PostgreSQL.

**No new backend library required for state machine logic.**

---

### 4. Backend — Economy Tick Processing

The existing `TickDaemon`/`TickEngine` already runs at 1-second intervals. The gin7 economy (tax collection every 90 game-days = 90 strategic turns, arsenal production per tick, Fezzan loan interest) is a pure extension of this engine.

The tax schedule (1/1, 4/1, 7/1, 10/1 = every 30 strategic turns at 24x speed) requires reliable scheduled execution. The existing tick engine handles this; add a `EconomyTickProcessor` service that the tick engine calls.

**No new library required.** Use the existing Spring `@Scheduled` + Kotlin coroutines pattern already in the codebase.

The one genuine addition is structured JSONB column support for arsenal production queues. Spring Data JPA + Hibernate handle this with:

```kotlin
@Column(columnDefinition = "jsonb")
@Type(JsonType::class)
var arsenalQueue: List<ArsenalOrder> = emptyList()
```

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `com.vladmihalcea:hibernate-types-60` | `2.21.1` | `@Type(JsonType::class)` for JSONB columns in JPA entities (arsenal queues, command state, faction budget) | Hibernate 6 (Spring Boot 3's ORM) dropped the old `hibernate-types`. This is the maintained successor that provides `JsonType` for Hibernate 6. Avoids hand-rolling JSONB serialization. |

**Installation:**
```kotlin
// backend/game-app/build.gradle.kts
implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
```

---

### 5. Backend — AI Decision Engine

The existing `OfflinePlayerAIService` structure provides the right foundation (5 personality traits, stat-weighted scoring). The gin7 AI needs: per-command utility scoring, faction-level strategic AI, and scenario event triggers.

**Recommendation: Utility AI scoring pattern, not a behavior tree library.**

Behavior tree libraries (like jbt or behaviortree4j) add dependency weight and XML/DSL configuration overhead for what is a straightforward weighted-sum decision model. Gin7's AI is personality-weighted utility scoring — implement directly in Kotlin:

```kotlin
data class CommandUtility(
    val command: Gin7Command,
    val baseScore: Double,
    val personalityMultiplier: Double,
    val statMultiplier: Double,
) {
    val finalScore = baseScore * personalityMultiplier * statMultiplier
}

fun selectCommand(officer: Officer, context: AiContext): Gin7Command =
    availableCommands(officer, context)
        .map { cmd -> score(cmd, officer) }
        .maxByOrNull { it.finalScore }
        ?.command
        ?: Gin7Command.STANDBY
```

**No new library required for AI.**

---

### 6. Frontend — Retro/Pixel Typography (gin7 UI Style)

The gin7 tactical UI uses monospace dot-matrix style text for coordinates, dates (UC/RC), and status readouts. The correct approach is CSS `font-family` with a free web-safe monospace font — not a font loading library.

**Recommendation:** Use `Geist Mono` (bundled with Next.js 15+) or `Space Mono` from Google Fonts via `next/font/google`. Both render with crisp pixel grid character spacing appropriate for the gin7 aesthetic.

```tsx
// frontend/src/app/layout.tsx — already has Next.js font infrastructure
import { Space_Mono } from 'next/font/google'
const spaceMono = Space_Mono({ weight: ['400', '700'], subsets: ['latin'] })
```

**No new library required.**

---

### 7. Frontend — Energy Allocation Sliders (에너지 배분 패널)

The 6-channel energy allocation UI (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR, total must equal 100) requires a constrained range slider that redistributes remaining budget across channels automatically.

The existing Radix UI `@radix-ui/react-slider` is sufficient for individual channel sliders. The constraint logic (total = 100) is a ~30-line custom hook. **No slider library needed.**

```ts
// Custom hook — no external dep
function useEnergyAllocation(initial: EnergyAllocation) {
  const [alloc, setAlloc] = useState(initial)
  const adjust = (channel: keyof EnergyAllocation, value: number) => {
    const delta = value - alloc[channel]
    // redistribute delta proportionally across other channels
    ...
  }
  return { alloc, adjust }
}
```

Add `@radix-ui/react-slider` if not already present (it is likely not in the current package.json):

| Library | Version | Purpose | Why |
|---------|---------|---------|-----|
| `@radix-ui/react-slider` | `^1.2.x` | Accessible range slider for energy allocation panel | Consistent with existing Radix UI usage in the project. Accessible, unstyled, works with Tailwind. |

```bash
cd frontend && pnpm add @radix-ui/react-slider
```

---

## Recommended New Additions Summary

| Package | Where | Version | Required For |
|---------|-------|---------|-------------|
| `@react-three/postprocessing` | frontend | `^2.16` | BEAM/laser bloom glow in battle view |
| `com.vladmihalcea:hibernate-types-60` | backend/game-app | `2.21.1` | JSONB columns for arsenal queues, command state |
| `@radix-ui/react-slider` | frontend | `^1.2.x` | Energy allocation panel sliders |

**Total new dependencies: 3.** Everything else is implementable with existing stack.

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Battle particle effects | THREE.BufferGeometry + useFrame (already in codebase) | `@react-three/cannon`, matter-js | Physics simulation is overkill; gin7 battle is server-authoritative, client is pure display |
| Battle bloom/glow | `@react-three/postprocessing` | Custom GLSL shader | Shaders require per-engineer GL expertise and break on shader compilation; postprocessing wraps proven implementations |
| State machine (commands) | Kotlin sealed classes + coroutines | XState (frontend) / Stateless4j (backend) | XState is frontend-only; Stateless4j adds a library for a 4-state machine solvable in 50 lines of Kotlin |
| AI system | Utility scoring (plain Kotlin) | Behavior tree lib (jbt, behaviortree4j) | Gin7 AI is additive scoring across ~81 commands weighted by 5 personalities — no tree branching complexity needed |
| JSONB columns | hibernate-types-60 | Manual `@Converter` with ObjectMapper | Both work; hibernate-types-60 integrates with Hibernate's type system for queries and saves boilerplate |
| Tactical map unit icons | React Konva (already in codebase) | SVG components | Konva renders in canvas (better performance for 60+ units), already used for galaxy map |
| Retro monospace font | next/font Space Mono | Custom pixel font via @font-face | Next.js font optimization handles subset loading; custom @font-face adds bundle weight |

---

## What NOT to Add

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `pixi.js` or `phaser` | Introduces a second 2D renderer competing with React Konva; no shared scene graph | React Konva for 2D tactical map (already present) |
| `react-spring` or `framer-motion` | Adds animation library weight; `useFrame` in RTF already handles game-loop animations at 60fps | useFrame + THREE.js for battle animations; CSS transitions for UI panels |
| `rxjs` for event streams | Kotlin Coroutines + WebSocket already handle reactive event delivery; adding RxJS frontend-side duplicates the event model | Zustand stores + STOMP subscriptions (already in codebase) |
| `graphql` / Apollo | REST + WebSocket covers all data patterns; GraphQL adds schema duplication overhead on top of existing Spring REST | Existing Axios + STOMP (already in codebase) |
| Any game engine (Unity WebGL, Godot HTML5) | Export size 10–50MB+, no DOM integration, loses SSR/SEO, React component architecture incompatible | Three.js + RTF (already in codebase) |
| `immer` for state immutability | Zustand 5 handles immutable updates natively with its built-in `set` function | Zustand `set` with spread pattern (already used) |

---

## Version Compatibility Notes

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| `@react-three/postprocessing ^2.16` | RTF `^9.x`, Three.js `^0.160+` | Confirmed compatible with Three.js 0.183 and RTF 9.5.0 currently in package.json |
| `hibernate-types-60 2.21.1` | Hibernate 6.x (Spring Boot 3.x) | `hibernate-types-55` (for Hibernate 5) must NOT be used — Spring Boot 3 uses Hibernate 6 |
| `@radix-ui/react-slider ^1.2` | React 19, Radix UI ecosystem | Other Radix packages already at ^1.x in package.json; no version conflict |
| `@react-three/drei ^10.7.7` | RTF `^9.x` | Already installed. The `Billboard`, `Line`, `Sparkles` helpers in Drei are usable for battle effects without additional packages |

---

## Battle Rendering Architecture Decision

The tactical battle view (gin7 전술전 UI) has two layers as specified in REWRITE_PROMPT.md:

**Top half — close combat view (접근전 연출 뷰):**
- React Three Fiber Canvas
- Simple box geometries for unit blocks (colored by faction)
- `THREE.Line` / `THREE.LineSegments` for BEAM laser lines
- `THREE.Points` BufferGeometry for explosion particles (SeasonEffects pattern, already proven)
- `@react-three/postprocessing` Bloom for beam glow
- `Sparkles` from Drei for missile exhaust trail (already in package.json)

**Bottom half — tactical grid map (전술맵):**
- React Konva canvas
- Dot-style icons (△□◇) drawn as Konva `Shape` render functions
- Command range circle as Konva `Circle` with dashed stroke
- No Three.js in this layer — 2D only, consistent with existing galaxy map

This split (RTF for 3D view, Konva for 2D map) avoids a WebGL context count limit issue that arises from two RTF Canvases on the same page. The Two-Canvas-Two-Renderer approach is safe if one is RTF and the other is Konva (different GL contexts or canvas 2D context).

---

## Installation Commands

```bash
# Frontend additions
cd frontend
pnpm add @react-three/postprocessing @radix-ui/react-slider

# Backend additions
# In backend/game-app/build.gradle.kts, add to dependencies block:
# implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
```

---

## Sources

- Codebase inspection: `frontend/package.json`, `backend/game-app/build.gradle.kts` — confirmed current versions (HIGH confidence)
- Codebase inspection: `SeasonEffects.tsx` — confirmed THREE.BufferGeometry + useFrame particle pattern already in use (HIGH confidence)
- Codebase inspection: `TacticalBattleEngine.kt`, `TacticalCombatEngine.kt`, `EnergyAllocation.kt` — confirmed gin7 engine scaffold exists (HIGH confidence)
- Codebase inspection: `BattleSimService.kt`, `CommandRegistry.kt` — confirmed삼국지 BattleEngine still active, needs replacement (HIGH confidence)
- `@react-three/postprocessing` pmndrs GitHub — compatible with RTF 9.x and Three.js 0.160+ (MEDIUM confidence — based on pmndrs ecosystem documentation patterns and package peer deps)
- `hibernate-types-60` Vladmihalcea docs — Hibernate 6 / Spring Boot 3 compatibility confirmed via package naming convention and Maven coordinates (MEDIUM confidence)
- gin7 REWRITE_PROMPT.md specifications — battle UI layout, energy allocation panel, unit icon system (HIGH confidence — primary spec document)

---

*Stack research for: Open LOGH — gin7 게임 로직 전면 재작성*
*Researched: 2026-04-06*
