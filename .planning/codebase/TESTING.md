# Testing Patterns

**Analysis Date:** 2026-03-31

## Test Framework

**Backend:**
- Runner: JUnit 5 (Jupiter) via Spring Boot Test
- Assertion Library: `org.junit.jupiter.api.Assertions` (assertEquals, assertTrue, assertNotNull, assertThrows)
- Mocking: Mockito (`org.mockito.Mockito`)
- Config: `backend/build.gradle.kts` (root `useJUnitPlatform()` in all subprojects)
- Coroutine testing: `kotlinx-coroutines-test`
- Test DB: H2 in-memory (`testRuntimeOnly("com.h2database:h2")`)

**Frontend Unit:**
- Runner: Vitest 3.2
- Config: `frontend/vitest.config.ts`
- Assertion: Vitest built-in (`expect`, `describe`, `it`)
- Environment: `node` (not jsdom -- tests do NOT render React components)

**Frontend E2E:**
- Runner: Playwright 1.58
- Config: `frontend/playwright.config.ts`
- Browser: Chromium only
- Timeout: 90s per test, 15s for assertions, 30s for navigation

**Run Commands:**
```bash
# Backend - all tests
cd backend && ./gradlew test --no-daemon

# Backend - parity suite only
cd backend && ./gradlew :game-app:test \
    --tests 'com.opensam.qa.parity.*' \
    --tests 'com.opensam.qa.ApiContractTest' \
    --tests 'com.opensam.qa.GoldenValueTest' \
    --tests 'com.opensam.command.CommandParityTest' \
    --tests 'com.opensam.engine.FormulaParityTest' \
    --tests 'com.opensam.engine.DeterministicReplayParityTest' \
    --no-daemon

# Frontend - unit tests
pnpm --dir frontend test --run

# Frontend - unit tests (watch mode)
cd frontend && pnpm test

# Frontend - typecheck
cd frontend && pnpm typecheck

# Frontend - e2e tests
cd frontend && pnpm e2e

# Frontend - OAuth e2e only
cd frontend && pnpm e2e:oauth

# Full verification (pre-commit)
./verify pre-commit

# Full CI verification
./verify ci
```

## Test File Organization

**Backend - Location:**
- Mirror structure under `src/test/kotlin/com/opensam/`
- Tests organized by layer/domain matching source structure

**Backend - Structure:**
```
backend/game-app/src/test/kotlin/com/opensam/
├── command/                    # Command logic tests
│   ├── general/                # Individual general command tests
│   │   └── FieldBattleTest.kt
│   ├── CommandTest.kt          # Base command execution tests
│   ├── CommandExecutorTest.kt
│   ├── CommandParityTest.kt    # Legacy parity checks
│   ├── CommandRegistryTest.kt
│   ├── ConstraintTest.kt
│   ├── ConstraintChainTest.kt
│   ├── GeneralCivilCommandTest.kt
│   ├── GeneralMilitaryCommandTest.kt
│   ├── GeneralPoliticalCommandTest.kt
│   ├── IndividualCommandTest.kt
│   ├── NationCommandTest.kt
│   ├── NationDiplomacyStrategicCommandTest.kt
│   ├── NationResearchSpecialCommandTest.kt
│   ├── NationResourceCommandTest.kt
│   └── LastTurnTest.kt
├── controller/
│   └── GeneralControllerTest.kt
├── engine/                     # Game engine tests
│   ├── ai/
│   │   ├── GeneralAITest.kt
│   │   ├── NationAITest.kt
│   │   └── NpcPolicyTest.kt
│   ├── map/
│   │   ├── MapServiceTest.kt
│   │   └── ProximityDetectorTest.kt
│   ├── modifier/
│   │   └── OfficerLevelModifierTest.kt
│   ├── trigger/
│   │   ├── GeneralTriggerTest.kt
│   │   └── TriggerCallerTest.kt
│   ├── turn/
│   │   └── cqrs/
│   │       └── TurnCoordinatorIntegrationTest.kt
│   ├── war/
│   │   └── (battle engine tests)
│   ├── FormulaParityTest.kt
│   ├── DeterministicRngTest.kt
│   ├── DeterministicReplayParityTest.kt
│   ├── EconomyServiceTest.kt
│   ├── GameplayIntegrationTest.kt
│   ├── GoldenSnapshotTest.kt
│   └── ... (20+ engine test files)
├── entity/
│   └── (entity-level tests)
├── qa/                         # Quality assurance / parity
│   ├── parity/
│   │   ├── BattleParityTest.kt
│   │   ├── CommandParityTest.kt
│   │   ├── ConstraintParityTest.kt
│   │   ├── EconomyEventParityTest.kt
│   │   ├── EconomyFormulaParityTest.kt
│   │   ├── NpcAiParityTest.kt
│   │   ├── TechResearchParityTest.kt
│   │   └── TurnPipelineParityTest.kt
│   └── GoldenValueTest.kt
├── service/                    # Service layer tests
│   ├── GeneralServiceTest.kt
│   ├── AuthServiceTest.kt
│   ├── CityServiceTest.kt
│   ├── CommandServiceTest.kt
│   ├── NationServiceTest.kt
│   ├── WorldServiceTest.kt
│   └── ... (19 service test files)
└── test/                       # Test utilities
    └── InMemoryTurnHarness.kt
```

