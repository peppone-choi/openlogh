# Requirements: Open LOGH (오픈 은하영웅전설)

**Defined:** 2026-03-28
**Core Value:** gin7의 "조직 시뮬레이션" — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행

## v1 Requirements

Requirements sourced from `docs/feature-checklist.md` (P0~P2) and `docs/feature-audit.md` (gap analysis).

### Session (세션)

- [x] **SESS-01**: 시나리오 선택하여 새 게임 세션 생성 (P0)
- [x] **SESS-02**: 기존 세션에 캐릭터 선택하여 참가, 최대 2,000명 (P0)
- [x] **SESS-03**: 제국/동맹 진영 선택 (P0)
- [ ] **SESS-04**: 승리 조건 달성 시 세션 자동 종료 (P1)
- [ ] **SESS-05**: 종료된 세션 초기 조건으로 재시작 (P2)
- [x] **SESS-06**: 게임 시간 실시간 24배속 (P1)
- [x] **SESS-07**: 퇴장 플레이어 재등록 제한 (P2)

### Character (캐릭터)

- [ ] **CHAR-01**: 원작 오리지널 캐릭터 선택 (능력치 고정, 추첨) (P0)
- [ ] **CHAR-02**: 제네레이트 캐릭터 생성 (이름/외모/능력치 배분) (P1)
- [ ] **CHAR-03**: 8개 능력치 (통솔/지휘/정보/정치/운영/기동/공격/방어) (P0)
- [ ] **CHAR-04**: 능력치 성장 — 나이 효과 (청년 +, 장년 -) (P1)
- [ ] **CHAR-05**: 능력치 성장 — 경험치 (CP 사용량 누적, 100=1 상승) (P1)
- [ ] **CHAR-06**: 분류 (군인/정치가), 전신 가능 (P1)
- [ ] **CHAR-07**: 출자 — 제국 (귀족/제국기사/평민/망명자) (P1)
- [ ] **CHAR-08**: 출자 — 동맹 (시민/망명자) (P1)
- [ ] **CHAR-09**: 캐릭터 인계 (세션 간, 평가 포인트 기준, 60세 이하) (P2)
- [ ] **CHAR-10**: 캐릭터 삭제 (대령 이하, 거주구/호텔 체류 중) (P2)
- [ ] **CHAR-11**: 부상/치료 (전투 패배 시 능력치 하락, 회복 기간) (P1)
- [ ] **CHAR-12**: 전사 (기함 격침 시 선택적) (P2)
- [ ] **CHAR-13**: 위치 상태 (행성 체류/함대 탑승/우주 이동) (P0)
- [ ] **CHAR-14**: 공작 능력치 3종 (정치/정보/군사공작, 최대 8,000) (P2)
- [ ] **CHAR-15**: 출신지 — 기함 격침 시 귀환 설정 미지정이면 자동 귀환 (P1)

### Rank & Personnel (계급/인사)

- [ ] **RANK-01**: 소위~원수 11단계 계급 체계 (P0)
- [ ] **RANK-02**: 계급별 인원 제한 (원수5, 상급대장5, 대장10 등) (P0)
- [ ] **RANK-03**: 공적 포인트 축적 (전투/작전/점령) (P0)
- [ ] **RANK-04**: 계급 래더 (5법칙: 공적→작위→훈장→영향력→능력합계) (P1)
- [ ] **RANK-05**: 수동 승진 (인사권자 실행, 공적 0 리셋, 직무카드 상실) (P0)
- [ ] **RANK-06**: 자동 승진 (대령 이하 30G일마다 래더 1위, 평균 공적 부여) (P1)
- [ ] **RANK-07**: 수동 강등 (인사권자 실행, 공적 100, 직무카드 상실) (P1)
- [ ] **RANK-08**: 자동 강등 (대령 이하 30G일마다 조건 충족 시) (P2)
- [ ] **RANK-09**: 인사권 체계 (제국: 황제/군무상서/인사국장, 동맹: 상응) (P0)
- [ ] **RANK-10**: 임명/파면 (상위 직책→하위, 계급 범위 제한) (P0)
- [ ] **RANK-11**: 작위 — 제국 (공작/후작/백작/자작/남작/제국기사) (P2)
- [ ] **RANK-12**: 서훈 (훈장 수여, 래더 제3법칙) (P2)
- [ ] **RANK-13**: 평가 포인트 (세션 내 캐릭터 평가) (P1)
- [ ] **RANK-14**: 명성 포인트 (세션 간 플레이어 평가) (P2)

### Organization (조직)

- [ ] **ORG-01**: 직무권한카드 시스템 (최대 16장/캐릭터) (P0)
- [ ] **ORG-02**: 제국군 조직도 (황궁→내각→군무성→통수본부→함대, 100+ 직책) (P0)
- [ ] **ORG-03**: 동맹군 조직도 (최고평의회→국방위원회→함대, 100+ 직책) (P0)
- [ ] **ORG-04**: 제안 시스템 (하급→상급, 우호도/상성 반영 수락률) (P1)
- [ ] **ORG-05**: 명령 시스템 (상급→하급 명령 하달) (P1)
- [ ] **ORG-06**: 겸임 (복수 직무 보유 가능) (P1)
- [ ] **ORG-07**: 봉토카드 (제국 전용, 승진/강등 시에도 유지) (P2)
- [ ] **ORG-08**: 체포 권한 (헌병총감/내무상서/사법상서 등) (P1)

