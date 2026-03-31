# Technology Stack: Parity Verification Tooling

**Project:** OpenSamguk Legacy Parity
**Researched:** 2026-03-31
**Mode:** Ecosystem -- what tooling to add for systematic parity verification

## Context

The project already has a mature stack (Spring Boot 3.4.2/Kotlin 2.1.0, Next.js 16, PostgreSQL 16, Redis 7) and 99 backend test files including a well-established parity testing foundation:

- `qa/parity/` package with 8 dedicated parity tests (Battle, Command, Constraint, EconomyFormula, EconomyEvent, NpcAi, TechResearch, TurnPipeline)
- `GoldenSnapshotTest` and `GoldenValueTest` for regression detection
- `DeterministicReplayParityTest` for turn engine replay verification
- `FormulaParityTest` for economy/battle formula matching
- `InMemoryTurnHarness` for full turn lifecycle testing without DB
- `LiteHashDRBG` with PHP vector test parity (deterministic RNG matching legacy PHP)
- Playwright E2E with parity spec directory (`frontend/e2e/parity/`)
- Verification script (`scripts/verify/run.sh`) with pre-commit and CI profiles

This STACK.md recommends **additions** to close parity verification gaps, not a rewrite of existing tooling.

## Recommended Additions

### Backend Test Libraries

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| AssertJ | 3.27.x (via Spring Boot BOM) | Fluent assertions for parity comparisons | Already used in `TurnPipelineParityTest`. Spring Boot 3.4 BOM includes it. Fluent `.isEqualTo()`, `.extracting()`, `.satisfies()` chains are far more readable than JUnit `assertEquals` for complex parity assertions on game state objects. No version pin needed -- Spring Boot BOM manages it. | HIGH |
| jqwik | 1.9.3 | Property-based testing for formula parity | 93 commands with variable inputs need more than hand-picked test vectors. jqwik generates thousands of random input combinations (stat values, crew sizes, resource amounts) and verifies invariants hold. Its Kotlin module supports Kotlin 2.0+. Critical for catching edge cases PHP handles differently (integer overflow, rounding, negative values). | HIGH |
| jqwik-kotlin | 1.9.3 | Kotlin DSL for jqwik | Provides `combine {}` DSL, `anyForSubtypeOf<>()`, and Kotlin-idiomatic arbitrary builders. Required companion to jqwik for this Kotlin codebase. | HIGH |
| ArchUnit | 1.4.1 | Architecture compliance tests | Enforces gateway-app/game-app boundaries, repository pattern, and service layer rules as executable tests. Catches architectural drift (e.g., game-app importing gateway-app code, controllers calling repositories directly). Works with JVM bytecode so Kotlin is fully supported. | MEDIUM |
| Testcontainers | 2.0.x | PostgreSQL integration tests | Current tests use H2 in PostgreSQL compatibility mode, which misses real PostgreSQL behavior (JSON operators, `ON CONFLICT`, window functions). Testcontainers spins up a real PostgreSQL 16 container for integration tests. Spring Boot 3.4 has native `@ServiceConnection` support. Use for DB-layer parity tests only -- keep unit tests on H2/in-memory for speed. | MEDIUM |
| Testcontainers PostgreSQL | 2.0.x | PostgreSQL module | Module for the PostgreSQL container. Pairs with `spring-boot-testcontainers`. | MEDIUM |

### Frontend Test Libraries

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Playwright (existing) | 1.58.x | Visual regression for parity | Already installed. Add `toHaveScreenshot()` assertions to parity specs in `frontend/e2e/parity/`. Compare against legacy screenshots in `parity-screenshots/`. No new dependency needed -- just configuration. | HIGH |
| Vitest (existing) | 3.2.x | Unit test game formula mirrors | Already installed. Add `toMatchInlineSnapshot()` for frontend-side formula calculations (if any). No new dependency needed. | HIGH |

### Build/CI Tooling

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Gradle test filtering (existing) | -- | Separate parity suite from unit suite | Already partially done in `run.sh`. Formalize with JUnit5 `@Tag("parity")` annotations so `./gradlew test -PincludeTags=parity` runs parity tests independently. No library needed -- built into JUnit5 Platform. | HIGH |

## What NOT to Add (and Why)

