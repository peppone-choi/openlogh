---
phase: 02-character-rank-and-organization
verified: 2026-03-29T12:00:00Z
status: human_needed
score: 5/5 must-haves verified
human_verification:
  - test: "Visual verification of character selection page"
    expected: "Two tabs (original/generate), 8-stat allocator with budget=400, origin selector, character grid with stat bars"
    why_human: "UI layout, spacing, color accuracy, DungGeunMo font rendering, responsive behavior"
  - test: "Visual verification of officer profile page"
    expected: "4-section layout: basic info, 8 stat bars with colors, position card chips with tooltip, location/status"
    why_human: "Visual appearance of stat bars, tooltip behavior, section separator rendering"
  - test: "Visual verification of org chart page"
    expected: "Two faction tabs with gold/blue color differentiation, expandable tree to depth 3, vacant markers"
    why_human: "Collapsible tree interaction, faction color accuracy, scroll position preservation on tab switch"
---

# Phase 02: Character, Rank, and Organization Verification Report

**Phase Goal:** Every officer has a persistent identity with 8 stats, a rank on the 11-tier ladder, and authority stored in the relational PositionCard table rather than JSONB
**Verified:** 2026-03-29T12:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A player can select a canonical LOGH character (fixed stats) or generate a custom officer with distributed 8-stat points | VERIFIED | CharacterCreationService validates 400-point total (STAT_TOTAL=400, STAT_MIN=10, STAT_MAX=100). CharacterController exposes POST /generate, POST /select-original, GET /available-originals. ScenarioService handles both 5-stat legacy and 8-stat formats with mobility/attack/defense defaults. Frontend select-pool page has two tabs with StatAllocator, OriginSelector, CharacterPickGrid, all wired to backend API via characterApi. Zod schema validates client-side. |
| 2 | An officer has a visible rank on the 11-tier ladder, and rank-gated commands are blocked for officers below the required rank | VERIFIED | RankLadderService has getRankLadder (5-law sort: merit, peerage, medal, influence, stat total), RANK_LIMITS for ranks 4-10, canPromote with PositionCard-based authority. PersonnelCommands uses hasPersonnelAuthority checking position card codes (emperor/chairman/military_minister/personnel_chief). Zero `officerLevel < 20` legacy checks remain. RankController exposes GET /api/rank/ladder/{sessionId} and GET /api/rank/limits. |
| 3 | Promoting an officer via manual promotion resets merit points to 0, revokes current position cards, and the PositionCard table reflects the change (not officer.meta JSON) | VERIFIED | PersonnelCommands sets `dg.experience = 0` on promotion (RANK-05), `dg.experience = 100` on demotion (RANK-07). `positionCardService?.revokeOnRankChange` called in promotion, demotion, and special promotion commands. RankLadderService auto-promotion/demotion also calls revokeOnRankChange. Zero `meta["positionCards"]` references remain in command or modifier code. V39 migration backfills JSONB data to position_card table. |
| 4 | The organization chart for both Empire and Alliance is navigable, showing all 100+ positions and their current holders | VERIFIED | OrgChartController returns all holders via single findBySessionId query with in-memory officer join. PositionCardController returns per-officer and per-session cards. Frontend org-chart page has two faction tabs, fetches from /api/org-chart/{sessionId}, merges holder data with static EMPIRE_ORG and ALLIANCE_ORG trees. OrgTreeNodeLive renders expandable tree with faction colors (--empire-gold / --alliance-blue). Vacant positions marked with `[&#x25A1; 공석]`. |
| 5 | An officer's home planet is set so that if their flagship is destroyed they are automatically returned there | VERIFIED | Officer.kt has `homePlanetId: Long?` field. V40 migration adds home_planet_id column with JSONB backfill from meta.returnPlanetId. CharacterCreationService sets homePlanetId on generated officer creation. ScenarioService sets homePlanetId during officer spawning. CharacterLifecycleService.triggerDeath moves officer to `homePlanetId ?: factionCapitalPlanetId`. |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/.../service/PositionCardService.kt` | Facade for position card CRUD | VERIFIED | 5 public methods: getHeldCardCodes, appointPosition, dismissPosition, revokeOnRankChange, getCardCount |
| `backend/.../migration/V39__backfill_position_cards_from_jsonb.sql` | JSONB to relational backfill | VERIFIED | INSERT INTO position_card from officer.meta positionCards array with ON CONFLICT DO NOTHING |
| `backend/.../service/CharacterCreationService.kt` | 8-stat creation + validation | VERIFIED | validateStatAllocation (400/10/100), createGeneratedOfficer, selectOriginalOfficer, enforceOpsStatCap (8000) |
| `backend/.../controller/CharacterController.kt` | REST endpoints for character flow | VERIFIED | POST /generate, POST /select-original, GET /available-originals |
| `backend/.../migration/V40__add_home_planet_and_origin_columns.sql` | home_planet_id column | VERIFIED | ALTER TABLE officer ADD COLUMN home_planet_id with JSONB backfill |
| `backend/.../service/RankLadderService.kt` | Rank ladder + auto promotion | VERIFIED | getRankLadder (5-law sort), processAutoPromotion, processAutoDemotion, canPromote, RANK_LIMITS |
| `backend/.../controller/RankController.kt` | Rank REST endpoints | VERIFIED | GET /api/rank/ladder/{sessionId}, GET /api/rank/limits |
| `backend/.../service/StatGrowthService.kt` | Age modifier + exp growth | VERIFIED | getAgeMultiplier (1.2/1.0/0.8), processExpGrowth (100 threshold, carry-over, cap 100), processElderDecay |
| `backend/.../controller/OrgChartController.kt` | Org chart aggregation | VERIFIED | Single-query findBySessionId + in-memory join, OrgChartResponse with allPositionTypes |
| `backend/.../controller/PositionCardController.kt` | Position card queries | VERIFIED | GET /{sessionId}/{officerId}, GET /{sessionId} |
| `backend/.../dto/OrgChartDto.kt` | DTO classes | VERIFIED | OrgChartHolder, OrgChartResponse, PositionTypeInfo, OfficerPositionCard |
| `backend/.../service/CharacterLifecycleService.kt` | Lifecycle operations | VERIFIED | canDeleteOfficer (rank<=4), applyInjury, recoverFromInjury, triggerDeath, canInherit (age<=60), inheritOfficer |
| `frontend/.../types/index.ts` | Updated Officer interface | VERIFIED | careerType, originType, homePlanetId, locationState, STAT_KEYS_8, STAT_COLORS |
| `frontend/.../schemas/character-creation.ts` | Zod validation schema | VERIFIED | characterCreationSchema, STAT_TOTAL=400, STAT_MIN=10, STAT_MAX=100 |
| `frontend/.../game/stat-allocator.tsx` | 8-stat allocator component | VERIFIED | StatAllocator with budget badge, balanced/random presets, +/- controls |
| `frontend/.../game/origin-selector.tsx` | Origin selector component | VERIFIED | Empire radio (noble/knight/commoner), Alliance fixed label (citizen) |
| `frontend/.../game/character-pick-grid.tsx` | Character selection grid | VERIFIED | CharacterPickGrid with stat bars, select button, selecting state |
| `frontend/.../select-pool/page.tsx` | Character selection page | VERIFIED | Two tabs, API-wired fetch + post, Zod validation, toast, router navigation |
| `frontend/.../game/officer-profile-card.tsx` | Officer profile 4-section | VERIFIED | Sections: basic info, stats with bars, position card grid, location/status |
| `frontend/.../game/position-card-grid.tsx` | Position card grid | VERIFIED | PositionCardGrid with empty state text |
| `frontend/.../game/position-card-chip.tsx` | Position card chip | VERIFIED | Badge with game font, tooltip for granted commands |
| `frontend/.../game/org-tree-node-live.tsx` | Live org tree node | VERIFIED | Faction colors, Collapsible, vacant marker, recursive rendering |
| `frontend/.../org-chart/page.tsx` | Org chart page | VERIFIED | Faction tabs, API fetch, merge with static tree, legend text |
| `frontend/.../officer/page.tsx` | Officer profile page | VERIFIED | OfficerProfileCard rendering, position card API fetch |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| CommandExecutor.kt | PositionCardService | positionCardService?.getHeldCardCodes | WIRED | Replaces meta["positionCards"] cast |
| PersonnelCommands.kt | PositionCardService | appointPosition/dismissPosition/revokeOnRankChange | WIRED | 6 callsites in appoint, dismiss, promote, demote, special-promote |
| OfficerLevelModifier.kt | PositionCardService | revokeOnRankChange in promotion+demotion | WIRED | Lines 177 and 191 |
| RankLadderService.kt | PositionCardService | revokeOnRankChange in auto promo/demo, canPromote card check | WIRED | Lines 94, 126, 148 |
| OrgChartController.kt | PositionCardRepository | findBySessionId + findAllById | WIRED | Single-query N+1-free pattern |
| CharacterController.kt | CharacterCreationService | REST delegates to service | WIRED | All 3 endpoints delegate |
| ScenarioService.kt | Officer.kt | 8-stat parsing with mobility/attack/defense | WIRED | Lines 641-746 set all 8 stats + homePlanetId |
| select-pool/page.tsx | /api/character/* | characterApi fetch + post | WIRED | getAvailableOriginals, selectOriginal, generate |
| org-chart/page.tsx | /api/org-chart/{sessionId} | fetch for live holder data | WIRED | Line 228 |
| officer/page.tsx | /api/position-cards/{sessionId}/{officerId} | fetch for officer cards | WIRED | Line 106 |
| officer-profile-card.tsx | position-card-grid.tsx | PositionCardGrid in section 3 | WIRED | Import and render |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TypeScript compilation | `npx tsc --noEmit --pretty` | Clean -- zero errors | PASS |
| No legacy JSONB reads in commands | `grep meta["positionCards"] in command/` | 0 matches | PASS |
| No legacy JSONB reads in modifier | `grep meta["positionCards"] in engine/modifier/` | 0 matches | PASS |
| No officerLevel < 20 in personnel | `grep officerLevel < 20 in PersonnelCommands.kt` | 0 matches | PASS |
| All phase 02 commits present | `git log --since=2026-03-28` | 16 feat commits + 7 docs commits | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| CHAR-01 | 02-02, 02-05 | Original character selection (fixed stats) | SATISFIED | CharacterCreationService.selectOriginalOfficer, CharacterPickGrid |
| CHAR-02 | 02-02, 02-05 | Generate character (stat distribution) | SATISFIED | CharacterCreationService.createGeneratedOfficer, StatAllocator |
| CHAR-03 | 02-02, 02-05, 02-06 | 8 stats (leadership/command/intelligence/politics/admin/mobility/attack/defense) | SATISFIED | Officer.kt has all 8, STAT_KEYS_8, StatAllocator, LoghBar rendering |
| CHAR-04 | 02-03 | Age effect on stat growth | SATISFIED | StatGrowthService.getAgeMultiplier: youth +20%, elder -20% |
| CHAR-05 | 02-03 | Exp accumulation (100 = +1 stat) | SATISFIED | StatGrowthService.processExpGrowth with carry-over and cap |
| CHAR-06 | 02-02, 02-06 | Career type (military/politician) | SATISFIED | Officer.careerType field, frontend display |
| CHAR-07 | 02-02, 02-05 | Empire origin (noble/knight/commoner) | SATISFIED | OriginSelector empire radio, backend validation |
| CHAR-08 | 02-02, 02-05 | Alliance origin (citizen) | SATISFIED | OriginSelector alliance fixed label, backend validation |
| CHAR-09 | 02-08 | Cross-session inheritance | SATISFIED | CharacterLifecycleService.canInherit (famePoints>0, age<=60), inheritOfficer (80% stats) |
| CHAR-10 | 02-08 | Character deletion (rank<=colonel, planet) | SATISFIED | CharacterLifecycleService.canDeleteOfficer (MAX_DELETION_RANK=4, locationState=planet) |
| CHAR-11 | 02-08 | Injury/treatment | SATISFIED | CharacterLifecycleService.applyInjury (stat penalty via meta), recoverFromInjury |
| CHAR-12 | 02-08 | Death on flagship destruction | SATISFIED | CharacterLifecycleService.triggerDeath (sets killTurn, moves to homePlanet) |
| CHAR-13 | 02-02, 02-06 | Location state (planet/fleet/space) | SATISFIED | Officer.locationState field, frontend display |
| CHAR-14 | 02-08 | Covert ops cap (8000) | SATISFIED | CharacterCreationService.OPS_STAT_CAP=8000, enforceOpsStatCap |
| CHAR-15 | 02-02 | Home planet auto-return | SATISFIED | Officer.homePlanetId, V40 migration, triggerDeath uses homePlanetId |
| RANK-01 | 02-03 | 11-tier rank system (sub-lt to marshal) | SATISFIED | Officer.rank Short 0-10, RankLadderService |
| RANK-02 | 02-03 | Per-rank personnel limits | SATISFIED | RankLadderService.RANK_LIMITS (marshal=5, fleet admiral=5, admiral=10, etc.) |
| RANK-03 | 02-03 | Merit point accumulation | SATISFIED | Officer.experience field, ladder sorting by merit |
| RANK-04 | 02-03 | 5-law rank ladder | SATISFIED | RankLadderService.getRankLadder sorting |
| RANK-05 | 02-03 | Manual promotion resets merit to 0 | SATISFIED | PersonnelCommands: dg.experience = 0, revokeOnRankChange |
| RANK-06 | 02-03 | Auto promotion (colonel and below, 30 game days) | SATISFIED | RankLadderService.processAutoPromotion |
| RANK-07 | 02-03 | Manual demotion sets merit to 100 | SATISFIED | PersonnelCommands: dg.experience = 100, revokeOnRankChange |
| RANK-08 | 02-03 | Auto demotion | SATISFIED | RankLadderService.processAutoDemotion |
| RANK-09 | 02-03 | Personnel authority hierarchy | SATISFIED | PERSONNEL_AUTHORITY_CARDS set, hasPersonnelAuthority, canPromote |
| RANK-10 | 02-03 | Appointment/dismissal with rank range | SATISFIED | PersonnelCommands appointment/dismissal commands |
| RANK-11 | 02-03 | Peerage system (empire) | SATISFIED | Officer.peerage field, PersonnelCommands 서작 command |
| RANK-12 | 02-03 | Medal system (ladder 3rd law) | SATISFIED | PersonnelCommands 서훈 command, ladder sort by medal |
| RANK-13 | 02-03 | Evaluation points | SATISFIED | Officer.experience as evaluation, RankLadderService |
| RANK-14 | 02-03 | Fame points (cross-session) | SATISFIED | Officer.famePoints field, inheritance check |
| ORG-01 | 02-01, 02-06 | Position card system (max 16) | SATISFIED | PositionCardService, CommandGating.MAX_CARDS=16, position_card table |
| ORG-02 | 02-04, 02-06 | Empire org chart (100+ positions) | SATISFIED | EMPIRE_ORG tree in org-chart page, OrgChartController |
| ORG-03 | 02-04, 02-06 | Alliance org chart (100+ positions) | SATISFIED | ALLIANCE_ORG tree in org-chart page |
| ORG-06 | 02-01 | Concurrent positions (multiple cards) | SATISFIED | PositionCardService supports multiple cards per officer |
| ORG-08 | 02-04 | Arrest authority cards | SATISFIED | PositionCardType includes arrest-related cards, exposed via API |
| PERS-06 | 02-02 | Home planet setting for auto-return | SATISFIED | Officer.homePlanetId, V40 migration, CharacterCreationService sets it |
| HARD-03 | 02-01 | PositionCard JSONB to relational migration | SATISFIED | V39 migration, PositionCardService facade, 6 callsite migrations, zero JSONB reads remaining |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| -- | -- | No anti-patterns found | -- | -- |

No TODO/FIXME/PLACEHOLDER/stub patterns found in any phase 02 files. No empty implementations. No hardcoded empty data. Clean codebase.

### Human Verification Required

### 1. Character Selection Page Visual Check

**Test:** Navigate to the character selection page after joining a session. Verify two tabs render correctly, 8-stat allocator has +/- buttons with budget badge, and origin selector renders faction-appropriate options.
**Expected:** Budget badge shows remaining points with color feedback. Balanced/random presets work. Empire shows 3-radio origin; Alliance shows fixed "citizen" label.
**Why human:** Visual layout, spacing, responsive behavior, DungGeunMo font rendering cannot be verified programmatically.

### 2. Officer Profile Page Visual Check

**Test:** Navigate to an officer profile page. Verify all 4 sections render correctly with stat bars, position card chips, and location/status.
**Expected:** 8 horizontal stat bars with distinct colors, exp indicators in yellow, position card chips with game font and tooltip on hover, location/status section with color-coded injury.
**Why human:** Stat bar proportions, tooltip hover behavior, section separator rendering, color accuracy.

### 3. Organization Chart Visual Check

**Test:** Navigate to /org-chart. Verify faction tabs with distinct color schemes, expandable/collapsible tree, and vacant position markers.
**Expected:** Empire nodes use gold (#c9a84c), Alliance nodes use blue (#1e4a8a). Tree expands to depth 3 by default. Clicking nodes toggles children. Vacant positions show muted marker.
**Why human:** Faction color accuracy, collapsible interaction feel, scroll position preservation on tab switch, tree indentation.

### Gaps Summary

All 5 observable truths verified. All 36 requirement IDs covered across 7 completed plans. All backend and frontend artifacts exist, are substantive (not stubs), and are properly wired.

The only remaining item is Plan 07 (visual verification checkpoint), which requires human inspection of the three main UI surfaces. This is expected -- Plan 07 was explicitly designed as `autonomous: false` with `type: checkpoint:human-verify`.

No code gaps found. No blockers. No stubs. No orphaned artifacts.

---

_Verified: 2026-03-29T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
