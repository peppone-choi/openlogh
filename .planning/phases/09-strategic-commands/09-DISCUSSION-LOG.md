# Phase 9: 지휘권 분배 + 커맨드레인지서클 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 09-strategic-commands
**Areas discussed:** 분함대 배정 메카닉, CRC 명령 전달 규칙, 지휘권 우선순위 로직, 통신 방해 시스템

---

## 분함대 배정 메카닉

### 배정 단위

| Option | Description | Selected |
|--------|-------------|----------|
| 개별 유닛(ShipUnit) 단위 | 60개 유닛을 하나씩 분함대장에게 배정 (gin7 매뉴얼 기준) | ✓ |
| Fleet 단위 | 현재 SubFleet.unitFleetIds가 List<Long>으로 Fleet ID 기준 — Fleet 단위로 배정 | |
| You decide | gin7 매뉴얼과 현재 코드 구조에 맞게 판단 | |

**User's choice:** 개별 유닛(ShipUnit) 단위
**Notes:** SubFleet.unitFleetIds를 ShipUnit ID 기반으로 변경 필요

### 크기 제한

| Option | Description | Selected |
|--------|-------------|----------|
| 무제한 | 60유닛 범위 내에서 사령관이 자유롭게 분배 (최소 1유닛부터) | ✓ |
| gin7 조직 규칙 적용 | 함대 60유닛, 순찰대 3유닛, 수송함대 23유닛, 지상부대 6유닛 등 조직 유형별 고정 | |
| You decide | 게임 밸런스에 맞게 판단 | |

**User's choice:** 무제한
**Notes:** None

### 배정 시점

| Option | Description | Selected |
|--------|-------------|----------|
| 전투 전 + 전투 중 모두 | 사령관이 실시간으로 재배정 가능 (CMD-05: 서클 밖 + 정지 조건) | ✓ |
| 전투 전에만 | 전투 개시 시 배정 고정, 전투 중 변경 불가 | |
| You decide | CMD-05 요구사항에 맞게 판단 | |

**User's choice:** 전투 전 + 전투 중 모두
**Notes:** CMD-05 조건(서클 밖 + 정지 유닛) 충족 필요

### 지휘관 조건

| Option | Description | Selected |
|--------|-------------|----------|
| gin7 10명 슬롯 규칙 | 함대 슬롯(CrewSlotRole)에 배정된 장교만 분함대장 가능 — 이미 UnitCrew 엔티티 존재 | ✓ |
| 계급 기준 | 일정 계급 이상이면 누구나 분함대장 가능 | |
| You decide | gin7 매뉴얼과 코드 기준으로 판단 | |

**User's choice:** gin7 10명 슬롯 규칙
**Notes:** UnitCrew 엔티티 기반

---

## CRC 명령 전달 규칙

### CRC 반경

| Option | Description | Selected |
|--------|-------------|----------|
| command 스탯 비례 | 지휘관의 command(지휘) 스탯이 높을수록 CRC 최대 반경과 확장 속도가 커짐 — 현재 CommandRange 구조에 부합 | ✓ |
| 고정 반경 | 모든 지휘관 동일 반경, 스탯 무관 | |
| You decide | gin7 매뉴얼과 코드 기준으로 판단 | |

**User's choice:** command 스탯 비례
**Notes:** CommandRange(currentRange/maxRange/expansionRate) 구조 유지

### CRC 밖 행동

| Option | Description | Selected |
|--------|-------------|----------|
| 마지막 명령 유지 | CRC를 벗어나면 마지막으로 받은 명령을 계속 수행, 새 명령 불가 | |
| AI 자율 행동 | CRC 밖이면 Phase 11 전술 AI가 자율적으로 행동 결정 | |
| 복합 | 기본은 마지막 명령 유지, 위험 상황(HP<30%)에서는 AI 자율 퇴각 | ✓ |
| You decide | gin7 기준으로 판단 | |

**User's choice:** 복합
**Notes:** HP<30% 임계값에서 AI 자율 퇴각 전환

### CRC 리셋

