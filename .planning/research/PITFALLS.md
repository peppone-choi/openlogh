# Pitfalls Research

**Domain:** gin7 게임 로직 전면 재작성 — Web MMO (Spring Boot 3 + Next.js 15)
**Researched:** 2026-04-06
**Confidence:** HIGH — based on direct codebase inspection of CommandRegistry.kt, CommandExecutor.kt, TacticalBattleEngine.kt, BattleEngine.kt, EconomyService.kt, TickEngine.kt, Fleet.kt, Planet.kt, and all command general/*.kt files

---

## Critical Pitfalls

### Pitfall 1: CommandRegistry 교체 시 기존 테스트 전체 파괴

**What goes wrong:**
CommandRegistry.kt에 93개 삼국지 커맨드(농지개간, 상업투자, 징병 등)가 하드코딩되어 있고, `backend/game-app/src/test/` 아래에 CommandParityTest, CommandRegistryTest, GeneralCivilCommandTest, GeneralMilitaryCommandTest, NationCommandTest 등 수십 개 테스트가 이 커맨드 코드에 직접 의존한다. 삼국지 커맨드를 단번에 제거하면 빌드 자체가 깨진다.

**Why it happens:**
테스트가 커맨드 이름(한국어 문자열 키)을 직접 참조하거나, CommandRegistry가 특정 커맨드를 등록하는지 assert한다. gin7 커맨드를 새로 등록하고 삼국지 커맨드를 삭제하면 컴파일 에러가 아닌 런타임 assert 실패로 나타나 추적이 어렵다.

**How to avoid:**
"삭제 전에 등록" 원칙: gin7 81종을 먼저 CommandRegistry에 추가(stub으로라도)한 뒤, 삼국지 커맨드를 단계적으로 제거한다. 각 삭제 단계마다 테스트를 통과시킨 후 다음 단계로 진행. CommandParityTest의 기댓값(expected command set)을 gin7 커맨드 목록으로 먼저 업데이트하고 RED 상태를 확인한 뒤 구현으로 GREEN화하는 TDD 순서를 따른다.

**Warning signs:**
- `CommandRegistry` init 블록이 120줄 이상인 상태로 gin7 커맨드가 추가되면 파일이 200줄을 초과한다 — 이 시점에 분리 리팩터링 필요
- `CommandParityTest.kt`가 실패하기 시작하면 교체 작업이 절반쯤 진행된 것

**Phase to address:**
Phase 1 (삼국지 로직 제거 + gin7 커맨드 스텁 등록) — 커맨드 교체는 반드시 이 단계에서 완료해야 이후 모든 구현이 올바른 기반 위에 놓인다.

---

### Pitfall 2: officerLevel >= 5 레거시 폴백이 gin7 권한 시스템을 무력화

**What goes wrong:**
CommandExecutor.kt line 186에 `// Legacy fallback: officerLevel >= 5 still grants access during migration`라는 주석과 함께 FactionCommand 권한 검사를 우회하는 코드가 있다. CommandService.kt line 160에도 같은 패턴이 있다. gin7 직무권한카드 시스템을 구현해도 이 폴백이 살아있으면 계급이 5 이상인 모든 플레이어가 카드 없이 모든 국가 커맨드를 실행할 수 있다. 이는 진영 내 조직 시뮬레이션의 핵심 가치를 파괴한다.

**Why it happens:**
포크 당시 OpenSamguk의 officerLevel 기반 권한 체계가 PositionCard 시스템 도입 전에 남아 있었다. 마이그레이션 기간 임시방편으로 추가된 폴백인데, gin7 카드 시스템이 완성된 후에도 제거하지 않으면 영구 취약점이 된다.

**How to avoid:**
gin7 81종 커맨드의 requiredCards 매핑이 commands.json에 완전히 정의된 시점에 officerLevel >= 5 폴백을 제거하는 별도 태스크를 계획한다. PositionCardRegistry.canExecute()가 모든 gin7 커맨드를 커버한다는 통합 테스트를 작성한 후 폴백을 삭제한다.

**Warning signs:**
- `grep -r "officerLevel >= 5"` 결과가 CommandExecutor, CommandService, BattleService, WarAftermath, FrontInfoService, InMemoryTurnProcessor 등 7곳 이상 — 제거 체크리스트 필수
- 권한 없는 계급의 플레이어가 고위 커맨드를 실행 성공하는 버그 리포트

**Phase to address:**
Phase 3 (gin7 커맨드 81종 구현) 완료 직후, Phase 4 시작 전.

---

### Pitfall 3: TickEngine의 runMonthlyPipeline이 실제 경제 처리를 호출하지 않음

**What goes wrong:**
TickEngine.kt의 `runMonthlyPipeline()`은 현재 `gameEventService.broadcastTurnAdvance()` 호출만 하고 실제 EconomyService.processMonthly()를 호출하지 않는다(line 126-136에 TODO 주석 있음). gin7 경제 시스템(세수, 조병창 자동생산, 유지비, 차관 이자)을 구현해도 이 연결이 없으면 월간 경제 처리가 영원히 실행되지 않는다.

**Why it happens:**
"TODO: Wire to actual TurnPipeline steps when turn-based command execution is fully decoupled from monthly processing"라는 주석이 있다. TurnPipeline에 EconomyPreUpdateStep, EconomyPostUpdateStep이 존재하지만 TickEngine에서 TurnPipeline을 호출하는 연결이 빠져 있다.

**How to avoid:**
gin7 경제 시스템 구현(Phase 5) 시작 직전에 TickEngine → TurnPipeline → EconomyService 연결을 먼저 완성한다. 단위 테스트로 `processTick()` 호출 후 월 경계에서 EconomyService.processMonthly()가 호출되는지 검증하는 테스트를 추가한다.

**Warning signs:**
- 세수가 0으로 유지되는 현상
- 조병창 자동생산이 발동되지 않음
- 차관 이자가 쌓이지 않음

**Phase to address:**
Phase 5 (경제 시스템) 첫 번째 태스크로 반드시 연결 완성.

---

### Pitfall 4: TacticalBattleEngine에 미사일 시스템과 물자 소모가 없음

**What goes wrong:**
현재 TacticalBattleEngine.kt는 BEAM과 GUN만 구현되어 있고 미사일이 없다. gin7에서 미사일은 거리 무관 최강 무기이며 물자를 소모하는 핵심 자원 제약이다. 미사일 없이 행성 점령(정밀폭격/무차별폭격)을 구현하면 물자 소모 없이 무한 폭격이 가능해져 경제-전투 연동이 파괴된다.

**Why it happens:**
TacticalBattleEngine의 processCombat()은 energy.beam > 0과 energy.gun > 0만 체크하고 missile 처리 분기가 없다. FleetWarehouse 엔티티에 supplies 필드가 있지만 전투 엔진이 이를 참조하지 않는다.

**How to avoid:**
전투 엔진에 세 번째 무기 채널을 추가할 때 TacticalUnit에 `suppliesRemaining: Int`를 추가하고 미사일 발사마다 차감하는 로직을 구현한다. `suppliesRemaining == 0`이면 미사일 발사 불가 처리. FleetWarehouse 조회를 전투 초기화 시점에 수행하여 TacticalUnit에 주입한다.

**Warning signs:**
- TacticalBattleState에 suppliesRemaining 없이 미사일 커맨드가 구현되는 경우
- 행성 점령 커맨드가 물자 차감 없이 성공하는 경우

**Phase to address:**
Phase 4 (전술전 엔진) — 미사일 시스템은 행성 점령 커맨드 구현보다 반드시 먼저.

---

### Pitfall 5: BattleEngine.kt (삼국지 수치비교 전투)와 TacticalBattleEngine.kt가 동시에 존재

**What goes wrong:**
`engine/war/BattleEngine.kt`는 삼국지 CrewType 기반 phase 전투 시스템(ARM_PER_PHASE = 500.0, `while (currentPhase < maxPhase)` 루프)이고, `engine/tactical/TacticalBattleEngine.kt`는 gin7 에너지 기반 시스템이다. 두 시스템이 공존하는 동안 `BattleService.kt`와 `FieldBattleService.kt`가 어느 엔진을 호출하는지 모호해지며, 새 커맨드가 잘못된 엔진을 호출할 수 있다.

**Why it happens:**
단계적 마이그레이션 중 구 시스템 제거를 미루면 두 시스템이 공존하게 된다. BattleTrigger.kt, FieldBattleTrigger.kt, BattleTriggerService.kt가 어느 엔진과 연결되는지 명확한 분리가 없는 상태.

**How to avoid:**
Phase 4 시작 시 `engine/war/` 디렉터리의 삼국지 전투 파일 목록을 명시하고 "삭제 예정" 표시를 추가한다. TacticalBattleEngine이 모든 전투 케이스를 처리할 준비가 되면 BattleEngine, FieldBattleService를 한 번에 삭제한다. 중간 기간에는 BattleService가 TacticalBattleEngine을 호출하도록 라우팅 계층을 추가한다.

**Warning signs:**
- BattleService.kt에서 여전히 `CrewType.fromCode()`를 호출하는 코드
- `WarUnitOfficer.shipClass`가 gin7 ShipSubtype이 아닌 삼국지 병종 코드를 받는 경우

**Phase to address:**
Phase 1 (삼국지 로직 제거) — 전투 엔진 교체 계획을 Phase 1 시작 직전에 문서화.

---

### Pitfall 6: WebSocket 전술전 상태 동기화 - 클라이언트 상태 폭발

**What goes wrong:**
TacticalBattleEngine.processTick()이 매 틱마다 BattleTickEvent 목록을 생성하고 이를 STOMP `/topic/world/{sessionId}/battle`로 브로드캐스트하는 구조다. 한 세션에서 동시 전술전이 여러 성계에서 발생하면(MMO 특성상 자연스러운 상황) 브로드캐스트 채널이 모든 클라이언트에게 모든 성계 전투 이벤트를 전송한다. 전방 성계를 보고 있지 않은 2000명의 클라이언트에게 불필요한 패킷이 전달된다.

**Why it happens:**
현재 TacticalBattleState가 `battleId`와 `starSystemId`를 가지지만 브로드캐스트 채널이 세션 단위로 단일화되어 있다. 클라이언트 측 필터링이 없으면 파싱 비용만 발생한다.

**How to avoid:**
전술전 이벤트 채널을 `/topic/world/{sessionId}/battle/{battleId}`로 성계별 분리한다. 클라이언트는 현재 관전 중인 battleId를 subscribe/unsubscribe하도록 한다. BattleTickEvent에는 반드시 `battleId`를 포함해야 한다.

**Warning signs:**
- 하나의 성계에서 전투가 시작될 때 다른 성계 플레이어의 네트워크 트래픽이 급증
- 클라이언트 콘솔에서 unhandled battleId 이벤트 수신 로그

**Phase to address:**
Phase 4 (전술전 엔진) — 전술전 WebSocket 설계 시점에 반드시 채널 분리 설계.

---

### Pitfall 7: 88 서브타입 전투 스탯이 런타임에 로드되지 않고 고정값으로 사용됨

**What goes wrong:**
TacticalBattleEngine.kt의 BEAM_BASE_DAMAGE = 30.0, GUN_BASE_DAMAGE = 40.0이 하드코딩된 상수다. ship_stats_empire.json과 ship_stats_alliance.json에 88개 서브타입별로 beam.damage, gun.damage, armor, shield 값이 상세히 정의되어 있지만, TacticalUnit 초기화 시점에 이 JSON을 읽어 unit별 스탯을 주입하는 로직이 없다. 전 서브타입이 동일한 전투력을 가진 상태로 운용된다.

**Why it happens:**
TacticalBattleEngine은 순수 계산 엔진(도메인 서비스)으로 외부 데이터 로드 없이 동작하도록 설계되었으나, 초기화 레이어(TacticalBattleService)에서 JSON 스탯을 TacticalUnit에 매핑하는 코드가 아직 없다.

**How to avoid:**
TacticalBattleService에서 전투 초기화 시 `ShipUnitStats`(shared 모듈의 모델)를 조회하여 TacticalUnit의 `beamDamage`, `gunDamage`, `armorFront`, `armorSide`, `shieldProtection` 등을 채워주는 매퍼를 작성한다. TacticalBattleEngine은 이 값을 외부에서 주입받는 방식으로 상수를 제거한다.

**Warning signs:**
- TacticalUnit 생성 코드에서 `beamDamage = 30` 같은 리터럴 대입
- 동맹 타격순항함이 제국 전함과 동일한 화력으로 전투하는 결과

**Phase to address:**
Phase 2 (함종 시스템) — ShipUnit 엔티티 완성 시 TacticalBattleService 매퍼도 함께 작성.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| 삼국지 커맨드를 일부만 남기고 gin7 커맨드 추가 | 빌드 유지 | 두 세계관 커맨드 공존, 테스트 혼재, 권한 시스템 이중화 | never — 완전 교체 필수 |
| officerLevel >= 5 폴백 유지 | 기존 플레이어 접근 가능 | 직무권한카드 시스템 무력화 | 마이그레이션 기간 한정, gin7 카드 구현 완료 즉시 제거 |
| TacticalBattleEngine 상수값 사용 (88 스탯 미반영) | 빠른 전투 테스트 가능 | 서브타입 밸런스 불가능 | Phase 2 완료 전까지만 |
| 월간 파이프라인 미연결 상태로 경제 구현 | 경제 로직 단위 테스트 가능 | 실제 게임에서 경제 동작 안함 | never — TickEngine 연결은 경제 구현과 동시에 |
| EconomyService에 삼국지 국가레벨명(방랑군, 도위, 주자사...) 유지 | 코드 변경 최소화 | 게임 세계관 불일치, 플레이어 혼란 | Phase 5 이전까지만 허용 |
| `ship_stats_*.json`을 SharedModule에서만 읽고 DB에 캐싱 안함 | 구현 단순 | 매 전투 초기화마다 JSON 파싱 | 세션당 동시 전투 5개 이하일 때만 |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| STOMP + TacticalBattle | 세션 전체 채널(`/topic/world/{sessionId}/battle`)에 모든 전투 이벤트 브로드캐스트 | 배틀별 채널(`/battle/{battleId}`)로 분리, 클라이언트 subscribe 관리 |
| Spring `@Transactional` + TacticalBattleState | TacticalBattleState를 JPA 엔티티로 만들고 매 틱마다 DB 저장 | 인메모리 상태 유지 + 전투 종료 시에만 TacticalBattle 엔티티 저장 |
| TickDaemon + TacticalBattle 루프 | TickDaemon의 1초 틱에서 전술전 processTick()을 직접 호출 | 전술전은 별도 coroutine scope 또는 ConcurrentHashMap 기반 in-memory 루프로 분리 |
| Fleet.meta (JSONB) + ShipClass 스탯 | Fleet.meta에 서브타입 스탯을 직접 직렬화 저장 | Fleet은 `shipClass` + `subtype` 코드만 보유, 스탯은 ShipUnitStats JSON에서 런타임 조회 |
| Next.js STOMP + React Three Fiber | WebSocket 이벤트를 React state로 직접 적용하여 매 이벤트마다 re-render | Zustand store에 배틀 상태를 두고 tick event를 batching하여 16ms마다 한 번 렌더 |
| Flyway V45+ 마이그레이션 + 기존 FK | 삼국지 컬럼 DROP 시 FK 의존성 확인 없이 제거 | `pg_constraint` 조회로 FK 의존성 먼저 확인 후 순서대로 DROP |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| CommandExecutor.buildConstraintEnv()가 매 커맨드마다 전체 Officer/Planet 목록 조회 | 플레이어 수 증가 시 커맨드 응답 시간 선형 증가 | Redis에 세션별 constraintEnv 캐시(TTL 30초), 엔티티 변경 이벤트로 무효화 | 세션당 200명 이상 |
| TacticalBattleEngine.processTick()에서 매 틱마다 O(n²) 적-아군 거리 계산 | 30개 유닛 이상 전투에서 틱 처리 지연 | 공간 분할(그리드 버킷 또는 k-d tree), 탐지 범위(SENSOR) 기반 사전 컬링 | 유닛 수 20개 이상 |
| TickEngine이 매 틱마다 SessionStateRepository.save() 호출 | DB write가 1초 주기로 발생, IOPS 한계 도달 | dirty flag 패턴: 변경된 세션만 저장, 변경 없으면 skip | 동시 활성 세션 3개 이상 |
| NPC AI가 모든 NPC에 대해 매 틱 의사결정 실행 | 2000명 세션에서 NPC 수천 명의 AI가 1초마다 돌아감 | 슬롯 기반 스케줄링: 전체 NPC를 N개 그룹으로 나눠 틱마다 1그룹씩 처리 | NPC 500명 이상 |
| 전술전 종료 후 TacticalBattle 이벤트를 DB에 건별 INSERT | 수천 개 BattleTickEvent가 루프로 개별 저장 | 전투 종료 시 JSONB 컬럼에 전체 tick log를 단일 저장 | 전투 300틱 이상 |
| React Three Fiber + Konva 동시 마운트 (전술전 UI) | 전술전 페이지 로드 시 메모리 사용 500MB+ | 3D 전투 연출 뷰와 2D 전술맵을 동적 import + lazy loading | 저사양 클라이언트 전체 |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| 커맨드 실행 시 CP 차감을 클라이언트 요청값으로 결정 | CP 조작으로 비용 없이 커맨드 실행 | CP 비용을 commands.json 서버 측 데이터로만 결정, 클라이언트 값 무시 |
| 전술전 에너지 배분 합산을 클라이언트 측 검증만 | BEAM+GUN+SHIELD+ENGINE+WARP+SENSOR 합이 100 초과 | EnergyAllocation.validate()를 서버에서 강제 검증 후 거부 |
| 페잔 차관 금액에 상한 없음 | 단일 플레이어가 무한 차관으로 진영 자금 독점 | 진영당 최대 차관 한도(gin7 규칙 기반) 서버 강제 |
| TacticalBattle WebSocket 채널에 sessionId만 검증 | 다른 세션 전투 이벤트 수신/개입 | battleId + 해당 플레이어의 officerId 소속 검증 |
| NPC 조종 커맨드를 일반 플레이어가 실행 가능 | NPC 조종으로 게임 밸런스 파괴 | ALWAYS_ALLOWED_COMMANDS의 NPC능동, CR건국 등은 내부 서비스 호출 전용으로 HTTP endpoint 차단 |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| 직무권한카드 없이 커맨드 실행 시 에러 메시지만 표시 | 왜 실패했는지, 어떤 카드가 필요한지 모름 | 에러 메시지에 필요한 카드명과 취득 조건 표시 |
| 전술전 에너지 배분 패널에서 슬라이더 합이 100 초과 시 즉시 거부 | 하나 올리면 다른 것을 내려야 하는데 인터페이스가 강제 안함 | 슬라이더 변경 시 나머지 채널에서 자동으로 차감하는 linked slider 구현 |
| 은하맵에서 현재 내 함대 위치와 이동 가능 성계 표시 없음 | 플레이어가 워프항행 커맨드 사용 전 가능 경로 파악 불가 | 함대 선택 시 현재 성계에서 인접한 성계를 하이라이트 |
| 커맨드 대기시간/실행시간을 초 단위로만 표시 | gin7 24배속 기준 "게임 시각"으로 이해 필요 | "대기 8틱 (실시간 8초 / 게임 3분 12초)" 형식으로 병기 |
| 전술전 도중 전략 화면 커맨드 실행 가능 | 전술전 집중 불가, 게임 상태 불일치 | 전술전 참가 중 MCP 소모 커맨드 실행 차단 (게임 규칙) |
| 88 서브타입이 함대 편성 UI에 모두 나열 | 선택지 과부하, 초보자 진입 장벽 | 계급에 따라 접근 가능한 서브타입 필터링, 상위 서브타입은 잠금 표시 |

---

## "Looks Done But Isn't" Checklist

- [ ] **gin7 커맨드 구현:** commands.json에 정의된 81종을 보면 구현된 것처럼 보이지만 — CommandRegistry에 실제 핸들러가 등록되고 CP 차감 + 대기시간 + 실행시간 모두 처리되는지 확인
- [ ] **전술전 에너지 시스템:** EnergyAllocation enum이 있고 TacticalUnit에 energy 필드가 있지만 — beamMultiplier(), shieldAbsorption() 등의 계산이 JSON 서브타입 스탯과 연동되는지 확인
- [ ] **월간 경제 처리:** EconomyService.processMonthly()가 구현되어 있지만 — TickEngine에서 실제 호출되는지 확인 (현재 미연결 상태)
- [ ] **행성 점령:** TacticalBattleEngine에 fortress gun이 있지만 — 점령 커맨드(정밀폭격/육전대강하 등) 6종이 각각 물자 소모 + 지지도 변화 + 방위력 변화를 올바르게 처리하는지 확인
- [ ] **NPC AI 인물 교체:** PersonalityTrait 5종 구현 완료처럼 보이지만 — general_pool.json이 여전히 삼국지 인물을 포함하는지 확인
- [ ] **함대 편성 제한:** FormationCapService가 있지만 — "소장: 4개 부대, 중장 이상: 8개 부대" gin7 규칙이 RankTitle enum과 연동되는지, 인구-함대 편성 제한(10억당 함대 1)이 강제되는지 확인
- [ ] **페잔 차관 엔딩:** FezzanEndingService가 있지만 — 100틱마다 checkAndTrigger()가 실제 "페잔 엔딩" 조건(미상환 + 페잔 군사 점령)을 모두 체크하는지 확인
- [ ] **프론트엔드 삼국지 잔재 제거:** 컴포넌트명이 바뀌어도 — city-basic-card, general-basic-card, map-3d/city/CityModel.tsx 등이 코드베이스에 남아 있는지 확인

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| CommandRegistry 대규모 파괴 | HIGH | git 이전 커밋에서 커맨드 팩토리 복원 → gin7 커맨드를 별도 파일에 먼저 작성 → 교체 순서 재설계 |
| officerLevel 폴백 제거 후 기존 플레이어 접근 불가 | MEDIUM | PositionCard 일괄 부여 마이그레이션 스크립트 실행 (V45__ 이후 Flyway) |
| 月간 경제 파이프라인 더블 실행 (TickEngine + TurnPipeline 양쪽에서 호출) | HIGH | EconomyService에 멱등성 보장 플래그 추가, 실행 이력을 SessionState.meta에 기록하여 중복 실행 차단 |
| 전술전 인메모리 상태 소실 (서버 재시작) | MEDIUM | TacticalBattle 엔티티에 state JSONB 컬럼 추가, 전투 매 10틱마다 스냅샷 저장 |
| 88 서브타입 밸런스 문제 (출시 후) | LOW | ship_stats_*.json 수정 후 재배포 (코드 변경 없음) — 이를 위해 스탯을 반드시 JSON에서만 관리 |
| Flyway 마이그레이션 실패 (V45+ 중 FK 오류) | HIGH | 해당 migration 파일에 DROP CONSTRAINT → DROP COLUMN 순서 보장, 로컬 테스트 후 적용 |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| CommandRegistry 교체 시 테스트 파괴 | Phase 1 (커맨드 교체) | CommandRegistryTest가 gin7 81종 커맨드 모두 등록 확인 |
| officerLevel >= 5 폴백 잔존 | Phase 3 완료 직후 | `grep -r "officerLevel >= 5"` 결과 0건 |
| TickEngine monthly 미연결 | Phase 5 시작 시 | 월간 틱 경계에서 EconomyService.processMonthly() 호출 통합 테스트 통과 |
| 미사일/물자 소모 미구현 | Phase 4 (전술전 엔진) | 미사일 stock 0인 함대가 정밀폭격 실패하는 테스트 통과 |
| 구 BattleEngine 잔존 | Phase 1 | `engine/war/BattleEngine.kt` 파일 없음 확인 |
| STOMP 채널 미분리 | Phase 4 | 두 개 성계에서 동시 전투 시 각 클라이언트가 자기 battleId 이벤트만 수신하는 E2E 테스트 |
| 88 서브타입 스탯 미반영 | Phase 2 (함종 시스템) | Battleship-I vs Battleship-VIII 전투에서 VIII 명백히 우세한 결과 |
| WebSocket 대량 브로드캐스트 | Phase 4 | 2000 클라이언트 시뮬레이션에서 비관련 전투 이벤트 수신 0건 |
| React Three Fiber + Konva 동시 마운트 | Phase 7 (프론트엔드) | 전술전 페이지 Lighthouse 메모리 점수, 저사양 기기 테스트 |
| NPC 인물 데이터 삼국지 잔재 | Phase 6 (AI) | general_pool.json에서 삼국지 인물명 0건 grep 확인 |

---

## Sources

- 직접 코드 검사: `CommandRegistry.kt`, `CommandExecutor.kt` (officerLevel >= 5 폴백 확인)
- 직접 코드 검사: `TacticalBattleEngine.kt` (미사일 미구현, 상수 hardcode 확인)
- 직접 코드 검사: `TickEngine.kt` line 126-136 (runMonthlyPipeline TODO 확인)
- 직접 코드 검사: `BattleEngine.kt` (삼국지 CrewType 기반 phase 전투 잔존 확인)
- 직접 코드 검사: `EconomyService.kt` (삼국지 국가레벨명 잔존 확인)
- 직접 코드 검사: `Fleet.kt`, `Planet.kt` (엔티티 구조 확인)
- 직접 코드 검사: `ship_stats_empire.json` (88 서브타입 스탯 데이터 존재 확인)
- 직접 코드 검사: `commands.json` (gin7 81종 CP비용/대기시간 정의 확인)
- `.planning/PROJECT.md` — 현재 상태 및 재작성 대상 명시
- `docs/REWRITE_PROMPT.md` — gin7 게임 메카닉스 전체 사양
- `CLAUDE.md` — 도메인 매핑, 함종, 전술모드, 경제 상세

---
*Pitfalls research for: gin7 게임 로직 전면 재작성 (Spring Boot 3 + Next.js 15 기반 Web MMO)*
*Researched: 2026-04-06*
