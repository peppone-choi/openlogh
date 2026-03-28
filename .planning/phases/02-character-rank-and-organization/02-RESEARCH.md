# Phase 2: Character, Rank, and Organization - Research

**Researched:** 2026-03-28
**Domain:** Officer entity model, rank/promotion system, position card JSONB-to-relational migration, organization chart UI, character creation flow
**Confidence:** HIGH

## Summary

Phase 2 transforms the officer identity system from a legacy three-kingdoms model into a full LOGH 8-stat officer with rank, career, origin, and relational position cards. The critical technical challenge is HARD-03: migrating `officer.meta["positionCards"]` JSONB access to queries against the existing `position_card` table, which is referenced in 6+ callsites across CommandExecutor, PersonnelCommands, and OfficerLevelModifier.

The backend infrastructure is well-established: Officer.kt already carries all 8 stats, exp fields, rank(0-10), careerType, originType, and peerage. RankLadderService implements the 5-law ladder with auto-promotion/demotion. PositionCard entity and repository exist with proper migrations (V32). The gap is that all runtime code still reads/writes position cards from JSONB, and the scenario data uses a 5-stat format that must be extended to 8 stats.

The frontend requires significant updates: the select-pool page uses the old 5-stat system (leadership/strength/intel/politics/charm with total=350), the org-chart is a static hardcoded tree with no live data binding, and the officer profile page lacks career/origin/position card display. New UI for character creation (8-stat allocation), org-chart with live holders, and enhanced officer profile (4-section layout per D-10) must be built.

**Primary recommendation:** Execute HARD-03 JSONB migration first (backend-only, 6 callsite refactor), then layer character creation and org-chart features on top of the clean relational model.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Original characters (Reinhard, Yang Wen-li, etc.) are first-come-first-served selection. Players pick from remaining characters when joining a session
- **D-02:** Generated characters allocate a total point budget across 8 stats with per-stat min/max limits
- **D-03:** Origin (noble/knight/commoner) and career (military start) per Claude's discretion based on gin7 manual. Empire: noble/knight/commoner selection, Alliance: citizen fixed
- **D-04:** 5-law rank ladder criteria are hidden from players. Promotion is manually executed by personnel authority holders (personnel chief/military minister/emperor)
- **D-05:** Auto promotion/demotion notification method at Claude's discretion
- **D-06:** Rank cap handling at Claude's discretion
- **D-07:** Empire/Alliance org charts displayed as expandable/collapsible tree hierarchy: Emperor/Chairman -> Cabinet/Council -> Military Ministry/Defense Committee -> Fleets. Each node shows current holder name
- **D-08:** Faction visual differentiation at Claude's discretion
- **D-09:** 8 stats visualized as horizontal bar chart + numeric value
- **D-10:** Profile screen has 4 sections: (1) Basic info (name, rank, faction, origin, career, age, portrait), (2) 8 stats + exp progress bars, (3) Position card list (max 16), (4) Location/status info (current location, fleet, injury, CP remaining)

### Claude's Discretion

- HARD-03 (PositionCard JSONB->relational) migration strategy and implementation
- Generate character total point budget and per-stat min/max values
- Auto promotion/demotion notification method (in-game mail, etc.)
- Rank cap reached handling (block promotion, etc.)
- Empire/Alliance org chart visual differentiation
- Original character data (stats, names, portraits) scenario JSON structure
- Flyway migration number and schema change details

### Deferred Ideas (OUT OF SCOPE)

None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>

## Phase Requirements