**Backend test count:** ~98 test files

**Frontend - Location:**
- Unit tests: co-located with source files using `.test.ts` / `.test.tsx` suffix
- E2E tests: `frontend/e2e/` directory

**Frontend - Structure:**
```
frontend/src/
├── app/
│   ├── layout.test.ts                    # Root layout tests
│   ├── (admin)/admin/
│   │   ├── admin-dashboard.test.ts
│   │   ├── page.test.ts
│   │   └── select-pool/select-pool.test.ts
│   ├── (auth)/
│   │   ├── login/login.test.ts
│   │   ├── privacy/privacy.test.ts
│   │   └── terms/terms.test.ts
│   ├── (game)/
│   │   ├── layout.test.ts
│   │   ├── page.test.ts
│   │   ├── page-width.test.ts
│   │   ├── city/city.test.ts
│   │   ├── commands/commands.test.ts
│   │   ├── generals/generals-detail.test.ts
│   │   ├── map/map.test.ts
│   │   └── ... (10+ page tests)
│   ├── (lobby)/lobby/
│   │   ├── lobby.test.ts
│   │   ├── join/join.test.ts
│   │   └── select-npc/select-npc.test.ts
│   └── (tutorial)/tutorial/tutorial-v2.test.ts
├── components/
│   ├── app-sidebar.test.tsx
│   ├── mobile-menu-sheet.test.tsx
│   ├── responsive-sheet.test.tsx
│   ├── top-bar.test.tsx
│   ├── game/
│   │   ├── city-basic-card.test.ts
│   │   ├── command-arg-form.test.ts
│   │   ├── command-panel.test.ts
│   │   ├── command-select-form.test.ts
│   │   ├── game-dashboard.test.ts
│   │   ├── general-basic-card.test.ts
│   │   ├── map-city-selector.test.tsx
│   │   ├── map-viewer.test.ts
│   │   ├── message-plate.test.ts
│   │   ├── nation-basic-card.test.ts
│   │   ├── record-zone.test.ts
│   │   └── unit-markers.test.ts
│   └── ui/
│       ├── 8bit/8bit-imports.test.ts
│       ├── collapsible.test.tsx
│       ├── resizable.test.tsx
│       └── sheet.test.tsx
├── hooks/
│   ├── useDebouncedCallback.test.ts
│   └── use-mobile.test.ts
├── lib/
│   ├── api-error.test.ts
│   ├── auth-error.test.ts
│   ├── game-utils.test.ts
│   ├── image.test.ts
│   └── interception-utils.test.ts
└── stores/
    ├── authStore.test.ts
    ├── gameStore.test.ts
    ├── generalStore.test.ts
    ├── tutorialStore.test.ts
    └── worldStore.test.ts

frontend/e2e/
├── game-flow.spec.ts           # Full game flow e2e
├── oauth-gate.spec.ts          # OAuth login flow e2e
└── parity/                     # Legacy parity comparison e2e
    ├── 01-main.spec.ts
    ├── 02-links.spec.ts
    ├── 03-pages.spec.ts
    ├── 04-commands.spec.ts
    └── 05-nation-commands.spec.ts
```

**Frontend test count:** ~56 unit test files + 7 e2e spec files

## Test Structure

### Backend Unit Test Pattern

