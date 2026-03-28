# Coding Conventions

**Analysis Date:** 2026-03-28

## Naming Patterns

**Files:**

- TypeScript/React: camelCase with `.ts`, `.tsx`, `.test.ts` extensions
    - Example: `worldStore.ts`, `stat-bar.tsx`, `lobby.test.ts`
- Kotlin: PascalCase for classes/entities, camelCase for functions/files
    - Example: `AppUser.kt`, `SecurityConfig.kt`, `JosaUtil.kt`
- CSS: kebab-case for class names, applied via Tailwind or direct className
    - Example: `game-font`, `text-muted-foreground`

**Functions:**

- Frontend (TypeScript): camelCase for regular functions, PascalCase for React components
    - Regular: `fetchWorlds()`, `handleSelectWorld()`, `getServerPhase()`
    - Components: `StatBar()`, `LobbyPage()`, `ServerStatusCard()`
    - Helper functions inside components use camelCase: `getPlayerInfo()`, `getActionAvailability()`
- Backend (Kotlin): camelCase for methods, PascalCase for classes
    - Example: `fun pick(text: String, josa: String)`, `class AppUser`

**Variables:**

- Frontend: camelCase, nullable types indicated with `?`
    - Example: `currentWorld`, `myGeneral`, `wsConnectedRef`
    - State properties: `loading: boolean`, `worlds: WorldState[]`
    - React hooks: `const [notice, setNotice] = useState('')`
- Backend (Kotlin): camelCase for properties, UPPER_SNAKE_CASE for constants
    - Example: `var loginId: String`, `private val JOSA_MAP = mapOf(...)`
    - Entity properties match database column names with @Column annotation

**Types:**

- Frontend: Interface prefix with `Interface` in JSDoc or explicit names
    - Example: `interface WorldStore`, `interface StatBarProps`, `type User`
    - Component props: `interface [ComponentName]Props`
    - Use `type` for aliases, `interface` for object shapes
- Backend (Kotlin): Data classes for DTOs, regular classes for entities
    - Example: `data class ChangePasswordRequest`, `class AppUser`
    - JSON serialization uses jackson annotations
    - @JdbcTypeCode for complex JSON fields

**Naming for Legacy Compatibility:**

- Comments indicate deprecated aliases (backward compat with OpenSamguk)
    - Example: `/** @deprecated use capitalStarSystemId */`
    - Dual field names supported: `capitalCityId` and `capitalStarSystemId`
- Korean characters used in game-specific code (commands, UI text)
    - Example: `command 휴식`, `object JosaUtil`, Korean function names in business logic

## Code Style

**Formatting:**

- Frontend: ESLint + Next.js core-web-vitals rules enforce style
    - Strict TypeScript: `strict: true` in tsconfig.json
    - Module resolution: `bundler` for Next.js compatibility
    - Target: ES2017 with ESNext modules
- Backend: Kotlin style conventions via Spring Boot
    - Indentation: 4 spaces (Kotlin standard)
    - Properties: var/val with type inference where clear
    - No explicit semicolons required (Kotlin style)

**Linting:**

- Frontend: `eslint` with next/core-web-vitals
    - Config: `/Users/apple/Desktop/openlogh/frontend/eslint.config.mjs`
    - Run: `pnpm lint`
- Backend: No explicit linter config found (Spring Boot conventions)
    - Code follows Kotlin standard library patterns

## Import Organization

**Order (Frontend):**

1. External packages: `import { create } from 'zustand'`
2. Internal types: `import type { Officer } from '@/types'`
3. Internal utilities/hooks: `import { officerApi } from '@/lib/gameApi'`
4. Components: `import { Button } from '@/components/ui/button'`
5. Relative imports: `import { useWorldStore } from '@/stores/worldStore'`

**Order (Backend - Kotlin):**

1. Package declaration
2. Java/Jakarta imports
3. Spring imports
4. Project imports
5. Local imports

**Path Aliases:**

- Frontend: `@/*` → `./src/*` defined in `tsconfig.json`
    - Use `@/types`, `@/components`, `@/lib`, `@/stores`, `@/hooks`
    - Do NOT use relative paths for cross-directory imports

## Error Handling

**Patterns:**

- Frontend: try/catch in async functions, console.error for logging
    - Example: `const { data } = await worldApi.list()` with implicit error propagation
    - Async operations wrapped in try/finally for state cleanup
    - Example: `try { fetchWorlds() } finally { set({ loading: false }) }`
    - Error handling in API calls: `.catch(() => {})` for optional operations
