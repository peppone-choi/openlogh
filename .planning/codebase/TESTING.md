# Testing Patterns

**Analysis Date:** 2026-04-05

## Test Framework

**Backend Runner:**
- JUnit 5 (Jupiter)
- Config: Gradle test task in `backend/game-app/build.gradle.kts`
- Spring Boot Test for integration tests
- Mockito for mocking repositories

**Frontend Unit Runner:**
- Vitest 3.2.4
- Config: `frontend/vitest.config.ts`
- Environment: `node` (not jsdom)
- Path alias: `@/` -> `./src/`

**Frontend E2E Runner:**
- Playwright 1.58.2
- Config: `frontend/playwright.config.ts`
- Browser: Chromium only
- Timeout: 90s per test, 15s for expects
- Retries: 1

**Run Commands:**
```bash
# Backend tests
cd backend && ./gradlew :game-app:test

# Frontend unit tests
cd frontend && pnpm test           # Run all (watch mode)
cd frontend && pnpm test -- --run  # Run once

# Frontend E2E tests
cd frontend && pnpm e2e            # All E2E
cd frontend && pnpm e2e:oauth      # OAuth gate only
cd frontend && pnpm e2e:setup      # Install browsers
```

## Test File Organization

**Backend Location:**
- Mirror source structure under `src/test/kotlin/com/openlogh/`
- Parity tests: `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/`
- QA tests: `backend/game-app/src/test/kotlin/com/openlogh/qa/`
- Command tests: `backend/game-app/src/test/kotlin/com/openlogh/command/`
- Engine tests: `backend/game-app/src/test/kotlin/com/openlogh/engine/`
- Controller tests: `backend/game-app/src/test/kotlin/com/openlogh/controller/`
- Gateway tests: `backend/gateway-app/src/test/kotlin/com/openlogh/gateway/service/`

**Frontend Unit Location:**
- Co-located with source files
- Pattern: `{feature-name}.test.ts` next to `page.tsx`
- Example: `frontend/src/app/(game)/commands/commands.test.ts`
- UI component tests: `frontend/src/components/ui/sidebar.test.tsx`, `frontend/src/components/ui/8bit/8bit-imports.test.ts`

**Frontend E2E Location:**
- `frontend/e2e/` directory
- Parity tests: `frontend/e2e/parity/` (numbered prefix for ordering)
- General E2E: `frontend/e2e/oauth-gate.spec.ts`, `frontend/e2e/game-flow.spec.ts`

**Naming:**
- Backend: `{ClassName}Test.kt` (`CommandRegistryTest.kt`, `GeneralMilitaryCommandTest.kt`)
- Backend parity: `{Domain}ParityTest.kt` (`EconomyCommandParityTest.kt`, `BattleParityTest.kt`)
- Backend QA: `{Purpose}Test.kt` (`GoldenValueTest.kt`, `ApiContractTest.kt`)
- Frontend unit: `{feature-name}.test.ts` (`commands.test.ts`, `layout.test.ts`)
- Frontend E2E: `{NN}-{name}.spec.ts` for parity (`01-main.spec.ts`), `{name}.spec.ts` for general

**File Counts:**
- Backend: 125 test files
- Frontend unit: 64 test files
- Frontend E2E: 7 spec files

## Test Structure

### Backend: JUnit 5 with Nested Classes

```kotlin
@DisplayName("Command Logic Legacy Parity")
class CommandParityTest {

    private val mapper = jacksonObjectMapper()

    @Nested
    @DisplayName("che_훈련 - legacy che_훈련.php:89")
    inner class TrainingParity {

        @Test
        fun `high leadership low crew gives high training score`() {
            val gen = createGeneral(leadership = 100, crew = 100, train = 0)
            val result = runCmd(che_훈련(gen, createEnv()), "train_1")
            assertTrue(result.success)
            val json = mapper.readTree(result.message)
            assertEquals(100, json["statChanges"]["train"].asInt())
        }
    }

    // ── Helpers ──
    private fun createGeneral(...): General = General(...)
    private fun createCity(...): City = City(...)
    private fun createEnv(...): CommandEnv = CommandEnv(...)
}
```

