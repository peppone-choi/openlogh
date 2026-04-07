# Phase 8: 엔진 통합 + 커맨드 버퍼 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 08-scenario-character-system
**Areas discussed:** 엔진 통합 전략, 커맨드 버퍼 범위, CommandHierarchy 설계, 기존 코드 처리

---

## 엔진 통합 전략

### Q1: 통합 기반 엔진 선택

| Option | Description | Selected |
|--------|-------------|----------|
| TacticalBattleEngine 기반 (추천) | tactical/의 기존 엔진 유지, war/의 추가 기능(weaponCooldowns, debuffs, DetectionCapability, CommandRange 객체) 병합. TacticalBattleService가 이미 사용 중 | ✓ |
| TacticalCombatEngine 기반 | war/의 엔진이 더 최신이고 확장 필드가 있음. 단, TacticalBattleService 연동 재작성 필요 | |
| 새 통합 TacticalUnit | 두 엔진의 필드를 모두 모아 새로 설계. 가장 깨끗하지만 작업량 최대 | |

**User's choice:** TacticalBattleEngine 기반 (추천)
**Notes:** 이미 TacticalBattleService가 이 엔진을 사용 중이므로 연동 변경 최소화

### Q2: 서비스 통합 범위

| Option | Description | Selected |
|--------|-------------|----------|
| tactical/로 통합 (추천) | war/의 DetectionEngine/PlanetCaptureProcessor를 tactical/ 기존 서비스에 병합, war/ 패키지 삭제 | ✓ |
| 유지 분리 | war/ 서비스 그대로 두고 통합 엔진이 둘 다 호출 | |
| Claude 판단 | Claude가 최합리적 방식 결정 | |

**User's choice:** tactical/로 통합 (추천)
**Notes:** None

---

## 커맨드 버퍼 범위

### Q3: 버퍼링 범위

| Option | Description | Selected |
|--------|-------------|----------|
| 전술 명령 전체 버퍼링 (추천) | 에너지/태세/퇴각/공격대상/진형 등 모든 WebSocket 명령을 ConcurrentLinkedQueue에 버퍼링, tick 시작 시 일괄 처리 | ✓ |
| 상태 변경만 버퍼링 | 에너지/태세/진형만 버퍼링, 퇴각 같은 즉시성 명령은 직접 적용 | |
| Claude 판단 | 명령 종류별 버퍼링/즉시 처리 경계를 Claude가 결정 | |

**User's choice:** 전술 명령 전체 버퍼링 (추천)
**Notes:** None

### Q4: 버퍼 소유자

| Option | Description | Selected |
|--------|-------------|----------|
| TacticalBattleState 내부 (추천) | 각 전투별 상태 객체에 commandBuffer 필드 추가. 전투별 격리 + 라이프사이클 동기화 | ✓ |
| TacticalBattleService 레벨 | ConcurrentHashMap<Long, Queue> 형태로 서비스에서 관리 | |
| Claude 판단 | 아키텍처적으로 가장 적합한 위치를 Claude가 결정 | |

**User's choice:** TacticalBattleState 내부 (추천)
**Notes:** None

---

## CommandHierarchy 설계

### Q5: 데이터 모델 범위

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 9-10 필수 필드 포함 (추천) | 분함대 배정, 승계 대기열, CRC 반경, 통신방해 플래그까지 모델링. Phase 9-10에서 로직만 추가 | ✓ |
| Phase 8 최소한만 | 사령관-유닛 매핑과 사이드만. 나머지는 Phase 9-10에서 스키마 확장 | |
| Claude 판단 | Phase 9-10 요구사항 분석하여 최적 범위를 Claude가 결정 | |

**User's choice:** Phase 9-10 필수 필드 포함 (추천)
**Notes:** None

### Q6: 초기화 방식

| Option | Description | Selected |
|--------|-------------|----------|
| Fleet 엔티티 기반 자동 생성 (추천) | startBattle() 시 Fleet의 officerId를 사령관으로, ShipUnit들을 unitIds로, 승계 대기열은 계급순 초기화 | ✓ |
| 전략에서 사전 설정 | 전술전 진입 전에 사령관이 분함대 배정을 미리 설정하고 전투 시작 시 로드 | |
| Claude 판단 | 기존 코드 흐름 분석하여 최적 방식을 Claude가 결정 | |

**User's choice:** Fleet 엔티티 기반 자동 생성 (추천)
**Notes:** None

---

## 기존 코드 처리

### Q7: war/ 패키지 + 중복 컨트롤러 처리

| Option | Description | Selected |
|--------|-------------|----------|
| 완전 삭제 (추천) | war/ 패키지 삭제, 중복 컨트롤러(BattleRestController, TacticalBattleController, TacticalBattleRestController) 삭제. BattleWebSocketController + TacticalBattleService만 유지 | ✓ |
| @Deprecated 후 Phase 14에서 삭제 | 프론트엔드 통합 전까지 레거시 컨트롤러 유지하되 deprecated 마킹 | |
| Claude 판단 | 코드 분석 후 안전하게 삭제 가능한 것만 제거, 나머지 유지 | |

**User's choice:** 완전 삭제 (추천)
**Notes:** None

---

## Claude's Discretion

- TacticalUnit 필드 병합 시 이름 충돌 해소 방식
- war/ 서비스와 tactical/ 서비스의 구체적 기능 병합 전략
- 커맨드 버퍼의 TacticalCommand sealed class 설계
- 테스트 전략 및 마이그레이션 순서

## Deferred Ideas

None — discussion stayed within phase scope.
