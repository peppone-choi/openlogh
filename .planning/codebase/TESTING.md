# Testing Patterns

**Analysis Date:** 2026-03-31

## Test Framework

**Runner:**

- Frontend unit: Vitest 3.2.4 (config: `frontend/vitest.config.ts`)
- Frontend E2E: Playwright 1.58.2 (config: `frontend/playwright.config.ts`)
- Backend: JUnit 5 (Jupiter) via Gradle, configured in `backend/build.gradle.kts`

**Assertion Library:**

- Frontend: Vitest built-in `expect()` (Jest-compatible API)
- Frontend E2E: Playwright `expect()` with locator matchers
- Backend: `org.junit.jupiter.api.Assertions.*` (assertEquals, assertTrue, assertFalse, assertThrows, assertNotNull)

**Run Commands:**

Frontend:

```bash
cd frontend && pnpm test              # Run all Vitest unit tests (watch mode)
cd frontend && pnpm test -- --run     # Run once and exit
cd frontend && pnpm test -- --run --reporter=verbose  # Verbose (used in CI)
cd frontend && pnpm e2e               # Run all Playwright E2E tests
cd frontend && pnpm e2e:setup         # Install Playwright Chromium browser
cd frontend && pnpm e2e:oauth         # Run OAuth-specific E2E tests only
cd frontend && pnpm verify:parity     # Frontend parity verification script
```

Backend:

```bash
cd backend && ./gradlew test                    # Run all JUnit tests (both apps)
cd backend && ./gradlew :game-app:test          # Test game-app only
cd backend && ./gradlew :gateway-app:test       # Test gateway-app only
```

## Test File Organization

**Location:**

- Frontend unit: Co-located with source files
    - `frontend/src/app/(game)/page.tsx` -> `frontend/src/app/(game)/page.test.ts`
    - `frontend/src/lib/game-utils.ts` -> `frontend/src/lib/game-utils.test.ts`
    - `frontend/src/stores/gameStore.ts` -> `frontend/src/stores/gameStore.test.ts`
    - `frontend/src/components/app-sidebar.tsx` -> `frontend/src/components/app-sidebar.test.tsx`
    - Vitest config: `include: ['src/**/*.{test,spec}.{ts,tsx}']`
- Frontend E2E: Separate `frontend/e2e/` directory
    - `frontend/e2e/oauth-gate.spec.ts`
    - `frontend/e2e/game-flow.spec.ts`
    - `frontend/e2e/parity/` subdirectory for parity checks
- Backend: Standard `src/test/kotlin/` parallel tree
    - `backend/game-app/src/test/kotlin/com/openlogh/` (mirrors main source structure)
    - `backend/gateway-app/src/test/kotlin/com/openlogh/gateway/` (only 1 test file)

**Naming:**

- Frontend unit: `[feature-name].test.ts` or `[feature-name].test.tsx`
    - Page tests: `page.test.ts`, `layout.test.ts`
    - Feature tests: `lobby.test.ts`, `join.test.ts`, `select-npc.test.ts`
    - Component tests: `app-sidebar.test.tsx`, `collapsible.test.tsx`
    - Library tests: `game-utils.test.ts`, `api-error.test.ts`, `auth-error.test.ts`
- Frontend E2E: `[flow-name].spec.ts`
    - Examples: `oauth-gate.spec.ts`, `game-flow.spec.ts`
    - Parity: `01-main.spec.ts`, `02-links.spec.ts`, `03-pages.spec.ts`
- Backend: `[ClassName]Test.kt`
    - Service tests: `OfficerServiceTest.kt`, `AuthServiceTest.kt`
    - Engine tests: `BattleEngineTest.kt`, `DeterministicRngTest.kt`
    - Parity tests: `BattleParityTest.kt`, `CommandParityTest.kt`, `FormulaParityTest.kt`
    - QA tests: `GoldenValueTest.kt`, `GoldenSnapshotTest.kt`, `ApiContractTest.kt`

**Directory Structure:**

