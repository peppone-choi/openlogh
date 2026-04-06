# Requirements: Open LOGH v2.0

**Defined:** 2026-04-06
**Core Value:** gin7의 핵심인 "조직 시뮬레이션" — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행

## v2.0 Requirements

### 레거시 제거 (LEGACY)

- [ ] **LEGACY-01**: 삼국지 93개 커맨드(농지개간, 상업투자, 징병 등) 완전 제거
- [ ] **LEGACY-02**: 병종 상성 시스템(보병/궁병/기병) 완전 제거
- [ ] **LEGACY-03**: 삼국지 수치비교 자동전투 로직 제거
- [x] **LEGACY-04**: 삼국지 경제 로직(농업/상업 수치) 제거
- [x] **LEGACY-05**: 삼국지 아이템(무기/서적/말) 제거
- [x] **LEGACY-06**: 삼국지 NPC 데이터(general_pool.json) 제거

### 함종/유닛 시스템 (SHIP)

- [x] **SHIP-01**: 11함종 × I~VIII 서브타입 함정 유닛 구현 (ship_stats JSON 기반, 300척 단위)
- [x] **SHIP-02**: 진영별 함종 차이 구현 (제국: 고속전함/뇌격정모함, 동맹: 타격순항함)
- [x] **SHIP-03**: 기함 유닛 구현 (고유명 기함 + 범용기함, 계급별 결정)
- [x] **SHIP-04**: 육전대 유닛 구현 (장갑병/장갑유탄병/경장육전병, ground_unit_stats.json 기반)
- [x] **SHIP-05**: 함정 유닛 상세 스탯 구현 (장갑/실드/무기/속도/승무원/물자적재량)
- [x] **SHIP-06**: 승조원 수련도 시스템 (엘리트/베테란/노멀/그린 4단계)

### 커맨드 시스템 (CMD)

- [x] **CMD-01**: 작전커맨드 16종 구현 (워프항행~육전대철수)
- [x] **CMD-02**: 개인커맨드 15종 구현 (원거리이동~기함구매)
- [x] **CMD-03**: 지휘커맨드 8종 구현 (작전계획~수송중지)
- [x] **CMD-04**: 병참커맨드 6종 구현 (완전수리~할당)
- [x] **CMD-05**: 인사커맨드 10종 구현 (승진~봉토직할)
- [x] **CMD-06**: 정치커맨드 12종 구현 (야회~통치목표)
- [x] **CMD-07**: 첩보커맨드 14종 구현 (일제수색~귀환공작)
- [x] **CMD-08**: 실시간 커맨드 실행 파이프라인 (CP차감→대기시간→실행→결과)
- [x] **CMD-09**: 직무권한카드 기반 커맨드 게이팅 (commands.json 연동)

### 전술전 엔진 (BATTLE)

- [ ] **BATTLE-01**: 전투 개시/종료 조건 구현 (같은 그리드 적아 공존 시 자동 개시)
- [ ] **BATTLE-02**: 에너지 배분 시스템 (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR, 총합 제한)
- [x] **BATTLE-03**: 무기 시스템 구현 (빔/건/미사일/전투정, 사거리/위력/물자소비)
- [x] **BATTLE-04**: 진형 시스템 구현 (방추/함종별/혼성/삼열, 공격/방어/속도 보정)
- [x] **BATTLE-05**: 커맨드레인지서클 구현 (시간경과 확대, 발령시 0 리셋, 지휘스탯 확대율)
- [x] **BATTLE-06**: 색적 시스템 구현 (SENSOR 배분 기반, 거리/유닛종별 정밀도)
- [x] **BATTLE-07**: 요새포 시스템 구현 (토르해머/가이에스하켄, 사선 통과 명중, 아군 피격)
- [x] **BATTLE-08**: 지상전 구현 (육전대 강하, 지상전 박스 30유닛 제한, 행성타입별)
- [x] **BATTLE-09**: 행성/요새 점령 처리 (항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동)
- [ ] **BATTLE-10**: 태세 시스템 (항행/정박/주류/전투 4종, 각각 공격/색적/사기 보정)
- [x] **BATTLE-11**: 전사/부상 시스템 (기함 격침→부상→귀환성 워프)
- [x] **BATTLE-12**: 전술 유닛 커맨드 (이동/회전/평행이동/반전/공격/사격/공전/대열/수리/보급/출격)

