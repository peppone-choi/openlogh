# Roadmap: Open LOGH v2.0 — gin7 게임 로직 전면 재작성

## Overview

삼국지 기반 게임 로직을 gin7 매뉴얼 기준으로 전면 재작성한다. 레거시 제거와 함종 기반 유닛 시스템 확립(Phase 1)에서 시작해, 직무권한카드 기반 81종 커맨드 시스템(Phase 2), 실시간 전술전 엔진(Phase 3), 경제 시스템(Phase 4), AI 시스템(Phase 5), 프론트엔드 전면 재작성(Phase 6), 시나리오 데이터 및 밸런싱(Phase 7) 순으로 진행한다. 각 페이즈는 다음 페이즈의 전제조건이며, Phase 4는 Phase 3과 병렬 진행 가능하다.

## Phases

- [x] **Phase 1: 레거시 제거 + 함종 유닛 기반** - 삼국지 잔재 완전 제거 및 gin7 ShipUnit 엔티티 수립 (completed 2026-04-06)
- [ ] **Phase 2: gin7 81종 커맨드 시스템** - 직무권한카드 기반 커맨드 전면 재구현
- [ ] **Phase 3: 실시간 전술전 엔진** - 에너지/무기/진형/색적/요새포/지상전 구현
- [ ] **Phase 4: 경제 시스템** - 행성자원/조병창/세율/차관 경제 루프 구현
- [ ] **Phase 5: AI 시스템** - 성격 기반 NPC AI 및 진영 AI 구현
- [ ] **Phase 6: 프론트엔드 통합** - 은하맵/전술전 UI/전략게임 화면 전면 재작성
- [ ] **Phase 7: 시나리오 데이터 + 밸런싱** - 10개 시나리오 데이터 및 균형 조정

## Phase Details

### Phase 1: 레거시 제거 + 함종 유닛 기반
**Goal**: 삼국지 게임 로직이 완전히 제거되고 gin7 함종/유닛 엔티티가 전투와 커맨드의 기반으로 작동한다
**Depends on**: Nothing (first phase)
**Requirements**: LEGACY-01, LEGACY-02, LEGACY-03, LEGACY-04, LEGACY-05, LEGACY-06, SHIP-01, SHIP-02, SHIP-03, SHIP-04, SHIP-05, SHIP-06
**Success Criteria** (what must be TRUE):
  1. CommandRegistry에 삼국지 커맨드가 0개 존재한다 (gin7 stub 81종으로 대체됨)
  2. ShipUnit 엔티티가 DB에 존재하며 11함종 × 서브타입 스탯이 ship_stats JSON에서 로드된다
  3. 기함/육전대 유닛이 Fleet에 연결 가능하며 승조원 수련도 4단계가 적용된다
  4. 삼국지 병종 상성 및 수치비교 전투 로직이 코드베이스에 존재하지 않는다
  5. `grep -r "officerLevel >= 5"` 결과가 0이거나 stub 대체가 완료된 상태이다
**Plans**: 5 plans

Plans:
- [x] 01-01-PLAN.md — Gin7CommandRegistry stub (81종) + 삼국지 커맨드 파일 삭제
- [x] 01-02-PLAN.md — 삼국지 전투엔진 삭제 (BattleEngine/BattleService/GroundBattleEngine)
- [x] 01-03-PLAN.md — EconomyService 삼국지 로직 제거 + processMonthly() stub
- [x] 01-04-PLAN.md — ShipUnit 엔티티 + V45 DB 마이그레이션 + ShipUnitRepository
- [x] 01-05-PLAN.md — ShipStatRegistry (JSON 로드) + ShipUnitService + officerLevel >= 5 전량 제거

### Phase 2: gin7 81종 커맨드 시스템
**Goal**: 직무권한카드 기반 81종 gin7 커맨드가 실시간 실행 파이프라인을 통해 동작하며 삼국지 권한 우회가 완전히 제거된다
**Depends on**: Phase 1
**Requirements**: CMD-01, CMD-02, CMD-03, CMD-04, CMD-05, CMD-06, CMD-07, CMD-08, CMD-09
**Success Criteria** (what must be TRUE):
  1. 플레이어가 직무권한카드를 통해서만 해당 커맨드 그룹에 접근할 수 있다 (officerLevel >= 5 우회 0건)
  2. 커맨드 실행 시 CP 차감 → 대기시간 → 실행 → WebSocket 결과 브로드캐스트 흐름이 동작한다
  3. 7개 커맨드 그룹(작전/개인/지휘/병참/인사/정치/첩보) 81종이 모두 CommandRegistry에 등록된다
  4. 계급이 낮은 장교가 상급자에게 제안(제안커맨드)을 발행하고 승인/거부가 처리된다
