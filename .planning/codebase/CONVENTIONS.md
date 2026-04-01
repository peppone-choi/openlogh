# Coding Conventions

**Analysis Date:** 2026-03-31

## Naming Patterns

**Files (Backend - Kotlin):**
- Entity classes: PascalCase singular nouns - `General.kt`, `City.kt`, `Nation.kt`, `WorldState.kt`
- Repositories: `{Entity}Repository.kt` - `GeneralRepository.kt`, `CityRepository.kt`
- Services: `{Domain}Service.kt` - `GeneralService.kt`, `EconomyService.kt`, `MapService.kt`
- Controllers: `{Domain}Controller.kt` - `GeneralController.kt`, `AdminController.kt`
- DTOs: grouped by domain in `{Domain}Dtos.kt` files - `GeneralDtos.kt`, `AuthDtos.kt`, `CommandDtos.kt`
- Tests: `{ClassUnderTest}Test.kt` - `GeneralServiceTest.kt`, `CommandTest.kt`
- Game commands (Korean): `che_{한글명}.kt` - follows legacy PHP naming from `hwe/sammo/Command/General/`

**Files (Frontend - TypeScript):**
- Components: kebab-case `.tsx` - `city-basic-card.tsx`, `command-panel.tsx`, `game-dashboard.tsx`
- Hooks: camelCase with `use` prefix - `useWebSocket.ts`, `useDebouncedCallback.ts`, `use-mobile.ts` (mixed convention)
- Stores: camelCase with `Store` suffix - `gameStore.ts`, `authStore.ts`, `worldStore.ts`
- Lib utilities: kebab-case - `api-error.ts`, `game-utils.ts`, `map-constants.ts`
- Tests: co-located with source, `.test.ts` / `.test.tsx` suffix - `gameStore.test.ts`, `city-basic-card.test.ts`
- Pages: Next.js App Router convention - `page.tsx` in route directories

**Functions (Backend):**
- Use camelCase: `listByWorld()`, `getMyGeneral()`, `createGeneral()`
- Repository methods follow Spring Data naming: `findByWorldId()`, `findByWorldIdAndUserId()`
- Service methods use domain verbs: `possessNpc()`, `buildPoolGeneral()`, `selectFromPool()`

**Functions (Frontend):**
- React components: PascalCase exported functions - `export function CityBasicCard()`
- Hooks: camelCase with `use` prefix - `useWebSocket()`, `useGameStore()`
- Utility functions: camelCase - `applyApiErrorMessage()`, `extractAuthErrorMessage()`
- Store actions: camelCase verbs - `loadAll()`, `loadMap()`, `clear()`

**Variables:**
- Backend: camelCase for local variables, UPPER_SNAKE_CASE for constants
- Frontend: camelCase for variables, UPPER_SNAKE_CASE for module-level constants (`LOGIN_TOKEN_KEY`, `OTP_TICKET_STORAGE_KEY`)

**Types (Frontend):**
- Interfaces: PascalCase with descriptive suffixes - `FrontInfoResponse`, `GeneralFrontInfo`, `CityBasicCardProps`
- Type aliases: PascalCase - `MailboxType`, `CommandArg`, `InheritBuffType`
- All types centralized in `frontend/src/types/index.ts`

**Types (Backend):**
- Data classes for DTOs: PascalCase with `Request`/`Response` suffixes - `CreateGeneralRequest`, `GeneralResponse`
- Entities: PascalCase singular nouns matching table names - `General`, `City`, `Nation`

## Field Naming (Legacy Parity Critical)

Follow core PHP conventions strictly. These field names MUST match legacy:
- `intel` (NOT `intelligence`)
- `crew` / `crewType` / `train` / `atmos` (military stats)
- `agri` / `comm` / `secu` / `def` / `wall` (city stats)
- `pop` / `popMax` / `agriMax` / `commMax` / `secuMax` / `defMax` / `wallMax`
- `dex1` through `dex5` (dexterity stats)
- `npcState` / `npcOrg` / `killTurn` / `turnTime`
- `officerLevel` / `officerCity`
- `belong` / `betray` / `personalCode` / `specialCode` / `specAge`
- `bill` / `rate` / `rateTmp` (nation economy)

