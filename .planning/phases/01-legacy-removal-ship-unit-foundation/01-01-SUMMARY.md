---
phase: 01-legacy-removal-ship-unit-foundation
plan: 01
subsystem: command
tags: [legacy-removal, gin7, stub, command-registry]
dependency_graph:
  requires: []
  provides: [Gin7CommandRegistry, gin7-command-stubs]
  affects: [CommandExecutor, CommandRegistry]
tech_stack:
  added: []
  patterns: [@Primary Spring DI override, stub-first implementation]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ItemModifiers.kt
  deleted:
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/ (75 files)
    - backend/game-app/src/main/kotlin/com/openlogh/command/nation/ (47 files)
decisions:
  - Gin7CommandRegistry extends CommandRegistry (not interface) to preserve CommandExecutor type injection without changing its constructor
  - 82 stubs registered (81 commands from commands.json + 1 "대기" ALWAYS_ALLOWED)
  - CommandRegistry marked open class to allow Gin7CommandRegistry inheritance
metrics:
  duration: ~20 minutes
  tasks_completed: 2
  files_changed: 125
  completed_date: 2026-04-06
---

# Phase 01 Plan 01: Legacy Command Removal + Gin7 Stub Registry Summary

## One-liner

삼국지 커맨드 122종 구현체 전량 삭제 후 commands.json 기반 gin7 82종 @Primary stub 레지스트리로 교체, BUILD SUCCESS 확인.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Gin7CommandRegistry 생성 (82종 stub) | 71b33092 | Gin7CommandRegistry.kt (created) |
| 2 | 삼국지 커맨드 파일 삭제 + CommandRegistry 비우기 | f06570f0 | 122 files deleted, CommandRegistry.kt, CommandExecutor.kt |

## What Was Built

### Task 1: Gin7CommandRegistry.kt

`Gin7CommandRegistry`를 `CommandRegistry`의 서브클래스로 생성. `@Primary`로 `CommandExecutor`에 자동 주입된다. commands.json의 7개 그룹 81종 커맨드 + "대기" ALWAYS_ALLOWED 커맨드를 stub으로 등록:

- 작전커맨드 (Operations, MCP): 16종
- 개인커맨드 (Personal, PCP): 15종
- 지휘커맨드 (Command, MCP): 8종
- 병참커맨드 (Logistics, MCP): 6종
- 인사커맨드 (Personnel, PCP): 10종
- 정치커맨드 (Politics, PCP): 12종
- 첩보커맨드 (Intelligence, MCP): 14종
- 대기 (standby, ALWAYS_ALLOWED): 1종

모든 stub은 `CommandResult.fail("[$nameKo] Phase 2에서 구현 예정 (stub)")` 반환.

### Task 2: Legacy Command File Deletion

- `command/general/` 75개 파일 전량 삭제 (삼국지 officer 커맨드)
- `command/nation/` 47개 파일 전량 삭제 (삼국지 faction 커맨드)
- `CommandRegistry.kt` init{} 블록 비움 (삼국지 커맨드 등록 0종), `open class`로 변경
- `CommandExecutor.kt` ALWAYS_ALLOWED_COMMANDS: `setOf("휴식", "Nation휴식", "NPC능동", "CR건국", "CR맹훈련")` → `setOf("대기")`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed ItemModifiers.kt parseCrewType return type**
- **Found during:** Task 2 final compile verification
- **Issue:** `parseCrewType` returned `Any?` but `.getAttackCoef()` (method on `CrewType`) was called on it — compile error
- **Fix:** Changed return type to `CrewType?`, added `import com.openlogh.model.CrewType`
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ItemModifiers.kt`
- **Commit:** cdaea2ea

**Note on pre-existing state:** The `ItemModifiers.kt` error was in the working tree (pre-existing unstaged modification) before this plan started. The stash test confirmed the repo compiled cleanly at the previous commit. The fix was required to restore build to SUCCESS state.

### Design Deviation: Interface vs Inheritance

The plan specified creating a `CommandRegistryPort` interface for `Gin7CommandRegistry` to implement. However, `CommandExecutor` injects `CommandRegistry` directly by class type (not interface). Creating an interface would require modifying `CommandExecutor`'s constructor — an architectural change (Rule 4 risk).

**Resolution:** `Gin7CommandRegistry extends CommandRegistry` + `@Primary`. `CommandExecutor` requires no changes. `CommandRegistry` was marked `open class` to enable inheritance. This achieves the same result with zero risk.

### Stub Count: 82 vs 81

The plan specifies 81 stubs. commands.json has exactly 81 commands (16+15+8+6+10+12+14). The "대기" ALWAYS_ALLOWED standby command brings the total to 82 registered stubs. The acceptance criteria `≥81` is satisfied.

## Known Stubs

All 82 commands in `Gin7CommandRegistry` are stubs — intentional. Phase 2 will implement them group by group. They return `CommandResult.fail(...)` and are not wired to any game state.

Remaining legacy references outside `command/` directory (out of scope for this plan):
- `ArgSchemas.kt` — dead schema entries for deleted commands (harmless, Phase 2 will update)
- `PositionCardRegistry.kt`, `CommandService.kt`, `OfficerAI.kt`, `RealtimeService.kt` — reference old command names; to be updated in Phase 2

## Self-Check: PASSED

| Item | Status |
|------|--------|
| Gin7CommandRegistry.kt exists | FOUND |
| CommandRegistry.kt exists | FOUND |
| Commit 71b33092 (Task 1) | FOUND |
| Commit f06570f0 (Task 2) | FOUND |
| Commit cdaea2ea (Rule 1 fix) | FOUND |
| BUILD SUCCESSFUL | PASSED |
