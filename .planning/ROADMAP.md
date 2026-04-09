# Roadmap: Open LOGH

## Milestones

- ✅ **v2.0 gin7 게임 로직 전면 재작성** - Phases 1-7 (shipped 2026-04-06)
- 🚧 **v2.1 전술전 지휘체계 + AI** - Phases 8-14 (in progress)

## Phases

<details>
<summary>v2.0 gin7 게임 로직 전면 재작성 (Phases 1-7) - SHIPPED 2026-04-06</summary>

- [x] **Phase 1: 레거시 제거 + 함종 유닛 기반** - 삼국지 잔재 완전 제거 및 gin7 ShipUnit 엔티티 수립
- [x] **Phase 2: gin7 81종 커맨드 시스템** - 직무권한카드 기반 커맨드 전면 재구현
- [x] **Phase 3: 실시간 전술전 엔진** - 에너지/무기/진형/색적/요새포/지상전 구현
- [x] **Phase 4: 경제 시스템** - 행성자원/조병창/세율/차관 경제 루프 구현
- [x] **Phase 5: AI 시스템** - 성격 기반 NPC AI 및 진영 AI 구현
- [x] **Phase 6: 프론트엔드 통합** - 은하맵/전술전 UI/전략게임 화면 전면 재작성
- [x] **Phase 7: 시나리오 데이터 + 밸런싱** - 10개 시나리오 데이터 및 균형 조정

</details>

### v2.1 전술전 지휘체계 + AI

- [x] **Phase 8: 엔진 통합 + 커맨드 버퍼** - 듀얼 전술 엔진 통합, 커맨드 버퍼 동시성 보장, 지휘 계층 데이터 모델 수립
- [x] **Phase 9: 지휘권 분배 + 커맨드레인지서클** - 분함대 배정, 우선순위 기반 지휘권, CRC 명령 전달, 통신 방해
- [x] **Phase 10: 지휘 승계** - 후계자 지명, 부상/사망 구분, 30틱 공백, 자동 승계, 체계 붕괴
- [ ] **Phase 11: 전술 AI** - 작전 목적 기반 AI 행동, 성격 기반 전술, 위협 평가, 에너지/진형 자동 조정
- [x] **Phase 12: 작전 연동** - 작전계획-전술전 연결, 작전 참가 부대 공적 보너스, 발령-도달 시작 (2026-04-09)
- [x] **Phase 13: 전략 AI** - 진영 AI 자동 작전 수립, 전력 평가 기반 작전 유형 선택 (2026-04-09)
- [ ] **Phase 14: 프론트엔드 통합** - CRC 시각화, 분함대 패널, 권한 기반 UI 제한, 승계 피드백, 안개 효과

## Phase Details

<details>
<summary>v2.0 Phase Details (Phases 1-7)</summary>

### Phase 1: 레거시 제거 + 함종 유닛 기반
**Goal**: 삼국지 게임 로직이 완전히 제거되고 gin7 함종/유닛 엔티티가 전투와 커맨드의 기반으로 작동한다
**Depends on**: Nothing (first phase)
**Requirements**: LEGACY-01, LEGACY-02, LEGACY-03, LEGACY-04, LEGACY-05, LEGACY-06, SHIP-01, SHIP-02, SHIP-03, SHIP-04, SHIP-05, SHIP-06
**Success Criteria** (what must be TRUE):
  1. CommandRegistry에 삼국지 커맨드가 0개 존재한다 (gin7 stub 81종으로 대체됨)
  2. ShipUnit 엔티티가 DB에 존재하며 11함종 x 서브타입 스탯이 ship_stats JSON에서 로드된다
  3. 기함/육전대 유닛이 Fleet에 연결 가능하며 승조원 수련도 4단계가 적용된다
  4. 삼국지 병종 상성 및 수치비교 전투 로직이 코드베이스에 존재하지 않는다
  5. `grep -r "officerLevel >= 5"` 결과가 0이거나 stub 대체가 완료된 상태이다
**Plans**: 5 plans (complete)