### 경제 시스템 (ECON)

- [x] **ECON-01**: 행성 자원 시스템 (인구/생산/교역/치안/지지도/궤도방어/요새방어)
- [ ] **ECON-02**: 조병창 자동생산 (행성별 자동생산 품목, 지배권 이전까지 계속)
- [x] **ECON-03**: 세율/납입률 시스템 (행성별 세수→진영 자금, 90일 주기 징수)
- [x] **ECON-04**: 행성/부대 창고 시스템 (행성창고↔부대창고 간 이동)
- [ ] **ECON-05**: 페잔 차관 시스템 (일시불 차관, 이자 10%/분기, 미상환 시 페잔 엔딩)
- [ ] **ECON-06**: 함대 출격 비용 (출격 중 함대 수 비례 자금 소모, 운영력 절감)

### AI 시스템 (AI)

- [ ] **AI-01**: 성격 기반 행동 시스템 (5종 PersonalityTrait 가중치 의사결정)
- [ ] **AI-02**: 오프라인 플레이어 자동 행동 (플레이어 스탯으로 AI 가동)
- [ ] **AI-03**: 진영 AI (작전수립, 예산 배분, 인사 자동 처리)
- [ ] **AI-04**: 시나리오 이벤트 AI (쿠데타 조건 감지, 내전 트리거)

### 프론트엔드 (FE)

- [ ] **FE-01**: 은하맵 재작성 (도트스타일 성계 아이콘, 진영 색상 기반 5단계 음영)
- [ ] **FE-02**: 전술전 전략맵 UI (도형 아이콘 △□◇, 커맨드레인지서클, 정보패널)
- [ ] **FE-03**: 전투 접근전 뷰 (상하 2분할, 상단 연출뷰+하단 전술맵, React Three Fiber)
- [ ] **FE-04**: 전략 게임 화면 (성계맵 확대, 정보뷰, 직무권한카드 탭, 동스폿 캐릭터)
- [ ] **FE-05**: 커맨드 실행 UI (직무권한카드→커맨드 목록→CP표시→실시간 실행)
- [ ] **FE-06**: 함대/행성 관리 UI (부대 편성, 승조원, 창고, 자원, 시설, 조병창)
- [ ] **FE-07**: 정치 UI (제국 쿠데타/귀족, 동맹 의회/선거, 페잔 차관/정보)
- [ ] **FE-08**: 삼국지 UI 잔재 완전 제거 (city/nation/troop 용어, 컴포넌트)

### 시나리오 데이터 (SCEN)

- [ ] **SCEN-01**: 10개 시나리오 초기 데이터 (인물 배치, 함대 편성, 행성 상태)
- [ ] **SCEN-02**: LOGH 원작 캐릭터 데이터 (8스탯, 성격, 계급, 직무카드)
- [ ] **SCEN-03**: 커스텀 캐릭터 생성 시스템 (8스탯 배분, 진영 선택)

### 밸런싱/테스트 (TEST)

- [ ] **TEST-01**: 함종 간 밸런스 검증 (88 서브타입 전투 시뮬레이션)
- [ ] **TEST-02**: 커맨드 CP 비용/대기시간 밸런싱
- [ ] **TEST-03**: 경제 밸런스 (세율/생산/소비 순환)
- [ ] **TEST-04**: 통합 테스트 (전술전→경제→인사 연동)

## v3.0 Requirements (Deferred)

### 고급 기능

