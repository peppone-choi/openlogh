# Phase 14: 프론트엔드 통합 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-09
**Phase:** 14-frontend-integration
**Areas discussed:** CRC 다중 시각화, 분함대 배정 UX, 권한 UI 게이팅, 승계 피드백+안개+DTO, R3F 3D 뷰 연동, 작전계획 UI, 전투 종료 화면, NPC/오프라인 구분

---

## CRC 다중 시각화

### 표시 범위

| Option | Description | Selected |
|--------|-------------|----------|
| 모든 지휘관 CRC 동시 표시 | 전체 지휘 구조 한눈에 보이나 화면 혼잡 | |
| 내 지휘권 CRC만 | 본인 + 명령 가능한 아군 CRC만 | ✓ |
| 선택한 유닛의 CRC만 | 현재 동작 유지, 단순하나 FE-01 요구사항 불충분 | |

**User's choice:** 내 지휘권 CRC만 (추천)

### 색상 구분

| Option | Description | Selected |
|--------|-------------|----------|
| 진영색 기반 (empire 파랑/alliance 빨강) | 기존 CommandRangeCircle 유지, 다수 지휘관 구분 없음 | |
| 지휘관 개인별 고유 색상 (hue 5-8종) | 누가 지휘하는지 명확하나 색상 피로 | |
| 진영색 + 선택 시 hue 변종 | 기본 가독성 유지 + 선택 시 강조 | ✓ |

**User's choice:** 진영색 + 선택 시 hue 변종 (추천)

### CRC 축소 애니메이션

| Option | Description | Selected |
|--------|-------------|----------|
| 현재 애니메이션 유지 (내부 확장 루프) | 시각만 재생, 백엔드와 무관 | |
| 백엔드 tick 실제값 보간 반영 | 게임플레이와 1:1 동기 | ✓ |
| 두 표현 모두 | 복잡, CRC 2개 그림 | |

**User's choice:** 백엔드 tick에 맞춰 실제 축소 반영 (추천)

---

## 분함대 배정 UX

### 인터랙션 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 드래그&드롭 (유닛 카드 → 지휘관 버킷) | 직관적, 구현 비용 높음 | ✓ |
| 체크박스 리스트 → 상위자 드롭다운 | 단순, 대량 배정 편함 | |
| 트리뷰 → 노드 이동 | 구조 명확하나 직관 낮음 | |

**User's choice:** 드래그&드롭

### 배정 타이밍

| Option | Description | Selected |
|--------|-------------|----------|
| PREPARING phase에서만 | 간결하나 전투 중 변경 불가 | |
| 전투 중 상시 CMD-05 조건 | CRC 밖 + 정지 유닛만 | |
| PREPARING=자유 + ACTIVE=CMD-05 | 백엔드 규칙과 일치 | ✓ |

**User's choice:** 준비 시 전체 + 전투 중 CMD-05 조건 (추천)

### 패널 형태

| Option | Description | Selected |
|--------|-------------|----------|
| 사이드시트 드로어 | 필요할 때만 열고 닫기 | ✓ |
| 전용 탭/패널 (하단 고정) | 상시 가시성 | |
| 모달 (전체 화면 차단) | 배정 집중 가능, ACTIVE에서 불리 | |

**User's choice:** 사이드시트 드로어

---

## 권한 UI 게이팅

### 명령 버튼 처리

| Option | Description | Selected |
|--------|-------------|----------|
| 버튼 disabled + 툴팁으로 사유 안내 | 정보 보여주고 조작만 차단 | ✓ |
| 버튼 완전 숨김 | 깔끔하나 사유 불명 | |
| 정보 전용 뷰로 분기 | 관찰 모드 UX, 구현 복잡 | |

**User's choice:** 버튼 disabled + 툴팁으로 사유 안내 (추천)

### 제안 경로

| Option | Description | Selected |
|--------|-------------|----------|
| disabled 상태에서 Shift+클릭/베리어트 | 단축키 재활용 | ✓ |
| InfoPanel에 별도 "제안하기" 섹션 | 발견성 높음 | |
| Phase 14 범위 외 | 단순 게이팅만 | |

