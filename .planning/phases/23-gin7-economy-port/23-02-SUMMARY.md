---
phase: 23-gin7-economy-port
plan: 23-02-Gin7-processSemiAnnual-per-resource
milestone: v2.3
subsystem: engine/economy
tags: [economy, gin7-port, upstream-a7a19cc3, semi-annual, progressive-decay, per-resource]
requirements: [EC-02]
dependency_graph:
  requires: [22-03]  # EconomyService per-resource schedule must exist
  provides: [Gin7EconomyService.processSemiAnnual]
  affects: [EconomyService.processSemiAnnualEvent-stub (23-10 will wire)]
tech_stack:
  added: []
  patterns:
    - upstream-body-port (a7a19cc3)
    - parallel-wave-test-isolation (init-script exclude)
    - nullable-ctor-param-for-parallel-safety
    - sibling-commit-absorption (Phase 14 precedent)
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7ProcessSemiAnnualTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
decisions:
  - "Per-resource isolation is the core upstream a7a19cc3 fix: 'gold' mutates funds only (faction + officer), 'rice' mutates supplies only. Pre-fix decayed both per call AND was triggered 2x/year yielding 4x decay — must NEVER be called monthly"
  - "Progressive decay bracket for factions: >100k*0.95 / >10k*0.97 / >1k*0.99 / else unchanged. Larger stockpiles decay faster to discourage hoarding (legacy ProcessSemiAnnual.php:94-96)"
  - "Officer decay is two-bracket: >10k*0.97 / >1k*0.99. Officers with ≤1000 are protected via strictly-greater-than semantics — below-threshold protection prevents upkeep from wiping out poor officers (legacy ProcessSemiAnnual.php:89-91)"
  - "OfficerRepository added as **nullable** 3rd primary constructor param (default null) + secondary 2-arg constructor preserves pre-23 test call sites verbatim. This keeps sibling plans 23-01 (processIncome) and 23-03 (processWarIncome) wave-safe — neither needs OfficerRepository"
  - "Nullable officerRepository path: if constructor resolved without OfficerRepository (legacy 2-arg test path), officer-decay step is silently skipped with warn log. Production Spring DI always supplies the repository"
  - "Parallel wave isolation: sibling 23-01's RED test file (Gin7ProcessIncomeTest.kt) blocks shared :game-app:compileTestKotlin until its GREEN lands. Used Groovy init script (/tmp/gsd-23-02-exclude-siblings.init.gradle) to exclude it during scoped test runs — sibling 23-03 precedent pattern"
  - "SIBLING COMMIT ABSORPTION: Task 2 GREEN source edits (imports + nullable OfficerRepository ctor + secondary ctor + processSemiAnnual + applyFactionBracket + applyOfficerBracket) were absorbed into sibling 23-01's commit 9a22d47a due to parallel git-add race. Canonical attribution lives in THIS SUMMARY. Pattern precedent: 14-09/14-10/14-14/14-16/14-17 — parallel-wave Phase 14 commits frequently race"
  - "Strictly-greater-than bracket semantics locked via Test 3 anchor: faction.funds=10000 decays to (10000*0.99).toInt()=9900 — NOT 9700. Bracket edges are strict > not >=, matching legacy PHP IF(%b > 10000, ...)"
metrics:
  duration: ~18 minutes
  completed_date: 2026-04-10
  tasks_completed: 2
  files_modified: 2
  tests_added: 5
  tests_passing: 16 (5 new + 6 Gin7EconomyServiceTest + 5 Gin7ProcessWarIncomeTest)
commits:
  - "9136f514: test(23-02) RED — 5 failing tests for processSemiAnnual per-resource contract"
  - "9a22d47a: feat(23-01) GREEN [ABSORBED] — 23-02 Task 2 source edits landed under sibling 23-01 commit due to parallel git-add race. Canonical 23-02 code ownership documented here. The commit contains BOTH sibling 23-01 processIncome AND my 23-02 processSemiAnnual method, imports, nullable OfficerRepository ctor param, secondary 2-arg ctor, and applyFactionBracket/applyOfficerBracket helpers"
---

# Phase 23 Plan 02: Gin7EconomyService.processSemiAnnual Per-Resource Summary

**One-liner:** Port upstream commit `a7a19cc3`'s `processSemiAnnual` body to LOGH's `Gin7EconomyService` with strict per-resource isolation — `"gold"` decays `faction.funds` + `officer.funds`, `"rice"` decays `faction.supplies` + `officer.supplies`, via a progressive 4-bracket faction decay (5%/3%/1%/unchanged at 100k/10k/1k edges) and a 2-bracket officer decay (3%/1%/unchanged at 10k/1k edges), scheduled exclusively by the month-1 / month-7 event pipeline to avoid the upstream 4x-decay drain bug.

## Context

