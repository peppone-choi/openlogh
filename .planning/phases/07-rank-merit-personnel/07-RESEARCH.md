# Phase 7: Rank, Merit & Personnel - Research

## Current State Analysis

### Existing Infrastructure

**RankTitleResolver** (`model/RankTitle.kt`): Already resolves 11-tier (0-10) rank titles for Empire and Alliance factions. Tier 0 = Sub-Lieutenant (소위), Tier 10 = Reichsmarschall/Fleet Admiral (원수).

**CpPoolConfig** (`model/CpPoolConfig.kt`): Maps rank level to max CP pool size (5 at rank 0, 35 at rank 10). Both PCP and MCP share the same max.

**Officer Entity** (`entity/Officer.kt`): Has `officerLevel: Short` (the rank tier), `experience: Int`, `dedication: Int`, `dedLevel: Short`, `expLevel: Short`. No merit/evaluation/fame point fields exist yet.

**OfficerRankService** (`service/OfficerRankService.kt`): Legacy rank title resolver from JSON file. Needs to coexist or be replaced by RankTitleResolver.

**Faction Entity** (`entity/Faction.kt`): Has `factionType: String` field (e.g., "empire", "alliance"). Used to determine rank title resolution.

**OfficerRepository**: Has `findBySessionIdAndFactionId()` for querying officers per faction per session.

### Missing Components

1. **Merit/Evaluation/Fame point fields** on Officer entity (DB columns)
2. **RankLadderService** - rank ordering, auto-promotion, headcount enforcement
3. **PersonnelService** - manual promotion/demotion/appointment with authority checks
4. **PersonnelController** - REST API endpoints
5. **PersonnelDtos** - request/response DTOs
6. **Frontend** - rank display, personnel panel, merit progress UI

## gin7 Manual Key Rules (p.33-36)

### Merit Points (功績/공적)
- Primary metric for rank advancement
- Accumulated from combat outcomes, command execution, mission success
- Reset to 0 on promotion, reset to 100 on demotion
- Determines rank order within the same tier

### Rank Ladder Ordering
Officers within the same rank are ordered by:
1. Merit points (공적) - descending
2. Peerage/fame (작위/명성) - descending
3. Medals (훈장) - descending
4. Influence (세력) - descending
5. Total stats sum - descending

### Auto-Promotion System
- Applies to ranks below Captain (대좌, tier 4 and below)
- Every 30 game days, the top officer on the ladder within each tier gets promoted
- On promotion: merit reset to 0, all position cards revoked except PERSONAL/CAPTAIN/FIEF

### Headcount Limits by Rank
| Rank Tier | Title | Headcount Limit |
|-----------|-------|-----------------|
| 10 | 원수 (Reichsmarschall/Fleet Admiral) | 5 |
| 9 | 상급대장 (Fleet Admiral/Admiral of the Fleet) | 5 (Empire only) |
| 8 | 대장 (Admiral) | 10 |
| 7 | 중장 (Vice Admiral) | 20 |
| 6 | 소장 (Rear Admiral) | 40 |
| 5 | 준장 (Commodore) | 80 |
| 0-4 | 대좌 이하 (Captain and below) | Unlimited |

### Personnel Authority
Who can promote/demote/appoint:
- Tier 10 (원수): Only by Sovereign (Emperor/Supreme Council Chairman)
- Tier 5-9 (준장~상급대장): By Minister of Military Affairs (군무상서) / Defense Committee Chair (국방위원장)
- Tier 0-4 (대좌 이하): By Personnel Bureau Chief (인사국장)

### Demotion Rules
- On demotion: merit reset to 100, position cards revoked except PERSONAL/CAPTAIN/FIEF
- Can be triggered by disciplinary action from superiors with authority

## Design Decisions

1. **Merit points as `Int`** - sufficient range, simple arithmetic
2. **Evaluation and Fame as separate `Int` fields** - support future peerage/medal systems
3. **Headcount enforcement at service level** - not DB constraint, for flexibility
4. **Auto-promotion via tick engine hook** - called every 30 game days
5. **Position card reset on promotion/demotion** - integrate with existing PositionCard system
6. **Personnel authority via PositionCard check** - SOVEREIGN, MILITARY_MINISTER, PERSONNEL_CHIEF cards

## Implementation Plan

### Plan 1: Flyway Migration + Entity Update
- V36 migration: add merit, evaluation_points, fame_points columns to officer table
- Update Officer entity with new fields
- Add RankHeadcount config model

### Plan 2: RankLadderService + PersonnelService
- RankLadderService: rank ordering, auto-promotion logic, headcount check
- PersonnelService: manual promote/demote/appoint with authority validation
- PersonnelController + DTOs

### Plan 3: Frontend Rank Display + Personnel Panel
- TypeScript types for rank/personnel
- Personnel API client
- Rank display component, merit progress bar
- Personnel management panel (for authorized officers)
