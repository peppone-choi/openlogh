# Phase 12: 작전 연동 - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

전략 게임의 작전계획(OperationPlan)이 전술전 AI 행동을 결정하고, 작전에 참가한 부대만 공적 보너스를 받으며, 발령된 부대가 목표 성계 도달 시 작전이 자동 시작되는 연동 시스템.

Phase 11에서 구현한 TacticalAI는 MissionObjective를 파라미터로 받아 행동하는 순수 함수다. 이 페이즈는 OperationPlan 엔티티 자체와 그것을 TacticalAI의 입력으로 흘려보내는 파이프라인을 만든다.

**범위:**
- OperationPlan 데이터 모델 + V45 마이그레이션
- OperationPlanCommand 확장 (stub → 실제)
- BattleTriggerService에서 작전→missionObjective 주입
- 작전 변경 시 활성 전투 상태 동기화 채널
- 공적 보너스 지급 로직 (BattleResultService)
- OperationActivationService (PENDING → ACTIVE 전이)
- 작전 status 생명주기 + 자연 완료 조건

**범위 제외:**
- 전략 AI가 작전을 자율 수립 (Phase 13 SAI-01/SAI-02)
- 작전계획 UI / 함대 드래그 배정 (Phase 14 FE-02)
- 새로운 작전 유형 추가 (CONQUEST/DEFENSE/SWEEP만)

</domain>

<decisions>
## Implementation Decisions

### 작전계획 데이터 모델 (OPS-01, OPS-02, OPS-03)
- **D-01:** 새 OperationPlan 엔티티 + operation_plan 테이블 + V45 마이그레이션. nation.meta JSONB 확장이 아닌 정식 엔티티.
- **D-02:** 한 진영이 동시에 여러 작전을 가질 수 있다 (동맹/서부전선/이제르론 등 멀티 지역 동시 수행).
- **D-03:** 한 작전은 단일 성계 목표(`targetStarSystemId`). MissionObjective는 그 성계에 대한 행동 — CONQUEST=점령할 성계, DEFENSE=방어할 성계, SWEEP=철수시킬 성계.
- **D-04:** 1 부대 = 1 작전 (배타적). 새 작전 지정 시 기존 작전 participantFleetIds에서 자동 제거.
- **D-05:** OperationPlan 엔티티 필드:
  - `id` (PK), `sessionId`, `factionId`, `name`
  - `objective: MissionObjective` (CONQUEST/DEFENSE/SWEEP)
  - `targetStarSystemId: Long`
  - `status: OperationStatus` (PENDING/ACTIVE/COMPLETED/CANCELLED)
  - `participantFleetIds: List<Long>` (JSONB array column)
  - `scale: Int` (1~7, 기존 OperationPlanCommand scale 유지 — MCP 비용/공적 레버리지)
  - `issuedByOfficerId: Long` (발령자 — 히스토리/권한 검증)
  - `issuedAtTick: Long` (발령 시점)
  - `expectedCompletionTick: Long?` (예상 완료 — 자동 실패 반등 로직 가능, 이번 페이즈에서는 컬럼만 추가, 사용은 추후)