**Plans**: 7 plans

Plans:
- [x] 02-01-PLAN.md — PositionCardRegistry gin7 매핑 교체 + Gin7CommandRegistry MCP/PCP 분리
- [x] 02-02-PLAN.md — 작전커맨드 16종 + 병참커맨드 6종 구현체
- [x] 02-03-PLAN.md — 개인커맨드 15종 + 인사커맨드 10종 구현체
- [x] 02-04-PLAN.md — 정치커맨드 12종 + 첩보커맨드 14종 구현체
- [x] 02-05-PLAN.md — 지휘커맨드 8종 구현체 + stub 전량 제거
- [x] 02-06-PLAN.md — 제안 시스템 (CommandProposal 엔티티 + 승인/거부 API)
- [ ] 02-07-PLAN.md — 통합 테스트 (81종 등록 + 파이프라인 + 제안 시스템)

### Phase 3: 실시간 전술전 엔진
**Goal**: 에너지 배분, 무기 시스템, 진형, 커맨드레인지서클, 색적, 요새포, 지상전을 포함한 gin7 전술전이 실시간으로 동작한다
**Depends on**: Phase 1, Phase 2 (일부 전술 커맨드는 CMD 시스템 필요)
**Requirements**: BATTLE-01, BATTLE-02, BATTLE-03, BATTLE-04, BATTLE-05, BATTLE-06, BATTLE-07, BATTLE-08, BATTLE-09, BATTLE-10, BATTLE-11, BATTLE-12
**Success Criteria** (what must be TRUE):
  1. 같은 그리드에 적아 유닛이 공존하면 전투가 자동 개시되고, 종료 조건 달성 시 정상 종료된다
  2. BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR 6채널 에너지 슬라이더 합계가 100을 유지하며 WebSocket으로 실시간 반영된다
  3. 빔/건/미사일(물자소비) 무기가 사거리/위력/보정에 따라 피해를 계산하고 88 서브타입 스탯이 적용된다
  4. 커맨드레인지서클이 tick 경과에 따라 확대되고 명령 발령 시 0으로 리셋된다
  5. 육전대 강하 후 지상전이 시작되며 6종 행성 점령 방식(항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동)이 각기 다른 결과를 낸다
**Plans**: 5 plans

Plans:
- [x] 03-01-PLAN.md — TacticalUnit 확장 + 태세/에너지 WebSocket 채널 + 자동 전투 개시
- [x] 03-02-PLAN.md — 미사일/전투정 무기 시스템 + BEAM 사거리 곡선 + 색적 DetectionService
- [x] 03-03-PLAN.md — 요새포 4종 FortressGunSystem + 전투 REST API + 퇴각/공격대상 채널
- [x] 03-04-PLAN.md — 지상전 박스(30유닛 제한) + 행성 점령 6종 PlanetConquestService
- [x] 03-05-PLAN.md — 기함 격침→부상→귀환성 워프 + 전술 커맨드 11종 + 통합 테스트
**UI hint**: yes

### Phase 4: 경제 시스템
**Goal**: 행성 자원 생산, 조병창 자동생산, 세율/납입, 창고 이동, 페잔 차관이 TickEngine의 월별 파이프라인을 통해 순환된다
**Depends on**: Phase 2 (커맨드 시스템으로 경제 커맨드 실행)
**Requirements**: ECON-01, ECON-02, ECON-03, ECON-04, ECON-05, ECON-06
**Success Criteria** (what must be TRUE):
  1. TickEngine.runMonthlyPipeline()이 Gin7EconomyService를 호출하여 90일 주기 세수가 진영 자금에 반영된다
  2. 행성 조병창이 tick마다 자동으로 함선/지상유닛을 생산하며 플레이어 조작 없이 지속된다
  3. 행성창고와 부대창고 간 자원 이동이 가능하다
  4. 페잔 차관 이후 미상환 시 페잔 엔딩 조건이 트리거된다
**Plans**: 5 plans

Plans:
- [ ] 04-01-PLAN.md — Gin7EconomyService (세율 징수 + 행성자원 성장) + TickEngine wiring
- [ ] 04-02-PLAN.md — ShipyardProductionService gin7 확장 + TickEngine 주기적 호출
- [ ] 04-03-PLAN.md — 행성↔부대창고 transfer/return API (WarehouseService + WarehouseController)
- [ ] 04-04-PLAN.md — 페잔 차관 엔딩 트리거 (FezzanEndingService) + 함대 출격비용 (FleetSortieCostService)
- [ ] 04-05-PLAN.md — 경제 시스템 통합 테스트 (ECON-01~06 전체 검증)

