# Phase 14: 프론트엔드 통합 - Context

**Gathered:** 2026-04-09
**Status:** Ready for planning

<domain>
## Phase Boundary

v2.1에서 구축한 백엔드 기능(엔진 통합, 지휘권 분배·CRC, 지휘 승계, 전술 AI, 작전 연동, 전략 AI)을 프론트엔드에서 완전히 시각화·조작 가능하게 만든다.

**범위:**
- FE-01: 전술맵 CRC 시각화 (내 지휘권 범위 + 실시간 tick 반영)
- FE-02: 분함대 배정 사이드시트 드로어 (드래그&드롭)
- FE-03: 지휘권 기반 UI 게이팅 (disabled + 툴팁 + 제안 경로)
- FE-04: 지휘 승계 피드백 (유닛 위 카운트다운 + 상단 토스트 + 기함 격침 플래시)
- FE-05: 안개(fog of war) — 색적 밖 적 유닛 마지막 목격 위치 고스트
- 백엔드 DTO 확장: `TacticalBattleDto`에 CommandHierarchy/sensorRange/succession 필드 + 승계 이벤트 채널
- 작전계획 UI: 은하맵 오버레이 + 커맨드 패널 '작전계획' 항목
- 전투 종료 모달 + 작전 보너스 요약
- NPC/오프라인 유닛 시각 구분 (상태 마커 + 작전 목적 표시)
- R3F 3D 상단 뷰 **제거**, Konva 2D 단일 렌더러로 단순화 (Phase 6 결정 변경)

**범위 제외:**
- 모바일 전술맵 UI (REQUIREMENTS.md Out of Scope)
- 전술전 리플레이 시스템 (Out of Scope)
- 신규 백엔드 게임 로직 (모든 엔진 기능은 Phase 8-13 완료)
- 음성/음향 시스템 개편 (기존 useSoundEffects.ts 재활용만)
- 튜토리얼 신규 제작 (기존 tutorialStore 유지)

</domain>

<decisions>
## Implementation Decisions

