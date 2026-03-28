# Testing Patterns

**Analysis Date:** 2026-03-28

## Test Framework

**Runner:**

- Frontend: Vitest v2+ (configured in `/Users/apple/Desktop/openlogh/frontend/vitest.config.ts`)
- Backend: JUnit 5 (Jupiter) with Mockito for mocking

**Assertion Library:**

- Frontend: Vitest's built-in expect() (compatible with Jest)
- Backend: JUnit 5 Assertions (org.junit.jupiter.api.Assertions)

**E2E Testing:**

- Frontend: Playwright (configured in `/Users/apple/Desktop/openlogh/frontend/playwright.config.ts`)
- Setup: `playwright install --with-deps chromium`

**Run Commands:**

Frontend:

```bash
pnpm test              # Run all Vitest tests
pnpm test --watch     # Watch mode
pnpm e2e              # Run Playwright E2E tests
pnpm e2e:setup        # Install Playwright browsers
pnpm verify:parity    # Frontend parity verification
```

Backend:

```bash
./gradlew test                    # Run JUnit tests
./gradlew :game-app:test         # Test game-app specifically
./gradlew :gateway-app:test      # Test gateway-app specifically
```

## Test File Organization

**Location:**

- Frontend: Co-located with source
    - Pattern: `join/page.tsx` + `join/join.test.ts`
    - Pattern: `app/layout.tsx` → `app/layout.test.tsx` (if testing non-page)
    - Configuration: vitest.config.ts: `include: ['src/**/*.{test,spec}.{ts,tsx}']`
- Backend: Separate test tree
    - Pattern: `src/main/kotlin/com/openlogh/` → `src/test/kotlin/com/openlogh/`
    - Example: `gateway-app/src/test/kotlin/com/openlogh/gateway/service/AuthServiceTest.kt`

**Naming:**

- Frontend: `[module].test.ts` or `[module].spec.ts`
    - Example: `lobby.test.ts`, `join.test.ts`, `select-npc.test.ts`
- Backend: `[Class]Test.kt`
    - Example: `AuthServiceTest.kt`, `CommandParityTest.kt`, `SelectPoolTest.kt`

**Structure:**

```
frontend/
├── src/app/
│   ├── (lobby)/lobby/
│   │   ├── page.tsx
│   │   └── lobby.test.ts
│   ├── (auth)/login/
│   │   ├── page.tsx
│   │   └── login.test.ts

backend/
├── gateway-app/src/
│   ├── main/kotlin/com/openlogh/gateway/
│   │   ├── service/AuthService.kt
│   │   └── entity/AppUser.kt
│   └── test/kotlin/com/openlogh/gateway/
│       └── service/AuthServiceTest.kt
```

## Test Structure

**Suite Organization (Frontend - Vitest):**

```typescript
import { describe, expect, it } from 'vitest';

describe('feature area', () => {
    it('specific behavior with expected result', () => {
        // Arrange: setup
        const input = value;

        // Act: execute
        const result = functionUnderTest(input);

        // Assert: verify
        expect(result).toBe(expected);
    });

    it('another behavior', () => {
        // ...
    });
});
```

From `lobby.test.ts`:

```typescript
describe('lobby getServerPhase', () => {
    it('detects closed when startTime is in future', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        const opentime = new Date(Date.now() + 7200000).toISOString();
        expect(getServerPhase({}, { startTime, opentime })).toBe('폐쇄');
    });
});
```

**Suite Organization (Backend - JUnit 5):**

```kotlin
class AuthServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtUtil: JwtUtil
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        passwordEncoder = mock(PasswordEncoder::class.java)
        jwtUtil = mock(JwtUtil::class.java)

        service = AuthService(appUserRepository, passwordEncoder, jwtUtil, ...)
    }

    @Test
    fun `test description in backticks`() {
        // Arrange
        `when`(appUserRepository.existsByLoginId("testuser")).thenReturn(false)

        // Act
        val response = service.register(RegisterRequest(...))

        // Assert
        assertEquals("expected", response.value)
    }

    @DisplayName("Feature description")
    @Test
    fun `another test`() { }
}
```