### Galaxy Map & Planets (은하 지도/성계)

- [ ] **MAP-01**: 100광년 단위 그리드 기반 은하 지도 (80개 성계) (P0)
- [ ] **MAP-02**: 그리드 종류 (공간/성계/항행불능) (P0)
- [ ] **MAP-03**: 그리드 진입 제한 (300유닛/진영, 최대 2진영, 부분진입 금지) (P1)
- [ ] **MAP-04**: 행성 관리 (인구/생산/교역/치안/지지도/궤도방어/요새) (P0)
- [ ] **MAP-05**: 행성 시설 (조병공창/사관학교/주거구/호텔/회의실/주점 등) (P1)
- [ ] **MAP-06**: 행성 총독 직무 (P1)
- [ ] **MAP-07**: 행성 수비대 (육전대 10유닛, 치안 상승, 반란 진압) (P1)
- [ ] **MAP-08**: 요새 시스템 (이제르론/가이에스부르크/렌텐베르크/가르미슈) (P0)
- [ ] **MAP-09**: 요새포 (아군/적군 무차별 타격) (P1)
- [ ] **MAP-10**: 행성 점령 후처리 (6단계 프로세스) (P0)
- [ ] **MAP-11**: 세금 징수 (분기별, 행성 인구/경제력 기반) (P1)
- [ ] **MAP-12**: 페잔 중립 (침범 시 페널티) (P2)
- [ ] **MAP-13**: 지형 장애물 (플라즈마 폭풍/사르갓소 스페이스) (P1)
- [ ] **MAP-14**: 행성 타입 (통상/가스/요새별 참전 지상병 제한) (P1)

### Fleet (함대)

- [ ] **FLET-01**: 함대 (최대 60유닛/18,000척, 10명 편성) (P0)
- [ ] **FLET-02**: 순찰대 (3유닛/900척, 3명 편성) (P0)
- [ ] **FLET-03**: 수송함대 (23유닛/6,900척, 3명 편성) (P1)
- [ ] **FLET-04**: 지상부대 (6유닛, 최대 900척+90,000명, 1명) (P1)
- [ ] **FLET-05**: 행성수비대 (육전대 10유닛, 최대 300,000명, 1명) (P1)
- [ ] **FLET-06**: 함대 계층 구조 (함대→분함대) (P0)
- [ ] **FLET-07**: 함종 체계 11종 (진영별 전용함 포함) (P0)
- [ ] **FLET-08**: 지상 부대 유형 3종 (장갑병/장갑척탄병/경장육전병) (P1)
- [ ] **FLET-09**: 승무원 등급 4종 (엘리트/베테랑/노멀/그린) (P1)
- [ ] **FLET-10**: 사기 시스템 (통솔 기반 최대 사기, 20 이하=전투불능) (P0)
- [ ] **FLET-11**: 항속/연료 (워프 시 소모, 100 미만 워프 불가) (P1)
- [ ] **FLET-12**: 부대 편성 제한 (인구 비례) (P1)
- [ ] **FLET-13**: 사령관-함대 분리 (사령관 행성 체류, 함대 별도 위치) (P0)
- [ ] **FLET-14**: 독행함 제한 (적 유닛/행성 성계 진입 불가) (P1)
- [ ] **FLET-15**: 상선 유닛 (승무원 불필요, 자동 보충) (P2)

### Logistics (병참)

- [ ] **LOGI-01**: 행성 창고 (유닛/물자 보관, 중앙정부 관할) (P1)
- [ ] **LOGI-02**: 부대 창고 (각 부대 전용) (P1)
- [ ] **LOGI-03**: 할당 (행성→부대 유닛/물자 이동, 상호배타 제약) (P1)
- [ ] **LOGI-04**: 재편성 (부대 유닛 구성 변경, 승무원 확인) (P1)
- [ ] **LOGI-05**: 보충 (동일 서브타입, 1함종씩만, 승무원 자동) (P0)
- [ ] **LOGI-06**: 함선 생산 (조병공창 보유 행성/요새에서만) (P1)
- [ ] **LOGI-07**: 병사 모병 (행성 인구에서, 그린 등급만) (P1)
- [ ] **LOGI-08**: 자동 생산 (행성별 자동 유닛/물자 생산) (P1)
- [ ] **LOGI-09**: 수송 계획/실행 (수송함대 패키지 관리) (P2)

### Command Points (커맨드 포인트)

- [ ] **CP-01**: PCP/MCP 이원화 (P0)
- [ ] **CP-02**: CP 회복 (실시간 5분/게임 2시간마다, 정치+운영 영향, 오프라인 포함) (P0)
- [ ] **CP-03**: CP 대용 (다른 CP로 2배 소모, 경험치 제외) (P1)
- [ ] **CP-04**: 전술전 중 CP 회복 정지 (P1)
- [ ] **CP-05**: 경험치 연동 (PCP→통솔/정치/운영/정보, MCP→지휘/기동/공격/방어) (P1)

