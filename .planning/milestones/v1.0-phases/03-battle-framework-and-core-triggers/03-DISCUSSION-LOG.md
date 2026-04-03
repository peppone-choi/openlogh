# Phase 3: Battle Framework and Core Triggers - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md -- this log preserves the alternatives considered.

**Date:** 2026-04-01
**Phase:** 03-battle-framework-and-core-triggers
**Areas discussed:** Trigger framework design, killnum data access, Testing strategy, Battle XP scope

---

## Trigger Framework Design

| Option | Description | Selected |
|--------|-------------|----------|
| 독립 인터페이스 | WarUnitTrigger 인터페이스를 새로 만들고, 각 트리거를 별도 클래스로 구현. BattleEngine이 전투 페이즈별로 트리거 리스트를 순회 호출. ActionModifier는 stat 수정 전담으로 유지. | ✓ |
| ActionModifier 확장 | 기존 ActionModifier에 onBattlePhase() 메서드를 추가하여 트리거 로직을 같은 객체에 넣음. 한 곳에서 관리 가능하지만 stat과 트리거가 섞임. | |
| Claude 판단에 맡김 | 레거시 PHP 구조를 분석해서 가장 패리티에 맞는 방식으로 결정 | |

**User's choice:** 독립 인터페이스
**Notes:** ActionModifier와 WarUnitTrigger의 관심사를 명확히 분리

---

## 무쌍 killnum 데이터 접근

| Option | Description | Selected |
|--------|-------------|----------|
| StatContext 필드 추가 | StatContext에 killnum: Double 필드를 추가하고, ModifierService가 General 엔티티에서 조회해서 전달. 가장 단순하고 레거시 패턴과 일치. | ✓ |
| 별도 룩업 서비스 | RankDataService를 만들어 killnum을 조회. Modifier가 서비스를 주입받아 사용. 더 유연하지만 복잡도 증가. | |
| Claude 판단에 맡김 | 레거시 PHP 코드를 확인해서 가장 적합한 방식 결정 | |

**User's choice:** StatContext 필드 추가
**Notes:** 없음

---

## 테스트 전략

| Option | Description | Selected |
|--------|-------------|----------|
| 트리거별 단위 테스트 | 각 트리거를 격리하여 테스트. 고정 시드 RNG로 확률 결과를 결정론적으로 검증. PHP 입력/출력 golden value로 패리티 확인. | |
| 통합 전투 시뮬레이션 | 전체 전투 루프를 고정 시드로 돌리고 최종 결과(사상자, 사기, 부상 등)를 golden snapshot으로 검증. 더 현실적이지만 디버깅 어려움. | |
| 둘 다 하되 단위 우선 | 트리거별 단위 테스트 먼저 작성, 이후 Phase 4에서 통합 전투 시뮬레이션 추가 | ✓ |

**User's choice:** 둘 다 하되 단위 우선
**Notes:** 단위 테스트로 개별 트리거 패리티 확보 후, Phase 4에서 전체 전투 통합 검증

---

## 전투 경험치(C7) 구현 범위

| Option | Description | Selected |
|--------|-------------|----------|
| XP 계산만 | 전투에서 XP 획득 공식만 구현. 레벨업/스탯 성장은 이미 GeneralMaintenanceService에 있으니 XP가 정확히 쌓이는지만 검증. | |
| XP + 레벨업 통합 검증 | XP 계산 + 레벨업 공식까지 함께 검증. 전투 → 경험치 → 레벨업 파이프라인 전체 패리티 확인. | ✓ |
| Claude 판단에 맡김 | legacy 코드를 확인해서 C7 범위를 결정 | |

**User's choice:** XP + 레벨업 통합 검증
**Notes:** 전투 → 경험치 → 레벨업 전체 파이프라인 패리티 보장

---

## Claude's Discretion

- WarUnitTrigger hook 메서드 시그니처 (researcher가 legacy PHP에서 결정)
- 트리거 등록 메커니즘 (planner가 기존 패턴 기반으로 결정)
- BattleTriggerContext 추가 필드 필요 여부

## Deferred Ideas

None
