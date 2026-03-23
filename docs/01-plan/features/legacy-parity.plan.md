# Plan: Legacy Parity (전체 패러티 점검 및 버그 수정)

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | legacy-parity (레거시 PHP 전체 패러티 점검 및 버그 수정) |
| 작성일 | 2026-03-23 |
| 목표 기간 | 단계별 진행 (Phase 1~10) |

### Value Delivered

| 관점 | 내용 |
|------|------|
| **Problem** | 레거시 PHP(devsam/core)와 현재 구현 간 로직 불일치 및 미발견 버그가 존재할 수 있음 |
| **Solution** | 10개 영역을 체계적으로 비교 점검하고, 의도적 변경 외 모든 불일치를 수정 |
| **Function UX Effect** | 게임 플레이가 레거시와 동일한 결과를 보장하여 유저 기대 충족 |
| **Core Value** | 검증된 패러티로 안정적인 서비스 런칭 가능 |

---

## 1. 배경 및 목적

### 1.1 현황

| 항목 | Legacy PHP | Current (Kotlin/Next.js) | 상태 |
|------|-----------|--------------------------|------|
| 장수 커맨드 | 55개 | 55개 | 구조 완료, 로직 검증 필요 |
| 국가 커맨드 | 38개 | 38개 | 구조 완료, 로직 검증 필요 |
| API 엔드포인트 | 78개 (14 카테고리) | 50+ 컨트롤러 | 매핑 검증 필요 |
| 시나리오 | 83개 | 84개 | 완료 (+1 추가) |
| 맵 | 8개 | 9개 | 완료 (+1 duel) |
| NPC AI | GeneralAI.php (4,293줄) | GeneralAI + NationAI | 로직 검증 필요 |
| 전투 시스템 | process_war.php (33KB) | BattleEngine + WarFormula | 공식 검증 필요 |
| 경제 시스템 | func.php (80KB) | EconomyService | 계산 검증 필요 |
| 이벤트 시스템 | func_time_event.php | EventService | 트리거 검증 필요 |
| 테스트 | 없음 | 89개 (패러티 테스트 포함) | 테스트 결과 확인 필요 |

### 1.2 목적

레거시 PHP 코드와 현재 구현의 **게임 로직 동일성**을 보장한다.
- 의도적 변경 외 모든 불일치를 발견하고 수정
- 기존 패러티 테스트 실행 및 실패 항목 수정
- 코드 비교를 통한 미발견 로직 차이 탐색

---

## 2. 의도적 변경사항 (패러티 점검 제외)

다음 항목은 의도적으로 레거시와 다르게 변경한 것이므로 패러티 점검에서 제외한다.

| 영역 | 변경 내용 | 이유 |
|------|----------|------|
| **스탯 시스템** | 3-stat → 5-stat (통솔/무력/지력 + 정치/매력) | opensamguk 확장 |
| **DB** | MariaDB → PostgreSQL | 현대적 DB 채택 |
| **NPC 토큰** | DB 테이블 → Redis | 성능 최적화 |
| **서버 모델** | per-server profile → World = Profile | 아키텍처 개선 |
| **프로세스** | 단일 → Gateway + Game 멀티프로세스 | 확장성 |
| **턴 엔진** | PHP → CQRS 기반 Kotlin 엔진 | 아키텍처 개선 |
| **인증** | 세션 기반 → OAuth/JWT | 보안 강화 |
| **UI/UX** | PHP 웹페이지 → Next.js SPA | 현대적 UI |
| **필드 네이밍** | intelligence → intel | core 원본 준수 |

---

## 3. 점검 영역 (10 Phase)

### Phase 1: 기존 패러티 테스트 실행 및 실패 분석
> **우선도: 최상** | 가장 빠르게 불일치를 발견할 수 있음

