# Phase 2: Character, Rank, and Organization - Context

**Gathered:** 2026-03-28
**Status:** Ready for planning

<domain>
## Phase Boundary

모든 장교가 8개 능력치를 가진 영속적 정체성, 11단계 계급 래더 위의 위치, 그리고 관계형 PositionCard 테이블 기반의 직무권한을 갖도록 한다. 오리지널/제네레이트 캐릭터 생성 흐름, 조직도 탐색, 장교 프로필 화면을 포함한다.

</domain>

<decisions>
## Implementation Decisions

### 캐릭터 선택/생성 흐름

- **D-01:** 오리지널 캐릭터(라인하르트, 양웬리 등)는 선착순 선택. 세션 참가 시 남은 캐릭터 목록에서 선택하며, 먼저 들어온 플레이어가 유리
- **D-02:** 제네레이트 캐릭터는 총합 포인트 예산을 8개 스탯에 자유롭게 배분. 각 스탯 최소/최대 제한 있음
- **D-03:** 출자(origin)와 분류(career)는 Claude 재량으로 gin7 매뉴얼 참고하여 구현. 제국: 귀족/기사/평민 중 선택, 동맹: 시민 고정. 분류는 기본 군인으로 시작

### 계급 래더와 승진

- **D-04:** 5법칙 계급 래더 기준(공적→작위→훈장→영향력→능력합계)은 플레이어에게 비공개. 승진은 인사권자(인사국장/군무상서/황제 등)가 수동으로 실행
- **D-05:** 자동 승진/강등(30G일마다, 대령 이하) 알림 방식은 Claude 재량
- **D-06:** 계급별 인원 제한(원수 5, 상급대장 5 등) 도달 시 처리는 Claude 재량

### 조직도

- **D-07:** 제국군/동맹군 조직도는 확장/축소 가능한 트리 계층구조로 표시. 황제/의장 → 내각/평의회 → 군무성/국방위 → 함대 순으로 전개. 각 노드에 현직자 이름 표시
- **D-08:** 제국군과 동맹군 조직도의 진영 차별화 방식은 Claude 재량

### 장교 프로필

- **D-09:** 8개 능력치는 수평 바 차트 + 수치로 시각화
- **D-10:** 프로필 화면은 4개 섹션으로 구성:
    1. 기본 정보 (이름, 계급, 진영, 출자, 분류, 나이, 초상화)
    2. 8개 능력치 + 경험치 진행도 바
    3. 직무권한카드 목록 (최대 16장)
    4. 위치/상태 정보 (현재 위치, 소속 함대, 부상, CP 잔량)

### Claude's Discretion

- HARD-03 (PositionCard JSONB→관계형) 마이그레이션 전략 및 구현 방식
- 제네레이트 캐릭터 총합 포인트 수치 및 최소/최대 스탯 제한값
- 자동 승진/강등 알림 방식 (게임 내 메일 등)
- 계급별 인원 제한 도달 시 처리 (승진 차단 등)
- 제국군/동맹군 조직도 비주얼 차별화
- 오리지널 캐릭터 데이터(능력치, 이름, 초상화) 시나리오 JSON 구조
- Flyway 마이그레이션 번호 및 스키마 변경 세부 사항

</decisions>

<canonical_refs>

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Game Rules & Mechanics

- `docs/reference/gin7manual.txt` -- gin7 원작 매뉴얼 (계급 체계, 조직도, 직무권한카드, 캐릭터 생성 규칙)
- `docs/reference/gin7manualsaved.pdf` -- gin7 매뉴얼 원본 PDF (54페이지)
- `docs/reference/logh4_mechanics.md` -- LOGH4 메카닉스 레퍼런스 (능력치, 성장, 계급)

### Requirements & Features

- `.planning/REQUIREMENTS.md` -- Phase 2 요구사항: CHAR-01~15, RANK-01~14, ORG-01~03, ORG-06, ORG-08, PERS-06, HARD-03
- `docs/feature-checklist.md` -- 전체 기능 체크리스트 (P0/P1/P2 우선순위)
- `docs/feature-audit.md` -- gin7 매뉴얼 vs 체크리스트 갭 분석

### Architecture & Existing Code

- `.planning/codebase/ARCHITECTURE.md` -- 시스템 아키텍처 (gateway-app + game-app)
- `.planning/codebase/STRUCTURE.md` -- 코드베이스 디렉터리 구조
- `.planning/codebase/CONVENTIONS.md` -- 코딩 컨벤션 및 네이밍 패턴

### Domain Mapping

- `CLAUDE.md` -- 삼국지→LOGH 도메인 매핑 (엔티티, 능력치, 계급 체계, 조직 구조 전체 정의)

</canonical_refs>

<code_context>

## Existing Code Insights

### Reusable Assets

- `Officer.kt` -- 8개 능력치(leadership~defense) + exp 필드, rank(Short 0~10), careerType, originType, peerage, ops 능력치 3종 이미 정의됨
- `PositionCard.kt` -- 관계형 엔티티 존재 (officerId, sessionId, positionType, positionNameKo, meta)
- `PositionCardSystem.kt` -- 22+ PositionCardType enum, CommandGating(canExecuteCommand, canAddCard), ProposalSystem 구현됨
- `PersonnelCommands.kt` -- 승진/강등/임명/파면/서작/서훈/봉토수여/봉토직할 커맨드 존재 (단, JSONB 기반)
- `PositionCardRepository.kt` -- JPA repository 존재
- `ScenarioService.kt` -- 시나리오 기반 월드 초기화 로직

### Established Patterns

- JPA 엔티티 + Repository 패턴
- 커맨드 CQRS: CommandExecutor → NationCommand/GeneralCommand → ConstraintResult → CommandResult
- Flyway 마이그레이션 (V38까지 존재)
- Officer 엔티티에 @Version 낙관적 락 적용됨 (Phase 1)
- OpenSamguk 호환 alias (init 블록에서 구 필드명 매핑)

### Integration Points

- `PersonnelCommands.kt` -- HARD-03의 핵심 대상. `officer.meta["positionCards"]` JSONB 참조를 PositionCard 테이블 쿼리로 전환 필요
- `CommandGating.canExecuteCommand()` -- heldCards를 PositionCard 테이블에서 조회하도록 변경
- `ScenarioService.kt` -- 오리지널 캐릭터 데이터 로딩 및 세션 초기화 시 Officer 생성
- `frontend/src/app/(lobby)/` -- 캐릭터 선택/생성 UI 연결 지점
- `frontend/src/app/(game)/` -- 조직도, 장교 프로필 페이지 추가 지점

</code_context>

<specifics>
## Specific Ideas

- gin7 원작 충실 재현이 최우선 -- 편의 기능보다 원작 메카닉스 우선 (Phase 1에서 확립)
- 승진은 인사권자가 수동 실행하는 것이 gin7의 "조직 시뮬레이션" 핵심 가치와 일치
- 5법칙 비공개는 원작의 긴장감을 유지하면서도, 인사권자에게는 판단 기준으로 제공될 수 있음
- 조직도 트리뷰 프리뷰에서 보인 것처럼 빈 자리는 [□]로 표시하여 임명 가능 직책을 시각적으로 구분

</specifics>

<deferred>
## Deferred Ideas

None -- discussion stayed within phase scope

</deferred>

---

_Phase: 02-character-rank-and-organization_
_Context gathered: 2026-03-28_
