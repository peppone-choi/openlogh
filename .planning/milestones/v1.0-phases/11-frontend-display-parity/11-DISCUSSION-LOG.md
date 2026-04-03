# Phase 11: Frontend Display Parity - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-03
**Phase:** 11-frontend-display-parity
**Areas discussed:** 검증 방식, 배틀 로그 표시, 누락 필드 처리, 플랜 분할

---

## 검증 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 필드 감사 + Vitest | 레거시 API 응답 필드를 목록화하고 현재 타입/컴포넌트와 1:1 대조. 누락 필드는 Vitest로 검증 | ✓ |
| Playwright E2E 스크린샷 | 레거시 UI 스크린샷과 현재 UI를 시각적으로 비교. 전체 스택 구동 필요 | |
| 수동 체크리스트 | 레거시 화면별 표시 필드 목록을 문서화하고 수동으로 확인 | |

**User's choice:** 필드 감사 + Vitest
**Notes:** None

### 검증 범위

| Option | Description | Selected |
|--------|-------------|----------|
| FE 요구사항 4개만 | FE-01~04 대상만: 대시보드, 장수 상세, 국가 관리, 배틀 로그 | |
| 전체 40+ 라우트 전수 감사 | 모든 게임 페이지(auction, betting, diplomacy, vote 등 포함)를 레거시와 비교 | ✓ |
| FE-01~04 + 핵심 하위페이지 | 4개 요구사항 대상 + 직접 관련된 하위 페이지(city 상세, troop 등)도 포함 | |

**User's choice:** 전체 40+ 라우트 전수 감사
**Notes:** None

### 테스트 깊이

| Option | Description | Selected |
|--------|-------------|----------|
| 타입/필드 존재 검증 | 각 컴포넌트가 레거시 필드를 렌더링하는지 확인 (props 확인 + 누락 필드 발견) | |
| 계산값 정확성까지 | 타입 검증 + 계산된 값(전투력, 보너스 적용 스탯 등)이 백엔드와 일치하는지까지 검증 | ✓ |
| 렌더링 + 스냅샷 | 컴포넌트 렌더링 결과의 스냅샷 테스트로 시각적 회귀 방지 | |

**User's choice:** 계산값 정확성까지
**Notes:** None

---

## 배틀 로그 표시

| Option | Description | Selected |
|--------|-------------|----------|
| 새 컴포넌트 구현 | 레거시 배틀 로그 포맷(color tag, 데미지값, 트리거 활성화 등)을 매칭하는 전용 컴포넌트 새로 작성 | ✓ |
| 기존 record-zone 확장 | 이미 있는 record-zone.tsx를 확장해서 배틀 로그도 표시하도록 개조 | |
| 검증만 + 구현 보류 | 레거시 배틀 로그 포맷만 분석/문서화하고, 실제 컴포넌트 구현은 별도 Phase로 | |

**User's choice:** 새 컴포넌트 구현
**Notes:** None

### Color Tag 렌더링

| Option | Description | Selected |
|--------|-------------|----------|
| 레거시 색상 완전 재현 | PHP color tag를 파싱해서 동일한 색상으로 렌더링. 기존 formatLog.ts 기반 확장 | ✓ |
| 단순화된 색상 매핑 | color tag를 Tailwind 클래스로 매핑하되 정확한 색상 일치는 요구하지 않음 | |
| Claude 재량 | formatLog.ts 기존 구현을 기반으로 Claude가 최선의 방식 판단 | |

**User's choice:** 레거시 색상 완전 재현
**Notes:** None

---

## 누락 필드 처리

| Option | Description | Selected |
|--------|-------------|----------|
| 즉시 추가 | 누락 필드 발견 시 백엔드 DTO 확인 → 프론트엔드 타입/컴포넌트에 추가. 이 Phase에서 바로 반영 | ✓ |
| 갭 문서화 + 별도 처리 | 누락 필드를 목록화만 하고 실제 추가는 후속 작업으로 미룸 | |
| 백엔드에 있는 것만 추가 | 백엔드 DTO에 이미 있는 필드만 프론트에 추가. 백엔드에 없는 필드는 갭만 기록 | |

**User's choice:** 즉시 추가
**Notes:** None

### 백엔드에도 없는 필드 처리

| Option | Description | Selected |
|--------|-------------|----------|
| 백엔드까지 풀스택 추가 | 레거시에 있는 필드면 BE DTO → FE 타입 → 컴포넌트 전체 파이프라인 추가 | ✓ |
| FE만 + 백로그 | 프론트에서 placeholder/stub으로 표시하고 백엔드 추가는 별도 백로그로 기록 | |
| 갭 문서화만 | 백엔드 누락 필드 목록만 작성하고 구현은 하지 않음 | |

**User's choice:** 백엔드까지 풀스택 추가
**Notes:** None

---

## 플랜 분할

| Option | Description | Selected |
|--------|-------------|----------|
| 감사/목록화 → 구현/테스트 | Plan 1: 전수 필드 감사 + 누락 목록화. Plan 2: 누락 추가(BE+FE) + 배틀로그 + Vitest | ✓ |
| 페이지별 분할 | Plan 1: 대시보드+장수 상세 감사/구현. Plan 2: 국가 관리+배틀로그 감사/구현 | |
| 3플랜 분할 | Plan 1: 전체 필드 감사. Plan 2: 누락 필드 추가(BE+FE). Plan 3: 배틀로그 + Vitest | |

**User's choice:** 감사/목록화 → 구현/테스트
**Notes:** None

---

## Claude's Discretion

- 40+ 라우트 감사 시 우선순위 결정
- 배틀 로그 컴포넌트의 세부 구조
- Vitest 테스트 파일 구조
- 레거시에만 있고 opensamguk에서 의미 없는 필드 스킵 여부

## Deferred Ideas

None — discussion stayed within phase scope