**대상 테스트:**
- `CommandParityTest` — 커맨드 결과 패러티
- `BattleParityTest` / `BattleEngineParityTest` — 전투 패러티
- `FormulaParityTest` — 데미지/명중 공식 패러티
- `NpcAiParityTest` — NPC AI 패러티
- `DeterministicReplayParityTest` — 결정론적 리플레이 패러티
- `ConstraintParityTest` — 커맨드 제약조건 패러티
- `WarUnitCityParityTest` — 도시 방어 패러티
- `GoldenValueTest` — 골든값 패러티
- `ApiContractTest` — API 계약 패러티

**검증 방법:**
```bash
cd backend && ./gradlew :game-app:test \
  --tests 'com.opensam.qa.parity.*' \
  --tests 'com.opensam.qa.ApiContractTest' \
  --tests 'com.opensam.qa.GoldenValueTest' \
  --tests 'com.opensam.command.CommandParityTest' \
  --tests 'com.opensam.engine.FormulaParityTest' \
  --tests 'com.opensam.engine.DeterministicReplayParityTest' \
  --no-daemon
```

**성공 기준:** 모든 패러티 테스트 통과

---

### Phase 2: 커맨드 로직 패러티 (55 장수 + 38 국가)
> **우선도: 상** | 게임플레이의 핵심

**장수 커맨드 점검 (55개):**

| 카테고리 | 커맨드 | 점검 항목 |
|----------|--------|----------|
| 이동/전투 | 이동, 출병, 강행, 거병, 집합, 전투태세, 탈취, 파괴, 화계 | 이동 로직, 전투 판정, 결과 계산 |
| 내정 | 모병, 징병, 훈련, 단련, 숙련전환, 농지개간, 상업투자, 기술연구, 물자조달, 사기진작, 치안강화, 성벽보수, 수비강화, 군량매매 | 수치 변화량, 제약조건, 성공/실패 판정 |
| 인사/정치 | 등용, 등용수락, 임관, 장수대상임관, 선양, 랜덤임관, 건국, 무작위건국, 모반시도, 선동, 인재탐색, 견문 | 성공 확률, 조건 판정 |
| 경제 | 증여, 장비매매, 헌납 | 자원 이동량, 수수료 |
| 기타 | 방랑, 귀환, 접경귀환, 소집해제, 주민선정, 정착장려, 은퇴, 요양, 휴식, NPC능동, CR건국, CR맹훈련 | 상태 변화, 조건 |

**국가 커맨드 점검 (38개):**

| 카테고리 | 커맨드 | 점검 항목 |
|----------|--------|----------|
| 외교 | 선전포고, 종전제의/수락, 불가침제의/수락, 불가침파기제의/수락 | 외교 상태 전이 |
| 군사/전략 | 발령, 급습, 의병모집, 필사즉생, 초토화, 피장파장, 허보, 부대탈퇴지시 | 효과 계산, 조건 |
| 행정 | 천도, 무작위수도이전, 국호변경, 국기변경, 포상, 몰수, 백성동원, 물자원조, 감축, 증축, 수몰, 이호경식 | 수치/상태 변화 |
| 연구 | event_상병~원융노병 (9종) | 연구 조건, 결과 |

**점검 방법:**
1. legacy PHP 커맨드 파일 (`hwe/sammo/Command/General/`, `Nation/`) 읽기
2. 대응 Kotlin 구현 비교
3. 수식, 조건문, 결과값 1:1 대조
4. 불일치 시 패러티 테스트 추가 후 수정

---

### Phase 3: 전투/전쟁 시스템 패러티
> **우선도: 상** | 가장 복잡한 로직

**비교 대상:**
- Legacy: `hwe/process_war.php` (33KB)
- Current: `BattleEngine`, `WarFormula`, `WarUnit*`, `BattleTrigger`

