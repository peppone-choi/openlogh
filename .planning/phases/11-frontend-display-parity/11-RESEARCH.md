# Phase 11: Frontend Display Parity - Research

**Researched:** 2026-04-03
**Domain:** Frontend display parity (legacy PHP/Vue vs current Next.js/React)
**Confidence:** HIGH

## Summary

Phase 11 is a frontend display parity audit and implementation phase. The legacy PHP/Vue frontend renders game data via `GetFrontInfo.php` API and 16+ Vue pages. The current Next.js frontend has 60+ routes with comprehensive type definitions (`FrontInfoResponse`, `GeneralFrontInfo`, `NationFrontInfo`, `CityFrontInfo`) that already map closely to the legacy API. The existing `formatLog.ts` color tag parser matches the legacy implementation nearly 1:1. Key gaps are: (1) some fields present in legacy types but missing from display, (2) battle log HTML structure (`small_war_log` template) not parsed by current `formatLog.ts`, (3) `autorunUser` field cast unsafely in dashboard, (4) legacy `GeneralSupplementCard` fields like win rate, kill ratio, dex levels with color grades not fully replicated.

**Primary recommendation:** The audit (Plan 1) should enumerate all legacy Vue pages/TS files field-by-field against current FE pages, then Plan 2 implements missing fields with fullstack additions where needed, builds a battle log HTML parser, and adds Vitest tests for calculated value accuracy.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- D-01: 필드 감사 + Vitest -- 레거시 GetFrontInfo.php 및 각 페이지별 API 응답 필드를 목록화하고, 현재 FrontInfoResponse 타입/컴포넌트와 1:1 대조. 누락 필드는 Vitest로 컴포넌트 렌더링 테스트 작성
- D-02: 전체 40+ 라우트 전수 감사 -- FE-01~04 대상뿐 아니라 auction, betting, diplomacy, vote, spy, tournament 등 모든 게임 페이지를 레거시와 비교
- D-03: 테스트 깊이: 계산값 정확성까지 -- 타입/필드 존재 검증 + 계산된 값(전투력, 보너스 적용 스탯 등)이 백엔드와 일치하는지까지 검증. mock data로 렌더링 검증
- D-04: 새 전용 컴포넌트 구현 -- 레거시 배틀 로그 포맷(color tag, 데미지값, 트리거 활성화 등)을 분석하고 매칭하는 전용 컴포넌트 새로 작성
- D-05: 레거시 색상 완전 재현 -- PHP color tag(`<R>`, `<C>` 등)를 파싱해서 동일한 색상으로 렌더링. 기존 formatLog.ts를 기반으로 확장
- D-06: 누락 필드 즉시 추가 -- 레거시에 있지만 현재 FE에 없는 필드 발견 시 이 Phase에서 바로 반영 (보류하지 않음)
- D-07: 백엔드까지 풀스택 추가 -- 백엔드 DTO에도 없는 필드(레거시에만 있는 경우) 발견 시 BE DTO -> FE 타입 -> 컴포넌트 전체 파이프라인 추가. 풀스택 패러티 달성
- D-08: 2플랜 분할: 감사/목록화 -> 구현/테스트

### Claude's Discretion
- 40+ 라우트 감사 시 우선순위 결정 (핵심 페이지 먼저 vs 알파벳순)
- 배틀 로그 컴포넌트의 세부 구조 (단일 파일 vs 서브컴포넌트 분리)
- Vitest 테스트 파일 구조 (페이지별 vs 기능별)
- 레거시에만 있고 opensamguk에서 의미 없는 필드(예: serverCnt) 스킵 여부 판단

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FE-01 | Verify game dashboard displays all information present in legacy UI | Legacy `GetFrontInfo.php` field inventory complete; `GameInfo.vue`, `PageFront.vue` structure analyzed; current `game-dashboard.tsx` gaps identified |
| FE-02 | Verify general detail page shows correct stats and calculated values | Legacy `GeneralBasicCard.vue` + `GeneralSupplementCard.vue` field inventory complete; calculated fields (injury reduction, win rate, kill ratio, dex levels) identified |
| FE-03 | Verify nation management page shows correct aggregated data | Legacy `NationBasicCard.vue` fields mapped; aggregation queries (population, crew, tech level) identified |
| FE-04 | Verify battle log display matches legacy format and content | Legacy `small_war_log.php` HTML template analyzed; `ActionLogger.php` battle log types documented; CSS classes mapped |
</phase_requirements>