**User's choice:** disabled 상태에서 'up→제안' 단축키/베리어트 버튼

### 시각적 차별

| Option | Description | Selected |
|--------|-------------|----------|
| 테두리 하이라이트 (금색 테두리) | 공간 인식 | |
| 선택 시에만 배지/태그 | 최소 시각 변경 | |
| 둘 다 (테두리 + 선택 배지) | 공간 + 세부 분리 | ✓ |

**User's choice:** 둘 다 (테두리 + 선택 배지, 추천)

---

## 승계 피드백 + 안개 + DTO

### 승계 시각 피드백

| Option | Description | Selected |
|--------|-------------|----------|
| 지역 효과: 유닛 위 카운트다운 + 상단 토스트 | 화면 중단 없음 | ✓ |
| 전체 화면 섬광 연출 | 극적이나 로비 고도 커질 수 있음 | |
| 패널 전용 알림만 | 감성 낮음, FE-04 불충족 | |

**User's choice:** 지역 효과: 유닛 위 카운트다운 + 상단 토스트 알림 (추천)

### Fog of War 렌더링

| Option | Description | Selected |
|--------|-------------|----------|
| 완전 숨김 | 간단 | |
| 마지막 목격 위치 고스트 | 전통적 fog, 긴장감 | ✓ |
| 태그 미표시 + 단순 아이콘만 | 접근 타이밍만 보임 | |

**User's choice:** 마지막 목격 위치 고스트 (추천)

### 시야 기준

| Option | Description | Selected |
|--------|-------------|----------|
| TacticalUnit.commandRange 재활용 | 기존 필드 | |
| 새 sensorRange 필드 (DTO 확장) | gin7 sensor 슬라이더와 일치 | |
| 지휘관 단위 서클 (분함대장 CRC 내 유닛은 내 시야) | 지휘 네트워크 공유 | ✓ |

**User's choice:** 지휘관 단위 서클 (실제로 sensorRange DTO 확장 + hierarchy 결합으로 해석)

### DTO 확장 범위

| Option | Description | Selected |
|--------|-------------|----------|
| 리소스 기본만 (Hierarchy + sensorRange + 승계) | Phase 14 범위 안 | |
| 최소 변경 (기존 필드 재활용) | 정보 불일치 위험 | |
| DTO 확장 + 승계 이벤트 채널 | 상시 상태 DTO + 순간 효과 이벤트 | ✓ |

**User's choice:** DTO 확장 + 승계 이벤트 채널 (추천)

---

## R3F 3D 상단 뷰 연동

### HUD 동기 범위

| Option | Description | Selected |
|--------|-------------|----------|
| R3F는 연출 전용, 모든 HUD는 Konva 2D에만 | R3F 단순 장식 역할 | ✓ |
| R3F에 안개만 동기, CRC/승계는 Konva | 부분 동기화 | |
| 모든 요소 R3F + Konva 동기 | 최대 일관성, 구현 무거움 | |

**User's choice:** R3F는 연출 전용, 모든 HUD는 Konva 2D에만

### R3F 뷰 자체 존치

| Option | Description | Selected |
|--------|-------------|----------|
| 유지 — 기존 배치대로 상하 분할 | Phase 6 결정 유지 | |
| Phase 14에서 제거, Konva 단일 렌더러 | Phase 6 결정 변경 | ✓ |
| 키 토글로 선택적 표시 | 구현 복잡, 테스트 부담 | |

**User's choice:** Phase 14에서 제거, Konva 단일 렌더러

---

## 작전계획 UI

### 은하맵 시각화

| Option | Description | Selected |
|--------|-------------|----------|
| 목표 성계에 작전 유형 배지 + 함대 경로선 | 상시 표시, 정보 밀도 높음 | |
| 사이드 패널 + 클릭 시 카메라 포커스 | 맑끔 유지 | |
| 상세 퀘어리 (테마 지도 오버레이, F1 토글) | 필요 시 정보 레이어 전환 | ✓ |

