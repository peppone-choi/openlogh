# Requirements: Open LOGH v2.1

**Defined:** 2026-04-07
**Core Value:** gin7 조직 시뮬레이션 — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행

## v2.1 Requirements

Requirements for milestone v2.1: 전술전 지휘체계 + AI. Each maps to roadmap phases.

### 엔진 통합 (ENGINE)

- [x] **ENGINE-01**: 듀얼 전술 엔진(TacticalBattleEngine + TacticalCombatEngine)이 단일 엔진으로 통합된다
- [x] **ENGINE-02**: 커맨드 버퍼 패턴으로 tick 루프와 WebSocket 명령의 동시성이 보장된다
- [x] **ENGINE-03**: 지휘 계층 데이터 모델(CommandHierarchy)이 전투 상태에 포함된다

### 지휘권 분배 (CMD)

- [ ] **CMD-01**: 사령관이 함대 내 60유닛을 부사령관/참모에게 분함대로 배정할 수 있다
- [ ] **CMD-02**: 지휘권 우선순위(온라인→계급→평가→공적)가 자동 적용된다
- [ ] **CMD-03**: 커맨드레인지서클 내 유닛에만 명령이 전달된다
- [ ] **CMD-04**: 서클 밖 유닛은 마지막 명령을 유지하거나 AI 자율 행동한다
- [ ] **CMD-05**: 사령관이 실시간으로 유닛을 재배정할 수 있다 (서클 밖 + 정지 조건)
- [ ] **CMD-06**: 통신 방해 시 총사령관의 전군 명령이 불가능하다

### 지휘 승계 (SUCC)

- [ ] **SUCC-01**: 사령관이 사전에 후계자를 지명할 수 있다
- [ ] **SUCC-02**: 사령관 부상 시 지휘력이 저하되며, 이 시점에 후계자를 지명하여 지휘권을 위임할 수 있다
- [ ] **SUCC-03**: 사령관 사망(기함 격침/전사) 시 30틱 공백 후 사전 지명된 후계자가 승계한다
- [ ] **SUCC-04**: 사전 지명자가 없거나 지명자도 사망한 경우 차순위 계급자가 자동 승계한다
- [ ] **SUCC-05**: 분함대장 지휘 불가 시 해당 유닛이 사령관 직할로 복귀한다
- [ ] **SUCC-06**: 모든 사령관 지휘 불가 시 지휘 체계가 붕괴하여 각 유닛이 독립 AI로 행동한다

### 전술 AI (TAI)

- [ ] **TAI-01**: 오프라인/NPC 유닛이 작전 목적 기반 기본 행동을 수행한다 (점령→행성이동, 방어→현위치수비, 소탕→적추격)
- [ ] **TAI-02**: 성격(PersonalityTrait) 기반으로 전술 차이가 발생한다 (AGGRESSIVE→공격적, DEFENSIVE→방어적)
- [ ] **TAI-03**: 위협 평가 기반 퇴각 판단이 작동한다 (HP<20%, 사기<30%)
- [ ] **TAI-04**: 에너지 배분/진형/태세가 상황에 따라 자동 조정된다
- [ ] **TAI-05**: 집중공격/분산공격 전략이 적용된다

### 작전 연동 (OPS)

- [ ] **OPS-01**: 전략 게임의 작전계획(점령/방어/소탕) 목적이 전술전 AI 기본 행동을 결정한다
- [ ] **OPS-02**: 작전에 참가한 부대만 공적 보너스를 받는다
- [ ] **OPS-03**: 발령된 부대가 목표 성계 도달 시 작전이 시작된다

### 전략 AI (SAI)

- [ ] **SAI-01**: 전술전 진입 시 AI가 작전계획을 자동 수립한다
- [ ] **SAI-02**: 전력 평가 기반으로 작전 유형(점령/방어/소탕)을 선택한다

### 프론트엔드 (FE)

- [ ] **FE-01**: 전술맵에 커맨드레인지서클이 시각화된다
- [ ] **FE-02**: 분함대 배정 패널에서 유닛을 배정/해제할 수 있다
- [ ] **FE-03**: 지휘권에 따라 명령 가능 UI가 제한된다
- [ ] **FE-04**: 지휘 승계 시 시각적 피드백이 표시된다
- [ ] **FE-05**: 안개(fog-of-war) 효과가 적용된다

## Future Requirements

Deferred to future release. Tracked but not in current roadmap.

### 성능 최적화

- **PERF-01**: WebSocket 델타 브로드캐스팅으로 대역폭 최적화
- **PERF-02**: 공간 해싱으로 O(N^2) 탐지/사거리 쿼리 최적화

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| 전술전 리플레이 시스템 | 복잡도 높음, 핵심 가치와 무관 |
| 유닛별 미세 조종 (개별 이동 경로) | gin7 설계 철학에 반함 — 조직 시뮬레이션이 핵심 |
| 멀티플레이어 음성 채팅 | 인프라 비용 과다, 텍스트 통신으로 충분 |
| 모바일 전술맵 UI | 웹 우선, 모바일은 후순위 |
| AI 학습/ML 기반 전술 판단 | 과도한 복잡도, utility scoring으로 충분 |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ENGINE-01 | Phase 8 | Complete |
| ENGINE-02 | Phase 8 | Complete |
| ENGINE-03 | Phase 8 | Complete |
| CMD-01 | Phase 9 | Pending |
| CMD-02 | Phase 9 | Pending |
| CMD-03 | Phase 9 | Pending |
| CMD-04 | Phase 9 | Pending |
| CMD-05 | Phase 9 | Pending |
| CMD-06 | Phase 9 | Pending |
| SUCC-01 | Phase 10 | Pending |
| SUCC-02 | Phase 10 | Pending |
| SUCC-03 | Phase 10 | Pending |
| SUCC-04 | Phase 10 | Pending |
| SUCC-05 | Phase 10 | Pending |
| SUCC-06 | Phase 10 | Pending |
| TAI-01 | Phase 11 | Pending |
| TAI-02 | Phase 11 | Pending |
| TAI-03 | Phase 11 | Pending |
| TAI-04 | Phase 11 | Pending |
| TAI-05 | Phase 11 | Pending |
| OPS-01 | Phase 12 | Pending |
| OPS-02 | Phase 12 | Pending |
| OPS-03 | Phase 12 | Pending |
| SAI-01 | Phase 13 | Pending |
| SAI-02 | Phase 13 | Pending |
| FE-01 | Phase 14 | Pending |
| FE-02 | Phase 14 | Pending |
| FE-03 | Phase 14 | Pending |
| FE-04 | Phase 14 | Pending |
| FE-05 | Phase 14 | Pending |

**Coverage:**
- v2.1 requirements: 30 total
- Mapped to phases: 30
- Unmapped: 0

---
*Requirements defined: 2026-04-07*
*Last updated: 2026-04-07 after roadmap creation*