### CRC 시각화 (FE-01)
- **D-01:** 내 지휘권 CRC만 표시 — 로그인 장교가 사령관/분함대장일 때 본인 CRC + 본인이 명령 가능한 아군 지휘관 CRC. 전체 지휘관 CRC 동시 표시는 하지 않음(화면 혼잡 방지).
- **D-02:** 색상 규칙은 진영색 유지(empire=#4466ff / alliance=#ff4444) + 선택/호버 시 hue 변종으로 강조. 지휘관 개인별 팔레트는 도입하지 않음.
- **D-03:** 명령 발령 시 CRC 축소 표현은 **백엔드 tick의 실제 commandRange 값을 보간**하여 반영. 기존 `CommandRangeCircle.tsx`의 로컬 3초 루프 애니메이션은 제거하고 서버 수치를 SoT로 삼는다. Phase 9 "명령 시 0 리셋 → 점진 확대" 로직과 1:1 동기.
- **D-04:** 기존 `CommandRangeCircle.tsx`는 다중 렌더 + 실제값 연동을 위해 재작성 필요 (단일 selectedUnit 한정 → fleetId → hierarchy 매핑 기반 다중 렌더).

### 분함대 배정 UX (FE-02)
- **D-05:** 드래그&드롭 인터랙션 — 60유닛 카드를 부사령관/참모 6명/전계 버킷 영역으로 드래그. 라이브러리는 `@dnd-kit/core` 권장(React 19 호환성). react-dnd는 피한다.
- **D-06:** 배정 타이밍 — `PREPARING` phase에서는 자유 배정, `ACTIVE` phase에서는 CMD-05 조건(대상 유닛이 CRC 밖 + 정지 상태)만 허용. 조건 불만족 유닛은 드래그 시작 단계에서 회색/블록 표시.
- **D-07:** 배정 패널 UI 형태 — **사이드시트 드로어**(전술 화면 우측 오버레이). 기존 `responsive-sheet.tsx` 재사용 가능. 필요할 때 열고 닫아 전투 흐름 끊지 않음.
- **D-08:** 백엔드 호출은 기존 `AssignSubFleet` / `ReassignUnit` TacticalCommand에 연결 (Phase 9 D-16 "Administrative commands bypass CRC"). WebSocket 커맨드 버퍼 경로 사용.

### 권한 기반 UI 게이팅 (FE-03)
- **D-09:** 내 지휘권 밖 유닛의 명령 버튼은 **disabled 상태 + hover 툴팁**으로 사유 안내 ("지휘권 없음 / 상위자에게 제안하기 Shift+클릭"). 숨김 방식은 사용하지 않음 — 플레이어가 시스템을 이해해야 하기 때문.
- **D-10:** 제안(proposal) 경로 — disabled 버튼을 `Shift+클릭` 또는 2차 액션으로 "상위자에게 제안" 흐름 진입. 기존 `proposal-panel.tsx` 재활용. 별도 전용 마주어드는 만들지 않음.
- **D-11:** 시각 차별은 두 층위 — (a) 내 지휘권 하 유닛에 금색 테두리(항상 표시), (b) 선택 시 InfoPanel 상단에 "본인의 지휘권 하 유닛입니다" / "국의 지휘관" 배지. 공간 인식 + 세부 확인 이중 안내.
- **D-12:** 게이팅 로직 판단은 **프론트엔드에서 hierarchy 데이터 기반 계산** — 서버는 hierarchy 구조만 DTO로 내보내고, FE는 현재 officerId가 해당 유닛의 지휘 체인에 있는지 판단. 서버는 실제 실행 시점에 다시 검증(double-check).

### 지휘 승계 피드백 (FE-04)
- **D-13:** 지역 효과 방식 — 유닛 위 카운트다운 오버레이(30→0) + 상단 Sonner 토스트 알림 "사령관 [X] 전사, 30틱 후 승계". 전체 화면 섬광은 피한다(로비 고도 우려).
- **D-14:** 기함 격침 순간 효과 — 해당 유닛 위치에만 플래시 효과(0.5초 확산 링 + 짧은 흰색 불빛). 화면 전체 효과 없음.
- **D-15:** 사운드는 기존 `useSoundEffects.ts`에 승계/격침 2종만 추가. 복잡한 BGM/사운드 디자인 없음.
- **D-16:** 승계 완료 시 새 사령관 아이콘 주위에 짧은 "지휘 인수" 링 효과 + 토스트 "[새 사령관명] 지휘 인수".

### 안개 (Fog of War, FE-05)
- **D-17:** 렌더링 방식 — **마지막 목격 위치 고스트** 표시. 색적 범위 밖 적은 실시간 위치를 숨기되, 마지막으로 본 시점의 (x, y) + 반투명 회색 실루엣으로 표시. 완전 숨김 아님.
- **D-18:** 시야 기준은 **지휘관 단위 서클** — 분함대장 CRC 내에서 감지된 적 유닛은 동일 지휘 네트워크 상의 모든 아군에게 공유(사령관이 부사령관 정보를 흡수). D-12의 hierarchy 계산과 재사용.
- **D-19:** 시야 반경은 **새 `sensorRange` DTO 필드**(에너지 sensor 슬라이더 기반으로 백엔드에서 계산). `commandRange`와 분리 — 통신 범위와 감지 범위는 gin7에서 별개.
- **D-20:** 고스트 데이터 저장 — 프론트엔드 store (`tacticalStore`) `lastSeenEnemyPositions: Map<fleetId, { x, y, tick, ships }>` 필드. 매 tick에 감지된 적만 업데이트, 감지 누락된 적은 stale 상태로 유지. 서버는 stale 상태를 계산하지 않음(순수 FE 책임).

### 백엔드 DTO 확장
- **D-21:** `TacticalBattleDto`에 신규 필드 — `attackerHierarchy: CommandHierarchyDto`, `defenderHierarchy: CommandHierarchyDto`. CommandHierarchyDto는 `commanderOfficerId`, `subFleets: List<{ commanderOfficerId, memberFleetIds }>`, `successionQueue: List<officerId>`.
- **D-22:** `TacticalUnitDto`에 신규 필드 — `sensorRange: Double`, `subFleetCommanderId: Long?`(속한 분함대 지휘관), `successionState: String?`("PENDING_SUCCESSION" | null), `successionTicksRemaining: Int?`, `isOnline: Boolean`, `isNpc: Boolean`.
- **D-23:** `BattleTickEventDto.type` 신규 항목 추가 — `FLAGSHIP_DESTROYED`, `SUCCESSION_STARTED`, `SUCCESSION_COMPLETED`, `JAMMING_ACTIVE`. 이벤트로 한시적 효과(플래시/토스트) 트리거, 상시 상태는 DTO 필드로 전달.
- **D-24:** 기존 `commandRange` 필드는 유지(값 의미만 명확화: 명령 통신 범위). 새 `sensorRange` 추가.

### R3F 3D 뷰 제거 (Phase 6 결정 변경)
- **D-25:** `TacticalMapR3F.tsx` 및 관련 `BattleCloseViewScene.tsx` 등 R3F 3D 상단 뷰 컴포넌트 **제거**. Phase 14에서 `@react-three/fiber`, `@react-three/drei` 의존성 제거 가능 여부 검토(타 컴포넌트 사용 여부 planner가 확인).
- **D-26:** 전술전 화면은 Konva 2D `BattleMap.tsx` 단일 렌더러로 통일. 상하 분할 대신 `BattleMap`이 전체 화면 차지, 상단 작은 `MiniMap.tsx`는 유지(다른 용도).
- **D-27:** Phase 6 D-"상하 2분할 (상단 3D 연출뷰 + 하단 2D 전술맵)" 결정을 **덮어씀**. 이유: HUD 일관성 + 구현 비용 절감 + 성능 안정성. 원작 gin7도 2D 중심이었음.

### 작전계획 UI (Phase 12 연동)
- **D-28:** 은하맵 시각화 — **상세 퀘어리 오버레이 모드** (F1 토글형). 기본 은하맵은 현재 상태 유지, 토글 시 CONQUEST/DEFENSE/SWEEP 아이콘을 목표 성계에 배지, 참여 함대→목표 점선 경로선, 적 전력 추정(sensor 한정) 표시.
- **D-29:** 작전 발령 진입점 — 기존 지휘 권한 커맨드 패널(`command-panel.tsx`)의 "작전계획" 항목을 통해. 기존 commandStore 흐름 재활용. 은하맵 우클릭 방식은 도입하지 않음(추후 여유가 있으면 추가 고려).
- **D-30:** 진행 중 작전 리스트는 은하맵 사이드 패널에 표시 — 항목 클릭 시 해당 목표 성계로 카메라 포커스 + 강조 효과. 기존 galaxyStore에 `activeOperations` 필드 추가.
- **D-31:** 작전 상태 실시간 업데이트는 WebSocket 이벤트로 동기화 — Phase 12 D-08 "TacticalBattleService 동기화 채널" 패턴 재활용, 전략 게임 측에도 `/topic/world/{sessionId}/operations` 채널 추가.

### 전투 종료 화면
- **D-32:** **전체 화면 모달** — 승패 헤더(진영 로고 + "승리"/"패배"), 중앙에 유닛별 요약 테이블, 하단에 "전략맵으로" CTA. BattleResult는 기존 DTO의 `result` 필드 활용.
- **D-33:** 공적 보너스 표시 — 각 참여 유닛 행에 "기본 X + 작전 +Y = 총 Z" 형식으로 분해 표시. 작전 참여 유닛은 배경 하이라이트. Phase 12 OPS-02 검증을 UI에서 직접 확인 가능.
- **D-34:** 추가 정보 — 유닛별 생존/격침, 총 교전 틱, 적 유닛 격침 수, 피해량. 미세 스탯 나열은 생략(주요 메트릭만).

### NPC/오프라인 유닛 시각 구분
- **D-35:** 각 `TacticalUnitIcon` 우상단에 작은 상태 마커 — `●` 초록(online), `○` 회색(offline), `🤖`(NPC). 아이콘 본체는 그대로(불투명도 차등 없음) — 파괴 상태와 구분 필요.
- **D-36:** NPC 선택 시 `InfoPanel`에 작전 목적 표시 — "현재 목적: CONQUEST / 목표: 엘 파실 성계 / 추적: [적 유닛]". 이동 방향선을 지도에 점선으로 표시(선택된 경우만).
- **D-37:** `TacticalUnitDto`의 `isOnline`, `isNpc` 필드(D-22)를 사용. `missionObjectiveByFleetId`가 DTO에 노출 필요 — `TacticalUnitDto.missionObjective: String?` 추가 고려.

### Claude's Discretion
- 드로어/모달 정확한 애니메이션 (framer-motion 사용 여부)
- Konva 성능 최적화 (레이어 분리, listening=false 범위)
- 고스트 데이터 만료 시간(60틱 이후 자동 제거 등)
- 테스트 전략 (vitest 단위 + Playwright 스냅샷 범위)
- F1 토글 키 바인딩 및 다른 단축키 충돌 확인
- 제안 경로 Shift+클릭 vs 우클릭 vs 롱프레스 중 최종 선택
- 분함대 드롭존의 정확한 레이아웃 (그리드 vs 플렉스)
- 상단 토스트 라이브러리 설정 (기존 sonner 테마 조정)
- R3F 제거 시 발생하는 경로 테스트 업데이트 범위
- DTO 확장에 따른 jackson 직렬화 설정 세부

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 프론트엔드 전술 컴포넌트 (확장/수정 대상)
- `frontend/src/components/tactical/BattleMap.tsx` — Konva 메인 전술맵, Layer/Stage 구조
- `frontend/src/components/tactical/CommandRangeCircle.tsx` — CRC 렌더러 (재작성 대상)
- `frontend/src/components/tactical/TacticalUnitIcon.tsx` — 아이콘 규칙 △□, 상태 마커 추가 위치
- `frontend/src/components/tactical/InfoPanel.tsx` — 권한 배지/작전 목적 표시 위치
- `frontend/src/components/tactical/EnergyPanel.tsx` — 에너지 슬라이더 (게이팅 대상)
- `frontend/src/components/tactical/FormationSelector.tsx` — 진형 선택 (게이팅 대상)
- `frontend/src/components/tactical/MiniMap.tsx` — 미니맵 유지 확인
- `frontend/src/components/tactical/TacticalMapR3F.tsx` — **제거 대상** (D-25)
- `frontend/src/components/tactical/BattleCloseViewScene.tsx` — **제거 대상** (D-25)
- `frontend/src/components/tactical/BattleCloseView.tsx` — R3F 연관 확인 후 제거/정리
- `frontend/src/components/tactical/BattleCloseViewPanel.tsx` — 동일

### 프론트엔드 게임 UI
- `frontend/src/components/game/command-panel.tsx` — 작전계획 항목 추가 위치 (D-29)
- `frontend/src/components/game/command-execution-panel.tsx` — disabled 상태 + 툴팁 (D-09)
- `frontend/src/components/game/proposal-panel.tsx` — 제안 경로 재활용 (D-10)
- `frontend/src/components/game/fleet-composition-panel.tsx` — 분함대 배정 UX 참고 (D-05)
- `frontend/src/components/game/map-canvas.tsx` — 은하맵 작전 오버레이 위치 (D-28)
- `frontend/src/components/game/unit-markers.tsx` — 유닛 마커 패턴
- `frontend/src/components/responsive-sheet.tsx` — 사이드시트 드로어 재사용 (D-07)

### 상태 관리 (Zustand Stores)
- `frontend/src/stores/tacticalStore.ts` — `lastSeenEnemyPositions` 추가 위치 (D-20)
- `frontend/src/stores/galaxyStore.ts` — `activeOperations` 추가 위치 (D-30)
- `frontend/src/stores/commandStore.ts` — 작전계획 커맨드 흐름
- `frontend/src/stores/worldStore.ts` — 세션 단위 상태
- `frontend/src/stores/officerStore.ts` — 본인 지휘권 판단 기준

### 타입 정의
- `frontend/src/types/tactical.ts` — TacticalUnit, TacticalBattle, BattleTickEvent (DTO 동기화 대상)

### 훅/유틸
- `frontend/src/hooks/useWebSocket.ts` — STOMP 구독 (/topic/world/{sessionId}/operations 추가)
- `frontend/src/hooks/useSoundEffects.ts` — 승계/격침 사운드 추가 (D-15)
- `frontend/src/hooks/useHotkeys.ts` — F1 토글 바인딩 (D-28)

### 백엔드 DTO/컨트롤러 (확장 대상)
- `backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt` — DTO 확장 (D-21~D-24)
- `backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt` — REST 응답
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — DTO 빌더
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt` — DTO 매핑 소스
- `backend/game-app/src/main/kotlin/com/openlogh/model/CommandRange.kt` — 기존 CRC 모델

### 백엔드 엔진/서비스 (참조)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — 이벤트 발행 지점
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/SuccessionService.kt` — 승계 이벤트 트리거 소스
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommunicationJamming.kt` — JAMMING_ACTIVE 이벤트 소스
- `backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt` — 작전 UI 백엔드 호출 대상
- `backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt` — 작전 조회

### 프로젝트 가이드/이전 결정
- `CLAUDE.md` — 도메인 매핑, 함종, UI 텍스트 한국어 규칙
- `.planning/PROJECT.md` — 한국어 UI, 삼국지 용어 제거
- `.planning/REQUIREMENTS.md` — FE-01~FE-05 요구사항 정의
- `.planning/phases/06-frontend-integration/06-CONTEXT.md` — 전임 FE 결정 (D-25, D-27로 일부 덮어씀)
- `.planning/phases/09-strategic-commands/09-CONTEXT.md` — CRC 백엔드 모델
- `.planning/phases/10-tactical-combat/10-CONTEXT.md` — 승계 모델
- `.planning/phases/11-tactical-ai/11-CONTEXT.md` — 전술 AI 동작
- `.planning/phases/12-operation-integration/12-CONTEXT.md` — 작전 엔티티/동기화 채널
- `.planning/phases/13-ai/13-CONTEXT.md` — 전략 AI 산출물

### 외부 문서
- `docs/REWRITE_PROMPT.md` — 프론트엔드 전면 재작성 스펙
- `docs/reference/` — gin7 매뉴얼 Chapter 2 (게임 화면과 조작방법)
- `/Users/apple/Downloads/gin7manualsaved.pdf` — gin7 공식 매뉴얼 (UI 참고 페이지 18-25, 전술전 화면)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`BattleMap.tsx`**: 1000×1000 게임 좌표, Layer 분리(Background/CommandRange/Units) 이미 확립 — 레이어 추가로 fog/succession 효과 주입 가능
- **`CommandRangeCircle.tsx`**: side 기반 색상 + Konva 애니메이션 기반. 재작성 시 hierarchy-driven 구조로 전환
- **`TacticalUnitIcon.tsx`**: isFlagship→△, 나머지→□ 규칙 확립 (Phase 6 memory rule)
- **`responsive-sheet.tsx`**: 사이드시트 드로어 재사용 가능 (D-07)
- **`proposal-panel.tsx`**: 제안 흐름 이미 존재 (D-10)
- **`command-panel.tsx`**: Position card 기반 탭 구조 — 작전계획 항목 추가 확장
- **`sonner`**: 토스트 알림 시스템 이미 설치됨 (D-13)
- **Zustand stores**: tacticalStore.onBattleTick WebSocket 리듀서 패턴 — fog, hierarchy, events 모두 동일 경로로 주입 가능
- **`useWebSocket.ts`**: STOMP 구독 래퍼 이미 존재 — 새 채널 추가 쉬움
- **`useHotkeys.ts`**: 키 바인딩 인프라 있음 (F1 토글)
- **`TacticalBattleDtos.kt`**: 기본 DTO 이미 있음 — 필드 추가만 필요

### Established Patterns
- **Konva Layer 분리**: BattleMap에서 배경/그리드/CRC/유닛 4레이어 구조 — 추가 레이어(fog, succession-fx) 동일 패턴
- **WebSocket reducer**: `onBattleTick(data)` → Zustand set → React 리렌더. 새 이벤트 타입도 동일 경로
- **진영색 상수**: `#4466ff` / `#ff4444` 리터럴 — 공통 constants 파일로 추출 고려(planner 재량)
- **Sidesheet + Sonner**: Radix/sonner 이미 설치, 기존 mobile-menu-sheet 패턴 재사용
- **PositionCard 게이팅**: 백엔드 `CommandGating`과 1:1 매핑 패턴 — FE에서도 동일 hierarchy 기반 계산
- **CommandExecutor 플로우**: 기존 커맨드 파이프라인 유지, 작전계획도 동일 경로

### Integration Points
- **WebSocket 채널**:
  - 기존: `/topic/world/{sessionId}/events`, `/topic/battle/{battleId}/tick`
  - 추가: `/topic/world/{sessionId}/operations` (작전 실시간 동기화)
- **REST API**:
  - 기존: `/api/{sessionId}/battles/`, `/api/warehouse/`, `/api/command/`
  - 확장: TacticalBattleRestController 응답에 hierarchy/sensorRange 포함
- **커맨드 실행**: `/app/command/{sessionId}/execute` — OperationPlanCommand 이미 존재(Phase 12)
- **상태 동기화 경로**: backend TacticalBattleService → BattleTickBroadcast → frontend tacticalStore.onBattleTick → BattleMap/InfoPanel 리렌더
- **작전 → 은하맵**: backend OperationPlanService mutation → `/topic/world/{sessionId}/operations` 발행 → galaxyStore.activeOperations 갱신

</code_context>

<specifics>
## Specific Ideas

- **"마지막 목격 위치 고스트" 패턴**: StarCraft/Command&Conquer 계열의 전통적 fog of war UX. 플레이어가 "저기 있었는데 어디 갔지?" 긴장감을 준다. 단순 숨김보다 전략 판단에 가치 있음.
- **제안 경로 Shift+클릭**: 기존 Linux/Windows UI 패턴 재활용 — 주액션 + Shift = 대체 액션. 별도 버튼 추가 없이 발견성 유지.
- **R3F 제거 결정의 의의**: Phase 6에서 "LOGH 분위기"를 위해 3D 연출뷰를 채택했지만, 실제 구현과 유지 비용이 조직 시뮬레이션 핵심 가치보다 크다. gin7 원작도 2D 중심이었고, "조직 시뮬레이션" 핵심에 3D 연출은 필수 아님. 리소스를 HUD/UX 완성도에 집중.
- **F1 토글형 작전 오버레이**: 은하맵의 기본 가시성 유지 + 필요 시 상세 퀘어리. "Civilization" 계열의 하이라이트 맵 패턴 — 플레이어가 주도적으로 정보 레이어 선택.
- **작전 보너스 분해 표시 (기본 + 작전 = 총)**: OPS-02 검증이 숨어있던 "이거 진짜 적용되나?" 의문을 UI가 직접 해결. 게임 디자이너/QA/플레이어 모두가 같은 정보 공유.
- **상태 마커 (●/○/🤖)**: 아이콘 위에 작게 — 다른 MMORPG의 파티창 온라인 표시 관례 차용. 플레이어가 "이 유닛 명령은 누가 받는지" 즉시 인지.
- **승계 카운트다운 30→0**: Phase 10의 30틱 공백과 정확히 동기. 백엔드 tick broadcast의 `successionTicksRemaining` 필드 그대로 노출.

</specifics>

<deferred>
## Deferred Ideas

- **모바일 반응형 전술맵**: REQUIREMENTS.md Out of Scope 명시 — v2.1 범위 외
- **전술전 리플레이**: Out of Scope — 복잡도 대비 핵심 가치 낮음
- **음성 채팅**: Out of Scope
- **은하맵 우클릭 작전 발령**: D-29에서 커맨드 패널 경로만 채택 — 추후 UX 개선 시 재검토
- **지휘관 개인별 CRC 고유 팔레트**: D-02에서 진영색만 채택 — 다중 지휘관 활성 빈도가 높아지면 재검토
- **전체 화면 섬광 효과**: D-13에서 지역 효과로 결정 — 서사가 강한 순간(사령부 함락 등) 한정으로 v2.2에서 도입 고려
- **다중 지휘관 CRC 동시 표시**: D-01에서 내 지휘권만 — "관전자 모드" UI 별도 추가 시 재활성화
- **BGM/사운드 디자인 개편**: D-15에서 최소한만 — v2.2 오디오 패치
- **R3F 3D 뷰**: D-25에서 제거 — 유의미한 이유 확보 시 재도입 (예: 전투 리플레이 뷰어)
- **작전 완료 알림 메시지 패널**: Phase 12 deferred와 동일 — Phase 14 작전 UI 통합에서 최소 토스트만, 상세 알림은 추후

</deferred>

---

*Phase: 14-frontend-integration*
*Context gathered: 2026-04-09*