### Strategic Commands — Operations (작전 커맨드)

- [ ] **OPS-01**: 워프 항행 (MCP 40, 그리드 간 이동, 워프 오차) (P0)
- [ ] **OPS-02**: 성계내 항행 (MCP 160, 같은 성계 내 행성간 이동) (P0)
- [ ] **OPS-03**: 연료 보급 (MCP 160, 항속 회복) (P1)
- [ ] **OPS-04**: 전략 색적 (MCP, 그리드 정보 수집) (P1)
- [ ] **OPS-05**: 군기 유지 (MCP 80, 혼란 발생률 감소) (P1)
- [ ] **OPS-06**: 항주 훈련 (MCP 80, 부대 훈련도 증가) (P1)
- [ ] **OPS-07**: 육전 훈련 (MCP 80) (P1)
- [ ] **OPS-08**: 공전 훈련 (MCP 80) (P1)
- [ ] **OPS-09**: 육전전술훈련 (MCP 80, 스킬 습득) (P2)
- [ ] **OPS-10**: 공전전술훈련 (MCP 80, 스킬 습득) (P2)
- [ ] **OPS-11**: 경계 출동 (MCP 160, 치안율 증가) (P1)
- [ ] **OPS-12**: 무력 진압 (MCP 160, 치안↑ 지지율↓) (P1)
- [ ] **OPS-13**: 분열 행진 (MCP 160, 지지율 증가) (P1)
- [ ] **OPS-14**: 징발 (MCP 160, 적 행성 군수물자 징발) (P1)
- [ ] **OPS-15**: 특별 경비 (MCP 160, 경비 태세 강화) (P2)

### Strategic Commands — Personal (개인 커맨드)

- [ ] **PERS-01**: 원거리 이동 (PCP 10, 행성 내 시설간) (P1)
- [ ] **PERS-02**: 근거리 이동 (PCP 5, 시설 내 스팟간) (P2)
- [ ] **PERS-03**: 망명 (PCP 320, 진영 변경, 적 수도 구금, 주소록 삭제) (P2)
- [ ] **PERS-04**: 퇴역 (PCP 160, 군인→정치가, 30G일 지원 불가) (P2)
- [ ] **PERS-05**: 지원 (PCP 160, 정치가→군인, 계급 소좌) (P2)
- [ ] **PERS-06**: 귀환 설정 (0, 기함 격침 시 귀환 행성 설정) (P1)
- [ ] **PERS-07**: 회견 (PCP 10, 동일 스팟 인물, 우호도 증가) (P1)
- [ ] **PERS-08**: 수강 (PCP 160, 사관학교에서만, 능력 증가) (P1)
- [ ] **PERS-09**: 병기연습 (PCP 10, 사관학교 시뮬레이터) (P2)
- [ ] **PERS-10**: 기함 구매 (PCP 80, 평가 포인트 소비) (P2)
- [ ] **PERS-11**: 자금 투입 (PCP 80, 사적 구좌에서) (P2)

### Strategic Commands — Command (지휘 커맨드)

- [ ] **CMD-01**: 작전 계획 (MCP 10~1280, 점령/방위/소탕) (P0)
- [ ] **CMD-02**: 발령 (MCP 1~320, 작전에 부대 배정) (P0)
- [ ] **CMD-03**: 작전 철회 (MCP 5~320) (P1)
- [ ] **CMD-04**: 부대 결성 (MCP 320) (P0)
- [ ] **CMD-05**: 부대 해산 (MCP 160) (P0)
- [ ] **CMD-06**: 강의 (MCP 160, 사관학교, 120G분 유효) (P2)
- [ ] **CMD-07**: 수송 계획 (MCP 80, 수송 패키지 작성) (P2)
- [ ] **CMD-08**: 수송 중지 (MCP 80) (P2)

### Strategic Commands — Logistics (병참 커맨드)

- [ ] **LCMD-01**: 할당 (PCP 160, 행성→부대 유닛/물자) (P1)
- [ ] **LCMD-02**: 재편성 (MCP 160, 부대 유닛 구성 변경) (P1)
- [ ] **LCMD-03**: 보충 (MCP 160, 동일 서브타입 보충) (P0)
- [ ] **LCMD-04**: 완전 수리 (MCP 160, 기함+전 유닛 수리) (P1)
- [ ] **LCMD-05**: 완전 보급 (MCP 160, 임의 부대 군수물자 보급) (P1)
- [ ] **LCMD-06**: 반출입 (MCP 160, 수송 패키지 반출/반입) (P2)
- [ ] **LCMD-07**: 수송 계획/중지 (MCP 80) (P2)

### Strategic Commands — Personnel (인사 커맨드)