| Option | Description | Selected |
|--------|-------------|----------|
| 명령 발령 시 0 리셋 | 현재 구조 유지 — 명령할때마다 CRC가 수축, 명령 빈도와 CRC 크기 간 트레이드오프 | ✓ |
| 기함 변경 시에만 리셋 | 기함이 변경될 때만 CRC 리셋, 이동/공격 명령은 리셋 없음 | |
| You decide | gin7 매뉴얼 기준으로 판단 | |

**User's choice:** 명령 발령 시 0 리셋
**Notes:** None

### CRC 경계

| Option | Description | Selected |
|--------|-------------|----------|
| 이진 판정 | CRC 내에 있으면 명령 수신, 밖으로 나가는 순간 명령 두절 — 단순 거리 비교 | ✓ |
| 버퍼 존 적용 | CRC 반경의 110%까지는 명령 수신 가능 (soft boundary) | |
| You decide | 기술적 판단에 맡김 | |

**User's choice:** 이진 판정
**Notes:** None

---

## 지휘권 우선순위 로직

### 동점 처리

| Option | Description | Selected |
|--------|-------------|----------|
| officerId 오름차순 | 모든 기준이 동점이면 더 오래된(낮은 ID) 장교가 우선 — 결정적이고 단순 | ✓ |
| 랜덤 선택 | 동점 시 랜덤으로 선택 — 매번 결과가 달라질 수 있음 | |
| You decide | 기술적 판단에 맡김 | |

**User's choice:** officerId 오름차순
**Notes:** None

### 재계산 시점

| Option | Description | Selected |
|--------|-------------|----------|
| 이벤트 기반 | 장교 온라인/오프라인 변경, 부상, 사망 시에만 재계산 — 성능 효율적 | ✓ |
| 매 tick | 매 tick마다 전체 우선순위 재계산 — 단순하지만 비용 높음 | |
| You decide | 성능과 정확성 균형에 맡김 | |

**User's choice:** 이벤트 기반
**Notes:** None

### NPC 지휘권

| Option | Description | Selected |
|--------|-------------|----------|
| 온라인 장교 우선 | 온라인 플레이어가 있으면 계급 무관 우선 배정 — gin7 조직 시뮬레이션 철학 | ✓ |
| 계급 순수 적용 | 온라인/오프라인 구분 없이 순수 계급 우선순위로 처리 | |
| You decide | 게임 디자인에 맞게 판단 | |

**User's choice:** 온라인 장교 우선
**Notes:** None

---

## 통신 방해 시스템

### 트리거

| Option | Description | Selected |
|--------|-------------|----------|
| 적 특수 능력/장비 | 적 장교의 특수 능력 또는 특수장비로 통신 방해 발동 — gin7 기준 | ✓ |
| 전투 조건 기반 | 특정 조건(예: 성간 전투, 요새 방어) 시 자동 발동 | |
| You decide | gin7 매뉴얼 기준으로 판단 | |

**User's choice:** 적 특수 능력/장비
**Notes:** None

### 방해 범위

| Option | Description | Selected |
|--------|-------------|----------|
| 전군 명령만 차단 | 총사령관→전군 명령 불가, 분함대장→자기 유닛 명령은 정상 동작 — CMD-06 정확히 충족 | ✓ |
| 전체 CRC 무효화 | commJammed=true면 모든 CRC가 0으로 — 지휘관 마다 자기 유닛도 명령 불가 | |
| You decide | CMD-06 요구사항 기준으로 판단 | |

**User's choice:** 전군 명령만 차단
**Notes:** CMD-06 정확히 충족

### 해제 조건

| Option | Description | Selected |
|--------|-------------|----------|
| 시간 경과 + 적 발동자 격침 | 일정 tick 후 자동 해제, 또는 방해 발동자 격침/퇴각 시 즉시 해제 | ✓ |
| 시간 경과만 | 고정 tick 후 자동 해제 | |
| You decide | gin7 기준으로 판단 | |

**User's choice:** 시간 경과 + 적 발동자 격침
**Notes:** None

---

## Claude's Discretion

- SubFleet.unitFleetIds → ShipUnit ID 기반 리팩토링 방식
- CRC 반경 수식 (command → maxRange/expansionRate)
- HP<30% AI 자율 퇴각 구체적 메카닉
- 통신 방해 지속 tick 수, 특수 능력/장비 연동
- 테스트 전략 및 구현 순서

## Deferred Ideas

None — discussion stayed within phase scope.
