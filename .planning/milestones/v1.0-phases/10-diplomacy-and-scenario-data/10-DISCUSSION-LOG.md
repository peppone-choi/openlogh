# Phase 10: Diplomacy and Scenario Data - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 10-diplomacy-and-scenario-data
**Areas discussed:** 외교 상태 검증, 통일/게임종료, 시나리오 데이터 검증, 플랜 분할 전략

---

## 외교 상태 검증

| Option | Description | Selected |
|--------|-------------|----------|
| 레거시 PHP 상태 머신 추출 후 golden value 테스트 | PHP에서 외교 상태 전환 조건을 추출하고 golden value 테스트로 검증 (Phase 9 패턴 재사용) | ✓ |
| 기존 Diplomacy 엔티티/커맨드 코드 대비 PHP 1:1 비교 | 이미 구현된 코드를 레거시와 라인별 비교하여 차이점만 수정 | |
| Claude 재량 | 구현 방식은 Claude가 판단 | |

**User's choice:** 레거시 PHP 상태 머신 추출 후 golden value 테스트
**Notes:** Phase 9의 DisasterParityTest 패턴을 재사용하는 방향

| Option | Description | Selected |
|--------|-------------|----------|
| 모든 타이머 값 검증 | 휴전 쿨다운, 동맹 최소 기간, 선전포고 제한 등 전체 | ✓ |
| 주요 타이머만 검증 | 휴전 쿨다운과 동맹 최소 기간만 | |
| Claude 재량 | 범위를 Claude가 판단 | |

**User's choice:** 모든 타이머 값 검증

| Option | Description | Selected |
|--------|-------------|----------|
| DiplomacyParityTest.kt 새 파일 생성 | qa/parity/에 외교 전용 패러티 테스트 | ✓ |
| 기존 테스트 파일 확장 | NationService 관련 기존 테스트에 추가 | |
| Claude 재량 | 테스트 구조는 Claude가 판단 | |

**User's choice:** DiplomacyParityTest.kt 새 파일 생성

---

## 통일/게임종료

| Option | Description | Selected |
|--------|-------------|----------|
| UnificationCheck 턴 스텝 대비 PHP checkEmperior 1:1 검증 | 이미 있는 턴 스텝만 검증 | |
| 전체 게임종료 조건 매핑 + 테스트 | 통일, 방랑자 타임아웃, 기한 만료 등 전체 | ✓ |
| Claude 재량 | 범위를 Claude가 판단 | |

**User's choice:** 전체 게임종료 조건 매핑 + 테스트

| Option | Description | Selected |
|--------|-------------|----------|
| DiplomacyParityTest.kt에 함께 | 외교 + 게임종료를 같은 파일에 섹션 분리 | |
| GameEndParityTest.kt 별도 파일 | 독립적 관심사이므로 별도 파일 | ✓ |
| Claude 재량 | Claude가 판단 | |

**User's choice:** GameEndParityTest.kt 별도 파일

---

## 시나리오 데이터 검증

| Option | Description | Selected |
|--------|-------------|----------|
| 레거시 PHP 시나리오 파일과 JSON diff 자동화 | 84개 전체 시나리오에 대한 자동 비교 테스트 | ✓ |
| 샘플링 검증 | 주요 10~20개만 선별하여 검증 | |
| Claude 재량 | 검증 범위를 Claude가 판단 | |

**User's choice:** 레거시 PHP 시나리오 파일과 JSON diff 자동화

| Option | Description | Selected |
|--------|-------------|----------|
| 장수 + 도시 + 국가 모두 자동화 | NPC 스탯, 도시 초기값, 국가 관계 모두 diff 자동화 | |
| 장수 스탯만 자동화, 도시/국가는 수동 | 양이 많은 장수만 자동화 | |
| Claude 재량 | Claude가 판단 | ✓ |

**User's choice:** Claude 재량

| Option | Description | Selected |
|--------|-------------|----------|
| ScenarioDataParityTest.kt 새 파일 | qa/parity/에 시나리오 데이터 전용 테스트 | ✓ |
| 기존 ScenarioService 테스트 확장 | 기존 테스트에 추가 | |
| Claude 재량 | Claude가 판단 | |

**User's choice:** ScenarioDataParityTest.kt 새 파일

---

## 플랜 분할 전략

| Option | Description | Selected |
|--------|-------------|----------|
| 2플랜: 외교/게임종료 + 시나리오 데이터 | Plan 1: DIPL-01~03, Plan 2: DATA-01~03 | |
| 3플랜: 외교 + 게임종료 + 시나리오 | Plan 1: DIPL-01,02, Plan 2: DIPL-03, Plan 3: DATA-01~03 | ✓ |
| Claude 재량 | 분할을 Claude가 판단 | |

**User's choice:** 3플랜: 외교 + 게임종료 + 시나리오

| Option | Description | Selected |
|--------|-------------|----------|
| Wave 1: 외교+게임종료 병렬, Wave 2: 시나리오 | 외교와 게임종료는 독립적이라 병렬, 시나리오는 순차 | ✓ |
| 모두 Wave 1 병렬 | 3개 플랜 모두 동시 실행 | |
| Claude 재량 | Claude가 판단 | |

**User's choice:** Wave 1: 외교+게임종료 병렬, Wave 2: 시나리오

---

## Claude's Discretion

- 도시/국가 초기 조건 자동화 범위 (장수 스탯은 자동화 필수, 나머지는 Claude 판단)
- 시나리오 데이터 diff 도구 구현 세부 사항
- 외교 상태 머신 추출 시 어떤 PHP 파일을 읽을지

## Deferred Ideas

None — discussion stayed within phase scope