- Backend (Kotlin): Custom exceptions, validation exceptions, service-level error handling
    - Example: `assertThrows(IllegalArgumentException::class.java)` in tests
    - Example: `throw IllegalStateException("User requested deletion")`
    - Service methods throw on validation failure, controller handles HTTP mapping

**Custom Exceptions:**

- Backend: `IllegalArgumentException`, `IllegalStateException` for business logic violations
    - Example: `OtpRequiredException` with `otpTicket` property
    - Example: `SystemSettingsService.AuthFlags` for conditional auth state

**Validation:**

- Frontend: Inline validation in component logic
    - Example: `citySelectable = nationId === 0` (check before enabling)
- Backend: Service-level validation, throws on failure
    - Example: `requireTermsAgreements()` throws IllegalArgumentException if not agreed

## Logging

**Framework:** console (frontend), implicit in Spring (backend)

**Patterns:**

- Frontend: `console.error()` for error logging in catch blocks
    - Example: `console.error(loginData.reason)`, `console.error('Failed to reserve command:', error)`
    - Minimal logging, primarily error cases
    - No structured logging framework detected
- Backend: Spring Boot default logger via SLF4J (implicit, not shown in code)
    - Tests verify behavior rather than log messages

## Comments

**When to Comment:**

- Complex Korean particle logic (JosaUtil): Detailed explanation of algorithm
- Legacy parity checks: "Legacy parity: ..." prefix indicates OpenSamguk compatibility code
- Non-obvious game logic: Comments explain game rules and state transitions
    - Example: "폐쇄/가오픈/오픈/통일/정지/종료" server phase comments
- Data flow for complex operations: Describe why not just what

**JSDoc/TSDoc:**

- Frontend: Minimal use, type signatures sufficient due to TypeScript
    - Used in utility functions with complex logic
- Backend (Kotlin): KDoc style (Kotlin documentation)
    - Example: `/** Pick the correct 조사 for the given text. */`
    - Document public methods and classes
    - Include @param and @return for non-obvious parameters

**Example Comment Style:**

```kotlin
/**
 * Korean 조사 (particle) utility - picks the correct particle variant
 * based on whether the preceding Korean character has a final consonant (받침).
 *
 * Ported from legacy PHP JosaUtil and core2026 TS JosaUtil.
 */
```

## Function Design

**Size:**

- Frontend: Utility functions < 50 lines (e.g., `getServerPhase()`, `getPlayerInfo()`)
- Component rendering: Extract helper functions when logic exceeds 20 lines
- Backend: Service methods < 100 lines, business logic extracted to utilities

**Parameters:**

- Frontend: Use object destructuring for > 2 parameters
    - Example: `function StatBar({ label, value, max = 100, color })`
- Backend: Constructor injection for dependencies, method params < 4
    - Example: `class AuthService(appUserRepository, passwordEncoder, jwtUtil, ...)`

**Return Values:**

- Frontend: Explicit return types on functions
    - Example: `getServerPhase(w: WorldState): { label: string; color: string; icon: typeof Shield }`
- Backend: Non-nullable returns preferred, Optional<T> for nullable
    - Example: `fun pick(text: String, josa: String): String`

**Async Handling:**

- Frontend: async/await in stores and components
    - Example: `fetchWorlds: async () => { set({ loading: true }); try { ... } finally { set({ loading: false }) } }`
- Backend: Kotlin coroutines with `runBlocking` in tests
    - Example: `runBlocking { 휴식(gen, env).run(Random(42)) }`

## Module Design

**Exports:**

- Frontend: Named exports for utilities and types
    - Example: `export const useWorldStore = create<WorldStore>()(persist(...))`
    - Example: `export function StatBar({ ... })`
    - Default exports for page components only
- Backend: Public constructors and methods, package-private for internals
    - Example: `class AuthService(...)` public, helper methods package-private

**Barrel Files:**

- Frontend: `types/index.ts` aggregates all type exports
    - No barrel files in components/ (import directly: `@/components/ui/button`)
    - No barrel files in stores/ (import by store name)
- Backend: Not applicable (package structure used)

**File Organization:**

- Frontend co-located: Tests alongside source
    - Example: `join/page.tsx` + `join/join.test.ts` in same directory
- Backend: Standard Spring structure
    - `/src/main/kotlin/com/openlogh/` → implementation
    - `/src/test/kotlin/com/openlogh/` → tests

---

_Convention analysis: 2026-03-28_
