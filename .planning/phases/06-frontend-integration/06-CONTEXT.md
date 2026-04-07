# Phase 6: 프론트엔드 통합 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

은하영웅전설 세계관의 프론트엔드를 전면 재작성한다. 은하맵(도트스타일 성계 아이콘, 진영 색상 5단계 음영), 전술전 UI(도형 아이콘 △□◇, 커맨드레인지서클, 에너지 슬라이더, 정보패널), 전투 접근전 뷰(상하 2분할, React Three Fiber + React Konva), 전략 게임 화면(직무권한카드 탭, 동스폿 캐릭터), 커맨드 실행 UI, 함대/행성 관리 UI, 정치 UI를 구현하고, 삼국지 UI 잔재를 완전 제거한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion.

Key references:
- `docs/REWRITE_PROMPT.md` — 프론트엔드 전면 재작성 상세 스펙
- gin7 매뉴얼 Chapter 2: 게임 화면과 조작방법 (pages 18-25)
- 기존 React Konva 은하맵 — 확장/교체
- 기존 Three.js/React Three Fiber — 전투 뷰 활용
- WebSocket/STOMP 구독 — 백엔드 Phase 2-5 시스템 연결

UI 스타일:
- 진영 색상: 제국=#4466ff(파랑), 동맹=#ff4444(빨강)
- 도트스타일 유닛 아이콘: △기함, □전함/순양함, ◇구축함
- 전술전: 상하 2분할 (상단 3D 연출뷰 + 하단 2D 전술맵)
- 모든 UI 텍스트 한국어

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- React Konva 은하맵 (frontend/src/components/)
- Three.js / React Three Fiber 3D 인프라
- Zustand 상태관리 stores
- WebSocket/STOMP 클라이언트 (@stomp/stompjs)
- Radix UI 컴포넌트 라이브러리
- Tailwind CSS 스타일링

### Integration Points
- WebSocket: /topic/world/{sessionId}/events, /tactical-battle/{battleId}
- REST API: /api/{sessionId}/battles/, /api/warehouse/, /api/command/
- Phase 2 커맨드 실행 채널: /app/command/{sessionId}/execute
- Phase 3 전술전 채널: /app/battle/{sessionId}/{battleId}/energy, /stance, /unit-command

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped. Refer to REWRITE_PROMPT.md frontend specs.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>