## Legacy UI Inventory

### Legacy Pages (16 Vue + 26 TS pages)

| Legacy File | Current Route | Status |
|------------|---------------|--------|
| `PageFront.vue` | `(game)/page.tsx` (game-dashboard) | Exists -- needs field audit |
| `PageBattleCenter.vue` | `(game)/battle-center/page.tsx` | Exists -- needs log format audit |
| `PageBoard.vue` | `(game)/board/page.tsx` | Exists |
| `PageCachedMap.vue` | `(game)/map/page.tsx` | Exists |
| `PageChiefCenter.vue` | `(game)/chief/page.tsx` | Exists |
| `PageGlobalDiplomacy.vue` | `(game)/global-diplomacy/page.tsx` | Exists |
| `PageHistory.vue` | `(game)/history/page.tsx` | Exists |
| `PageInheritPoint.vue` | `(game)/inherit/page.tsx` | Exists |
| `PageJoin.vue` | `(lobby)/lobby/join/page.tsx` | Exists |
| `PageNPCControl.vue` | `(game)/npc-control/page.tsx` | Exists |
| `PageNationBetting.vue` | `(game)/nation-betting/page.tsx` | Exists |
| `PageNationGeneral.vue` | `(game)/nation-generals/page.tsx` | Exists |
| `PageNationStratFinan.vue` | `(game)/nation-finance/page.tsx` | Exists |
| `PageTroop.vue` | `(game)/troop/page.tsx` | Exists |
| `PageVote.vue` | `(game)/vote/page.tsx` | Exists |
| `PageAuction.vue` | `(game)/auction/page.tsx` | Exists |
| `battleCenter.ts` / `v_battleCenter.ts` | `(game)/battle/page.tsx` | Exists |
| `bestGeneral.ts` | `(game)/best-generals/page.tsx` | Exists |
| `betting.ts` | `(game)/betting/page.tsx` | Exists |
| `currentCity.ts` | `(game)/city/page.tsx` | Exists |
| `diplomacy.ts` | `(game)/diplomacy/page.tsx` | Exists |
| `hallOfFame.ts` | `(game)/hall-of-fame/page.tsx` | Exists |
| `msg.ts` | `(game)/messages/page.tsx` | Exists |
| `myPage.ts` | `(game)/my-page/page.tsx` | Exists |
| `battle_simulator.ts` | `(game)/battle-simulator/page.tsx` | Exists |
| `select_npc.ts` | `(lobby)/lobby/select-npc/page.tsx` | Exists |
| `v_processing.ts` | `(game)/processing/page.tsx` | Exists |
| `extKingdoms.ts` | `(game)/nations/page.tsx` | Exists |
| `extExpandCity.ts` | `(game)/nation-cities/page.tsx` | Exists |

**Additional current routes without legacy equivalent:** `spy`, `superior`, `personnel`, `internal-affairs`, `commands`, `general`, `generals/[id]`, `emperor`, `dynasty`, `traffic`, `tournament` -- these are opensamguk extensions or reorganized pages.

### FrontInfo API Field Comparison (FE-01)

#### GlobalInfo