### Phase 5: AI 시스템
**Goal**: 오프라인 플레이어와 NPC가 gin7 81종 커맨드 시스템을 통해 성격 기반으로 행동하며, 진영 AI가 예산/인사/작전을 자율 처리한다
**Depends on**: Phase 2 (AI는 동일한 커맨드 파이프라인 사용)
**Requirements**: AI-01, AI-02, AI-03, AI-04
**Success Criteria** (what must be TRUE):
  1. 오프라인 상태의 플레이어 캐릭터가 해당 플레이어의 스탯과 성격 가중치에 따라 커맨드를 자동 실행한다
  2. 진영 AI가 예산 배분과 인사 처리를 자율적으로 수행하며 NPC 진영이 독립적으로 행동 가능하다
  3. 쿠데타 조건이 감지되면 시나리오 이벤트 AI가 내전 트리거를 발동한다
  4. NPC AI가 1 tick당 1 그룹 처리(슬롯 기반 스케줄링)로 O(n) 성능 문제 없이 동작한다
**Plans**: TBD

### Phase 6: 프론트엔드 통합
**Goal**: 은하영웅전설 세계관의 전략 게임 화면, 전술전 UI, 은하맵이 삼국지 잔재 없이 한국어로 완성되며 WebSocket 구독이 모든 백엔드 시스템에 연결된다
**Depends on**: Phase 1, Phase 2, Phase 3 (UI가 의존하는 백엔드 시스템)
**Requirements**: FE-01, FE-02, FE-03, FE-04, FE-05, FE-06, FE-07, FE-08
**Success Criteria** (what must be TRUE):
  1. 은하맵에서 도트스타일 성계 아이콘과 진영 색상 5단계 음영이 표시되며 함대 위치와 이동 범위가 하이라이트된다
  2. 전술전 화면이 상단 3D 접근전 뷰(React Three Fiber)와 하단 2D 전술맵(React Konva)으로 분할되며 에너지 슬라이더가 실시간 동작한다
  3. 전략 게임 화면에서 직무권한카드 탭 → 커맨드 목록 → CP 표시 → 실행 흐름이 완전히 동작한다
  4. city/nation/troop 등 삼국지 용어와 컴포넌트가 코드베이스에 0건 존재한다
  5. 진영별 정치 UI(제국 쿠데타/귀족, 동맹 의회/선거, 페잔 차관/정보)가 동작한다
**Plans**: TBD
**UI hint**: yes

### Phase 7: 시나리오 데이터 + 밸런싱
**Goal**: 10개 시나리오의 초기 데이터가 완비되고, 플레이테스트 기반 밸런스 조정이 적용되어 실제 플레이 가능한 게임이 완성된다
**Depends on**: Phase 1, Phase 2, Phase 3, Phase 4, Phase 5, Phase 6
**Requirements**: SCEN-01, SCEN-02, SCEN-03, TEST-01, TEST-02, TEST-03, TEST-04
**Success Criteria** (what must be TRUE):
  1. 10개 시나리오 모두 인물 배치, 함대 편성, 행성 상태 초기값이 DB에 적재되어 게임 시작이 가능하다
  2. LOGH 원작 캐릭터(라인하르트, 양웬리 등)의 8스탯/성격/계급/직무카드가 gin7 매뉴얼 기준으로 입력된다
  3. 커스텀 캐릭터 생성 시 8스탯 배분과 진영 선택이 가능하다
  4. 88 서브타입 전투 시뮬레이션, CP 비용/대기시간, 경제 순환이 밸런스 검증 통과 상태이다
**Plans**: TBD

## Progress

**Execution Order:** 1 → 2 → 3 → 4 (Phase 3과 병렬 가능) → 5 → 6 → 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. 레거시 제거 + 함종 유닛 기반 | 8/8 | Complete   | 2026-04-06 |
| 2. gin7 81종 커맨드 시스템 | 6/7 | In Progress|  |
| 3. 실시간 전술전 엔진 | 2/5 | In Progress|  |
| 4. 경제 시스템 | 0/5 | Not started | - |
| 5. AI 시스템 | 0/TBD | Not started | - |
| 6. 프론트엔드 통합 | 0/TBD | Not started | - |
| 7. 시나리오 데이터 + 밸런싱 | 0/TBD | Not started | - |