**Patterns:**

- Setup: `@BeforeEach` (JUnit) or inline in test (Vitest)
- Teardown: `@AfterAll` (JUnit) or cleanup in E2E hooks
- Assertions: `expect(actual).toBe(expected)` (Vitest) or `assertEquals(expected, actual)` (JUnit)
- Test names: Descriptive, use backticks for Kotlin tests `` `test name` ``

## Mocking

**Framework:**

- Frontend: No mocking framework explicitly used (tests are pure logic)
    - State passed directly: mock data constructed inline
    - Component testing via data fixtures
- Backend: Mockito
    - Config: `org.mockito.Mockito.*`
    - Setup: `mock(Class::class.java)` for all dependencies

**Patterns (Backend):**

```kotlin
// Setup mocks
appUserRepository = mock(AppUserRepository::class.java)
passwordEncoder = mock(PasswordEncoder::class.java)

// Configure behavior
`when`(appUserRepository.existsByLoginId("testuser")).thenReturn(false)
`when`(passwordEncoder.encode("secret123")).thenReturn("encoded")

// Capture arguments
val captor = ArgumentCaptor.forClass(AppUser::class.java)
verify(appUserRepository, atLeastOnce()).save(captor.capture())
val savedUser = captor.allValues.last()
```

**What to Mock:**

- Backend: Database repositories, external services, password encoders
    - Keep mocked: `AppUserRepository`, `PasswordEncoder`, `JwtUtil`
    - Keep real: Business logic under test
- Frontend: No explicit mocking (use fixtures and direct state)

**What NOT to Mock:**

- Business logic (service methods under test)
- Core game logic (want to test real behavior)
- Utility functions like `JosaUtil.pick()`

## Fixtures and Factories

**Test Data (Frontend):**

```typescript
// Inline construction
const mockNation = { name: '조조군', abbreviation: '조' };
const mockWorld = { id: 1, name: 'Test', scenarioCode: 'scenario1' };

// Date fixtures
const startTime = new Date(Date.now() + 3600000).toISOString();
const opentime = new Date(Date.now() + 7200000).toISOString();
```

**Test Data (Backend):**

From `AuthServiceTest.kt`:

```kotlin
val user = AppUser(
    id = 9,
    loginId = "deleted",
    displayName = "탈퇴유저",
    passwordHash = "encoded",
    meta = mutableMapOf("deleteRequestedAt" to "2026-03-01T00:00:00Z"),
)

val now = OffsetDateTime.now()
val dateInFuture = now.plusHours(12).toString()
```

**Location:**

- Frontend: Inline in test files (small fixtures)
    - File: `src/app/(lobby)/lobby/join/join.test.ts` - all fixtures inline
- Backend: Inline in test files, DtoStubs.kt for complex shared fixtures
    - File: `/Users/apple/Desktop/openlogh/backend/game-app/src/test/kotlin/com/openlogh/dto/DtoStubs.kt`
    - Pattern: Reusable objects for multi-test suites

## Coverage

**Requirements:** Not enforced (no coverage thresholds detected)

**View Coverage:**

```bash
# Frontend: Vitest coverage
pnpm test --coverage

# Backend: Gradle coverage (jacoco if configured)
./gradlew jacocoTestReport  # (if jacoco plugin enabled)
```

## Test Types

**Unit Tests:**

- Scope: Individual functions, service methods, pure logic
- Approach: Fast, isolated, no external dependencies
- Frontend example: `lobby.test.ts` - `getServerPhase()` with mocked dates
- Backend example: `AuthServiceTest.kt` - service methods with mocked repositories

**Integration Tests:**

