# Phase 2: gin7 81종 커맨드 시스템 - Context

**Gathered:** 2026-04-06
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

직무권한카드 기반 81종 gin7 커맨드를 실시간 실행 파이프라인으로 구현한다. 7개 커맨드 그룹(작전 16종, 개인 15종, 지휘 8종, 병참 6종, 인사 10종, 정치 12종, 첩보 14종)을 commands.json 데이터 기반으로 구현하며, 각 커맨드는 CP 차감 → 대기시간 → 실행 → 결과 흐름을 따른다. 삼국지 officerLevel >= 5 권한 우회가 완전히 제거된 상태(Phase 1 완료)에서 PositionCard 게이팅만으로 커맨드 접근을 제어한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — discuss phase was skipped per autonomous workflow. Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions.

Key references:
- `backend/shared/src/main/resources/data/commands.json` — 81종 커맨드 정의 (CP비용, 대기시간, 실행시간, PCP/MCP 구분)
- Phase 1에서 생성된 Gin7CommandRegistry — 82개 stub이 이미 등록됨
- 기존 CommandExecutor의 PositionCard 체크, CP 차감, cooldown 메커니즘 재사용
- 제안 시스템: 하급자가 상급자에게 커맨드 실행을 제안, 승인/거부 처리

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Gin7CommandRegistry.kt` — 82개 stub 등록됨 (Phase 1에서 생성)
- `CommandExecutor.kt` — PositionCard 체크, CP 차감, cooldown 인프라
- `CpService.kt` — PCP/MCP 분리, 회복/대용(2배) 로직
- `PositionCardRegistry.kt` — 82종 PositionCard 정의
- `commands.json` — 81종 커맨드 CP비용/대기시간/실행시간 데이터

### Established Patterns
- 커맨드 실행: CommandRegistry → CommandExecutor → validation → CP deduction → execution
- WebSocket: /app/command/{sessionId}/execute → /topic/world/{sessionId}/events

### Integration Points
- TickEngine의 실시간 tick과 커맨드 대기시간/실행시간 연동
- WebSocket 결과 브로드캐스트
- Officer 엔티티의 PositionCard 필드 연결

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped. Refer to ROADMAP phase description and success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — discuss phase skipped.

</deferred>