- **ADV-01**: 특수장비 시스템 (원작 설정 기반)
- **ADV-02**: 관세 시스템 (교역 품목별 관세율)
- **ADV-03**: 제국/동맹 예산 분리 (군무성+통수본부)
- **ADV-04**: 시나리오 이벤트 스크립팅 (쿠데타/내전 상세)
- **ADV-05**: 승리 화면 4단계 평가 (결정적/한정적/국지적/패배)

## Out of Scope

| Feature | Reason |
|---------|--------|
| 모바일 앱 | 웹 우선, 모바일 이후 |
| 음성 채팅 | 복잡도 과다, 텍스트 채팅으로 충분 |
| 3D 함선 모델링 | 스프라이트/로우폴리로 대체, 성능 우선 |
| 페잔 플레이어블 | gin7 매뉴얼 기준 NPC 전용 |
| 실시간 음성 지휘 | 외부 디스코드 연동으로 대체 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LEGACY-01 | Phase 1 | Pending |
| LEGACY-02 | Phase 1 | Pending |
| LEGACY-03 | Phase 1 | Pending |
| LEGACY-04 | Phase 1 | Complete |
| LEGACY-05 | Phase 1 | Complete |
| LEGACY-06 | Phase 1 | Complete |
| SHIP-01 | Phase 1 | Complete |
| SHIP-02 | Phase 1 | Complete |
| SHIP-03 | Phase 1 | Complete |
| SHIP-04 | Phase 1 | Complete |
| SHIP-05 | Phase 1 | Complete |
| SHIP-06 | Phase 1 | Complete |
| CMD-01 | Phase 2 | Complete |
| CMD-02 | Phase 2 | Complete |
| CMD-03 | Phase 2 | Complete |
| CMD-04 | Phase 2 | Complete |
| CMD-05 | Phase 2 | Complete |
| CMD-06 | Phase 2 | Complete |
| CMD-07 | Phase 2 | Complete |
| CMD-08 | Phase 2 | Complete |
| CMD-09 | Phase 2 | Complete |
| BATTLE-01 | Phase 3 | Pending |
| BATTLE-02 | Phase 3 | Pending |
| BATTLE-03 | Phase 3 | Complete |
| BATTLE-04 | Phase 3 | Complete |
| BATTLE-05 | Phase 3 | Complete |
| BATTLE-06 | Phase 3 | Complete |
| BATTLE-07 | Phase 3 | Complete |
| BATTLE-08 | Phase 3 | Complete |
| BATTLE-09 | Phase 3 | Complete |
| BATTLE-10 | Phase 3 | Pending |
| BATTLE-11 | Phase 3 | Complete |
| BATTLE-12 | Phase 3 | Complete |
| ECON-01 | Phase 4 | Complete |
| ECON-02 | Phase 4 | Pending |
| ECON-03 | Phase 4 | Complete |
| ECON-04 | Phase 4 | Complete |
| ECON-05 | Phase 4 | Pending |
| ECON-06 | Phase 4 | Pending |
| AI-01 | Phase 5 | Pending |
| AI-02 | Phase 5 | Pending |
| AI-03 | Phase 5 | Pending |
| AI-04 | Phase 5 | Pending |
| FE-01 | Phase 6 | Pending |
| FE-02 | Phase 6 | Pending |
| FE-03 | Phase 6 | Pending |
| FE-04 | Phase 6 | Pending |
| FE-05 | Phase 6 | Pending |
| FE-06 | Phase 6 | Pending |
| FE-07 | Phase 6 | Pending |
| FE-08 | Phase 6 | Pending |
| SCEN-01 | Phase 7 | Pending |
| SCEN-02 | Phase 7 | Pending |
| SCEN-03 | Phase 7 | Pending |
| TEST-01 | Phase 7 | Pending |
| TEST-02 | Phase 7 | Pending |
| TEST-03 | Phase 7 | Pending |
| TEST-04 | Phase 7 | Pending |

**Coverage:**
- v2.0 requirements: 51 total
- Mapped to phases: 51
- Unmapped: 0

---
*Requirements defined: 2026-04-06*
*Last updated: 2026-04-06 — traceability filled by roadmapper*
