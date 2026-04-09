---
phase: 14-frontend-integration
plan: 06
subsystem: frontend
tags: [typescript, types, dto-parity, tactical-battle, command-hierarchy, succession, fog-of-war, battle-summary, operation-events]

# Dependency graph
requires:
  - phase: 14-frontend-integration (14-01)
    provides: "Backend CommandHierarchyDto + SubFleetDto + extended TacticalUnitDto / TacticalBattleDto / BattleTickBroadcast"
  - phase: 14-frontend-integration (14-02)
    provides: "Backend BattleSummaryDto + BattleSummaryRow contract"
  - phase: 14-frontend-integration (14-03)
    provides: "Backend sensorRange field on TacticalUnitDto (shipped opportunistically by 14-01)"
  - phase: 14-frontend-integration (14-04)
    provides: "Backend OperationEventDto WebSocket event shape"
  - phase: 14-frontend-integration (14-05)
    provides: "Wave 0 test scaffold + tacticalBattleFixture.ts that referenced hierarchy fields via `as unknown as TacticalBattle` cast"
provides:
  - "Extended `TacticalUnit` with 8 optional Phase 14 fields (sensorRange, subFleetCommanderId, successionState, successionTicksRemaining, isOnline, isNpc, missionObjective, maxCommandRange)"
  - "Extended `TacticalBattle` + `BattleTickBroadcast` with per-tick attackerHierarchy / defenderHierarchy"
  - "New `CommandHierarchyDto` + `SubFleetDto` interfaces mirroring backend DTOs field-for-field"
  - "Extended `BattleTickEvent.type` union with FLAGSHIP_DESTROYED / SUCCESSION_STARTED / SUCCESSION_COMPLETED / JAMMING_ACTIVE (plus string fallback)"
  - "New `BattleSummaryDto` + `BattleSummaryRow` for 14-18 end-of-battle modal"
  - "New `OperationEventDto` + `OperationObjective` + `OperationStatus` for 14-16 ops WebSocket overlay"
  - "Unshimmed `tacticalBattleFixture.ts` — removed `type CommandHierarchyDto = unknown` placeholder and `as unknown as TacticalBattle` cast now that the type carries the hierarchy fields natively"
  - "Contract test `frontend/src/types/tactical.types.test.ts` — 9 compile-time assertions pinning the DTO shape"
affects: [14-08, 14-09, 14-10, 14-11, 14-12, 14-13, 14-14, 14-15, 14-16, 14-17, 14-18]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Optional-field extension pattern: all new Phase 14 fields on TacticalUnit are `?:` so existing fixtures that pre-date the extension still compile without updates"
    - "String-fallback union literal (`| (string & {})`) on BattleTickEvent.type so literal inference is preserved for the 8 known values while unknown server-side codes still typecheck"
    - "Compile-time type tests via Vitest: `tactical.types.test.ts` uses `import type` + explicit declaration to force `tsc --noEmit` to fail if the contract drifts — lighter-weight than a runtime-only parity verifier"
    - "Barrel-bypass import for tactical types: consumers import directly from `@/types/tactical` (matches existing fixture pattern) rather than re-exporting through `types/index.ts` which is OpenSamguk legacy shape"

key-files:
  created:
    - "frontend/src/types/tactical.types.test.ts"
  modified:
    - "frontend/src/types/tactical.ts"
    - "frontend/src/test/fixtures/tacticalBattleFixture.ts"
    - ".planning/phases/14-frontend-integration/deferred-items.md"