### Phase 2: gin7 81종 커맨드 시스템
**Goal**: 직무권한카드 기반 81종 gin7 커맨드가 실시간 실행 파이프라인을 통해 동작하며 삼국지 권한 우회가 완전히 제거된다
**Depends on**: Phase 1
**Requirements**: CMD-01, CMD-02, CMD-03, CMD-04, CMD-05, CMD-06, CMD-07, CMD-08, CMD-09
**Success Criteria** (what must be TRUE):
  1. 플레이어가 직무권한카드를 통해서만 해당 커맨드 그룹에 접근할 수 있다
  2. 커맨드 실행 시 CP 차감 -> 대기시간 -> 실행 -> WebSocket 결과 브로드캐스트 흐름이 동작한다
  3. 7개 커맨드 그룹 81종이 모두 CommandRegistry에 등록된다
  4. 계급이 낮은 장교가 상급자에게 제안을 발행하고 승인/거부가 처리된다
**Plans**: 7 plans (complete)

### Phase 3: 실시간 전술전 엔진
**Goal**: 에너지 배분, 무기 시스템, 진형, 커맨드레인지서클, 색적, 요새포, 지상전을 포함한 gin7 전술전이 실시간으로 동작한다
**Depends on**: Phase 1, Phase 2
**Requirements**: BATTLE-01 through BATTLE-12
**Success Criteria** (what must be TRUE):
  1. 적아 유닛 공존 시 전투 자동 개시, 종료 조건 달성 시 정상 종료
  2. 6채널 에너지 슬라이더 합계 100 유지, WebSocket 실시간 반영
  3. 빔/건/미사일 무기 사거리/위력/보정 피해 계산, 88 서브타입 스탯 적용
  4. 커맨드레인지서클 tick 확대, 명령 발령 시 0 리셋
  5. 지상전 및 6종 행성 점령 방식 동작
**Plans**: 5 plans (complete)

### Phase 4: 경제 시스템
**Goal**: 행성 자원 생산, 조병창, 세율/납입, 창고 이동, 페잔 차관이 월별 파이프라인을 통해 순환된다
**Depends on**: Phase 2
**Requirements**: ECON-01 through ECON-06
**Success Criteria** (what must be TRUE):
  1. 90일 주기 세수가 진영 자금에 반영된다
  2. 행성 조병창이 tick마다 자동 생산한다
  3. 행성창고와 부대창고 간 자원 이동이 가능하다
  4. 페잔 차관 미상환 시 페잔 엔딩이 트리거된다
**Plans**: 5 plans (complete)

### Phase 5: AI 시스템
**Goal**: 오프라인 플레이어와 NPC가 성격 기반으로 행동하며, 진영 AI가 자율 처리한다
**Depends on**: Phase 2
**Requirements**: AI-01 through AI-04
**Success Criteria** (what must be TRUE):
  1. 오프라인 플레이어 캐릭터가 성격 가중치에 따라 커맨드를 자동 실행한다
  2. 진영 AI가 예산/인사를 자율 처리한다
  3. 쿠데타 조건 감지 시 내전 트리거가 발동한다
  4. NPC AI가 슬롯 기반 스케줄링으로 성능 문제 없이 동작한다
**Plans**: 3 plans (complete)

### Phase 6: 프론트엔드 통합
**Goal**: 전략 게임 화면, 전술전 UI, 은하맵이 삼국지 잔재 없이 한국어로 완성된다
**Depends on**: Phase 1, Phase 2, Phase 3
**Requirements**: FE-01 through FE-08
**Success Criteria** (what must be TRUE):
  1. 은하맵 도트스타일 성계 아이콘과 진영 색상 표시
  2. 전술전 화면이 3D+2D 분할 에너지 슬라이더 실시간 동작
  3. 직무권한카드 탭 -> 커맨드 -> CP -> 실행 흐름 동작
  4. 삼국지 용어/컴포넌트 0건
  5. 진영별 정치 UI 동작
**Plans**: 8 plans (complete)
**UI hint**: yes