- [ ] **PCMD-01**: 승진 (PCP 160) (P0)
- [ ] **PCMD-02**: 발탁 (PCP 640, 래더 최상위 아닌 인물 승격) (P1)
- [ ] **PCMD-03**: 강등 (PCP 320) (P1)
- [ ] **PCMD-04**: 서작 (PCP 160, 제국만) (P2)
- [ ] **PCMD-05**: 서훈 (PCP 160) (P2)
- [ ] **PCMD-06**: 임명 (PCP 160, 직무카드 부여) (P0)
- [ ] **PCMD-07**: 파면 (PCP 160, 직무카드 박탈) (P0)
- [ ] **PCMD-08**: 사임 (PCP 80, 자발적 포기) (P1)
- [ ] **PCMD-09**: 봉토 수여 (PCP 640, 제국만) (P2)
- [ ] **PCMD-10**: 봉토 직할 (PCP 640, 제국만) (P2)

### Strategic Commands — Political (정치 커맨드)

- [ ] **POL-01**: 예산 편성 (PCP) (P1)
- [ ] **POL-02**: 납입률 변경 (PCP 320) (P1)
- [ ] **POL-03**: 관세율 변경 (PCP 320) (P2)
- [ ] **POL-04**: 분배 (PCP 320, 국가 예산→행성 원조금) (P2)
- [ ] **POL-05**: 야회 (PCP 320, 영향력 변동) (P2)
- [ ] **POL-06**: 수렵 (PCP 320, 봉토 행성, 우호도+영향력) (P2)
- [ ] **POL-07**: 회담 (PCP 320, 호텔, 영향력) (P2)
- [ ] **POL-08**: 담화 (PCP 320, 호텔, 우호도+영향력) (P2)
- [ ] **POL-09**: 연설 (PCP 320, 영향력+지지율) (P2)
- [ ] **POL-10**: 국가 목표 (PCP 320) (P2)
- [ ] **POL-11**: 통치 목표 (PCP 80) (P2)
- [ ] **POL-12**: 처단 (PCP 320, 구금자 처분) (P2)
- [ ] **POL-13**: 외교 (PCP 320, 페잔 교섭) (P2)
- [ ] **POL-14**: 제안 (0, 상관에게, 우호도/상성 반영) (P1)
- [ ] **POL-15**: 명령 (0, 부하에게 하달) (P1)
- [ ] **POL-16**: 제안 공작 (정치공작 1,000) (P2)

### Strategic Commands — Intelligence (첩보 커맨드)

- [ ] **INTL-01**: 일제 수색 (PCP 160) (P1)
- [ ] **INTL-02**: 체포 허가 (PCP 800, 체포 리스트 등록) (P1)
- [ ] **INTL-03**: 집행 명령 (PCP 800, 체포 권한 부여) (P1)
- [ ] **INTL-04**: 체포 명령 (PCP 160, 실제 체포 시도) (P1)
- [ ] **INTL-05**: 사열 (PCP 160, 쿠데타 징후 탐지) (P2)
- [ ] **INTL-06**: 습격 (PCP 160, 적 진영 인물) (P2)
- [ ] **INTL-07**: 감시 (PCP 160, 발각까지 지속) (P2)
- [ ] **INTL-08**: 잠입 공작 (PCP 320, 적 행성 침입) (P2)
- [ ] **INTL-09**: 탈출 공작 (PCP 160) (P2)
- [ ] **INTL-10**: 정보 공작 (PCP 160, 시설 정보 획득) (P2)
- [ ] **INTL-11**: 파괴 공작 (PCP 160, 시한폭탄 설치) (P2)
- [ ] **INTL-12**: 선동 공작 (PCP 160, 지지율 하락) (P2)
- [ ] **INTL-13**: 귀환 공작 (PCP 320) (P2)
- [ ] **INTL-14**: 통신 방해 (정보공작 2,000) (P2)
- [ ] **INTL-15**: 위장 함대 (정보공작 1,000) (P2)

### Coup d'État (쿠데타)

- [ ] **COUP-01**: 반의 (PCP 640, 쿠데타 수모자) (P2)
- [ ] **COUP-02**: 모의 (PCP 640, 참가 교섭) (P2)
- [ ] **COUP-03**: 설득 (PCP 640, 반란 충성도 상승) (P2)
- [ ] **COUP-04**: 참가 (PCP 160, 교섭 동의) (P2)
- [ ] **COUP-05**: 반란 (PCP 640, 쿠데타 실행) (P2)
- [ ] **COUP-06**: 사열 (PCP 160, 징후 탐지 — 대응 측) (P2)

### Tactical Combat (전술전)