key-decisions:
  - "[Phase 14-06]: New Phase 14 TacticalUnit fields are optional (?:) rather than required — per 14-RESEARCH.md assumption that backend may ship them in a different wave than frontend consumption. Once all Phase 14 backend plans land, a future cleanup plan can tighten them to required without breaking existing call sites."
  - "[Phase 14-06]: BattleTickEvent.type uses a union with a string fallback (`| (string & {})`) instead of a closed union — keeps literal inference on the 8 known values (Damage/Heal/Destroy/Retreat + 4 D-23) for autocomplete while still accepting unknown codes (future D-24/D-37 events) without a type cast."
  - "[Phase 14-06]: DID NOT re-export new types through `frontend/src/types/index.ts` barrel. The barrel is OpenSamguk legacy and contains NO tactical re-exports — consumers (including the Wave 0 fixture) already import directly from `@/types/tactical`. Adding a partial re-export would create an inconsistent import pattern."
  - "[Phase 14-06]: `successionState` typed as literal union `'PENDING_SUCCESSION' | null` rather than `string | null` — the backend only ever emits that one value per D-22, so tightening the type gives better autocomplete and catches typos in downstream consumers (14-14 FlagshipFlash)."
  - "[Phase 14-06]: `OperationObjective` / `OperationStatus` lifted as separate exported types (not just inline in OperationEventDto) — 14-16 UI code will need to render objective badges and status filters, and a shared type enables reuse without duplicating the literal union."
  - "[Phase 14-06]: Dropped the `as unknown as TacticalBattle` cast from `createFixtureBattle` and the local `type CommandHierarchyDto = unknown` placeholder in tacticalBattleFixture.ts. The fixture now type-checks natively against the extended TacticalBattle — which is exactly what the Wave 0 scaffold comment said would happen at 14-06 time."
  - "[Phase 14-06]: Contract-test file uses `import type { ... } from './tactical'` (local path) rather than `@/types/tactical` (alias path) to keep the compile-time failure path as short as possible — if `tactical.ts` drops any exported symbol, the test file is the first thing tsc flags before even walking into `src/app`."
  - "[Phase 14-06]: Plan-prescribed `<automated>` verify ran `pnpm verify:parity` which fails on 22 pre-existing missing OpenSamguk route/spec files — this is an unrelated legacy tooling break, NOT Kotlin↔TS DTO parity. Logged to deferred-items.md as pre-existing; 14-VALIDATION.md line 141 confirms the actual DTO parity gate is `verify-type-parity` skill (not this script)."

patterns-established:
  - "Compile-time type parity tests via Vitest + type-only imports: tests exist both as runtime no-ops and as compile barriers"
  - "Optional-field extension of shared DTO interfaces across waves: new fields opt-in with `?:`, tightened to required in a later cleanup plan once all producers/consumers have landed"
  - "String-fallback literal unions for event-type fields: preserves autocomplete on known values while staying forward-compatible with future events"
  - "Fixture shim removal as a deliberate 14-06 task: the Wave 0 fixture authors deliberately wrote `as unknown as TacticalBattle` so the Wave 2 type-sync agent would have a clear removal target"

requirements-completed: [FE-01, FE-02, FE-03, FE-04, FE-05]

# Metrics
duration: ~6 min
completed: 2026-04-09
---

# Phase 14 Plan 14-06: Frontend TypeScript type sync with Phase 14 DTO extensions Summary

**Mirrored Phase 14 backend DTO extensions (CommandHierarchyDto, SubFleetDto, 8 new optional TacticalUnit fields, per-tick hierarchy on TacticalBattle/BattleTickBroadcast, 4 new D-23 BattleTickEvent.type literals, BattleSummaryDto, OperationEventDto) into `frontend/src/types/tactical.ts` and removed the Wave 0 fixture shims — waves 3-5 can now typecheck the extended shape natively without `as unknown as TacticalBattle` casts.**

## Performance

- **Duration:** ~6 min (single atomic RED+GREEN commit, no sibling-executor contention since plan is frontend-only and Wave 1 DTOs had already landed)
- **Started:** 2026-04-09T10:55:37Z
- **Completed:** 2026-04-09T11:01:10Z
- **Tasks:** 1 (TDD: contract-test file authored alongside the type extension in a single atomic commit per parallel wave protocol)
- **Files modified:** 3 source + 1 new test file + 1 deferred-items append

## Accomplishments

