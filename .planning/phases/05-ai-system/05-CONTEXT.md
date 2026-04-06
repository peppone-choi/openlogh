# Phase 5: AI 시스템 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

gin7 기반 NPC/AI 시스템을 구현한다. 성격 기반 행동(5종 PersonalityTrait 가중치 의사결정), 오프라인 플레이어 자동 행동(플레이어 스탯으로 AI 가동), 진영 AI(작전수립/예산 배분/인사 자동 처리), 시나리오 이벤트 AI(쿠데타 조건 감지/내전 트리거)를 구현한다. AI는 Phase 2에서 구현된 81종 커맨드 파이프라인을 통해 행동한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion.

Key references:
- 기존 `PersonalityTrait.kt` — 5종 (AGGRESSIVE/DEFENSIVE/BALANCED/POLITICAL/CAUTIOUS) 이미 존재
- 기존 `OfflinePlayerAIService.kt` — 오프라인 AI 기초 이미 존재
- Research: utility scoring (~50 lines Kotlin), no behavior tree library needed
- AI 처리: 10틱/100틱 인터벌 분산 (진영 AI: factionCount 라운드 로빈, NPC: 100틱 일괄)
- 기존 `FezzanAiService.kt` — 페잔 AI 이미 존재 (유지)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PersonalityTrait.kt` — 5종 성격 enum
- `OfflinePlayerAIService.kt` — 오프라인 AI 기초
- `FezzanAiService.kt` — 페잔 AI
- `NpcPolicy.kt` — NPC 정책 로직
- `CoupPhase.kt` — 쿠데타 단계 enum
- Phase 2 커맨드 파이프라인 — AI가 동일 파이프라인 사용

### Integration Points
- TickEngine: AI 틱 처리 (processPolitics 내)
- CommandExecutor: AI가 커맨드 실행
- Gin7CommandRegistry: 81종 커맨드 접근

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>
