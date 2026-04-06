# Architecture Patterns

**Domain:** gin7 게임 로직 전면 재작성 — 기존 multi-JVM 아키텍처 통합
**Researched:** 2026-04-06
**Sources:** 실제 코드베이스 직접 분석 (CommandRegistry.kt, TickEngine.kt, TacticalBattleEngine.kt, TacticalBattleService.kt, CommandExecutor.kt, EconomyService.kt, Fleet.kt, ShipyardProductionService.kt, commands.json, PROJECT.md, REWRITE_PROMPT.md)

---

## 현재 아키텍처 요약 (통합 기준점)

```
Browser
  │ WebSocket (STOMP) + HTTP
  ▼
gateway-app:8080
  │ HTTP proxy (WebFlux)
  ▼
game-app:9001+
  ├── TickDaemon (1초 tick = 24게임초)
  │   └── TickEngine.processTick(world)
  │       ├── realtimeService.processCompletedCommands()    ← 커맨드 완료 처리
  │       ├── realtimeService.regenerateCommandPoints()     ← 300틱마다 CP 회복
  │       ├── advanceMonth() + runMonthlyPipeline()         ← 월 경계 처리
  │       └── processPolitics()                             ← 쿠데타/선거/차관/페잔
  │
  ├── CommandExecutor (CQRS 커맨드 dispatch)
  │   ├── PositionCard 권한 체크
  │   ├── CP 차감
  │   ├── Cooldown 체크
  │   └── CommandRegistry → 커맨드 실행 → 엔티티 저장
  │
  ├── TacticalBattleService
  │   ├── activeBattles: ConcurrentHashMap<Long, TacticalBattleState>  ← 전부 in-memory
  │   ├── TacticalBattleEngine (순수 함수 엔진, stateless)
  │   └── broadcast → /topic/world/{sessionId}/tactical-battle/{battleId}
  │
  └── EconomyService.processMonthly()  ← 월마다 실행
      └── preUpdateMonthly / postUpdateMonthly
```

**핵심 불변사항**: session_id FK 격리, JPA 엔티티 저장, Redis 캐시, STOMP WebSocket 토픽 구조 유지.

---

## 1. 전술전 엔진 — 배틀 상태 관리 전략

### 현재 구현 (유지)

`TacticalBattleService`는 이미 올바른 패턴을 채택하고 있다:

```kotlin
// 현재: game-app JVM 메모리에서 처리 (올바름)
private val activeBattles = ConcurrentHashMap<Long, TacticalBattleState>()
```

`TacticalBattleState`는 매 틱 변하는 실시간 데이터(posX/Y, velX/Y, hp, energy, commandRange)를 담으며 **DB/Redis로 내보내지 않는다.** 이 패턴은 gin7 전술전에서도 그대로 유지한다.

### 상태별 스토리지 전략

| 상태 유형 | 스토리지 | 이유 |
|-----------|----------|------|
| 유닛 실시간 위치/속도/HP | `ConcurrentHashMap<Long, TacticalBattleState>` (JVM 메모리) | 매 틱 변경, 레이턴시 0 필요 |
| 에너지 배분 (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) | 동일 (in-memory TacticalUnit.energy) | 실시간 변경, 브로드캐스트만 필요 |
| 진형 설정 | 동일 | 전투 중 변경 가능 |
| 미사일 재고 | 동일 (TacticalUnit.missileCount: Int 추가 필요) | 틱마다 소모 |
| 요새포 상태 | 동일 (TacticalBattleState.fortressGun*) | 이미 구현됨 |
| 전투 결과/전사/공적 | PostgreSQL (TacticalBattle + 새 CasualtyRecord 엔티티) | 영속화 필요 |
| 전투 중 지상전 상태 | in-memory (GroundBattleState, TacticalBattleState 하위) | 전투 범위 내 |
| 전투 이력 (재생용) | PostgreSQL (BattleTickSnapshot, 100틱마다 스냅샷) | 재접속 대응 |
| 색적(탐지) 정보 | in-memory (DetectionMatrix per battle) | 매 틱 재계산 |

