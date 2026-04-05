# Phase 11: Frontend Display Parity - Full Audit

**Audited:** 2026-04-03
**Method:** Field-by-field comparison of legacy PHP/Vue source vs current Next.js/React components
**Source of truth:** `legacy-core/` PHP/Vue source code (not docs)

---

## Section 1: Dashboard / GlobalInfo (FE-01)

### GameInfo.vue vs game-dashboard.tsx Header

Legacy `GameInfo.vue` displays a grid of global information fields sourced from `GetFrontInfo.php -> generateGlobalInfo()`.

| # | Legacy Field | Legacy Display (GameInfo.vue) | BE DTO | FE Type (`GlobalInfo`) | FE Display (game-dashboard.tsx) | Gap Type |
|---|-------------|-------------------------------|--------|------------------------|--------------------------------|----------|
| 1 | scenarioText | Title + scenario name in cyan | YES | YES | YES (header + grid) | None |
| 2 | extendedGeneral | "확장"/"표준" | YES | YES (`extendedGeneral`) | YES | None |
| 3 | isFiction | "가상"/"사실" | YES | YES (`isFiction`) | YES | None |
| 4 | npcMode | "불가능"/"가능"/"선택 생성" | YES | YES (`npcMode`) | YES | None |
| 5 | autorunUser | "기타 설정: [mode]" via `<AutorunInfo>` | YES | **NO** (missing from `GlobalInfo`) | YES (unsafe cast `as unknown as Record`) | **type-missing** |
| 6 | turnterm | "N분 턴 서버" | YES | YES (`turnTerm`) | YES | None |
| 7 | year/month | "현재: N年 N月" | YES | YES | YES | None |
| 8 | onlineUserCnt | "전체 접속자 수: N명" | YES | YES | YES | None |
| 9 | apiLimit | "턴당 갱신횟수: N회" | YES | YES | YES | None |
| 10 | genCount | "등록 장수: 유저 N / N + NPC N명" | YES | YES | YES | None |
| 11 | isTournamentActive | Tournament status with link/color | YES | YES | YES | None |
| 12 | tournamentType | formatTournamentType(type) | YES | YES | YES | None |
| 13 | tournamentState | formatTournamentStep(state) | YES | YES | YES (partially -- step detail less) | **format-diff** |
| 14 | tournamentTime | Time substring display | YES | YES | YES | None |
| 15 | lastExecuted | "동작 시각: HH:MM" with locked color | YES | YES | YES | None |
| 16 | auctionCount | "N건 거래 진행중" link | YES | YES | YES | None |
| 17 | lastVote | "설문 진행 중: [title]" link | YES | YES | YES | None |
| 18 | isLocked | Color toggle on 동작 시각 | YES | YES | YES | None |
| 19 | joinMode | Not displayed in GameInfo.vue directly | YES | YES (`joinMode`) | NO | **display-only** |
| 20 | develCost | Not displayed in GameInfo.vue directly | YES | YES (`develCost`) | NO | **display-only** |
| 21 | noticeMsg | Not displayed in GameInfo.vue directly | YES | YES (`noticeMsg`) | NO | **display-only** |
| 22 | serverCnt | "N기" in title | YES | YES (`serverCnt`) | YES | None |
| 23 | startyear | Not displayed directly (used in tech calc) | YES | YES (`startyear`) | NO (used internally) | None |
| 24 | generalCntLimit | Max general count | YES | YES | YES | None |
| 25 | lastVoteID | Used for vote notification logic | YES | YES | YES (internal) | None |

### GameInfo.vue -- Tournament Step Detail

Legacy `GameInfo.vue` uses `formatTournamentStep()` which returns `{ availableJoin, state, nextText }` and renders:
- If `availableJoin`: cyan link to `b_tournament.php` with orange state text
- Else: cyan span with magenta state text
- Shows "다음경기/다음추첨" next text + time

Current `game-dashboard.tsx` shows simplified "진행중" text without step detail (state/nextText). The `formatTournamentStep()` utility exists in `game-utils.ts` but is not called from the dashboard.

**Gap:** `format-diff` -- tournament step state/nextText not shown in dashboard.

### GameInfo.vue -- autorunUser Unsafe Cast

Legacy passes `globalInfo.autorunUser` to `<AutorunInfo>` component directly.
Current dashboard accesses it via `(global as unknown as Record<string, unknown>).autorunUser` -- unsafe cast because `autorunUser` is missing from the `GlobalInfo` interface in `types/index.ts`.

**Gap:** `type-missing` -- add `autorunUser?: number` to `GlobalInfo` interface.

### PageFront.vue -- Online Nations / Users / Nation Notice

| # | Legacy Display | Current Display | Gap |
|---|---------------|----------------|-----|
| 1 | "접속중인 국가: [names]" (globalInfo.onlineNations) | YES (with color dots) | None |
| 2 | "【 접속자 】 [onlineGen]" (nationInfo.onlineGen) | YES | None |
| 3 | "【 국가방침 】" + nationNotice HTML | YES (dangerouslySetInnerHTML) | None |

### GameBottomBar.vue

Legacy renders a mobile bottom bar with: 외부 메뉴, 국가 메뉴, 빠른 이동, 갱신 button.
Current has mobile tab bar (지도/명령/상태/동향) + desktop refresh button. Different UX approach but functionally equivalent -- navigation is reorganized, not missing.

