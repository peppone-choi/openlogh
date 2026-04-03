# Phase 1: Deterministic Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 01-deterministic-foundation
**Areas discussed:** RNG replacement, Exception logging, Turn ordering, Cross-language verification
**Mode:** Auto (all decisions auto-selected)

---

## RNG Replacement

| Option | Description | Selected |
|--------|-------------|----------|
| Inject world-seeded LiteHashDRBG | Follow existing 29-file pattern, replace java.util.Random with DeterministicRng.create() | ✓ |
| Create new RNG wrapper | New abstraction layer around LiteHashDRBG | |
| Use Kotlin Random with fixed seed | kotlin.random.Random(seed) | |

**User's choice:** [auto] Inject world-seeded LiteHashDRBG (recommended default -- consistent with 29 files already using this pattern)
**Notes:** Two locations confirmed: TurnService.kt:1050 and GeneralTrigger.kt:200

---

## Exception Logging

| Option | Description | Selected |
|--------|-------------|----------|
| SLF4J warn/error with context | Add logging, preserve control flow | ✓ |
| Rethrow exceptions | Change catch blocks to propagate | |
| Structured logging (MDC) | Add MDC context for all engine operations | |

**User's choice:** [auto] SLF4J warn/error with context (recommended default -- non-invasive, observable)
**Notes:** 141 catch blocks across 40 files. TurnService.kt (21 blocks) prioritized.

---

## Turn Ordering

| Option | Description | Selected |
|--------|-------------|----------|
| Sort by primary key (ID) | Deterministic, simple, matches typical DB ordering | ✓ |
| Sort by creation timestamp | Deterministic but requires timestamp field | |
| Preserve query order with ORDER BY | Push ordering to DB queries | |

**User's choice:** [auto] Sort by primary key (recommended default -- simple, deterministic)
**Notes:** Applied at InMemoryTurnProcessor level, not repository level.

---

## Cross-Language Verification

| Option | Description | Selected |
|--------|-------------|----------|
| Golden value tests | Hardcoded expected PHP outputs in Kotlin tests | ✓ |
| Runtime PHP execution | Run PHP and compare output | |
| Snapshot comparison | Generate JSON snapshots from both runtimes | |

**User's choice:** [auto] Golden value tests (recommended default -- extends existing LiteHashDRBGTest.kt)
**Notes:** Test vectors to cover initial seed, sequential draws, 100+ sequence, edge seeds.

---

## Claude's Discretion

- Catch block triage order beyond TurnService
- @Tag("parity") annotation timing
- RandUtil single-element behavior fix approach

## Deferred Ideas

None