**Redis는 배틀 상태에 사용하지 않는다.** Redis 직렬화 오버헤드가 1초 틱 사이클에서 병목이 된다. Redis는 현재 사용처(세션 캐시, 연결된 클라이언트 추적)를 유지한다.

### TacticalBattleState 확장 (신규 필드)

```kotlin
// 기존 TacticalUnit에 추가할 필드
var missileCount: Int = 100          // 미사일 재고 (소모형)
var shipSubtype: String = ""         // ShipSubtype 코드 (전투 스탯 결정)
var isFlagship: Boolean = false      // 기함 여부
var flagshipCode: String = ""        // 고유기함 코드 (빌헬미나 등)
var groundUnitsEmbark: Int = 0       // 탑재 육전대 수
var detectionRadius: Double = 0.0   // SENSOR 배분 기반 탐지 반경

// 기존 TacticalBattleState에 추가할 필드
val groundBattleState: GroundBattleState? = null   // 행성 지상전 (별도 박스)
val detectionMatrix: MutableMap<Long, Set<Long>>   // officerId → 탐지된 적 unitId set
var currentPhase: TacticalPhase = TacticalPhase.MOVEMENT  // 보급/이동/수색/교전/점령
```

---

## 2. CommandRegistry 마이그레이션 전략

### 현재 문제

`CommandRegistry.kt`에 93개 삼국지 커맨드가 `init {}` 블록에 직접 등록되어 있다. `commands.json`의 gin7 81종은 문서용으로만 존재하며 실제로 연결되지 않았다.

### 마이그레이션 방법: 병렬 레지스트리 → 단계적 교체

**단계 1: 커맨드 분리 (기존 코드 보존)**

```
CommandRegistry (기존, 삼국지 93종)  ← 삭제 대상
Gin7CommandRegistry (신규, gin7 81종) ← 새로 작성
```

`Gin7CommandRegistry`는 `commands.json`의 7개 그룹을 그대로 반영하는 구조로 작성한다:

```kotlin
@Component
class Gin7CommandRegistry {
    // 7개 CommandGroup 각각 별도 맵
    private val operationsCommands   = mutableMapOf<String, OfficerCommandFactory>()  // 16종
    private val personalCommands     = mutableMapOf<String, OfficerCommandFactory>()  // 15종
    private val commanderCommands    = mutableMapOf<String, OfficerCommandFactory>()  // 8종
    private val logisticsCommands    = mutableMapOf<String, OfficerCommandFactory>()  // 6종
    private val personnelCommands    = mutableMapOf<String, OfficerCommandFactory>()  // 10종
    private val politicsCommands     = mutableMapOf<String, OfficerCommandFactory>()  // 12종
    private val intelligenceCommands = mutableMapOf<String, OfficerCommandFactory>()  // 14종
}
```

**단계 2: CommandExecutor를 Gin7CommandRegistry로 교체**

`CommandExecutor`는 생성자 주입으로 `CommandRegistry`를 받으므로, `Gin7CommandRegistry`가 동일 인터페이스를 구현하면 Spring DI로 교체된다. `@Primary` 어노테이션 활용.

**단계 3: ALWAYS_ALLOWED_COMMANDS 갱신**

현재 `setOf("휴식", "Nation휴식", "NPC능동", "CR건국", "CR맹훈련")`를 gin7 기준으로 교체:
```kotlin
private val ALWAYS_ALLOWED_COMMANDS = setOf("대기") // gin7의 기본 휴식 커맨드
```

**단계 4: 커맨드별 PositionCard 매핑 갱신**

`PositionCardRegistry`의 카드→커맨드 매핑 테이블을 gin7 81종에 맞게 재작성. 기존 82종 카드 코드는 유지하되, 커맨드 연결만 교체한다.

### 커맨드 실행 흐름 (변경 없음)

```
WebSocket /app/command/{sessionId}/execute
  → CommandController
  → CommandExecutor.executeOfficerCommand()
      ├── PositionCard 권한 체크 (유지)
      ├── CP 차감 (PCP/MCP, 유지)
      ├── Cooldown 체크 (유지)
      └── Gin7CommandRegistry → 커맨드 run() → 엔티티 저장
```

