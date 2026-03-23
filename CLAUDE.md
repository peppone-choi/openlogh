# Open LOGH (오픈 은하영웅전설)

Web-based Legend of the Galactic Heroes multiplayer strategy game.
Forked from OpenSamguk, re-themed to the LOGH universe.

## Tech Stack

- Backend: Spring Boot 3 (Kotlin)
- Frontend: Next.js 15
- Database: PostgreSQL 16
- Cache: Redis 7

## Project Structure

- `backend/` - Spring Boot backend (gateway-app + game-app)
- `frontend/` - Next.js frontend
- `docs/` - Game design docs and reference materials
- `docs/reference/` - Original gin7 manual and Korean reference

## Commands

### Backend

```bash
cd backend && ./gradlew :gateway-app:bootRun
cd backend && ./gradlew :game-app:bootRun
```

### Frontend

```bash
cd frontend && pnpm dev
```

### Docker (DB services)

```bash
docker-compose up -d
```

## Domain Mapping (삼국지 → 은하영웅전설)

This project transforms a Three Kingdoms game into a LOGH space opera game.

### Core Entity Mapping

| OpenSamguk (삼국지) | OpenLOGH (은하영웅전설) | DB Table        | Notes                         |
| ------------------- | ----------------------- | --------------- | ----------------------------- |
| General (장수)      | Officer (제독/장교)     | `officer`       | Character the player controls |
| City (도시/성)      | Planet (행성)           | `planet`        | Territory unit                |
| Nation (국가)       | Faction (진영)          | `faction`       | Empire/Alliance/Fezzan        |
| Troop (부대)        | Fleet (함대)            | `fleet`         | Military unit                 |
| WorldState          | SessionState            | `session_state` | Game session                  |
| Emperor (황제)      | Sovereign (원수/의장)   | `sovereign`     | Faction leader                |

### Stat Mapping

| OpenSamguk 5-stat | OpenLOGH 8-stat       | Field Name       | Description                |
| ----------------- | --------------------- | ---------------- | -------------------------- |
| leadership (통솔) | leadership (통솔)     | `leadership`     | 인재 활용, 함대 최대 사기  |
| strength (무력)   | command (지휘)        | `command`        | 부대 지휘 능력             |
| intel (지력)      | intelligence (정보)   | `intelligence`   | 정보 수집/분석, 첩보, 색적 |
| politics (정치)   | politics (정치)       | `politics`       | 시민 지지 획득             |
| charm (매력)      | administration (운영) | `administration` | 행성 통치, 사무 관리       |
| - (new)           | mobility (기동)       | `mobility`       | 함대 이동/기동 지휘        |
| - (new)           | attack (공격)         | `attack`         | 공격 지휘 능력             |
| - (new)           | defense (방어)        | `defense`        | 방어 지휘 능력             |

### Resource Mapping

| OpenSamguk      | OpenLOGH         | Field        | Description           |
| --------------- | ---------------- | ------------ | --------------------- |
| gold (금)       | funds (자금)     | `funds`      | 국가/개인 자금        |
| rice (식량)     | supplies (물자)  | `supplies`   | 군수 물자             |
| crew (병력)     | ships (함선)     | `ships`      | 함선 수               |
| crewType (병종) | shipClass (함종) | `ship_class` | 전함/순양함/구축함 등 |
| train (훈련)    | training (훈련)  | `training`   | 부대 훈련도           |
| atmos (사기)    | morale (사기)    | `morale`     | 부대 사기             |

### City → Planet Field Mapping

| OpenSamguk     | OpenLOGH                   | Field             | Description        |
| -------------- | -------------------------- | ----------------- | ------------------ |
| pop (인구)     | population (인구)          | `population`      | 행성 인구          |
| agri (농업)    | production (생산)          | `production`      | 생산력 (함선/물자) |
| comm (상업)    | commerce (교역)            | `commerce`        | 교역/경제          |
| secu (치안)    | security (치안)            | `security`        | 행성 치안          |
| trust (민심)   | approval (지지도)          | `approval`        | 주민 지지도        |
| def (수비)     | orbital_defense (궤도방어) | `orbital_defense` | 궤도 방어력        |
| wall (성벽)    | fortress (요새)            | `fortress`        | 요새 방어력        |
| trade (교역로) | trade_route (항로)         | `trade_route`     | 교역 항로          |

### Nation → Faction Field Mapping

