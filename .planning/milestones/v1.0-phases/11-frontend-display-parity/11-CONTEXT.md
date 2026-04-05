# Phase 11: Frontend Display Parity - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

레거시 PHP UI에 표시되는 모든 게임 정보가 현재 프론트엔드에 올바른 데이터 값으로 표시되는지 검증. 40+ 라우트 전수 감사 후 누락 필드를 BE DTO부터 FE 컴포넌트까지 풀스택으로 추가하고, 배틀 로그 전용 컴포넌트를 새로 구현.

Requirements: FE-01, FE-02, FE-03, FE-04

</domain>

<decisions>
## Implementation Decisions

### 검증 방식
- **D-01:** 필드 감사 + Vitest — 레거시 GetFrontInfo.php 및 각 페이지별 API 응답 필드를 목록화하고, 현재 FrontInfoResponse 타입/컴포넌트와 1:1 대조. 누락 필드는 Vitest로 컴포넌트 렌더링 테스트 작성
- **D-02:** 전체 40+ 라우트 전수 감사 — FE-01~04 대상뿐 아니라 auction, betting, diplomacy, vote, spy, tournament 등 모든 게임 페이지를 레거시와 비교
- **D-03:** 테스트 깊이: 계산값 정확성까지 — 타입/필드 존재 검증 + 계산된 값(전투력, 보너스 적용 스탯 등)이 백엔드와 일치하는지까지 검증. mock data로 렌더링 검증

### 배틀 로그 표시
- **D-04:** 새 전용 컴포넌트 구현 — 레거시 배틀 로그 포맷(color tag, 데미지값, 트리거 활성화 등)을 분석하고 매칭하는 전용 컴포넌트 새로 작성
- **D-05:** 레거시 색상 완전 재현 — PHP color tag(`<R>`, `<C>` 등)를 파싱해서 동일한 색상으로 렌더링. 기존 formatLog.ts를 기반으로 확장

### 누락 필드 처리
- **D-06:** 누락 필드 즉시 추가 — 레거시에 있지만 현재 FE에 없는 필드 발견 시 이 Phase에서 바로 반영 (보류하지 않음)
- **D-07:** 백엔드까지 풀스택 추가 — 백엔드 DTO에도 없는 필드(레거시에만 있는 경우) 발견 시 BE DTO → FE 타입 → 컴포넌트 전체 파이프라인 추가. 풀스택 패러티 달성

### 플랜 분할
- **D-08:** 2플랜 분할: 감사/목록화 → 구현/테스트
  - Plan 1: 레거시 40+ 페이지 전수 필드 감사 + 누락 필드 목록화 + 배틀 로그 포맷 분석
  - Plan 2: 누락 필드 추가(BE+FE) + 배틀로그 컴포넌트 구현 + Vitest 테스트(계산값 정확성 포함)

### Claude's Discretion
- 40+ 라우트 감사 시 우선순위 결정 (핵심 페이지 먼저 vs 알파벳순)
- 배틀 로그 컴포넌트의 세부 구조 (단일 파일 vs 서브컴포넌트 분리)
- Vitest 테스트 파일 구조 (페이지별 vs 기능별)
- 레거시에만 있고 opensamguk에서 의미 없는 필드(예: serverCnt) 스킵 여부 판단

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 레거시 프론트엔드 소스
- `legacy-core/hwe/ts/PageFront.vue` — 메인 대시보드 Vue 컴포넌트 (GameInfo, CityBasicCard, NationBasicCard, GeneralBasicCard 조합)
- `legacy-core/hwe/ts/components/GameInfo.vue` — 글로벌 정보 표시 (시나리오, NPC모드, 토너먼트, 연월, 접속자, API제한, 장수수, 경매, 설문)
- `legacy-core/hwe/ts/components/GameBottomBar.vue` — 하단 바 (접속 국가, 접속자)

### 레거시 API
- `legacy-core/hwe/sammo/API/General/GetFrontInfo.php` — 메인 페이지 정보 반환 API (general record, world history, 전체 front info)

### 현재 프론트엔드 핵심 파일
- `frontend/src/types/index.ts` — FrontInfoResponse, GlobalInfo, GeneralFrontInfo, NationFrontInfo, CityFrontInfo 타입 정의
- `frontend/src/components/game/game-dashboard.tsx` — 현재 메인 대시보드
- `frontend/src/components/game/general-basic-card.tsx` — 장수 기본 카드
- `frontend/src/components/game/nation-basic-card.tsx` — 국가 기본 카드
- `frontend/src/components/game/city-basic-card.tsx` — 도시 기본 카드
- `frontend/src/components/game/record-zone.tsx` — 기록/로그 표시 영역
- `frontend/src/lib/formatLog.ts` — 로그 color tag 파싱/포맷팅
- `frontend/src/lib/gameApi.ts` — API 클라이언트 (frontApi 등)

### 현재 백엔드 DTO
- `backend/game-app/src/main/kotlin/com/opensam/dto/` — 게임 DTO 파일들
- `backend/game-app/src/main/kotlin/com/opensam/controller/FrontController.kt` — FrontInfo API 컨트롤러

### 레거시 페이지별 소스 (전수 감사용)
- `legacy-core/hwe/ts/` — 레거시 Vue 페이지/컴포넌트 전체

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `formatLog.ts`: color tag(`<R>`, `<C>` 등) 파싱 유틸리티 — 배틀 로그 렌더링의 기반
- `record-zone.tsx`: 기존 기록 표시 컴포넌트 — 배틀 로그 컴포넌트 참고 가능
- `stat-bar.tsx`, `resource-display.tsx`: 수치 표시 컴포넌트 재사용 가능
- `empty-state.tsx`, `loading-state.tsx`, `error-state.tsx`: 상태 컴포넌트 존재

### Established Patterns
- 컴포넌트: kebab-case `.tsx`, `'use client'` 지시어, inline interface props
- 타입: 모두 `frontend/src/types/index.ts`에 중앙 집중
- API: `frontApi` 객체 리터럴로 도메인별 그룹핑 (`frontend/src/lib/gameApi.ts`)
- 테스트: 소스와 co-located `.test.ts`/`.test.tsx`, Vitest + jsdom/node

### Integration Points
- `FrontInfoResponse` 타입 확장 시 백엔드 DTO 동기화 필요
- 새 배틀 로그 컴포넌트는 `(game)/battle/page.tsx` 라우트에서 사용
- WebSocket STOMP 토픽 `/topic/world/{id}/battle`에서 실시간 배틀 이벤트 수신

</code_context>

<specifics>
## Specific Ideas

- 레거시 `GameInfo.vue`의 정보 밀도(시나리오명, NPC모드, 토너먼트, 접속자, API제한, 장수수, 경매, 설문)를 현재 대시보드에도 동일 수준으로 반영
- 배틀 로그는 레거시 PHP의 color tag를 정확히 재현 (R=빨강, C=청록 등 동일 색상)
- 40+ 라우트 전수 감사이므로, 감사 결과를 구조화된 문서(페이지별 필드 매핑 테이블)로 정리

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 11-frontend-display-parity*
*Context gathered: 2026-04-03*