```
frontend/
├── e2e/
│   ├── oauth-gate.spec.ts
│   ├── game-flow.spec.ts
│   └── parity/
│       ├── parity-config.ts
│       ├── parity-helpers.ts
│       ├── 01-main.spec.ts
│       ├── 02-links.spec.ts
│       ├── 03-pages.spec.ts
│       ├── 04-commands.spec.ts
│       └── 05-nation-commands.spec.ts
├── src/
│   ├── app/(game)/page.test.ts        # Co-located with page.tsx
│   ├── lib/game-utils.test.ts         # Co-located with game-utils.ts
│   ├── stores/gameStore.test.ts       # Co-located with gameStore.ts
│   └── components/app-sidebar.test.tsx # Co-located with app-sidebar.tsx

backend/game-app/src/test/kotlin/com/openlogh/
├── OpenloghApplicationTests.kt        # Spring Boot smoke test
├── command/
│   ├── CommandTest.kt                 # Core command tests
│   ├── CommandExecutorTest.kt
│   ├── CommandRegistryTest.kt
│   ├── ConstraintTest.kt
│   ├── ConstraintChainTest.kt
│   ├── CommandParityTest.kt
│   ├── GeneralCivilCommandTest.kt
│   ├── IndividualCommandTest.kt
│   ├── LastTurnTest.kt
│   └── NationResourceCommandTest.kt
├── controller/
│   ├── ControllerStubs.kt            # Test-only lightweight controller stubs
│   ├── GeneralControllerTest.kt
│   └── OfficerControllerTest.kt
├── dto/
│   └── DtoStubs.kt                   # Shared test DTO definitions
├── engine/
│   ├── BattleEngineTest.kt (→ relocated to war/)
│   ├── CommandPointServiceTest.kt
│   ├── DeterministicRngTest.kt
│   ├── DeterministicReplayParityTest.kt
│   ├── EconomyServiceTest.kt
│   ├── FormulaParityTest.kt
│   ├── GameplayIntegrationTest.kt
│   ├── GoldenSnapshotTest.kt
│   ├── InMemoryTurnHarnessIntegrationTest.kt
│   ├── TurnServiceTest.kt
│   ├── TurnDaemonTest.kt
│   ├── ai/
│   │   ├── FactionAITest.kt
│   │   ├── NpcPolicyTest.kt
│   │   └── OfficerAITest.kt
│   ├── tactical/
│   │   ├── TacticalBattleEngineTest.kt
│   │   └── TacticalTestFixtures.kt    # Shared fixture object
│   ├── trigger/
│   │   ├── GeneralTriggerTest.kt
│   │   └── TriggerCallerTest.kt
│   ├── turn/cqrs/
│   │   ├── TurnCoordinatorTest.kt
│   │   └── TurnStatusServiceTest.kt
│   └── war/
│       ├── BattleEngineTest.kt
│       ├── BattleEngineParityTest.kt
│       ├── BattleServiceTest.kt
│       ├── BattleTriggerTest.kt
│       ├── WarAftermathTest.kt
│       ├── WarFormulaTest.kt
│       └── WarUnitCityParityTest.kt
├── entity/
│   ├── OfficerOptimisticLockTest.kt
│   └── SelectPoolTest.kt
├── qa/
│   ├── ApiContractTest.kt
│   ├── GoldenValueTest.kt
│   └── parity/
│       ├── BattleParityTest.kt
│       ├── CommandParityTest.kt
│       ├── ConstraintParityTest.kt
│       ├── NpcAiParityTest.kt
│       └── TechResearchParityTest.kt
├── service/
│   ├── ServiceStubs.kt                # typealias GeneralService = OfficerService
│   ├── AccountServiceTest.kt
│   ├── AdminServiceTest.kt
│   ├── AuthServiceTest.kt
│   ├── CharacterCreationServiceTest.kt
│   ├── CharacterLifecycleServiceTest.kt
│   ├── FactionJoinServiceTest.kt
│   ├── FactionServiceTest.kt
│   ├── OfficerServiceTest.kt
│   ├── PermissionServiceTest.kt
│   ├── PlanetServiceTest.kt
│   ├── PositionCardServiceTest.kt
│   ├── RankLadderServiceTest.kt
│   ├── SelectPoolServiceTest.kt
│   ├── WorldServiceTest.kt
│   └── (15+ more service tests)
├── test/
│   └── InMemoryTurnHarness.kt         # Central test harness
└── websocket/
    └── TacticalExecutorLeakTest.kt
```

