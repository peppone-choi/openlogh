# Phase 8: 엔진 통합 + 커맨드 버퍼 - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

듀얼 전술 엔진(TacticalBattleEngine + TacticalCombatEngine)을 단일 엔진으로 통합하고, WebSocket 전술 명령에 커맨드 버퍼 패턴(ConcurrentLinkedQueue)을 도입하며, 지휘 계층 데이터 모델(CommandHierarchy)을 TacticalBattleState에 포함시킨다. engine/war/ 패키지와 중복 컨트롤러를 삭제하여 전술전 코드 경로를 단일화한다.

</domain>

<decisions>
## Implementation Decisions

### 엔진 통합 전략
- **D-01:** TacticalBattleEngine(engine/tactical/)을 기반으로 통합한다. TacticalCombatEngine(engine/war/)의 추가 필드(weaponCooldowns, debuffs, DetectionCapability, CommandRange 객체)를 TacticalBattleEngine.TacticalUnit에 병합한다.
- **D-02:** war/ 패키지의 독립 서비스들(DetectionEngine, PlanetCaptureProcessor, BattleTrigger)을 tactical/ 패키지로 통합한다. 이미 tactical/에 DetectionService, BattleTriggerService, PlanetConquestService가 존재하므로 기능 병합 후 war/ 패키지를 삭제한다.

### 커맨드 버퍼
- **D-03:** 모든 WebSocket 전술 명령(에너지/태세/퇴각/공격대상/진형 등)을 ConcurrentLinkedQueue에 버퍼링한다. tick 시작 시 drain하여 일괄 적용 후 tick을 처리한다. 즉시 적용되는 명령은 없다.
- **D-04:** ConcurrentLinkedQueue는 TacticalBattleState 내부에 commandBuffer 필드로 소유한다. 전투별 격리 + 상태 생성/소멸과 라이프사이클 동기화.

### CommandHierarchy 설계
- **D-05:** Phase 9-10에 필요한 필드를 선제 모델링한다: fleetCommander, subCommanders(Map<Long, SubFleet>), successionQueue, crcRadius(Map<Long, Double>), commJammed 플래그. Phase 8에서는 데이터 모델만 수립하고, 지휘권/승계 로직은 Phase 9-10에서 구현한다.
- **D-06:** 전투 초기화(startBattle()) 시 Fleet 엔티티 기반으로 CommandHierarchy를 자동 생성한다. 사령관은 Fleet의 officerId, 유닛은 해당 Fleet의 ShipUnit들, 승계 대기열은 계급순으로 초기화한다.

### 기존 코드 처리
- **D-07:** 통합 후 engine/war/ 패키지를 완전 삭제한다. TacticalCombatEngine.kt, DetectionEngine.kt, PlanetCaptureProcessor.kt, BattleTrigger.kt 모두 제거.
- **D-08:** 중복 컨트롤러(BattleRestController, TacticalBattleController, TacticalBattleRestController)를 삭제한다. BattleWebSocketController + TacticalBattleService만 유지.

### Claude's Discretion
- TacticalUnit 필드 병합 시 이름 충돌 해소 방식 (예: commandRange Double → CommandRange 객체 전환 방법)
- war/ 서비스와 tactical/ 서비스의 구체적 기능 병합 전략
- 커맨드 버퍼의 TacticalCommand sealed class 설계
- 테스트 전략 및 마이그레이션 순서

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 전술전 엔진
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — 통합 기반 엔진, TacticalUnit 정의
- `backend/game-app/src/main/kotlin/com/openlogh/engine/war/TacticalCombatEngine.kt` — 병합 대상 엔진, TacticalUnit 필드 diff 필수
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — 전투 라이프사이클, activeBattles ConcurrentHashMap
- `backend/game-app/src/main/kotlin/com/openlogh/controller/BattleWebSocketController.kt` — 커맨드 버퍼 도입 대상

### 병합 대상 (war/ → tactical/)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/war/DetectionEngine.kt` — 색적 엔진 (tactical/DetectionService.kt에 병합)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/war/PlanetCaptureProcessor.kt` — 행성 점령 (tactical/PlanetConquestService.kt에 병합)
- `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleTrigger.kt` — 전투 트리거 (tactical/BattleTriggerService.kt에 병합)

### 삭제 대상 컨트롤러
- `backend/game-app/src/main/kotlin/com/openlogh/controller/BattleRestController.kt`
- `backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleController.kt`
- `backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt`

### 참조 문서
- `docs/REWRITE_PROMPT.md` — 전술전 상세 스펙
- `docs/reference/gin4ex_wiki.md` — 전술 모드 상세
- `backend/shared/src/main/resources/data/commands.json` — gin7 81종 커맨드 정의

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TacticalBattleEngine.kt` — 통합 기반, TacticalUnit에 이미 8스탯/에너지/진형/CRC/태세/미사일 필드 존재
- `TacticalBattleService.kt` — ConcurrentHashMap<Long, TacticalBattleState> in-memory 관리, 커맨드 버퍼 도입 지점
- `BattleTriggerService.kt` — buildInitialState() 메서드에 CommandHierarchy 생성 로직 추가 지점
- `EnergyAllocation.kt`, `Formation.kt`, `UnitStance.kt` — 기존 모델 재활용
- `DetectionService.kt`, `FortressGunSystem.kt`, `MissileWeaponSystem.kt`, `GroundBattleEngine.kt` — tactical/ 기존 서비스들

### Established Patterns
- 전투 상태는 in-memory(ConcurrentHashMap), DB는 TacticalBattle 엔티티로 메타데이터만 관리
- WebSocket: STOMP over SockJS, /app/battle/{sessionId}/{battleId}/* 채널
- officerId는 payload에 포함 (JWT principal은 String subject)

### Integration Points
- `TickEngine.kt` / `TickDaemon.kt` — 전술전 tick 호출 지점
- `BattleWebSocketController.kt` — 커맨드 버퍼로의 전환 지점
- `GameEventService.kt` — 전투 이벤트 브로드캐스트

</code_context>

<specifics>
## Specific Ideas

No specific requirements — user selected recommended approaches for all decisions.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 08-scenario-character-system*
*Context gathered: 2026-04-07*