| Legacy Field | Backend DTO | FE Type | FE Display | Gap |
|-------------|-------------|---------|------------|-----|
| scenarioText | YES | YES | YES | None |
| extendedGeneral | YES | YES | YES | None |
| isFiction | YES | YES | YES | None |
| npcMode | YES | YES | YES | None |
| joinMode | YES | YES | NO | Not displayed in dashboard |
| autorunUser | YES (as `autorunUser`) | NO (missing from GlobalInfo type) | YES (unsafe cast) | **Type missing, unsafe cast** |
| turnterm/turnTerm | YES | YES | YES | None |
| lastExecuted | YES | YES | YES | None |
| lastVoteID | YES | YES | YES | None |
| develCost | YES | YES | NO | Not displayed |
| noticeMsg | YES | YES | NO | Not displayed |
| onlineNations | YES | YES | YES | None |
| onlineUserCnt | YES | YES | YES | None |
| apiLimit | YES | YES | YES | None |
| auctionCount | YES | YES | YES | None |
| tournamentState | YES | YES | YES | None |
| tournamentType | YES | YES | YES | None |
| tournamentTime | YES | YES | YES | None |
| isTournamentActive | YES | YES | YES | None |
| isTournamentApplicationOpen | YES | YES | Indirectly | None |
| isBettingActive | YES | YES | Indirectly | None |
| isLocked | YES | YES | YES | None |
| genCount | YES | YES | YES | None |
| generalCntLimit | YES | YES | YES | None |
| serverCnt | YES | YES | YES | Could skip (opensamguk=single server) |
| lastVote | YES | YES | YES | None |
| year | YES | YES | YES | None |
| month | YES | YES | YES | None |
| startyear | YES | YES | NO | Not displayed directly |
| realtimeMode | YES | YES | Indirectly | None |

#### GeneralFrontInfo

| Legacy Field | Backend DTO | FE Type | FE Display | Gap |
|-------------|-------------|---------|------------|-----|
| no | YES | YES | YES | None |
| name | YES | YES | YES | None |
| picture | YES | YES | YES | None |
| imgsvr | YES | YES | YES | None |
| nation | YES | YES | YES | None |
| npc | YES | YES | YES | None |
| city | YES | YES | YES | None |
| troop | YES | YES | YES | None |
| officerLevel | YES | YES | YES | None |
| officerLevelText | YES | YES | YES | None |
| officerCity | YES | YES | NO | **Not displayed in dashboard** |
| permission | YES | YES | Indirectly | Used for access control |
| lbonus | YES | YES | YES | Displayed as +bonus |
| leadership/strength/intel | YES | YES | YES | None |
| leadershipExp/strengthExp/intelExp | YES | YES | YES | None |
| politics/politicsExp | YES | YES | YES | None (opensamguk extension) |
| charm/charmExp | YES | YES | YES | None (opensamguk extension) |
| experience | YES | YES | YES | None |
| dedication | YES | YES | YES | None |
| explevel | YES | YES | YES | None |
| dedlevel | YES | YES | YES | None |
| honorText | YES | YES | YES | None |
| dedLevelText | YES | YES | YES | None |
| bill | YES | YES | NO | **Not displayed (봉급)** |
| gold | YES | YES | YES | None |
| rice | YES | YES | YES | None |
| crew | YES | YES | YES | None |
| crewtype | YES | YES | YES | None |
| train | YES | YES | YES | None |
| atmos | YES | YES | YES | None |
| weapon/book/horse/item | YES | YES | YES | None |
| personal | YES | YES | YES | None |
| specialDomestic | YES | YES | YES | None |
| specialWar | YES | YES | YES | None |
| specage | YES | YES | NO | **Not displayed (특기 나이)** |
| specage2 | YES | YES | NO | **Not displayed** |
| age | YES | YES | YES | None |
| injury | YES | YES | YES | None |
| killturn | YES | YES | NO | **Not displayed (삭턴)** |
| belong | YES | YES | YES | None |
| betray | YES | YES | NO | **Not displayed** |
| blockState | YES | YES | NO | Not displayed (internal) |
| defenceTrain | YES | YES | YES | None |
| turntime | YES | YES | YES | None |
| recentWar | YES | YES | YES | None |
| commandPoints | YES | YES | YES | None |
| commandEndTime | YES | YES | YES | None |
| ownerName | YES | YES | NO | Not displayed |
| refreshScoreTotal | YES | YES | NO | **Not displayed (벌점)** |
| refreshScore | YES | YES | NO | **Not displayed** |
| autorunLimit | YES | YES | NO | Not displayed |
| reservedCommand | YES | YES | Indirectly | Shown in command panel |
| troopInfo | YES | YES | NO | **Not displayed in general card** |
| dex1-5 | YES | YES | YES | None |
| warnum/killnum/deathnum | YES | YES | YES | None |
| killcrew/deathcrew | YES | YES | YES | None |
| firenum | YES | YES | YES | None |
| autorun_limit (legacy) | YES (autorunLimit) | YES | NO | Not displayed |