**Gap:** None (intentional redesign).

---

## Section 2: General Card (FE-02)

### GeneralBasicCard.vue vs general-basic-card.tsx

Legacy `GeneralBasicCard.vue` receives `general`, `troopInfo`, `nation`, `turnTerm`, `lastExecuted` props.

#### Header Row

| # | Legacy Field | Legacy Display | Current Display | Gap |
|---|-------------|---------------|----------------|-----|
| 1 | name | General name with nation color bg | YES | None |
| 2 | officerLevel + officerLevelText | "관직명" in name row | YES (`officerLevelText`) | None |
| 3 | officer_city | City name for levels 2-4: `formatCityName(general.officer_city)` | Shows `general.officerCity` as number (not resolved to name) | **format-diff** |
| 4 | generalTypeCall | 용장/명장/만능 etc | YES (`formatGeneralTypeCall`) | None |
| 5 | injuryInfo | 건강/경상/중상 with color | YES (`formatInjury`) | None |
| 6 | turntime | substring(11,19) time display | YES | None |

#### Stats with Injury Reduction (calcInjury)

Legacy applies `calcInjury("leadership", general)` which computes `Math.floor(baseStat * (100 - injury) / 100)` and displays the **reduced** value with injury color.

Current applies `calcInjury(general.leadership, general.injury)` which computes `Math.round((baseStat * (100 - injury)) / 100)`.

**Difference:** Legacy uses `Math.floor`, current uses `Math.round`. For example:
- leadership=80, injury=15: legacy = `Math.floor(80*85/100)` = 68, current = `Math.round(80*85/100)` = 68 (same)
- leadership=81, injury=15: legacy = `Math.floor(81*85/100)` = `Math.floor(68.85)` = 68, current = `Math.round(68.85)` = 69 (DIFFERS)

**Gap:** `calc-missing` -- `calcInjury` in `game-utils.ts` uses `Math.round` but legacy uses `Math.floor`. Need to change to `Math.floor` for parity.

| # | Stat | Legacy | Current | Gap |
|---|------|--------|---------|-----|
| 1 | 통솔 (leadership) | calcInjury + injury color + `+lbonus` in cyan | calcInjury + injury color + `+lbonus` in cyan | **calc-missing** (Math.round vs Math.floor) |
| 2 | 무력 (strength) | calcInjury + injury color | calcInjury + injury color | Same calc issue |
| 3 | 지력 (intel) | calcInjury + injury color | calcInjury + injury color | Same calc issue |
| 4 | 정치 (politics) | N/A (opensamguk extension) | calcInjury + injury color | opensamguk-only |
| 5 | 매력 (charm) | N/A (opensamguk extension) | calcInjury + injury color | opensamguk-only |

#### Leadership Bonus (lbonus)

Legacy: `<span v-if="general.lbonus > 0" style="color: cyan">+{{ general.lbonus }}</span>` -- shown next to leadership stat.

Current: `{bonus != null && bonus > 0 && (<span className="text-cyan-400 font-normal text-[10px]">+{bonus}</span>)}` -- shown via `StatCell` component.

**Gap:** None -- lbonus is displayed in current. Parity achieved.

#### Stat Experience Bars

Legacy uses `statUpThreshold = gameConstStore.value.gameConst.upgradeLimit` from game constants.
Current hardcodes `statUpThreshold = 100`.

**Gap:** `format-diff` -- `statUpThreshold` should come from game constants, not hardcoded. If `upgradeLimit` differs from 100, the exp bar percentage will be wrong.

#### Items and Equipment

| # | Field | Legacy | Current | Gap |
|---|-------|--------|---------|-----|
| 1 | horse | Name + tooltip(info) | Name only (no tooltip) | **format-diff** |
| 2 | weapon | Name + tooltip(info) | Name only (no tooltip) | **format-diff** |
| 3 | book | Name + tooltip(info) | Name only (no tooltip) | **format-diff** |
| 4 | item | Name + tooltip(info) | Name only (no tooltip) | **format-diff** |
| 5 | crewtype | Icon + name + tooltip(info) | Icon + name (no tooltip) | **format-diff** |
| 6 | personal | Name + tooltip(info) | Name only | **format-diff** |
| 7 | specialDomestic | Name/age + tooltip(info) | Name/age (no tooltip) | **format-diff** |
| 8 | specialWar | Name/age + tooltip(info) | Name/age (no tooltip) | **format-diff** |

Note: Legacy resolves item/crewtype/personal/special codes via `gameConstStore.value.iActionInfo` which provides `{ value, name, info }` objects with tooltip descriptions. Current only shows the raw code or name without info tooltips.

#### Age Coloring

Legacy:
```javascript
ageColor.value = (() => {
    const age = general.age;
    const retirementYear = gameConstStore.value.gameConst.retirementYear;
    if (age < retirementYear * 0.75) return "limegreen";
    if (age < retirementYear) return "yellow";
    return "red";
})();
```

Current `ageColor()` in `game-utils.ts`:
```typescript
export function ageColor(age: number, retirementYear: number = 80): string {
    if (age < retirementYear * 0.75) return 'limegreen';
    if (age < retirementYear) return 'yellow';
    return 'red';
}
```