**User's choice:** 상세 퀘어리 (테마 지도 오버레이)

### 발령 UI 진입점

| Option | Description | Selected |
|--------|-------------|----------|
| 지휘 권한 커맨드 패널의 '작전계획' 항목 | 기존 commandStore 흐름 재활용 | ✓ |
| 은하맵에서 함대 선택 → 목표 성계 마주어드 | UX 우수하나 구현 복잡 | |
| 둘 다 지원 | 유지비 큼 | |

**User's choice:** 지휘 권한 커맨드 패널의 '작전계획' 항목 (추천)

### Phase 14 포함 범위

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 14에 포함 | v2.1 완성 | ✓ |
| 이후 배치 (v2.2 또는 Phase 15) | 분리 | |

**User's choice:** Phase 14에 포함 (추천)

---

## 전투 종료 화면

### 화면 형태

| Option | Description | Selected |
|--------|-------------|----------|
| 전체 화면 모달 (승패 헤더 + 요약) | 확실한 마무리 | ✓ |
| 상단 배너 + 확장 사이드 패널 | 전술맵 유지 | |
| 토스트 + 로그 패널 | 흐름 끊김 최소화, 보너스 인지 부족 | |

**User's choice:** 전체 화면 모달 (승패 헤더 + 요약)

### 보너스 표시

| Option | Description | Selected |
|--------|-------------|----------|
| '기본 X + 작전 +Y = 총 Z' 나열 | 투명, OPS-02 검증 UI | ✓ |
| '★ 작전 참여' 배지 + 툴팁 | 간단 | |
| 구분 없음 (합산만) | OPS-02 인지 불가 | |

**User's choice:** '기본 X + 작전 +Y = 총 Z' 나열 (추천)

---

## NPC/오프라인 유닛 구분

### 시각 구분 방식

| Option | Description | Selected |
|--------|-------------|----------|
| 아이콘 옹 상태 마커 (●/○/🤖) | 고밀도에서도 구분 가능 | ✓ |
| 아이콘 불투명도 차등 | 격침과 구분 어려움 | |
| InfoPanel 선택 시에만 | 지도에서 구분 불가 | |

**User's choice:** 아이콘 옹 상태 마커 (추천)

### NPC 작전 목적 노출

| Option | Description | Selected |
|--------|-------------|----------|
| 선택 시 작전 목적 + 이동 방향선 | 협업 가능성 | ✓ |
| 목적만 표시, 방향선 없음 | 간단 | |
| 아무 정보도 노출 안 함 | 예측 불가 | |

**User's choice:** 선택한 NPC의 작전 목적 + 이동 방향선 표시 (추천)

---

## Claude's Discretion

- 드로어/모달 애니메이션 세부 (framer-motion 사용 여부)
- Konva 성능 최적화 (레이어 분리, listening=false 범위)
- 고스트 데이터 만료 시간 (60틱 기본 제안)
- 테스트 전략 (vitest 단위 + Playwright 스냅샷 범위)
- F1 토글 키 바인딩 및 단축키 충돌 확인
- 제안 경로 Shift+클릭 vs 우클릭 vs 롱프레스 최종 선택
- 분함대 드롭존 레이아웃 (그리드 vs 플렉스)
- Sonner 토스트 테마 설정
- R3F 제거 시 경로 테스트 업데이트 범위
- DTO 확장에 따른 jackson 직렬화 설정

## Deferred Ideas

- 모바일 반응형 전술맵 (Out of Scope)
- 전술전 리플레이 (Out of Scope)
- 은하맵 우클릭 작전 발령 (추후 UX 개선)
- 지휘관 개인별 CRC 고유 팔레트 (사용 빈도 증가 시)
- 전체 화면 섬광 효과 (서사 순간 한정으로 v2.2)
- 다중 지휘관 CRC 동시 표시 (관전자 모드 UI 추가 시)
- BGM/사운드 디자인 개편 (v2.2)
- R3F 3D 뷰 (전투 리플레이 뷰어용으로 재도입 가능)
- 작전 완료 상세 알림 패널 (추후)
