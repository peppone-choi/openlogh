# Coding Conventions

**Analysis Date:** 2026-04-05

## Naming Patterns

**Files:**
- Kotlin entities: PascalCase matching class name (`General.kt`, `City.kt`, `Nation.kt`)
- Kotlin services: PascalCase with `Service` suffix (`CommandService.kt`, `CityService.kt`)
- Kotlin commands: Korean action name prefixed with map code (`che_훈련.kt`, `che_징병.kt`)
- TypeScript pages: `page.tsx` (Next.js App Router convention)
- TypeScript tests: co-located with kebab-case descriptive name (`commands.test.ts`, `layout.test.ts`, `city.test.ts`)
- TypeScript components: kebab-case filenames (`page-header.tsx`, `command-panel.tsx`, `loading-state.tsx`)
- UI components: kebab-case in `components/ui/` (`button.tsx`, `card.tsx`, `tabs.tsx`)
- 8-bit themed UI: under `components/ui/8bit/` subdirectory with same kebab-case naming

**Functions:**
- Kotlin: camelCase for all methods (`reserveGeneralTurns`, `executeCommand`, `calcDevelopment`)
- TypeScript: camelCase for regular functions, PascalCase for React components (`Button`, `CommandPanel`)
- Private Kotlin helpers: camelCase prefixed descriptively (`buildFullGeneralTurnQueue`, `defaultGeneralTurn`)

**Variables:**
- Kotlin: camelCase for properties, `UPPER_SNAKE_CASE` for companion object constants
- TypeScript: camelCase for variables, UPPER_SNAKE_CASE for module-level constants
- Example constants: `REGION_HABUK = 1`, `DEFAULT_CITY_WALL = 1000`, `EXPAND_CITY_DEFAULT_COST = 60000`

**Types/Classes:**
- Kotlin entities: PascalCase, mutable `var` properties, JPA annotations (`General`, `City`, `Nation`)
- Kotlin DTOs: PascalCase data classes with `Request`/`Response` suffix (`LoginRequest`, `AuthResponse`, `CreateWorldRequest`)
- TypeScript interfaces: PascalCase, exported from `@/types/index.ts` (`WorldState`, `Nation`, `User`, `CommandTableEntry`)
- No `I` prefix on interfaces

**Korean in Code:**
- Command action codes use Korean names: `"휴식"`, `"농지개간"`, `"징병"`, `"출병"`
- Command class names use Korean: `che_훈련`, `che_징병`, `che_성벽보수`
- Category names in Korean: `"개인"`, `"내정"`, `"군사"`, `"인사"`, `"계략"`
- Error messages in Korean: `"실시간 모드에서는 예턴 예약을 사용할 수 없습니다."`
- UI labels in Korean: `"지도"`, `"명령"`, `"상태"`, `"동향"`
- Region/level names in Korean maps: `REGION_NAMES`, `LEVEL_NAMES`

## Code Style

**Formatting:**
- Frontend: ESLint with `next/core-web-vitals` rules
- Backend: Kotlin conventions via Spring Boot (no explicit formatter config)
- Indentation: 4 spaces (Kotlin), 4 spaces (TypeScript)

**Linting:**
- Frontend: `eslint` — run via `pnpm lint`
- TypeScript strict mode enabled in `frontend/tsconfig.json`
- Backend: No explicit linter; relies on Kotlin compiler and Spring conventions

## Import Organization

**Kotlin (Backend):**
1. `com.openlogh.*` (project packages — command, entity, dto, repository, engine, service)
2. `jakarta.*` (JPA/persistence)
3. `kotlinx.*` (coroutines)
4. `org.springframework.*` (framework)
5. `org.junit.*`, `org.mockito.*` (test-only)
6. Java stdlib (`java.time.*`, `kotlin.math.*`)
- Wildcard imports used for repositories: `import com.openlogh.repository.*`
- Wildcard imports used for JUnit assertions: `import org.junit.jupiter.api.Assertions.*`

**TypeScript (Frontend):**
1. React/Next.js framework imports (`react`, `next/navigation`)
2. Icon libraries (`lucide-react`)
3. Internal components via `@/components/*` alias
4. Stores via `@/stores/*` alias
5. API/lib utilities via `@/lib/*` alias
6. Hooks via `@/hooks/*` alias
7. Types via `@/types` (use `import type` for type-only imports)

**Path Aliases:**
- `@/*` maps to `./src/*` (defined in `frontend/tsconfig.json`)

## Error Handling

**Backend Patterns:**

Use `IllegalArgumentException` for invalid input and `IllegalStateException` for business rule violations:
```kotlin
// Invalid entity reference
val general = generalRepository.findById(generalId).orElseThrow {
    IllegalArgumentException("General not found: $generalId")
}

// Business rule violation
if (world.realtimeMode) {
    throw IllegalStateException("실시간 모드에서는 예턴 예약을 사용할 수 없습니다.")
}
```

Return `null` for "not found" in non-critical paths (soft failure):
```kotlin
fun executeCommand(generalId: Long, ...): CommandResult? {
    val general = generalRepository.findById(generalId).orElse(null) ?: return null
    ...
}
```

Use `CommandResult` for command execution outcomes:
```kotlin
data class CommandResult(val success: Boolean, val logs: List<String>)
// Usage:
return CommandResult(success = false, logs = listOf("국가 명령 권한이 없습니다."))
```

Global exception handler in `backend/game-app/src/main/kotlin/com/openlogh/config/GlobalExceptionHandler.kt`:
- `IllegalArgumentException` -> 400 Bad Request with `{"error": "message"}`
- `MethodArgumentNotValidException` -> 400 Bad Request with `{"errors": {"field": "message"}}`

