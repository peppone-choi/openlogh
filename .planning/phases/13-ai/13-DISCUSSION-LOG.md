# Phase 13: 전략 AI - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-09
**Phase:** 13-전략 AI
**Areas discussed:** 전력 평가 모델, 작전 대상 선정 로직, FactionAI 통합 방식, 부대 배정 전략

---

## 전력 평가 모델

### 성계별 전력 평가 단위

| Option | Description | Selected |
|--------|-------------|----------|
| 함대 수 기반 | 성계에 주둔한 함대 수를 진영별로 집계. 간단하고 기존 패턴과 일관됨 | |
| HP 합산 기반 | 성계 주둔 Fleet들의 ships + HP를 합산하여 정밀하게 비교. DB 조회 비용 더 높음 | |
| 복합 스코어링 | 함대 수 + 함선 수 + 사령관 리더십 + 이동 가능 여부를 다차원 점수화 | ✓ |

**User's choice:** 복합 스코어링
**Notes:** 가장 정밀한 평가를 원함

### 적 성계 정보 접근 (안개 전쟁 고려)

| Option | Description | Selected |
|--------|-------------|----------|
| 완전 정보 | 전략 AI는 진영 수준 의사결정 — 모든 성계의 함대 정보를 다 볼 수 있음 | |
| 안개 제한 적용 | 정보(intelligence) 스탯 높은 장교가 있는 진영만 적 정보를 정확하게 평가 | ✓ |
| You decide | Claude가 적절히 판단 | |

**User's choice:** 안개 제한 적용
**Notes:** 전략 수준에서도 정보 비대칭 도입

### 안개 제한 기준

| Option | Description | Selected |
|--------|-------------|----------|
| 진영 최고 intelligence | 진영 내 모든 장교 중 intelligence 스탯 최고값이 임계값 이상이면 정확한 정보 | |
| 성계별 첩보원 존재 여부 | 해당 성계에 실제 체류 중인 intelligence 높은 장교가 있어야 정확한 정보 획득 | ✓ |
| You decide | Claude가 게임성과 복잡도를 고려해 판단 | |

**User's choice:** 성계별 첩보원 존재 여부
**Notes:** 더 게임적인 선택. 정보 장교의 배치가 전략적 의미를 가짐

### 복합 스코어링 구성 요소

| Option | Description | Selected |
|--------|-------------|----------|
| 함선 수 + 사령관 능력 | 함선 수(ships 합산)가 전력의 핵심이고, 사령관의 command/leadership가 질적 차이 | |
| 함대 수 + 함선 수 + 요새 방어력 | 함대 수로 물량, 함선 수로 화력, 궤도방어/요새로 방어력 | |
| You decide | Claude가 게임 밸런스 고려해 결정 | ✓ |

**User's choice:** You decide
**Notes:** Claude 재량

---

## 작전 대상 선정 로직

### 작전 유형별 대상 성계 선정 기준

| Option | Description | Selected |
|--------|-------------|----------|
| 전선(frontState) 기반 | frontState 데이터를 기반으로 전선 성계 식별 | |
| 성계 간 거리 기반 | StarRoute 그래프 탐색으로 가까운 성계 우선 | |
| 복합 (전선 + 전략적 가치) | 전선 여부 + 성계 자원량 + 연결성 등을 복합 평가 | ✓ |

**User's choice:** 복합 (전선 + 전략적 가치)
**Notes:** 단순 전선 기반이 아닌 전략적 가치도 고려

### 동시 작전 수 제한

| Option | Description | Selected |
|--------|-------------|----------|
| 무제한 | Phase 12 D-02와 일관. AI도 필요한 만큼 수립 | ✓ |
| 함대 수 기반 제한 | 전체 함대의 60% 이상을 작전에 투입하지 않도록 제한 | |
| You decide | Claude가 밸런스 고려해 판단 | |

**User's choice:** 무제한
**Notes:** Phase 12와 일관성 유지

---

## FactionAI 통합 방식

### 작전계획 생성 방식

| Option | Description | Selected |
|--------|-------------|----------|
| OperationPlanService 직접 호출 | FactionAI가 서비스를 직접 호출. CP/권한 검증 우회 | |
| CommandExecutor 통해 실행 | 기존 커맨드 파이프라인 일관 유지. CP 소모, 권한 검증, 로그 발행 | ✓ |
| You decide | Claude가 기존 패턴과 일관성을 고려해 결정 | |

**User's choice:** CommandExecutor 통해 실행
**Notes:** 기존 커맨드 파이프라인의 일관성 중시

### 삼국지 잔재 처리

| Option | Description | Selected |
|--------|-------------|----------|
| 완전 제거 + 작전계획 교체 | atWar 분기를 전략 AI 작전 수립으로 완전 교체 | ✓ |
| 작전계획 + 기존 병행 | 작전계획을 추가하되 기존 전쟁 커맨드도 유지 | |

**User's choice:** 완전 제거 + 작전계획 교체
**Notes:** 삼국지 잔재 완전 제거 원칙

### AI 장교 설정

| Option | Description | Selected |
|--------|-------------|----------|
| 진영 원수/의장 | Faction의 sovereign을 발령자로 사용. commander 권한 자동 보유 | ✓ |
| 새 AI 전용 장교 생성 | AI 전용 가상 장교 생성하여 커맨드 실행 | |
| You decide | Claude가 기존 커맨드 구조를 고려해 판단 | |

**User's choice:** 진영 원수/의장
**Notes:** 자연스럽게 commander 권한 보유

---

## 부대 배정 전략

### 함대 선정 기준

| Option | Description | Selected |
|--------|-------------|----------|
| 유휴 + 근접 기반 | 작전 미배정 + 목표 성계 근처의 함대 우선 선택 | |
| 전력 기반 최적 배정 | 적 전력보다 충분한 전력이 되도록 최소 함대 조합 계산. 나머지 방어 예비 | ✓ |
| You decide | Claude가 게임 밸런스를 고려해 결정 | |

**User's choice:** 전력 기반 최적 배정
**Notes:** 과도한 전력 집중 방지, 방어 예비 유지

### 성격 영향

| Option | Description | Selected |
|--------|-------------|----------|
| 성격 반영 | AGGRESSIVE→CONQUEST 우선, DEFENSIVE→DEFENSE 우선 등. Phase 11과 일관 | ✓ |
| 성격 무관 (순수 전력 평가) | 진영 성격이 아닌 순수한 전력 분석으로만 작전 유형 결정 | |
| You decide | Claude가 gin7 원작 게임성을 고려해 판단 | |

**User's choice:** 성격 반영
**Notes:** 원작 분위기 재현 중시

## Claude's Discretion

- 복합 스코어링의 구체적 가중치 및 공식
- 첩보원 판정 기준 (intelligence 임계값)
- 노이즈 추정치의 오차 범위
- 전략적 가치 평가 세부 요소
- 작전 수립 주기
- 최소 함대 조합 알고리즘
- 기존 FactionAI의 비전쟁 로직 유지 범위

## Deferred Ideas

- 연쇄 작전(CASCADE)
- 작전 우선순위
- 전략 AI 학습 (ML 기반)
- 외교 AI 고도화