## Test Structure

**Backend - JUnit 5 Pattern:**

```kotlin
class SomeServiceTest {

    private lateinit var dependency1: Dependency1
    private lateinit var dependency2: Dependency2
    private lateinit var service: SomeService

    @BeforeEach
    fun setUp() {
        dependency1 = mock(Dependency1::class.java)
        dependency2 = mock(Dependency2::class.java)
        service = SomeService(dependency1, dependency2)

        // Common stub setup
        `when`(dependency1.findById(anyLong())).thenReturn(Optional.of(testEntity))
    }

    @Test
    fun `descriptive test name in Korean backticks`() {
        // Arrange: build test data inline
        val entity = SomeEntity(id = 1, name = "테스트")

        // Act
        val result = service.doSomething(entity)

        // Assert
        assertEquals(expected, result)
        verify(dependency1).save(any())
    }
}
```

**Backend - Nested Tests with @DisplayName:**

```kotlin
@DisplayName("Golden Value Regression Tests")
class GoldenValueTest {

    @Nested
    @DisplayName("BattleEngine dex damage golden values")
    inner class DexDamage {

        @Test
        @DisplayName("dex5 advantage yields higher dexLog ratio")
        fun `dex5 advantage`() {
            val log = getDexLog(1000, 200)
            assertTrue(log > 1.0)
        }
    }
}
```

**Frontend - Vitest Pattern:**

```typescript
import { describe, expect, it } from 'vitest';

describe('feature area', () => {
    it('specific behavior description', () => {
        // Inline data setup + assertion
        const result = functionUnderTest(input);
        expect(result).toBe(expected);
    });
});
```

**Test Name Conventions:**

- Backend: Backtick test names in Korean or English describing behavior
    - `fun \`createOfficer applies legacy join options and seeds rest turns\`()`
    - `fun \`resolveBattle deterministic with same seed\`()`
    - `fun \`dex 0 returns level 0\`()`
- Frontend: English `it()` descriptions
    - `it('parses legacy prefixed crew type', () => { ... })`
    - `it('desktop refresh button exists with RefreshCw icon', () => { ... })`

## Mocking

**Backend Framework:** Mockito

```kotlin
// Create mock
appUserRepository = mock(AppUserRepository::class.java)

// Stub return values
`when`(appUserRepository.findByLoginId("user")).thenReturn(user)
`when`(officerRepository.save(any(Officer::class.java))).thenAnswer { invocation ->
    val officer = invocation.getArgument<Officer>(0)
    if (officer.id == 0L) officer.id = 77
    officer
}
`when`(gameConstService.getInt(anyString())).thenAnswer { invocation ->
    constMap[invocation.getArgument<String>(0)] ?: 0
}

// Verify interactions
verify(officerTurnRepository).saveAll(captor.capture())
val turns = captor.value.toList()
assertEquals(30, turns.size)
```

**Frontend: No mocking framework**

- Frontend tests are predominantly pure logic tests (no React component rendering)
- Test data constructed inline as plain objects
- Some tests read source files to verify code structure:
    ```typescript
    // From app-sidebar.test.tsx
    const source = readFileSync(resolve(__dirname, 'app-sidebar.tsx'), 'utf-8');
    expect(source).not.toContain("{ title: '전체맵'");
    ```
- Some tests verify behavior via boolean flags (assertion-style tests):
    ```typescript
    // From command-panel.test.ts
    it('no longer displays duplicate turn timer badge', () => {
        const hasDuplicateTimer = false;
        expect(hasDuplicateTimer).toBe(false);
    });
    ```

**E2E: Playwright API mocking with `page.route()`:**

