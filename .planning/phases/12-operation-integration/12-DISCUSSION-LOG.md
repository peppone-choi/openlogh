# Phase 12: 작전 연동 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-08
**Phase:** 12-operation-integration
**Areas discussed:** 작전계획 데이터 모델, 작전-전술전 연동, 공적 보너스 메커니즘, 발령-도달 시작 흐름

---

## 작전계획 데이터 모델

### Q1: OperationPlan을 어떻게 저장할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 새 OperationPlan 엔티티 (Recommended) | 새 테이블 operation_plan + V45 마이그레이션. 쿼리 가능, 외래키 제약, 아키텍처 일관성 | ✓ |
| nation.meta JSONB 확장 | 기존 nation.meta["operationPlan"] 구조를 objective/targetSystemId 등으로 확장. 마이그레이션 불필요, 구조화된 쿼리 불가 | |
| Faction 필드 직접 추가 | Faction 엔티티에 currentOperationObjective 등 직접 추가. 단순하지만 진영당 1개 작전만 가능 | |

**User's choice:** 새 OperationPlan 엔티티
**Notes:** 기존 phase 8 blocker("OperationPlan entity design decision needed: new table vs enriched JSONB")가 해소됨

### Q2: 한 진영이 동시에 여러 개의 작전을 가질 수 있어야 할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 진영당 1개 (Recommended) | gin7 원작처럼 진영당 한 개의 작전만 활성. 단순 | |
| 진영당 동시 여러 개 | 동맹/서부전선/이제르론 등 여러 지역 동시. 복잡도 높음 | ✓ |

**User's choice:** 진영당 동시 여러 개
**Notes:** Recommended 와는 다른 선택 — 멀티 지역 동시 작전이 게임 플레이에 더 중요하다고 판단

### Q3: OperationPlan의 목표는 무엇을 가리켜야 할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 단일 성계 목표 (Recommended) | targetStarSystemId 하나. CONQUEST/DEFENSE/SWEEP 매핑이 명확 | ✓ |
| 성계 집합 목표 | targetStarSystemIds 리스트. 광역 작전 표현 가능 | |
| 자유 목표 | objective 종류만 지정, 구체 대상 없음 | |

**User's choice:** 단일 성계 목표

### Q4: 한 부대가 두 작전에 속하면 어떻게 할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 1부대 = 1작전 (Recommended) | Fleet은 한 번에 하나의 작전. 새 작전 지정 시 기존에서 자동 제외 | ✓ |
| 다중 소속 허용 | Fleet이 여러 작전에 속함. 우선순위 규칙 필요 | |

**User's choice:** 1부대 = 1작전

### Q5: OperationPlan의 추가 필드는?

| Option | Description | Selected |
|--------|-------------|----------|
| scale (1∼7) (Recommended) | 기존 OperationPlanCommand scale 유지. MCP 비용/공적 보너스 레버리지 | ✓ |
| issuedByOfficerId (Recommended) | 발령자 — 히스토리/권한 검증 | ✓ |
| issuedAtTick (Recommended) | 발령 시점 tick. 작전 지속 시간 계산 | ✓ |
| expectedCompletionTick | 예상 완료 — 자동 실패 반등 가능 | ✓ |

**User's choice:** 4가지 모두 선택 (multi-select)

---

## 작전-전술전 연동

### Q6: missionObjective는 언제 주입되어야 할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 전투 초기화 시점 (Recommended) | BattleTriggerService가 전투 생성 시 한 번에 매핑, 이후 불변 | |
| 매 tick 동적 결정 | 매 tick OperationPlan 조회. 변경 즉시 반영, DB 오버헤드 증가 | ✓ |

**User's choice:** 매 tick 동적 결정 (충돌로 재결정됨 — 아래 Q9 참조)

### Q7: OperationPlan에 속하지 않는 Fleet의 기본 행동?

| Option | Description | Selected |
|--------|-------------|----------|
| DEFENSE 기본 (Recommended) | 현 위치 수비, 가장 안전 | |
| SWEEP 기본 | 적극 소탕. 독립 부대 공격적 | |
| 성격 기반 자동 선택 | AGGRESSIVE→SWEEP, DEFENSIVE→DEFENSE 등 성격별 다름 | ✓ |

**User's choice:** 성격 기반 자동 선택

### Q8: TacticalUnit.missionObjective stub 처리?

| Option | Description | Selected |
|--------|-------------|----------|
| stub 필드 유지, 주입 경로만 추가 (Recommended) | TacticalUnit 필드 그대로, BattleTriggerService에서 주입. Phase 11 테스트 무파괴 | |
| stub 제거, 참조 기반으로 교체 | TacticalUnit이 OperationPlan 참조. 더 큰 리팩터 | ✓ |

**User's choice:** stub 제거, 참조 기반으로 교체 (충돌로 재결정됨)

### Q9: ⚠️ 충돌 해결 — TacticalAI 순수성 vs 동적 작전 변경

| Option | Description | Selected |
|--------|-------------|----------|
| BattleTriggerService가 동기화 채널 제공 (Recommended) | TacticalBattleState에 missionObjectiveByFleetId 맵. OperationPlan 변경 시 syncOperationToActiveBattles 호출하여 활성 전투 맵 업데이트. 순수성 + 동적 반영 둘 다 달성 | ✓ |
| 전투 시작 시점에만 주입 (고정) | 한 번 매핑 후 불변. 단순하지만 전투 중 작전 변경 미반영 | |
| TacticalBattleEngine을 hybrid로 전환 | DB 접근 허용. v2.1 Roadmap 결정 재교섭 필요 | |

