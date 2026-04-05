# Phase 4: Battle Completion - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 04-battle-completion
**Areas discussed:** 도시치료 scope, Formula verification, Siege mechanics, Trigger priority

---

## 도시치료 Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 4에서 구현 (추천) | BATTLE-08로 이미 배정. GeneralTriggerCaller로 구현하되 이 phase에서 처리 | ✓ |
| Phase 9로 이동 | Turn Engine Completion으로 옮김 — preTurn 트리거는 턴 엔진 영역 | |
| 이 페이즈에서 제외 | battle trigger가 아니므로 Phase 4 scope에서 빼고 별도 phase로 관리 | |

**User's choice:** Phase 4에서 구현 (추천)
**Notes:** GeneralTriggerCaller로 구현하되 BATTLE-08 배정 유지

---

## Formula Verification

| Option | Description | Selected |
|--------|-------------|----------|
| 대표 샘플 (추천) | 병종별 대표 조합 + 경계값 테스트. PHP golden value로 검증 | |
| 전수 매트릭스 | 모든 병종 조합(7x7=49쌍)에 대해 각각 PHP golden value 비교 | ✓ |
| Claude 판단에 맡김 | researcher가 process_war.php 분석 후 핵심 분기점을 파악하고 테스트 대상 결정 | |

**User's choice:** 전수 매트릭스
**Notes:** 모든 병종 조합에 대해 철저히 검증

---

## Siege Mechanics

| Option | Description | Selected |
|--------|-------------|----------|
| Golden value 비교 (추천) | PHP에서 공성 시나리오(성벽 데미지, 수비 보너스, 성문 돌파) 각각의 기대값 추출하여 검증 | ✓ |
| 통합 시뮬레이션 | 전체 공성전 플로우를 돌리고 최종 결과(city 점령 여부, 잔여 병력 등)를 PHP와 비교 | |
| 둘 다 | golden value로 개별 공식 검증 + 통합 시뮬레이션으로 end-to-end 확인 | |

**User's choice:** Golden value 비교 (추천)
**Notes:** 개별 공식 단위로 검증

---

## Trigger Priority

| Option | Description | Selected |
|--------|-------------|----------|
| Legacy 코드 순서 (추천) | process_war.php에서 trigger가 등록/호출되는 순서대로 구현. 의존성 문제 자연 해결 | ✓ |
| 난이도 순 | 쉬운 것(부상무효, 도시치료) 먼저 → 복잡한 것(반계, 돌격지속) 나중에 | |
| Claude 판단 | researcher가 의존성 분석 후 최적 순서 결정 | |

**User's choice:** Legacy 코드 순서 (추천)
**Notes:** process_war.php 코드 순서 따름

---

## Claude's Discretion

- 각 트리거의 hook method 선택 (onEngagementStart/onPreAttack/onPostDamage/onPostRound)
- Plan 분할 전략
- 도시치료 GeneralTriggerCaller 인터페이스 설계

## Deferred Ideas

None — discussion stayed within phase scope
