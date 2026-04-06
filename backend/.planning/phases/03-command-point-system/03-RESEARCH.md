# Phase 3: Command Point System — Research

## Current State Analysis

### Officer Entity (Officer.kt)
- Single `commandPoints: Int = 10` field (line 236)
- Single `commandEndTime: OffsetDateTime?` for command scheduling
- No PCP/MCP split — all commands draw from one pool
- 8-stat system already exists with PCP/MCP grouping in `OfficerStat` enum

### OfficerStat Model (OfficerStat.kt)
- `StatCategory.PCP`: LEADERSHIP, POLITICS, ADMINISTRATION, INTELLIGENCE
- `StatCategory.MCP`: COMMAND, MOBILITY, ATTACK, DEFENSE
- Already provides `pcpStats()` and `mcpStats()` companion methods

### Command System (BaseCommand.kt)
- `getCommandPointCost(): Int = 1` — returns a single cost, no pool distinction
- Commands extend `OfficerCommand` (personal) or `FactionCommand` (nation-level)
- No concept of which pool (PCP/MCP) a command should draw from

### RealtimeService (RealtimeService.kt)
- `scheduleCommand()` line 299-300: checks `general.commandPoints < commandPointCost` — single pool
- `regenerateCommandPoints()` line 214-223: adds flat `world.commandPointRegenRate` to all officers, capped at 100
- No stat-based recovery calculation

### TickEngine (TickEngine.kt)
- Calls `realtimeService.regenerateCommandPoints(world)` every 300 ticks (5 real minutes)
- Already gated correctly at `CP_REGEN_INTERVAL_TICKS = 300`

### CommandService (CommandService.kt)
- `getCommandTable()` returns `CommandTableEntry` with single `commandPointCost`
- Command categories exist (개인, 내정, 군사, 인사, 계략, 국가) but not mapped to PCP/MCP

### CommandTableEntry DTO
- Single `commandPointCost: Int = 1` field
- No pool type indicator

### SessionState Entity
- `commandPointRegenRate: Int = 1` — flat rate, not stat-based

### Frontend (types/index.ts)
- `commandPoints: number` — single field on Officer type
- `command-panel.tsx` displays `CP {realtimeStatus.commandPoints}`

### Database (Flyway)
- Latest migration: V30 (game time fields)
- Next available: V31

## Design Decisions

### 1. Pool Classification for Commands

Commands must declare which pool they cost. Mapping strategy:

| Category (current) | Pool | Rationale |
|---|---|---|
| 개인 (Personal) | PCP | Self-improvement is political action |
| 내정 (Domestic) | PCP | Administration/governance |
| 군사 (Military) | MCP | Combat/military operations |
| 인사 (Personnel) | PCP | Appointments and personnel |
| 계략 (Schemes) | MCP | Intelligence/covert operations |
| 국가 (Nation-level) | PCP | Governance decisions |
| FactionCommand (all) | PCP | National-level political commands |

This mapping will live in BaseCommand as an overridable `getCommandPoolType(): StatCategory` defaulting to PCP.

### 2. Cross-Use at 2x Cost

When primary pool insufficient but the other pool has enough at 2x cost:
- Deduct from alternate pool at 2x
- Example: MCP command costs 3, officer has MCP=1, PCP=10 -> deduct 6 PCP (3 * 2)
- If neither pool alone suffices, fail with error message

### 3. Recovery Formula

Per 5-minute regen cycle:
- PCP recovery = floor((politics + administration) / 20) + 1, capped at pcpMax
- MCP recovery = floor((command + mobility) / 20) + 1, capped at mcpMax
- Base recovery of 1 ensures even low-stat officers recover something

### 4. Pool Size by Rank

| Rank Level | PCP Max | MCP Max | Total |
|---|---|---|---|
| 0 (Sub-Lt) | 5 | 5 | 10 |
| 1 (Lieutenant) | 6 | 6 | 12 |
| 2 (Lt Cmdr) | 7 | 7 | 14 |
| 3 (Commander) | 8 | 8 | 16 |
| 4 (Captain) | 10 | 10 | 20 |
| 5 (Commodore) | 12 | 12 | 24 |
| 6 (Rear Adm) | 15 | 15 | 30 |
| 7 (Vice Adm) | 18 | 18 | 36 |
| 8 (Admiral) | 22 | 22 | 44 |
| 9 (Fleet Adm) | 27 | 27 | 54 |
| 10 (Reichsm.) | 35 | 35 | 70 |

Formula: `basePool(rank) = [5,6,7,8,10,12,15,18,22,27,35]`

### 5. Experience Growth from CP Usage

When PCP is spent: distribute exp across leadership, politics, administration, intelligence (weighted by cost).
When MCP is spent: distribute exp across command, mobility, attack, defense.
Cross-use still grants exp to the command's native pool stats (not the pool actually deducted).

### 6. Database Changes (V31)

Add columns to `officer` table:
- `pcp INT NOT NULL DEFAULT 5` — current PCP
- `mcp INT NOT NULL DEFAULT 5` — current MCP
- `pcp_max INT NOT NULL DEFAULT 5` — max PCP (derived from rank)
- `mcp_max INT NOT NULL DEFAULT 5` — max MCP (derived from rank)

Migrate existing `command_points` data: split evenly into pcp/mcp.
Keep `command_points` column temporarily for backward compat (deprecated).

## Files to Modify

### Backend
- `game-app/src/main/resources/db/migration/V31__add_pcp_mcp_columns.sql` (new)
- `game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` (add pcp/mcp/pcpMax/mcpMax fields)
- `game-app/src/main/kotlin/com/openlogh/command/BaseCommand.kt` (add getCommandPoolType)
- `game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt` (dual pool deduction + regen)
- `game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt` (no change needed, calls regen)
- `game-app/src/main/kotlin/com/openlogh/dto/CommandDtos.kt` (add pool type to CommandTableEntry)
- `game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` (pass pool info to table)
- `game-app/src/main/kotlin/com/openlogh/service/CpService.kt` (new — CP resolver logic)

### Frontend
- `frontend/src/types/index.ts` (add pcp/mcp/pcpMax/mcpMax)
- `frontend/src/components/game/command-panel.tsx` (display dual pools)

## Risk Assessment

- **Low risk**: Migration is additive (new columns with defaults)
- **Medium risk**: RealtimeService.scheduleCommand has tight coupling to single `commandPoints` — must update atomically
- **Low risk**: BaseCommand.getCommandPointCost already returns int, adding pool type is additive
- **No risk**: TickEngine delegates to RealtimeService, no direct CP logic
