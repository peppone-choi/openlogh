# Phase 9: Turn Engine Completion - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

턴 파이프라인의 4개 stub 메서드(checkWander, updateOnline, checkOverhead, updateGeneralNumber)를 legacy PHP 로직 그대로 구현하고, turn step ordering이 legacy daemon.ts + func_gamerule.php와 일치하는지 검증하며, 재해/이벤트 트리거 확률과 효과가 legacy와 동일한지 golden value로 검증.

Requirements: TURN-01, TURN-02, TURN-03, TURN-04, TURN-05, TURN-06

</domain>

<decisions>
## Implementation Decisions

### Stub 메서드 구현 범위 (Stub Method Scope)
- **D-01:** 4개 stub 모두 legacy PHP 로직 그대로 구현 — updateOnline(TrafficSnapshotStep과 별도로 per-tick 로직 추가), checkOverhead(JVM 환경 차이에도 legacy guard 로직 이식), checkWander, updateGeneralNumber 전부 필수.
- **D-02:** checkWander()는 CommandExecutor 경유 — legacy와 동일하게 `che_해산` Command 객체를 생성하고 `hasFullConditionMet()` 체크 후 `run()` 실행. 기존 커맨드 시스템 재활용.

### Turn Step Ordering 검증 (Turn Step Ordering Verification)
- **D-03:** 단계별 순서 assertion — legacy postUpdateMonthly()의 호출 순서를 문서화하고, Kotlin TurnService의 실제 호출 순서와 1:1 대조. 순서 불일치 발견 시 즉시 수정 (발견 즉시 수정 패턴 계승).

### Event/Disaster 검증 범위 (Event/Disaster Verification)
- **D-04:** 확률 + 효과 검증 — legacy PHP의 재해 발생 확률(boomRate, 가뭄/역병 등)을 월별로 추출하고 Kotlin 값과 1:1 assertion + 발생 시 도시 상태 변화(pop, agri 등)도 golden value로 검증.

### Plan 분할 전략 (Plan Split Strategy)
- **D-05:** 구현→검증 순차 2분할:
  - Plan 1: 4개 stub 구현(checkWander, updateOnline, checkOverhead, updateGeneralNumber) + 기본 단위 테스트
  - Plan 2: turn step ordering 순서 assertion + 재해 확률/효과 golden value 검증

### 테스트 전략 (Testing Strategy — Phase 5~8 패턴 계승)
- **D-06:** PHP 수동 추적 — PHP 코드를 직접 읽고 고정 입력의 기대값을 수동 계산 후 golden value로 잠금.
- **D-07:** 기존 테스트 파일 확장 우선 — 새 파일 생성보다 기존 구조 보강.

### Claude's Discretion
- stub 메서드별 구체적 구현 세부사항 (PHP → Kotlin 변환 시 DB 쿼리 패턴 등)
- golden value fixture의 게임 상태(seed, 도시/국가 조합) 설계
- updateOnline과 TrafficSnapshotStep 간 중복 처리 방식
- checkOverhead의 JVM 환경 적응 범위
- ordering assertion의 구체적 테스트 구조

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/func.php` §1205–1240 — `updateOnline()` 구현 (온라인 장수 카운트, 국가별 그룹핑, 동접 스냅샷)
- `legacy-core/hwe/func.php` §87–90 — `refreshNationStaticInfo()` 구현 (국가 캐시 초기화)
- `legacy-core/hwe/func_gamerule.php` §174–186 — `updateGeneralNumber()` 구현 (국가별 장수 수 갱신 + refreshNationStaticInfo 호출)
- `legacy-core/hwe/func_gamerule.php` §445–467 — `checkWander()` 구현 (방랑군 자동 해산: officer_level=12 + nation.level=0 → che_해산 실행)
- `legacy-core/hwe/func_gamerule.php` §400–441 — `postUpdateMonthly()` 호출 순서 (checkWander → updateGeneralNumber → refreshNationStaticInfo → checkEmperior → triggerTournament → registerAuction → SetNationFront)
- `legacy-core/hwe/sammo/TurnExecutionHelper.php` §419 — `updateOnline()` 호출 위치 (per-tick)

### Existing Kotlin Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` §987–1082 — 4개 stub 메서드 위치
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` §277–390 — 현재 턴 파이프라인 순서 (per-tick + monthly loop)
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/DisasterAndTradeStep.kt` — Step 1100: 재해/boom 처리
- `backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt` §700–760 — boomRate 확률 계산

### Existing Commands
- `backend/game-app/src/main/kotlin/com/opensam/command/general/che_해산.kt` — checkWander에서 사용할 해산 커맨드 (이미 구현됨)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `che_해산` Command: checkWander()에서 CommandExecutor를 통해 직접 실행 가능 (legacy 패턴과 동일)
- `TrafficSnapshotStep(700)`: 유사한 온라인 스냅샷 로직 — updateOnline과 역할 중복 가능성 확인 필요
- `NationService`, `GeneralRepository`: updateGeneralNumber에서 국가별 장수 수 갱신 시 활용
- `GeneralAccessLogRepository`: updateOnline에서 접속 로그 조회 시 활용

### Established Patterns
- Turn pipeline: TurnService.kt에서 step 200~1700은 TurnPipeline으로, postUpdateMonthly 함수들은 직접 호출 (하이브리드 패턴)
- 각 step은 try-catch로 감싸고 실패 시 로그 후 계속 진행 (legacy daemon.ts 패러티)
- DeterministicRng: 재해/이벤트 확률에 사용 (registerAuction에서 이미 사용 중인 패턴)

### Integration Points
- `TurnService.kt` §360–380: postUpdateMonthly 위치에 checkWander, updateGeneralNumber 호출 이미 존재 (stub만 채우면 됨)
- `TurnService.kt` §279–280: per-tick 위치에 updateOnline, checkOverhead 호출 이미 존재

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-turn-engine-completion*
*Context gathered: 2026-04-02*
