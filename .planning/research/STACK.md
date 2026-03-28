# Technology Stack — OpenLOGH Multiplayer Strategy Game

**Project:** Open LOGH (오픈 은하영웅전설)
**Researched:** 2026-03-28
**Scope:** Libraries/tools needed for _unimplemented_ features on the existing Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 base. Does not re-recommend what is already in place.

---

## What Is Already In Place (Do Not Re-Add)

The existing codebase has these — they are constraints, not recommendations:

| Already Present          | Version        |
| ------------------------ | -------------- |
| Spring Boot              | 3.4.2          |
| Spring WebSocket (STOMP) | via Boot 3.4.2 |
| Spring Data Redis        | via Boot 3.4.2 |
| @stomp/stompjs           | 7.3.0          |
| SockJS Client            | 1.6.1          |
| Zustand                  | 5.0.11         |
| React Three Fiber        | 9.5.0          |
| React Three Drei         | 10.7.7         |
| Kotlin                   | 2.1.0          |
| JJWT                     | 0.12.6         |

---

## Recommended Additions by Domain

### 1. Real-Time Combat (WebSocket / RTS Tactical Layer)

#### Backend: Spring WebSocket with External STOMP Broker

**What to add:** RabbitMQ as external STOMP broker relay

**Why:** The existing Spring WebSocket uses the in-memory simple broker. For 2,000-player sessions, the in-memory broker cannot be clustered and has no persistence, ACK, or receipt support. Spring's `enableStompBrokerRelay()` connects to RabbitMQ over TCP (via reactor-netty), enabling full fan-out to all WebSocket pods without each pod needing direct knowledge of other pods' clients. Redis Pub/Sub is an alternative but requires manual broadcast wiring and has no STOMP semantics — RabbitMQ is the right tool when you already have STOMP infrastructure.

| Dependency             | Artifact                                            | Why                                                 |
| ---------------------- | --------------------------------------------------- | --------------------------------------------------- |
| RabbitMQ STOMP plugin  | server-side only, no extra JAR                      | Enables Spring STOMP relay                          |
| reactor-netty          | `io.projectreactor.netty:reactor-netty`             | Required TCP transport for broker relay             |
| netty-all              | `io.netty:netty-all`                                | Required by reactor-netty for relay                 |
| Spring AMQP (optional) | `org.springframework.boot:spring-boot-starter-amqp` | Only if you need direct AMQP queue ops beyond STOMP |

**Confidence:** HIGH — verified against official Spring Framework docs ([External Broker Relay](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html))

**Configuration pattern:**

```kotlin
override fun configureMessageBroker(registry: MessageBrokerRegistry) {
    registry.enableStompBrokerRelay("/topic", "/queue")
        .setRelayHost("rabbitmq")
        .setRelayPort(61613)          // RabbitMQ STOMP port
        .setClientLogin("guest")
        .setClientPasscode("guest")
    registry.setApplicationDestinationPrefixes("/app")
}
```

**Do NOT use:** ActiveMQ — RabbitMQ has better Kotlin ecosystem support, Docker image quality, and is widely used in Spring game/chat reference projects. The in-memory simple broker is fine for development but must not reach production with 2,000 players.

---

#### Backend: WebSocket JWT Authentication

**What to add:** Channel interceptor pattern for STOMP CONNECT message JWT validation

**Why:** Standard Spring Security HTTP filter chain does not apply to WebSocket upgrade handshakes in the same way. JWT must be validated on the STOMP CONNECT frame, not the HTTP upgrade. Spring Security's `@EnableWebSocketSecurity` + `AuthorizationManager<Message<?>>` is the current approach (Spring Security 6+).

**No new dependency required** — this is configuration of existing `spring-boot-starter-security` + `jjwt` (both already present).

