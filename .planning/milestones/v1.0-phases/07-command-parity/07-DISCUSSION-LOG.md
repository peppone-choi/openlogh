# Phase 7: Command Parity - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 07-command-parity
**Areas discussed:** Verification scope & batching, Constraint parity strategy, Log message & side effect matching, Kotlin-only commands handling

---

## Verification scope & batching

### Q1: 93개 커맨드 검증을 어떻게 배치할까요?

| Option | Description | Selected |
|--------|-------------|----------|
| 카테고리별 순차 | 내정(18) → 군사(18) → 정치(19) → 국가(38) 순서로 Plan 2~3개로 분할. 기존 테스트 파일 구조와 일치 | ✓ |
| 복잡도별 우선순위 | 단순 커맨드 먼저 → 복잡 커맨드 나중에. 빠른 성공 피드백 확보 | |
| 전체 일괄 검증 | 93개 커맨드를 한 번에 전수 읽고 카탈로그 작성 후 일괄 검증 | |

**User's choice:** 카테고리별 순차
**Notes:** 기존 테스트 파일 구조(GeneralCivilCommandTest 등)와 일치하여 자연스러운 분할

### Q2: 기존 테스트 파일 활용 방법

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 테스트 확장 | 기존 테스트 파일에 golden value assertion 추가. PHP 원본에서 기대값 추출 후 기존 구조 보강 | ✓ |
| ��도 parity 테스트 신규 작성 | CommandParityTest.kt 또는 신규 파일에 전용 parity 테스트 작성 | |
| You decide | Claude가 커맨드별 상황에 따라 판단 | |

**User's choice:** 기존 테스트 확장

### Q3: 경제 관련 커맨드 재검증 여부

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 6 결과 신뢰 | Phase 6에서 검증된 경제 커맨드는 재검증 없이 건너뜀 | |
| 회귀 테스트만 추가 | 경제 커맨드도 Phase 7 테스트 스위트에 포함하되, 기존 테스트가 통과하는지만 확인 | ✓ |

**User's choice:** 회귀 테스트만 추가

---

## Constraint parity strategy

### Q1: Constraint 검증 접근법

| Option | Description | Selected |
|--------|-------------|----------|
| 커맨드별 통합 검증 | 각 커맨드의 run() + constraint를 함께 검증. PHP 커맨드를 읽을 때 constraint도 카탈로그화 | |
| Constraint 별도 감사 | Constraint.kt를 별도 감사하여 공통 constraint를 먼저 전수 검증한 후 커맨드별 고유 constraint 검증 | |
| You decide | Claude가 커맨드별 복잡도에 따라 판단 | ✓ |

**User's choice:** You decide
**Notes:** Claude 재량으로 커맨드 복잡도에 따라 통합/별도 접근 결정

---

## Log message & side effect matching

### Q1: 로그 메시지 일치 수준

| Option | Description | Selected |
|--------|-------------|----------|
| 의미적 매칭 | 핵심 정보(장수명, 도시명, 수치, 성공/실패)가 동일하면 OK | |
| 정확 문자열 매칭 | PHP 원본과 byte-level 동일한 로그 문자열. color tag 포함 완전 일치 | ✓ |
| You decide | Claude가 판단 | |

**User's choice:** 정확 문자열 매칭

### Q2: Side effect 검증 방법

| Option | Description | Selected |
|--------|-------------|----------|
| Entity mutation 비교 | run() 전후 entity 상태 차이를 PHP 예상값과 비교 | |
| 로그 + mutation 동시 검증 | CommandResult.logs 정확 매칭 + entity mutation diff 둘 다 검증. 가장 엄격 | ✓ |
| You decide | Claude가 판단 | |

**User's choice:** 로그 + mutation 동시 검증

---

## Kotlin-only commands handling

### Q1: 레거시에 없는 Kotlin-only 커맨드(8개) 처리

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 7 범위 제외 | 레거시 parity 대상이 아니므로 건너뜀. 필요 시 후속 phase에서 처리 | |
| 기본 동작 테스트만 추가 | parity 대상은 아니지만 기본 동작(constraint 통과, entity mutation 정상) 테스트 추가 | ✓ |
| 레거시 재확인 | PHP 코드를 더 깊이 읽어서 이름만 다른 레거시 커맨드가 있는지 재확인 후 결정 | |

**User's choice:** 기본 동작 테스트만 추가

---

## Claude's Discretion

- Constraint 검증 접근법 (통합 vs 별도): 커맨드별 상황에 따라 Claude가 결정
- Plan 분할 구체적 경계
- PHP 코드 읽기 순서 및 카탈로그 정리 형식
- golden value 추출 세부 방식
- Kotlin-only 커맨드 기본 동작 테스트 범위

## Deferred Ideas

None — discussion stayed within phase scope