실시간 실행 패턴(턴 예약 없음, CP 차감 → 대기시간 → 실행)은 기존 `preReqTurn`/`postReqTurn` 쿨다운 메커니즘으로 표현된다. `waitTime` = `preReqTurn`, `duration` = `postReqTurn`으로 매핑.

---

## 3. 경제 틱 — TickEngine 통합 전략

### 현재 TickEngine 처리 구조

```
processTick(world)
  ├── 매 틱: processCompletedCommands()
  ├── 300틱: regenerateCommandPoints()
  ├── 월 경계(108,000틱): advanceMonth() + runMonthlyPipeline()  ← 경제 처리 위치
  └── 매 틱: processPolitics()
```

`runMonthlyPipeline()`은 현재 로그+브로드캐스트만 하고 있다. 이 지점에 경제 시스템을 연결한다.

### gin7 경제 틱 통합

```kotlin
private fun runMonthlyPipeline(world: SessionState) {
    // 기존 브로드캐스트 유지
    gameEventService.broadcastTurnAdvance(...)

    // gin7 경제 파이프라인 추가
    val sessionId = world.id.toLong()

    // 1. 조병창 자동생산 (매월)
    shipyardProductionService.runProduction(sessionId)

    // 2. 세금 처리 (3개월마다: 1/1, 4/1, 7/1, 10/1)
    if (world.currentMonth.toInt() in listOf(1, 4, 7, 10)) {
        gin7EconomyService.processTaxCollection(sessionId)
    }

    // 3. 함대 운영비 차감 (매월)
    gin7EconomyService.processFleetMaintenance(sessionId)

    // 4. 시설 유지비 차감 (매월)
    gin7EconomyService.processFacilityMaintenance(sessionId)

    // 5. 페잔 차관 이자 (3개월마다)
    if (world.currentMonth.toInt() in listOf(1, 4, 7, 10)) {
        fezzanService.processLoanQuarterly(sessionId)
    }
}
```

**기존 EconomyService는 삼국지 로직을 포함하므로 `Gin7EconomyService`로 대체한다.** 기존 `EconomyService`의 `processMonthly()` 호출은 `runMonthlyPipeline()`에서 제거하고 `Gin7EconomyService`로 교체.

### gin7 경제 모델 핵심 데이터 흐름

```
Planet.production + Planet.commerce
  ↓ (매월)
Planet.taxRevenue 계산 (세율 × 경제력)
  ↓
Faction.funds += taxRevenue (제국: 군무성/통수본부 분리)
  ↓
Faction.funds -= fleetMaintenanceCost (출격 중 함대 수 × 비용)
Faction.funds -= facilityMaintenanceCost (조병창/체재기지/방위기지)
  ↓
FezzanLoan 이자 처리 (못 갚으면 fezzanEndingService.checkAndTrigger)
```

---

## 4. 함선 유닛 엔티티 — 기존 Fleet/Officer 연결

### 현재 Fleet 엔티티 구조

```kotlin
Fleet {
    id, sessionId, leaderOfficerId, factionId, name,
    unitType: String = "FLEET",     // UnitType enum 코드
    maxUnits: Int = 60,             // 최대 부대 수
    currentUnits: Int = 0,          // 현재 부대 수
    maxCrew: Int = 10,              // 최대 승조원 수 (참모 포함)
    planetId: Long?,                // 현재 위치
    meta: MutableMap<String, Any>,  // 유연 필드
}
```

`Fleet`은 함대 조직 단위다. 함선 유닛(ShipUnit)은 `Fleet` 내 부대(UnitCrew/Slot)에 배속된다.

### 신규 ShipUnit 엔티티 (V45 마이그레이션)

