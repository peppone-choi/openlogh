# Phase 4 Research: Position Card & Command Authority

**Research Date:** 2026-04-05

## gin7 Position Card System (직무권한카드)

### Core Concept

In gin7, ALL commands are executed through Position Cards (직무권한카드). A position card represents a job/role an officer holds, and each card contains a set of commands that the holder can execute. Every character has at minimum the "Personal" (개인) and "Captain" (함장) cards, which allow basic movement and personal commands.

### Key Rules
- Officers can hold multiple positions simultaneously (兼任), gaining multiple cards
- Maximum 16 position cards per character
- When promoted, all cards EXCEPT "Personal", "Captain", and "Knight/封士" are lost
- When demoted, same rule applies -- most cards are stripped
- When a planet is captured, the losing faction's planet-specific cards (Governor, Garrison Commander, etc.) are lost

### Command Groups (7 groups)

Commands on position cards are categorized into 7 groups:

1. **作戦 (Operations)** - Common to all characters. Mostly flagship unit operations. Contained in [Captain Card].
2. **個人 (Personal)** - Common to all characters. Movement within star systems, individual actions. Contained in [Personal Card].
3. **指揮 (Command)** - Military operation planning, fleet composition, general command. On military leadership cards.
4. **兵站 (Logistics)** - Supply allocation, unit reorganization, fleet maintenance. On fleet commander cards.
5. **人事 (Personnel)** - Promotion, demotion, appointment. On HR authority cards.
6. **政治 (Politics)** - Budget management, national goals, faction core governance. On central government cards.
7. **諜報 (Intelligence)** - Mass searches, arrests, espionage infiltration. On intelligence agency cards.

### Command Points
- All commands consume either PCP (Political) or MCP (Military) command points (already implemented in Phase 3)
- Commands with 0 cost can be executed unlimited times
- CP recovery: every 2 game hours (5 real-time minutes) -- already implemented
- Cross-use: if primary pool insufficient, can use other pool at 2x cost -- already implemented

### Command Execution Time
- **実行待機時間 (Execution Wait Time)**: Time from input to execution start
- **実行所要時間 (Execution Duration)**: Time from execution start to completion
- These are real-time durations (already have `getDuration()` on BaseCommand)

## Organization Structure

### Empire (帝国軍) Organization - ~77 positions total

**Imperial Court (皇宮):**
- Emperor (皇帝) - 1
- Supreme Commander (帝国軍最高司令官) - 1
- Chief of Staff (幕僚総監) - 1
- HQ Staff (大本営参謀) - 10
- Imperial Chancellor (帝国宰相) - 1

**Cabinet (内閣):** - Politicians
- State Secretary (国務尚書) - 1
- Interior Secretary (内務尚書) - 1
- Finance Secretary (財務尚書) - 1
- Court Secretary (宮内尚書) - 1
- Justice Secretary (司法尚書) - 1
- Ceremonies Secretary (典礼尚書) - 1
- Science Secretary (科学尚書) - 1
- Cabinet Secretary (内閣書記官長) - 1

**Fezzan Embassy (駐フェザーン弁務官事務所):** - Diplomats
- High Commissioner (駐フェザーン弁務官) - 1
- Deputy Commissioner (フェザーン駐在補佐官) - 1
- Military Attache (フェザーン駐在武官) - 1

**Military Affairs Ministry (軍務省):**
- Military Affairs Secretary (軍務尚書) - 1
- Vice Secretary (軍務省次官) - 1
- HR Bureau Chief (軍務省人事局長) - 1
- Investigation Bureau Chief (軍務省調査局長) - 1
- Ministry Staff (軍務省参事官) - 10

**High Command (統帥本部):**
- Chief of High Command (統帥本部総長) - 1
- Vice Chief (統帥本部次長) - 1
- Operations 1st Chief (統帥本部作戦一課長) - 1 (fleet ops planning)
- Operations 2nd Chief (統帥本部作戦二課長) - 1 (transport/patrol/ground ops)
- Operations 3rd Chief (統帥本部作戦三課長) - 1 (solo ship ops)
- High Command Inspectors (統帥本部監察官) - 10

**Space Fleet Command (宇宙艦隊司令部):**
- Space Fleet Commander (宇宙艦隊司令長官) - 1
- Vice Commander (宇宙艦隊副司令長官) - 1
- Chief of Staff (宇宙艦隊総参謀長) - 1
- Staff (宇宙艦隊参謀) - 10

**Military Police (憲兵本部):**
- Military Police Commander (憲兵総監) - 1
- Vice Commander (憲兵副総監) - 1

**Ground Forces (装甲擲弾兵総監部):**
- Ground Forces Commander (装甲擲弾兵総監) - 1
- Vice Commander (装甲擲弾兵副総監) - 1

**Science & Technology (科学技術総監部):**
- Director (科学技術総監) - 1