**Gap:** Minor -- current defaults `retirementYear=80` instead of reading from `gameConst.retirementYear`. If game config uses a different retirement year, colors will be wrong. The function signature supports it but the caller in `general-basic-card.tsx` does not pass it.

#### Next Execute Time

Legacy:
```javascript
let turnTime = parseTime(general.value.turntime);
if (turnTime.getTime() < props.lastExecuted.getTime()) {
    turnTime = addMinutes(turnTime, props.turnTerm);
}
nextExecuteMinute.value = Math.floor(clamp((turnTime.getTime() - props.lastExecuted.getTime()) / 60000, 0, 999));
// Displayed as: {{ nextExecuteMinute }}분 남음
```

Current:
```typescript
let effective = turnTime;
if (effective < lastExecTime) {
    effective = effective + turnTerm * 60000;
}
const minutes = Math.max(0, Math.min(999, Math.floor((effective - lastExecTime) / 60000)));
nextExecText = `${minutes}분`;
```

**Gap:** Display text format: legacy shows "N분 남음", current shows "N분". Minor difference.

#### Additional Fields in Legacy GeneralBasicCard

| # | Field | Legacy Display | Current Display | Gap |
|---|-------|---------------|----------------|-----|
| 1 | gold | `general.gold.toLocaleString()` | YES | None |
| 2 | rice | `general.rice.toLocaleString()` | YES | None |
| 3 | crew | `general.crew.toLocaleString()` | YES | None |
| 4 | train | `general.train` | YES | None |
| 5 | atmos | `general.atmos` | YES | None |
| 6 | explevel | Level number + exp bar | YES | None |
| 7 | age | "N세" with ageColor | YES | None |
| 8 | defenceTrain | "수비 안함"(red) / "수비 함(훈사N)"(green) | YES (similar) | None |
| 9 | killturn | "N 턴" | YES (`general.killturn ?? '-'` + " 턴") | None |
| 10 | nextExecuteMinute | "N분 남음" | "N분" | **format-diff** |
| 11 | troopInfo | Name with leader city/command check | YES (name only, no leader city/command check) | **format-diff** |
| 12 | refreshScoreTotal | `formatRefreshScore(total) total점(score)` | YES | None |
| 13 | specage/specage2 | Shown when no special: "N세" | YES (via special display) | None |

#### Troop Info Detail

Legacy shows troop with strikethrough if leader has non-집합 command, orange if leader is in different city:
```html
<s v-if="troopInfo.leader.reservedCommand && troopInfo.leader.reservedCommand[0].action != 'che_집합'" style="color: gray">
<span v-else-if="troopInfo.leader.city == general.city">{{ troopInfo.name }}</span>
<span v-else style="color: orange">{{ troopInfo.name }}({{ formatCityName(troopInfo.leader, gameConstStore) }})</span>
```

Current shows only `general.troopInfo.name` without leader city or command status.

**Gap:** `format-diff` -- troop display missing leader city/command status styling.

### GeneralSupplementCard.vue vs general-supplement-card.tsx

| # | Field | Legacy | Current | Gap |
|---|-------|--------|---------|-----|
| 1 | 명성 (honor) | `formatHonor(exp) (exp)` | YES | None |
| 2 | 계급 (dedication) | `dedLevelText (dedication)` | YES | None |
| 3 | 봉급 (bill) | `general.bill.toLocaleString()` | YES | None |
| 4 | 전투 (warnum) | YES | YES | None |
| 5 | 계략 (firenum) | YES | YES | None |
| 6 | 사관 (belong) | "N년차" | YES | None |
| 7 | 승률 (win rate) | `((killnum / max(warnum,1)) * 100).toFixed(2) %` | YES (`.toFixed(2)`) | None |
| 8 | 승리 (killnum) | YES | YES | None |
| 9 | 패배 (deathnum) | `general.deathnum.toPrecision()` | YES (`.toLocaleString()`) | None |
| 10 | 살상률 (kill ratio) | `((killcrew / max(deathcrew,1)) * 100).toFixed(2) %` | YES (`.toFixed(2)`) | None |
| 11 | 사살 (killcrew) | YES | YES | None |
| 12 | 피살 (deathcrew) | YES | YES | None |

#### Kill Ratio Formula Difference

Legacy supplement card: `(general.killcrew / Math.max(general.deathcrew, 1)) * 100`
Current general-basic-card (merged view): `totalCrewCasualties = killcrew + deathcrew; killCrewRate = killcrew / totalCrewCasualties * 100`

**Gap:** `calc-missing` -- current uses `killcrew / (killcrew + deathcrew)` but legacy uses `killcrew / max(deathcrew, 1)`. Different formula producing different results. Need to match legacy formula.

#### Dexterity Display

| Aspect | Legacy | Current | Gap |
|--------|--------|---------|-----|
| Label | 보/궁/기/귀/차 | YES | None |
| Grade name | `formatDexLevel(dex).name` with color | YES | None |
| Value | `(dex / 1000).toFixed(1)K` | YES | None |
| Bar | `SammoBar percent=(dex/1000000)*100` | YES | None |

**Gap:** None -- dex display matches legacy.

#### Reserved Commands