```kotlin
@Entity @Table(name = "ship_unit")
class ShipUnit(
    @Id @GeneratedValue
    var id: Long = 0,

    @Column(name = "session_id") var sessionId: Long = 0,
    @Column(name = "fleet_id") var fleetId: Long = 0,          // 소속 Fleet
    @Column(name = "slot_index") var slotIndex: Int = 0,        // 함대 내 부대 번호 (0~7)

    // 함종 정보
    @Column(name = "ship_class") var shipClass: String = "BATTLESHIP",     // ShipClassType enum
    @Column(name = "ship_subtype") var shipSubtype: String = "TYPE_I",     // ShipSubtype enum

    // 전투 수치 (ship_stats JSON에서 로드)
    @Column var shipCount: Int = 0,         // 현재 함수 (300 단위)
    @Column var maxShipCount: Int = 300,    // 최대 함수
    @Column var armor: Int = 0,             // 장갑
    @Column var shield: Int = 0,            // 실드
    @Column var weaponPower: Int = 0,       // 무기 위력
    @Column var speed: Int = 0,             // 속도
    @Column var crewCapacity: Int = 0,      // 승무원 용량
    @Column var supplyCapacity: Int = 0,    // 물자 적재량

    // 상태
    @Column var morale: Short = 50,
    @Column var training: Short = 50,
    @Column(name = "missile_stock") var missileStock: Int = 100,
    @Column var stance: String = "CRUISE",  // UnitStance: NAVIGATION/DOCK/CRUISE/COMBAT

    // 기함 정보
    @Column(name = "is_flagship") var isFlagship: Boolean = false,
    @Column(name = "flagship_code") var flagshipCode: String = "",

    // 지상부대
    @Column(name = "ground_unit_type") var groundUnitType: String = "",  // GroundUnitType
    @Column(name = "ground_unit_count") var groundUnitCount: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var meta: MutableMap<String, Any> = mutableMapOf(),
)
```

### Fleet → ShipUnit 관계

```
Fleet (함대 조직)
  ├── leaderOfficerId → Officer (사령관)
  ├── UnitCrew[] (슬롯: 참모/부관/부사령관 등)
  └── ShipUnit[] (부대 0~7번, fleetId FK)
       └── 각 ShipUnit: 300척 단위, 함종+서브타입 결정
```

`Officer.ships` 필드(현재 총 함선 수)는 함대 사령관의 기함 부대(`SlotIndex=0`) ShipUnit의 합산으로 계산되도록 점진 마이그레이션한다.

---

## 5. 프론트엔드 WebSocket 채널 전략

### 현재 채널 구조 (유지)

```
/topic/world/{sessionId}/events          ← 전략 게임 이벤트 (브로드캐스트)
/topic/world/{sessionId}/tactical-battle/{battleId}  ← 전술전 틱 브로드캐스트
/app/command/{sessionId}/execute         ← 커맨드 실행 (단방향)
```

`TacticalBattleService.broadcastBattleState()`는 이미 `/topic/world/{sessionId}/tactical-battle/{battleId}`로 전송 중이다.

### 신규 채널 (추가 필요)

```
/topic/world/{sessionId}/battle-view/{battleId}
    └── 전술맵 전체 상태 (저주파, 5초마다) — 전략 화면 미니맵용

/app/battle/{sessionId}/{battleId}/energy
    └── 에너지 배분 변경 (클라이언트 → 서버)

/app/battle/{sessionId}/{battleId}/formation
    └── 진형 변경

/app/battle/{sessionId}/{battleId}/retreat
    └── 퇴각 명령

/app/battle/{sessionId}/{battleId}/attack-target
    └── 공격 대상 지정 (교전 턴)

/app/battle/{sessionId}/{battleId}/planet-conquest
    └── 행성 점령 커맨드 (항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동)

/topic/world/{sessionId}/economy
    └── 월간 경제 처리 결과 브로드캐스트 (세수/생산/유지비)
```

### 프론트엔드 화면별 채널 구독

| 화면 | 구독 채널 | 갱신 주기 |
|------|-----------|----------|
| 전략 게임 화면 | `/topic/world/{id}/events` | 틱 브로드캐스트 간격 |
| 전술전 전체 맵 | `/topic/world/{id}/tactical-battle/{bid}` | 매 틱 (1초) |
| 전투 연출 뷰 (접근전) | 동일 채널 (events 필터링) | 매 틱 |
| 은하맵 | `/topic/world/{id}/events` + REST | 틱 브로드캐스트 |

---

## 6. AI 처리 부하 분산

### 현재 AI 구조

`OfflinePlayerAIService` — 오프라인 플레이어의 커맨드를 자동 실행. `FezzanAiService` — 페잔 AI 자체 행동. 둘 다 TickEngine에서 호출된다.