#### NationFrontInfo -- Key Gaps

| Legacy Field | Displayed in Legacy | Displayed in Current | Gap |
|-------------|--------------------|--------------------|-----|
| type (name/pros/cons) | YES (NationBasicCard) | YES | None |
| topChiefs (12, 11) | YES | YES | None |
| population (cityCnt/now/max) | YES | YES | None |
| crew (generalCnt/now/max) | YES | YES | None |
| gold/rice | YES | YES | None |
| bill/taxRate | YES | YES | None |
| tech | YES (with tech level calc) | YES | **Tech level calculation may differ** |
| power | YES | YES | None |
| strategicCmdLimit | YES | YES | None |
| diplomaticLimit | YES | YES | None |
| impossibleStrategicCommand | YES (with tooltip) | YES | **Tooltip detail may be less** |
| prohibitScout | YES | YES | None |
| prohibitWar | YES | YES | None |

#### CityFrontInfo -- No significant gaps detected

All fields (pop, agri, comm, secu, def, wall, trade, officerList, trust, nationInfo) are present in both legacy and current.

### Calculated Values Needing Verification (FE-02, FE-03)

1. **Injury stat reduction**: Legacy `calcInjury()` reduces displayed stat by injury%. Current FE does NOT apply injury reduction to displayed stats.
2. **Win rate**: Legacy `GeneralSupplementCard` shows `(killnum / max(warnum, 1)) * 100`. Current battle-center `GeneralBasicCard` shows raw win/loss counts but NOT the rate.
3. **Kill ratio**: Legacy shows `(killcrew / max(deathcrew, 1)) * 100`. Not shown in current.
4. **Dex level display**: Legacy `formatDexLevel()` shows colored level names. Current shows raw numbers only.
5. **Tech level**: Legacy `convTechLevel()` and `isTechLimited()` with dynamic max based on startyear. Current nation card may not have this calculation.
6. **Next execute time**: Legacy calculates `(turnTime - lastExecuted) / 60000` minutes. Current dashboard does not show this.
7. **Leadership bonus**: Legacy shows `+lbonus` next to leadership stat with cyan color. Current general-basic-card (dashboard) does not show lbonus.

## Battle Log Format Analysis (FE-04)

### Legacy Battle Log Structure

The legacy battle log uses an HTML template (`small_war_log.php`) with CSS classes, NOT just color tags:

```html
<div class="small_war_log">
    <span class="me">
        <span class="name_plate">
            <span class="crew_type">{crewTypeShortName}</span>
            <span class="name_plate_cover">【<span class="name">{name}</span>】</span>
        </span>
        <span class="crew_plate">
            <span class="remain_crew">{remainCrew}</span>
            <span class="killed_plate">(<span class="killed_crew">{killedCrew}</span>)</span>
        </span>
    </span>
    <span class="war_type war_type_{attack|defense|siege}">{arrow}</span>
    <span class="you">
        <span class="crew_plate">...</span>
        <span class="name_plate">...</span>
    </span>
</div>
```

### CSS Color Mapping

| CSS Class | Color | Purpose |
|-----------|-------|---------|
| `.war_type_attack` | cyan | Attack arrow |
| `.war_type_defense` | magenta | Defense arrow |
| `.war_type_siege` | white | Siege arrow |
| `.name_plate` | font-size: 0.75em | Small text for name |
| `.name_plate_cover` | yellow | Name bracket highlight |
| `.crew_plate` | orangered | Crew numbers |

### Current Battle Log Handling

The current `formatLog.ts` handles simple color tags (`<R>`, `<C>`, etc.) but does NOT parse the `small_war_log` HTML structure. Battle logs rendered via `formatLog()` will show raw HTML instead of styled content.

**Two separate rendering paths needed:**
1. **Color tag logs** (general records, global records, history) -- already handled by `formatLog.ts`
2. **Battle result logs** (HTML template output) -- needs new parser/component that handles CSS-class-based HTML structure

### Color Tag Parity