- **Full field-for-field mirror of backend `TacticalBattleDtos.kt` Phase 14 extensions.** Every field added by 14-01/14-02/14-04 has a TypeScript equivalent with matching name, type, and nullability semantics.
- **`CommandHierarchyDto` + `SubFleetDto` exported** with Phase 14 D-21 field names (`commanderOfficerId` / `memberFleetIds`) — stable frontend contract even though the backend engine internally uses `commanderId` / `unitIds` (the backend absorbs the rename in `CommandHierarchyDto.fromEngine`).
- **8 new optional `TacticalUnit` fields** matching backend `TacticalUnitDto`: `sensorRange`, `subFleetCommanderId`, `successionState`, `successionTicksRemaining`, `isOnline`, `isNpc`, `missionObjective`, `maxCommandRange`. All optional to stay compatible with the 14-05 Wave 0 fixture factory and any pre-14-06 test scaffolds.
- **`TacticalBattle` + `BattleTickBroadcast` carry per-tick hierarchy** (`attackerHierarchy?: CommandHierarchyDto | null` + defender). 14-11 fog-of-war reducer and 14-13 canCommandUnit gating now have a compile-time target.
- **`BattleTickEvent.type` accepts 4 new D-23 literals** (`FLAGSHIP_DESTROYED`, `SUCCESSION_STARTED`, `SUCCESSION_COMPLETED`, `JAMMING_ACTIVE`) plus a string fallback — 14-14 FlagshipFlash subscribes to these without a type cast, and future unknown codes still compile.
- **`BattleSummaryDto` + `BattleSummaryRow` ready for 14-18.** Merit breakdown rows (`baseMerit`, `operationMultiplier`, `totalMerit`, `isOperationParticipant`) mirror the backend wire format exactly; 14-18 end-of-battle modal will render "기본 X + 작전 +Y = 총 Z" directly from these rows.
- **`OperationEventDto` + `OperationObjective` + `OperationStatus` ready for 14-16.** Shared objective/status enums enable 14-16 operation overlay UI (badges, filters) to import a single source of truth.
- **Fixture shim removal.** `frontend/src/test/fixtures/tacticalBattleFixture.ts` dropped:
  - The `type CommandHierarchyDto = unknown` local placeholder (now imported from `@/types/tactical`)
  - The `as unknown as TacticalBattle` cast (now fully type-safe since the type natively carries the hierarchy fields)
  - The comment "plan 14-06 extends the TacticalBattle type and these casts will disappear then" (deleted — the casts did disappear)
- **9 compile-time contract tests** in `frontend/src/types/tactical.types.test.ts` pin the DTO shape so a future drift from the backend will fail `tsc --noEmit` AND `pnpm test --run`.

## Task Commits

Single atomic commit per parallel wave protocol (RED test file + GREEN type extension in one commit, per 14-01 precedent — prevents sibling executors from picking up a broken-module intermediate state):

1. **Task 1: Extend TacticalUnit/TacticalBattle/BattleTickBroadcast + add CommandHierarchyDto/SubFleetDto/BattleSummaryDto/OperationEventDto + extend BattleTickEvent union + remove fixture shim + pin contract with type tests** — `426ee9d0` (feat)

Commit uses `--no-verify` per parallel wave protocol (avoids pre-commit hook contention with other active Wave 2 agents).

## Files Created/Modified

### Created

- `frontend/src/types/tactical.types.test.ts` — 9 compile-time contract tests asserting: (1) SubFleetDto field names, (2) CommandHierarchyDto full-field construction, (3) bare TacticalUnit compat, (4) TacticalUnit with all Phase 14 fields, (5) TacticalBattle hierarchy, (6) BattleTickBroadcast hierarchy, (7) all 4 D-23 BattleTickEvent literals, (8) BattleSummaryDto merit breakdown, (9) OperationEventDto for all 4 event types.

### Modified

- `frontend/src/types/tactical.ts` — added `SubFleetDto`, `CommandHierarchyDto`, extended `TacticalUnit` with 8 optional fields, extended `TacticalBattle` + `BattleTickBroadcast` with hierarchy fields, extended `BattleTickEvent.type` to a closed+fallback union, added `BattleSummaryRow`, `BattleSummaryDto`, `OperationObjective`, `OperationStatus`, `OperationEventDto`.
- `frontend/src/test/fixtures/tacticalBattleFixture.ts` — removed `type CommandHierarchyDto = unknown` placeholder, imported the real `CommandHierarchyDto` from `@/types/tactical`, dropped `as unknown as TacticalBattle` cast, replaced with direct `TacticalBattle` annotation.
- `.planning/phases/14-frontend-integration/deferred-items.md` — appended pre-existing `verify:parity` break (legacy OpenSamguk route/spec checker, unrelated to the Kotlin↔TS DTO parity 14-06 actually cares about).

## Decisions Made

See frontmatter `key-decisions` — 8 decisions captured, most notable:

1. **Optional-field extension** for the 8 new TacticalUnit fields rather than making them required — preserves compatibility with Wave 0 fixture factory and pre-14-06 test scaffolds, allows phased backend rollout.
2. **String-fallback BattleTickEvent.type union** (`| (string & {})`) — keeps autocomplete for the 8 known literals while staying forward-compatible with future event codes.
3. **NO re-export through `types/index.ts` barrel** — the barrel is OpenSamguk legacy and already does not re-export any tactical types; the Wave 0 fixture and all tactical consumers already import directly from `@/types/tactical`.
4. **`'PENDING_SUCCESSION' | null` literal on `successionState`** — tighter than the backend's `String?` but matches the one emitted value per D-22, catches typos at compile time.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Scope-correct] Plan's `index.ts` barrel re-export step is conditional — skipped per the "otherwise follow the existing pattern" branch**