Legacy shows up to 5 reserved commands with `turn.brief`.
Current also shows up to 5 with `cmd.brief`.

**Gap:** None.

---

## Section 3: Nation Card (FE-03)

### NationBasicCard.vue vs nation-basic-card.tsx

| # | Field | Legacy Display | Current Display | Gap |
|---|-------|---------------|----------------|-----|
| 1 | name | Nation color bg + name | YES | None |
| 2 | type (name/pros/cons) | "성향 [name] (pros cons)" | YES | None |
| 3 | topChiefs[12] / topChiefs[11] | Officer level text for levels 12, 11 | Uses levels 20, 19 | **format-diff** |
| 4 | population.now/max | YES | YES | None |
| 5 | crew.now/max | YES | YES | None |
| 6 | gold | YES | YES | None |
| 7 | rice | YES | YES | None |
| 8 | bill (지급률) | "N%" | YES | None |
| 9 | taxRate (세율) | "N%" | YES | None |
| 10 | population.cityCnt (속령) | YES | YES | None |
| 11 | crew.generalCnt (장수) | YES | YES | None |
| 12 | power (국력) | YES | YES | None |
| 13 | tech (기술력) | "N등급 / tech" with color | YES | None |
| 14 | strategicCmdLimit | "N턴"(red) / "가능"(yellow/green) | YES | None |
| 15 | diplomaticLimit | "N턴"(red) / "가능"(green) | YES | None |
| 16 | prohibitScout | "금지"(red) / "허가"(green) | YES | None |
| 17 | prohibitWar | "금지"(red) / "허가"(green) | YES | None |

#### Top Chiefs Officer Level Mismatch

Legacy uses `topChiefs[12]` and `topChiefs[11]` (legacy officer system).
Current uses `topChiefs[20]` and `topChiefs[19]` (opensamguk extended officer system).

This is an **intentional** opensamguk change from the legacy 12-level to 20-level officer system. Not a parity gap.

#### Tech Level Calculation

Legacy:
```javascript
const initialAllowedTechLevel = gameConstStore.value.gameConst.initialAllowedTechLevel;
const techLevelIncYear = gameConstStore.value.gameConst.techLevelIncYear;
maxTechLevel.value = getMaxRelativeTechLevel(startyear, year, gameConstStore.value.gameConst.maxTechLevel, initialAllowed, techIncYear);
currentTechLevel.value = convTechLevel(nation.tech, maxTechLevel.value);
```

Current:
```typescript
const maxTechLevel = 10;       // HARDCODED
const initialAllowed = 1;       // HARDCODED
const techIncYear = 5;          // HARDCODED
const currentTechLevel = convTechLevel(nation.tech, maxTechLevel);
```

**Gap:** `calc-missing` -- tech level constants are hardcoded in `nation-basic-card.tsx` instead of read from game constants. If game config differs, tech level display will be wrong.

#### Impossible Strategic Command Tooltip

Legacy:
```javascript
for (const [cmdName, turnCnt] of nation.impossibleStrategicCommand) {
    const [year, month] = parseYearMonth(yearMonth + turnCnt);
    texts.push(`${cmdName}: ${turnCnt}턴 뒤(${year}년 ${month}월부터)`);
}
// Rendered as v-b-tooltip.hover with HTML content
```

Current:
```typescript
const impossibleStrategicCommandText = `불가 전략: ${nation.impossibleStrategicCommand.join(', ')}`;
// Rendered as title attribute
```

**Gap:** `format-diff` -- current joins command names as simple string instead of formatting each with turn count and target year/month. Tooltip content is less detailed.

#### Strategic Command "가능" Color

Legacy: `<span v-else style="color: yellow">가능</span>` (yellow when impossibleStrategicCommandText exists, green when not)
Current: `<span style={{ color: 'limegreen' }}>가능</span>` (always limegreen)

**Gap:** `format-diff` -- legacy shows yellow "가능" when there are some impossible commands (but overall limit is 0), current always shows limegreen.

---

## Section 4: City Card (FE-03 supplement)

### CityBasicCard.vue vs city-basic-card.tsx

| # | Field | Legacy Display | Current Display | Gap |
|---|-------|---------------|----------------|-----|
| 1 | Region + Level + Name | "【region | level】 name" | YES | None |
| 2 | Nation info | "지배 국가 【name】" / "공 백 지" | YES | None |
| 3 | pop (주민) | bar + "now / max" | YES | None |
| 4 | trust (민심) | bar + value | YES | None |
| 5 | agri (농업) | bar + "now / max" | YES | None |
| 6 | comm (상업) | bar + "now / max" | YES | None |
| 7 | secu (치안) | bar + "now / max" | YES | None |
| 8 | def (수비) | bar + "now / max" | YES | None |
| 9 | wall (성벽) | bar + "now / max" | YES | None |
| 10 | trade (시세) | bar + "N%" / "상인 없음" | YES | None |
| 11 | officerList[4] (태수) | Name with NPC color | YES | None |
| 12 | officerList[3] (군사) | Name with NPC color | YES | None |
| 13 | officerList[2] (종사) | Name with NPC color | YES | None |

**No significant gaps detected.** City card matches legacy field-by-field. RESEARCH.md assessment confirmed.

---

## Section 5: Gap Summary Table

