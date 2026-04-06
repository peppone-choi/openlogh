# Phase 3: 실시간 전술전 엔진 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning
**Mode:** Auto-generated (discuss skipped via autonomous workflow)

<domain>
## Phase Boundary

gin7 전술전 엔진을 완성한다. 에너지 배분(6채널), 무기 시스템(빔/건/미사일/전투정), 진형(방추/함종별/혼성/삼열), 커맨드레인지서클, 색적, 요새포(토르해머/가이에스하켄), 지상전(육전대 강하, 30유닛 제한), 행성 점령(6종), 태세(4종), 전사/부상 시스템을 구현한다. 기존 TacticalBattleEngine.kt를 확장하며, Phase 1에서 생성된 ShipUnit 엔티티와 Phase 2의 커맨드 시스템을 활용한다.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — discuss phase was skipped per autonomous workflow.

Key references:
- gin7 매뉴얼 Chapter 4: 전술게임 (pages 45-54)
- `docs/REWRITE_PROMPT.md` — 전술전 상세 스펙
- `docs/reference/gin4ex_wiki.md` — 전술 모드 상세 (12턴 구성, 전술 턴 규칙)
- 기존 `TacticalBattleEngine.kt` / `TacticalBattleService.kt` — 확장 대상
- `ship_stats_empire.json` / `ship_stats_alliance.json` — 88 서브타입 전투 스탯
- `ground_unit_stats.json` — 육전병 스탯

핵심 메카닉스:
- 전투 개시: 같은 그리드에 적아 유닛 공존 시 자동 개시
- 에너지: BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR 총합 100
- 무기: 빔(중근거리, 70% 사거리에서 최대), 건(중근거리), 미사일(원거리, 물자소비), 전투정(속도저하)
- 진형: 방추(공격↑)/함종별/혼성/삼열(방어↑)
- 커맨드레인지서클: 시간경과로 확대, 발령시 0 리셋, 지휘 스탯으로 확대율
- 색적: SENSOR 배분 + 유닛 성능 + 거리/종별 정밀도
- 요새포: 사선 통과 전 유닛 명중, 아군 피격 가능, 에너지 충전 후 자동 발사
- 지상전: 육전대 강하 → 지상전 박스(30유닛) → 행성타입별 참가 유닛
- 점령: 항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동 (각각 다른 결과)
- 태세: 항행/정박/주류/전투
- 전사/부상: 기함 격침→부상→귀환성 워프

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TacticalBattleEngine.kt` — 기존 전술전 기초 (확장 대상)
- `TacticalBattleService.kt` — ConcurrentHashMap<Long, TacticalBattleState> in-memory 관리
- `BattleTriggerService.kt` — 전투 개시 조건 감지
- `ShipUnit.kt` / `ShipStatRegistry.kt` — Phase 1에서 생성된 함종 스탯 시스템
- `EnergyAllocation.kt` — 6채널 에너지 모델 (이미 존재)
- `Formation.kt` — 4종 진형 enum (이미 존재)
- `UnitStance.kt` — 4종 태세 enum (이미 존재)
- `DetectionInfo.kt` — 색적 정보 모델 (이미 존재)

### Integration Points
- WebSocket: /topic/world/{sessionId}/tactical-battle/{battleId}
- TickEngine: 전투 틱 처리
- Phase 2 커맨드: 전술 유닛 커맨드 (이동/공격/사격 등)

</code_context>

<specifics>
## Specific Ideas

No specific requirements — discuss phase skipped. Refer to ROADMAP phase description, success criteria, and gin7 manual Chapter 4.

</specifics>

<deferred>
## Deferred Ideas

None — discuss phase skipped.

</deferred>