| Tag | Legacy CSS | Current Color | Match |
|-----|-----------|---------------|-------|
| R | red | red | YES |
| B | blue | blue | YES |
| G | green | green | YES |
| M | magenta | magenta | YES |
| C | cyan | cyan | YES |
| L | limegreen | limegreen | YES |
| S | skyblue | skyblue | YES |
| O | orangered | orangered | YES |
| D | orangered | orangered | YES |
| Y | yellow | yellow | YES |
| W | white | white | YES |

The current `formatLog.ts` also handles `<b>`, `</b>` bold tags and compound tags like `<R1>` (color + small). The legacy does NOT have bold tag support -- this is an opensamguk extension. **No color tag gaps.**

## Backend DTO Gap Analysis

The backend `FrontInfoDtos.kt` already contains all fields that the legacy `GetFrontInfo.php` returns. No backend DTO additions needed for the main FrontInfo response.

**One gap found:** The `GlobalInfo` DTO in backend has `autorunUser` field missing from the response. The dashboard casts `global` to `Record<string, unknown>` to access it, confirming it exists in the API response but is not typed.

**Potential backend additions needed for other pages** will be discovered during the Plan 1 audit. The FrontInfo DTO itself is complete.

## Architecture Patterns

### Recommended Approach for Plan 1 (Audit)

Create a structured audit document for each page category:

```
audit/
  dashboard-fields.md     -- GameInfo, general/nation/city cards
  battle-log-format.md    -- small_war_log HTML analysis
  page-audit-matrix.md    -- all 40+ routes field comparison
```

### Recommended Approach for Plan 2 (Implementation)

```
Changes per gap type:
1. FE-only display gaps → update component, add Vitest test
2. FE type gaps → update types/index.ts + component + test
3. Backend DTO gaps → update DTO + FE type + component + test
4. Battle log → new component + formatBattleLog utility + test
```

### Battle Log Component Structure (Discretion: single file recommended)

```
frontend/src/components/game/battle-log-entry.tsx  -- single component
  - Parse small_war_log HTML structure
  - Apply CSS class-based coloring
  - Handle both HTML template logs and color-tag logs
  - Export as <BattleLogEntry message={string} />
```

### Vitest Test Structure (Discretion: feature-grouped recommended)

```
frontend/src/lib/formatLog.test.ts           -- color tag parser tests
frontend/src/lib/formatBattleLog.test.ts      -- battle log HTML parser tests
frontend/src/components/game/__tests__/
  general-basic-card.test.tsx   -- calculated value accuracy
  nation-basic-card.test.tsx    -- aggregation accuracy
  city-basic-card.test.tsx      -- field completeness
  battle-log-entry.test.tsx     -- battle log rendering
```

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTML sanitization | Custom regex sanitizer | `DOMParser` or `dangerouslySetInnerHTML` with known-safe template | Battle log HTML comes from server-rendered templates |
| Color tag parsing | New parser | Existing `formatLog.ts` | Already has full parity with legacy |
| Progress bars | Custom bar component | Existing `stat-bar.tsx` or `SammoBar` equivalent | Already exists in component library |

## Common Pitfalls

### Pitfall 1: Battle Log HTML vs Color Tags
**What goes wrong:** Treating all log messages as color-tag-only, when battle result/detail logs contain full HTML (`<div class="small_war_log">...`).
**Why it happens:** `formatLog.ts` only handles `<R>`, `<C>` etc. tags, not CSS-class-based HTML.
**How to avoid:** Check if message starts with `<div class="small_war_log"` and route to HTML renderer vs color-tag parser.
**Warning signs:** Battle logs showing raw HTML tags in the UI.

### Pitfall 2: Injury-Reduced Stats Not Shown
**What goes wrong:** Dashboard shows raw stat values without injury reduction, while legacy shows reduced values with colored text.
**Why it happens:** `calcInjury()` function exists in legacy but not called in current FE.
**How to avoid:** Implement `calcInjury(statName, general)` that returns `Math.floor(stat * (100 - injury) / 100)` and display with injury color.
**Warning signs:** General stats don't match what legacy shows for injured generals.

### Pitfall 3: autorunUser Field Type Safety
**What goes wrong:** `autorunUser` accessed via unsafe `as unknown as Record<string, unknown>` cast.
**Why it happens:** Field exists in backend response but not in FE `GlobalInfo` type definition.
**How to avoid:** Add `autorunUser?: number` to `GlobalInfo` interface in `types/index.ts`.
**Warning signs:** TypeScript errors or runtime `undefined` for autorun display.

