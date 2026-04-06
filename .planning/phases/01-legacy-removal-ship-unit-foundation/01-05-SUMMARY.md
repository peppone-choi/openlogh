---
phase: 01-legacy-removal-ship-unit-foundation
plan: "05"
subsystem: backend/game-app
tags: [ship-stats, registry, service, authority, position-card]
dependency_graph:
  requires: [01-03, 01-04]
  provides: [ShipStatRegistry, ShipUnitService, authority-bypass-removed]
  affects: [CommandExecutor, CommandService, FactionService, FrontInfoService, InMemoryTurnProcessor, UnificationService]
tech_stack:
  added: []
  patterns: [ClassPathResource JSON load via classloader, @PostConstruct registry init, PositionCard-only authority]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/service/ShipStatRegistry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/ShipUnitService.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FactionService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FrontInfoService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/UnificationService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/OfficerLevelModifier.kt
decisions:
  - "JSON structure is nested (shipClasses[].subtypes[]) not flat — buildSubtypeKey() maps classId+subtype to enum name"
  - "ShipSubtype enum stats serve as design reference; ShipStatRegistry JSON is the runtime source"
  - "FactionService chief access returns permission==ambassador only pending Phase 2 PositionCard implementation"
  - "Java 25 incompatible with Gradle 8.12 native-platform — build requires JAVA_HOME pointing to Java 23"
metrics:
  duration_minutes: 12
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_changed: 9
---

# Phase 1 Plan 05: ShipStatRegistry + ShipUnitService + officerLevel bypass 제거 Summary

**One-liner:** JSON-driven ShipStatRegistry로 88 서브타입 스탯 로드, ShipUnitService 스탯 주입 연결, 코드베이스 전체 `officerLevel >= 5` authority bypass 완전 제거 (0건)

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | ShipStatRegistry 생성 | 1148fe52 | ShipStatRegistry.kt (new) |
| 2 | ShipUnitService + officerLevel >= 5 제거 | 9106de50 | ShipUnitService.kt (new), 7 files modified |

## What Was Built

### Task 1: ShipStatRegistry

`backend/game-app/src/main/kotlin/com/openlogh/service/ShipStatRegistry.kt`

- `@PostConstruct fun load()` — 애플리케이션 시작 시 `ship_stats_empire.json`, `ship_stats_alliance.json`, `ground_unit_stats.json` 자동 로드
- JSON 구조: `{ "shipClasses": [ { "classId": "battleship", "subtypes": [ { "subtype": "I", ... } ] } ] }` — 중첩 구조를 `buildSubtypeKey()` 로 flat key로 변환 (`"battleship" + "I"` → `"BATTLESHIP_I"`)
- `getShipStat(subtypeName, factionType)` — faction 우선 조회, fallback 지원
- `ShipSubtypeStat` / `GroundUnitStat` data class 동일 파일 정의
- classloader `getResourceAsStream` 사용 (shared 모듈 리소스 접근)

### Task 2: ShipUnitService

`backend/game-app/src/main/kotlin/com/openlogh/service/ShipUnitService.kt`

- `createShipUnit(sessionId, fleetId, slotIndex, shipSubtypeName, factionType, shipCount)` — ShipUnit 생성 + ShipStatRegistry.getShipStat() 스탯 주입
- `findByFleet()`, `findFlagship()`, `applyBattleDamage()` CRUD 메서드

### officerLevel >= 5 제거 — 7개 파일, 0건 달성

| 파일 | 처리 방식 |
|------|-----------|
| `CommandExecutor.kt` | legacy fallback 블록 제거, PositionCard 체크만 유지 |
| `CommandService.kt` | `hasFactionCardAccess` OR 조건 제거, 필터 조건 제거 (2곳) |
| `FactionService.kt` | chief 접근 체크에서 officerLevel 조건 제거, 중복 직위 해소 블록 제거 (TODO stub) |
| `FrontInfoService.kt` | `calcPermission()` level 2 반환 제거, `calcLeadershipBonus()` 중간 레벨 보너스 제거 |
| `InMemoryTurnProcessor.kt` | `officerLevel >= 5 &&` 조건 제거 |
| `UnificationService.kt` | `officerLevel >= 5 &&` 필터 조건 제거 |
| `OfficerLevelModifier.kt` | KDoc 주석 내 legacy 표현 명확화 |

## Verification

```
grep -rn "officerLevel >= 5" backend/game-app/src/main/kotlin/com/openlogh/ --include="*.kt"
# 결과: 0건

JAVA_HOME=.../temurin-23.0.2 ./gradlew :game-app:compileKotlin
# BUILD SUCCESSFUL
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] JSON 파싱 로직 조정 — 중첩 구조 처리**
- **Found during:** Task 1
- **Issue:** Plan의 예상 JSON 구조(`{ "BATTLESHIP_I": { ... } }`)와 실제 JSON 구조(`{ "shipClasses": [ { "classId": "battleship", "subtypes": [ ... ] } ] }`)가 다름
- **Fix:** `buildSubtypeKey(classId, subtypeStr)` 메서드로 classId + subtype 조합해서 enum 이름 생성, 중첩 리스트 순회 파싱
- **Files modified:** ShipStatRegistry.kt
- **Commit:** 1148fe52

**2. [Rule 3 - Blocking] Java 25 incompatibility — pre-existing build failure**
- **Found during:** Task 1 verification
- **Issue:** 기본 JVM이 Java 25(temurin-25)인데 Gradle 8.12 native-platform이 Java 25와 호환 안 됨 — 이미 Plan 04 이전부터 발생한 pre-existing 문제
- **Fix:** `JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home` 지정해서 빌드
- **Commit:** 해당 없음 (환경 이슈, 코드 변경 없음)

## Known Stubs

| File | Location | Stub | Reason |
|------|----------|------|--------|
| `FactionService.kt` | `isTopSecretAccessible()` | `return officer.permission == "ambassador"` only | Phase 2에서 gin7 faction chief PositionCard 구현 시 교체 |
| `FactionService.kt` | `appointOfficer()` | duplicate rank removal block removed (TODO comment) | Phase 2 PositionCard 시스템에서 직위 중복 관리 |
| `FrontInfoService.kt` | `calcPermission()` | level 2 permission tier removed | Phase 2에서 PositionCard 기반 접근 레벨로 교체 |
| `FrontInfoService.kt` | `calcLeadershipBonus()` | 중간 레벨(8-19) 보너스 제거 | Phase 2에서 PositionCard 기반 보너스로 교체 |

## Self-Check: PASSED

- FOUND: ShipStatRegistry.kt
- FOUND: ShipUnitService.kt
- FOUND: 01-05-SUMMARY.md
- FOUND: commit 1148fe52 (Task 1)
- FOUND: commit 9106de50 (Task 2)