**Patterns:**
- `@DisplayName` on class and nested classes for readable test names (443 usages across 26 files)
- `@Nested inner class` to group related tests by topic
- Backtick method names for BDD-style descriptions: `` `training cannot exceed max train` ``
- Private helper factories at bottom of test class
- `runBlocking { }` wrapper for coroutine command execution

### Frontend Unit: Vitest describe/it

```typescript
import { describe, expect, it } from 'vitest';

describe('game layout phase redirect', () => {
    function shouldRedirect(config: {...}, pathname: string): string | null {
        // Extract and test pure logic without rendering
    }

    it('redirects to lobby when reserved (startTime in future)', () => {
        const startTime = new Date(Date.now() + 3600000).toISOString();
        expect(shouldRedirect({ startTime }, '/map')).toBe('/lobby');
    });
});
```

**Patterns:**
- Extract pure logic into local helper functions, test the logic (not the component render)
- No DOM rendering — Vitest environment is `node`, not `jsdom`
- Source-reading tests: read `.tsx` file as string and assert content via `toContain()`:
```typescript
const src = fs.readFileSync(path.resolve(__dirname, 'page.tsx'), 'utf-8');
it('subscribes to /turn topic', () => {
    expect(src).toContain('/topic/world/${currentWorld.id}/turn');
});
```
- Structural assertion tests: verify CSS classes, boolean flags, data shapes

### Frontend E2E: Playwright Parity Tests

```typescript
import { test } from '@playwright/test';
import { assertParityMatches, createReport, writeParityReport, type ParityCheckResult } from './parity-helpers';

test.describe('Parity: Main', () => {
    test('main page sections are visible in legacy and new', async ({ browser }) => {
        const results: ParityCheckResult[] = [];
        // ... login to both systems, compare, collect results
        assertParityMatches('main', results);
    });
});
```

## Mocking

**Backend Mocking:**
- Framework: Mockito
- Pattern: Mock repositories, inject into command constructors
```kotlin
private val generalRepo = mock(GeneralRepository::class.java)
`when`(generalRepo.findById(1L)).thenReturn(Optional.of(testGeneral))
```
- Most tests are **mock-free** — commands are tested by direct instantiation with entity objects

**Frontend Mocking:**
- No mocking framework used
- Tests extract pure logic into functions and test those directly
- No component rendering or DOM mocking

**What to Mock (Backend):**
- Repository calls when testing controller/service layers
- External services (Redis, WebSocket) in integration tests

**What NOT to Mock:**
- Command logic — test with real entity objects and `LiteHashDRBG` seeds
- Engine calculations — use golden values for regression
- Entity relationships — use in-memory objects

## Fixtures and Factories

**Backend Test Data Factories:**
Each test class defines its own `createGeneral()`, `createCity()`, `createEnv()`, `createNation()` helper functions:

```kotlin
private fun createGeneral(
    leadership: Short = 70,
    strength: Short = 70,
    intel: Short = 70,
    gold: Int = 500,
    rice: Int = 500,
    crew: Int = 1000,
    crewType: Short = 0,
    train: Short = 60,
    atmos: Short = 60,
): General = General(
    id = 1, worldId = 1, name = "테스트장수",
    nationId = 1, cityId = 1,
    gold = gold, rice = rice, crew = crew,
    crewType = crewType, train = train, atmos = atmos,
    leadership = leadership, strength = strength, intel = intel,
    politics = 60, charm = 60,
    turnTime = OffsetDateTime.now(),
)

private fun createEnv(develCost: Int = 100): CommandEnv = CommandEnv(
    year = 200, month = 1, startYear = 190,
    worldId = 1, realtimeMode = false, develCost = develCost,
)
```

- Korean test names: `"테스트장수"` (test general), `"테스트도시"` (test city), `"테스트국"` (test nation)
- Default values optimized for the specific test scenario
- No shared fixture files — each test class is self-contained

**Frontend E2E Test Data:**
- `frontend/e2e/parity/parity-helpers.ts` provides shared helper functions
- `frontend/e2e/parity/parity-config.ts` provides configuration constants
- Helper interfaces: `ParityCheckResult`, `GameContext`, `GeneralSnapshot`, `CitySnapshot`, `NationSnapshot`
- Auto-creates test generals if none exist (via API calls in setup)

