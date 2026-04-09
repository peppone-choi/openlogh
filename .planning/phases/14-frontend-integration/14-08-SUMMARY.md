---
phase: 14-frontend-integration
plan: 08
subsystem: ui
tags: [frontend, r3f-removal, konva, tactical-ui, wave-2, dep-cleanup, regression-guard]

# Dependency graph
requires:
  - phase: 14-07
    provides: "@dnd-kit/core + @dnd-kit/utilities installed (kept typecheck green across Wave 1→2 boundary while R3F source was still present)"
  - phase: 14-05
    provides: "Wave 0 no-r3f-imports.test.ts scaffold (tolerance mode) — this plan flips it to strict per its deviation 2 unlock-point comment"
provides:
  - "Konva BattleMap.tsx as the single tactical renderer (D-25, D-26, D-27 enforced in code)"
  - "Strict no-r3f-imports regression guard (expect(offenders).toEqual([]) + package.json dep check)"
  - "Zero @react-three/* + three dependency surface in frontend/package.json + lockfile"
affects:
  - 14-09  # CRC render layer — confirmed Konva, no R3F detour
  - 14-10  # BattleMap hierarchy-driven multi-CRC render
  - 14-11  # FogLayer — Konva Layer composition without R3F Canvas interference
  - 14-12  # SubFleetAssignmentDrawer — renders over BattleMap, no 3D overlap concerns
  - 14-14  # FlagshipFlash — Konva local FX, not R3F shader
  - 14-18  # BattleEndModal — plain React, no 3D

# Tech tracking
tech-stack:
  added: []
  removed:
    - "@react-three/fiber@9.5.0"
    - "@react-three/drei@10.7.7"
    - "three@0.183.2"
    - "@types/three@0.183.1"
  patterns:
    - "Atomic dep-remove + source-delete: R3F source files and their 4 npm deps removed in a single plan to keep typecheck green on every intermediate commit (D-25 removal sequence from 14-RESEARCH §2)"
    - "Regression guard upgrade pattern: Wave 0 tolerance scaffold carried an inline unlock comment pointing to 14-08; this plan flips the assertion and adds a second package.json dep-list assertion"
    - "Full-screen takeover removal: Battle page (감찰부) lost its `if (currentBattle) return <BattleCloseView />` hijack; tactical rendering now lives solely on /tactical per D-26"

key-files:
  created: []
  modified:
    - "frontend/package.json — removed @react-three/fiber, @react-three/drei, three, @types/three"
    - "frontend/pnpm-lock.yaml — -468 lines (48 packages dropped from lockfile)"
    - "frontend/src/__tests__/no-r3f-imports.test.ts — flipped to strict (expect(offenders).toEqual([])) + added package.json dep-list assertion"
    - "frontend/src/app/(game)/battle/page.tsx — removed BattleCloseView import, useTacticalStore currentBattle destructure, and the full-screen takeover branch"
  deleted:
    - "frontend/src/components/tactical/TacticalMapR3F.tsx — 97 lines, R3F 3D grid tactical overview"
    - "frontend/src/components/tactical/BattleCloseViewScene.tsx — 303 lines, R3F combat drama scene"
    - "frontend/src/components/tactical/BattleCloseView.tsx — 116 lines, full-screen R3F layout root"
    - "frontend/src/components/tactical/BattleCloseViewPanel.tsx — 186 lines, officer info side panel (plain React, but orphaned after layout root deletion)"

key-decisions:
  - "D-08-01: Battle page (감찰부 war-status admin page) does NOT get a BattleMap migration replacement — per D-26, tactical rendering is unified under /tactical, so the 감찰부 full-screen takeover branch was removed outright. 감찰부 remains a pure war-status report page."
  - "D-08-02: Kept Node built-in fs.readdirSync walker from the 14-05 Wave 0 scaffold rather than importing globSync from `glob` (not a frontend dependency). The plan's template code suggested `globSync` but the 14-05 decision to avoid that dep is still in effect — adding glob just for this test would be out of scope."
  - "D-08-03: The __dirname→package.json path math is 2 levels up (`join(__dirname, '..', '..', 'package.json')`), not 3 as suggested by the plan template. `__dirname` resolves to `frontend/src/__tests__` under vitest, so 2 `..` reaches `frontend/` — initial 3-level attempt triggered ENOENT on monorepo root, fixed inline before commit."
  - "D-08-04: Deleted BattleCloseViewPanel.tsx even though it had no R3F imports itself — it was exclusively consumed by the deleted BattleCloseView.tsx layout root, so leaving it would create an orphan. Plan frontmatter explicitly lists it in files_modified (as a delete target)."