- [ ] **TAC-01**: 전투 발생 (같은 그리드에 적/아군 유닛 존재 시) (P0)
- [ ] **TAC-02**: 전투 종료 (적 전멸/퇴각 + 점령) (P0)
- [ ] **TAC-03**: 실시간 전술전 (WebSocket 기반 RTS) (P0)
- [ ] **TAC-04**: 명령-수행 체계 (총사령관→함대사령관→함선) (P0)
- [ ] **TAC-05**: 이동 커맨드 (이동/선회/평행이동/반전) (P0)
- [ ] **TAC-06**: 공격 커맨드 (공격/사격/공전 명령) (P0)
- [ ] **TAC-07**: 정지 명령 (P0)
- [ ] **TAC-08**: 출격 커맨드 (행성/요새 유닛 출격) (P1)
- [ ] **TAC-09**: 철퇴 명령 (레이더 원외 + WARP 에너지 + 2.5분 준비) (P0)
- [ ] **TAC-10**: 진형 시스템 7종 (방추/함종/혼성/삼열/대열해제) (P0)
- [ ] **TAC-11**: 에너지 분배 6채널 (BEAM/GUN/SHIELD(4방향)/ENGINE/WARP/SENSOR) (P0)
- [ ] **TAC-12**: 커맨드 레인지 (기함 중심 지휘 범위) (P1)
- [ ] **TAC-13**: 색적 (자동, SENSOR+정보 능력치) (P1)
- [ ] **TAC-14**: 태세 변경 4종 (항행/정박/주류/전투) (P1)
- [ ] **TAC-15**: 사선 판정 (빔/건/미사일 장애물 체크) (P1)
- [ ] **TAC-16**: 유닛 손해 (300척 단위, 1척씩 격침) (P0)
- [ ] **TAC-17**: 기함 손해 (통상/손해 상태, 공작함 수리) (P1)
- [ ] **TAC-18**: 지상전 (육전대 강하→수비대 전투→점령) (P1)
- [ ] **TAC-19**: 요새포 (사선 상 무차별 타격, 방위사령관만) (P1)
- [ ] **TAC-20**: 지휘권 분배 (온라인/계급/평가/공적 우선순위) (P1)
- [ ] **TAC-21**: 전술 커맨드 처리 시간 (실행대기+실행소요) (P1)
- [ ] **TAC-22**: 전투 시각화 (2D 탑뷰 렌더링) (P0)
- [ ] **TAC-23**: 전투 로그 (P0)

### Communication (통신)

- [ ] **COMM-01**: 게임 내 메일 (개인+직무 주소, 120통 제한) (P0)
- [ ] **COMM-02**: 메신저 (1:1 실시간) (P1)
- [ ] **COMM-03**: 채팅 (같은 스팟/부대/그리드) (P0)
- [ ] **COMM-04**: 명함 교환 (채팅 시 메일 주소 교환) (P2)
- [ ] **COMM-05**: 주소록 (최대 100개, 망명 시 전체 삭제) (P2)
- [ ] **COMM-06**: 전술전 채팅 (전체/함대/동진영 3채널) (P1)

### Victory (승패)

- [ ] **VICT-01**: 수도 점령 승리 (P0)
- [ ] **VICT-02**: 영토 축소 승리 (적 성계 3개 이하) (P0)
- [ ] **VICT-03**: 시간 만료 (우주력 801년 7월 27일, 인구 비교) (P1)
- [ ] **VICT-04**: 결정적 승리 조건 (인구90%/유닛10배/쿠데타없음) (P2)
- [ ] **VICT-05**: 한정적/국지적 승리 구분 (P2)

### Session Management (로그인/로그아웃)

- [x] **SMGT-01**: 오프라인 지속 (캐릭터 세계 존재, CP 계속 회복) (P0)
- [ ] **SMGT-02**: AI 대행 (오프라인 전투 시 AI 지휘) (P1)
- [ ] **SMGT-03**: 안전 지역 (자택/호텔 로그아웃 시 전사 불가) (P2)
- [ ] **SMGT-04**: 체포 가능 (오프라인 중에도 체포/인사 대상) (P2)

### NPC AI

- [ ] **NPC-01**: NPC 자동 행동 (미선택 원작 캐릭터 AI 제어) (P1)
- [ ] **NPC-02**: 전투 AI (오프라인 사령관 대행, 능력치 비례) (P0)
- [ ] **NPC-03**: 내정 AI (NPC 행성 총독/정치인 자동 관리) (P2)

### Influence System (영향력)

- [ ] **INFL-01**: 영향력 포인트 (래더 제4법칙, 공적/평가/명성과 별개) (P1)
- [ ] **INFL-02**: 영향력 변동 커맨드 연동 (야회/수렵/회담/담화/연설) (P1)
- [ ] **INFL-03**: 영향력 조회 (P2)

### Friendship System (우호도)

- [ ] **FRND-01**: 캐릭터 간 우호도 (회견/수렵/담화로 변동, 제안 수락률 영향) (P1)
- [ ] **FRND-02**: 상성 (캐릭터 간 기본 친화도) (P2)
- [ ] **FRND-03**: 우호도 조회 (P2)

### Fief System (봉토 — 제국 전용)

- [ ] **FIEF-01**: 봉토카드 (승진/강등 시 유지되는 특수 직무카드) (P2)
- [ ] **FIEF-02**: 봉토 수여 (남작+ 작위 보유자에게 행성 수여) (P2)
- [ ] **FIEF-03**: 봉토 직할 환수 (P2)
- [ ] **FIEF-04**: 봉토 행성 특권 (수렵 커맨드) (P2)

### Officer Academy (사관학교)

- [ ] **ACAD-01**: 사관학교장/교관 직책 (P2)
- [ ] **ACAD-02**: 수강 (PCP 160, 능력 증가) (P1)
- [ ] **ACAD-03**: 병기연습 (PCP 10, 시뮬레이터) (P2)
- [ ] **ACAD-04**: 강의 (MCP 160, 120G분 유효) (P2)