**User's choice:** BattleTriggerService가 동기화 채널 제공
**Notes:** Q6/Q8의 답변(매 tick DB 조회 + 참조 기반 교체)이 v2.1 Roadmap 결정 "TacticalAI must be pure function operating only on TacticalBattleState — no DB access"와 충돌. 동기화 채널 패턴으로 해소 — 맵은 SoT, TacticalUnit.missionObjective는 read-through 캐시.

---

## 공적 보너스 메커니즘

### Q10: 공적 보너스 계산 방식?

| Option | Description | Selected |
|--------|-------------|----------|
| 고정 적용 배율 (Recommended) | 작전 참가자만 ×1.5. PlanetConquestService 산출값에 곱하기 | ✓ |
| scale 연동 배율 | OperationPlan.scale에 따라 ×1.2 ~ ×2.0 차등. gin7 원작과 일치, 복잡 | |
| 고정 가산점 | +200 고정. 단순하지만 비례 깨짐 | |

**User's choice:** 고정 적용 배율

### Q11: 보너스 지급 시점?

| Option | Description | Selected |
|--------|-------------|----------|
| 전투 종료 직후 (Recommended) | BattleResultService가 종료 시 즉시 적용 | ✓ |
| 작전 완료 시(목표 달성 후) | status=COMPLETED 시 지급. 시차 있음, 의미상 일치 | |

**User's choice:** 전투 종료 직후

### Q12: 전투 중 OperationPlan 철회 시 보너스?

| Option | Description | Selected |
|--------|-------------|----------|
| 아니오, 철회 시 취소 (Recommended) | 동기화 채널이 missionObjectiveByFleetId에서 제거 → 보너스 없음 | ✓ |
| 네, 전투 진입 시점 기준 지급 | 전투 시작 시 snapshot. 자원한 이탈에 불이익 없음 | |

**User's choice:** 아니오, 철회 시 취소

---

## 발령-도달 시작 흐름

### Q13: 도달 감지는 어디서?

| Option | Description | Selected |
|--------|-------------|----------|
| 별도 OperationActivationService (Recommended) | 매 tick PENDING 작전 조회 + fleet planetId 비교. TickDaemon에서 호출. 관심사 분리 | ✓ |
| BattleTriggerService에 통합 | 기존 fleet-by-planet 그룹핑 재활용. 쿼리 중복 제거, Service 두꺼움 | |
| MovementService 후크 | arrived 콜백. 이벤트 드리븐, MovementService 책임 증가 | |

**User's choice:** 별도 OperationActivationService

### Q14: 작전 status 생명주기?

| Option | Description | Selected |
|--------|-------------|----------|
| PENDING→ACTIVE→COMPLETED/CANCELLED (Recommended) | 명확한 4단계. DRAFT 없음 | ✓ |
| DRAFT→PENDING→ACTIVE→COMPLETED/FAILED/CANCELLED | 6단계 더 세밀. DRAFT 필요성 불확실 | |

**User's choice:** PENDING→ACTIVE→COMPLETED/CANCELLED

### Q15: ACTIVE 전이 조건?

| Option | Description | Selected |
|--------|-------------|----------|
| 참가자 중 1부대라도 도달 (Recommended) | 선봉대 도착 즉시 시작. gin7 "함대별 각자 이동"과 일치 | ✓ |
| 참가자 과반수 도달 | 50% 이상. 전력 보장, 대기 시간 발생 | |
| 참가자 전원 도달 | 100%. 가장 보수적, 한 부대 프리즈로 작전 전체 차단 | |

**User's choice:** 참가자 중 1부대라도 도달

### Q16: ACTIVE → COMPLETED 자연 조건?

| Option | Description | Selected |
|--------|-------------|----------|
| 유형별 자연스러운 조건 (Recommended) | CONQUEST=점령, DEFENSE=N틱 안정, SWEEP=적 전멸. 의미 일치 | ✓ |
| 전투 종료 + 아군 승리 공통 | 단일 규칙. 단순하지만 SWEEP 의미 약화 | |
| 수동 종료만 | 자동 완료 없음. OPS-02 보너스 조건 모호 | |

**User's choice:** 유형별 자연스러운 조건

### Q17: OperationPlan은 fleetIds를 어떻게 얻나요?

| Option | Description | Selected |
|--------|-------------|----------|
| OperationPlanCommand arg 확장 (Recommended) | 기존 커맨드에 objective/targetStarSystemId/participantFleetIds 추가. 단일 흐름 | ✓ |
| 별도 OperationAssignFleetCommand | 작전 생성과 함대 할당 분리. 모듈화, 더 많은 조작 | |

**User's choice:** OperationPlanCommand arg 확장

---

## Claude's Discretion

다음은 사용자가 명시적으로 결정하지 않고 planner/researcher 재량으로 남긴 항목:
- TacticalBattleService 동기화 채널 구현 패턴 (Spring ApplicationEvent vs 직접 호출)
- OperationCompletionService 분리 여부
- DEFENSE 작전 안정 N틱 상수 값 (60틱 권장)
- expectedCompletionTick 컬럼의 실제 사용 시점
- 작전 상태 전이 이벤트 발행 여부 (감사 로그용)
- OperationPlan repository 패턴

## Deferred Ideas

- expectedCompletionTick 자동 실패 반등 (이번 페이즈는 컬럼만)
- DRAFT 상태
- 진영 내 다중 작전 우선순위/자원 경쟁
- scale별 차등 보상 배율
- 작전 실패 페널티
- 장교별 동시 작전 한도
- 연쇄 작전 (CASCADE)
- 작전 변경 플레이어 알림 메시지