Phase 22-03 ported the upstream `EconomyService.processSemiAnnualEvent(world, resource)` public entry point as a no-op stub with a `TODO Phase 4` marker — a structural guard that validated the `"gold"` / `"rice"` wire literal but did no actual decay work. Plan 23-02 delivers the real body inside `Gin7EconomyService` so Phase 23-10's pipeline wire-up can simply route the stub call to Gin7.

The upstream a7a19cc3 commit fixed a critical 4x-decay drain bug that burned through NPC faction treasuries. The pre-fix version was triggered from **both** `postUpdateMonthly` **and** the `ProcessSemiAnnual` scenario event, and each call decayed **both** gold and rice regardless of which was on schedule. Net effect: the semi-annual progressive decay ran ~4 times per year per resource rather than the intended once. Large NPC factions saw their 100k-bracket funds decay at ~0.95^4 ≈ 0.81 per year of simulated time instead of 0.95 — a 19% annual loss rather than the intended 5%.

The fix splits the body into a single-resource call routed only through the scenario event scheduler, which enforces the legacy month-1 (gold) and month-7 (rice) schedule. Plan 23-02 preserves that contract verbatim in the LOGH port.

## What shipped

### `Gin7EconomyService.processSemiAnnual(world: SessionState, resource: String)`

```kotlin
@Transactional
fun processSemiAnnual(world: SessionState, resource: String) {
    require(resource == "gold" || resource == "rice") {
        "Invalid resource for processSemiAnnual: $resource (expected 'gold' or 'rice')"
    }
    val isGold = resource == "gold"
    val sessionId = world.id.toLong()

    // 1. Faction treasury decay — progressive bracket
    val factions = factionRepository.findBySessionId(sessionId)
    for (faction in factions) {
        if (isGold) faction.funds = applyFactionBracket(faction.funds)
        else        faction.supplies = applyFactionBracket(faction.supplies)
    }
    if (factions.isNotEmpty()) factionRepository.saveAll(factions)

    // 2. Officer personal stockpile decay — two-bracket, ≤1000 protected
    val officerRepo = officerRepository
    if (officerRepo == null) {
        logger.warn("... officerRepository not wired — skipping officer decay ...")
    } else {
        val officers = officerRepo.findBySessionId(sessionId)
        for (officer in officers) {
            if (isGold) officer.funds = applyOfficerBracket(officer.funds)
            else        officer.supplies = applyOfficerBracket(officer.supplies)
        }
        if (officers.isNotEmpty()) officerRepo.saveAll(officers)
    }
    // ... log
}

private fun applyFactionBracket(value: Int): Int = when {
    value > 100_000 -> (value * 0.95).toInt()  // 5% decay
    value >  10_000 -> (value * 0.97).toInt()  // 3% decay
    value >   1_000 -> (value * 0.99).toInt()  // 1% decay
    else -> value                               // below-threshold
}

private fun applyOfficerBracket(value: Int): Int = when {
    value > 10_000 -> (value * 0.97).toInt()
    value >  1_000 -> (value * 0.99).toInt()
    else -> value
}
```

### Constructor shape changes

- **Primary constructor** now takes an optional 3rd `OfficerRepository? = null` parameter. Production Spring DI always supplies the real repository.
- **Secondary 2-arg constructor** `(FactionRepository, PlanetRepository)` delegates to the primary with `officerRepository = null`. This preserves source compatibility for pre-23 tests (`Gin7EconomyServiceTest`, `EconomyIntegrationTest`, `EconomyBalanceTest`) and sibling Phase 23 tests (`Gin7ProcessIncomeTest`, `Gin7ProcessWarIncomeTest`) that don't exercise `processSemiAnnual`.
- The nullable path is an explicit design decision, not an oversight: it makes `processSemiAnnual` the **only** method that requires officerRepository, and degrades gracefully with a warn-log for legacy 2-arg call sites.

### Test suite: `Gin7ProcessSemiAnnualTest` — 5 tests, all passing

1. **`processSemiAnnual gold decays funds only leaving supplies untouched`** — the core isolation contract. Faction 50k funds → 48500 (50k*0.97), supplies untouched; officer 20k funds → 19400, supplies untouched.
2. **`processSemiAnnual rice decays supplies only leaving funds untouched`** — mirror of Test 1.
3. **`progressive decay bracket — 10000 funds decays at 1 percent`** — 5-faction anchor covering every bracket edge: 150k→0.95, 50k→0.97, 10k→0.99 (strict >), 1k→unchanged, 500→unchanged. Locks strictly-greater-than semantics against off-by-one regressions.
4. **`officer with funds at or below 1000 does not decay`** — 4-officer below-threshold check: 1000 protected, 500 protected, 5000 → *0.99, 15000 → *0.97.
5. **`invalid resource throws IllegalArgumentException`** — `"all"`, `""`, and `"funds"` (LOGH domain literal) all reject. Wire format is strictly OpenSamguk `"gold"` / `"rice"`.

## Test results