**Pattern:**

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class JwtChannelInterceptor(private val jwtUtil: JwtUtil) : ChannelInterceptor {
    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = StompHeaderAccessor.wrap(message)
        if (StompCommand.CONNECT == accessor.command) {
            val token = accessor.getFirstNativeHeader("Authorization")
                ?.removePrefix("Bearer ") ?: throw AccessDeniedException("No token")
            val principal = jwtUtil.validateAndGetPrincipal(token)
            accessor.user = principal
        }
        return message
    }
}
```

**Confidence:** HIGH — verified against Spring Framework token auth docs ([Token Auth](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html))

---

### 2. Turn-Based Game Tick Engine

#### Backend: Kotlin Coroutines Scheduler

**What to add:** `kotlinx-coroutines-core` + Spring's `@Scheduled` with coroutine bridge

**Why:** The gin7 game has a real-time 24x accelerated clock. Each game session needs its own independent tick loop (turn resolution, CP recovery every 5 min game-time, fleet movement, etc.). Kotlin coroutines are the right primitive: they are non-blocking, cheap to create per-session, and `fixedDelay`-based scheduling via Spring's coroutine support works cleanly with `suspend fun`.

**Important caveat (verified):** Spring Boot 3.2.x had a known bug where `@Scheduled` instrumentation does not work for Kotlin `suspend` functions ([Spring Issue #32165](https://github.com/spring-projects/spring-framework/issues/32165)). The workaround is to launch a `CoroutineScope` manually inside a regular `@Scheduled` method, or to use Spring Boot 3.3+ where this is resolved.

| Dependency                | Artifact                                           | Version                    |
| ------------------------- | -------------------------------------------------- | -------------------------- |
| Kotlin Coroutines         | `org.jetbrains.kotlinx:kotlinx-coroutines-core`    | 1.8.x (matches Kotlin 2.1) |
| Coroutines Reactor bridge | `org.jetbrains.kotlinx:kotlinx-coroutines-reactor` | 1.8.x                      |

**Confidence:** HIGH — first-party Kotlin/Spring documentation confirms coroutine support.

**Recommended pattern for per-session tick loops:**

```kotlin
// In GameSessionService — one coroutine scope per active session
class SessionTickEngine(
    private val sessionId: Long,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    fun start() {
        scope.launch {
            while (isActive) {
                processTick(sessionId)
                delay(GAME_TICK_INTERVAL_MS)   // e.g. 5_000L for 5-real-second ticks
            }
        }
    }
    fun stop() = scope.cancel()
}
```

This gives session isolation: each session's tick loop is independent and can be stopped on session end without affecting others.

**Do NOT use:** Spring's `@Scheduled(fixedDelay=...)` at the application level for per-session logic — it has a single global scheduler and cannot carry session context cleanly. Use `CoroutineScope` per session instead.

---

### 3. NPC AI (Finite State Machine)

#### Backend: KStateMachine

**What to add:** `io.github.nsk90:kstatemachine-coroutines`

**Why:** NPC officers (unselected original characters) need autonomous behavior driven by game state. KStateMachine is the most mature Kotlin-native FSM library with coroutine support, clean DSL, hierarchical state support, and Kotlin Multiplatform compatibility. It is zero-dependency (no Android SDK, no external frameworks), runs on JVM, and version 0.36.0 (January 2025) supports Kotlin 2.x.

It is far preferable to Spring State Machine (`org.springframework.statemachine`) which is Java-first, XML-heavy in configuration, and adds significant overhead for simple game AI behavior trees.

| Dependency                 | Artifact                                   | Version |
| -------------------------- | ------------------------------------------ | ------- |
| KStateMachine (coroutines) | `io.github.nsk90:kstatemachine-coroutines` | 0.36.0  |

**Confidence:** MEDIUM — version verified from GitHub releases ([KStateMachine releases](https://github.com/KStateMachine/kstatemachine/releases)). Suitability for game NPC AI is based on library capabilities verified from official docs + community usage.

**Do NOT use:** Spring State Machine — Java-first API, poor Kotlin ergonomics, heavyweight configuration for game AI use cases.

**Do NOT use:** Raw `when`/`sealed class` FSM for complex NPC behavior — works for 2-3 states but becomes unmaintainable at 10+ states with transition guards and entry/exit actions.

---

### 4. Distributed Session Locking (Turn Processing)

#### Backend: PostgreSQL Advisory Locks (no new dependency)

**Why:** When processing a game turn, only one game-app instance should process a given session's tick at a time. Redis SETNX-based locks are an option but add failure-mode complexity. PostgreSQL advisory locks (`pg_try_advisory_xact_lock`) are available via the existing JDBC connection, require zero new infrastructure, and are automatically released on transaction end — perfect for per-session turn serialization.

**No new dependency required** — use via `JdbcTemplate` (already on classpath via Spring Data JPA).

```kotlin
@Transactional
fun processSessionTurn(sessionId: Long) {
    val lockAcquired = jdbcTemplate.queryForObject(
        "SELECT pg_try_advisory_xact_lock(?)", Boolean::class.java, sessionId
    ) ?: false
    if (!lockAcquired) return   // another instance is processing this session
    // ... turn logic
}
```

**Confidence:** HIGH — PostgreSQL advisory lock pattern is well-documented and production-proven. Verified via [PostgreSQL docs](https://www.postgresql.org/docs/current/explicit-locking.html).

**Do NOT use:** `@Transactional` with JPA optimistic locking for turn serialization — retry storm under contention at scale.

---

### 5. Frontend: RTS Combat Rendering

#### Frontend: R3F useFrame for Game Loop (already present, pattern needed)

**What to add:** No new library — React Three Fiber's `useFrame` hook (already in the project) is the correct game loop primitive.

**Why:** `useFrame(callback, priority)` executes on every rendered frame (60fps), receives `delta` (seconds since last frame), and supports priority ordering for multi-pass rendering. This is the correct mechanism for interpolating fleet positions between server state snapshots.

**Pattern for fleet position interpolation:**

```typescript
// Receive server position snapshots via STOMP WebSocket -> Zustand store
// Interpolate visually in useFrame
useFrame((state, delta) => {
    // Lerp toward server-authoritative position
    meshRef.current.position.lerp(targetPosition, Math.min(delta * LERP_SPEED, 1));
});
```

**Critical constraint:** Never call React `setState` inside `useFrame`. Drive visual state through refs or Zustand's `getState()` directly (not through React re-renders). This is the standard pattern for R3F game loops.

**Confidence:** HIGH — verified against R3F official docs ([useFrame](https://r3f.docs.pmnd.rs/api/hooks#useframe))

---

### 6. Frontend: Game State Management

#### Frontend: Zustand Slices Pattern (already present, architecture needed)

**What to add:** No new library — Zustand 5.x (already in project) with the slices pattern.

**Why:** The game has deeply nested, domain-separated state: session, faction, officer/character, fleet, planet, combat, UI. Zustand's slice pattern allows one global store split by domain, with selective subscription to minimize re-renders. For a 2,000-player game with frequent state updates, avoiding unnecessary re-renders is critical.

**Recommended slice structure:**

```
useGameStore
  ├── sessionSlice     — session metadata, game time, victory conditions
  ├── officerSlice     — current player's officer, CP, rank, stats
  ├── factionSlice     — faction resources, org chart visibility
  ├── galaxySlice      — planet list, fleet positions (large, freq. updated)
  ├── combatSlice      — active battle state (RTS, high-frequency updates)
  └── uiSlice          — modal state, selected entity, panel layout