```kotlin
class GeneralServiceTest {
    // Declare mocks as lateinit vars
    private lateinit var generalRepository: GeneralRepository
    private lateinit var service: GeneralService

    @BeforeEach
    fun setUp() {
        // Initialize mocks
        generalRepository = mock(GeneralRepository::class.java)
        // Construct service under test with mocks
        service = GeneralService(generalRepository, ...)

        // Configure common mock behaviors
        `when`(generalRepository.save(any(General::class.java))).thenAnswer { invocation ->
            val general = invocation.getArgument<General>(0)
            if (general.id == 0L) general.id = 77
            general
        }
    }

    @Test
    fun `createGeneral applies legacy join options and seeds rest turns`() {
        // Arrange: set up test data
        val user = AppUser(id = 1, loginId = "user", ...)
        `when`(appUserRepository.findByLoginId("user")).thenReturn(user)

        // Act
        val result = service.createGeneral(worldId, "user", request)

        // Assert
        assertNotNull(result)
        assertEquals(expected, result.field)
        verify(generalRepository).save(any())
    }
}
```

**Key patterns:**
- Backtick method names in Korean/English for readability
- Manual mock construction (no `@Mock`/`@InjectMocks` annotations)
- Private helper methods for creating test entities: `createTestGeneral()`, `createTestCity()`, `createTestNation()`
- `@BeforeEach` for mock setup, no `@AfterEach` cleanup

### Backend Parity Test Pattern

```kotlin
@DisplayName("Command Logic Legacy Parity")
class CommandParityTest {
    @Nested
    @DisplayName("che_훈련 - legacy che_훈련.php:89")
    inner class TrainingParity {
        @Test
        fun `high leadership low crew gives high training score`() {
            // References specific legacy PHP lines
            val gen = createGeneral(leadership = 100, crew = 100, train = 0)
            val result = runCmd(che_훈련(gen, createEnv()), "train_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(100, json["statChanges"]["train"].asInt())
        }
    }
}
```

**Key patterns:**
- `@Nested` inner classes group related parity checks
- `@DisplayName` with legacy PHP file references
- Comments document legacy formula being verified
- Deterministic RNG via `LiteHashDRBG.build(seed)` for reproducible battle tests

### Frontend Unit Test Pattern

```typescript
import { describe, expect, it } from 'vitest';

describe('applyApiErrorMessage', () => {
    it('uses backend error for 400 responses', () => {
        const error = createAxiosError(400, { error: '능력치 합계가 350이어야 합니다.' });
        const result = applyApiErrorMessage(error);
        expect(result.message).toBe('능력치 합계가 350이어야 합니다.');
    });
});
```

**Key patterns:**
- `describe`/`it` blocks (not `test`)
- Korean strings in test data matching production behavior
- Helper factory functions for test objects: `createAxiosError()`
- Logic-only testing -- no React component rendering (node environment, not jsdom)
- Some tests verify source code structure via `fs.readFileSync` (e.g., checking `data-tutorial` attributes exist)

### Frontend Source-Inspection Test Pattern

Some frontend tests verify structural properties by reading source files:

```typescript
describe('CommandPanel WebSocket subscriptions', () => {
    const source = fs.readFileSync(path.resolve(__dirname, 'command-panel.tsx'), 'utf-8');

    it('subscribes to /command topic for real-time command updates', () => {
        expect(source).toContain('/topic/world/${currentWorld.id}/command');
    });
});
```

This pattern verifies that components contain expected patterns without rendering.

### Frontend E2E Test Pattern

```typescript
import { expect, test } from '@playwright/test';

test.describe('OAuth gate: login -> lobby -> world entry', () => {
    test('OAuth callback success lands in lobby', async ({ page }) => {
        // Create mock JWT
        const token = createTestJwt({ userId: 999, sub: 'oauth_e2e', ... });

        // Mock API routes
        await page.route(`${API_BASE}/auth/oauth/login`, async (route) => {
            await route.fulfill({ status: 200, body: JSON.stringify({ token }) });
        });

        // Navigate and assert
        await page.goto('/auth/kakao/callback?code=test');
        await expect(page).toHaveURL(/\/lobby/);
    });
});
```

**Key patterns:**
- API mocking via `page.route()` -- no real backend required
- Test JWT creation helper functions
- Both legacy and new system screenshots for parity comparison

## Mocking

**Backend Framework:** Mockito (standard Spring Boot test dependency)

