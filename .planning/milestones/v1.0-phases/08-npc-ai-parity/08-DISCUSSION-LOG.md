# Phase 8: NPC AI Parity - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-02
**Phase:** 08-npc-ai-parity
**Areas discussed:** 검증 범위 및 분할, 외교 AI 접근법, NationAI vs GeneralAI 경계, 테스트 전략

---

## 검증 범위 및 분할

### 분할 전략

| Option | Description | Selected |
|--------|-------------|----------|
| 기능별 3분할 (추천) | 군사 AI + 내정경제 AI + 외교인사방랑 AI — 각 Plan이 독립적으로 검증 가능 | ✓ |
| 역할별 2분할 | GeneralAI 전체 (Plan 1) + NationAI 전체 (Plan 2) — 단순하지만 Plan 1이 매우 큼 | |
| 4분할 세분화 | 군사 + 내정 + 외교인사 + NationAI — 더 세분화되지만 Plan이 많아짐 | |

**User's choice:** 기능별 3분할 (추천)
**Notes:** None

### 대조 방식

| Option | Description | Selected |
|--------|-------------|----------|
| PHP 수동 추적 (추천) | Phase 5/6/7과 동일: PHP do*() 코드를 직접 읽고 기대 결정값을 수동 계산 후 golden value로 잠금 | ✓ |
| 결정 분기점 검증 | do*() 메서드 전체보다 의사결정 분기점(if/when 조건)만 검증 — 빠르지만 누락 위험 | |
| 통합 시나리오 | 10턴 시뮬레이션으로 NPC 행동 시퀀스 전체를 golden snapshot으로 잠금 — 포괄적이지만 디버깅 어려움 | |

**User's choice:** PHP 수동 추적 (추천)
**Notes:** None

---

## 외교 AI 접근법

| Option | Description | Selected |
|--------|-------------|----------|
| PHP 기준 재작성 (추천) | PHP do선전포고()/do불가침제의()를 정밀 읽고 Kotlin 외교 로직을 PHP와 동일하게 수정. 발견 즉시 수정 패턴. | ✓ |
| 현재 로직 유지 + 차이점 문서화 | Kotlin 외교 AI를 의도적 개선으로 간주하고, PHP와의 차이를 문서화만 함 | |
| Phase 10으로 연기 | 외교 로직은 Phase 10(Diplomacy)에서 다루고, Phase 8에서는 군사/내정/인사 AI만 검증 | |

**User's choice:** PHP 기준 재작성 (추천)
**Notes:** None

---

## NationAI vs GeneralAI 경계

| Option | Description | Selected |
|--------|-------------|----------|
| PHP 기준 대조 (추천) | PHP do선전포고() ↔ Kotlin doDeclaration() 처럼 PHP 메서드 단위로 1:1 대조. 출력(action string)이 동일하면 OK. 파일 구조 자체는 수정 안 함. | ✓ |
| Kotlin 구조로 통합 | NationAI.kt를 GeneralAI.kt로 합쳐서 PHP와 동일한 파일 구조로 만든 후 검증 | |
| Kotlin 구조 기준 | GeneralAI.kt/NationAI.kt 각각 독립적으로 검증하고, PHP 대조는 참고만 함 | |

**User's choice:** PHP 기준 대조 (추천)
**Notes:** None

---

## 테스트 전략

### 테스트 깊이

| Option | Description | Selected |
|--------|-------------|----------|
| 의사결정 golden value (추천) | 고정 게임 상태 → PHP에서 기대하는 action string 수동 계산 → Kotlin 출력 대조 | |
| 조건별 단위 테스트 | 각 do*() 내부 if/when 분기 조건을 개별 테스트 — 더 정밀하지만 테스트 수가 매우 많아짐 | |
| 병행: golden + 핵심 분기점 | do*() 메서드별 golden value 1~2개 + 핵심 의사결정 분기점에 대한 조건별 테스트 추가 | ✓ |

**User's choice:** 병행: golden + 핵심 분기점
**Notes:** None

### 기존 테스트 활용

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 확장 (추천) | 기존 테스트 파일에 golden value assertion 추가. Phase 7과 동일하게 기존 구조 보강. | ✓ |
| 새 parity 테스트 생성 | 기존 테스트는 그대로 두고, NpcAiParityTest.kt를 확장하거나 새 parity 테스트 클래스 생성 | |
| Claude 재량 | do*() 메서드 구조에 따라 기존 확장 vs 새 파일 생성을 판단 | |

**User's choice:** 기존 확장 (추천)
**Notes:** None

---

## Claude's Discretion

- Plan 3분할 내 구체적 메서드 배치 경계
- PHP 코드 읽기 순서 및 카탈로그 정리 형식
- golden value fixture 설계
- 핵심 분기점 테스트 조건 선정
- NationAI.kt 외교 로직 수정 시 리팩터링 범위

## Deferred Ideas

None — discussion stayed within phase scope
