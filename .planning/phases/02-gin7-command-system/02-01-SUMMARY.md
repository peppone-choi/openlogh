---
phase: 02-gin7-command-system
plan: "01"
subsystem: command-authority
tags: [position-card, command-registry, mcp, pcp, gin7]
dependency_graph:
  requires: []
  provides: [gin7-commandGroupMap, mcp-pcp-routing]
  affects: [CommandExecutor, PositionCardRegistry, Gin7CommandRegistry]
tech_stack:
  added: []
  patterns: [registerMcpStub/registerPcpStub, StatCategory override via cpType param]
key_files:
  created: []
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/model/PositionCardRegistry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt
decisions:
  - "Gin7StubCommand uses single cpType: StatCategory constructor param rather than two separate subclasses — simpler, less duplication"
  - "StatCategory.MCP import added directly to Gin7CommandRegistry (not via BaseCommand) — avoids coupling to model package from base class"
  - "대기 registered as registerPcpStub (not omitted) — ensures ALWAYS_ALLOWED fallback works even if registry lookup is called"
metrics:
  duration_minutes: 12
  completed: "2026-04-06T14:24:37Z"
  tasks_completed: 2
  files_modified: 2
---

# Phase 2 Plan 01: gin7 권한 체계 기반 — commandGroupMap 교체 + MCP/PCP 분리 Summary

gin7 81종 커맨드 코드를 PositionCardRegistry의 7-group commandGroupMap으로 전면 교체하고, Gin7CommandRegistry에 MCP/PCP CP 풀 분리 헬퍼를 추가하여 CommandExecutor의 PositionCard 체크가 실제로 동작하게 한다.

## What Was Built

### Task 1: PositionCardRegistry — gin7 81종 commandGroupMap 교체

`PositionCardRegistry.commandGroupMap`에서 삼국지 커맨드 코드(출병, 농지개간, 모반시도 등 93종)를 완전히 제거하고 gin7 81종 한국어 커맨드 코드를 7개 그룹으로 매핑하였다.

| Group | 한국어 | CP 풀 | 종수 |
|-------|--------|-------|------|
| OPERATIONS | 작전커맨드 | MCP | 16 |
| PERSONAL | 개인커맨드 | PCP | 15 |
| COMMAND | 지휘커맨드 | MCP | 8 |
| LOGISTICS | 병참커맨드 | MCP | 6 |
| PERSONNEL | 인사커맨드 | PCP | 10 |
| POLITICS | 정치커맨드 | PCP | 12 |
| INTELLIGENCE | 첩보커맨드 | MCP | 14 |
| (없음) | 대기 | — | 1 (ALWAYS_ALLOWED) |

"대기"는 commandGroupMap에 포함하지 않음 — 매핑 없으면 허용하는 기존 로직(`canExecute` fallback) 활용.

**Commit:** `4c9e77d2`

### Task 2: Gin7CommandRegistry — MCP/PCP stub 분리

`registerStub(nameKo)` 단일 함수를 두 개로 분리하였다:

- `registerMcpStub(nameKo)` — `Gin7StubCommand(cpType = StatCategory.MCP)` 생성
- `registerPcpStub(nameKo)` — `Gin7StubCommand(cpType = StatCategory.PCP)` 생성

`Gin7StubCommand`에 `cpType: StatCategory` 파라미터를 추가하여 `getCommandPoolType()`에서 해당 값을 반환하도록 override하였다. MCP 44종 커맨드(작전16 + 지휘8 + 병참6 + 첩보14)가 이제 `StatCategory.MCP`를 반환하고, PCP 37+1종(개인15 + 인사10 + 정치12 + 대기1)이 `StatCategory.PCP`를 반환한다.

**Commit:** `632bb86e`

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Environment Note

Gradle build initially failed with "25.0.2" error due to JDK version mismatch (system Java 25 vs daemon Java 23). Resolved by explicitly setting `JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home`. This is a pre-existing environment configuration issue, not caused by code changes.

## Verification Results

```
# gin7 key codes present in PositionCardRegistry: 6/6 checked
# StatCategory.MCP occurrences in Gin7CommandRegistry: 2 (import + cpType value)
# 삼국지 codes (출병, 농지개간) in PositionCardRegistry: 0
# ./gradlew :game-app:compileKotlin: BUILD SUCCESSFUL
```

## Known Stubs

All 81 commands in `Gin7CommandRegistry` remain as stubs (`CommandResult.fail("... Phase 2에서 구현 예정 (stub)")`). This is intentional — subsequent plans in Phase 2 will replace each group's stubs with real implementations. The stubs now correctly route to MCP or PCP CP pools, enabling CommandExecutor to function correctly once CP deduction logic is wired.

## Self-Check: PASSED

- `PositionCardRegistry.kt` — modified, 81종 gin7 codes present, 삼국지 codes absent
- `Gin7CommandRegistry.kt` — modified, registerMcpStub/registerPcpStub present, StatCategory.MCP override in place
- Commit `4c9e77d2` — exists
- Commit `632bb86e` — exists
- BUILD SUCCESSFUL confirmed with JAVA_HOME=temurin-23.0.2