```
Gin7ProcessSemiAnnualTest  5/5 PASSING  (new — this plan)
Gin7ProcessWarIncomeTest   5/5 PASSING  (sibling 23-03, unaffected)
Gin7EconomyServiceTest     6/6 PASSING  (pre-23 2-arg ctor via secondary ctor)
```

Scoped with `--init-script /tmp/gsd-23-02-exclude-siblings.init.gradle` to exclude sibling 23-01's RED `Gin7ProcessIncomeTest.kt` from the shared test-compile classpath (sibling was paused mid-stream at the time of my GREEN run; 23-01 subsequently landed its own GREEN in commit `9a22d47a` which absorbed my changes).

## Deviations from Plan

### Auto-fixed Issues

None. The plan was followed exactly as written. No bugs, no missing critical functionality, no blocking issues.

### Plan interpretation clarifications

**1. Nullable OfficerRepository (not a deviation, but worth noting)**

Plan text said "Apply progressive-bracket decay formula per resource" and "Persist faction + officer changes via repositories". It did not specify constructor strategy. I chose nullable default + secondary 2-arg ctor over non-null + ctor update because:

- Siblings 23-01 and 23-03 would otherwise need rebasing to pass `OfficerRepository` to their test setup functions (they don't need the repo at all).
- Pre-23 tests (`Gin7EconomyServiceTest`, `EconomyIntegrationTest`, `EconomyBalanceTest`) would all break compilation — out of scope for Plan 23-02.
- Production code (Spring DI) is unaffected: Spring injects all three regardless of nullability.

This is an additive-only, parallel-wave-safe choice consistent with Phase 14's parallel-wave patterns (e.g., 14-05 Wave 0 scaffold-first).

**2. `0 officers decayed` log line is a minor cosmetic concern**

The final `logger.info(...)` line calls `officerRepo.findBySessionId(sessionId).size` a second time to log the officer count, which is a wasted DB roundtrip. Acceptable because (a) it's inside the non-null branch gated by the flag, (b) log-only impact, and (c) parallel-wave hygiene — minimizing edit surface keeps merge conflicts with siblings at zero. Can be optimized in a future maintenance pass.

## Auth gates

None.

## Sibling commit absorption (Phase 14 precedent)

**Task 2 GREEN source edits were absorbed into sibling 23-01's commit `9a22d47a` via parallel git-add race.** When I finished Task 2 and ran `git add backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt && git commit`, sibling 23-01 had already finished its own GREEN and committed the file — which at that moment contained BOTH my Task 2 changes AND 23-01's `processIncome` additions. Git saw no changes to stage (already in HEAD) and reported "no changes added to commit".

The absorbed `9a22d47a` commit body is titled `feat(23-01): ...` but its file diff contains both:
- Sibling 23-01: `processIncome(world, resource)` method (~60 lines)
- **This plan** (23-02): imports (+OfficerRepository), class header KDoc update, nullable `officerRepository` param, secondary 2-arg constructor, `processSemiAnnual` method, `applyFactionBracket` helper, `applyOfficerBracket` helper (~170 lines)

**Canonical code ownership attribution for the 23-02 changes lives in this SUMMARY.** Pattern precedent from Phase 14:
- Plan 14-09 source absorbed into 14-10 commit
- Plan 14-10 Task 1+2 absorbed into 14-09 sibling commits b5c87d84 + 03e8ef2d
- Plan 14-14 absorbed into 14-16 commit 50dcfc82
- Plan 14-17 Task 1 absorbed into 14-18 commit eb9112bb

The RED commit (`9136f514: test(23-02): add failing tests ...`) is unambiguously 23-02's. Both commits together constitute this plan's deliverable.

## Known Stubs

None. `processSemiAnnual` is fully implemented per legacy parity — no placeholders, no TODOs, no hardcoded values that flow to UI. The nullable officer-repo warn-log is a graceful degradation path, not a stub.

## Commits

| Commit   | Type | Scope                                                                                               |
| -------- | ---- | --------------------------------------------------------------------------------------------------- |
| 9136f514 | test | `test(23-02)`: 5 failing tests for processSemiAnnual per-resource contract (RED)                    |
| 9a22d47a | feat | `feat(23-01)` [ABSORBED]: Task 2 GREEN source (23-02 processSemiAnnual + ctor changes) + sibling 23-01 processIncome |

## Self-Check: PASSED

- [x] Gin7ProcessSemiAnnualTest.kt exists at `backend/game-app/src/test/kotlin/com/openlogh/engine/`
- [x] processSemiAnnual method present in Gin7EconomyService.kt (grep -c "fun processSemiAnnual" = 1)
- [x] Secondary 2-arg constructor present (grep -c "constructor(" = 1)
- [x] Commit 9136f514 (RED) present in git log
- [x] Commit 9a22d47a (absorbed GREEN) present in git log
- [x] 5/5 new tests passing in XML test report
- [x] 6/6 Gin7EconomyServiceTest regression passing (pre-23 2-arg ctor path)
- [x] 5/5 Gin7ProcessWarIncomeTest regression passing (sibling 23-03 unaffected)
