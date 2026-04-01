# Coding Conventions

**Analysis Date:** 2026-03-31

## Naming Patterns

**Files:**

- TypeScript/React: camelCase with `.ts`, `.tsx`, `.test.ts` extensions
    - Pages: `page.tsx` (Next.js convention)
    - Components: `app-sidebar.tsx`, `mobile-menu-sheet.tsx`, `command-panel.tsx`
    - Stores: `gameStore.ts`, `authStore.ts`, `worldStore.ts`
    - Libraries: `api.ts`, `api-error.ts`, `game-utils.ts`, `josa.ts`
    - Tests: `lobby.test.ts`, `game-utils.test.ts`, `app-sidebar.test.tsx`
- Kotlin: PascalCase for classes/entities, camelCase for files
    - Entities: `Officer.kt`, `Planet.kt`, `Faction.kt`, `SessionState.kt`
    - Services: `OfficerService.kt`, `FactionJoinService.kt`, `GameConstService.kt`
    - Controllers: `OfficerController.kt`, `CommandController.kt`, `FrontInfoController.kt`
    - Tests: `OfficerServiceTest.kt`, `BattleEngineTest.kt`, `GoldenSnapshotTest.kt`
- CSS: Tailwind utility classes, no standalone CSS files

**Functions:**

- Frontend (TypeScript): camelCase for regular functions, PascalCase for React components
    - Regular: `fetchWorlds()`, `handleSelectWorld()`, `getServerPhase()`
    - Components: `AppSidebar()`, `TopBar()`, `MobileMenuSheet()`
    - Hooks: `useMobile()` (custom hooks with `use` prefix)
    - Utility exports: `pick()`, `put()` in `josa.ts`; `applyApiErrorMessage()` in `api-error.ts`
- Backend (Kotlin): camelCase for methods, PascalCase for classes
    - Service methods: `fun createOfficer(sessionId, loginId, request)`
    - Repository queries: `findBySessionIdAndUserId()`, `findByNameAndWorldId()`
    - Korean names for game commands: `class 휴식(general, env)`, `class 훈련(general, env)`

**Variables:**

- Frontend: camelCase, nullable types indicated with `?`
    - Store state: `starSystems: StarSystem[]`, `factions: Faction[]`, `loading: boolean`
    - Module-level: `let _inflightLoadAll: { worldId: number; promise: Promise<void> } | null = null`
    - React hooks: `const [notice, setNotice] = useState('')`
- Backend (Kotlin): camelCase for properties, UPPER_SNAKE_CASE for file-level constants
    - Entity fields: `var factionId: Long = 0`, `var sessionId: Long = 0`
    - File-level: `private const val COMPAT_UNSET_LONG: Long = Long.MIN_VALUE`
    - Entity `@Column` annotations explicitly name the database column

**Types:**

- Frontend: `interface` for object shapes, `type` for aliases and unions
    - Store interfaces: `interface GameStore { ... }` in `gameStore.ts`
    - Domain types in barrel file: `frontend/src/types/index.ts` (aggregates all types)
    - API payload types: `interface ApiErrorPayload` in `api-error.ts`
    - Tactical types: `frontend/src/types/tactical.ts`
- Backend (Kotlin): JPA `@Entity` classes for persistence, `data class` for DTOs
    - Entities: `class Officer(...)` with `@Entity`, `@Table`, `@Column` annotations
    - DTOs: `data class CreateOfficerRequest(...)` in `dto/` package
    - Stubs for testing: `data class CreateGeneralRequest(...)` in test `dto/DtoStubs.kt`

**Legacy Compatibility Naming:**

- Entity type aliases in `backend/game-app/src/main/kotlin/com/openlogh/entity/TypeAliases.kt`:
    ```kotlin
    typealias General = Officer
    typealias City = Planet
    typealias Nation = Faction
    typealias WorldState = SessionState
    typealias GeneralTurn = OfficerTurn
    typealias NationTurn = FactionTurn
    ```
- Frontend deprecated aliases marked with `/** @deprecated */` JSDoc comments
    - `frontend/src/types/index.ts`: `/** @deprecated use funds */ gold: number;`
    - `frontend/src/stores/gameStore.ts`: `/** @deprecated use starSystems */ cities: StarSystem[];`
- Korean characters used for game commands, action codes, and UI text
    - Backend: `actionCode = "휴식"`, `actionCode = "훈련"`, `actionCode = "농지개간"`
    - Frontend: Korean labels in UI constants and error messages
- When adding new entity fields, always provide the LOGH name as primary; add OpenSamguk alias if backward compat needed

## Code Style

**Formatting:**

- Frontend: ESLint with Next.js core-web-vitals and TypeScript presets
    - Config: `frontend/eslint.config.mjs` (flat config format)
    - TypeScript strict mode: `"strict": true` in `frontend/tsconfig.json`
    - Target: ES2017, module: esnext, moduleResolution: bundler
    - Run: `pnpm lint` (also runs in CI)