### Phase 7: 시나리오 데이터 + 밸런싱
**Goal**: 10개 시나리오 초기 데이터 완비, 밸런스 조정 적용, 실제 플레이 가능
**Depends on**: Phase 1-6
**Requirements**: SCEN-01 through SCEN-03, TEST-01 through TEST-04
**Success Criteria** (what must be TRUE):
  1. 10개 시나리오 인물/함대/행성 초기값 적재, 게임 시작 가능
  2. LOGH 원작 캐릭터 8스탯/성격/계급/직무카드 입력 완료
  3. 커스텀 캐릭터 생성 시 8스탯 배분과 진영 선택 가능
  4. 전투/CP/경제 밸런스 검증 통과
**Plans**: 4 plans (complete)

</details>

### Phase 8: 엔진 통합 + 커맨드 버퍼
**Goal**: 듀얼 전술 엔진이 단일 엔진으로 통합되고, 커맨드 버퍼로 tick-WebSocket 동시성이 보장되며, 지휘 계층 데이터 모델이 전투 상태에 포함된다
**Depends on**: Phase 7 (v2.0 완료)
**Requirements**: ENGINE-01, ENGINE-02, ENGINE-03
**Success Criteria** (what must be TRUE):
  1. TacticalCombatEngine(engine/war/)이 제거 또는 비활성화되고 TacticalBattleEngine 하나만 전술전을 처리한다
  2. WebSocket으로 수신된 전술 명령이 ConcurrentLinkedQueue에 버퍼링되어 tick 시작 시점에 일괄 처리된다 (직접 상태 변경 없음)
  3. TacticalBattleState에 CommandHierarchy(사령관-유닛 매핑, 승계 대기열)가 포함되어 전투 초기화 시 자동 생성된다
**Plans**: 3 plans
Plans:
- [x] 08-01-PLAN.md — TacticalCommand sealed class + CommandHierarchy 데이터 모델 + 테스트 스캐폴드
- [x] 08-02-PLAN.md — 엔진 통합: TacticalUnit 필드 병합, war/ 패키지 삭제, 중복 컨트롤러 삭제
- [x] 08-03-PLAN.md — 커맨드 버퍼 통합 + CommandHierarchy 초기화

### Phase 9: 지휘권 분배 + 커맨드레인지서클
**Goal**: 사령관이 함대를 분함대로 나누어 지휘하고, 커맨드레인지서클 내 유닛에만 명령이 전달되며, 통신 방해가 지휘 체계에 영향을 미친다
**Depends on**: Phase 8
**Requirements**: CMD-01, CMD-02, CMD-03, CMD-04, CMD-05, CMD-06
**Success Criteria** (what must be TRUE):
  1. 사령관이 60유닛을 부사령관/참모에게 분함대로 배정할 수 있고, 온라인->계급->평가->공적 우선순위가 자동 적용된다
  2. 지휘관의 CRC 반경 내 유닛에만 명령이 전달되고, CRC 밖 유닛은 마지막 명령 유지 또는 AI 자율 행동한다
  3. 사령관이 서클 밖이면서 정지 중인 유닛을 실시간으로 재배정할 수 있다
  4. 통신 방해 상태에서 총사령관의 전군 명령이 차단된다
**Plans**: 4 plans
Plans:
- [x] 09-01-PLAN.md — 분함대 배정 데이터 모델 + CommandHierarchyService + CommandPriorityComparator
- [x] 09-02-PLAN.md — CrcValidator + OutOfCrcBehavior 순수 로직
- [x] 09-03-PLAN.md — 엔진 통합: CRC 게이트 + 서클 밖 행동 + 재배정 처리
- [x] 09-04-PLAN.md — 통신 방해 시스템 + WebSocket 엔드포인트

### Phase 10: 지휘 승계
**Goal**: 사령관 부상/사망 시 지휘권이 규칙에 따라 승계되며, 체계 붕괴 시 유닛이 독립 AI로 전환된다
**Depends on**: Phase 9
**Requirements**: SUCC-01, SUCC-02, SUCC-03, SUCC-04, SUCC-05, SUCC-06
**Success Criteria** (what must be TRUE):
  1. 사령관이 사전에 후계자를 지명할 수 있고, 부상 시 지휘력 저하와 함께 지휘권 위임이 가능하다
  2. 사령관 사망(기함 격침) 시 30틱 공백 후 사전 지명자가 승계하며, 지명자 부재/사망 시 차순위 계급자가 자동 승계한다
  3. 분함대장 지휘 불가 시 해당 유닛이 사령관 직할로 복귀한다
  4. 모든 사령관 지휘 불가 시 지휘 체계가 붕괴하여 각 유닛이 독립 AI로 행동한다