| ID      | Description                                                                                    | Research Support                                                                                                           |
| ------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| CHAR-01 | Original character selection (fixed stats, first-come)                                         | SelectPool system exists; scenario*logh*\*.json has 5-stat officer data; needs 8-stat extension and SelectPool integration |
| CHAR-02 | Generate character creation (name/appearance/stat allocation)                                  | Frontend select-pool page exists with old 5-stat UI; needs 8-stat rewrite with new total/min/max                           |
| CHAR-03 | 8 stats (leadership/command/intelligence/politics/administration/mobility/attack/defense)      | Officer.kt already has all 8 stats + exp fields; scenario data only has 5 stats; frontend has partial 8-stat display       |
| CHAR-04 | Stat growth - age effect (youth +, elder -)                                                    | Officer.kt has age, birthYear, deathYear; OfficerLevelModifier exists for stat modifiers                                   |
| CHAR-05 | Stat growth - exp (CP usage cumulative, 100=1 increase)                                        | Officer.kt has pcpUsedTotal/mcpUsedTotal and \*Exp fields; wiring needed                                                   |
| CHAR-06 | Career type (military/politician), class change possible                                       | Officer.kt has careerType field; no transition logic yet                                                                   |
| CHAR-07 | Origin - Empire (noble/knight/commoner/exile)                                                  | Officer.kt has originType field with these values                                                                          |
| CHAR-08 | Origin - Alliance (citizen/exile)                                                              | Same field, Alliance-specific values                                                                                       |
| CHAR-09 | Character inheritance (cross-session, evaluation points, age<=60)                              | Officer has famePoints; needs cross-session logic in gateway-app                                                           |
| CHAR-10 | Character deletion (colonel and below, in residential/hotel)                                   | Officer.killTurn exists; need facility check                                                                               |
| CHAR-11 | Injury/treatment (stat decrease on defeat, recovery period)                                    | Officer.injury field exists; no recovery logic yet                                                                         |
| CHAR-12 | Death (flagship destruction, optional)                                                         | Officer.killTurn exists; no flagship-death trigger yet                                                                     |
| CHAR-13 | Location state (planet/fleet/space)                                                            | Officer.locationState field exists ("planet" default)                                                                      |
| CHAR-14 | Covert ops stats 3 types (political/intel/military ops, max 8000)                              | Officer has politicalOps/intelOps/militaryOps fields already                                                               |
| CHAR-15 | Home planet - auto-return on flagship destruction                                              | meta["returnPlanetId"] pattern exists in PersonalCommands.kt; needs dedicated column                                       |
| RANK-01 | 11-tier rank system (sub-lt to marshal)                                                        | Officer.rank (Short 0-10) exists; OfficerLevelModifier has rank constants                                                  |
| RANK-02 | Per-rank personnel limit (marshal=5, fleet admiral=5, etc.)                                    | RankLadderService.RANK_LIMITS already defined                                                                              |
| RANK-03 | Merit point accumulation (combat/operations/occupation)                                        | Officer.experience (Int) field exists                                                                                      |
| RANK-04 | Rank ladder (5 laws: merit->peerage->medal->influence->total stats)                            | RankLadderService.getRankLadder() fully implemented                                                                        |
| RANK-05 | Manual promotion (personnel authority, merit reset to 0, position cards revoked)               | PersonnelCommands.승진 exists but uses JSONB; OfficerLevelModifier.applyPromotionEffects uses JSONB                        |
| RANK-06 | Auto promotion (colonel and below, every 30G days, ladder #1)                                  | RankLadderService.processAutoPromotion() implemented                                                                       |
| RANK-07 | Manual demotion (personnel authority, merit=100, cards revoked)                                | PersonnelCommands.강등 exists; OfficerLevelModifier.applyDemotionEffects uses JSONB                                        |
| RANK-08 | Auto demotion (colonel and below, 30G days, on excess)                                         | RankLadderService.processAutoDemotion() implemented                                                                        |
| RANK-09 | Personnel authority hierarchy (Emperor/Military Minister/Personnel Chief)                      | PositionCardType enum defines these with minRank; CommandGating checks cards                                               |
| RANK-10 | Appointment/dismissal (superior->subordinate, rank range limits)                               | PersonnelCommands 임명/파면 exist but use JSONB                                                                            |
| RANK-11 | Peerage - Empire (duke/marquis/count/viscount/baron/knight)                                    | PersonnelCommands.서작 exists; NOBLE_RANKS defined                                                                         |
| RANK-12 | Medal award (experience+, betray-)                                                             | PersonnelCommands.서훈 exists                                                                                              |
| RANK-13 | Evaluation points (in-session character evaluation)                                            | Officer.experience used; needs dedicated evaluation system                                                                 |
| RANK-14 | Fame points (cross-session player evaluation)                                                  | Officer.famePoints field exists                                                                                            |
| ORG-01  | Position card system (max 16 per character)                                                    | PositionCardSystem fully defined (22+ types); CommandGating.MAX_CARDS=16                                                   |
| ORG-02  | Empire org chart (Palace->Cabinet->Military Ministry->Supreme Command->Fleets, 100+ positions) | Frontend org-chart page has static EMPIRE_ORG tree; needs live data binding                                                |
| ORG-03  | Alliance org chart (Supreme Council->Defense Committee->Fleets, 100+ positions)                | Frontend has static ALLIANCE_ORG tree; needs live data binding                                                             |
| ORG-06  | Concurrent positions (multiple position cards)                                                 | CommandGating.canAddCard() checks < 16; supported by design                                                                |
| ORG-08  | Arrest authority (provost marshal/interior minister/justice minister)                          | PositionCardType.MILITARY_POLICE_CHIEF has arrest_permit in grantedCommands                                                |
| PERS-06 | Return setting (set return planet on flagship destruction)                                     | PersonalCommands.귀환설정 command exists, uses meta["returnPlanetId"]                                                      |
| HARD-03 | PositionCard JSONB->relational migration                                                       | 6+ callsites read meta["positionCards"]; position_card table exists (V32); migration is code refactor                      |

</phase_requirements>

## Standard Stack

### Core (Already in project -- no new dependencies)

| Library         | Version                  | Purpose                                  | Why Standard                             |
| --------------- | ------------------------ | ---------------------------------------- | ---------------------------------------- |
| Spring Data JPA | 3.4.2 (Spring Boot)      | PositionCard queries, Officer repository | Already used for all entities            |
| Flyway          | 1.0 (Spring Boot plugin) | Schema migration V39+                    | Already manages 38 migrations            |
| Next.js         | 16.1.6                   | Frontend pages and routing               | Already in use                           |
| React           | 19.2.3                   | UI components                            | Already in use                           |
| Zustand         | 5.0.11                   | Frontend state management                | Already used for officerStore, gameStore |
| Tailwind CSS    | 4                        | Styling                                  | Already in use                           |
| Radix UI        | 1.4.3                    | Headless components (tree, accordion)    | Already used for Card, Badge, Button     |
| Lucide React    | 0.564.0                  | Icons                                    | Already in use                           |
| Vitest          | 3.2.4                    | Frontend unit testing                    | Already configured                       |
| JUnit 5         | (Spring Boot bundled)    | Backend unit testing                     | Already configured                       |

### Supporting (No new packages needed)

This phase requires zero new dependencies. All functionality is achievable with the existing stack.

### Alternatives Considered

| Instead of            | Could Use                              | Tradeoff                                                                                    |
| --------------------- | -------------------------------------- | ------------------------------------------------------------------------------------------- |
| Custom tree component | react-arborist or similar tree library | Existing OrgTreeNode component works; custom gives full control over LOGH-specific styling  |
| D3.js for org chart   | Fancy interactive visualization        | Overkill; expandable tree with Radix Accordion pattern is simpler and matches D-07 decision |

**Installation:** No new packages required.

## Architecture Patterns

### Backend: HARD-03 Migration Pattern

**What:** Replace all `officer.meta["positionCards"]` JSONB reads/writes with `PositionCardRepository` queries.

**Six callsites to migrate:**

1. `CommandExecutor.kt:87` -- reads heldCards for command gating
2. `PersonnelCommands.kt:301` (임명 checkFullCondition) -- reads card count
3. `PersonnelCommands.kt:311-312` (임명 run) -- adds card to JSONB list
4. `PersonnelCommands.kt:350` (파면 checkFullCondition) -- reads card list
5. `PersonnelCommands.kt:361` (파면 run) -- removes card from JSONB list
6. `OfficerLevelModifier.kt:175,196` (applyPromotionEffects, applyDemotionEffects) -- retains only basic+fief cards

**Migration strategy:**

```
Phase A: Add PositionCardRepository injection to affected services
Phase B: Replace JSONB reads with repository.findBySessionIdAndOfficerId() queries
Phase C: Replace JSONB writes with repository.save()/delete() calls
Phase D: Remove meta["positionCards"] writes (keep read as fallback during transition)
Phase E: Flyway V39 migration to backfill position_card rows from existing meta JSONB
Phase F: Remove all JSONB fallback reads
```

**Pattern for reads (after migration):**

```kotlin
// BEFORE (JSONB):
val heldCards = (general.meta["positionCards"] as? List<*>)
    ?.mapNotNull { it as? String }
    ?: CommandGating.defaultCards()

// AFTER (relational):
val positionCards = positionCardRepository.findBySessionIdAndOfficerId(
    general.sessionId, general.id
)
val heldCards = if (positionCards.isEmpty()) {
    CommandGating.defaultCards()
} else {
    positionCards.map { it.positionType }
}
```

**Pattern for writes (appointment):**

```kotlin
// BEFORE (JSONB):
val cards = (dg.meta["positionCards"] as? MutableList<String>)
    ?: mutableListOf("personal", "captain").also { dg.meta["positionCards"] = it }
if (positionCode !in cards) { cards.add(positionCode) }

// AFTER (relational):
val existing = positionCardRepository.findBySessionIdAndOfficerId(sessionId, dg.id)
if (existing.none { it.positionType == positionCode }) {
    positionCardRepository.save(PositionCard(
        officerId = dg.id,
        sessionId = sessionId,
        positionType = positionCode,
        positionNameKo = cardType.displayName,
    ))
}
```

**Pattern for promotion card revocation:**

```kotlin
// BEFORE (JSONB):
val cards = (officer.meta["positionCards"] as? MutableList<String>)
if (cards != null) {
    val retained = setOf("personal", "captain")
    val fiefCards = cards.filter { it.startsWith("fief_") }
    cards.retainAll(retained)
    cards.addAll(fiefCards)
}

// AFTER (relational):
val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officer.id)
val toDelete = cards.filter { card ->
    card.positionType !in setOf("personal", "captain") &&
    !card.positionType.startsWith("fief_")
}
positionCardRepository.deleteAll(toDelete)
```

### Backend: Scenario 8-Stat Extension

**Current scenario officer array format (5-stat):**

```
[factionIdx, name, picture, factionRef, cityName,
 leadership, strength, intelligence, politics, charm,
 rank, birthYear, deathYear, personality, special, bio]
```

**New scenario officer array format (8-stat):**

```
[factionIdx, name, picture, factionRef, cityName,
 leadership, command, intelligence, politics, administration,
 mobility, attack, defense,
 rank, birthYear, deathYear, personality, special, bio]
```

The `parseGeneral()` function in ScenarioService.kt already handles multiple tuple layouts (3-stat legacy, 5-stat). Add an 8-stat detection branch:

```kotlin
val hasEightStatTuple = row.getOrNull(12) is Number && row.getOrNull(13) is Number && row.getOrNull(14) is Number && row.getOrNull(15) is Number
```

### Backend: Home Planet Column

**Current state:** `meta["returnPlanetId"]` used in PersonalCommands.귀환설정

**Target:** Add `home_planet_id` BIGINT column to officer table via Flyway migration. The 귀환설정 command sets this column; flagship destruction checks this column for auto-return.

### Frontend: Officer Profile 4-Section Layout (D-10)

```
+-----------------------------------+
| Section 1: Basic Info             |
| [Portrait] Name, Rank, Faction    |
|            Origin, Career, Age    |
+-----------------------------------+
| Section 2: 8 Stats + Exp          |
| leadership [====...] 99  +12exp   |
| command    [===....] 75  +3exp    |
| ...                               |
+-----------------------------------+
| Section 3: Position Cards          |
| [개인] [함장] [함대사령관]         |
| (up to 16 cards in grid)          |
+-----------------------------------+
| Section 4: Location/Status         |
| Location: 오딘 | Fleet: 제1함대    |
| Injury: 건강 | PCP: 10 MCP: 10    |
+-----------------------------------+
```

### Frontend: Org Chart with Live Data

**Current state:** Static hardcoded tree in org-chart/page.tsx

**Target:** Each org node maps to a PositionCardType. Fetch position_card records for the session, join with officer names, and display current holder in each node. Empty positions show `[□ 공석]`.

**Data flow:**

1. GET `/api/position-cards?sessionId=X` -> list of all position cards in session
2. Join with officer data (already in officerStore/gameStore)
3. Render tree where each node shows: title, required rank, current holder name + portrait (or vacant marker)

### Frontend: Character Creation 8-Stat Allocation

**Current state:** select-pool/page.tsx uses 5 stats (STAT_TOTAL=350, MIN=10, MAX=100)

**Target:** 8 stats, new total budget (recommended: 400 for 8 stats, min=10, max=100). Add origin/career selection for Empire. Alliance auto-sets citizen.

### Recommended Project Structure (changes only)

```
backend/game-app/src/main/kotlin/com/openlogh/
├── entity/Officer.kt              # Add homePlanetId column
├── entity/PositionCard.kt         # No change needed
├── repository/PositionCardRepository.kt  # Add deleteByOfficerIdAndPositionTypeNotIn()
├── service/PositionCardService.kt  # NEW: Facade for card CRUD operations
├── service/RankLadderService.kt   # Wire to PositionCardService for promotion effects
├── service/CharacterCreationService.kt  # NEW: 8-stat validation + officer creation
├── controller/PositionCardController.kt  # NEW: REST endpoints for card queries
├── controller/OrgChartController.kt  # NEW: Aggregated org chart data endpoint
├── command/CommandExecutor.kt     # Inject PositionCardRepository, remove JSONB reads
├── command/nation/PersonnelCommands.kt  # Use PositionCardRepository
├── engine/modifier/OfficerLevelModifier.kt  # Use PositionCardRepository
├── engine/organization/PositionCardSystem.kt  # No change needed
└── resources/db/migration/
    ├── V39__add_home_planet_column.sql  # officer.home_planet_id
    └── V40__backfill_position_cards.sql  # JSONB -> position_card table

frontend/src/
├── app/(lobby)/lobby/select-pool/page.tsx  # Rewrite: 8-stat, origin/career
├── app/(game)/org-chart/page.tsx  # Rewrite: live data, holder display
├── app/(game)/officer/page.tsx    # Enhance: 4-section layout per D-10
├── app/(game)/position-cards/page.tsx  # Enhance: card list with details
└── components/game/
    ├── stat-allocation.tsx  # NEW: 8-stat slider/input component
    ├── org-tree-node.tsx    # NEW: extracted tree node with holder
    └── position-card-badge.tsx  # NEW: card display component
```

### Anti-Patterns to Avoid

- **Dual-write trap:** Do NOT write to both JSONB and relational table simultaneously. Migrate reads first, then writes, then drop JSONB field. Dual-write creates divergence bugs.
- **N+1 queries in org chart:** Fetch ALL position cards for a session in one query, then map in-memory. Do not query per-node.
- **Hardcoded stat budgets:** Store stat constraints (total, min, max) in ScenarioStat (already exists in ScenarioData.kt) so different scenarios can have different budgets.
- **Mixing officerLevel and rank:** `officerLevel` is the legacy compat alias for `rank` (defined in EntityCompat.kt). Use `rank` consistently in new code.

## Don't Hand-Roll

| Problem                           | Don't Build                            | Use Instead                                           | Why                                       |
| --------------------------------- | -------------------------------------- | ----------------------------------------------------- | ----------------------------------------- |
| Tree UI component                 | Custom recursive renderer from scratch | Extract existing OrgTreeNode, enhance with data props | Already works; just needs data binding    |
| Stat bar visualization            | Canvas-based chart                     | Existing LoghBar component                            | Already in officer page, battle UI        |
| Form validation (stat allocation) | Manual math checks                     | Zod schema with refinement                            | Already in project (zod 4.3.6), type-safe |
| Position card CRUD                | Direct SQL in commands                 | PositionCardRepository (JPA)                          | Already exists, just not wired in         |
| Rank display text                 | New formatter                          | Existing formatOfficerLevelText()                     | Already handles rank->text conversion     |

**Key insight:** Most backend infrastructure already exists. This phase is primarily a **wiring** exercise (connect relational table to existing logic) plus **frontend rewrite** (5-stat -> 8-stat, static -> live data).

## Common Pitfalls

### Pitfall 1: JSONB Data Loss During Migration

**What goes wrong:** Existing sessions have position cards stored only in officer.meta JSONB. If code switches to relational reads without backfilling the position_card table, all existing officers lose their positions.
**Why it happens:** V32 created the table but no data was ever inserted there; all runtime code writes to JSONB.
**How to avoid:** V40 migration MUST read officer.meta->>'positionCards' and INSERT corresponding rows into position_card before any code changes deploy.
**Warning signs:** Officers who had positions suddenly show only [personal, captain] cards.

### Pitfall 2: Rank 20+ Check in Personnel Commands

**What goes wrong:** PersonnelCommands.kt checks `general.officerLevel < 20` for permission. The rank field only goes 0-10, but legacy code uses `officerLevel` which maps to `rank` via EntityCompat.
**Why it happens:** Original OpenSamguk had rank values up to 20+ for ruler-level. The `officerLevel < 20` check effectively means "only ruler can promote" which is different from the gin7 model where Personnel Chief (rank 6+) can promote.
**How to avoid:** Replace `officerLevel < 20` checks with PositionCard-based authority checks. An officer with `personnel_chief`, `military_minister`, or `emperor` card should have promotion authority.
**Warning signs:** Only the sovereign can promote/demote, not the personnel chief.

### Pitfall 3: Scenario Stat Column Mismatch

**What goes wrong:** LOGH scenario JSONs have 5 numeric stats (leadership, strength, intelligence, politics, charm) mapped to Officer fields as (leadership, command, intelligence, politics, administration). The 3 new stats (mobility, attack, defense) default to 50 for scenario NPCs.
**Why it happens:** parseGeneral() maps `strength` -> `command` and `charm` -> `administration`. Adding 3 new columns to scenario JSON requires updating ALL 10 scenario*logh*\*.json files AND the parseGeneral() tuple detection logic.
**How to avoid:** First update parseGeneral() to detect 8-stat format, then update scenario JSONs. Keep 5-stat detection as fallback with computed defaults for missing stats (e.g., mobility = (leadership+command)/2, attack = command, defense = intelligence).
**Warning signs:** New stats are always 50 for all officers.

### Pitfall 4: Optimistic Lock Conflicts on Officer During Card Operations

**What goes wrong:** Officer entity has @Version for optimistic locking (added in Phase 1). If position card operations also modify officer.meta (JSONB), they trigger version increments and potential conflicts with concurrent command execution.
**Why it happens:** JSONB card operations modify the Officer row, creating contention.
**How to avoid:** The relational migration eliminates this entirely -- position_card is a separate table, so card operations don't touch the officer row. This is a hidden benefit of HARD-03.
**Warning signs:** StaleObjectStateException during appointment/dismissal commands.

### Pitfall 5: Frontend Officer Type Missing New Fields

**What goes wrong:** The TypeScript `Officer` interface in `types/index.ts` lacks fields like `careerType`, `originType`, `homePlanetId` that are needed for the profile UI.
**Why it happens:** Frontend type was ported from OpenSamguk and not updated for all LOGH fields.
**How to avoid:** Audit Officer.kt columns against the TS interface. Add missing fields. The backend already serializes all fields -- the frontend just doesn't type them.
**Warning signs:** TypeScript errors or `undefined` values in profile display.

## Code Examples

### Flyway V39: Add home_planet_id Column

```sql
-- V39: Add home_planet_id to officer (CHAR-15, PERS-06)
ALTER TABLE officer ADD COLUMN home_planet_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN officer.home_planet_id IS 'Home planet for auto-return on flagship destruction';
```

### Flyway V40: Backfill Position Cards from JSONB

```sql
-- V40: Backfill position_card from officer.meta JSONB
-- Must run BEFORE code switches to relational reads
INSERT INTO position_card (officer_id, session_id, position_type, position_name_ko, granted_at, meta)
SELECT
    g.id,
    g.world_id,
    card_code.value,
    card_code.value,  -- Will be updated with proper Korean names by application
    now(),
    '{}'::jsonb
FROM officer g,
     jsonb_array_elements_text(g.meta->'positionCards') AS card_code
WHERE g.meta ? 'positionCards'
  AND jsonb_typeof(g.meta->'positionCards') = 'array'
ON CONFLICT DO NOTHING;
```

### PositionCardService Facade

```kotlin
@Service
class PositionCardService(
    private val positionCardRepository: PositionCardRepository,
) {
    fun getHeldCardCodes(sessionId: Long, officerId: Long): List<String> {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        return if (cards.isEmpty()) CommandGating.defaultCards()
        else cards.map { it.positionType }
    }

    fun appointPosition(sessionId: Long, officerId: Long, cardType: PositionCardType) {
        val existing = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        if (existing.any { it.positionType == cardType.code }) return // already holds
        positionCardRepository.save(PositionCard(
            officerId = officerId,
            sessionId = sessionId,
            positionType = cardType.code,
            positionNameKo = cardType.displayName,
        ))
    }

    fun dismissPosition(sessionId: Long, officerId: Long, positionCode: String) {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        val target = cards.find { it.positionType == positionCode } ?: return
        positionCardRepository.delete(target)
    }

    fun revokeOnRankChange(sessionId: Long, officerId: Long) {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        val toDelete = cards.filter { card ->
            card.positionType !in setOf("personal", "captain") &&
            !card.positionType.startsWith("fief_")
        }
        if (toDelete.isNotEmpty()) positionCardRepository.deleteAll(toDelete)
    }
}
```

### 8-Stat Zod Validation Schema (Frontend)

```typescript
import { z } from 'zod';

const STAT_KEYS_8 = [
    'leadership',
    'command',
    'intelligence',
    'politics',
    'administration',
    'mobility',
    'attack',
    'defense',
] as const;

const statSchema = z.number().int().min(10).max(100);

export const characterCreationSchema = z.object({
    name: z.string().min(2).max(20),
    originType: z.enum(['noble', 'knight', 'commoner', 'citizen', 'exile']),
    stats: z
        .object({
            leadership: statSchema,
            command: statSchema,
            intelligence: statSchema,
            politics: statSchema,
            administration: statSchema,
            mobility: statSchema,
            attack: statSchema,
            defense: statSchema,
        })
        .refine((stats) => Object.values(stats).reduce((a, b) => a + b, 0) === 400, {
            message: '능력치 합계는 400이어야 합니다.',
        }),
});
```

### OrgChart REST Endpoint

```kotlin
@RestController
@RequestMapping("/api/org-chart")
class OrgChartController(
    private val positionCardRepository: PositionCardRepository,
    private val officerRepository: OfficerRepository,
) {
    @GetMapping("/{sessionId}")
    fun getOrgChart(@PathVariable sessionId: Long): OrgChartResponse {
        val cards = positionCardRepository.findBySessionId(sessionId)
        val officerIds = cards.map { it.officerId }.distinct()
        val officers = officerRepository.findAllById(officerIds).associateBy { it.id }

        val holders = cards.map { card ->
            val officer = officers[card.officerId]
            OrgChartHolder(
                positionType = card.positionType,
                positionNameKo = card.positionNameKo,
                officerId = card.officerId,
                officerName = officer?.name,
                officerPicture = officer?.picture,
                officerRank = officer?.rank?.toInt(),
                factionId = officer?.factionId ?: 0,
            )
        }
        return OrgChartResponse(holders)
    }
}
```

## State of the Art

| Old Approach                                             | Current Approach                                                                                | When Changed                | Impact                                                                          |
| -------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | --------------------------- | ------------------------------------------------------------------------------- |
| 5-stat system (leadership/strength/intel/politics/charm) | 8-stat system (leadership/command/intelligence/politics/administration/mobility/attack/defense) | Phase 1 entity rename       | Scenario data and character creation must use 8 stats                           |
| Position cards in officer.meta JSONB                     | Relational position_card table (V32)                                                            | V32 migration created table | Table exists but code still uses JSONB -- HARD-03 bridges this gap              |
| officerLevel (rank 0-20+)                                | rank (0-10)                                                                                     | LOGH domain mapping         | Legacy checks using officerLevel>=20 must be replaced with card-based authority |
| Static org chart tree                                    | Live org chart with data binding                                                                | This phase                  | Frontend org-chart page must fetch position_card data                           |

**Deprecated/outdated:**

- `officer.meta["positionCards"]` -- must be replaced by position_card table queries (HARD-03)
- `officerLevel < 20` checks in PersonnelCommands -- must use PositionCard-based authority
- 5-stat STAT_KEYS in frontend select-pool -- must be 8-stat
- LEGACY_PERSONALITY_OPTIONS in select-pool -- review if still applicable to LOGH

## Open Questions

1. **Generate Character Stat Budget**
    - What we know: Old system used 350 total across 5 stats (avg 70). ScenarioStat already defines total/min/max (defaults: total=165, min=15, max=80 for legacy 3-stat).
    - What's unclear: Ideal total for 8 stats. 400 gives avg 50 (conservative). 480 gives avg 60 (closer to original feel).
    - Recommendation: Use 400 total, min=10, max=100. Store in ScenarioStat for per-scenario tuning. Original characters often have stats in 70-99 range, so generated characters should feel noticeably weaker (this is intentional -- incentivizes picking originals).

2. **Backfill Strategy for Existing Sessions**
    - What we know: Existing sessions may have officers with JSONB position cards. V40 migration backfills.
    - What's unclear: Are there active sessions right now with meaningful JSONB card data?
    - Recommendation: V40 backfill migration handles it. If no active sessions, backfill is a no-op safety measure.

3. **Personnel Authority Chain After HARD-03**
    - What we know: Currently `officerLevel < 20` gates all personnel commands. After migration, should use card-based checks.
    - What's unclear: Exact authority hierarchy -- who can promote whom? Can personnel chief promote to any rank, or only up to their own rank?
    - Recommendation: gin7 rules: Emperor/Chairman can promote anyone. Military Minister can promote up to Admiral (rank 8). Personnel Chief can promote up to Rear Admiral (rank 6). Implement as `PositionCardType.maxPromotionRank` field.

4. **Original Character Data Completeness**
    - What we know: scenario_logh_1.json has officers with 5 stats + rank + birth/death years. There are 10 LOGH scenario files.
    - What's unclear: Whether all 10 scenarios have complete officer rosters, and whether they need 8-stat updates.
    - Recommendation: Update all 10 scenarios to 8-stat format. For the 3 new stats, derive from existing stats as defaults (mobility = avg(leadership, command), attack = command, defense = intelligence) then hand-tune key characters.

## Validation Architecture

### Test Framework

| Property               | Value                                                                        |
| ---------------------- | ---------------------------------------------------------------------------- |
| Framework (Backend)    | JUnit 5 (Jupiter) + Spring Boot Test                                         |
| Framework (Frontend)   | Vitest 3.2.4                                                                 |
| Config file (Backend)  | backend/game-app/build.gradle.kts (standard Spring Boot test)                |
| Config file (Frontend) | frontend/vitest.config.ts                                                    |
| Quick run (Backend)    | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*" -x bootJar` |
| Quick run (Frontend)   | `cd frontend && pnpm vitest run --reporter=verbose`                          |
| Full suite             | Both above combined                                                          |

### Phase Requirements -> Test Map

| Req ID  | Behavior                                     | Test Type | Automated Command                                                                    | File Exists?          |
| ------- | -------------------------------------------- | --------- | ------------------------------------------------------------------------------------ | --------------------- |
| HARD-03 | Position card CRUD via relational table      | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.PositionCardServiceTest"`    | Wave 0                |
| HARD-03 | CommandExecutor uses relational cards        | unit      | `./gradlew :game-app:test --tests "com.openlogh.command.CommandExecutorTest"`        | Exists (needs update) |
| RANK-05 | Manual promotion resets merit, revokes cards | unit      | `./gradlew :game-app:test --tests "com.openlogh.command.NationCommandTest"`          | Exists (needs update) |
| RANK-02 | Rank limit enforcement                       | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.RankLadderServiceTest"`      | Wave 0                |
| CHAR-02 | 8-stat validation (sum=400, min=10, max=100) | unit      | `pnpm vitest run src/app/(lobby)/lobby/select-pool/`                                 | Wave 0                |
| CHAR-15 | Home planet auto-return                      | unit      | `./gradlew :game-app:test --tests "com.openlogh.command.GeneralMilitaryCommandTest"` | Exists (needs update) |
| ORG-01  | Card gating blocks unauthorized commands     | unit      | `./gradlew :game-app:test --tests "com.openlogh.command.CommandExecutorTest"`        | Exists (needs update) |

### Sampling Rate

- **Per task commit:** `./gradlew :game-app:test --tests "com.openlogh.*" -x bootJar` (backend) or `pnpm vitest run` (frontend)
- **Per wave merge:** Full backend + frontend test suite
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/PositionCardServiceTest.kt` -- covers HARD-03 CRUD
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/RankLadderServiceTest.kt` -- covers RANK-02, RANK-06, RANK-08
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/CharacterCreationServiceTest.kt` -- covers CHAR-02, CHAR-03
- [ ] Update existing `CommandExecutorTest.kt` -- add relational card gating tests

## Sources

### Primary (HIGH confidence)

- Direct codebase analysis: Officer.kt, PositionCard.kt, PositionCardSystem.kt, PersonnelCommands.kt, CommandExecutor.kt, OfficerLevelModifier.kt, RankLadderService.kt, ScenarioService.kt, SelectPoolService.kt
- Flyway migration V32 (position_card table schema)
- Frontend: org-chart/page.tsx, officer/page.tsx, select-pool/page.tsx, types/index.ts
- Scenario data: scenario_logh_1.json (officer array format)
- Entity compat: EntityCompat.kt (officerLevel = rank alias)
- ScenarioData.kt model (ScenarioStat for stat budget)

### Secondary (MEDIUM confidence)

- CONTEXT.md decisions D-01 through D-10 (user decisions from discussion phase)
- REQUIREMENTS.md CHAR-01 through HARD-03 (requirement definitions)

### Tertiary (LOW confidence)

- gin7 manual mechanics (referenced in code comments, not directly verified against manual PDF) -- planner should cross-reference docs/reference/gin7manual.txt for edge cases

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH -- all libraries already in project, zero new dependencies
- Architecture (HARD-03 migration): HIGH -- callsites precisely identified, patterns clear
- Architecture (character creation): HIGH -- existing entity model supports 8 stats, ScenarioStat model exists
- Architecture (org chart): MEDIUM -- frontend tree component exists but needs significant data binding work; REST endpoint pattern is standard
- Pitfalls: HIGH -- identified from direct code analysis of JSONB/relational mismatch
- Scenario data format: MEDIUM -- 5-stat format verified, 8-stat extension path clear but requires manual data entry for 3 new stats across 10 JSON files

**Research date:** 2026-03-28
**Valid until:** 2026-04-28 (stable -- no external dependency changes expected)