| OpenSamguk       | OpenLOGH                   | Field               |
| ---------------- | -------------------------- | ------------------- |
| gold             | funds                      | `funds`             |
| rice             | supplies                   | `supplies`          |
| bill (세율)      | tax_rate (세율)            | `tax_rate`          |
| rate (징병률)    | conscription_rate (징병률) | `conscription_rate` |
| tech (기술)      | tech_level (기술력)        | `tech_level`        |
| power (국력)     | military_power (군사력)    | `military_power`    |
| level (국가레벨) | faction_rank (진영 등급)   | `faction_rank`      |
| typeCode         | faction_type (진영 타입)   | `faction_type`      |

### Item Mapping

| OpenSamguk    | OpenLOGH                     | Field            |
| ------------- | ---------------------------- | ---------------- |
| weapon (무기) | flagship (기함)              | `flagship_code`  |
| book (서적)   | special_equipment (특수장비) | `equip_code`     |
| horse (말)    | engine (기관)                | `engine_code`    |
| item (아이템) | accessory (부속품)           | `accessory_code` |

### Faction Types (진영)

| Code       | Name (한국어) | Name (English)        | Description           |
| ---------- | ------------- | --------------------- | --------------------- |
| `empire`   | 은하제국      | Galactic Empire       | 전제군주제, 귀족 체계 |
| `alliance` | 자유행성동맹  | Free Planets Alliance | 민주공화제            |
| `fezzan`   | 페잔 자치령   | Fezzan Dominion       | 중립 교역 국가        |
| `rebel`    | 반란군        | Rebel Forces          | 쿠데타/반란 세력      |

### Ship Classes (함종)

| Code         | Name     | Ships/Unit | Description                         |
| ------------ | -------- | ---------- | ----------------------------------- |
| `battleship` | 전함     | 300        | 주력 전투함                         |
| `cruiser`    | 순양함   | 300        | 범용 전투함                         |
| `destroyer`  | 구축함   | 300        | 고속 전투함                         |
| `carrier`    | 항공모함 | 300        | 스파르타니안 운용                   |
| `transport`  | 수송함   | 300        | 물자/병력 수송                      |
| `hospital`   | 병원선   | 300        | 부상자 치료                         |
| `fortress`   | 요새     | 1          | 이동 요새 (이제르론/가이에스부르크) |

### Rank System (계급)

#### Empire (제국군)

| Level | Rank                 | Korean   |
| ----- | -------------------- | -------- |
| 10    | Reichsmarschall      | 원수     |
| 9     | Fleet Admiral        | 상급대장 |
| 8     | Admiral              | 대장     |
| 7     | Vice Admiral         | 중장     |
| 6     | Rear Admiral         | 소장     |
| 5     | Commodore            | 준장     |
| 4     | Captain              | 대령     |
| 3     | Commander            | 중령     |
| 2     | Lieutenant Commander | 소령     |
| 1     | Lieutenant           | 대위     |
| 0     | Sub-Lieutenant       | 소위     |

#### Alliance (동맹군)

Same structure, different titles where applicable.

### Organization (조직 구조)

Based on gin7 manual:

- 함대: 최대 60유닛(18,000척), 사령관+부사령관+참모장+참모6+부관 = 10명
- 순찰대: 3유닛(900척), 사령관+부사령관+부관 = 3명
- 수송함대: 수송함20유닛+전투함3유닛, 사령관+부사령관+부관 = 3명
- 지상부대: 양륙함3유닛+육전대3유닛, 사령관 1명
- 행성수비대: 육전대10유닛, 지휘관 1명

### Command Points (커맨드 포인트)

Two types:

- PCP (Political Command Points) - 정략 커맨드 포인트
- MCP (Military Command Points) - 군사 커맨드 포인트
- Recovery: every 5 real-time minutes
- Cross-use: can substitute at 2x cost

### Combat System

**Real-time fleet battles (RTS)**:

- Strategic game: turn-based territory management
- Tactical game: real-time space fleet combat
- Energy allocation: BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR
- Formations: 紡錘(wedge), 艦種(by-class), 混成(mixed), 三列(three-column)

### Victory Conditions

- Capture enemy capital star system
- Enemy controls 3 or fewer star systems (including capital)
- Time limit reached → population comparison

### Package Structure

- Backend: `com.openlogh` (renamed from `com.opensam`)
- Gateway: `com.openlogh.gateway`
- Game: `com.openlogh` (game-app root)

## Architecture Decisions

- **Multi-Process**: Split into `gateway-app` + versioned `game-app` JVMs
- **Session = World**: `SessionState` entity for per-game-session state
- **Logical Isolation**: Game entities use `session_id` FK
- **Turn Engine**: Runs inside `game-app` for strategic (turn-based) processing
- **Combat Engine**: Real-time fleet battle system (WebSocket-based)
- **Two game modes**: Strategic (turn-based management) + Tactical (real-time combat)