### gin7 AI 아키텍처

```
TickEngine.processTick()
  └── (매 10틱) aiScheduler.processAiTick(sessionId, world.tickCount)
       ├── OfflinePlayerAiService (오프라인 플레이어 대행, 기존 패턴 유지)
       │   └── 성격 기반 커맨드 선택 → CommandExecutor.executeOfficerCommand()
       ├── FactionAiService (진영 AI: 작전수립/예산/인사)
       │   └── 10틱마다 1개 진영 AI 처리 (라운드 로빈)
       └── NpcAiService (NPC 개인 행동)
           └── 100틱마다 일괄 처리
```

**부하 분산 원칙:**
- AI는 매 틱 전부 실행하지 않는다. 10틱/100틱 인터벌로 나눈다.
- 진영 AI는 라운드 로빈으로 1틱에 1진영만 처리 (`world.tickCount % factionCount`).
- 커맨드 실행은 기존 `CommandExecutor`를 재사용 — AI와 플레이어가 동일 검증 경로를 통과한다.
- 전술전 중 AI 처리: 에너지 배분 결정만 in-memory 업데이트 (DB 접근 없음).

---

## 컴포넌트 경계 (신규 vs 수정)

### 신규 생성 컴포넌트

| 컴포넌트 | 위치 | 목적 |
|----------|------|------|
| `Gin7CommandRegistry` | `command/` | gin7 81종 커맨드 등록 |
| `command/operations/` 패키지 | `command/operations/` | 작전커맨드 16종 구현체 |
| `command/personal/` 패키지 | `command/personal/` | 개인커맨드 15종 |
| `command/commander/` 패키지 | `command/commander/` | 지휘커맨드 8종 |
| `command/logistics/` 패키지 | `command/logistics/` | 병참커맨드 6종 |
| `command/personnel/` 패키지 | `command/personnel/` | 인사커맨드 10종 |
| `command/politics/` 패키지 | `command/politics/` | 정치커맨드 12종 |
| `command/intelligence/` 패키지 | `command/intelligence/` | 첩보커맨드 14종 |
| `ShipUnit` 엔티티 | `entity/ShipUnit.kt` | 함정 유닛 (300척 단위) |
| `ShipUnitRepository` | `repository/` | ShipUnit CRUD |
| `ShipStatRegistry` | `service/` | ship_stats JSON 로드/조회 |
| `Gin7EconomyService` | `engine/` | gin7 경제 로직 |
| `FleetFormationService` | `service/` | 함대 편성 규칙 |
| `FactionAiService` | `service/ai/` | 진영 AI 의사결정 |
| `TacticalPhaseService` | `engine/tactical/` | 전술 턴 5단계 관리 |
| `GroundBattleEngine` | `engine/tactical/` | 지상전 박스 처리 |
| `PlanetConquestService` | `engine/tactical/` | 점령 커맨드 6종 |
| `DetectionService` | `engine/tactical/` | 색적/탐지 계산 |
| `MissileWeaponSystem` | `engine/tactical/` | 미사일 소모형 무기 |
| `FlagshipService` | `service/` | 기함 구매/배정/계급 연동 |
| V45~V5x Flyway 마이그레이션 | `resources/db/migration/` | ShipUnit 테이블 + 삼국지 필드 제거 |

### 기존 수정 컴포넌트

| 컴포넌트 | 변경 내용 |
|----------|---------|
| `CommandRegistry` | `@Primary` 제거 또는 삭제. Gin7CommandRegistry로 교체 |
| `CommandExecutor` | `CommandRegistry` → `Gin7CommandRegistry` 의존성 교체. ALWAYS_ALLOWED_COMMANDS 갱신 |
| `TickEngine` | `runMonthlyPipeline()` 에 `Gin7EconomyService` 연결. AI 스케줄러 추가 |
| `TacticalBattleService` | `TacticalUnit`에 missileCount/shipSubtype/isFlagship 필드 대응. 새 채널 추가 |
| `TacticalBattleEngine` | 미사일 무기 시스템 추가. 색적 계산 연동. 진형 4종 보정값 gin7 수치로 교체 |
| `TacticalBattleState` | groundBattleState, detectionMatrix, currentPhase 필드 추가 |
| `TacticalBattleController` (WebSocket) | 신규 배틀 채널 `/app/battle/**` 엔드포인트 추가 |
| `Fleet` | `meta` JSON에 flagship_code, formation, stance 저장 (V45 이전은 meta 활용) |
| `EconomyService` | 삼국지 로직 제거. gin7 세율/조병창/운영비 로직으로 교체 |
| `ShipyardProductionService` | 기존 구조 유지. ShipClassType 다양화. 진영별 생산 품목 구분 |

