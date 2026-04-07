---
gsd_state_version: 1.0
milestone: v2.1
milestone_name: 전술전 지휘체계 + AI
status: defining-requirements
stopped_at: null
last_updated: "2026-04-07"
last_activity: 2026-04-07
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-04-07)

**Core value:** gin7 조직 시뮬레이션 — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행
**Current focus:** Milestone v2.1 — 전술전 지휘체계 + AI

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-04-07 — Milestone v2.1 started

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: -
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**

- Last 5 plans: none yet
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

- [v2.0-Roadmap]: Phase 4 (Economy) can run in parallel with Phase 3 (Battle) — both depend only on Phase 2
- [v2.0-Phase 01]: ShipUnit shipClass/shipSubtype stored as String enum names for schema evolution flexibility
- [v2.0-Phase 01]: ShipStatRegistry parses nested JSON (shipClasses[].subtypes[]) into flat subtype key map via buildSubtypeKey()
- [v2.0-Phase 02]: Gin7StubCommand uses single cpType: StatCategory constructor param
- [v2.0-Phase 02]: positionCards is MutableList<String> (JSONB) not comma-separated String
- [v2.0-Phase 02]: CommandProposalService: uses currentYear for both year and startYear in CommandEnv
- [v2.0-Phase 03]: BattleWebSocketController uses officerId in payload (not OfficerPrincipal)
- [v2.0-Phase 03]: UnitStance.defenseModifier: NAVIGATION=1.0, ANCHORING=1.1, STATIONED=1.3, COMBAT=0.9
- [v2.0-Phase 03]: DetectionService wraps DetectionEngine — precision>=0.5 OR 2+ detectors for confirmation
- [v2.0-Phase 03]: isFlagship cleared on destruction before replacement promotion
- [v2.0-Phase 04]: Tax collection on months 1,4,7,10 only (90-day gin7 cycle)
- [v2.0-Phase 05]: UtilityScorer is pure object (no Spring DI) — stat drivers are static, personality weights applied at call time
- [v2.0-Phase 05]: AiCommandBridge uses runBlocking for CommandExecutor.executeOfficerCommand (suspend fun)
- [v2.0-Phase 06]: BattleMap converted from SVG to React Konva for gin7 dot-style rendering
- [v2.0-Phase 06]: Tactical icon rule: isFlagship → △, all others → □, ◇ removed
- [v2.0-Phase 06]: Used REST as authoritative command execution; WebSocket publish is fire-and-forget supplement
- [v2.0-Phase 07]: H2 ddl-auto=create-drop + NON_KEYWORDS로 통합 테스트 DB 설정

### Pending Todos

None yet.

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-04-07
Stopped at: Milestone v2.1 initialization
Resume file: None