**Frontend Patterns:**

Minimal error handling — `console.error` in catch blocks:
```typescript
console.error('Failed to reserve command:', error);
```

No centralized error boundary or toast-based error display pattern. Errors are logged to console.

## Logging

**Backend:**
- SLF4J via Kotlin `LoggerFactory.getLogger()` (Spring Boot default)
- Game-app process logs written to `logs/game-{commitSha}.log` by `GameProcessOrchestrator`
- No structured logging framework

**Frontend:**
- `console.error()` for error logging (sparingly — only 3 occurrences in `frontend/src/`)
- No centralized logging service or aggregation

## Comments

**When to Comment (Backend):**
- Legacy parity references: `"Legacy parity: General::checkStatChange() in legacy/hwe/sammo/General.php"`
- Legacy formula documentation: `"Legacy: GameConst::$upgradeLimit"`
- KDoc on public service methods explaining purpose and formula
- Section dividers using `// ── Section Name ──` pattern

**When to Comment (Frontend):**
- Minimal comments — TypeScript types serve as documentation
- Complex logic gets inline comments explaining game rules

**Legacy Reference Pattern:**
Use `Legacy:` or `Legacy parity:` prefix to trace back to original PHP code:
```kotlin
/** Legacy parity: General::checkStatChange() in legacy/hwe/sammo/General.php */
/** Legacy: GeneralTrigger/che_부상경감.php */
/** Legacy: TurnExecutionHelper::preprocessCommand() */
```

**Section Dividers (Kotlin):**
```kotlin
// ── Basic CRUD ──
// ── Adjacency / Map Queries ──
// ── Supply Calculation ──
// ── Development Calculation (legacy parity) ──
```

## Function Design

**Backend Service Methods:**
- Constructor injection for all dependencies (Spring `@Service` classes)
- `@Transactional` on methods that write data
- Return nullable types (`CommandResult?`, `City?`) for "not found" scenarios
- Keep methods under 100 lines; extract private helpers for complex logic
- Use `companion object` for constants related to the service domain

**Backend Service Pattern:**
```kotlin
@Service
class CityService(
    private val cityRepository: CityRepository,    // constructor injection
    private val mapService: MapService,
    private val generalRepository: GeneralRepository,
) {
    companion object {
        const val REGION_HABUK = 1
        const val DEFAULT_CITY_WALL = 1000
    }

    // Public method with KDoc
    /** Calculate development effectiveness for a city based on general stats. */
    fun calcDevelopment(city: City, statValue: Int, baseAmount: Int): Int { ... }

    // Private helper
    private fun isCityVisibleToGeneral(...): Boolean { ... }
}
```

**Frontend Functions:**
- Utility functions under 50 lines
- Use object destructuring for component props
- Async/await in stores and API calls
- `useDebouncedCallback` hook for WebSocket event handlers

## DTO Patterns

**Backend DTOs (Kotlin data classes):**
- Located in `backend/shared/src/main/kotlin/com/openlogh/shared/dto/` for cross-module DTOs
- Located in `backend/game-app/src/main/kotlin/com/openlogh/dto/` for game-specific DTOs
- Inline DTOs in controller files for one-off request/response types
- Naming: `{Action}{Request|Response}` (`LoginRequest`, `AuthResponse`, `CreateWorldRequest`)
- Default values for optional fields: `data class BuildPoolGeneralRequest(val name: String, val leadership: Short = 70)`

**Frontend Types:**
- Centralized in `frontend/src/types/index.ts`
- Use `interface` for object shapes, `type` for unions/aliases
- Export all types from barrel file

## Entity Patterns

**JPA Entities (Kotlin):**
- Mutable `var` properties (not `val`) for JPA compatibility
- `@Entity` + `@Table(name = "...")` annotations
- `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `@Column(name = "snake_case")` mapping to database columns
- JSON columns use `@JdbcTypeCode(SqlTypes.JSON)` with `MutableMap<String, Any>` type
- Entity classes are NOT data classes (no `copy()` method) — manual copy helpers in tests
- Default values for all properties to support JPA no-arg constructor

**Entity Example:**
```kotlin
@Entity
@Table(name = "general")
class General(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "world_id", nullable = false)
    var worldId: Long = 0,
    @Column(nullable = false)
    var name: String = "",
    @Column(nullable = false)
    var leadership: Short = 50,
    // ...
)
```

## Module Design

**Backend:**
- Named exports via `package` (Kotlin convention)
- Public constructors and methods; `private` for internal helpers
- Package structure: `com.openlogh.{entity|service|command|engine|repository|dto|config|controller}`

**Frontend:**
- Named exports for utilities and types
- Default exports NOT used (Next.js page components are the exception)
- Barrel file at `frontend/src/types/index.ts` aggregates all type exports
- Stores export named hooks: `useWorldStore`, `useGeneralStore`

## Component Design (Frontend)

**UI Components:**
- Two tiers: base Shadcn UI (`components/ui/`) and 8-bit themed wrappers (`components/ui/8bit/`)
- Use `class-variance-authority` (CVA) for variant definitions
- Use `cn()` utility (clsx + tailwind-merge) for conditional class composition
- Props extend native HTML element attributes plus CVA variant props
- Ref forwarding via `ref?: React.Ref<HTMLElement>`

**Game Components:**
- Located in `frontend/src/components/game/`
- Use Zustand stores for state (`useWorldStore`, `useGeneralStore`)
- WebSocket subscriptions via `subscribeWebSocket` from `@/lib/websocket`
- Debounced callbacks for real-time event handling

---

*Convention analysis: 2026-04-05*