```typescript
await page.route(`${API_BASE}/auth/oauth/login`, async (route) => {
    await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ token }),
    });
});
```

**What to Mock (Backend):**

- Database repositories (all `*Repository` interfaces)
- External services called by the service under test
- Password encoders, JWT utilities

**What NOT to Mock (Backend):**

- Business logic under test (service methods, command execution)
- Pure calculation functions (war formulas, economy formulas)
- `CommandRegistry`, `CommandExecutor` (use real instances in integration tests)
- `MapService` in integration tests (real instance with `init()`)

## Fixtures and Factories

**Backend Test Harness:**

The central test infrastructure is `InMemoryTurnHarness` at `backend/game-app/src/test/kotlin/com/openlogh/test/InMemoryTurnHarness.kt`:

```kotlin
val harness = InMemoryTurnHarness()
harness.putWorld(world)
harness.putFaction(nation)
harness.putPlanet(city)
harness.putOfficer(general)
harness.queueOfficerTurn(officerId = general.id, actionCode = "훈련", turnIdx = 0)
harness.turnService.processWorld(world)
assertTrue(harness.officerTurnsFor(general.id).isEmpty())
```

Key features:
- In-memory maps for all entity types (officers, planets, factions, etc.)
- Mocked repositories that read/write to in-memory maps
- Real `TurnService`, `CommandExecutor`, `CommandRegistry` wired together
- Compatibility aliases: `putGeneral()` -> `putOfficer()`, `putCity()` -> `putPlanet()`
- Used by: `GameplayIntegrationTest.kt`, `GoldenSnapshotTest.kt`, `InMemoryTurnHarnessIntegrationTest.kt`

**Backend Entity Factory Pattern:**

Tests define private `create*()` helper functions within the test class:

```kotlin
private fun createOfficer(
    id: Long = 1,
    factionId: Long = 1,
    leadership: Short = 50,
    command: Short = 50,
    ships: Int = 1000,
    // ... defaults for all fields
): Officer {
    return Officer(
        id = id, sessionId = 1, name = "장수$id",
        factionId = factionId, leadership = leadership,
        // ...
    )
}
```

Pattern: Every test file defines its own factory methods with sensible defaults + named parameters for overrides.

**Backend Tactical Fixture Object:**

`backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalTestFixtures.kt`:
```kotlin
object TacticalTestFixtures {
    fun defaultOfficerStats(officerId: Long = 1L, name: String = "테스트 제독", ...) = OfficerStats(...)
    fun createUnit(id: Int, fleetId: Long = 1L, ...) = TacticalUnit(...)
    fun createFleet(fleetId: Long = 1L, ...) = TacticalFleet(...)
}
```

**Backend Stub Files:**

- `backend/game-app/src/test/kotlin/com/openlogh/dto/DtoStubs.kt` - Lightweight DTO definitions for tests
- `backend/game-app/src/test/kotlin/com/openlogh/controller/ControllerStubs.kt` - Stub controller implementations for testing controller logic without Spring context
- `backend/game-app/src/test/kotlin/com/openlogh/service/ServiceStubs.kt` - Type alias `GeneralService = OfficerService`

**Frontend Fixtures:**

- Inline data construction in each test file
- No shared fixture files for frontend
- E2E helpers in `frontend/e2e/parity/parity-helpers.ts` (interfaces + utility functions)

## Coverage

**Requirements:** Not enforced (no coverage thresholds configured)

- No Jacoco plugin configured in backend `build.gradle.kts`
- No `--coverage` flag in CI for frontend Vitest

**View Coverage:**

```bash
# Frontend
cd frontend && pnpm test -- --coverage

# Backend (would require adding jacoco plugin to build.gradle.kts)
cd backend && ./gradlew jacocoTestReport
```

## CI Integration

**CI Pipeline:** GitHub Actions at `.github/workflows/verify.yml`

Runs on: Every push to `main` and every pull request.