### 작전-전술전 연동 (OPS-01)
- **D-06:** TacticalBattleState에 `missionObjectiveByFleetId: MutableMap<Long, MissionObjective>` 필드 추가. TacticalAI는 이 맵에서 missionObjective를 읽는다 (DB 접근 없음 — 엔진 순수성 유지).
- **D-07:** BattleTriggerService가 전투 생성 시 참가 Fleet들의 OperationPlan을 조회하여 맵을 한 번에 채운다.
- **D-08:** OperationPlan 변경 시(CRUD) 동기화 채널: `TacticalBattleService.syncOperationToActiveBattles(operationPlan)`이 활성 전투의 missionObjectiveByFleetId 맵을 업데이트. Spring 이벤트 또는 명시적 호출 — 구현 패턴은 planner 결정.
- **D-09:** Phase 11의 TacticalUnit.missionObjective 필드는 **stub 유지, 주입 경로만 추가**. (사용자 답변 "참조 기반으로 교체"는 엔진 순수성과 충돌하여 충돌 해결 단계에서 "동기화 채널 제공"으로 재결정됨.) 즉, missionObjectiveByFleetId 맵이 SoT(source of truth)이고 TacticalUnit.missionObjective는 fleetId 기반 조회 결과를 캐시하는 read-through 필드다. 매 tick 시작 시점에 맵에서 갱신.
- **D-10:** OperationPlan에 속하지 않는 Fleet의 missionObjective 기본값은 **성격 기반 자동 선택**:
  - AGGRESSIVE → SWEEP
  - DEFENSIVE → DEFENSE
  - CAUTIOUS → DEFENSE
  - BALANCED → DEFENSE
  - POLITICAL → DEFENSE
  - 매핑 함수는 `MissionObjective.defaultForPersonality(personality)` 헬퍼로 정의.

### 공적 보너스 메커니즘 (OPS-02)
- **D-11:** 고정 적용 배율 — 작전 참가자에게 기본 공적 × 1.5 (50% 추가). PlanetConquestService 등에서 산출된 base merit에 배율만 곱한다.
- **D-12:** 지급 시점은 전투 종료 직후. TacticalBattleEngine이 BattleOutcome을 결정하면 BattleResultService가 missionObjectiveByFleetId에 fleet이 포함된 참가자에게 보너스 적용.
- **D-13:** OperationCancelCommand 실행 또는 작전이 CANCELLED로 전이되면 동기화 채널을 통해 활성 전투의 missionObjectiveByFleetId에서 해당 fleet 제거 → 전투 종료 시점에는 보너스 대상 아님.
- **D-14:** 공적 보너스는 Officer.meritPoints에 직접 누적. PlanetConquestService 패턴(int meritPoints) 재사용.

### 발령-도달 시작 흐름 (OPS-03)
- **D-15:** 별도 OperationActivationService — 매 tick `findByStatus(PENDING)` 후 participantFleetIds의 현재 planetId == targetStarSystemId인 Fleet이 1개라도 있으면 status = ACTIVE 전이. 호출은 TickDaemon 스케줄 안 (BattleTriggerService 직전 권장).
- **D-16:** Status 생명주기: `PENDING → ACTIVE → COMPLETED | CANCELLED`. DRAFT는 도입하지 않음 (OperationPlanCommand가 발령과 동시).
- **D-17:** ACTIVE 전이 조건: 참가자 중 1부대라도 도달. 선봉대가 본대보다 먼저 도달해도 시작 — gin7 원작의 "함대별 각자 이동" 설정과 일치.
- **D-18:** COMPLETED 자연 조건 (유형별):
  - **CONQUEST** → targetStarSystemId의 owningFactionId == operation.factionId (점령 완료)
  - **DEFENSE** → targetStarSystemId에 적 함대 없음 + N틱 안정 (N은 상수, planner 결정 — 60틱 권장)
  - **SWEEP** → targetStarSystemId의 적 함대 모두 격침 또는 퇴각
  - 평가는 OperationActivationService와 동일 tick 루프 또는 별도 OperationCompletionService에서 수행 (planner 결정)
- **D-19:** OperationPlanCommand arg 확장 — `objective`, `targetStarSystemId`, `participantFleetIds`를 받도록 변경. PositionCard 권한(commander 그룹)은 그대로 유지. arg 검증: factionId 일치, fleet 소유 확인, 1부대=1작전 보장.