- Scope: Service + Repository layers, full command execution
- Approach: Uses in-memory harness, mocked DB
- Frontend: Not explicitly separated (component tests are integration-ish)
- Backend example: `InMemoryTurnHarnessIntegrationTest.kt` - full turn processing
    - File: `/Users/apple/Desktop/openlogh/backend/game-app/src/test/kotlin/com/openlogh/test/InMemoryTurnHarness.kt`
    - Pattern: Mock repositories + real business logic

**E2E Tests:**

- Framework: Playwright (frontend only)
- Scope: Full user flows from UI through backend
- Files: `e2e/game-flow.spec.ts`, `e2e/parity/*.spec.ts`
- Pattern: Real game-app running, browser automation
- Setup: Pauses turn daemon before tests to prevent row-lock contention

**Parity Tests (QA):**

- Scope: Verify Kotlin implementation matches legacy PHP behavior
- Framework: JUnit 5 with custom harness
- Files: `/backend/game-app/src/test/kotlin/com/openlogh/qa/parity/`
    - `CommandParityTest.kt` - Command determinism
    - `BattleParityTest.kt` - Battle calculation equivalence
    - `ApiContractTest.kt` - API response shape verification

## Common Patterns

**Async Testing:**

Frontend (Vitest with async/await):

```typescript
it('async operation works', async () => {
    const result = await functionReturningPromise();
    expect(result).toBe(expected);
});
```

Backend (Kotlin coroutines in tests):

```kotlin
@Test
fun `command executes deterministically`() {
    val first = runBlocking { 휴식(gen, env).run(Random(42)) }
    val second = runBlocking { 휴식(gen, env).run(Random(42)) }
    assertTrue(first.logs == second.logs)
}
```

**Error Testing:**

Frontend (exception verification):

```typescript
expect(() => {
    // code that should throw
}).toThrow(Error);
```

Backend (exception assertion):

```kotlin
@Test
fun `register requires terms agreements`() {
    val ex = assertThrows(IllegalArgumentException::class.java) {
        service.register(RegisterRequest(agreeTerms = false, ...))
    }
    assertEquals("약관에 동의해야 가입하실 수 있습니다.", ex.message)
}
```

**Mock Verification:**

Backend:

```kotlin
// Verify called at least once
verify(appUserRepository, atLeastOnce()).save(any(AppUser::class.java))

// Verify never called
verify(appUserRepository, never()).save(any(AppUser::class.java))

// Capture arguments
val captor = ArgumentCaptor.forClass(AppUser::class.java)
verify(appUserRepository).save(captor.capture())
val savedUser = captor.value
```

**Date/Time Testing:**

```kotlin
val now = OffsetDateTime.now()
val futureDate = now.plusHours(1).toString()
val pastDate = now.minusHours(1).toString()

expect(getServerPhase({}, { startTime: futureDate })).toBe('폐쇄')
```

**Test Setup/Teardown (E2E):**

From `game-flow.spec.ts`:

```typescript
test.beforeAll(async ({ request }) => {
    // Pause turn daemon before all tests
    await request.post(`${GAME_APP_BASE}/internal/turn/pause`);
    // Wait for in-flight transaction to complete
    for (let i = 0; i < 12; i++) {
        const health = await request.get(`${GAME_APP_BASE}/internal/health`);
        const body = await health.json().catch(() => null);
        if (body?.turnState === 'PAUSED' || body?.turnState === 'IDLE') break;
        await new Promise((r) => setTimeout(r, 5000));
    }
});

test.afterAll(async ({ request }) => {
    // Resume turn daemon after tests
    await request.post(`${GAME_APP_BASE}/internal/turn/resume`);
});
```

**Serial Test Execution (E2E):**

```typescript
test.describe.serial('game full flow', () => {
    // Tests run sequentially
    test('step 1', async ({ page }) => {});
    test('step 2', async ({ page }) => {});
});
```

---

_Testing analysis: 2026-03-28_