| # | Field | Page | Gap Type | FE File | Legacy File | Action Needed |
|---|-------|------|----------|---------|-------------|---------------|
| 1 | autorunUser | Dashboard | type-missing | `types/index.ts`, `game-dashboard.tsx` | `GameInfo.vue` | Add `autorunUser?: number` to `GlobalInfo` interface; remove unsafe cast in dashboard |
| 2 | calcInjury rounding | General Card | calc-missing | `game-utils.ts` | `GeneralBasicCard.vue` (via `calcInjury.ts`) | Change `Math.round` to `Math.floor` in `calcInjury()` |
| 3 | kill ratio formula | General Card | calc-missing | `general-basic-card.tsx` | `GeneralSupplementCard.vue` | Change from `killcrew/(killcrew+deathcrew)` to `killcrew/max(deathcrew,1)` |
| 4 | tech level constants | Nation Card | calc-missing | `nation-basic-card.tsx` | `NationBasicCard.vue` | Read `maxTechLevel`, `initialAllowedTechLevel`, `techLevelIncYear` from game constants instead of hardcoding |
| 5 | impossibleStrategicCommand tooltip | Nation Card | format-diff | `nation-basic-card.tsx` | `NationBasicCard.vue` | Format each command with turn count and target year/month |
| 6 | strategicCmd "가능" color | Nation Card | format-diff | `nation-basic-card.tsx` | `NationBasicCard.vue` | Show yellow when impossibleStrategicCommand list non-empty but strategicCmdLimit=0 |
| 7 | statUpThreshold hardcoded | General Card | calc-missing | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Read `upgradeLimit` from game constants |
| 8 | item/equipment tooltips | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Add tooltip info for horse/weapon/book/item/crewtype/personal/special from gameConst iActionInfo |
| 9 | troopInfo leader detail | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Add strikethrough for non-집합 command, orange for different city, show leader city name |
| 10 | nextExecText "남음" suffix | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Change from "N분" to "N분 남음" |
| 11 | officerCity name resolution | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Resolve city ID to name via `formatCityName()` instead of showing raw number |
| 12 | tournamentStep detail | Dashboard | format-diff | `game-dashboard.tsx` | `GameInfo.vue` | Use `formatTournamentStep()` to show state/nextText instead of just "진행중" |
| 13 | joinMode display | Dashboard | display-only | `game-dashboard.tsx` | N/A (not shown in legacy GameInfo.vue either) | Low priority -- field exists in type but legacy also doesn't display prominently |
| 14 | develCost display | Dashboard | display-only | `game-dashboard.tsx` | N/A | Low priority -- internal field |
| 15 | ageColor retirementYear | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Pass `retirementYear` from game constants to `ageColor()` instead of default 80 |

---

## Section 6: Non-Core Page Audit

Audit of all legacy Vue pages and TS pages against current Next.js routes. Pages are grouped by priority: core game pages first, then support pages, then remaining.

### Core Game Pages

#### 1. PageBattleCenter.vue -> `(game)/battle-center/page.tsx`

**Status:** Matched (route exists)

Legacy displays:
- General selector (dropdown with NPC color, officer level markers, turntime)
- `GeneralBasicCard` + `GeneralSupplementCard` for selected general
- General history (장수 열전) via `v-html` formatLog
- Battle detail log (전투 기록) via `v-html` formatLog
- Battle result log (전투 결과) via `v-html` formatLog

**Key gap:** Battle detail/result logs use `v-html` which renders `small_war_log` HTML templates. Current route likely uses `formatLog()` which cannot parse the HTML template structure. See Section 7.

#### 2. PageTroop.vue -> `(game)/troop/page.tsx`

**Status:** Matched (route exists)

Legacy displays:
- Troop list with: troopName, city name (from gameConstStore), turnTime, leader icon/name
- Reserved command briefs for troop leader
- Member list with: name, city difference highlighting (orange for different city), leader bold
- Troop actions: join/leave/create/dismiss buttons

**Key fields:** All troop-related fields (troopName, leader, members, city, reservedCommand) are structural data, not FrontInfo fields. This page uses a separate API endpoint. No FrontInfo field gaps expected -- display parity depends on the troop API implementation.

#### 3. PageGlobalDiplomacy.vue -> `(game)/global-diplomacy/page.tsx`

**Status:** Matched (route exists)