**Plans**: 7 plans
Plans:
- [x] 10-01-PLAN.md — 에너지/진형/전투상태 데이터 모델 (tactical combat)
- [x] 10-02-PLAN.md — 전술전 엔진 + 요새포 시스템 (tactical combat)
- [x] 10-03-PLAN.md — WebSocket 전투 컨트롤러 + REST API (tactical combat)
- [x] 10-04-PLAN.md — 프론트엔드 전술전 UI (tactical combat)
- [x] 10-05-PLAN.md — 후계자 지명 + 부상 지휘력 저하 + 위임 (SUCC-01, SUCC-02)
- [x] 10-06-PLAN.md — 30틱 공백 + 자동 승계 + 분함대 복귀 (SUCC-03, SUCC-04, SUCC-05)
- [x] 10-07-PLAN.md — 지휘 체계 붕괴 + 독립 AI 전환 (SUCC-06)

### Phase 11: 전술 AI
**Goal**: 오프라인/NPC 유닛이 작전 목적과 성격에 따라 자동 전투를 수행하며, 위협 평가 기반 퇴각과 에너지/진형 자동 조정이 동작한다
**Depends on**: Phase 9, Phase 10
**Requirements**: TAI-01, TAI-02, TAI-03, TAI-04, TAI-05
**Success Criteria** (what must be TRUE):
  1. AI 유닛이 작전 목적(점령/방어/소탕)에 따라 서로 다른 기본 행동(행성이동/현위치수비/적추격)을 수행한다
  2. 성격 특성(AGGRESSIVE/DEFENSIVE 등)에 따라 교전 거리, 퇴각 임계값, 공격 대상 선택이 달라진다
  3. HP<20% 또는 사기<30% 조건에서 AI가 퇴각 판단을 실행한다
  4. AI가 상황에 따라 에너지 배분, 진형, 태세를 자동 변경하고 집중/분산 공격을 전환한다
**Plans**: 3 plans
Plans:
- [x] 11-01-PLAN.md — 전술 AI 데이터 모델 + 위협 평가 + 성격별 전술 프로파일
- [x] 11-02-PLAN.md — TacticalAI 핵심 로직: 작전 목적별 행동 + 성격별 전투 + 에너지/진형/집중화력
- [x] 11-03-PLAN.md — 엔진 통합: AI 틱 처리 + 즉시 재평가 트리거 + 체계 붕괴 AI 전환

### Phase 12: 작전 연동
**Goal**: 전략 게임의 작전계획이 전술전 AI 행동을 결정하고, 작전 참가 부대가 공적 보상을 받으며, 발령-도달로 작전이 시작된다
**Depends on**: Phase 11
**Requirements**: OPS-01, OPS-02, OPS-03
**Success Criteria** (what must be TRUE):
  1. 전략 게임에서 발령한 작전계획(점령/방어/소탕)의 목적이 전술전 진입 시 AI 기본 행동(TAI-01)에 자동 전달된다
  2. 작전에 참가한 부대가 전투 종료 후 비참가 부대 대비 공적 보너스를 받는다
  3. 발령된 부대가 목표 성계에 도달하면 작전이 자동 시작된다
**Plans**: 4 plans
Plans:
- [x] 12-01-PLAN.md — OperationPlan 엔티티 + V47 마이그레이션 + MissionObjective.defaultForPersonality + 리포지토리
- [x] 12-02-PLAN.md — OperationPlanCommand/CancelCommand 재작성 + WarpNavigationCommand Fleet.planetId 버그 수정
- [x] 12-03-PLAN.md — TacticalBattleState missionObjectiveByFleetId + BattleTriggerService 주입 + sync 채널
- [x] 12-04-PLAN.md — OperationLifecycleService + TickEngine 5.5단계 + endBattle ×1.5 공적 보너스