**Backend Patterns:**
```kotlin
// Mock creation (manual, not annotation-based)
private val generalRepository = mock(GeneralRepository::class.java)

// Stubbing
`when`(generalRepository.findByWorldId(1L)).thenReturn(listOf(testGeneral))

// Answer-based stubbing for save operations
`when`(generalRepository.save(any(General::class.java))).thenAnswer { invocation ->
    val entity = invocation.getArgument<General>(0)
    if (entity.id == 0L) entity.id = nextId.getAndIncrement()
    entity
}

// Verification
verify(generalRepository).save(any())
```

**What to Mock (Backend):**
- All repository interfaces
- External services not under test
- `GameConstService` for game configuration values

**What NOT to Mock (Backend):**
- The class under test
- Pure domain logic (commands, formulas, battle engine)
- `LiteHashDRBG` -- use with known seeds for determinism

**Frontend Mocking:** Minimal -- most tests verify pure logic without mocking.

**E2E Mocking:** Playwright `page.route()` for API interception:
```typescript
await page.route(`${API_BASE}/worlds`, async (route) => {
    await route.fulfill({ status: 200, body: JSON.stringify([...]) });
});
```

## Fixtures and Factories

**Backend Test Data:**
```kotlin
// Helper functions with named parameters and defaults
private fun createTestGeneral(
    gold: Int = 1000,
    rice: Int = 1000,
    crew: Int = 0,
    crewType: Short = 0,
    leadership: Short = 50,
    strength: Short = 50,
    intel: Short = 50,
    politics: Short = 50,
    charm: Short = 50,
    nationId: Long = 1,
    cityId: Long = 1,
    officerLevel: Short = 0,
): General {
    return General(id = 1, worldId = 1, name = "테스트장수", ...)
}

private fun createTestCity(
    nationId: Long = 1,
    agri: Int = 500,
    agriMax: Int = 1000,
    ...
): City { ... }

private fun createTestNation(
    id: Long = 1,
    level: Short = 1,
    gold: Int = 10000,
): Nation { ... }
```

**Location:** Factory functions are defined as `private fun` inside each test class. No shared test fixtures directory.

**Integration Test Harness:** `backend/game-app/src/test/kotlin/com/opensam/test/InMemoryTurnHarness.kt`
- Provides in-memory implementations of all repositories
- Pre-wires `CommandExecutor`, `TurnPipeline`, and all engine services
- Used by integration tests that need full turn execution pipeline

**Frontend Test Data:**
```typescript
function createAxiosError(
    status: number,
    data: ApiErrorPayload | undefined,
    message = 'Request failed'
): AxiosError<ApiErrorPayload | undefined> { ... }
```

Factory functions defined inline in each test file. No shared fixtures.

## Coverage

**Requirements:** No coverage threshold enforced in CI or config.

**View Coverage:**
```bash
# Backend
cd backend && ./gradlew test jacocoTestReport  # (if jacoco configured)

# Frontend
cd frontend && pnpm test -- --coverage
```

No Jacoco or Istanbul/v8 coverage configuration detected.

## Test Types

**Unit Tests (Backend - ~80+ files):**
- Scope: Individual service methods, commands, engine calculations
- Pattern: Mock dependencies, test single method behavior
- Files: `backend/game-app/src/test/kotlin/com/opensam/service/*Test.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/command/*Test.kt`

**Unit Tests (Frontend - ~56 files):**
- Scope: Pure logic functions, store behavior, utility validation
- Pattern: Import function, call with inputs, assert outputs
- NO component rendering tests (vitest runs in node environment)
- Files: `frontend/src/**/*.test.{ts,tsx}`

**Parity Tests (Backend - ~10 files):**
- Scope: Verify Kotlin implementation matches legacy PHP behavior
- Pattern: Reproduce legacy formula/logic, assert identical results
- Reference legacy PHP source lines in comments
- Files: `backend/game-app/src/test/kotlin/com/opensam/qa/parity/*ParityTest.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/engine/FormulaParityTest.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/command/CommandParityTest.kt`

**Integration Tests (Backend - ~5 files):**
- Scope: Multi-component interactions (turn pipeline, gameplay flow)
- Pattern: Use `InMemoryTurnHarness` for full pipeline testing
- Files: `backend/game-app/src/test/kotlin/com/opensam/engine/GameplayIntegrationTest.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/engine/InMemoryTurnHarnessIntegrationTest.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/engine/turn/cqrs/TurnCoordinatorIntegrationTest.kt`

