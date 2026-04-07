# Phase 7: 시나리오 데이터 + 밸런싱 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

10개 시나리오 초기 데이터(인물 배치, 함대 편성, 행성 상태)를 완비하고, LOGH 원작 캐릭터 데이터(8스탯/성격/계급/직무카드)를 입력하며, 커스텀 캐릭터 생성 시스템(8스탯 배분/진영 선택)을 구현한다. 88 서브타입 전투 시뮬레이션, CP 비용/대기시간, 경제 순환 밸런스를 검증한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion.

Key references:
- `docs/scenarios.json` — 10개 시나리오 데이터
- `docs/reference/scenarios_detail.md` — 시나리오 상세 설명
- `docs/star_systems.json` — 80개 성계 데이터
- gin7 매뉴얼 — 초기 직무권한카드 보유 정보 (pages 55-68)
- gin7 매뉴얼 — 부대 초기 배치 정보 (page 78)
- `backend/shared/src/main/resources/data/commands.json` — CP 비용/대기시간
- `backend/shared/src/main/resources/data/ship_stats_empire.json` / `ship_stats_alliance.json`

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `scenarios.json` — 10개 시나리오 기본 데이터 이미 존재
- `scenarios_detail.md` — 시나리오 상세 설명
- `star_systems.json` — 80개 성계 좌표/항로
- SessionLifecycleService — 세션 생성/시작
- VictoryService — 승리 조건 3종 + 4등급 평가

### Integration Points
- 시나리오 시작 시 Officer/Fleet/Planet 초기 데이터 로드
- 커스텀 캐릭터 생성 → Officer 엔티티 생성
- 밸런스 테스트 → Phase 1-5 시스템 통합 검증

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>