Legacy displays:
- Diplomacy matrix table: nations x nations with state symbols (불가침 @, 통상 ., 선포 ▲, 교전 ★)
- Nation color headers
- My nation rows highlighted (#660000 background)
- Legend footer

**Key fields:** diplomacyList matrix, nation colors/names. Uses separate diplomacy API. No FrontInfo field gaps.

#### 4. PageNationGeneral.vue -> `(game)/nation-generals/page.tsx`

**Status:** Matched (route exists)

Legacy displays:
- GeneralList component with all nation generals
- Shows GeneralBasicCard + GeneralSupplementCard per general on click
- Troop list overlay

**Key fields:** Uses nation general list API. Same general fields as Section 2. No additional FrontInfo gaps beyond those already documented.

#### 5. PageChiefCenter.vue -> `(game)/chief/page.tsx`

**Status:** Matched (route exists)

Legacy displays:
- Chief command slots for officer levels [12, 10, 8, 6, 11, 9, 7, 5] (legacy system)
- Turn index grid with max turns
- Reserved command editing for own chief slot
- Top officer display with turn info

**Key fields:** Chief-specific data (officer turns, commands). Uses separate chief API. Opensamguk uses extended 20-level officer system, so slot layout differs intentionally.

#### 6. PageNationStratFinan.vue -> `(game)/nation-finance/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Nation strategic/financial management page with bill/tax controls.

**Key fields:** Nation bill/taxRate (already in NationFrontInfo), strategic command lists. No additional gaps.

### Support Pages

#### 7. PageHistory.vue -> `(game)/history/page.tsx`

**Status:** Matched (route exists)

Legacy displays: World history timeline with formatLog-rendered entries.

**Key fields:** history records -- same as dashboard record zone. No additional gaps.

#### 8. PageBoard.vue -> `(game)/board/page.tsx`

**Status:** Matched (route exists)

Legacy displays: In-game bulletin board with posts.

**Key fields:** Board API (separate from FrontInfo). No FrontInfo gaps.

#### 9. PageVote.vue -> `(game)/vote/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Vote/survey creation and participation UI.

**Key fields:** Vote API. No FrontInfo gaps.

#### 10. PageAuction.vue -> `(game)/auction/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Item auction marketplace.

**Key fields:** Auction API. No FrontInfo gaps.

#### 11. PageNationBetting.vue -> `(game)/nation-betting/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Nation betting UI for tournament.

**Key fields:** Betting/tournament API. No FrontInfo gaps.

#### 12. PageInheritPoint.vue -> `(game)/inherit/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Inheritance point management.

**Key fields:** Inherit API. No FrontInfo gaps.

#### 13. PageNPCControl.vue -> `(game)/npc-control/page.tsx`

**Status:** Matched (route exists)

Legacy displays: NPC general management for officers.

**Key fields:** NPC control API. No FrontInfo gaps.

#### 14. PageCachedMap.vue -> `(game)/map/page.tsx`

**Status:** Matched (route exists)

Legacy displays: Cached static map view.

**Key fields:** Map data API. No FrontInfo gaps.

#### 15. PageJoin.vue -> `(lobby)/lobby/join/page.tsx`

**Status:** Matched (route exists, in lobby group)

Legacy displays: General creation/join form.

**Key fields:** Join API. No FrontInfo gaps.

### TS Pages (Legacy jQuery/Vanilla Pages)

#### 16. battleCenter.ts -> `(game)/battle/page.tsx`

**Status:** Matched

Legacy: Battle center detail page (alternative to PageBattleCenter.vue).
Same battle log rendering concern as #1 above.

#### 17. bestGeneral.ts -> `(game)/best-generals/page.tsx`

**Status:** Matched

Legacy: Best generals ranking page. No FrontInfo gaps.

#### 18. betting.ts -> `(game)/betting/page.tsx`

**Status:** Matched

Legacy: Individual betting page. No FrontInfo gaps.

#### 19. currentCity.ts -> `(game)/city/page.tsx`

**Status:** Matched

Legacy: Current city detail view. Uses city data from separate API.

#### 20. diplomacy.ts -> `(game)/diplomacy/page.tsx`

**Status:** Matched

Legacy: Nation-to-nation diplomacy actions page. No FrontInfo gaps.

#### 21. hallOfFame.ts -> `(game)/hall-of-fame/page.tsx`

**Status:** Matched

Legacy: Hall of fame / past winners. No FrontInfo gaps.

#### 22. msg.ts -> `(game)/messages/page.tsx`

**Status:** Matched

Legacy: Private/national/diplomatic messaging. No FrontInfo gaps.

#### 23. myPage.ts -> `(game)/my-page/page.tsx`

**Status:** Matched

Legacy: Player profile page. No FrontInfo gaps.

#### 24. battle_simulator.ts -> `(game)/battle-simulator/page.tsx`

**Status:** Matched

Legacy: Battle simulation tool. No FrontInfo gaps.

#### 25. select_npc.ts -> `(lobby)/lobby/select-npc/page.tsx`

**Status:** Matched (in lobby group)

Legacy: NPC selection for joining. No FrontInfo gaps.

#### 26. v_processing.ts -> `(game)/processing/page.tsx`

**Status:** Matched

Legacy: Turn processing status page. No FrontInfo gaps.

#### 27. extKingdoms.ts -> `(game)/nations/page.tsx`

**Status:** Matched

Legacy: All nations overview page. No FrontInfo gaps.

#### 28. extExpandCity.ts -> `(game)/nation-cities/page.tsx`

**Status:** Matched

Legacy: Nation cities expansion view. No FrontInfo gaps.

### Opensamguk-Only Pages (no legacy equivalent)

| # | Current Route | Purpose | Status |
|---|--------------|---------|--------|
| 29 | `(game)/spy/page.tsx` | Spy system | opensamguk-only, skip |
| 30 | `(game)/superior/page.tsx` | Superior officer view | opensamguk-only, skip |
| 31 | `(game)/personnel/page.tsx` | Personnel management | opensamguk-only, skip |
| 32 | `(game)/internal-affairs/page.tsx` | Internal affairs panel | opensamguk-only, skip |
| 33 | `(game)/commands/page.tsx` | Command reference | opensamguk-only, skip |
| 34 | `(game)/general/page.tsx` | General detail | opensamguk-only, skip |
| 35 | `(game)/generals/[id]/page.tsx` | General by ID | opensamguk-only, skip |
| 36 | `(game)/generals/page.tsx` | All generals list | opensamguk-only, skip |
| 37 | `(game)/emperor/page.tsx` | Emperor system | opensamguk-only, skip |
| 38 | `(game)/dynasty/page.tsx` | Dynasty history | opensamguk-only, skip |
| 39 | `(game)/traffic/page.tsx` | Traffic/route info | opensamguk-only, skip |
| 40 | `(game)/tournament/page.tsx` | Tournament view | opensamguk extension |
| 41 | `(game)/nation/page.tsx` | Nation detail | opensamguk-only, skip |
| 42 | `(game)/npc-list/page.tsx` | NPC list | opensamguk-only, skip |

### Non-Core Page Audit Summary

All 28 legacy pages (16 Vue + 12 TS) have corresponding routes in the current frontend. No missing pages. The primary cross-cutting gap is **battle log HTML rendering** (affects PageBattleCenter, battleCenter.ts, and the dashboard record zone).

---

## Section 7: Battle Log Format Analysis (FE-04)

### 1. HTML Template Structure

Source: `legacy-core/hwe/templates/small_war_log.php`

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
    <span class="war_type war_type_{attack|defense|siege}">{arrow: -> or <-}</span>
    <span class="you">
        <span class="crew_plate">
            <span class="remain_crew">{remainCrew}</span>
            <span class="killed_plate">(<span class="killed_crew">{killedCrew}</span>)</span>
        </span>
        <span class="name_plate">
            <span class="crew_type">{crewTypeShortName}</span>
            <span class="name_plate_cover">【<span class="name">{name}</span>】</span>
        </span>
    </span>
</div>
```

Key structural observations:
- `.me` (attacker) has name_plate BEFORE crew_plate
- `.you` (defender) has crew_plate BEFORE name_plate (mirrored layout)
- War type determines arrow direction and color

### 2. CSS Class-to-Color Mapping

Source: `legacy-core/hwe/scss/battleLog.scss`

| CSS Class | Property | Value | Purpose |
|-----------|----------|-------|---------|
| `.small_war_log` | display | inline-block | Container |
| `.war_type_attack` | color | cyan | Attack arrow (→) |
| `.war_type_defense` | color | magenta | Defense arrow (←) |
| `.war_type_siege` | color | white | Siege arrow (→) |
| `.name_plate` | font-size | 0.75em | Smaller text for general name |
| `.name_plate_cover` | color | yellow | Name bracket 【name】 |
| `.crew_plate` | color | orangered | Crew numbers |
| `.crew_plate` | font-size | 90% | Slightly smaller crew text |
| `.hidden_but_copyable` | color | rgba(0,0,0,0); font-size: 0 | Hidden text for copy-paste |

### 3. Battle Log Generation (ActionLogger.php lines 280-318)

Source: `legacy-core/hwe/sammo/ActionLogger.php`

```php
$render_me = [
    'crewtype' => $me->getCrewTypeShortName(),
    'name' => $me->getName(),
    'remain_crew' => $me->getHP(),
    'killed_crew' => -$me->getDeadCurrentBattle()
];
$render_oppose = [
    'crewtype' => $oppose->getCrewTypeShortName(),
    'name' => $oppose->getName(),
    'remain_crew' => $oppose->getHP(),
    'killed_crew' => -$oppose->getDeadCurrentBattle()
];

// War type determination:
if (!$me->isAttacker()) {
    $warType = 'defense';     // defense arrow <-
    $warTypeStr = '←';
} else if ($oppose instanceof WarUnitCity) {
    $warType = 'siege';       // siege arrow ->
    $warTypeStr = '→';
} else {
    $warType = 'attack';      // attack arrow ->
    $warTypeStr = '→';
}

// Rendered to single-line HTML string (newlines stripped)
$res = str_replace(["\r\n", "\r", "\n"], '', $templates->render('small_war_log', [...]));

// Pushed to: battleResultLog, battleDetailLog, actionLog
```

### 4. Detection Heuristic

Battle log HTML can be distinguished from color tag logs by checking for the CSS class:

```typescript
function isBattleLogHtml(message: string): boolean {
    return message.includes('class="small_war_log"');
}
```

This is reliable because:
- Only `ActionLogger::pushSmallBattleLog()` generates `small_war_log` HTML
- All other log entries use color tags (`<R>`, `<C>`, etc.)
- The class name `small_war_log` is unique to battle templates

### 5. Two Rendering Paths

**Path A: Color Tag Logs (existing)**
- Used for: general records, global records, world history, non-battle action logs
- Handler: `formatLog()` in `frontend/src/lib/formatLog.ts`
- Handles: `<R>`, `<C>`, `<G>`, `<M>`, `<B>`, `<L>`, `<S>`, `<O>`, `<D>`, `<Y>`, `<W>` color tags
- Also handles: `<b>`, `</b>` bold, `<1>` small text, compound tags like `<R1>`
- Status: **Complete -- no changes needed**

**Path B: HTML Template Battle Logs (NEW)**
- Used for: battle result/detail logs containing `small_war_log` HTML
- Handler: New `BattleLogEntry` component needed
- Must parse: `.small_war_log` div with `.me`, `.you`, `.war_type`, `.name_plate`, `.crew_plate` spans
- Must apply: CSS class-to-color mapping (cyan/magenta/white/yellow/orangered)
- Status: **Not implemented -- new component needed**

### 6. Wiring Point

**Primary wiring point:** `record-zone.tsx` line 42 calls `formatLog(message)` for all record entries.

This must be updated to:
1. Check `isBattleLogHtml(message)` first
2. If true: route to `BattleLogEntry` component (new, renders HTML structure with CSS colors)
3. If false: use existing `formatLog(message)` (color tag parser)

**Secondary wiring points:**
- `game-dashboard.tsx` lines 388-422: Record zone in dashboard also uses `formatLog(r.message)` directly
- `(game)/battle-center/page.tsx`: Battle center page renders battle detail/result logs
- `(game)/battle/page.tsx`: Battle page also renders battle logs

All these locations need the same `isBattleLogHtml` check routing.

---

## Section 8: Updated Gap Summary

Combined gap table from all sections (Section 5 core gaps + Section 6/7 new gaps):

| # | Field | Page | Gap Type | FE File | Legacy File | Action Needed |
|---|-------|------|----------|---------|-------------|---------------|
| 1 | autorunUser | Dashboard | type-missing | `types/index.ts`, `game-dashboard.tsx` | `GameInfo.vue` | Add `autorunUser?: number` to `GlobalInfo` interface; remove unsafe cast |
| 2 | calcInjury rounding | General Card | calc-missing | `game-utils.ts` | `GeneralBasicCard.vue` | Change `Math.round` to `Math.floor` in `calcInjury()` |
| 3 | kill ratio formula | General Card | calc-missing | `general-basic-card.tsx` | `GeneralSupplementCard.vue` | Change from `killcrew/(killcrew+deathcrew)` to `killcrew/max(deathcrew,1)` |
| 4 | tech level constants | Nation Card | calc-missing | `nation-basic-card.tsx` | `NationBasicCard.vue` | Read from game constants instead of hardcoding |
| 5 | impossibleStrategicCommand tooltip | Nation Card | format-diff | `nation-basic-card.tsx` | `NationBasicCard.vue` | Format each command with turn count and target year/month |
| 6 | strategicCmd "가능" color | Nation Card | format-diff | `nation-basic-card.tsx` | `NationBasicCard.vue` | Show yellow when impossible list non-empty but limit=0 |
| 7 | statUpThreshold hardcoded | General Card | calc-missing | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Read `upgradeLimit` from game constants |
| 8 | item/equipment tooltips | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Add tooltip info from gameConst iActionInfo |
| 9 | troopInfo leader detail | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Add strikethrough/orange for leader status |
| 10 | nextExecText "남음" suffix | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Change "N분" to "N분 남음" |
| 11 | officerCity name resolution | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Resolve city ID to name |
| 12 | tournamentStep detail | Dashboard | format-diff | `game-dashboard.tsx` | `GameInfo.vue` | Use `formatTournamentStep()` for state/nextText |
| 13 | joinMode display | Dashboard | display-only | `game-dashboard.tsx` | N/A | Low priority -- not shown in legacy either |
| 14 | develCost display | Dashboard | display-only | `game-dashboard.tsx` | N/A | Low priority -- internal field |
| 15 | ageColor retirementYear | General Card | format-diff | `general-basic-card.tsx` | `GeneralBasicCard.vue` | Pass retirementYear from game constants |
| 16 | battle log HTML renderer | Record Zone / Battle pages | component-new | `record-zone.tsx`, `game-dashboard.tsx` | `small_war_log.php`, `battleLog.scss` | Create `BattleLogEntry` component; add `isBattleLogHtml()` detection; wire into record rendering |
| 17 | battle log CSS colors | Battle Log | component-new | (new) `battle-log-entry.tsx` | `battleLog.scss` | Implement 6 CSS class-to-color mappings (cyan/magenta/white/yellow/orangered/0.75em) |
| 18 | battle detail wiring | Battle Center | component-new | `battle-center/page.tsx`, `battle/page.tsx` | `PageBattleCenter.vue` | Route battle detail/result logs through `BattleLogEntry` |

### Gap Statistics

| Gap Type | Count | Description |
|----------|-------|-------------|
| type-missing | 1 | Field missing from FE type definition |
| calc-missing | 4 | Calculation formula incorrect or constants hardcoded |
| format-diff | 8 | Display format differs from legacy |
| display-only | 2 | Field in type but not rendered (low priority) |
| component-new | 3 | New component needed for battle log rendering |
| **Total** | **18** | |

### Priority for Plan 02 Implementation

**P0 (Correctness):** #2 calcInjury rounding, #3 kill ratio formula, #1 autorunUser type safety
**P1 (Display parity):** #4 tech constants, #7 statUpThreshold, #16-18 battle log component
**P2 (Polish):** #5 tooltip, #6 color, #8 tooltips, #9 troop detail, #10-12 format tweaks, #15 ageColor
**P3 (Skip):** #13-14 display-only fields not shown in legacy either