### Pitfall 4: Vitest Environment for Component Tests
**What goes wrong:** Component rendering tests fail because vitest.config.ts uses `environment: 'node'`.
**Why it happens:** React components need `jsdom` or `happy-dom` for DOM APIs.
**How to avoid:** Add `// @vitest-environment jsdom` comment in component test files, or configure per-file environment.
**Warning signs:** `document is not defined` errors in component tests.

### Pitfall 5: Tech Level Calculation Mismatch
**What goes wrong:** Nation card shows wrong tech level because calculation depends on `gameConst` values (maxTechLevel, initialAllowedTechLevel, techLevelIncYear) that may not be available in FE.
**Why it happens:** Legacy injects `gameConstStore` via Vue provide/inject with full game constants. Current FE may not have all these constants.
**How to avoid:** Verify `game_const.json` includes tech level constants and that they're available in the nation card component.
**Warning signs:** Tech level shows raw float instead of "X등급" format.

## Code Examples

### Legacy calcInjury Pattern
```typescript
// Source: legacy-core/hwe/ts/utilGame/calcInjury.ts (inferred from GeneralBasicCard.vue)
function calcInjury(statName: string, general: GeneralFrontInfo): number {
    const baseStat = general[statName as keyof GeneralFrontInfo] as number;
    const injury = general.injury;
    return Math.floor(baseStat * (100 - injury) / 100);
}
```

### Legacy formatDexLevel Pattern
```typescript
// Source: legacy-core/hwe/ts/utilGame/formatDexLevel.ts (inferred from GeneralSupplementCard.vue)
// Returns { name: string, color: string } based on dex value thresholds
// Dex values displayed as (dex / 1000).toFixed(1) + "K"
```

