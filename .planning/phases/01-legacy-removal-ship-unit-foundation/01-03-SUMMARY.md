---
phase: 01-legacy-removal-ship-unit-foundation
plan: "03"
subsystem: economy-engine
tags: [legacy-removal, economy, stub, phase4-prep]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [stubbed-economy-service, gin7-nation-type-modifiers]
  affects: [turn-pipeline, game-app-compile]
tech_stack:
  added: []
  patterns: [stub-with-TODO, phase-gated-implementation]
key_files:
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/NationTypeModifiers.kt
decisions:
  - "Keep updateCitySupply/processDisasterOrBoom/randomizeCityTradeRate/processYearlyStatistics active — not삼국지-specific income logic, still gin7-compatible"
  - "city.commerce field references in supply decay and disaster are gin7 Planet entity fields, not legacy agri/comm calculation logic"
  - "NationTypeModifiers replaced with empty gin7 stubs (empire/alliance/fezzan/rebel) — onCalcIncome/onCalcStat bodies deferred to Phase 4"
  - "Java 23 required for compile (Java 25 incompatible with Gradle 8.x)"
metrics:
  duration: "~8 minutes"
  completed_date: "2026-04-06"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 2
---

# Phase 1 Plan 03: EconomyService 삼국지 로직 제거 Summary

EconomyService에서 삼국지 농업(agri)/상업(comm) 기반 경제 계산 로직을 완전 제거하고, processMonthly/preUpdateMonthly/postUpdateMonthly를 Phase 4 진입점 stub으로 대체. NationTypeModifiers를 gin7 진영 타입(empire/alliance/fezzan/rebel) 구조로 교체.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | EconomyService 삼국지 로직 제거 및 stub 대체 | c01a3595 | EconomyService.kt, NationTypeModifiers.kt |

## What Was Done

### EconomyService.kt

Removed 삼국지-specific private methods (612 lines removed, 73 added):
- `processIncome()` — agri/comm 기반 수입 계산
- `calcCityGoldIncome()`, `calcCityRiceIncome()`, `calcCityWallIncome()` — 도시별 수입 계산
- `processWarIncome()` — 전쟁 수입 처리
- `processSemiAnnual()` — 반기 처리 (인구/인프라 성장)
- `updateNationLevel()` — 삼국지 국력 등급 갱신
- `getDedLevel()`, `getBill()` — 봉급 계산 헬퍼

Stubbed public methods with TODO Phase 4:
- `processMonthly(world)` — 아무 처리도 하지 않음
- `preUpdateMonthly(world)` — 아무 처리도 하지 않음
- `postUpdateMonthly(world)` — 아무 처리도 하지 않음
- `processIncomeEvent(world)` — debug log만
- `processSemiAnnualEvent(world)` — debug log만
- `updateNationLevelEvent(world)` — debug log만

Retained active methods (gin7-compatible):
- `updateCitySupplyState()` / `updateCitySupply()` — 보급선 계산 (지도 연결성 기반)
- `processYearlyStatistics()` — 국력 통계 갱신
- `processDisasterOrBoom()` — 재난/호황 이벤트 (LOGH 세계관으로 텍스트 전환)
- `randomizeCityTradeRate()` — 교역률 무작위화

Removed imports:
- `IncomeContext` — EconomyService에서 더 이상 참조 없음
- `NationTypeModifiers` — EconomyService에서 더 이상 참조 없음
- `kotlin.math.ceil`, `kotlin.math.pow` — 제거된 계산 로직에서만 사용

### NationTypeModifiers.kt

Replaced 25 `che_*` 삼국지 국가타입 entries with 4 gin7 faction stubs:
- `empire` (은하제국)
- `alliance` (자유행성동맹)
- `fezzan` (페잔 자치령)
- `rebel` (반란군)

All modifier bodies are empty (default interface implementations) with TODO Phase 4 comments.

## Deviations from Plan

### Auto-noted

**1. [Rule 2 - Retained] disaster text 삼국지→LOGH 세계관 전환**
- **Found during:** Task 1 (processDisasterOrBoom retained)
- **Issue:** Disaster messages referenced 황건적, 역병 etc. in Chinese context
- **Fix:** Updated message strings to LOGH space context (행성, 항성폭풍, 반란군, etc.)
- **Files modified:** EconomyService.kt

**2. [Compile gate] Java 25 incompatible with Gradle 8.x**
- **Found during:** Verification
- **Issue:** `JAVA_HOME` pointed to Java 25 which is not supported by Gradle 8.12
- **Fix:** Ran compile with `JAVA_HOME` set to Java 23 (`temurin-23.0.2`)
- **Impact:** `./gradlew :game-app:compileKotlin` requires Java 17-23

## Known Stubs

| File | Location | Stub | Reason |
|------|----------|------|--------|
| EconomyService.kt | processMonthly() | Empty body | Phase 4 gin7 economy engine |
| EconomyService.kt | preUpdateMonthly() | Empty body | Phase 4 gin7 economy engine |
| EconomyService.kt | postUpdateMonthly() | Empty body | Phase 4 gin7 economy engine |
| EconomyService.kt | processIncomeEvent() | Debug log only | Phase 4 gin7 economy engine |
| EconomyService.kt | processSemiAnnualEvent() | Debug log only | Phase 4 gin7 economy engine |
| EconomyService.kt | updateNationLevelEvent() | Debug log only | Phase 4 gin7 economy engine |
| NationTypeModifiers.kt | empire/alliance/fezzan/rebel | Empty modifiers | Phase 4 gin7 faction modifiers |

These stubs are intentional — Phase 4 will implement `Gin7EconomyService` to replace them.

## Verification Results

```
grep agri/comm/농업/상업 EconomyService.kt
  → Only comment text and city.commerce field access (gin7 entity field, not legacy calculation)
  → Zero삼국지 income calculation logic

grep "TODO Phase 4" EconomyService.kt
  → 6 occurrences (processMonthly, preUpdateMonthly, postUpdateMonthly, 3 event methods)

JAVA_HOME=.../temurin-23.0.2 ./gradlew :game-app:compileKotlin
  → BUILD SUCCESSFUL
```

## Self-Check: PASSED

Files exist:
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/NationTypeModifiers.kt

Commits exist:
- FOUND: c01a3595