**점검 항목:**
- [ ] 데미지 공식 (물리/지략/화공)
- [ ] 명중/회피 계산
- [ ] 크루타입별 상성
- [ ] 특수능력 효과
- [ ] 장비 보너스
- [ ] 성벽 방어 공식
- [ ] 전투 결과 처리 (사망, 포로, 부상, 경험치)
- [ ] 전투 후 처리 (NPC 자동합류, 도시 함락)
- [ ] 결정론적 RNG 일치

---

### Phase 4: NPC AI 패러티
> **우선도: 상** | 게임 진행의 핵심

**비교 대상:**
- Legacy: `hwe/sammo/GeneralAI.php` (4,293줄, 150KB)
- Current: `GeneralAI` + `NationAI`

**점검 항목:**
- [ ] 방랑 NPC 행동 결정 로직
- [ ] 부대장 집합 로직
- [ ] 내정 행동 우선순위
- [ ] 군사 행동 결정 (이동, 공격 대상 선정)
- [ ] 정치 행동 (등용, 외교)
- [ ] 국가 AI 전략 결정
- [ ] AI 성격/특성에 따른 행동 분기
- [ ] NPC 스폰/부활 조건

---

### Phase 5: 경제 시스템 패러티
> **우선도: 중상**

**비교 대상:**
- Legacy: `hwe/func.php` 내 경제 관련 함수들
- Current: `EconomyService`

**점검 항목:**
- [ ] 도시 수입 계산 (금/쌀)
- [ ] 세율 적용
- [ ] 반기 이벤트
- [ ] 재난 효과 (메뚜기, 홍수)
- [ ] 교역 계산
- [ ] 도시 레벨별 한계치

---

### Phase 6: 턴 처리 순서 패러티
> **우선도: 중상**

**비교 대상:**
- Legacy: `src/daemon.ts` + `hwe/func_process.php`
- Current: `TurnService` 파이프라인

**점검 항목:**
- [ ] 턴 처리 단계 순서 일치
  1. 장수 커맨드 실행 (AI 포함)
  2. 보급 상태 갱신
  3. 이벤트 처리 (PRE_MONTH, MONTH)
  4. 월 진행
  5. 경제 파이프라인
  6. 외교 턴 처리
  7. 장수 유지보수 (노화, 경험, 충성, 부상, 은퇴)
  8. NPC 스폰 및 통일 체크
- [ ] 각 단계 내 세부 실행 순서

---

### Phase 7: 이벤트/트리거 시스템 패러티
> **우선도: 중**

**비교 대상:**
- Legacy: `hwe/func_time_event.php`, `BaseGeneralTrigger.php`, `BaseWarUnitTrigger.php`
- Current: `EventService`, `EventActionService`, `TriggerCaller`

**점검 항목:**
- [ ] 시간 이벤트 종류 및 발동 조건
- [ ] 장수 트리거 (특수능력, 아이템, 관직)
- [ ] 전투 트리거 (크루타입, 장비, 특성)
- [ ] 트리거 우선순위 및 중첩 처리

---

### Phase 8: API 엔드포인트 매핑 패러티
> **우선도: 중**

**비교 대상:**
- Legacy: `hwe/sammo/API/` 78개 엔드포인트 (14 카테고리)
- Current: 50+ 컨트롤러

**점검 항목:**
- [ ] 모든 레거시 API 엔드포인트가 매핑되어 있는지 확인
- [ ] 요청/응답 데이터 구조 일치
- [ ] 에러 처리 동작 일치
- [ ] 카테고리별 매핑표 작성

**레거시 API 카테고리 (78개):**
| 카테고리 | 수 | 현재 매핑 컨트롤러 |
|----------|---|-------------------|
| Auction | 9 | AuctionController |
| Betting | 3 | TournamentController |
| Command | 5 | CommandController |
| General | 8 | GeneralController, AccountController |
| Global | 14 | WorldController, MapController, RankingController |
| InheritAction | 8 | InheritanceController |
| Message | 7 | MessageController |
| Misc | 1 | AccountController (icon) |
| Nation | 11 | NationController, NationPolicyController |
| NationCommand | 5 | CommandController |
| Troop | 5 | TroopController |
| Vote | 5 | VoteController |