### Private Funds (사적 자금)

- [ ] **FUND-01**: 사적 구좌 (P2)
- [ ] **FUND-02**: 자금 투입 경로 (지방자금고/신임/지지 박스) (P2)

### Misc Systems (기타)

- [ ] **MISC-01**: 경매 시스템 (특수 기함/장비) (P2)
- [ ] **MISC-02**: 토너먼트 (1:1 전투) (P2)
- [ ] **MISC-03**: 랭킹 (공적/평가/명성) (P1)
- [ ] **MISC-04**: 게시판 (진영 내) (P1)
- [ ] **MISC-05**: 투표 (동맹 최고평의회 선거 등) (P2)
- [ ] **MISC-06**: 역사 기록 (세션 내 주요 사건 연표) (P1)

### Concurrency Hardening (동시성 보강 — 리서치 결과)

- [x] **HARD-01**: Officer 엔티티 @Version 추가 (CP race condition 해소) (P0)
- [x] **HARD-02**: 전술전 executor 스레드 누수 수정 (P0)
- [ ] **HARD-03**: PositionCard JSONB→관계형 마이그레이션 (P0)
- [ ] **HARD-04**: Spring STOMP 커넥션 풀 설정 (2,000명 스케일) (P1)
- [ ] **HARD-05**: WarAftermath 비동기 리팩터링 (P1)

## v2 Requirements

(None currently — all features scoped to v1 per user request)

## Out of Scope

| Feature              | Reason                        |
| -------------------- | ----------------------------- |
| 모바일 네이티브 앱   | 웹 기반 우선, 반응형으로 대응 |
| 독자적 세계관/스토리 | gin7 원작 충실 재현이 목표    |
| 과금/상점 시스템     | 오픈소스 프로젝트             |
| 3D 전술전 렌더링     | 2D 탑뷰 우선, 3D는 향후 고려  |

## Traceability

**Coverage note:** REQUIREMENTS.md header stated 207 requirements. Actual count derived from requirement IDs in this file is 243. The traceability table below maps all 243.

**Coverage:**

- v1 requirements: 243 total (header was incorrect)
- Mapped to phases: 243
- Unmapped: 0

