# Phase 5: Modifier Pipeline - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 05-modifier-pipeline
**Areas discussed:** Completeness audit, Verification strategy, Stacking/priority order, Scope boundaries

---

## Completeness Audit (감사 방법)

| Option | Description | Selected |
|--------|-------------|----------|
| PHP 전수 읽기 | legacy-core/ triggerCall 사용처 전체를 읽고 모디파이어 카탈로그를 만든 뒤 Kotlin 코드와 비교 | ✓ |
| 대표 커맨드 점검 | 대표 내정 커맨드 5~10개의 레거시 PHP를 읽어 모디파이어 훅 작동 확인 | |
| 코드 grep + diff | 레거시 PHP에서 onCalcDomestic/triggerCall 패턴을 grep하여 자동 비교 | |

**User's choice:** PHP 전수 읽기
**Notes:** None

---

## Verification Strategy (검증 전략)

| Option | Description | Selected |
|--------|-------------|----------|
| 모디파이어 단위 | 각 모디파이어별 DomesticContext 입력/출력 golden value 테스트 | |
| 커맨드 통합 테스트 | DomesticCommand.run() 통진 실행하여 최종 score 값 레거시 비교 | |
| 둘 다 (단위 + 통합) | 모디파이어 단위 golden value + 대표 커맨드 3~5개 통합 파이프라인 검증 | ✓ |

**User's choice:** 둘 다 (단위 + 통합)
**Notes:** None

---

## Stacking/Priority Order (스태킹 순서)

| Option | Description | Selected |
|--------|-------------|----------|
| PHP 순서 확인 후 고정 | 레거시 PHP triggerCall 순서 확인, getModifiers() 순서 일치 검증, 불일치 시 수정 | ✓ |
| 순서 무관 — 결과만 검증 | 순서가 결과에 영향 없다고 가정하고 최종 결과 값만 비교 | |

**User's choice:** PHP 순서 확인 후 고정
**Notes:** None

---

## Scope Boundaries (범위)

| Option | Description | Selected |
|--------|-------------|----------|
| 내정만 집중 | DomesticContext 관련만 검증/수정, stat/war 오류는 기록만 | |
| 발견 시 함께 수정 | PHP 전수 읽기 중 발견되는 stat/war 모디파이어 오류도 즉시 수정 | ✓ |
| 내정 + IncomeContext | DomesticContext와 IncomeContext 모두 검증 | |

**User's choice:** 발견 시 함께 수정
**Notes:** None

---

## Claude's Discretion

- PHP triggerCall 카탈로그 작성 세부 형식
- 단위 테스트 클래스 분할 전략
- 통합 테스트 대상 커맨드 선정
- golden value 추출 방법

## Deferred Ideas

None — discussion stayed within phase scope