---

### Phase 9: 게임 데이터/리소스 패러티
> **우선도: 중하**

**점검 항목:**
- [ ] 시나리오 NPC 장수 데이터 일치 (83개 시나리오)
- [ ] 맵 데이터 일치 (도시 좌표, 연결, 속성)
- [ ] 아이템/장비 데이터 일치
- [ ] 병종 데이터 일치 (7종 유닛셋)
- [ ] 관직 데이터 일치 (국가 레벨별)
- [ ] game_const.json vs 레거시 상수 일치

---

### Phase 10: 프론트엔드 출력 패러티
> **우선도: 하** | UI/UX 의도적 변경이므로 데이터 정확성만 점검

**점검 항목:**
- [ ] 장수 정보 표시 항목 누락 없음
- [ ] 국가 정보 표시 항목 누락 없음
- [ ] 도시 정보 표시 항목 누락 없음
- [ ] 전투 로그 표시 정확성
- [ ] 랭킹/명예의전당 항목 일치
- [ ] 역사 기록 항목 일치

---

## 4. 작업 순서 및 전략

```
Phase 1 (테스트 실행)
  ↓ 실패 항목 발견
Phase 2 (커맨드 패러티) + Phase 3 (전투 패러티) ← 병렬 가능
  ↓
Phase 4 (NPC AI) + Phase 5 (경제)  ← 병렬 가능
  ↓
Phase 6 (턴 처리) + Phase 7 (이벤트) ← 병렬 가능
  ↓
Phase 8 (API) + Phase 9 (데이터) ← 병렬 가능
  ↓
Phase 10 (프론트엔드)
```

### 원칙
1. **코드로만 판단** — 문서가 아닌 legacy-core/ PHP 코드를 진실의 근거로 사용
2. **의도적 변경 존중** — 섹션 2의 변경사항은 건드리지 않음
3. **테스트 우선** — 불일치 발견 시 패러티 테스트 추가 → 수정 → 통과 확인
4. **최소 변경** — 패러티를 맞추기 위한 최소한의 코드 변경만 수행

---

## 5. 성공 기준

| 기준 | 목표 |
|------|------|
| 패러티 테스트 통과율 | 100% |
| 커맨드 로직 일치 | 93/93 (의도적 변경 제외) |
| 전투 공식 일치 | 모든 데미지/명중 공식 |
| NPC AI 행동 일치 | 동일 입력 → 동일 출력 |
| API 엔드포인트 매핑 | 78/78 레거시 API 커버 |
| 게임 데이터 일치 | 모든 리소스 데이터 |

---

## 6. 리스크

| 리스크 | 영향 | 대응 |
|--------|------|------|
| GeneralAI.php 4,293줄 비교 작업량 | 높음 | 기능별 분할 비교, 우선순위 정해 순차 진행 |
| func.php 80KB 내 산재된 경제 로직 | 중 | 함수 단위로 격리하여 비교 |
| 5-stat 확장에 의한 연쇄 차이 | 중 | politics/charm 관련 분기는 의도적 변경으로 분류 |
| 결정론적 RNG 차이 | 높음 | DeterministicReplayParityTest로 검증 |

---

## 7. 코드 비교 결과 (2026-03-23 실행)

### 7.1 테스트 결과

| 항목 | 결과 |
|------|------|
| 패러티 테스트 | 181/181 PASS |
| 백엔드 전체 | 1136/1136 PASS (24건 수정 완료) |
| 프론트엔드 | 237/237 PASS |
| 타입체크 | PASS |

### 7.2 발견된 CRITICAL 이슈 (21건)

