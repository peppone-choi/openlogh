# Phase 9: 지휘권 분배 + 커맨드레인지서클 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

사령관이 함대(최대 60유닛)를 분함대로 나누어 부사령관/참모에게 배정하고, 커맨드레인지서클(CRC) 내 유닛에만 명령이 전달되며, 통신 방해가 지휘 체계에 영향을 미친다. 지휘권 우선순위(온라인→계급→평가→공적)가 자동 적용된다.

Requirements: CMD-01, CMD-02, CMD-03, CMD-04, CMD-05, CMD-06

</domain>

<decisions>
## Implementation Decisions

### 분함대 배정 메카닉
- **D-01:** 개별 ShipUnit 단위로 분함대에 배정한다. 현재 SubFleet.unitFleetIds(List<Long>)를 ShipUnit ID 기반으로 변경해야 한다.
- **D-02:** 분함대 크기는 무제한이다. 60유닛 범위 내에서 사령관이 자유롭게 분배한다 (최소 1유닛).
- **D-03:** 분함대 배정은 전투 전 + 전투 중 모두 가능하다. 전투 중 재배정은 CMD-05 조건(서클 밖 + 정지 유닛)을 충족해야 한다.
- **D-04:** 분함대장은 gin7 10명 슬롯 규칙(CrewSlotRole)에 따라 함대 슬롯에 배정된 장교만 가능하다. UnitCrew 엔티티 기반.

### CRC 명령 전달 규칙
- **D-05:** CRC 반경은 지휘관의 command(지휘) 스탯에 비례한다. command가 높을수록 maxRange와 expansionRate가 증가한다. 현재 CommandRange 구조(currentRange/maxRange/expansionRate) 유지.
- **D-06:** CRC 밖 유닛은 복합 행동한다: 기본은 마지막 명령 유지, HP<30% 위험 상황에서는 AI 자율 퇴각.
- **D-07:** 명령 발령 시 CRC가 0으로 리셋되고 tick마다 확장된다. 명령 빈도와 CRC 크기 간 트레이드오프.
- **D-08:** CRC 내/외는 이진 판정(단순 거리 비교)으로 처리한다. 버퍼 존 없음.

### 지휘권 우선순위 로직
- **D-09:** 우선순위: 온라인 → 계급 → 평가 → 공적. 모든 기준이 동점이면 officerId 오름차순(더 오래된 장교 우선).
- **D-10:** 우선순위 재계산은 이벤트 기반이다. 장교 온라인/오프라인 변경, 부상, 사망 시에만 재계산한다.
- **D-11:** 온라인 플레이어가 NPC/오프라인 플레이어보다 계급 무관하게 우선 배정된다 — gin7 조직 시뮬레이션 철학.

### 통신 방해 시스템
- **D-12:** 통신 방해는 적 장교의 특수 능력 또는 특수장비로 트리거된다 (gin7 기준).
- **D-13:** 통신 방해 시 총사령관의 전군 명령만 차단된다. 분함대장→자기 유닛 명령은 정상 동작한다 (CMD-06 충족).
- **D-14:** 통신 방해 해제 조건: 일정 tick 후 자동 해제, 또는 방해 발동자 격침/퇴각 시 즉시 해제.

### Claude's Discretion
- SubFleet.unitFleetIds → ShipUnit ID 기반으로의 구체적 리팩토링 방식
- CRC 반경의 정확한 수식 (command 스탯 → maxRange/expansionRate 변환 공식)
- HP<30% AI 자율 퇴각의 구체적 퇴각 방향/속도 결정
- 통신 방해 지속 tick 수, 특수 능력/장비 연동 구체적 메카닉
- 테스트 전략 및 구현 순서

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 지휘 계층 데이터 모델 (Phase 8에서 수립)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt` — CommandHierarchy, SubFleet 데이터 클래스, CRC/통신 방해 필드
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — TacticalUnit(commandRange, stance 등), tick 루프, 커맨드 버퍼 처리
- `backend/game-app/src/main/kotlin/com/openlogh/model/TacticalUnitState.kt` — JSONB 직렬화용 유닛 상태 (commandRange 필드들)

### 전투 서비스 및 조직 구조
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — 전투 라이프사이클, activeBattles, buildCommandHierarchy()
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` — 전투 초기화, CommandHierarchy 생성 지점

### 기존 모델
- `backend/game-app/src/main/kotlin/com/openlogh/model/CommandRange.kt` — CRC 반경 모델 (currentRange, maxRange, expansionRate)
- `backend/shared/src/main/resources/data/commands.json` — gin7 81종 커맨드 정의

### 참조 문서
- `docs/REWRITE_PROMPT.md` — 전술전 상세 스펙, CRC 규칙
- `docs/reference/gin4ex_wiki.md` — 전술 모드 상세, 조직 구조
- `docs/reference/unit_composition.md` — 부대 편성 규칙 (함대 10명 슬롯)
- `CLAUDE.md` — 조직 구조(함대/순찰대/수송함대), CrewSlotRole, 계급 시스템

### Phase 8 컨텍스트
- `.planning/phases/08-scenario-character-system/08-CONTEXT.md` — 엔진 통합, 커맨드 버퍼, CommandHierarchy 설계 결정

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CommandHierarchy.kt` — Phase 8에서 수립된 데이터 모델. subCommanders, successionQueue, crcRadius, commJammed 필드 이미 존재
- `SubFleet.kt` — commanderId, commanderName, unitFleetIds, commanderRank. unitFleetIds를 ShipUnit ID 기반으로 변경 필요
- `CommandRange` 모델 — currentRange/maxRange/expansionRate, tick 확장 로직 기반
- `TacticalUnit.commandRange` — 이미 CommandRange 객체 소유, CRC 로직 연결 지점
- `UnitCrew` 엔티티 — CrewSlotRole 기반 함대 슬롯 관리, 분함대장 자격 검증에 활용

### Established Patterns
- 전투 상태는 in-memory(ConcurrentHashMap), 커맨드 버퍼(ConcurrentLinkedQueue)로 동시성 보장
- WebSocket STOMP: /app/battle/{sessionId}/{battleId}/* 채널
- 이벤트 브로드캐스트: GameEventService 통해 클라이언트 알림

### Integration Points
- `TacticalBattleEngine.processTick()` — CRC 검증, 명령 전달 로직 삽입 지점
- `BattleWebSocketController` — 분함대 배정/재배정 커맨드 수신 지점
- `TacticalBattleService.buildCommandHierarchy()` — 지휘권 우선순위 로직 확장 지점

</code_context>

<specifics>
## Specific Ideas

No specific requirements — user selected recommended approaches for most decisions, with one key customization: CRC 밖 유닛의 복합 행동 (마지막 명령 유지 + HP<30% 시 AI 자율 퇴각).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 09-strategic-commands*
*Context gathered: 2026-04-07*