| Requirement | Phase    | Status   |
| ----------- | -------- | -------- |
| SESS-01     | Phase 1  | Complete |
| SESS-02     | Phase 1  | Complete |
| SESS-03     | Phase 1  | Complete |
| SESS-04     | Phase 10 | Pending  |
| SESS-05     | Phase 10 | Pending  |
| SESS-06     | Phase 1  | Complete |
| SESS-07     | Phase 1  | Complete |
| CHAR-01     | Phase 2  | Pending  |
| CHAR-02     | Phase 2  | Pending  |
| CHAR-03     | Phase 2  | Pending  |
| CHAR-04     | Phase 2  | Pending  |
| CHAR-05     | Phase 2  | Pending  |
| CHAR-06     | Phase 2  | Pending  |
| CHAR-07     | Phase 2  | Pending  |
| CHAR-08     | Phase 2  | Pending  |
| CHAR-09     | Phase 2  | Pending  |
| CHAR-10     | Phase 2  | Pending  |
| CHAR-11     | Phase 2  | Pending  |
| CHAR-12     | Phase 2  | Pending  |
| CHAR-13     | Phase 2  | Pending  |
| CHAR-14     | Phase 2  | Pending  |
| CHAR-15     | Phase 2  | Pending  |
| RANK-01     | Phase 2  | Pending  |
| RANK-02     | Phase 2  | Pending  |
| RANK-03     | Phase 2  | Pending  |
| RANK-04     | Phase 2  | Pending  |
| RANK-05     | Phase 2  | Pending  |
| RANK-06     | Phase 2  | Pending  |
| RANK-07     | Phase 2  | Pending  |
| RANK-08     | Phase 2  | Pending  |
| RANK-09     | Phase 2  | Pending  |
| RANK-10     | Phase 2  | Pending  |
| RANK-11     | Phase 2  | Pending  |
| RANK-12     | Phase 2  | Pending  |
| RANK-13     | Phase 2  | Pending  |
| RANK-14     | Phase 2  | Pending  |
| ORG-01      | Phase 2  | Pending  |
| ORG-02      | Phase 2  | Pending  |
| ORG-03      | Phase 2  | Pending  |
| ORG-04      | Phase 7  | Pending  |
| ORG-05      | Phase 7  | Pending  |
| ORG-06      | Phase 2  | Pending  |
| ORG-07      | Phase 7  | Pending  |
| ORG-08      | Phase 2  | Pending  |
| MAP-01      | Phase 4  | Pending  |
| MAP-02      | Phase 4  | Pending  |
| MAP-03      | Phase 4  | Pending  |
| MAP-04      | Phase 4  | Pending  |
| MAP-05      | Phase 4  | Pending  |
| MAP-06      | Phase 4  | Pending  |
| MAP-07      | Phase 4  | Pending  |
| MAP-08      | Phase 4  | Pending  |
| MAP-09      | Phase 4  | Pending  |
| MAP-10      | Phase 4  | Pending  |
| MAP-11      | Phase 4  | Pending  |
| MAP-12      | Phase 4  | Pending  |
| MAP-13      | Phase 4  | Pending  |
| MAP-14      | Phase 4  | Pending  |
| FLET-01     | Phase 5  | Pending  |
| FLET-02     | Phase 5  | Pending  |
| FLET-03     | Phase 5  | Pending  |
| FLET-04     | Phase 5  | Pending  |
| FLET-05     | Phase 5  | Pending  |
| FLET-06     | Phase 5  | Pending  |
| FLET-07     | Phase 5  | Pending  |
| FLET-08     | Phase 5  | Pending  |
| FLET-09     | Phase 5  | Pending  |
| FLET-10     | Phase 5  | Pending  |
| FLET-11     | Phase 5  | Pending  |
| FLET-12     | Phase 5  | Pending  |
| FLET-13     | Phase 5  | Pending  |
| FLET-14     | Phase 5  | Pending  |
| FLET-15     | Phase 5  | Pending  |
| LOGI-01     | Phase 5  | Pending  |
| LOGI-02     | Phase 5  | Pending  |
| LOGI-03     | Phase 5  | Pending  |
| LOGI-04     | Phase 5  | Pending  |
| LOGI-05     | Phase 5  | Pending  |
| LOGI-06     | Phase 4  | Pending  |
| LOGI-07     | Phase 4  | Pending  |
| LOGI-08     | Phase 4  | Pending  |
| LOGI-09     | Phase 5  | Pending  |
| CP-01       | Phase 3  | Pending  |
| CP-02       | Phase 3  | Pending  |
| CP-03       | Phase 3  | Pending  |
| CP-04       | Phase 3  | Pending  |
| CP-05       | Phase 3  | Pending  |
| OPS-01      | Phase 3  | Pending  |
| OPS-02      | Phase 3  | Pending  |
| OPS-03      | Phase 5  | Pending  |
| OPS-04      | Phase 5  | Pending  |
| OPS-05      | Phase 5  | Pending  |
| OPS-06      | Phase 5  | Pending  |
| OPS-07      | Phase 5  | Pending  |
| OPS-08      | Phase 5  | Pending  |
| OPS-09      | Phase 5  | Pending  |
| OPS-10      | Phase 5  | Pending  |
| OPS-11      | Phase 5  | Pending  |
| OPS-12      | Phase 5  | Pending  |
| OPS-13      | Phase 5  | Pending  |
| OPS-14      | Phase 5  | Pending  |
| OPS-15      | Phase 10 | Pending  |
| PERS-01     | Phase 9  | Pending  |
| PERS-02     | Phase 9  | Pending  |
| PERS-03     | Phase 8  | Pending  |
| PERS-04     | Phase 8  | Pending  |
| PERS-05     | Phase 8  | Pending  |
| PERS-06     | Phase 2  | Pending  |
| PERS-07     | Phase 7  | Pending  |
| PERS-08     | Phase 9  | Pending  |
| PERS-09     | Phase 9  | Pending  |
| PERS-10     | Phase 10 | Pending  |
| PERS-11     | Phase 10 | Pending  |
| CMD-01      | Phase 3  | Pending  |
| CMD-02      | Phase 3  | Pending  |
| CMD-03      | Phase 5  | Pending  |
| CMD-04      | Phase 3  | Pending  |
| CMD-05      | Phase 3  | Pending  |
| CMD-06      | Phase 10 | Pending  |
| CMD-07      | Phase 10 | Pending  |
| CMD-08      | Phase 10 | Pending  |
| LCMD-01     | Phase 5  | Pending  |
| LCMD-02     | Phase 5  | Pending  |
| LCMD-03     | Phase 3  | Pending  |
| LCMD-04     | Phase 5  | Pending  |
| LCMD-05     | Phase 5  | Pending  |
| LCMD-06     | Phase 10 | Pending  |
| LCMD-07     | Phase 10 | Pending  |
| PCMD-01     | Phase 7  | Pending  |
| PCMD-02     | Phase 7  | Pending  |
| PCMD-03     | Phase 7  | Pending  |
| PCMD-04     | Phase 7  | Pending  |
| PCMD-05     | Phase 7  | Pending  |
| PCMD-06     | Phase 7  | Pending  |
| PCMD-07     | Phase 7  | Pending  |
| PCMD-08     | Phase 7  | Pending  |
| PCMD-09     | Phase 7  | Pending  |
| PCMD-10     | Phase 7  | Pending  |
| POL-01      | Phase 8  | Pending  |
| POL-02      | Phase 8  | Pending  |
| POL-03      | Phase 8  | Pending  |
| POL-04      | Phase 8  | Pending  |
| POL-05      | Phase 8  | Pending  |
| POL-06      | Phase 8  | Pending  |
| POL-07      | Phase 8  | Pending  |
| POL-08      | Phase 8  | Pending  |
| POL-09      | Phase 8  | Pending  |
| POL-10      | Phase 8  | Pending  |
| POL-11      | Phase 8  | Pending  |
| POL-12      | Phase 8  | Pending  |
| POL-13      | Phase 8  | Pending  |
| POL-14      | Phase 7  | Pending  |
| POL-15      | Phase 7  | Pending  |
| POL-16      | Phase 8  | Pending  |
| INTL-01     | Phase 8  | Pending  |
| INTL-02     | Phase 8  | Pending  |
| INTL-03     | Phase 8  | Pending  |
| INTL-04     | Phase 8  | Pending  |
| INTL-05     | Phase 8  | Pending  |
| INTL-06     | Phase 8  | Pending  |
| INTL-07     | Phase 8  | Pending  |
| INTL-08     | Phase 8  | Pending  |
| INTL-09     | Phase 8  | Pending  |
| INTL-10     | Phase 8  | Pending  |
| INTL-11     | Phase 8  | Pending  |
| INTL-12     | Phase 8  | Pending  |
| INTL-13     | Phase 8  | Pending  |
| INTL-14     | Phase 8  | Pending  |
| INTL-15     | Phase 8  | Pending  |
| COUP-01     | Phase 8  | Pending  |
| COUP-02     | Phase 8  | Pending  |
| COUP-03     | Phase 8  | Pending  |
| COUP-04     | Phase 8  | Pending  |
| COUP-05     | Phase 8  | Pending  |
| COUP-06     | Phase 8  | Pending  |
| TAC-01      | Phase 6  | Pending  |
| TAC-02      | Phase 6  | Pending  |
| TAC-03      | Phase 6  | Pending  |
| TAC-04      | Phase 6  | Pending  |
| TAC-05      | Phase 6  | Pending  |
| TAC-06      | Phase 6  | Pending  |
| TAC-07      | Phase 6  | Pending  |
| TAC-08      | Phase 6  | Pending  |
| TAC-09      | Phase 6  | Pending  |
| TAC-10      | Phase 6  | Pending  |
| TAC-11      | Phase 6  | Pending  |
| TAC-12      | Phase 6  | Pending  |
| TAC-13      | Phase 6  | Pending  |
| TAC-14      | Phase 6  | Pending  |
| TAC-15      | Phase 6  | Pending  |
| TAC-16      | Phase 6  | Pending  |
| TAC-17      | Phase 6  | Pending  |
| TAC-18      | Phase 6  | Pending  |
| TAC-19      | Phase 6  | Pending  |
| TAC-20      | Phase 6  | Pending  |
| TAC-21      | Phase 6  | Pending  |
| TAC-22      | Phase 6  | Pending  |
| TAC-23      | Phase 6  | Pending  |
| COMM-01     | Phase 9  | Pending  |
| COMM-02     | Phase 9  | Pending  |
| COMM-03     | Phase 9  | Pending  |
| COMM-04     | Phase 9  | Pending  |
| COMM-05     | Phase 9  | Pending  |
| COMM-06     | Phase 9  | Pending  |
| VICT-01     | Phase 10 | Pending  |
| VICT-02     | Phase 10 | Pending  |
| VICT-03     | Phase 10 | Pending  |
| VICT-04     | Phase 10 | Pending  |
| VICT-05     | Phase 10 | Pending  |
| SMGT-01     | Phase 1  | Complete |
| SMGT-02     | Phase 6  | Pending  |
| SMGT-03     | Phase 10 | Pending  |
| SMGT-04     | Phase 10 | Pending  |
| NPC-01      | Phase 6  | Pending  |
| NPC-02      | Phase 6  | Pending  |
| NPC-03      | Phase 6  | Pending  |
| INFL-01     | Phase 7  | Pending  |
| INFL-02     | Phase 7  | Pending  |
| INFL-03     | Phase 7  | Pending  |
| FRND-01     | Phase 7  | Pending  |
| FRND-02     | Phase 7  | Pending  |
| FRND-03     | Phase 7  | Pending  |
| FIEF-01     | Phase 9  | Pending  |
| FIEF-02     | Phase 9  | Pending  |
| FIEF-03     | Phase 9  | Pending  |
| FIEF-04     | Phase 9  | Pending  |
| ACAD-01     | Phase 9  | Pending  |
| ACAD-02     | Phase 9  | Pending  |
| ACAD-03     | Phase 9  | Pending  |
| ACAD-04     | Phase 9  | Pending  |
| FUND-01     | Phase 9  | Pending  |
| FUND-02     | Phase 9  | Pending  |
| MISC-01     | Phase 9  | Pending  |
| MISC-02     | Phase 9  | Pending  |
| MISC-03     | Phase 9  | Pending  |
| MISC-04     | Phase 9  | Pending  |
| MISC-05     | Phase 9  | Pending  |
| MISC-06     | Phase 9  | Pending  |
| HARD-01     | Phase 1  | Complete |
| HARD-02     | Phase 1  | Complete |
| HARD-03     | Phase 2  | Pending  |
| HARD-04     | Phase 10 | Pending  |
| HARD-05     | Phase 6  | Pending  |

---

_Requirements defined: 2026-03-28_
_Last updated: 2026-03-28 — traceability populated after roadmap creation_