Reference: `CLAUDE.md` Field Naming section and `frontend/src/types/index.ts`

## Code Style

**Formatting (Frontend):**
- 4-space indentation
- Single quotes for strings
- Trailing commas in multi-line structures
- No semicolons (auto-inserted by transpiler, but code consistently omits trailing semicolons in some files and includes them in others -- mixed but generally included)
- No Prettier config file detected -- style enforced by ESLint

**Formatting (Backend):**
- 4-space indentation (Kotlin default)
- Trailing commas in multi-line parameter lists and when-expressions
- String templates used for interpolation: `"${variable}"`

**Linting (Frontend):**
- ESLint 9 with flat config: `frontend/eslint.config.mjs`
- Extends `eslint-config-next/core-web-vitals` and `eslint-config-next/typescript`
- TypeScript strict mode enabled in `frontend/tsconfig.json`

**Linting (Backend):**
- No dedicated Kotlin linter (no detekt/ktlint config detected)
- Kotlin compiler with `-Xjsr305=strict` for null-safety interop
- JPA `allOpen` annotations for entities: `@Entity`, `@MappedSuperclass`, `@Embeddable`

## Import Organization

**Frontend (TypeScript):**
1. React/framework imports: `import { useState } from 'react'`
2. Next.js imports: `import { useRouter } from 'next/navigation'`
3. Third-party libraries: `import axios from 'axios'`, `import { create } from 'zustand'`
4. Path-aliased internal imports: `import { City } from '@/types'`, `import api from '@/lib/api'`
5. Relative imports for co-located files: `import { SammoBar } from '@/components/game/sammo-bar'`

**Path Alias:** `@/*` maps to `./src/*` (configured in `frontend/tsconfig.json`)

**Backend (Kotlin):**
1. Project package imports: `import com.opensam.dto.*`, `import com.opensam.service.*`
2. Spring framework imports: `import org.springframework.*`
3. Java/Jakarta imports: `import jakarta.persistence.*`, `import java.time.*`
4. Third-party imports: `import com.fasterxml.jackson.*`

No import ordering enforcer configured.

## Error Handling

**Frontend Patterns:**
- Axios interceptor catches 401 responses globally, clears token, redirects to `/login` (except for auth endpoints): `frontend/src/lib/api.ts`
- 400 responses: `applyApiErrorMessage()` extracts backend validation error messages: `frontend/src/lib/api-error.ts`
- Auth errors: `extractAuthErrorMessage()` provides fallback message extraction: `frontend/src/lib/auth-error.ts`
- Component-level: try-catch with `toast.error()` for user-facing errors (Sonner toast library)
- Store-level: `Promise.allSettled()` for parallel requests with partial failure tolerance: `frontend/src/stores/gameStore.ts`

**Backend Patterns:**
- Controllers return `ResponseEntity` with explicit HTTP status codes
- Null checks return `ResponseEntity.notFound().build()` or `ResponseEntity.badRequest().build()`
- Auth checks via `SecurityContextHolder.getContext().authentication?.name`
- Service-level: `IllegalArgumentException` with Korean error messages for validation
- No global `@ControllerAdvice` exception handler detected -- errors handled per-controller

## Logging

**Frontend:**
- No structured logging framework
- `console` methods for development debugging
- User-facing notifications via `sonner` toast: `toast.info()`, `toast.warning()`, `toast.error()`
- Sound effects on events via `playSoundEffect()`: `frontend/src/hooks/useSoundEffects.ts`

**Backend:**
- Spring Boot default logging (SLF4J/Logback)
- No custom logging configuration detected
- Game records stored in database via `RecordService` for audit trail

## Comments

**When to Comment:**
- Legacy parity references: `// Legacy: score = clamp(...)` with PHP line references
- JSDoc `/** */` on interfaces in `frontend/src/types/index.ts` for complex fields
- `@deprecated` annotations for superseded types
- Korean-language comments throughout (matches development team language)

**Pattern:**
```typescript
/** Spy intel map: city ID (string) -> spy level (number) */
spy: Record<string, number>;
```

```kotlin
/**
 * Get average stats for generals in a nation.
 */
@Query("""...""")
fun getAverageStats(...)
```

## Function Design