- **Found during:** Task 1 (reading `frontend/src/types/index.ts` to decide whether to re-export)
- **Issue:** Plan Step 7 said to re-export the new types through `frontend/src/types/index.ts`, but with the qualifier: "Only if the barrel already uses `export type { ... } from './tactical';` pattern — otherwise follow the existing pattern." Inspection showed the barrel contains ZERO re-exports from `./tactical` — it only defines legacy OpenSamguk interfaces inline (User, Nation, City, General, etc.).
- **Fix:** Did not add re-exports. The Wave 0 fixture and all tactical test files already import directly from `@/types/tactical`, so this matches the existing convention.
- **Files modified:** None (decision to skip).
- **Verification:** `grep -n "from.*tactical" frontend/src/types/index.ts` returned 0 matches, confirming the barrel has no precedent.

**2. [Out of scope] Plan's `<verification>` block lists `pnpm verify:parity` which is a pre-existing legacy break**

- **Found during:** Post-implementation verification
- **Issue:** `pnpm verify:parity` runs `scripts/verify/frontend-parity.mjs`, which asserts existence of 17 OpenSamguk legacy Next.js routes (`frontend/src/app/(game)/page.tsx`, etc.) and 5 Playwright parity specs (`frontend/e2e/parity/01-main.spec.ts`, etc.). None of these files exist in the current LOGH codebase — they were removed during the gin7 rewrite (v2.0). The script is stale legacy tooling that nobody has updated to the new LOGH route layout.
- **Fix:** Not a 14-06 concern. Logged to `deferred-items.md` with suggested owner (either rewrite the script to scan the actual LOGH route layout, or delete it if it no longer serves a purpose). Per 14-VALIDATION.md line 141, the actual Kotlin↔TS DTO parity gate is the `verify-type-parity` skill, not this script.
- **Files modified:** `.planning/phases/14-frontend-integration/deferred-items.md` (append only).
- **Verification:** `pnpm typecheck` exit 0; 9/9 type-parity contract tests pass; all plan acceptance grep checks pass.

**3. [Rule 2 - Missing Critical] Plan's BattleTickEvent replacement would strip the `value`/`detail`/`sourceUnitId`/`targetUnitId` structural fields**

- **Found during:** Task 1 (writing the BattleTickEvent replacement)
- **Issue:** Plan Step 5 showed a proposed replacement that listed only `sourceUnitId?`, `targetUnitId?`, `value?`, `detail?` as OPTIONAL — but the CURRENT file already declares them as REQUIRED (`sourceUnitId: number`, etc.), and existing Wave 0 fixture code + backend `BattleTickEventDto` always populate them (with zero defaults on the Kotlin side). Making them optional would force every consumer to add null guards for no reason.
- **Fix:** Kept the four structural fields as REQUIRED (matching current file + backend defaults) and only extended the `type` literal union. This is a safer contract than the plan's suggested shape.
- **Files modified:** `frontend/src/types/tactical.ts`
- **Verification:** All 9 type tests pass, including the explicit BattleTickEvent.type literal roundtrip test.

---

**Total deviations:** 3 auto-fixed (1 scope-correct skip, 1 out-of-scope legacy tooling, 1 missing critical structural preservation)

**Impact on plan:** None — all three preserve the plan's intent exactly. Deviation 1 follows the plan's own conditional branch. Deviation 2 is a pre-existing legacy tooling break that has nothing to do with DTO parity. Deviation 3 preserved structural fields the plan would have inadvertently weakened.

## Issues Encountered

- **`pnpm verify:parity` is broken at HEAD** — but not for any reason related to 14-06. It's a stale OpenSamguk route/spec existence checker. Logged and deferred.
- **Sibling parallel executor state leaking into working tree** — `git status` before commit showed `frontend/package.json` + `frontend/pnpm-lock.yaml` + `frontend/src/__tests__/no-r3f-imports.test.ts` as modified by other agents. Staged only the 4 files this plan owns (`git add frontend/src/types/tactical.ts frontend/src/types/tactical.types.test.ts frontend/src/test/fixtures/tacticalBattleFixture.ts .planning/phases/14-frontend-integration/deferred-items.md`) — standard parallel-wave isolation practice.

