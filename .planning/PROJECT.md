# Open LOGH (오픈 은하영웅전설)

## What This Is

은하영웅전설 VII(gin7, 2004 BOTHTEC)을 웹 기반으로 재구현하는 다인원 온라인 전략 시뮬레이션 게임. OpenSamguk(삼국지 웹게임)의 백엔드를 재활용하되, 프론트엔드는 LOGH 전용으로 새로 설계한다. 플레이어는 은하제국 또는 자유행성동맹의 장교(소위~원수)로 참가하여 조직 내에서 협력하며 진영의 승리를 목표로 한다.

## Core Value

gin7의 핵심인 **"조직 시뮬레이션"** — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행하며, 원작의 라인하르트나 양웬리의 입장을 체험할 수 있는 것.

## Context

### Origin
OpenSamguk(삼국지 웹게임) 포크. Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 기술 스택 유지. 패키지명 com.opensam → com.openlogh 변환 완료. 백엔드 빌드/부팅 확인됨.

### What Exists
- 백엔드: 세션/월드 관리, 인증(Kakao OAuth), 턴 엔진, 커맨드 시스템, 자동전투, WebSocket(STOMP), Flyway 마이그레이션 (V1~V27)
- 프론트엔드: Next.js 15 삼국지 UI (리스킨 예정, 구조 재설계)
- 코드베이스 매핑: .planning/codebase/ 7개 문서 완성

### What Needs to Change
삼국지 게임 로직 → LOGH/gin7 게임 로직 전면 전환

## Principles

1. **gin7 충실도**: 매뉴얼의 게임 메카닉스를 최대한 충실히 재현
2. **조직 시뮬레이션 우선**: 계급/직무/권한 구조가 게임의 본질
3. **리얼타임 기반**: 1초 서버 tick = 게임 내 24초 (24배속)
4. **인구-병력 연동**: 인구 10억당 함대 1, 순찰대/지상부대 6
5. **프론트엔드 신규 설계**: 백엔드만 재활용, UI/UX는 LOGH 전용
6. **점진적 확장**: 중규모(100-500명) 동시접속 목표

## Requirements

### Validated

- ✓ 세션/월드 생성 및 참가 — existing (OpenSamguk)
- ✓ 인증/OAuth 로그인 — existing
- ✓ WebSocket 실시간 이벤트 — existing
- ✓ Flyway DB 마이그레이션 — existing
- ✓ Gateway + Game-app 멀티프로세스 — existing
- ✓ @Transactional CUD 보호 — fixed

### Active

**캐릭터/계급 시스템**
- [ ] 8스탯 시스템 (leadership, politics, administration, intelligence, command, mobility, attack, defense)
- [ ] PCP(정략) + MCP(군사) 스탯 그룹 분리
- [ ] 11단계 계급 (소위~원수), 제국/동맹 칭호 구분
- [ ] 공적 포인트 기반 승진/강등
- [ ] 커스텀 캐릭터 생성 (8스탯 배분)
- [ ] 원작 캐릭터 일부 선택 가능 (시나리오별)
- [ ] 소위부터 플레이 시작 가능

**커맨드 시스템**
- [ ] 직무권한카드 기반 커맨드 체계 (77종 카드)
- [ ] PCP/MCP 커맨드포인트 분리 (각각 독립 회복, 5분마다)
- [ ] 교차사용 시 2배 비용
- [ ] 리얼타임 쿨다운 (턴 대기 없음)
- [ ] 제안공작 시스템

**리얼타임 엔진**
- [ ] 1초 서버 tick = 게임 내 24초 (24배속)
- [ ] 실시간 30시간 = 게임 내 1개월
- [ ] CP 5분마다 회복
- [ ] 커맨드 실행 시 실시간 대기

**조직 구조**
- [ ] 함대: 60유닛(18,000척), 10명 편성
- [ ] 순찰대: 3유닛(900척), 3명 편성
- [ ] 수송함대: 수송20+전투3유닛, 3명
- [ ] 지상부대: 양륙함3+육전대3유닛, 1명
- [ ] 행성수비대: 육전대10유닛, 1명
- [ ] 인구 10억당 함대/수송함대 1, 순찰대/지상부대 6

