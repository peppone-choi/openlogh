# Phase 11: 전술 AI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-08
**Phase:** 11-tactical-ai
**Areas discussed:** 작전 목적 기본 행동, 성격별 전투 스타일, AI 재평가 주기, 에너지/진형/태세 자동 조정

---

## 작전 목적 기본 행동

### 점령(Conquest) 행동

| Option | Description | Selected |
|--------|-------------|----------|
| 적 무시 직진 | 목표 우선, 적 무시하고 행성으로 직진 | |
| 교전 후 이동 | 교전 우선, 싸운 후 다시 행성으로 이동 | |
| 판단형 우회 | 약한 적만 교전, 강한 적은 우회 | ✓ |

**User's choice:** C — 판단형 우회
**Notes:** 위협 평가를 역으로 사용하여 낮은 위협 = 교전, 높은 위협 = 우회

### 방어(Defense) 행동

| Option | Description | Selected |
|--------|-------------|----------|
| 좌표 고정 | 지정 좌표에 고정, 접근 적만 공격 | |
| 범위 순찰 | 행성 궤도 주변 일정 범위 순찰 | |
| 요격-복귀 | 적 접근 시 요격 후 원위치 복귀 | ✓ |

**User's choice:** C — 요격 후 원위치 복귀
**Notes:** anchor point 패턴으로 원위치 좌표 기억

### 소탕(Sweep) 행동

| Option | Description | Selected |
|--------|-------------|----------|
| 최근접 추격 | 가장 가까운 적 유닛 추격 | |
| 약한 적 우선 | 가장 약한 적 우선 추격 | |
| 위협 평가 기반 | 가장 위험한 적 우선 추격 | ✓ |

**User's choice:** C — 위협 평가 기반
**Notes:** None

---

## 성격별 전투 스타일

### 교전 거리/대상/진형 차등

| Option | Description | Selected |
|--------|-------------|----------|
| 교전 거리만 차등 | 성격별 선호 교전 거리만 다름 | |
| 대상 선택만 차등 | 교전 거리 동일, 공격 대상만 다름 | |
| 전면 차등 | 교전 거리 + 대상 + 진형 모두 다름 | ✓ |

**User's choice:** C — 전면 차등
**Notes:** None

### 퇴각 임계값

| Option | Description | Selected |
|--------|-------------|----------|
| 고정 임계값 | HP<20%, 사기<30% 전 성격 동일 | |
| 성격별 차등 | AGGRESSIVE HP<10%, CAUTIOUS HP<30% 등 | ✓ |
| gin7 원작 방식 | 원작 기준 별도 방식 | |

**User's choice:** B — 성격별 차등
**Notes:** AGGRESSIVE는 끝까지 버팀, CAUTIOUS는 일찍 퇴각

---

## AI 재평가 주기

### 정기 재평가 간격

| Option | Description | Selected |
|--------|-------------|----------|
| 매 틱 | 가장 반응적, 연산 부하 높음 | |
| 10틱마다 | 1초에 1회, 적당한 반응성 | ✓ |
| 30틱마다 | 느리지만 지휘관 판단 지연 느낌 | |

**User's choice:** B — 10틱마다
**Notes:** None

### 급변 상황 반응

| Option | Description | Selected |
|--------|-------------|----------|
| 이벤트 트리거 즉시 | 주기와 관계없이 즉시 재평가 | ✓ |
| 다음 정기 재평가 | 지연 허용 | |

**User's choice:** A — 즉시 재평가
**Notes:** 기함 격침, 아군 전멸 등 급변 상황

---

## 에너지/진형/태세 자동 조정

### 에너지 배분 기준

| Option | Description | Selected |
|--------|-------------|----------|
| 거리 기반 | 원거리→BEAM, 근접→GUN, 피격→SHIELD | |
| HP 기반 | HP 높으면 공격, 낮으면 방어 | |
| 거리+HP 복합 | 두 요소 모두 고려 | ✓ |

**User's choice:** C — 복합 판단
**Notes:** None

### 진형 자동 변경

| Option | Description | Selected |
|--------|-------------|----------|
| 상황별 최적 | 상황만으로 자동 선택 | |
| 작전 목적별 고정 | 점령→紡錘, 방어→混成 등 | |
| 성격+상황 복합 | 성격 선호 + 상황 변경 가능 | ✓ |

**User's choice:** C — 성격 + 상황 복합
**Notes:** AGGRESSIVE→紡錘, DEFENSIVE→混成, CAUTIOUS→艦種 등 선호 진형

### 집중/분산 공격

| Option | Description | Selected |
|--------|-------------|----------|
| 적 수 기반 | 적 소수→집중, 다수→분산 | |
| 위협 평가 기반 | 고위협 집중, 저위협 분산 | ✓ (복합) |
| 작전 목적 연동 | 소탕→분산, 점령/방어→집중 | ✓ (복합) |

**User's choice:** B + C — 위협 평가 기반 + 작전 목적 연동
**Notes:** 소탕은 분산 경향, 점령/방어는 집중 경향이되 위협 평가로 최종 결정

---

## Claude's Discretion

- AI 내부 구현 패턴 선택
- 위협 평가 공식 구체적 가중치
- 재평가 이벤트 트리거 목록
- 테스트 전략

## Deferred Ideas

- 성계-행성 2계층 구조 (별도 마일스톤)