```

**Key rule:** `combatSlice` updates must NOT trigger re-renders in `galaxySlice` consumers. Use granular selectors:

```typescript
// Good — only re-renders when combat state changes
const fleetFormation = useCombatStore((state) => state.fleet.formation);

// Bad — re-renders on any store change
const store = useGameStore();
```

**Confidence:** HIGH — verified against Zustand official docs and current ecosystem consensus.

---

### 7. In-Game Communication (Mail, Messenger, Chat)

#### Backend: STOMP topic/queue routing (no new library)

**Why:** The three communication tiers (game mail, 1:1 messenger, spot chat) map cleanly to STOMP destinations:

- `/topic/chat.grid.{gridId}` — spot/grid chat (broadcast)
- `/user/queue/messenger` — 1:1 private messages (Spring's user destination)
- Game mail → REST API + PostgreSQL (not real-time, asynchronous delivery)

Spring's `SimpMessagingTemplate` + user destination support handles this with zero new dependencies.

**Do NOT use:** A separate chat service (Socket.IO, Pusher, etc.) — the existing STOMP infrastructure is sufficient and avoids a second WebSocket protocol stack.

---

### 8. Database: Game Data Patterns

#### Backend: Flyway (already present) + PostgreSQL JSONB for flexible game config

**What to add:** Use PostgreSQL JSONB columns for:

- Command definitions (70+ command types with varying parameters)
- Officer ability card definitions
- Ship class specifications (faction-specific variants)

**Why:** These are read-heavy, rarely-written configuration objects with variable schema. JSONB avoids 20+ lookup tables and Spring Data JPA handles it via `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6, already on classpath via Spring Boot 3).

**No new dependency required** — Hibernate 6 (via Spring Boot 3) supports JSONB natively.

```kotlin
@Column(columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
var commandParams: Map<String, Any> = emptyMap()
```

**Confidence:** HIGH — Hibernate 6 JSONB support is documented for Spring Boot 3.

---

## What NOT to Add