**Officer Academy (帝国軍士官学校):**
- Dean (士官学校長) - 1
- Instructors (士官学校教官) - 10

**Intelligence (総合偵察局):**
- Intelligence Agents (諜報官) - 50

**Fortress (要塞):**
- Fortress Commander (要塞司令官) - 1
- Garrison Commander (要塞守備隊指揮官) - 1
- Fortress Secretary (要塞事務総監) - 1

**Fleet (艦隊) - per fleet:**
- Fleet Commander (艦隊司令官) - 1
- Vice Commander (艦隊副司令官) - 1
- Chief of Staff (艦隊参謀長) - 1
- Staff Officers (艦隊参謀) - 6
- Adjutant (艦隊司令官副官) - 1

**Transport Fleet (輸送艦隊) - per fleet:**
- Transport Commander (輸送艦隊司令官) - 1
- Vice Commander (輸送艦隊副司令官) - 1
- Adjutant (輸送艦隊司令官副官) - 1

**Patrol (巡察隊) - per patrol:**
- Patrol Commander (巡察隊司令) - 1
- Vice Commander (巡察隊副司令) - 1
- Adjutant (巡察隊司令副官) - 1

**Ground Force (地上部隊) - per unit:**
- Ground Force Commander (地上部隊指揮官) - 1

**Planet Governance (各惑星):**
- Governor (惑星総督) - 1 per planet (politician or general)
- Garrison Commander (惑星守備隊指揮官) - 1 per planet

**Capital Planet (首都惑星):**
- Capital Defense Commander (帝都防衛司令官) - 1
- Imperial Guard Commander (近衛兵総監) - 1

### Alliance (同盟軍) Organization - similar structure

**Supreme Council (最高評議会):**
- Chairman (議長) - 1
- Vice Chairman (副議長) - 1
- State Committee Chair (国務委員長) - 1
- Defense Committee Chair (国防委員長) - 1
- Finance Committee Chair (財政委員長) - 1
- Law & Order Committee Chair (法秩序委員長) - 1
- Natural Resources Committee Chair (天然資源委員長) - 1
- Human Resources Committee Chair (人的資源委員長) - 1
- Economic Development Committee Chair (経済開発委員長) - 1
- Social Development Committee Chair (地域社会開発委員長) - 1
- Info & Transport Committee Chair (情報交通委員長) - 1
- Secretary (書記) - 1

**Defense Committee (国防委員会):**
- Investigation Dept Chief (査問部長) - 1
- Strategy Dept Chief (戦略部長) - 1
- HR Dept Chief (人事部長) - 1
- Defense Dept Chief (防衛部長) - 1
- Intelligence Dept Chief (情報部長) - 1
- Communications Dept Chief (通信部長) - 1
- Equipment Dept Chief (装備部長) - 1
- Facilities Dept Chief (施設部長) - 1
- Accounting Dept Chief (経理部長) - 1
- Education Dept Chief (教育部長) - 1
- Health Dept Chief (衛生部長) - 1

**Joint Operations HQ (統合作戦本部):**
- Chief (統合作戦本部長) - 1
- 1st Vice Chief (第一次長) - 1
- 2nd Vice Chief (第二次長) - 1
- 3rd Vice Chief (第三次長) - 1
- Staff (統合作戦本部参事官) - 10
- Ground War Supervisor (陸戦総監部長) - 1

**Logistics HQ (後方勤務本部):**
- Chief (後方勤務本部長) - 1
- Vice Chief (後方勤務本部次長) - 1
- Staff (後方勤務本部参事官) - 10
- Science Tech Chief (科学技術本部長) - 1
- Military Police Commander (憲兵司令官) - 1

**Space Fleet Command, Fleets, Patrols, Transport, Ground, Fortress, Planet, Intelligence** - mirror Empire structure with Alliance titles.

## Suggestion/Proposal System (提案/命令)

### Core Concept
In gin7, lower-ranking officers who lack the position card for a command can submit a **suggestion/proposal (提案)** to a superior who holds the required card. The superior can then **approve** or **reject** the proposal.

### How It Works
1. Officer A wants to execute a command they don't have authority for
2. Officer A sends a proposal to Officer B (who holds the required position card)
3. Officer B receives the proposal notification
4. Officer B can approve (execute the command on behalf of A) or reject it
5. If approved, the command is executed using Officer B's authority (position card) but charged to Officer A's CP

### Personnel Command as Example
- HR authority holders: Emperor/Military Affairs Secretary/HR Bureau Chief (Empire), Defense Committee Chair/HR Dept Chief (Alliance)
- A personnel command (promotion/demotion) can be proposed by any officer, but executed only by those with HR authority
- The approver can also propose rank changes for themselves

## Current Codebase State

