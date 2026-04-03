# Phase 9: Turn Engine Completion - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 09-turn-engine-completion
**Areas discussed:** Stub method scope, Turn step ordering, Event/disaster triggers, Plan 분할 전략

---

## Stub Method Scope

| Option | Description | Selected |
|--------|-------------|----------|
| 필수 구현 | 4개 모두 legacy PHP 로직 그대로 구현. updateOnline은 TrafficSnapshotStep과 별도로 per-tick 로직 추가, checkOverhead도 legacy의 guard 로직 이식. | ✓ |
| 선택적 구현 | checkWander + updateGeneralNumber만 실제 구현. updateOnline은 TrafficSnapshotStep으로 대체 확인 후 주석으로 남김, checkOverhead는 아키텍처 차이(JVM vs PHP)로 skip. | |
| Claude 판단 | PHP 코드를 읽고 실제 게임 로직에 영향이 있는지 판단해서 결정. | |

**User's choice:** 필수 구현 (Recommended)
**Notes:** 4개 stub 전부 legacy와 동일하게 구현

---

### checkWander 구현 방식 (Follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| CommandExecutor 경유 | Legacy와 동일하게 che_해산 Command 객체를 생성하고 hasFullConditionMet() 체크 후 run() 실행. 기존 커맨드 시스템 재활용. | ✓ |
| 직접 인라인 | 해산 로직(국가 해체, 장수 재야 전환)을 TurnService에 직접 작성. 커맨드 시스템 의존성 제거. | |

**User's choice:** CommandExecutor 경유 (Recommended)
**Notes:** Legacy PHP와 동일한 패턴으로 che_해산 커맨드 재사용

---

## Turn Step Ordering

| Option | Description | Selected |
|--------|-------------|----------|
| 단계별 순서 assertion | Legacy postUpdateMonthly()의 호출 순서를 문서화하고, Kotlin TurnService의 실제 호출 순서와 1:1 대조. 순서 불일치 발견 시 즉시 수정. | ✓ |
| Golden snapshot 비교 | 동일 입력으로 전체 턴을 돌리고 출력 상태를 PHP와 비교. 순서 문제가 결과에 영향을 주는지 통합 검증. | |
| 병행 접근 | 단계별 순서 assertion + 주요 분기점에 golden snapshot 추가. Phase 6~8 패턴 계승. | |

**User's choice:** 단계별 순서 assertion (Recommended)
**Notes:** None

---

## Event/Disaster Triggers

| Option | Description | Selected |
|--------|-------------|----------|
| 확률 golden value | Legacy PHP의 재해 발생 확률(boomRate, 가뭄/역병 등)을 월별로 추출하고 Kotlin 값과 1:1 assertion. 확률만 맞으면 OK. | |
| 확률 + 효과 검증 | 발생 확률 검증 + 발생 시 도시 상태 변화(pop, agri 등)도 golden value로 검증. 더 철저하지만 범위 넓음. | ✓ |
| Claude 판단 | PHP 코드를 읽고 이미 구현된 부분 vs 누락된 부분을 판별해서 검증 범위 결정. | |

**User's choice:** 확률 + 효과 검증
**Notes:** None

---

## Plan 분할 전략

| Option | Description | Selected |
|--------|-------------|----------|
| 구현→검증 순차 | Plan 1: 4개 stub 구현 + 기본 단위 테스트. Plan 2: turn step ordering 순서 assertion + 재해 확률/효과 golden value 검증. | ✓ |
| 기능별 분할 | Plan 1: checkWander + updateGeneralNumber(게임 로직 영향 높음) + 관련 검증. Plan 2: updateOnline + checkOverhead(운영계) + ordering/event 검증. | |
| Claude 판단 | PHP 코드 복잡도와 의존성을 파악해서 최적 분할 결정. | |

**User's choice:** 구현→검증 순차 (Recommended)
**Notes:** None

---

## Claude's Discretion

- stub 메서드별 구체적 구현 세부사항 (PHP → Kotlin 변환 시 DB 쿼리 패턴 등)
- golden value fixture의 게임 상태 설계
- updateOnline과 TrafficSnapshotStep 간 중복 처리 방식
- checkOverhead의 JVM 환경 적응 범위
- ordering assertion의 구체적 테스트 구조

## Deferred Ideas

None — discussion stayed within phase scope
