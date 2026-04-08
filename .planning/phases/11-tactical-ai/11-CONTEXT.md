# Phase 11: 전술 AI - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

NPC/오프라인 유닛이 전술전에서 작전 목적과 성격에 따라 자동 전투를 수행하는 AI 시스템.
에너지 배분, 진형, 태세, 공격 대상 선택, 퇴각 판단을 자동으로 수행한다.

Phase 12(작전 연동)에서 전략 게임의 작전계획이 전술전 AI에 연결되므로,
이 페이즈에서는 작전 목적을 파라미터로 받아 행동하는 AI 로직에 집중한다.
작전계획 엔티티 자체는 Phase 12 범위.

</domain>

<decisions>
## Implementation Decisions

### 작전 목적 기본 행동 (TAI-01)
- **D-01:** 점령(Conquest) — 약한 적만 교전, 강한 적은 우회하며 목표 행성 좌표로 이동 (판단형)
- **D-02:** 방어(Defense) — 적 접근 시 요격 후 원위치 복귀 (요격-복귀 패턴)
- **D-03:** 소탕(Sweep) — 위협 평가 기반으로 가장 위험한 적 우선 추격

### 성격별 전투 스타일 (TAI-02)
- **D-04:** 교전 거리, 공격 대상 선택, 진형 선호 모두 성격별로 다름 (전면 차등)
- **D-05:** 퇴각 임계값 성격별 차등 — AGGRESSIVE는 끝까지(HP<10%), CAUTIOUS는 일찍(HP<30%), DEFENSIVE/BALANCED/POLITICAL은 중간(HP<20%)

### AI 재평가 주기 (TAI-03)
- **D-06:** 10틱마다 정기 재평가 (1초에 1회)
- **D-07:** 급변 상황(기함 격침, 아군 전멸 등)은 이벤트 트리거로 즉시 재평가

### 에너지/진형/태세 자동 조정 (TAI-04)
- **D-08:** 에너지 배분은 거리 + HP 복합 판단 (원거리→BEAM, 근접→GUN, HP 낮으면→SHIELD/ENGINE)
- **D-09:** 진형은 성격이 선호 진형을 갖되 상황에 따라 변경 가능 (성격 + 상황 복합)
- **D-10:** 집중/분산 공격은 위협 평가 기반 + 작전 목적 연동 (소탕은 분산 경향, 점령/방어는 집중 경향이되 위협 평가로 최종 결정)

### Claude's Discretion
- AI 내부 구현 패턴 (state machine vs utility scoring vs behavior tree)
- 위협 평가 공식의 구체적 가중치 튜닝
- 재평가 이벤트 트리거의 구체적 목록
- 테스트 전략

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 기존 AI 시스템
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/PersonalityTrait.kt` — 5종 성격 + 스탯 가중치 + inferFromStats
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/UtilityScorer.kt` — 전략 AI 유틸리티 스코어링 패턴 (전술 AI도 동일 패턴 권장)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/AIContext.kt` — 전략 AI 컨텍스트 (전술 AI 컨텍스트 설계 참고)

### 전술전 엔진
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — processTick() 루프, 기존 step 순서
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/OutOfCrcBehavior.kt` — CRC 밖 유닛 AI (HP 퇴각, stuck 이동) — 이 패턴을 확장
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalCommand.kt` — sealed class 커맨드 (AI가 이 커맨드를 생성하여 enqueue)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt` — 지휘 계층 (AI는 계층 내 역할에 따라 행동)

### 유닛/전투 모델
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalUnit.kt` — 유닛 필드 (hp, morale, stance, formation, energy 등)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/SuccessionService.kt` — 승계 로직 (AI가 승계 상황을 인지해야 함)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **PersonalityTrait + PersonalityWeights**: 5종 성격과 8-stat 가중치 — 전술 AI 결정에 직접 사용 가능
- **UtilityScorer 패턴**: pure object + stat × weight 스코어링 — 전술 AI도 동일 패턴으로 TacticalUtilityScorer 가능
- **OutOfCrcBehavior**: HP 기반 퇴각 + 이동 로직 — 전술 AI의 퇴각 판단 기반으로 확장
- **TacticalCommand sealed class**: AI가 커맨드를 생성하여 commandBuffer에 enqueue하는 패턴 이미 확립

### Established Patterns
- 전술 엔진 서비스는 pure object (Spring DI 없음): CommandHierarchyService, SuccessionService, OutOfCrcBehavior
- processTick() step 순서에 AI 처리 step 삽입 필요
- BattleTickEvent로 AI 행동 브로드캐스트 가능

### Integration Points
- TacticalBattleEngine.processTick()에 AI step 추가 (step 0.5 processOutOfCrcUnits 확장 또는 새 step)
- AI가 TacticalCommand를 생성하여 commandBuffer에 enqueue → 기존 drainCommandBuffer에서 처리
- TacticalBattleState에 작전 목적 필드 추가 필요 (Phase 12 전까지 enum 파라미터로 stub)

</code_context>

<specifics>
## Specific Ideas

- 점령 AI의 "약한 적만 교전, 강한 적 우회"는 위협 평가 점수를 역으로 사용 — 낮은 위협 = 교전 가치, 높은 위협 = 우회 대상
- 방어 AI의 "요격 후 원위치 복귀"는 원위치 좌표를 기억하는 anchor point 패턴
- 성격별 진형 선호: AGGRESSIVE→紡錘(돌파), DEFENSIVE→混成(방어), CAUTIOUS→艦種(범용), BALANCED→상황 판단
- 급변 상황 즉시 재평가 트리거: 기함 격침, 분함대장 전사, 사기 급락, 지휘 체계 붕괴

</specifics>

<deferred>
## Deferred Ideas

- **성계-행성 2계층 구조**: gin7 원작의 성계 > 행성 영토 구조 반영 (별도 마일스톤)
- Phase 12(작전 연동)에서 작전계획 엔티티와 전술 AI 연결

</deferred>

---

*Phase: 11-tactical-ai*
*Context gathered: 2026-04-08*
