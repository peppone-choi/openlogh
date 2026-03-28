# Open LOGH (오픈 은하영웅전설)

## What This Is

은하영웅전설 VII(gin7, 2004 BOTHTEC)을 웹 기반으로 재구현하는 다인원 온라인 전략 시뮬레이션 게임. OpenSamguk(삼국지 웹게임)을 포크하여 LOGH 세계관으로 변환 중이며, 플레이어는 은하제국 또는 자유행성동맹의 장교로 참가하여 조직 내에서 협력하며 진영의 승리를 목표로 한다.

## Core Value

gin7의 핵심인 "조직 시뮬레이션" — 직무권한카드 기반 커맨드 시스템으로 다수 플레이어가 계급 구조 안에서 명령/제안/인사/정치를 수행하며, 원작의 라인하르트나 양웬리의 입장을 체험할 수 있는 것.

## Requirements

### Validated

- ✓ Spring Boot 3 (Kotlin) 백엔드 구조 (gateway-app + game-app) — existing
- ✓ Next.js 15 프론트엔드 기본 레이아웃 — existing
- ✓ PostgreSQL 16 + Redis 7 인프라 — existing
- ✓ 도메인 엔티티 매핑 (삼국지→LOGH 용어 변환) — existing
- ✓ 월드(세션) 생성 API — existing
- ✓ 3D 모델 뷰어 페이지 (React Three Fiber) — existing

### Active

**세션 시스템**

- [ ] 세션 생성/참가/진영 선택 (최대 2,000명)
- [ ] 게임 시간 (실시간 24배속)
- [ ] 승리/패배 조건 판정 및 세션 종료

**캐릭터 시스템**

- [ ] 오리지널/제네레이트 캐릭터 생성
- [ ] 8개 능력치 (통솔/지휘/정보/정치/운영/기동/공격/방어)
- [ ] 능력치 성장 (나이/경험치)
- [ ] 위치 상태 (행성 체류/함대 탑승/우주 이동)

**계급/인사 시스템**

- [ ] 소위~원수 11단계 계급 체계 (계급별 인원 제한)
- [ ] 공적 포인트 + 계급 래더 (5법칙)
- [ ] 승진/강등 (수동 + 자동)
- [ ] 임명/파면 (인사권 체계)
- [ ] 작위 (제국), 서훈, 영향력/우호도 시스템

**조직 구조**

- [ ] 직무권한카드 시스템 (최대 16장/캐릭터)
- [ ] 제국군/동맹군 조직도 (100+ 직책)
- [ ] 제안/명령 시스템
- [ ] 봉토 시스템 (제국 전용)

**은하 지도/성계**

- [ ] 100광년 단위 그리드 기반 은하 지도 (80개 성계)
- [ ] 행성 관리 (인구/생산/교역/치안/지지도/방어)
- [ ] 요새 시스템 (이제르론/가이에스부르크 등)
- [ ] 행성 점령/세금 징수

**함대 시스템**

- [ ] 함대/순찰대/수송함대/지상부대/행성수비대
- [ ] 함종 체계 (11종, 진영별 전용함)
- [ ] 사기/항속/승무원 등급
- [ ] 부대 편성 제한 (인구 비례)

**병참 시스템**

- [ ] 행성 창고 / 부대 창고
- [ ] 할당/재편성/보충/완전 수리
- [ ] 함선 생산 / 병사 모병

**커맨드 포인트**

- [ ] PCP/MCP 이원화
- [ ] CP 회복 (5분마다), 대용 (2배 소모)
- [ ] 경험치 연동

**전략 커맨드 (70+ 커맨드)**

- [ ] 작전 커맨드 (워프 항행, 색적, 훈련 등 15종)
- [ ] 개인 커맨드 (이동, 망명, 퇴역, 회견 등 11종)
- [ ] 지휘 커맨드 (작전계획, 발령, 부대결성 등 8종)
- [ ] 병참 커맨드 (할당, 재편성, 보충, 수송 등 7종)
- [ ] 인사 커맨드 (승진, 강등, 임명, 발탁, 봉토 등 7종)
- [ ] 정치 커맨드 (야회, 연설, 분배, 처단, 외교 등 10종)
- [ ] 첩보 커맨드 (체포, 습격, 감시, 침입공작 등 11종)
- [ ] 쿠데타 커맨드 (반의, 모의, 설득, 반란 등 6종)

**전술 게임 (RTS)**

- [ ] 실시간 함대 전투 (WebSocket 기반)
- [ ] 에너지 배분 (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR 6채널)
- [ ] 대형 변경 (방추/함종/혼성/삼열 등 7종)
- [ ] 전투 커맨드 (이동/공격/사격/반전/평행이동/출격 등)
- [ ] 요새포 사격
- [ ] 행성/요새 점령전

**통신 시스템**

- [ ] 게임 내 메일 (주소록, 120통 제한)
- [ ] 메신저 (1:1 실시간)
- [ ] 채팅 (같은 스팟/같은 그리드)

**경제 시스템**

- [ ] 세금 징수 / 국가 예산
- [ ] 교역 / 관세율
- [ ] 사적 자금 / 자금 투입

**기타 시스템**

- [ ] 사관학교 (수강/병기연습/강의)
- [ ] 체포/처단 체계
- [ ] NPC AI (미선택 원작 캐릭터 담당)

### Out of Scope

- 모바일 네이티브 앱 — 웹 기반 우선, 반응형으로 대응
- 독자적 세계관/스토리 — gin7 원작 충실 재현이 목표
- 과금/상점 시스템 — 오픈소스 프로젝트

## Context

- OpenSamguk에서 포크: 삼국지 → LOGH 도메인 매핑 완료 (엔티티/필드/용어)
- 백엔드: gateway-app + game-app 멀티프로세스 구조, 일부 API 동작 중
- 프론트엔드: Next.js 15 기본 레이아웃 + 3D 모델 뷰어 있음, 게임 UI는 미구현
- gin7 매뉴얼 PDF (54페이지) + LOGH4 메카닉스 레퍼런스 보유
- feature-checklist.md: 200+ 기능 정리 완료 (P0/P1/P2)
- feature-audit.md: gin7 매뉴얼 vs 체크리스트 갭 분석 완료
- 개발 우선순위: 백엔드 먼저 → 프론트엔드

## Constraints

- **Tech Stack**: Spring Boot 3 (Kotlin) + Next.js 15 + PostgreSQL 16 + Redis 7 — 기존 코드베이스 유지
- **Architecture**: gateway-app + versioned game-app JVM 분리 구조 유지
- **Reference Fidelity**: gin7 매뉴얼의 게임 메카닉스를 최대한 충실히 재현
- **Real-time**: 전술전은 WebSocket 기반 실시간 처리 필요
- **Scale**: 세션당 최대 2,000명 동시 접속 고려

## Key Decisions

| Decision                    | Rationale                          | Outcome   |
| --------------------------- | ---------------------------------- | --------- |
| OpenSamguk 포크 기반        | 삼국지 전략게임의 검증된 구조 활용 | ✓ Good    |
| 삼국→LOGH 도메인 매핑       | 엔티티/필드/용어 체계적 변환       | ✓ Good    |
| gateway-app + game-app 분리 | 게임 버전별 독립 실행, 세션 격리   | — Pending |
| 백엔드 우선 개발            | 게임 로직이 핵심, UI는 이후 연동   | — Pending |
| P0~P2 전체 스코프           | 완성도 있는 게임 경험 목표         | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):

1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):

1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---

_Last updated: 2026-03-28 after initialization_