### Phase 13: 전략 AI
**Goal**: AI 진영이 전쟁 상태에서 자동으로 작전계획을 수립하고 전력 평가에 따라 적절한 작전 유형을 선택한다
**Depends on**: Phase 12
**Requirements**: SAI-01, SAI-02
**Success Criteria** (what must be TRUE):
  1. AI 진영이 교전 중일 때 FactionAI가 자동으로 작전계획(점령/방어/소탕)을 생성한다
  2. 자기 진영 대비 적 전력이 약한 성계에는 점령, 위협받는 자기 성계에는 방어, 침입 함대에는 소탕 작전이 선택된다
**Plans**: 2 plans
Plans:
- [x] 13-01-PLAN.md — 전략 AI 순수 객체 스코어러 (전력평가+안개+작전대상+함대배정)
- [x] 13-02-PLAN.md — FactionAI atWar 분기 교체 + CommandExecutor 연동 + 테스트
### Phase 14: 프론트엔드 통합
**Goal**: 전술전 지휘체계의 모든 백엔드 기능이 프론트엔드에서 시각적으로 표현되고 조작 가능하다
**Depends on**: Phase 8, Phase 9, Phase 10, Phase 11, Phase 12, Phase 13
**Requirements**: FE-01, FE-02, FE-03, FE-04, FE-05
**Success Criteria** (what must be TRUE):
  1. 전술맵에 각 지휘관의 CRC가 기함 위치 중심으로 색상 구분되어 표시되고, 명령 발령 시 서클 축소가 시각적으로 반영된다
  2. 분함대 배정 패널에서 유닛을 드래그하여 지휘관별로 배정/해제할 수 있다
  3. 현재 로그인한 장교의 지휘권에 해당하지 않는 유닛의 명령 버튼이 비활성화된다
  4. 지휘 승계 발생 시 "지휘 승계 중" 카운트다운과 기함 격침 플래시가 표시된다
  5. 색적 범위 밖의 적 유닛이 안개 효과로 숨겨진다
**Plans**: 3 plans
Plans:
- [ ] 08-01-PLAN.md — TacticalCommand sealed class + CommandHierarchy 데이터 모델 + 테스트 스캐폴드
- [ ] 08-02-PLAN.md — 엔진 통합: TacticalUnit 필드 병합, war/ 패키지 삭제, 중복 컨트롤러 삭제
- [ ] 08-03-PLAN.md — 커맨드 버퍼 통합 + CommandHierarchy 초기화
**UI hint**: yes

## Progress

**Execution Order:** 8 -> 9 -> 10 -> 11 -> 12 -> 13 -> 14

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. 레거시 제거 + 함종 유닛 기반 | v2.0 | 5/5 | Complete | 2026-04-06 |
| 2. gin7 81종 커맨드 시스템 | v2.0 | 7/7 | Complete | 2026-04-06 |
| 3. 실시간 전술전 엔진 | v2.0 | 5/5 | Complete | 2026-04-06 |
| 4. 경제 시스템 | v2.0 | 5/5 | Complete | 2026-04-06 |
| 5. AI 시스템 | v2.0 | 3/3 | Complete | 2026-04-06 |
| 6. 프론트엔드 통합 | v2.0 | 8/8 | Complete | 2026-04-06 |
| 7. 시나리오 데이터 + 밸런싱 | v2.0 | 4/4 | Complete | 2026-04-06 |
| 8. 엔진 통합 + 커맨드 버퍼 | v2.1 | 3/3 | Complete | - |
| 9. 지휘권 분배 + CRC | v2.1 | 4/4 | Complete | 2026-04-07 |
| 10. 지휘 승계 | v2.1 | 7/7 | Complete   | 2026-04-07 |
| 11. 전술 AI | v2.1 | 3/3 | Complete    | 2026-04-08 |
| 12. 작전 연동 | v2.1 | 4/4 | Complete    | 2026-04-09 |
| 13. 전략 AI | v2.1 | 2/2 | Complete    | 2026-04-09 |
| 14. 프론트엔드 통합 | v2.1 | 0/0 | Not started | - |