### 삭제 대상 컴포넌트

| 컴포넌트 | 이유 |
|----------|------|
| `command/general/DomesticCommand.kt` 내 삼국지 커맨드들 | 농지개간/상업투자/징병 등 삼국지 전용 |
| `command/nation/` 내 삼국지 국가 커맨드들 | 칭제/선양/천자맞이 등 삼국지 전용 |
| `engine/war/BattleEngine.kt`, `FieldBattleService.kt` | 삼국지 수치비교 자동전투 |
| `engine/war/GroundBattleEngine.kt` (기존) | 삼국지 지상전 로직 (gin7 버전으로 대체) |

---

## 신규 API 엔드포인트

### REST (game-app 직접 또는 gateway 프록시)

```
POST /api/{sessionId}/battles/start
     body: { starSystemId, attackerFleetIds[], defenderFleetIds[] }

GET  /api/{sessionId}/battles/active
GET  /api/{sessionId}/battles/{battleId}

POST /api/{sessionId}/fleets/{fleetId}/ship-units
     body: { shipClass, shipSubtype, shipCount }

GET  /api/{sessionId}/fleets/{fleetId}/ship-units

POST /api/{sessionId}/officers/{officerId}/flagship
     body: { flagshipCode }

GET  /api/{sessionId}/economy/status
     ← 진영별 자금/세율/조병창 현황

POST /api/{sessionId}/economy/tax-rate
     body: { planetId, taxRate }
```

### WebSocket (STOMP)

```
/app/command/{sessionId}/execute          (기존 유지)
/app/battle/{sessionId}/{battleId}/energy
/app/battle/{sessionId}/{battleId}/formation
/app/battle/{sessionId}/{battleId}/retreat
/app/battle/{sessionId}/{battleId}/attack-target
/app/battle/{sessionId}/{battleId}/planet-conquest
```

---

## 빌드 순서 (의존성 기반)

의존성 방향: 엔티티 → 리포지토리 → 서비스 → 커맨드 → 컨트롤러

```
1단계: 삼국지 제거 + DB 스키마 정리
   └── V45 마이그레이션: crewType/병종 컬럼 제거
   └── 삼국지 커맨드 구현체 파일 삭제 (CommandRegistry 비움)
   └── BattleEngine/FieldBattleService 삼국지 로직 제거

2단계: ShipUnit 엔티티 + 함선 스탯
   └── V46 마이그레이션: ship_unit 테이블 생성
   └── ShipUnit 엔티티 + ShipUnitRepository
   └── ShipStatRegistry (JSON 로드)
   └── Fleet 엔티티 meta 필드 활용 또는 V47 신규 컬럼 추가

3단계: gin7 커맨드 시스템
   └── Gin7CommandRegistry (빈 껍데기 먼저)
   └── CommandExecutor → Gin7CommandRegistry 연결
   └── 커맨드 그룹별 구현체 (operations → personal → commander → logistics → personnel → politics → intelligence 순)
   └── PositionCardRegistry 커맨드 매핑 갱신

4단계: 전술전 엔진 확장
   └── TacticalUnit 신규 필드 추가
   └── MissileWeaponSystem
   └── DetectionService (SENSOR 기반 색적)
   └── TacticalPhaseService (5단계 턴)
   └── GroundBattleEngine (gin7 지상전)
   └── PlanetConquestService (점령 6종)
   └── 신규 WebSocket 채널 추가

5단계: 경제 시스템
   └── Gin7EconomyService
   └── TickEngine.runMonthlyPipeline() 연결
   └── FleetMaintenance 로직
   └── FezzanLoan 쿼터리 처리 (기존 fezzanService 확장)

6단계: AI 시스템
   └── FactionAiService
   └── NpcAiService 성격 기반 로직
   └── TickEngine AI 스케줄러 연결

7단계: 프론트엔드 (각 백엔드 시스템 완성 후)
   └── 전술전 UI (TacticalBattleEngine 완성 후)
   └── 전략 게임 화면 (커맨드 시스템 완성 후)
   └── 경제 UI (Gin7EconomyService 완성 후)
```