| Technology | Why Not |
|------------|---------|
| MockK | Project already uses Mockito consistently across 99 test files. Switching mocking frameworks mid-project creates cognitive overhead and style inconsistency for zero parity benefit. MockK is better for greenfield Kotlin but migration cost here outweighs benefit. |
| Kotest (as test framework) | JUnit5 is already deeply embedded. Kotest as a full replacement would require rewriting 99 test files. Use jqwik for property-based testing instead -- it integrates with JUnit5 directly. |
| Kotest property testing | jqwik has a significantly richer PBT feature set (state-based testing, recursive generators, better shrinking, statistics). Kotest PBT is smaller and less mature. Since we are adding PBT specifically for parity edge cases, jqwik is the stronger choice. |
| Spring Cloud Contract | Overkill for internal service communication. The gateway-to-game proxy is a simple HTTP forward, not a microservice contract negotiation. |
| Cucumber/BDD frameworks | Parity testing needs exact numeric assertions, not natural language specs. BDD adds ceremony without value here. |
| Selenium/WebDriver | Playwright is already installed and configured. No reason to add a second browser automation tool. |
| KotlinSnapshot | Unmaintained (last commit 2020). The project already has its own golden snapshot pattern (`GoldenSnapshotTest`) that works better because it uses domain-specific data classes rather than generic serialization. |
| Database comparison tools (Flyway Test, DBUnit) | The parity target is PHP/MariaDB vs Kotlin/PostgreSQL -- schemas differ intentionally. Schema-level comparison tools would produce noise. Formula-level parity tests are the right approach. |

## Installation

### Backend (add to `backend/game-app/build.gradle.kts`)

```kotlin
dependencies {
    // ... existing dependencies ...

    // Property-based testing for formula parity
    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("net.jqwik:jqwik-kotlin:1.9.3")

    // Architecture compliance
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")

    // Real PostgreSQL for integration tests (optional, for DB-layer parity)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:2.0.4")
}
```

### Frontend

No new installations needed. Use existing Playwright and Vitest with configuration changes:

```typescript
// playwright.config.ts -- add to existing config
expect: {
    timeout: 15_000,
    toHaveScreenshot: {
        maxDiffPixelRatio: 0.01,  // 1% tolerance for rendering differences
    },
},
```

## Dependency Compatibility Matrix

| New Dependency | Compatible With | Verified |
|----------------|----------------|----------|
| jqwik 1.9.3 | JUnit5 (existing), JVM 17, Kotlin 2.1.0 | YES -- jqwik 1.9.0+ supports Kotlin 2.0/K2 compiler |
| ArchUnit 1.4.1 | JUnit5 (existing), JVM 17 | YES -- archunit-junit5 module |
| Testcontainers 2.0.x | Spring Boot 3.4.2, Docker (existing), JUnit5 | YES -- Spring Boot 3.4 has native `@ServiceConnection` |
| AssertJ 3.27.x | Spring Boot 3.4.2 BOM | YES -- already managed by Spring Boot BOM |

## Existing Infrastructure to Leverage (Not Replace)

| Asset | Location | How to Extend |
|-------|----------|---------------|
| InMemoryTurnHarness | `test/.../test/InMemoryTurnHarness.kt` | Add jqwik `@Property` tests that feed random game states through it |
| LiteHashDRBG | `engine/LiteHashDRBG.kt` + test | Already PHP-vector-validated. Use as seeded RNG for deterministic property tests |
| qa/parity/ package | `test/.../qa/parity/` | Add `@Tag("parity")` to all classes. Expand with jqwik property tests per command |
| GoldenSnapshotTest | `test/.../engine/GoldenSnapshotTest.kt` | Add more scenarios (multi-nation war, economy cycle, NPC decisions) |
| parity-screenshots/ | `parity-screenshots/` | Use as Playwright visual regression baselines |
| verify script | `scripts/verify/run.sh` | Already runs `qa.parity.*` tests. Add `@Tag` filtering. |

## Sources

- [jqwik User Guide 1.9.3](https://jqwik.net/docs/current/user-guide.html)
- [jqwik-kotlin on Maven Central](https://mvnrepository.com/artifact/net.jqwik/jqwik-kotlin)
- [Property-based Testing in Kotlin with jqwik](https://johanneslink.net/property-based-testing-in-kotlin/)
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html)
- [ArchUnit with Kotlin/Spring Boot](https://blog.baudson.de/blog/tactical-design-by-example-using-kotlin-and-spring-boot-part-6-archunit)
- [Testcontainers for Java](https://java.testcontainers.org/)
- [Spring Boot Testcontainers with Kotlin](https://rieckpil.de/testing-spring-boot-applications-with-kotlin-and-testcontainers/)
- [AssertJ on Maven Central](https://mvnrepository.com/artifact/org.assertj/assertj-core)
- [Playwright Visual Comparisons](https://playwright.dev/docs/test-snapshots)
- [MockK](https://mockk.io/) -- evaluated but not recommended for this project