**Location:**
- Backend: Inline in each test class (private helper functions)
- Frontend E2E: `frontend/e2e/parity/parity-helpers.ts` (shared), `frontend/e2e/parity/parity-config.ts` (config)

## Deterministic Testing

**Seeded Random (DRBG):**
Commands use `LiteHashDRBG` (deterministic random bit generator) for reproducible outcomes:

```kotlin
val result = runBlocking { cmd.run(LiteHashDRBG.build("train_1")) }
```

- Each test passes a unique seed string to `LiteHashDRBG.build()`
- Same seed always produces same random values
- Enables cross-command determinism verification:
```kotlin
@Test
fun `all domestic commands deterministic with same seed`() {
    val r1 = runDomestic(cmd1, city1, "det_농지개간")
    val r2 = runDomestic(cmd2, city2, "det_농지개간")
    assertEquals(r1.message, r2.message, "Command should be deterministic")
}
```

## Coverage

**Requirements:** Not formally enforced (no coverage thresholds in config)

**View Coverage:**
```bash
cd frontend && pnpm test -- --coverage    # Vitest coverage
cd backend && ./gradlew :game-app:test    # JUnit (no coverage plugin detected)
```

## Test Types

### Parity Tests (Backend - Primary Pattern)

The dominant test pattern. Verify Kotlin reimplementation matches legacy PHP logic exactly.

**Location:** `backend/game-app/src/test/kotlin/com/openlogh/qa/parity/`
**Files:** 13 parity test files covering all major game systems

| File | Domain |
|------|--------|
| `CommandParityTest.kt` | General command formulas (training, conscription, domestic) |
| `EconomyCommandParityTest.kt` | Economy command calculations |
| `EconomyEventParityTest.kt` | Economy event processing |
| `EconomyFormulaParityTest.kt` | Core economy formulas |
| `EconomyIntegrationParityTest.kt` | End-to-end economy flows |
| `BattleParityTest.kt` | Combat system |
| `DiplomacyParityTest.kt` | Diplomacy mechanics |
| `TurnPipelineParityTest.kt` | Turn processing pipeline |
| `ScenarioDataParityTest.kt` | Scenario initialization data |
| `ConstraintParityTest.kt` | Command constraint validation |
| `DisasterParityTest.kt` | Disaster/event system |
| `NpcAiParityTest.kt` | NPC AI decision-making |
| `TechResearchParityTest.kt` | Technology research system |
| `GameEndParityTest.kt` | Victory/defeat conditions |

**Pattern:**
```kotlin
@DisplayName("che_훈련 - legacy che_훈련.php:89")
inner class TrainingParity {
    @Test
    fun `high leadership low crew gives high training score`() {
        // Legacy: score = clamp(round(leadership * 100 / crew * trainDelta), 0, maxTrain - train)
        val gen = createGeneral(leadership = 100, crew = 100, train = 0)
        val result = runCmd(che_훈련(gen, createEnv()), "train_1")
        val json = mapper.readTree(result.message)
        assertEquals(100, json["statChanges"]["train"].asInt())
    }
}
```

- Comments reference exact PHP file and line number
- `@DisplayName` includes legacy file reference
- Tests verify formula output matches PHP implementation exactly

### Golden Value Tests (Backend)

Lock in exact numeric outputs as regression guards.

**Location:** `backend/game-app/src/test/kotlin/com/openlogh/qa/GoldenValueTest.kt`

**Pattern:** Test specific formula outputs with exact expected values:
```kotlin
@Test
@DisplayName("same crew type: blendedTrain = (1000*80 + 1000*40) / 2000 = 60")
fun `same crew type blend produces train 60`() {
    // ...
    assertEquals(-20, trainDelta, "blended train should be 60 (delta=-20 from 80)")
}
```

### API Contract Tests (Backend)

Verify controller endpoints exist and DTO fields are correct using reflection.

**Location:** `backend/game-app/src/test/kotlin/com/openlogh/qa/ApiContractTest.kt`

**Pattern:**
```kotlin
@Test
fun `buildPoolGeneral endpoint exists`() {
    val fn = GeneralController::class.memberFunctions
        .firstOrNull { it.name == "buildPoolGeneral" }
    assertNotNull(fn)
    val annotation = fn!!.findAnnotation<PostMapping>()
    assertNotNull(annotation)
}
```