| Temptation                       | Why Not                                                                                                                      |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Socket.IO (Node.js)              | Already have Spring WebSocket + STOMP. Two WS stacks = double complexity.                                                    |
| Spring State Machine             | Java-first, heavyweight config, poor Kotlin DX. Use KStateMachine.                                                           |
| Separate game-state microservice | Already split gateway/game-app. Further splitting adds latency for turn processing.                                          |
| Redis SETNX distributed locks    | PostgreSQL advisory locks are simpler for this use case, zero new infra.                                                     |
| GraphQL subscriptions            | STOMP over WebSocket already handles real-time. GraphQL adds overhead without benefit here.                                  |
| XState (frontend)                | Overkill for UI state machines. Zustand slices + plain FSMs (enums + sealed classes) are sufficient.                         |
| Separate NPC AI service          | NPC logic runs inside game-app on the same session data. Cross-service calls for every NPC action adds unacceptable latency. |

---

## New Dependencies Summary

### Backend (add to game-app/build.gradle.kts)

```kotlin
dependencies {
    // External STOMP broker relay (RabbitMQ)
    implementation("io.projectreactor.netty:reactor-netty")
    implementation("io.netty:netty-all")

    // Kotlin coroutines (game tick engine)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")

    // NPC FSM
    implementation("io.github.nsk90:kstatemachine-coroutines:0.36.0")
}
```

### Backend (add to docker-compose.yml)

```yaml
rabbitmq:
    image: rabbitmq:3-management
    ports:
        - '5672:5672' # AMQP
        - '15672:15672' # Management UI
        - '61613:61613' # STOMP
    environment:
        RABBITMQ_DEFAULT_USER: guest
        RABBITMQ_DEFAULT_PASS: guest
```

### Frontend

No new npm packages required. All needed frontend capabilities are covered by existing stack:

- Zustand 5.x — game state slices
- @stomp/stompjs 7.x — WebSocket client
- SockJS 1.6.1 — WS fallback (maintenance mode but not deprecated; keep for now)
- React Three Fiber 9.x + useFrame — RTS rendering loop
- React Three Drei — 3D helpers (fleet models, labels, orbit controls)

---

## Confidence Assessment

| Area                          | Confidence | Basis                                                                                            |
| ----------------------------- | ---------- | ------------------------------------------------------------------------------------------------ |
| RabbitMQ STOMP relay          | HIGH       | Official Spring Framework docs verified                                                          |
| JWT WebSocket auth pattern    | HIGH       | Official Spring Security docs verified                                                           |
| Kotlin coroutines tick engine | HIGH       | Official Kotlin + Spring docs, known @Scheduled bug documented                                   |
| KStateMachine for NPC AI      | MEDIUM     | Version 0.36.0 verified from GitHub releases; game AI suitability inferred from library features |
| PostgreSQL advisory locks     | HIGH       | Official PG docs + Spring JDBC pattern well-established                                          |
| R3F useFrame game loop        | HIGH       | Official R3F docs verified                                                                       |
| Zustand slices architecture   | HIGH       | Official Zustand docs + 2025 community consensus                                                 |
| SockJS client status          | MEDIUM     | npm data shows maintenance mode (last release 1.6.1, low activity); not officially deprecated    |

---

## Sources

- [Spring Framework — External Broker Relay](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html)
- [Spring Framework — Token-Based WebSocket Auth](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
- [Spring Security — WebSocket Security](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html)
- [Spring Framework — SockJS Fallback](https://docs.spring.io/spring-framework/reference/web/websocket/fallback.html)
- [KStateMachine — GitHub Releases](https://github.com/KStateMachine/kstatemachine/releases)
- [KStateMachine — Overview](https://kstatemachine.github.io/kstatemachine/)
- [React Three Fiber — useFrame Hook](https://r3f.docs.pmnd.rs/api/hooks#useframe)
- [PostgreSQL — Explicit Locking (Advisory Locks)](https://www.postgresql.org/docs/current/explicit-locking.html)
- [Spring @Scheduled + Kotlin suspend issue #32165](https://github.com/spring-projects/spring-framework/issues/32165)
- [Baeldung — Non-Blocking Spring Boot with Kotlin Coroutines](https://www.baeldung.com/kotlin/spring-boot-kotlin-coroutines)
- [Baeldung — Redis Pub/Sub](https://www.baeldung.com/spring-data-redis-pub-sub)
- [Spring WebSocket + Redis Pub/Sub (multi-instance)](https://github.com/RawSanj/spring-redis-websocket)
- [Scaling WebSocket Messaging with Spring Boot](https://medium.com/@bytewise010/scaling-websocket-messaging-with-spring-boot-e9877c80f027)
