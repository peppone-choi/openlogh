# Phase 1: Session Foundation - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

Players can create and join game sessions by selecting a scenario and choosing a faction (Empire or Alliance). The backend is free of exploit-grade concurrency bugs: CP race condition (HARD-01) and executor thread leak (HARD-02). Offline officers persist in the game world with CP recovery and basic state progression.

</domain>

<decisions>
## Implementation Decisions

### Faction Join Constraints

- **D-01:** 진영 인원 비율 하드캡 3:2 적용 (한 진영에 전체의 최대 60%)
- **D-02:** 캡 도달 시 메시지로 차단 ("제국군 인원이 가득 찼습니다 — 동맹에 참가하거나 자리가 날 때까지 기다려주세요"). 플레이어는 로비에서 대기 가능

### Session Creation

- **D-03:** 세션 생성 시 시나리오 선택만 제공. 나머지 설정(24배속, 2000명 상한, 승리조건 등)은 시나리오 JSON에 정의된 기본값 사용
- **D-04:** 로비 세션 목록에 핵심 정보만 표시: 시나리오명, 현재 인원(제국/동맹 각각), 게임 내 날짜, 상태(모집중/진행중)

### Offline Officer State

- **D-05:** 오프라인 장교는 온라인과 동일하게 표시 — 온/오프라인 구분 아이콘 없음 (gin7 원작 충실). 다른 플레이어는 누가 오프라인인지 알 수 없음
- **D-06:** 오프라인 중 CP 회복 + 기본 상태 변화 (이동 중 함대는 계속 이동 등) 구현. 체포/인사/AI대행은 해당 Phase에서 구현

### Re-entry Rules (SESS-07)

- **D-07:** '퇴장'은 캐릭터 사망(전사)만 해당. 자발적 로그아웃은 오프라인 지속이지 퇴장이 아님
- **D-08:** 퇴장 후 즉시 재입장 가능 (쿨다운 없음)
- **D-09:** 재입장 제한: 같은 진영에만 복귀 가능 + 원작 캐릭터 사용 불가 (제네레이트 캐릭터만)

### Claude's Discretion

- HARD-01 (@Version 추가) 및 HARD-02 (executor 스레드 누수) 수정의 구체적 구현 방식
- DB 스키마 변경이 필요한 경우 Flyway 마이그레이션 구조
- 시나리오 JSON 스키마에 faction ratio 설정 추가 여부

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Session & Game Rules

- `docs/reference/gin7manual.txt` — gin7 원작 매뉴얼 (세션 규칙 line 316-330: 최대 2000명, 복귀제한, 세션 종료)
- `docs/reference/gin7manualsaved.pdf` — gin7 매뉴얼 원본 PDF (54페이지)
- `docs/reference/logh4_mechanics.md` — LOGH4 메카닉스 레퍼런스

### Requirements & Features

- `.planning/REQUIREMENTS.md` — Phase 1 요구사항: SESS-01~03, SESS-06~07, SMGT-01, HARD-01~02
- `docs/feature-checklist.md` — 전체 기능 체크리스트 (P0/P1/P2 우선순위)
- `docs/feature-audit.md` — gin7 매뉴얼 vs 체크리스트 갭 분석

### Architecture & Known Issues

- `.planning/codebase/ARCHITECTURE.md` — 시스템 아키텍처 (gateway-app + game-app 분리, 커맨드 플로우)
- `.planning/codebase/CONCERNS.md` — 알려진 동시성 버그 (TacticalWebSocketController executor 누수, Officer CP race condition)
- `.planning/codebase/STRUCTURE.md` — 코드베이스 디렉터리 구조

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- `WorldService` (gateway + game-app): 세션 CRUD 이미 구현됨 — 확장하여 faction ratio 검증 추가
- `ScenarioService`: 시나리오 JSON 로딩 및 월드 초기화 — 시나리오 기반 세션 생성에 활용
- `SessionState` 엔티티: session_id FK 기반 격리 패턴 확립됨
- `worldStore.ts` (Zustand): 프론트엔드 월드 목록/선택 상태 관리
- `(lobby)/` 라우트: 로비 페이지 구조 존재

### Established Patterns

- gateway-app → game-app 프록시 패턴 (`GameProcessOrchestrator`, `WorldRouteRegistry`)
- STOMP over SockJS 웹소켓 통신
- JWT 인증 (`AuthService`, Bearer 토큰)
- JPA 엔티티 + Repository 패턴

### Integration Points

- `GameProcessOrchestrator`: 세션 활성화 시 game-app 프로세스 스폰 — 세션 생성 플로우의 진입점
- `CommandExecutor`: CP 소비 로직이 있는 곳 — @Version 추가 대상
- `TacticalWebSocketController`: executor 스레드 누수가 있는 곳 — HARD-02 수정 대상
- `frontend/src/app/(lobby)/`: 세션 참가 UI 연결 지점

</code_context>

<specifics>
## Specific Ideas

- gin7 원작 충실 재현이 최우선 — 편의 기능보다 원작 메카닉스 우선
- 온/오프라인 구분 없는 것이 원작의 전략적 긴장감 핵심
- 3:2 비율 제한은 추후 시나리오별 커스터마이징 가능하도록 시나리오 JSON에서 읽는 구조 고려

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

_Phase: 01-session-foundation_
_Context gathered: 2026-03-28_