**Controller Tests (Backend - ~1 file):**
- Scope: HTTP layer behavior (status codes, authorization)
- Pattern: Direct controller method invocation with mocked services
- NOT using `MockMvc` or `@WebMvcTest` -- tests call controller methods directly
- Files: `backend/game-app/src/test/kotlin/com/opensam/controller/GeneralControllerTest.kt`

**E2E Tests (Frontend - 7 files):**
- Scope: Full user flows through browser
- Pattern: Playwright with API route mocking
- Files: `frontend/e2e/*.spec.ts`, `frontend/e2e/parity/*.spec.ts`

**Golden/Snapshot Tests (Backend):**
- Scope: Deterministic replay and golden value verification
- Files: `backend/game-app/src/test/kotlin/com/opensam/engine/GoldenSnapshotTest.kt`
- Files: `backend/game-app/src/test/kotlin/com/opensam/qa/GoldenValueTest.kt`

## Verification Pipeline

**Pre-commit hook** (`scripts/verify/run.sh`):
1. TDD gate check (`scripts/verify/tdd-gate.sh`):
   - Backend source changes MUST be accompanied by test changes
   - Frontend source changes MUST be accompanied by test changes
   - Deleting committed tests is blocked
2. If backend files staged: run all backend tests + parity suite
3. If frontend files staged: run unit tests + typecheck + structural parity check

**CI profile** (`./verify ci`):
1. Backend all tests
2. Backend parity suite
3. Frontend unit tests
4. Frontend typecheck
5. Frontend build
6. Frontend structural parity (`scripts/verify/frontend-parity.mjs`)

**Install hooks:**
```bash
./scripts/verify/install-hooks.sh
```

## Common Patterns

**Deterministic Testing (Backend):**
```kotlin
// Use LiteHashDRBG for reproducible random outcomes
val rng = LiteHashDRBG.build("battle_test_seed")
val result = battleEngine.resolveBattle(attacker, defenders, city, rng)

// Same seed always produces same result
val result2 = battleEngine.resolveBattle(attacker, defenders, city, LiteHashDRBG.build("battle_test_seed"))
assertEquals(result.attackerDamageDealt, result2.attackerDamageDealt)
```

**Async Testing (Frontend):**
```typescript
it('withTimeout rejects after specified ms', async () => {
    const withTimeout = <T>(promise: Promise<T>, ms = 10000): Promise<T> =>
        Promise.race([
            promise,
            new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
        ]);

    const slow = new Promise<string>((resolve) => setTimeout(() => resolve('done'), 500));
    await expect(withTimeout(slow, 50)).rejects.toThrow('timeout');
});
```

**Error Testing (Frontend):**
```typescript
it('does not override non-400 messages', () => {
    const error = createAxiosError(500, { error: 'server exploded' }, 'Internal Server Error');
    const result = applyApiErrorMessage(error);
    expect(result.message).toBe('Internal Server Error');
});
```

**Controller Authorization Testing (Backend):**
```kotlin
@BeforeEach
fun setUp() {
    SecurityContextHolder.getContext().authentication =
        UsernamePasswordAuthenticationToken("testuser", null)
}

@Test
fun `createGeneral returns 403 when world is closed`() {
    `when`(worldService.getGamePhase(world)).thenReturn(WorldService.PHASE_CLOSED)
    val result = controller.createGeneral(1L, CreateGeneralRequest(name = "test"))
    assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
}
```

## Adding New Tests

**New Backend Service Test:**
1. Create `backend/game-app/src/test/kotlin/com/opensam/service/{Name}ServiceTest.kt`
2. Mock all constructor dependencies with `mock()`
3. Use `@BeforeEach` to initialize service with mocks
4. Use backtick method names describing behavior
5. Include parity comments if implementing legacy logic

**New Frontend Unit Test:**
1. Create test file next to source: `{source-file}.test.ts`
2. Import `describe`, `expect`, `it` from `vitest`
3. Test pure logic only -- do not render components
4. For component structural checks, use `fs.readFileSync` pattern

**New E2E Test:**
1. Create `frontend/e2e/{feature}.spec.ts`
2. Mock API routes with `page.route()`
3. Use test JWT helper for authentication
4. Target Chromium only

**New Parity Test:**
1. Create in `backend/game-app/src/test/kotlin/com/opensam/qa/parity/{Feature}ParityTest.kt`
2. Reference specific legacy PHP files in comments
3. Use `@Nested` + `@DisplayName` for organization
4. Verify numeric outputs match legacy formulas exactly

---

*Testing analysis: 2026-03-31*