**Backend Job (`backend-verify`):**
1. Checkout code
2. Setup Java 17 (Temurin)
3. Setup Gradle
4. Compile all modules: `./gradlew classes testClasses --no-daemon`
5. Run unit tests: `./gradlew test --no-daemon` (with `SPRING_PROFILES_ACTIVE=test`)
6. Upload test reports on failure

**Frontend Job (`frontend-verify`):**
1. Checkout code
2. Setup pnpm 10
3. Setup Node.js 20 (with pnpm cache)
4. Install dependencies: `pnpm install --frozen-lockfile`
5. Lint: `pnpm lint`
6. Type check: `pnpm typecheck`
7. Unit tests: `pnpm test -- --run --reporter=verbose`

**Not in CI:** E2E tests (Playwright) are not run in CI -- only locally.

**Concurrency:** `cancel-in-progress: true` prevents stale pipeline runs.

## Test Types

**Unit Tests (Backend):**

- ~100 test files in `backend/game-app/src/test/`
- 1 test file in `backend/gateway-app/src/test/` (`AuthServiceTest.kt`)
- Scope: Service methods, engine calculations, command execution, entity behavior
- Pattern: Mock repositories, test real business logic
- Use Mockito for dependency isolation

**Unit Tests (Frontend):**

- ~42 test files co-located in `frontend/src/`
- Scope: Pure logic functions, store behavior, component exports, utility functions
- Pattern: Direct function calls with inline data, no DOM rendering
- Many tests are "structural" -- verifying code shape rather than runtime behavior:
    ```typescript
    const source = readFileSync(resolve(__dirname, 'app-sidebar.tsx'), 'utf-8');
    expect(source).not.toContain("{ title: '전체맵'");
    ```

**Integration Tests (Backend):**

- `GameplayIntegrationTest.kt` - Multi-turn domestic cycles via `InMemoryTurnHarness`
- `InMemoryTurnHarnessIntegrationTest.kt` - Full turn processing pipeline
- `GoldenSnapshotTest.kt` - Deterministic multi-turn snapshot comparison
- Pattern: Real business logic + mocked repositories via `InMemoryTurnHarness`

**Golden Value / Regression Tests (Backend):**

- `backend/game-app/src/test/kotlin/com/openlogh/qa/GoldenValueTest.kt` - Locked expected outputs from PHP parity analysis
- `backend/game-app/src/test/kotlin/com/openlogh/engine/GoldenSnapshotTest.kt` - Multi-turn snapshot determinism
- Pattern: Run scenario twice, assert identical results; compare against hardcoded expected values

**Parity Tests (Backend):**

Verify Kotlin implementation matches legacy PHP behavior:

- `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/BattleParityTest.kt` - Battle calculation equivalence
- `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/CommandParityTest.kt` - Command determinism
- `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/ConstraintParityTest.kt` - Constraint validation parity
- `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/NpcAiParityTest.kt` - NPC AI behavior match
- `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/TechResearchParityTest.kt` - Tech research formulas
- `backend/game-app/src/test/kotlin/com/openlogh/engine/FormulaParityTest.kt` - Economy/formula calculations
- `backend/game-app/src/test/kotlin/com/openlogh/engine/war/BattleEngineParityTest.kt` - War engine parity
- Pattern: Reference legacy PHP function names in comments, test exact same inputs/outputs

**E2E Tests (Frontend):**

- `frontend/e2e/oauth-gate.spec.ts` - OAuth login flow: callback -> lobby -> world entry
- `frontend/e2e/game-flow.spec.ts` - Full game flow with turn daemon pause/resume
- `frontend/e2e/parity/` - 5 spec files comparing legacy vs new system behavior
    - Shared config: `parity-config.ts`, `parity-helpers.ts`
- Playwright config: Chromium only, 90s timeout, 15s expect timeout, 1 retry
- API mocking via `page.route()` for deterministic tests

**Excluded Tests:**