patterns-established:
  - "R3F dep-remove sequence: grep consumers → migrate/delete them → delete source → pnpm remove deps → flip regression guard → verification chain (typecheck → scoped test → build → full test). Keeps typecheck green between every step."
  - "Regression guard flip pattern: a Wave 0 tolerance test file carries an inline `// Implemented in 14-XX` comment on the skipped companion test; the target plan replaces the tolerant live test AND the skipped companion with the strict version, keeping the file count stable."

requirements-completed: [FE-01]

# Metrics
duration: 6min
completed: 2026-04-09
---

# Phase 14 Plan 08: Remove R3F files + @react-three deps Summary

**R3F (@react-three/*, three) fully excised from the frontend — 4 R3F source files (707 lines) deleted, 4 npm packages (48 lockfile packages) removed, regression guard flipped from tolerance to strict. BattleMap.tsx (Konva) is now the sole tactical renderer per D-25/D-26/D-27, unblocking the rest of Wave 2-5 CRC/hierarchy/fog/drawer UI work.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-04-09T10:55:17Z
- **Completed:** 2026-04-09T11:01:33Z
- **Tasks:** 2
- **Files changed:** 9 (4 deleted, 5 modified)
- **Lines delta:** -1,175 net (707 R3F source + 468 lockfile − 18 test/page edits)

## Accomplishments

### Task 1 — Delete 4 R3F source files + migrate consumer

- Grepped `frontend/src/` for `TacticalMapR3F|BattleCloseViewScene|BattleCloseView|BattleCloseViewPanel` and for `from 'three'` / `from '@react-three/*'` — found ONLY two consumers outside the 4 files-to-delete: the regression test itself (comment references, not imports) and `frontend/src/app/(game)/battle/page.tsx` (import + full-screen takeover on `currentBattle`).
- Removed the battle page takeover: dropped `BattleCloseView` import, dropped `useTacticalStore` destructure (no longer needed — `currentBattle` was its only use), dropped the `if (currentBattle) return <BattleCloseView />` branch. Rationale (D-08-01): 감찰부 is the war-status report page, not a tactical view. Per D-26, tactical rendering lives on `/tactical` via `BattleMap`.
- `rm frontend/src/components/tactical/{TacticalMapR3F,BattleCloseViewScene,BattleCloseView,BattleCloseViewPanel}.tsx` (4 deletes, 707 lines).
- Post-delete verification: `rg "from ['\"](three|@react-three/)" frontend/src/` → zero matches. `rg "TacticalMapR3F|BattleCloseViewScene" frontend/src/` → only the two comment lines inside the regression test file (which Task 2 then overwrote).

### Task 2 — Remove deps + flip regression guard + verify chain

- `pnpm remove @react-three/fiber @react-three/drei three @types/three` — 48 transitive packages dropped from the lockfile (9 direct + 39 transitive). pnpm flagged an unrelated pre-existing peer warning (`@vitejs/plugin-react` wants vite@^8, has 7.3.1) — out of scope, not touched.
- Flipped `src/__tests__/no-r3f-imports.test.ts`:
  - Replaced the tolerant `expect(Array.isArray(offenders)).toBe(true)` with strict `expect(offenders).toEqual([])`
  - Dropped the `it.skip('WILL assert zero offenders…')` companion test
  - Added a NEW second `it('package.json does not list @react-three or three')` that reads `frontend/package.json` and asserts the 4 dep keys are `undefined`
  - Extended the scanner regex to also catch `from ['"]three['"]` (not just `@react-three/`), matching the plan's truthier definition
- Fixed inline: initial path math used 3 `..` which resolved to monorepo root — corrected to 2 `..` (D-08-03) so `__dirname` → `frontend/src/__tests__` → `frontend/package.json`.
- Verification chain (all scoped to files I touched per environment gotchas):
  - `pnpm typecheck` → exit 0 (zero tsc output)
  - `pnpm test --run src/__tests__/no-r3f-imports.test.ts` → 2/2 pass, 0 fail, 0 skip
  - `pnpm build` → exit 0 (Next.js 16.1.6 Turbopack, full route tree compiled including `/battle` and `/tactical`)
  - `pnpm test --run` (full suite) → 331 pass / 7 pre-existing fail / 55 skip — all 7 failures are in files not touched by this plan (`command-select-form.test.ts`, `game-dashboard.test.tsx`, `record-zone.test.ts`), matching the deferred-items.md fingerprint exactly

## Task Commits

Each task committed atomically with `--no-verify` per the parallel-executor wave-2 protocol (avoid pre-commit hook contention with sibling agents):

1. **Task 1: Delete 4 R3F files + battle page cleanup** — `d3814b19` (refactor)
   - Deleted: TacticalMapR3F.tsx, BattleCloseViewScene.tsx, BattleCloseView.tsx, BattleCloseViewPanel.tsx
   - Modified: frontend/src/app/(game)/battle/page.tsx (import + destructure + takeover branch removed)
   - 5 files changed, 707 deletions

2. **Task 2: Remove 4 deps + flip regression test + path-fix** — `d970fb4b` (chore)
   - Modified: frontend/package.json, frontend/pnpm-lock.yaml, frontend/src/__tests__/no-r3f-imports.test.ts
   - 3 files changed, 18 insertions(+), 468 deletions(-)

## Files Created/Modified

### Deleted (Task 1)
- `frontend/src/components/tactical/TacticalMapR3F.tsx` (97 lines)
- `frontend/src/components/tactical/BattleCloseViewScene.tsx` (303 lines)
- `frontend/src/components/tactical/BattleCloseView.tsx` (116 lines)
- `frontend/src/components/tactical/BattleCloseViewPanel.tsx` (186 lines)

### Modified (Task 1)
- `frontend/src/app/(game)/battle/page.tsx`:
  - Removed `import { useTacticalStore }` (only used for `currentBattle`)
  - Removed `import { BattleCloseView } from '@/components/tactical/BattleCloseView'`
  - Removed `const { currentBattle } = useTacticalStore();`
  - Removed `if (currentBattle) return <BattleCloseView />;` takeover branch

### Modified (Task 2)
- `frontend/package.json`:
  - Removed `"@react-three/fiber": "^9.5.0"`
  - Removed `"@react-three/drei": "^10.7.7"`
  - Removed `"three": "^0.183.2"`
  - Removed `"@types/three": "^0.183.1"` (devDependencies)
- `frontend/pnpm-lock.yaml` — −468 lines (48 packages removed from graph)
- `frontend/src/__tests__/no-r3f-imports.test.ts`:
  - Flipped `expect(Array.isArray(offenders)).toBe(true)` → `expect(offenders).toEqual([])`
  - Extended scanner to also match `from 'three'` (not just `@react-three/`)
  - Removed the `it.skip('WILL assert zero offenders after 14-08 removes R3F')` companion
  - Added a NEW `it('package.json does not list @react-three or three')` block
  - Header comment updated from "Wave 0 tolerance" → "Plan 14-08 flipped to strict"

### Metadata (this commit)
- `.planning/phases/14-frontend-integration/14-08-SUMMARY.md` — this file
- `.planning/STATE.md` — progress + decisions + metrics + session
- `.planning/ROADMAP.md` — plan progress row
- `.planning/REQUIREMENTS.md` — FE-01 requirement marked complete

## Decisions Made

- **D-08-01:** Battle page (감찰부) does NOT get a BattleMap replacement for its full-screen takeover — the takeover branch is deleted outright. Per D-26, tactical rendering is centralized on `/tactical` via `BattleMap.tsx`; 감찰부 remains a pure war-status admin page with no 3D/tactical hijack.
- **D-08-02:** Kept Node built-in `fs.readdirSync` walker from the 14-05 Wave 0 scaffold rather than importing `globSync` from `glob`. The plan template suggested `globSync`, but the `glob` package is not a frontend dependency and adding it just for this regression guard is out of scope (consistent with 14-05 deviation 1).
- **D-08-03:** `__dirname → package.json` path math is 2 levels up, not 3. `__dirname` resolves to `frontend/src/__tests__` under vitest; `join(__dirname, '..', '..', 'package.json')` = `frontend/package.json`. Initial 3-level attempt triggered ENOENT on monorepo root and was corrected before commit.
- **D-08-04:** Deleted `BattleCloseViewPanel.tsx` even though it had no `three`/`@react-three` imports of its own — it was exclusively consumed by the deleted `BattleCloseView.tsx` layout root, so leaving it would orphan it. The plan frontmatter explicitly listed it in `files_modified` as a delete target, so no scope expansion.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Wrong `..` count on package.json test path**
- **Found during:** Task 2 first run of `pnpm test --run src/__tests__/no-r3f-imports.test.ts`
- **Issue:** Plan template code used `join(__dirname, '..', '..', '..', 'package.json')` — with `__dirname = frontend/src/__tests__`, three `..` segments resolve to the monorepo root, where no `package.json` exists. Vitest failed the test with `ENOENT: no such file or directory, open '/Users/apple/Desktop/개인프로젝트/openlogh/package.json'`.
- **Fix:** Changed to two `..` segments: `join(__dirname, '..', '..', 'package.json')` → `frontend/package.json`. Re-ran scoped test: 2/2 pass.
- **Files modified:** `frontend/src/__tests__/no-r3f-imports.test.ts` (path math only, no behavior change)
- **Verification:** `pnpm test --run src/__tests__/no-r3f-imports.test.ts` → 2 passed / 0 failed
- **Committed in:** `d970fb4b` (Task 2 commit — fixed inline before staging)

**2. [Rule 3 - Blocking] `globSync` from `glob` package unavailable**
- **Found during:** Writing the new regression test body
- **Issue:** Plan template Step 4 showed `import { globSync } from 'glob';` but `glob` is not in `frontend/package.json` dependencies. This is the identical blocker that plan 14-05 encountered (`glob` unavailable) and resolved by switching to Node built-in `fs` walk.
- **Fix:** Kept the 14-05 `walk(dir)` function using `readdirSync + statSync` recursive traversal. Extended the regex to also match `from 'three'` (not just `@react-three/`) so the strict assertion covers the plan's full intent. This is a **recurrence** of 14-05 deviation 1 — same root cause, same fix.
- **Files modified:** `frontend/src/__tests__/no-r3f-imports.test.ts`
- **Verification:** Scoped test run: 2/2 pass with strict assertion.
- **Committed in:** `d970fb4b` (Task 2 commit)

**3. [Rule 3 - Scope boundary] Pre-existing 7 Vitest failures surfaced by `pnpm test --run`**
- **Found during:** Full test suite verification at end of Task 2
- **Issue:** `pnpm test --run` exits non-zero with 7 failures in 3 files: `command-select-form.test.ts` (1), `game-dashboard.test.tsx` (3), `record-zone.test.ts` (3). These failures are byte-for-byte identical to the failures already logged in `.planning/phases/14-frontend-integration/deferred-items.md` by the 14-05 agent. Environment gotchas block in prompt explicitly called these out as pre-existing.
- **Fix:** None — all 7 failures are in files this plan does NOT touch. Scope boundary rule: "Only auto-fix issues DIRECTLY caused by the current task's changes." The 14-08 changes (4 R3F deletes, battle page takeover removal, regression test flip, 4 dep removals) have zero overlap with the 3 failing test files.
- **Verification that failures are pre-existing:** `git log --oneline --follow frontend/src/components/game/record-zone.test.ts` shows these tests last touched long before Wave 2; they hardcode 삼국지 `/\d+년/` date regex that never matched LOGH `UC 795.9` format — unrelated to R3F removal.
- **Disposition:** Already documented in `deferred-items.md`, no new entry needed.
- **Committed in:** N/A — out of scope

---

**Total deviations:** 3 auto-fixed (2 Rule 3 - Blocking, 1 Rule 3 - Scope boundary)
**Impact on plan:** Plan objective fully satisfied. Deviations 1 and 2 were template fidelity issues resolved inline within Task 2 before commit. Deviation 3 is a known pre-existing environment state that the prompt explicitly flagged — no scope expansion.

## Issues Encountered

- **Unrelated pnpm peer warning:** `@vitejs/plugin-react 6.0.1` wants `vite@^8.0.0` but project has `7.3.1`. Pre-existing, not caused by this plan, not touched.
- **Full test suite has 7 pre-existing failures:** Already documented in `deferred-items.md`. Scope-boundary skip per the GSD rule and the environment gotchas block.

## Next Phase Readiness

**Unblocked by this plan:**
- **14-09 (CRC rendering):** CRC component layer is now guaranteed Konva-only. No more dual Canvas/Stage confusion.
- **14-10 (BattleMap hierarchy):** BattleMap is the sole tactical renderer; hierarchy-driven multi-CRC rendering can assume a single Stage.
- **14-11 (FogLayer):** Konva Layer composition has no R3F `<Canvas>` interference to worry about.
- **14-12 (SubFleetAssignmentDrawer):** dnd-kit drawer renders over BattleMap with no 3D z-order contention.
- **14-14 (FlagshipFlash):** Local FX wire into Konva Stage, not R3F shader.
- **14-18 (BattleEndModal):** Plain React modal, no 3D consideration.

**Bundle impact (anticipated):** 14-RESEARCH Section 2 predicted a ~150 kB gzip drop from removing `three` + `@react-three/fiber` + `@react-three/drei`. Not measured here (would need a before/after .next/build output diff), but lockfile shows 48 packages dropped which is consistent with the ~150 kB estimate.

**No blockers.** Wave 3-5 can proceed with BattleMap assumption confirmed in code.

## Known Stubs

None. Every file touched by this plan is in its final state:
- Regression test is strict (not tolerance mode) and has both assertions live.
- Battle page compiles cleanly with the takeover removed.
- Package.json and lockfile are fully consistent with R3F removal.

No intentional stubs exist in 14-08 — the plan's purpose is removal, not forward-wiring.

## Self-Check: PASSED

- DELETED: `frontend/src/components/tactical/TacticalMapR3F.tsx` — verified absent on disk
- DELETED: `frontend/src/components/tactical/BattleCloseViewScene.tsx` — verified absent on disk
- DELETED: `frontend/src/components/tactical/BattleCloseView.tsx` — verified absent on disk
- DELETED: `frontend/src/components/tactical/BattleCloseViewPanel.tsx` — verified absent on disk
- FOUND: `.planning/phases/14-frontend-integration/14-08-SUMMARY.md`
- FOUND: commit `d3814b19` (Task 1: R3F file deletion + battle page cleanup)
- FOUND: commit `d970fb4b` (Task 2: @react-three dep removal + strict regression test)
- `pnpm typecheck` exit 0 (verified during Task 2)
- `pnpm build` exit 0 (verified during Task 2)
- `pnpm test --run src/__tests__/no-r3f-imports.test.ts` → 2/2 pass (verified during Task 2)
- `grep -n "expect(offenders).toEqual(\[\])" frontend/src/__tests__/no-r3f-imports.test.ts` → line 49 match (verified during Task 2)
- `rg "from ['\"](three|@react-three/)" frontend/src/` → zero matches (verified during Task 1)
- `grep -nE '"@react-three/|"three":|"@types/three"' frontend/package.json` → zero matches (verified during Task 2)

---
*Phase: 14-frontend-integration*
*Completed: 2026-04-09*