### Claude's Discretion
- TacticalBattleService 동기화 채널 구현 패턴 (Spring ApplicationEvent vs 직접 호출)
- OperationCompletionService 분리 여부 (별도 vs OperationActivationService 통합)
- DEFENSE 작전 안정 N틱 상수 값 (60틱 권장이지만 planner 튜닝 가능)
- expectedCompletionTick 컬럼의 실제 사용 시점 (이번 페이즈에서는 컬럼만)
- ConvertCommand-style 상태 전이 이벤트 발행 여부 (감사 로그용)
- OperationPlan repository 패턴 (JpaRepository + 커스텀 쿼리)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase 11 산출물 (재사용 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` — CONQUEST/DEFENSE/SWEEP enum (이 페이즈에서 `defaultForPersonality()` 헬퍼 추가 예정)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAI.kt` — missionObjective 기반 행동 결정 로직 (수정 없음, 입력만 변경)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAIRunner.kt` — AI 진입점 (이 페이즈에서 미수정 권장)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAIContext.kt` — anchorX/Y 등 컨텍스트 (DEFENSE 작전과 연관)

### 전술전 엔진/서비스 (확장 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — TacticalUnit, TacticalBattleState 정의 (missionObjectiveByFleetId 맵 추가 위치)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` — 전투 생성 시 OperationPlan 조회 + 맵 주입
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — 동기화 채널 (`syncOperationToActiveBattles`) 추가 위치
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/PlanetConquestService.kt` — 기존 meritPoints 산출 패턴 참조 (작전 보너스 배율 적용 지점)

### 기존 OperationPlan stub (교체 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` — `nation.meta["operationPlan"]` 저장 stub (D-19 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` — meta 제거 stub (CANCELLED 전이로 교체)

### 부대 이동/도달 감지
- `backend/game-app/src/main/kotlin/com/openlogh/engine/map/MovementService.kt` — calculateNextPosition Triple<x,y,arrived> 반환 패턴 (참고용, 이 페이즈는 fleet.planetId 기반 감지)
- Fleet 엔티티의 `planetId` 필드 — 도달 후 갱신 지점 확인 필요

### 공적/계급 시스템
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` — meritPoints 필드
- `backend/game-app/src/main/kotlin/com/openlogh/service/RankLadderService.kt` — meritPoints 활용 패턴
- `backend/game-app/src/main/kotlin/com/openlogh/service/SessionLifecycleService.kt` — 최종 점수 계산에 meritPoints 반영

### 마이그레이션 패턴
- `backend/game-app/src/main/resources/db/migration/` 디렉토리 (V45__create_operation_plan.sql 신규)
- gin7 manual scale 1~7 비용 산식 — `docs/reference/` 또는 `backend/shared/src/main/resources/data/commands.json`

### 프로젝트 가이드
- `CLAUDE.md` — 도메인 매핑, gin7 작전계획 설명
- `.planning/REQUIREMENTS.md` — OPS-01, OPS-02, OPS-03 요구사항 정의
- `.planning/phases/11-tactical-ai/11-CONTEXT.md` — Phase 11 의사결정 (특히 missionObjective stub 의도)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **MissionObjective enum**: 이미 정의됨 — `CONQUEST`, `DEFENSE`, `SWEEP` 한국어명 포함. 헬퍼 함수만 추가하면 됨
- **TacticalUnit.missionObjective 필드**: stub이지만 주입 경로 이미 존재
- **PlanetConquestService 패턴**: type 기반 meritPoints 산출 — 작전 보너스 배율 적용 지점 명확
- **BattleTriggerService.checkForBattles**: fleet planetId 기반 그룹핑 — OperationActivationService와 동일 패턴 재활용 가능
- **OperationPlanCommand**: Position card 권한 게이팅 + MCP 비용 + scale 파라미터 인프라 이미 존재

### Established Patterns
- **Pure object 패턴**: CommandHierarchyService, SuccessionService, OutOfCrcBehavior, TacticalAIRunner 모두 Spring DI 없음. OperationActivationService는 DB 접근 필요하므로 @Service 정상 사용
- **TickDaemon 스케줄링**: BattleTriggerService.checkForBattles가 매 tick 호출되는 패턴 — OperationActivationService도 동일 위치
- **CommandPriorityComparator**: meritPoints/officerLevel/evaluationPoints 우선순위 — 보너스 적용 후에도 그대로 동작
- **JSONB 컬럼**: nation.meta 등 Map<String, Any?> JSONB 사용 패턴 — participantFleetIds도 JSONB List<Long>로 저장 가능
- **V## 마이그레이션**: Flyway, V44까지 사용 중 — V45 신규

### Integration Points
- **TacticalBattleEngine.processTick()**: tick 시작 시점에 missionObjectiveByFleetId → TacticalUnit.missionObjective 동기화 (read-through)
- **BattleTriggerService.checkForBattles()**: TacticalBattle 엔티티 생성 직후 OperationPlanRepository 조회 + map populate
- **TickDaemon 호출 순서**: OperationActivationService.activatePending() → BattleTriggerService.checkForBattles() (도달 인식이 전투 생성보다 먼저)
- **TacticalBattleEngine.checkBattleEnd()** 또는 BattleOutcome 처리: BattleResultService에서 작전 보너스 산출
- **OperationCancelCommand**: 기존 nation.meta 제거 대신 OperationPlanRepository.updateStatus(CANCELLED) + 동기화 채널 호출

</code_context>

<specifics>
## Specific Ideas

- gin7 원작에서 작전계획은 "히스토리에 남는 명령" 성격 — issuedByOfficerId/issuedAtTick 필드는 단순 메타가 아닌 게임 내 서사 요소 ("Reinhard의 해적 토벌 작전")
- COMPLETED 자연 조건이 유형별로 다른 이유: 단순 "전투 승리" 만으로는 SWEEP의 "철저한 소탕" 의미가 약함. 전투는 끝나도 잔존 적이 남으면 작전 미완료
- DEFENSE 안정 N틱 (60틱 ≈ 60 게임초 ≈ 1분 안정) 권장 — 너무 짧으면 적이 잠깐 떠난 사이 작전 종료, 너무 길면 다른 적이 도착해 SWEEP로 전환되는 혼란
- "1 부대 = 1 작전" 보장은 OperationPlanCommand 단계에서 검증 — DB 트리거 대신 어플리케이션 레벨
- 동기화 채널 패턴은 Phase 14 (프론트엔드)가 작전 변경을 실시간 표시할 때도 재활용 가능 (WebSocket 발행 hook)
- Phase 13 (전략 AI)의 SAI-01/02가 이 페이즈의 OperationPlan 엔티티 + OperationPlanCommand를 호출만 하면 되도록 API 설계 — FactionAI에서 호출 가능한 형태로 의식적 노출

</specifics>

<deferred>
## Deferred Ideas

- **expectedCompletionTick 자동 실패 반등**: 컬럼만 추가, 실제 자동 실패 로직은 별도 페이즈/마일스톤
- **DRAFT 상태 (작성 중 작전)**: 이번 페이즈는 즉시 발령 — 추후 UI 단계에서 필요 시 추가
- **작전 우선순위**: 한 진영 다중 작전 시 자원/CP 우선순위 (미반영)
- **작전 별 특수 보상**: scale별 차등 배율, 성공 보상 — 이번 페이즈는 단순 ×1.5
- **작전 실패 페널티**: 실패 시 공적 차감 등 — 미고려
- **장교별 동시 작전 한도**: issuedByOfficerId가 동시에 N개까지 — 이번 페이즈는 무제한
- **연쇄 작전(CASCADE)**: 작전 완료 시 다음 작전 자동 발령 — 미반영
- **작전 변경 알림 메시지**: 플레이어에게 "당신의 함대가 작전 X에 배정되었습니다" — Phase 14 통합

</deferred>

---

*Phase: 12-operation-integration*
*Context gathered: 2026-04-08*