## User Setup Required

None — this plan is pure frontend TypeScript type code with no external service configuration.

## Next Phase Readiness

- **14-08 (WebSocket connection tracking)** — unblocked. `TacticalUnit.isOnline?` is typed and ready for the store reducer to populate.
- **14-09 (CRC rendering)** — unblocked. `TacticalUnit.maxCommandRange?` + `commandRange` + `CommandHierarchyDto.subFleets` provide everything the outer dashed ring + inner solid ring need.
- **14-10 (sub-fleet drawer)** — unblocked. `CommandHierarchyDto.subFleets: SubFleetDto[]` + each `SubFleetDto.memberFleetIds` enables drag-to-assign UI.
- **14-11 (fog-of-war reducer)** — unblocked. `TacticalUnit.sensorRange?` drives the per-unit visibility check per D-19.
- **14-12 (succession countdown)** — unblocked. `TacticalUnit.successionState` + `successionTicksRemaining` drive the 30→0 overlay per D-22.
- **14-13 (canCommandUnit gating)** — unblocked. `CommandHierarchyDto.subFleets[*].memberFleetIds` + `fleetCommander` enable the "is this unit in my command chain?" check per FE-03 / D-11.
- **14-14 (FlagshipFlash)** — unblocked. `BattleTickEvent.type === 'FLAGSHIP_DESTROYED'` is a first-class literal.
- **14-15 (NPC markers)** — unblocked. `TacticalUnit.isNpc?` is a server-authoritative O(1) lookup per D-35.
- **14-16 (operation overlay)** — unblocked. `OperationEventDto` + `OperationObjective` + `OperationStatus` are ready for the WebSocket subscription and rendering.
- **14-18 (end-of-battle modal)** — unblocked. `BattleSummaryDto` + `BattleSummaryRow` match the backend `/api/v1/battle/{sessionId}/{battleId}/summary` endpoint 14-02 shipped.

### Blockers / concerns

- None for 14-06's own scope. `verify:parity` is deferred as unrelated legacy tooling.
- 14-03 backend `sensorRange` field already landed in 14-01 (was shipped opportunistically during the parallel wave 1 DTO frontload); the optional `sensorRange?: number` here is compatible with either timing.

---

*Phase: 14-frontend-integration*
*Plan: 06*
*Completed: 2026-04-09*

## Self-Check: PASSED

- [x] `frontend/src/types/tactical.ts` modified (verified via grep — 13 Phase 14 symbol additions)
- [x] `frontend/src/test/fixtures/tacticalBattleFixture.ts` modified (verified — cast removed)
- [x] `frontend/src/types/tactical.types.test.ts` created (9 tests, 100% pass)
- [x] `.planning/phases/14-frontend-integration/deferred-items.md` modified (pre-existing verify:parity break logged)
- [x] Commit `426ee9d0` exists (verified via `git log --oneline`)
- [x] `pnpm typecheck` exits 0 (verified)
- [x] `pnpm test --run src/types/tactical.types.test.ts src/test/fixtures` exits 0 (verified — 9/9 pass)
- [x] `grep -n "export interface CommandHierarchyDto" frontend/src/types/tactical.ts` → line 89
- [x] `grep -n "export interface SubFleetDto" frontend/src/types/tactical.ts` → line 73
- [x] `grep -n "sensorRange\?:" frontend/src/types/tactical.ts` → line 134
- [x] `grep -n "isNpc\?:" frontend/src/types/tactical.ts` → line 144
- [x] `grep -n "FLAGSHIP_DESTROYED" frontend/src/types/tactical.ts` → line 194
- [x] `grep -n "SUCCESSION_STARTED" frontend/src/types/tactical.ts` → line 195
- [x] `grep -n "export interface BattleSummaryDto" frontend/src/types/tactical.ts` → line 280
- [x] `grep -n "export interface OperationEventDto" frontend/src/types/tactical.ts` → line 299
- [x] `grep -n "maxCommandRange\?:" frontend/src/types/tactical.ts` → line 148
- [x] `grep -n "subFleetCommanderId\?:" frontend/src/types/tactical.ts` → line 136
- [x] `grep -n "successionState\?:" frontend/src/types/tactical.ts` → line 138
- [x] `! grep -n "ts-expect-error — hierarchy fields added in 14-06" frontend/src/test/fixtures/tacticalBattleFixture.ts` → forbidden pattern absent (never existed — shim was `as unknown as TacticalBattle` which is also now removed)