**Backend Services:**
- Constructor injection via Kotlin primary constructor: `class GeneralService(private val generalRepository: GeneralRepository, ...)`
- `@Service` annotation on all service classes
- `@Transactional` on write operations
- Methods return domain entities or null (not Optional)
- Companion objects for constants: `companion object { private const val JOIN_STAT_TOTAL = 350 }`

**Frontend Components:**
- Functional components only (no class components)
- `'use client'` directive at top of client-side components
- Props defined as inline interfaces: `interface CityBasicCardProps { ... }`
- Early return for null/empty states: `if (!city) return null;`
- `data-tutorial` attributes on key elements for tutorial system

**Frontend Stores (Zustand):**
- Created with `create<StoreType>((set) => ({...}))` pattern
- Exported as `use{Name}Store` hooks
- State and actions co-located in single store definition
- In-flight deduplication for network requests (see `_inflightLoadAll` in `frontend/src/stores/gameStore.ts`)

## Module Design

**Backend:**
- Multi-module Gradle project: `shared`, `gateway-app`, `game-app`
- `shared` module: DTOs, JWT validation, Jackson configuration (no Spring Boot app)
- `gateway-app`: auth, user management, proxy to game JVMs
- `game-app`: game logic, turn engine, commands, entities
- Package structure: `com.opensam.{layer}` - controller, service, repository, entity, dto, engine, command

**Frontend:**
- No barrel files except `frontend/src/types/index.ts` (single barrel for all types)
- API clients grouped by domain in `frontend/src/lib/gameApi.ts` as object literals: `worldApi`, `nationApi`, `cityApi`, `generalApi`
- Stores per domain: `authStore.ts`, `gameStore.ts`, `worldStore.ts`, `generalStore.ts`

**Exports:**
- Backend: standard Spring component scanning (no explicit exports)
- Frontend: named exports for components and utilities; default export only for `api` instance (`frontend/src/lib/api.ts`)

## API Layer Pattern

**Frontend API clients** (`frontend/src/lib/gameApi.ts`):
```typescript
export const cityApi = {
    listByWorld: (worldId: number) => api.get<City[]>(`/worlds/${worldId}/cities`),
    get: (id: number) => api.get<City>(`/cities/${id}`),
    listByNation: (nationId: number) => api.get<City[]>(`/nations/${nationId}/cities`),
};
```

**Backend Controllers** (`backend/game-app/src/main/kotlin/com/opensam/controller/`):
```kotlin
@RestController
@RequestMapping("/api")
class GeneralController(
    private val generalService: GeneralService,
    private val frontInfoService: FrontInfoService,
    private val worldService: WorldService,
) {
    @GetMapping("/worlds/{worldId}/generals")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(generalService.listByWorld(worldId).map { GeneralResponse.from(it) })
    }
}
```

**DTO Mapping:** Response DTOs use companion `from()` factory methods:
```kotlin
data class GeneralResponse(...) {
    companion object {
        fun from(entity: General): GeneralResponse = GeneralResponse(...)
    }
}
```

## Git Commit Message Style

**Format:** `{type}: {Korean description}`

**Types used:**
- `feat:` - new features
- `fix:` - bug fixes
- `chore:` - maintenance tasks
- `revert:` - reverts

**Examples from recent history:**
```
feat: WebSocket 실시간 갱신 전체 확대 — 대시보드 + 커맨드 리스트
fix: 시나리오 NPC 국가 지급률/세율 기본값 레거시 패러티 (bill=100, rate=15)
chore: 디버그 로그 제거 — 천도 버그 수정 확인 완료
revert: 3D 맵 전체 철회 — 2D 전용으로 전환
```

**Convention:** Messages are in Korean. Use em-dash (—) to separate the scope/area from details. Include parenthetical specifics when relevant.

## TDD Gate (Pre-Commit Hook)

The project enforces a **mandatory TDD gate** via `scripts/verify/tdd-gate.sh`:
- Backend source changes (`backend/.*/src/main/.*\.kt`) MUST be accompanied by backend test changes
- Frontend source changes (`frontend/src/(app|components|lib|stores|hooks)/.*\.(ts|tsx)`) MUST be accompanied by frontend test changes
- Deleting committed tests is blocked and requires manual review
- Installed via `scripts/verify/install-hooks.sh`

---

*Convention analysis: 2026-03-31*
