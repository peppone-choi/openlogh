# Phase 6: Economy Parity - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 06-economy-parity
**Areas discussed:** 검증 방법론, 레거시 소스 접근, 불일치 발견 시 대응, 검증 범위

---

## 검증 방법론

| Option | Description | Selected |
|--------|-------------|----------|
| Golden value 확장 | 기존 EconomyFormulaParityTest 패턴 확장. 각 공식별 고정 입력으로 PHP 결과 수동 계산 → 기대값 하드코딩. Phase 4/5와 동일한 검증 패턴. | |
| 다턴 시뮬레이션 비교 | Phase 2의 200턴 golden snapshot처럼 장기 시뮬레이션 돌리고 누적 경제 값 비교. 드리프트 감지에 강하지만 더 복잡. | |
| 병행 (단위 + 통합) | 공식별 golden value 단위 테스트 + 다턴 통합 테스트 모두 수행. 가장 철저하지만 작업량 많음. | ✓ |

**User's choice:** 병행 (단위 + 통합)
**Notes:** 공식별 golden value 단위 테스트 + 24턴(2년) 통합 시뮬레이션. 반기 처리 4회 포함.

### Follow-up: 통합 테스트 턴 수

| Option | Description | Selected |
|--------|-------------|----------|
| 12턴 (1년) | 반기 처리 2회 포함. 누적 드리프트 감지 가능하면서 테스트 속도 적정. | |
| 24턴 (2년) | 반기 처리 4회 + 장기 인구 변동 추적. 더 많은 누적 검증. | ✓ |
| Claude 판단에 맡김 | 공식 복잡도에 따라 적절한 턴 수 결정. | |

**User's choice:** 24턴 (2년)

---

## 레거시 소스 접근

| Option | Description | Selected |
|--------|-------------|----------|
| legacy-core 복원 | devsam/core를 다시 clone하여 legacy-core/에 배치. PHP 원본을 직접 읽어 golden value 추출. | ✓ |
| 기존 Kotlin 코드 신뢰 | EconomyService.kt의 공식/주석을 신뢰하고 단위 테스트로 확인. PHP 원본 없이 진행. | |
| 원격 참조만 | storage.hided.net/gitea/devsam/core 원격 서버에서 필요한 파일만 웹으로 읽음. | |

**User's choice:** legacy-core 복원
**Notes:** PHP 원본 코드를 직접 읽어 golden value를 추출하는 것이 정확도 보장.

---

## 불일치 발견 시 대응

| Option | Description | Selected |
|--------|-------------|----------|
| 발견 즉시 수정 | Phase 5 D-04와 동일. 감사 중 불일치 발견 시 즉시 수정하고 테스트 추가. | ✓ |
| 카탈로그 후 일괄 수정 | 전체 6개 공식 감사 완료 → 불일치 목록 작성 → 우선순위별 일괄 수정. | |
| 심각도 기준 구분 | 결과 차이 > 1%이면 즉시 수정, rounding 차이(±1) 수준이면 카탈로그만. | |

**User's choice:** 발견 즉시 수정
**Notes:** Phase 5와 일관된 접근법 유지.

---

## 검증 범위

| Option | Description | Selected |
|--------|-------------|----------|
| ECON-01~06 집중 | EconomyService의 6개 공식 + 재난/호황/국력 계산. 로드맵 목표와 정확히 일치. | |
| 턴 파이프라인 경제 스텝 전체 | 파이프라인 스텝 실행 순서와 조건까지 검증. | |
| 커맨드 내 경제 로직까지 | 무역, 징수 등 개별 커맨드 내 경제 계산도 포함. Phase 7과 범위 겹침 위험. | ✓ |

**User's choice:** 커맨드 내 경제 로직까지

### Follow-up: Phase 7 범위 구분

| Option | Description | Selected |
|--------|-------------|----------|
| 경제 공식만 검증 | 커맨드 내 경제 계산 공식만 검증. constraint/side-effect는 Phase 7. | |
| 커맨드 전체 동작 검증 | 경제 관련 커맨드는 이 Phase에서 완전히 검증. Phase 7에서는 비경제 커맨드만. | ✓ |

**User's choice:** 커맨드 전체 동작 검증
**Notes:** Phase 7에서는 비경제 커맨드만 다루도록 범위 분리.

---

## Claude's Discretion

- golden value 추출 시 PHP 코드 수동 추적 세부 방식
- 단위 테스트 클래스 분할 전략
- 통합 테스트 시나리오 설계
- 경제 관련 커맨드 식별 및 우선순위

## Deferred Ideas

None — discussion stayed within phase scope.