### What Exists
1. **Officer entity** has `officerLevel` (Short, 0-10 rank) and `permission` (String: "normal", "ambassador", "auditor")
2. **CommandService** gates faction commands at `officerLevel >= 5` (hardcoded)
3. **CommandRegistry** has 93 commands (55 officer + 38 faction) with Korean names
4. **BaseCommand** has `getCommandPoolType()`, `getCommandPointCost()`, `getDuration()`, `getPreReqTurn()`, `getPostReqTurn()`
5. **CpService** handles dual PCP/MCP deduction with cross-use at 2x cost
6. **CpPoolConfig** maps rank (0-10) to max CP pool size
7. **Cooldown system** exists (turn-indexed) but needs conversion to real-time timestamps
8. **PermissionService** handles ambassador/auditor permission assignment (basic role system)

### What's Missing (Phase 4 Scope)
1. **Position card data model** - No `position_card` table or enum
2. **Card-to-command mapping** - No mapping of which cards grant which commands
3. **Authority gating in CommandExecutor** - No card check before execution
4. **Real-time cooldown** - Cooldowns use turn indices, not wall-clock timestamps
5. **Suggestion/proposal entity** - No proposal table or workflow
6. **Command panel filtering by card** - Frontend shows all commands, not card-filtered

## Design Decisions for Implementation

### Position Card Storage
Store as a `position_card` column on Officer entity (JSONB array of card codes), rather than a separate table. Each card code is a string like `"PERSONAL"`, `"CAPTAIN"`, `"FLEET_COMMANDER"`, etc.

Rationale: Cards are a property of an officer, change infrequently, and the list is small (max 16). A JSONB array is simpler than a join table and avoids N+1 queries.

### Command Group Mapping
Create a static registry (`PositionCardRegistry`) that maps:
- Card code -> list of command action codes it grants
- Command action code -> which command group it belongs to
- Card code -> required minimum rank, max holders count, faction type

### 77 Cards Adaptation
The full gin7 organization has ~77 distinct positions. For Phase 4, we define all 77 card codes with their command group mappings, but only the core subset (Personal, Captain, fleet crew, planet governance, central government) will be actively testable. Full organizational hierarchy activation depends on Phase 5 (unit structure) and Phase 7 (rank/personnel).

### Cooldown Conversion
Convert from turn-index based (`year * 12 + month`) to `OffsetDateTime` based. The `Officer.meta["next_execute"]` map will store ISO timestamps instead of turn indices. This aligns with the real-time tick engine from Phase 2.

### Suggestion System
Create a `Proposal` entity (new table) with: requester_id, approver_id, action_code, args (JSONB), status (pending/approved/rejected), created_at, resolved_at. Use the existing message/notification system to notify the approver.

## Scope Boundaries

### In Scope (Phase 4)
- CMD-01: 77 position card definitions with command group mappings
- CMD-04: Real-time cooldowns (wall-clock OffsetDateTime)
- CMD-05: Suggestion/proposal system (submit, approve, reject)
- Authority gating: CommandExecutor checks officer's cards before allowing command
- Command panel: Filter shown commands by officer's current cards

### Out of Scope (Later Phases)
- Phase 5: Organization structure (fleet/patrol/ground crew slots)
- Phase 7: Rank-based promotion/demotion (auto card stripping)
- Phase 7: Appointment commands (assigning officers to positions)
- Phase 8: Scenario-specific initial card assignments

## Technical Approach

### Plan 1: Position Card Data Model + Static Registry
- Flyway migration V32: Add `position_cards` JSONB column to `officer` table
- `PositionCard` enum with all 77 card codes
- `CommandGroup` enum (OPERATIONS, PERSONAL, COMMAND, LOGISTICS, PERSONNEL, POLITICS, INTELLIGENCE)
- `PositionCardRegistry` static data: card -> commands, card -> min rank, card -> faction type
- Default cards: all officers get PERSONAL + CAPTAIN

### Plan 2: Authority Gating + Real-time Cooldowns
- Integrate card check into `CommandExecutor.executeOfficerCommand()` and `executeFactionCommand()`
- Convert cooldown system from turn-index to OffsetDateTime
- Update `CommandService.getCommandTable()` to filter by officer's cards
- Update `CommandService.getNationCommandTable()` similarly

### Plan 3: Suggestion/Proposal System
- Flyway migration V33: Create `proposal` table
- `ProposalService`: submit, approve, reject, list pending
- `ProposalController`: REST endpoints
- WebSocket notification to approver
- Frontend: proposal submission UI, pending proposals list

### Plan 4: Frontend Command Panel UI
- Command panel shows commands grouped by position card
- Cards displayed as tabs/sections
- Cooldown timers shown per command
- Suggestion button for commands the officer lacks authority for

## References

- gin7 manual pages 26-58 (Chapter 3: Strategic Game)
- Empire organization chart (p.28, p.55-57)
- Alliance organization chart (p.29, p.57-58)
- Command groups (p.27)
- Position cards overview (p.26)
- Suggestion system (inferred from personnel/appointment mechanics p.35-36)
- Existing code: CommandExecutor, CommandRegistry, CpService, PermissionService