- Backend: Kotlin style conventions (Spring Boot standard)
    - JVM target: 17 (configured in `backend/build.gradle.kts`)
    - Compiler flag: `-Xjsr305=strict` for strict JSR-305 null-safety
    - Indentation: 4 spaces
    - No explicit Kotlin linter (detekt/ktlint) configured
    - Trailing commas used in parameter lists (Kotlin convention)

**Linting:**

- Frontend: ESLint 9 with flat config (`eslint.config.mjs`)
    - Extends: `eslint-config-next/core-web-vitals`, `eslint-config-next/typescript`
    - Ignores: `.next/`, `out/`, `build/`, `next-env.d.ts`
    - Run: `pnpm lint` (CI runs this on every PR)
    - Type checking: `pnpm typecheck` (separate `tsc --noEmit` step)
- Backend: No explicit linter config (relies on Kotlin compiler and IDE conventions)

## Import Organization

**Order (Frontend):**

1. External packages: `import { create } from 'zustand'`
2. Type-only imports: `import type { StarSystem, Faction } from '@/types'`
3. Internal libraries/APIs: `import { planetApi, factionApi } from '@/lib/gameApi'`
4. Internal components: `import { Button } from '@/components/ui/button'`
5. Relative imports (rare): only for co-located files

**Order (Backend - Kotlin):**

1. Package declaration: `package com.openlogh.service`
2. Java/Jakarta imports: `import jakarta.persistence.*`
3. Hibernate imports: `import org.hibernate.annotations.JdbcTypeCode`
4. Spring imports: `import org.springframework.stereotype.Service`
5. Project imports: `import com.openlogh.entity.Officer`
6. Java stdlib: `import java.time.OffsetDateTime`

**Path Aliases:**

- Frontend: `@/*` maps to `./src/*` (defined in `frontend/tsconfig.json` and `frontend/vitest.config.ts`)
    - Use `@/types`, `@/components`, `@/lib`, `@/stores`, `@/hooks`
    - Always use path aliases for cross-directory imports, never relative paths like `../../`
    - Only use relative imports for files in the same directory

## Error Handling

**Backend Patterns:**

- Service layer throws exceptions for business logic violations:
    - `IllegalArgumentException` for invalid input / not-found resources
    - `IllegalStateException` for invalid state transitions
    - Custom exceptions: `OtpRequiredException`, `OtpValidationException`
    - Error messages in Korean for user-facing errors: `"계정 정보를 찾을 수 없습니다. 다시 로그인해주세요."`
- Controller layer maps exceptions to HTTP responses:
    - Not found: `ResponseEntity.notFound().build<Void>()`
    - Forbidden: `ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()`
    - Unauthorized: `ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()`
    - Service exceptions caught by `@ControllerAdvice` and mapped to error payloads
- Command system uses `CommandResult` (success flag + logs) for non-exception failures:
    - `ConstraintResult.Pass` / `ConstraintResult.Fail` for pre-validation
    - Commands return `result.success` boolean, not exceptions

**Frontend Patterns:**

- API interceptor in `frontend/src/lib/api.ts` handles global errors:
    - 401 responses: clears token, redirects to `/login` (skips auth endpoints)
    - 400 responses: `applyApiErrorMessage()` extracts backend error message
- Async operations use try/catch/finally:
    ```typescript
    set({ loading: true });
    try { /* async work */ } finally { set({ loading: false }); }
    ```
- Minimal `console.error()` calls (only 4 occurrences in entire frontend)
- `Promise.allSettled()` pattern for parallel data loading with partial failure tolerance (see `gameStore.ts`)
- Timeout wrapper for API calls: `withTimeout(promise, ms)` pattern in stores

**Validation:**

- Backend: Service-level validation throws on failure; JPA `@Column(nullable=false)` for DB-level
- Frontend: Zod schemas (`zod` v4) + React Hook Form (`react-hook-form`) + `@hookform/resolvers`

## Logging

**Framework:** console (frontend), SLF4J (backend)

**Patterns:**

- Frontend: `console.error()` only, used sparingly (4 total occurrences)
    - `console.error('Image upload failed:', e)` in `tiptap-editor.tsx`
    - `console.error('Failed to reserve command:', error)` in `processing/page.tsx`
    - No `console.log()` or `console.warn()` in production code
    - No structured logging framework
- Backend: SLF4J via Spring Boot defaults
    - Game-app logs written to `logs/game-{commitSha}.log` by `GameProcessOrchestrator`
    - No explicit logger declarations found in service code (implicit via Spring)

## Comments

**When to Comment:**