---

## 패턴: 전술전 틱과 전략 틱 분리

gin7의 핵심 설계: 전략 게임(1틱=24게임초)과 전술전(1틱=1전술초)은 **별도 루프**다.

```
TickDaemon (1초 interval)
  └── TickEngine.processTick(world)       ← 전략 게임 틱
      └── TacticalBattleService.processSessionBattles(sessionId)
           └── 각 activeBattle에 대해 TacticalBattleEngine.processTick()
                                          ← 전술전 틱 (동일 스레드, 동기)
```

현재 구현이 이미 올바른 구조다. `processSessionBattles()`가 TickEngine에서 호출되고 있으며, 전술전 틱이 전략 틱 내부에서 처리된다. 이 패턴을 유지하되 전술전 내 5단계 턴(보급/이동/수색/교전/점령) 진행을 `TacticalPhaseService`가 관리한다.

**주의**: 전술전이 길어질 경우 전략 틱이 1초를 초과할 수 있다. 대규모 전투(유닛 50개 이상) 시에는 전술전 틱을 별도 `@Scheduled(fixedDelay=100)` 루프로 분리하는 것을 3단계 완성 후 검토한다.

---

## 안티패턴 경고

### 금지: 전술전 상태를 Redis에 저장
TacticalBattleState를 Redis에 저장하면 직렬화/역직렬화 오버헤드가 1초 틱 사이클에서 병목이 된다. activeBattles ConcurrentHashMap 패턴을 유지한다. Redis는 세션/연결 관리에만 사용.

### 금지: CommandRegistry에 gin7/삼국지 커맨드 혼재
마이그레이션 과도기에도 두 레지스트리를 동일 파일에 공존시키지 않는다. Gin7CommandRegistry를 별도 파일로 분리하고 Spring @Primary로 교체한다.

### 금지: 경제 로직을 커맨드 핸들러에서 직접 처리
세금/생산/유지비는 TickEngine의 runMonthlyPipeline에서만 처리한다. 커맨드는 즉시 효과(물자배분, 보충 등)만 처리하고 주기적 경제 처리는 틱에 위임한다.

### 금지: ShipUnit 전투 스탯을 Officer 엔티티에 직접 저장
함선 스탯(장갑/실드/무기)은 ShipUnit + ShipStatRegistry에서 관리한다. Officer 엔티티는 8스탯(개인 능력치)만 보유한다.

---

## 신규 엔티티 요약

| 엔티티 | 테이블 | 용도 | 기존 관계 |
|--------|--------|------|----------|
| `ShipUnit` | `ship_unit` | 부대 단위 함선 (300척) | Fleet FK, Officer FK |
| `FlagshipRecord` | `flagship_record` | 기함 소유/이력 | Officer FK |
| `Gin7EconomyRecord` | `economy_record` | 월간 경제 처리 이력 | Faction FK, SessionState FK |
| `GroundBattle` | `ground_battle` (선택) | 지상전 결과 영속화 | TacticalBattle FK |

기존 수정 엔티티: `Fleet` (meta 확장), `Planet` (삼국지 전용 컬럼 V45에서 제거), `Officer` (삼국지 전용 필드 제거).

---

## 출처

- 코드 직접 분석: `CommandRegistry.kt`, `CommandExecutor.kt`, `TickEngine.kt`, `TacticalBattleEngine.kt`, `TacticalBattleService.kt`, `EconomyService.kt`, `Fleet.kt`, `ShipyardProductionService.kt`
- 도메인 명세: `docs/REWRITE_PROMPT.md`, `.planning/PROJECT.md`, `CLAUDE.md`
- 데이터: `backend/shared/src/main/resources/data/commands.json`