Six backend test files are explicitly excluded from compilation in `backend/game-app/build.gradle.kts`:
```kotlin
sourceSets {
    test {
        kotlin {
            exclude(
                "com/openlogh/command/ArgSchemaValidationTest.kt",
                "com/openlogh/command/GeneralMilitaryCommandTest.kt",
                "com/openlogh/command/GeneralPoliticalCommandTest.kt",
                "com/openlogh/command/NationCommandTest.kt",
                "com/openlogh/command/NationDiplomacyStrategicCommandTest.kt",
                "com/openlogh/command/NationResearchSpecialCommandTest.kt",
            )
        }
    }
}
```
These reference unimplemented command classes (pre-existing broken tests from OpenSamguk migration).

## Common Patterns

**Deterministic Testing (Backend):**

```kotlin
@Test
fun `resolveBattle deterministic with same seed`() {
    val result1 = engine.resolveBattle(
        WarUnitGeneral(createOfficer()), listOf(WarUnitGeneral(createOfficer())),
        createPlanet(), Random(123)
    )
    val result2 = engine.resolveBattle(
        WarUnitGeneral(createOfficer()), listOf(WarUnitGeneral(createOfficer())),
        createPlanet(), Random(123)
    )
    assertEquals(result1.attackerDamageDealt, result2.attackerDamageDealt)
    assertEquals(result1.attackerWon, result2.attackerWon)
}
```

Use `Random(seed)` or `LiteHashDRBG.build("seed_string")` for deterministic RNG in tests.

**Async Testing (Backend):**

```kotlin
@Test
fun `command executes successfully`() {
    val result = runBlocking { cmd.run(fixedRng) }
    assertTrue(result.success)
    assertTrue(result.logs[0].contains("expected text"))
}
```

**Error Testing (Backend):**

```kotlin
@Test
fun `createOfficer returns clear error when user not found`() {
    `when`(appUserRepository.findByLoginId("ghost")).thenReturn(null)
    val ex = assertThrows(IllegalArgumentException::class.java) {
        service.createOfficer(1L, "ghost", CreateOfficerRequest(...))
    }
    assertEquals("계정 정보를 찾을 수 없습니다. 다시 로그인해주세요.", ex.message)
}
```

**Async Testing (Frontend):**

```typescript
it('withTimeout rejects after specified ms', async () => {
    const slow = new Promise<string>((resolve) => setTimeout(() => resolve('done'), 500));
    await expect(withTimeout(slow, 50)).rejects.toThrow('timeout');
});
```

**E2E Setup/Teardown:**

```typescript
test.beforeAll(async ({ request }) => {
    await request.post(`${GAME_APP_BASE}/internal/turn/pause`);
    // Poll until turn daemon is paused
    for (let i = 0; i < 12; i++) {
        const health = await request.get(`${GAME_APP_BASE}/internal/health`);
        const body = await health.json().catch(() => null);
        if (body?.turnState === 'PAUSED' || body?.turnState === 'IDLE') break;
        await new Promise((r) => setTimeout(r, 5000));
    }
});

test.afterAll(async ({ request }) => {
    await request.post(`${GAME_APP_BASE}/internal/turn/resume`);
});
```

**Smoke Test Pattern (Backend):**

Iterate over all specials/variants to verify no crashes:
```kotlin
@Test
fun `resolveBattle with specials does not crash`() {
    val specials = listOf("필살", "회피", "반계", "신산", "위압", ...)
    for (special in specials) {
        val result = engine.resolveBattle(
            WarUnitGeneral(createOfficer(specialCode = special)),
            listOf(WarUnitGeneral(createOfficer())),
            createPlanet(), Random(42)
        )
        assertTrue(result.attackerDamageDealt > 0, "Battle with $special should deal damage")
    }
}
```

## Test Counts Summary

| Area | Test Files | Location |
|------|-----------|----------|
| Backend game-app | ~96 `.kt` files | `backend/game-app/src/test/` |
| Backend gateway-app | 1 `.kt` file | `backend/gateway-app/src/test/` |
| Frontend unit (Vitest) | ~42 `.test.ts(x)` files | `frontend/src/` (co-located) |
| Frontend E2E (Playwright) | 7 `.spec.ts` files | `frontend/e2e/` |
| **Total** | **~146 test files** | |

---

_Testing analysis: 2026-03-31_