**갤럭시 맵**
- [ ] 80개 성계 (docs/star_systems.json)
- [ ] 성계 간 항로 연결
- [ ] 제국/동맹/페잔 영역 구분
- [ ] 이제르론/가이에스부르크 등 요새 시스템

**전술전 (RTS)**
- [ ] WebSocket 기반 실시간 함대전
- [ ] 에너지 배분: BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR
- [ ] 진형: 방추/함종별/혼성/삼열
- [ ] 요새포 (토르 해머, 가이에스하켄)
- [ ] 점거 커맨드, 통신방해

**3세력 시스템**
- [ ] 제국: 전제군주제, 귀족 체계, 쿠데타 이벤트
- [ ] 동맹: 민주공화제, 최고평의회, 선거
- [ ] 페잔: NPC 세력, 중립 교역, 차관 시스템, 정보 거래
- [ ] 페잔 빚 상환, 페잔 엔딩

**시나리오**
- [ ] 10개 시나리오 (UC795.9 ~ UC799.4)
- [ ] 시나리오별 초기 성계 배치, 전투 현황, 편성 가능 함대
- [ ] 시나리오별 이벤트 (쿠데타, 내전 등)

**NPC AI**
- [ ] 성격/성향 기반 행동 패턴 (향상된 AI)
- [ ] 오프라인 플레이어 캐릭터 자동 행동
- [ ] 미선택 원작 캐릭터 자율 행동

**승리 조건**
- [ ] 적 수도 성계 점령
- [ ] 적 보유 성계 3개 이하
- [ ] UC801.7.27 시간제한 → 인구 비교
- [ ] 결정적/한정적/국지적/패배 4단계 평가

### Out of Scope

- 페잔 플레이어 세력 — NPC 전용으로 결정
- 모바일 앱 — 웹 기반 우선
- 대규모(2000명) 최적화 — 중규모(100-500명) 우선, 이후 확장

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 풀스택 MVP 동시 개발 | 전략+전술 모두 기본 기능을 갖춘 MVP | 전략/전술 병행 |
| 프론트엔드 신규 설계 | 삼국지 UI는 LOGH와 맞지 않음 | 백엔드만 재활용 |
| 커스텀 캐릭터 + 원작 일부 선택 | 다인원 MMO에서 모두가 라인하르트일 수 없음 | 커스텀 기본, 원작 일부 가능 |
| 소위부터 시작 | gin7 원작(소장급)과 다르게, 조직 시뮬레이션의 깊이 확장 | 전 계급 플레이 |
| 페잔 NPC 전용 | 3세력 중 교역국은 AI가 운영, 플레이 복잡도 절감 | 제국/동맹만 플레이어 |
| 1초 서버 tick | gin7 원작 24배속 충실 재현, 서버 부하 감수 | 최고 반응성 |
| 향상된 NPC AI | 성격/성향 기반으로 원작보다 정교한 행동 | 몰입감 향상 |
| 인구-병력 연동 | gin7 원작 시스템, 전략적 깊이 확보 | 인구 10억=함대1 |

## Constraints

- **Tech Stack**: Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 유지
- **Architecture**: gateway-app + game-app JVM 분리 구조 유지
- **Reference Fidelity**: gin7 매뉴얼의 게임 메카닉스 최대한 충실히 재현
- **Real-time**: 전술전은 WebSocket 기반 실시간 처리
- **Scale**: 세션당 100-500명 동시접속 목표
- **DB Migration**: Flyway V28__ 이후로 추가

## Reference Materials

- gin7 매뉴얼: `/Users/apple/Downloads/gin7manualsaved.pdf`
- 도메인 매핑: `CLAUDE.md` Domain Mapping 섹션
- 성계 데이터: `docs/star_systems.json` (80개 성계)
- 시나리오 데이터: `docs/scenarios.json` (10개 시나리오)
- 부대 편성: `docs/reference/unit_composition.md`
- 시나리오 상세: `docs/reference/scenarios_detail.md`
- 코드베이스 분석: `.planning/codebase/` (7개 문서)

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition:**
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone:**
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-05 after initialization*