| # | 카테고리 | 이슈 | PHP | Kotlin | 수정 유형 |
|---|---------|------|-----|--------|----------|
| C1 | 내정 | trainDelta/atmosDelta 600배 | 30 | 0.05 | 상수 |
| C2 | 내정 | 물자조달 공식 불일치 | ExpLevelBonus, 크리티컬2.2~3.0 | 미적용, 1.5 | 로직 |
| C3 | 내정 | 징병 최소인구 10배 | 30000 | 3000 | 상수 |
| C4 | 전투 | 화계 비용 20배 | develcost*5 | develcost*0.25 | 공식 |
| C5 | 전투 | 사보타지 확률 | 0.35 | 0.2 | 상수 |
| C6 | 전투 | 거리 폴백 | 99 | 1 | 상수 |
| C7 | 전투 | 전투 경험치 미구현 | damage/50, 도시1000 | 없음 | 신규 |
| C8 | 전투 | 방어자 부상 미적용 | 양측 5% | 공격자만 | 로직 |
| C9 | 전투 | 방어자 자격 필터 누락 | rice/train/atmos 확인 | crew>0만 | 로직 |
| C10 | 전투 | 수도 이전 알고리즘 | 가장 가까운 도시 | 인구 최대 | 로직 |
| C11 | NPC AI | minWarCrew 하드코딩 | 1500 (정책값) | 500 | 상수 |
| C12 | NPC AI | 선전포고 확률 지수 | ^6 | ^1.5 | 상수 |
| C13 | NPC AI | 출병 전방 조건 | front>=2 | frontState!=0 | 조건 |
| C14 | NPC AI | 전투준비 임계값 | 90 (정책값) | 80 | 상수 |
| C15 | 인사 | 등용 비용 *10 누락 | round()*10 | round() | 공식 |
| C16 | 국가 | 백성동원 반대 효과 | 방어↑ | 인구↓ | 재작성 |
| C17 | 국가 | 이호경식 다른 기능 | 전쟁기간 연장 | 제3국 선포 | 재작성 |
| C18 | 국가 | 감축 반대 효과 | 6스탯↓+환급 | 인구만↓+차감 | 재작성 |
| C19 | 연구 | 산저병/음귀병/화시병 수치 | preReq=11,cost=50000 | preReq=23,cost=100000 | 상수 |
| C20 | 외교 | 7개 외교 커맨드 상태 체크 누락 | 상태 확인 | 미확인 | 제약조건 |
| C21 | 분류 | UI에 숨겨야 할 커맨드 노출 | canDisplay=false | 기본 노출 | 로직 |

### 7.3 발견된 HIGH 이슈 (15건)

| # | 이슈 | 영향 |
|---|------|------|
| H1 | 훈련/사기진작 max 100→80 | 상한 부족 |
| H2 | 훈련/사기진작 부작용 1.0→0.9 | 불필요한 패널티 |
| H3 | 징병 blending 모디파이어 누락 | 아이템 효과 무시 |
| H4 | 내정 비용 모디파이어 누락 | 아이템 비용 감소 무시 |
| H5 | 스탯 getter raw값 사용 | 관직/아이템 보너스 미반영 |
| H6 | 특수능력 전투 중 동적→정적 | 조건부 능력 차이 |
| H7 | 방어 순서 스탯 평균 누락 | 방어 순서 차이 |
| H8 | NPC 불가침 로직 완전 상이 | 외교 행동 차이 |
| H9 | NPC 세율/관직 단순화 | 국가 운영 차이 |
| H10 | NPC 보상 계산 단순화 | 자원 분배 차이 |
| H11 | 증여/헌납 최소자원 상수 차이 | 자원 제한 차이 |
| H12 | 건국/무작위건국 BeLordOrUnaffiliated | 더 느슨한 조건 |
| H13 | 급습 선전포고 12개월 미확인 | 선전 직후 급습 가능 |
| H14 | postReqTurn 하드코딩 (6개 커맨드) | 쿨다운 차이 |
| H15 | 전투태세 중간턴 로직 누락 | 3턴 표시 없음 |