- Lightweight: no Spring context startup, uses Kotlin reflection only
- Verifies method existence, annotation presence, DTO field presence

### Command Unit Tests (Backend)

Test individual commands with mock-free direct execution.

**Location:** `backend/game-app/src/test/kotlin/com/openlogh/command/`
**Files:** `CommandRegistryTest.kt`, `GeneralMilitaryCommandTest.kt`, `GeneralPoliticalCommandTest.kt`, `FieldBattleTest.kt`, `ArgSchemaValidationTest.kt`, `ConstraintChainTest.kt`, etc.

**Pattern:**
```kotlin
@Test
fun `registry should have 58 general commands`() {
    val names = registry.getGeneralCommandNames()
    assertEquals(58, names.size)
}

@Test
fun `createGeneralCommand should fallback to 휴식 for unknown action`() {
    val cmd = registry.createGeneralCommand("존재하지않는명령", general, env)
    assertEquals("휴식", cmd.actionName)
}
```

### Frontend Unit Tests (Vitest)

Logic extraction tests — verify business logic without rendering components.

**Location:** Co-located with source as `*.test.ts`

**Patterns:**
1. **Pure logic extraction**: Re-implement page logic in test, verify behavior
2. **Source content assertion**: Read `.tsx` file as string, assert it contains expected patterns
3. **Structural verification**: Assert CSS class combinations, data shapes

### Frontend E2E Parity Tests (Playwright)

Compare legacy PHP system against new Next.js system side by side.

**Location:** `frontend/e2e/parity/`

**Numbered execution order:**
1. `01-main.spec.ts` — Main page sections
2. `02-links.spec.ts` — Navigation links
3. `03-pages.spec.ts` — Page content
4. `04-commands.spec.ts` — Command table and execution (600s timeout)
5. `05-nation-commands.spec.ts` — Nation-level commands

**Infrastructure:**
- `parity-config.ts`: URLs, credentials, expected command lists
- `parity-helpers.ts`: Login helpers, API wrappers, snapshot comparators, report generators
- Reports written to `../parity-screenshots/` as JSON + screenshots
- Turn daemon pause/resume to prevent state changes during tests
- Auto-creates test generals via API if needed

**ParityCheckResult pattern:**
```typescript
interface ParityCheckResult {
    check: string;       // e.g. "command_table_has_훈련"
    legacy: boolean;     // Present in legacy system?
    new: boolean;        // Present in new system?
    match: boolean;      // Do they agree?
    details?: string;
}
// Collected into array, then:
assertParityMatches('commands', results);
```

### Other E2E Tests

- `frontend/e2e/oauth-gate.spec.ts` — OAuth login flow
- `frontend/e2e/game-flow.spec.ts` — General game flow

## Common Patterns

**Async Testing (Backend):**
```kotlin
val result = runBlocking { cmd.run(LiteHashDRBG.build("seed")) }
assertTrue(result.success)
```

**JSON Result Parsing (Backend):**
```kotlin
private val mapper = jacksonObjectMapper()
val json = mapper.readTree(result.message)
assertEquals(100, json["statChanges"]["train"].asInt())
assertTrue(json["cityChanges"]["wall"].asInt() > 0)
```

**Error Testing (Backend):**
```kotlin
@Test
fun `createNationCommand should return null for unknown action`() {
    val cmd = registry.createNationCommand("존재하지않는명령", general, env)
    assertNull(cmd)
}
```

**Edge Case Testing (Backend):**
```kotlin
@Test
fun `training at max gives zero`() {
    val gen = createGeneral(train = 100)
    val result = runCmd(che_훈련(gen, createEnv()), "train_3")
    assertEquals(0, json["statChanges"]["train"].asInt())
}
```

## CI Integration

**Verification Scripts:**
- `frontend/scripts/verify-oauth-gate.mjs` — OAuth gate verification
- `scripts/verify/frontend-parity.mjs` — Parity report verification
- Run via `pnpm verify:oauth-gate` and `pnpm verify:parity`

**Docker CI:**
- Frontend Dockerfile includes multi-stage build (deps, builder, runner)
- GitHub Actions (`.github/workflows/`) handle CI/CD pipeline
- GHCR (GitHub Container Registry) for built images

---

*Testing analysis: 2026-04-05*