### Battle Log Detection
```typescript
// Detect if log message is HTML-template battle log vs color-tag log
function isBattleLogHtml(message: string): boolean {
    return message.includes('class="small_war_log"');
}
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 3.2.4 |
| Config file | `frontend/vitest.config.ts` |
| Quick run command | `cd frontend && npx vitest run --reporter=verbose` |
| Full suite command | `cd frontend && npx vitest run` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FE-01 | Dashboard shows all legacy fields | unit | `cd frontend && npx vitest run src/components/game/__tests__/game-dashboard.test.tsx -x` | Wave 0 |
| FE-02 | General card calculated stats correct | unit | `cd frontend && npx vitest run src/components/game/__tests__/general-basic-card.test.tsx -x` | Wave 0 |
| FE-03 | Nation card aggregated data correct | unit | `cd frontend && npx vitest run src/components/game/__tests__/nation-basic-card.test.tsx -x` | Wave 0 |
| FE-04 | Battle log format matches legacy | unit | `cd frontend && npx vitest run src/lib/formatBattleLog.test.ts -x` | Wave 0 |

### Sampling Rate
- **Per task commit:** `cd frontend && npx vitest run --reporter=verbose`
- **Per wave merge:** `cd frontend && npx vitest run`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `frontend/src/components/game/__tests__/game-dashboard.test.tsx` -- covers FE-01
- [ ] `frontend/src/components/game/__tests__/general-basic-card.test.tsx` -- covers FE-02
- [ ] `frontend/src/components/game/__tests__/nation-basic-card.test.tsx` -- covers FE-03
- [ ] `frontend/src/lib/formatBattleLog.test.ts` -- covers FE-04
- [ ] Vitest environment: component tests need `jsdom` -- may need `@vitest-environment jsdom` pragma or config update

## Key Identified Gaps Summary

### Dashboard (FE-01) Gaps
1. `autorunUser` -- field missing from `GlobalInfo` type, accessed via unsafe cast
2. `joinMode` -- not displayed
3. `develCost` -- not displayed (but may not be user-facing)
4. `officerCity` -- not displayed in general section
5. `bill` (봉급) -- not displayed
6. `specage`/`specage2` -- not displayed (특기 개방 나이)
7. `killturn` (삭턴) -- not displayed
8. `refreshScoreTotal`/`refreshScore` (벌점) -- not displayed
9. `troopInfo` -- not displayed in general card (부대 상태)
10. `betray` -- not displayed

### General Detail (FE-02) Gaps
1. **calcInjury not applied** -- stats shown raw, not injury-reduced
2. **lbonus not shown** in dashboard general card (shown in legacy with cyan +N)
3. **Win rate / kill ratio** -- not calculated (legacy shows percentages)
4. **Dex level names with color** -- legacy shows grade names (e.g., "달인") with colors, current shows raw numbers
5. **Next execute time** -- legacy shows "N분 남음", current doesn't
6. **Age color** -- legacy colors age (green/yellow/red based on retirement year)

### Nation Management (FE-03) Gaps
1. **Tech level grade display** -- legacy shows "N등급" with color based on limit, current may just show raw number
2. **Impossible strategic command tooltip** -- legacy shows detailed tooltip with turns remaining

### Battle Log (FE-04) Gaps
1. **HTML template battle logs not parsed** -- `small_war_log` HTML structure needs dedicated renderer
2. **CSS class-based coloring** -- war_type_attack (cyan), war_type_defense (magenta), crew_plate (orangered) etc.
3. **Structured data extraction** -- attacker name, defender name, remaining crew, killed crew from HTML

## Open Questions

1. **GameConst availability in FE**
   - What we know: Legacy uses `gameConstStore` for tech level, retirement year, upgrade limit, dex level thresholds
   - What's unclear: Whether all these constants are available in the current FE via API or static data
   - Recommendation: Check `game_const.json` and gameApi for gameConst endpoint during Plan 1 audit

2. **Battle log backend format**
   - What we know: Legacy backend renders HTML template and stores result as string in DB
   - What's unclear: Whether the Kotlin backend stores the same HTML format or a different structure
   - Recommendation: Check actual battle log records in the Kotlin backend during Plan 1 audit

3. **40+ page audit depth**
   - What we know: 60+ current routes, 16 Vue pages + 26 TS pages in legacy
   - What's unclear: How deep each non-core page audit needs to go
   - Recommendation: Core pages (dashboard, battle, nation, general) get field-by-field audit; other pages get existence + key field check

## Sources

### Primary (HIGH confidence)
- `legacy-core/hwe/sammo/API/General/GetFrontInfo.php` -- Complete legacy FrontInfo API response structure
- `legacy-core/hwe/ts/PageFront.vue` -- Legacy main dashboard Vue component
- `legacy-core/hwe/ts/components/GameInfo.vue` -- Legacy global info display
- `legacy-core/hwe/ts/components/GeneralBasicCard.vue` -- Legacy general card with all fields
- `legacy-core/hwe/ts/components/NationBasicCard.vue` -- Legacy nation card with all fields
- `legacy-core/hwe/ts/components/CityBasicCard.vue` -- Legacy city card
- `legacy-core/hwe/ts/components/GeneralSupplementCard.vue` -- Legacy supplement card (win rate, dex levels)
- `legacy-core/hwe/templates/small_war_log.php` -- Battle log HTML template
- `legacy-core/hwe/scss/battleLog.scss` -- Battle log CSS
- `legacy-core/hwe/ts/utilGame/formatLog.ts` -- Legacy color tag parser
- `backend/game-app/src/main/kotlin/com/opensam/dto/FrontInfoDtos.kt` -- Backend DTO definitions
- `frontend/src/types/index.ts` -- Current FE type definitions
- `frontend/src/lib/formatLog.ts` -- Current color tag parser
- `frontend/src/components/game/game-dashboard.tsx` -- Current dashboard component

### Secondary (MEDIUM confidence)
- `legacy-core/hwe/sammo/ActionLogger.php` -- Battle log generation (lines 290-318)
- `frontend/vitest.config.ts` -- Test framework configuration

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- existing Vitest + React testing patterns well established
- Architecture: HIGH -- legacy code fully readable, current code well-structured
- Pitfalls: HIGH -- specific gaps identified from direct code comparison
- Battle log format: HIGH -- template and CSS fully documented from source

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable -- legacy code frozen, current code structure unlikely to change)