- Korean particle logic: Detailed algorithm explanation in `frontend/src/lib/josa.ts`
- Legacy parity: "Legacy parity: ..." prefix or "Legacy:" comments indicate OpenSamguk compatibility
- Non-obvious game mechanics: Comments explain formulas, game rules, state transitions
    - Example in test: `// Legacy: FOOTMAN.attack=100, techAbil=0 -> (100+0)*130/100 = 130.0`
- TODO markers for unimplemented features: `// TODO: implement diplomacy response logic`
    - ~15+ TODO comments in controllers (`DiplomacyController.kt`, `AuctionController.kt`, `InheritanceController.kt`, etc.)
- Suppress annotations: `@file:Suppress("unused")` on type alias and stub files

**JSDoc/TSDoc:**

- Frontend: `/** @deprecated use newFieldName */` on backward-compat fields
- Frontend: JSDoc `@example` blocks on complex utility functions (see `josa.ts`)
- Backend: KDoc on public service/utility classes
    - `/** 전술 전투 테스트용 팩토리/헬퍼. */` on `TacticalTestFixtures`
    - `/** 분류: military(군인) / politician(정치가) */` on entity fields

## Function Design

**Size:**

- Frontend utility functions: < 50 lines (`applyApiErrorMessage()` ~15 lines, `pick()` ~12 lines)
- Frontend store methods: < 30 lines per action (`loadAll` ~25 lines)
- Backend service methods: < 100 lines, with early returns for validation
- Backend controller endpoints: < 20 lines (delegate to service)

**Parameters:**

- Frontend: Object destructuring for component props; flat params for utility functions
- Backend: Constructor injection for all dependencies (no field injection)
    - Services receive repositories and other services via constructor
    - Example: `class OfficerService(officerRepository, appUserRepository, sessionStateRepository, ...)`
    - Method params: typically 2-4 (sessionId, loginId, request DTO)

**Return Values:**

- Frontend: TypeScript enforces explicit typing via `strict: true`
- Backend services: Return nullable `Officer?` for "not found" cases; throw for errors
- Backend controllers: Return `ResponseEntity<T>` consistently
    - Success: `ResponseEntity.ok(data)`
    - Error: `ResponseEntity.status(code).build<Void>()`

**Async Handling:**

- Frontend: async/await in stores and event handlers
- Backend: Kotlin coroutines for commands (`runBlocking` in tests)
    - Command execution: `suspend fun run(rng: Random): CommandResult`
    - Tests use: `val result = runBlocking { cmd.run(fixedRng) }`

## Module Design

**Exports:**

- Frontend: Named exports for utilities, stores, components, types
    - Stores: `export const useGameStore = create<GameStore>()(persist(...))`
    - Utilities: `export function pick(text, josa)`, `export function applyApiErrorMessage(error)`
    - Default exports: Only for Next.js page components (`export default function Page()`)
- Backend: Public classes with `@Service`, `@RestController`, `@Entity` annotations
    - Package-private helper methods inside service classes

**Barrel Files:**

- Frontend: Single barrel file at `frontend/src/types/index.ts` aggregating all domain types
    - No barrel files in `components/`, `stores/`, or `lib/`
    - Import components directly: `@/components/ui/button`
    - Import stores directly: `@/stores/gameStore`
- Backend: Not used (Kotlin package structure serves this purpose)

**File Organization:**

- Frontend: Tests co-located with source files
    - Page tests: `app/(game)/page.test.ts` next to `app/(game)/page.tsx`
    - Component tests: `components/app-sidebar.test.tsx` next to `components/app-sidebar.tsx`
    - Lib tests: `lib/game-utils.test.ts` next to `lib/game-utils.ts`
- Backend: Separate `src/main/` and `src/test/` trees (standard Maven/Gradle layout)
    - Test packages mirror source packages: `com.openlogh.service` -> test `com.openlogh.service`

## API Design Conventions

**REST Endpoints:**

- All endpoints under `/api/` prefix
- Resource-centric: `/api/worlds/{worldId}/officers`, `/api/worlds/{worldId}/officers/me`
- Controllers use `@RestController` + `@RequestMapping("/api")` or `/api/{resource}`
- Authentication via `SecurityContextHolder.getContext().authentication` (Spring Security)
- Standard HTTP methods: `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`

**WebSocket Channels:**

- Command channel: `/app/command/{sessionId}/execute`
- Event broadcast: `/topic/world/{sessionId}/events`
- Battle updates: `/topic/world/{sessionId}/battle`
- Protocol: STOMP over SockJS

**Frontend API Client:**

- Axios instance at `frontend/src/lib/api.ts` with global interceptors
- Game-specific API functions in `frontend/src/lib/gameApi.ts`
- Token management: `localStorage.getItem('token')` added to `Authorization: Bearer` header

---

_Convention analysis: 2026-03-31_
