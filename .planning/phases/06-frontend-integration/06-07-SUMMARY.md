---
phase: "06"
plan: "07"
subsystem: frontend-politics
tags: [politics, faction, empire, alliance, fezzan, ui, zustand]
dependency_graph:
  requires: ["06-01"]
  provides: ["faction-politics-panels", "politics-page-router"]
  affects: ["frontend/src/app/(game)/politics/page.tsx", "frontend/src/stores/politicsStore.ts"]
tech_stack:
  added: []
  patterns: ["axios fetch on mount", "zustand factionType field", "tab-routed faction panels"]
key_files:
  created:
    - frontend/src/components/game/empire-politics-panel.tsx
    - frontend/src/components/game/alliance-politics-panel.tsx
    - frontend/src/components/game/fezzan-politics-panel.tsx
  modified:
    - frontend/src/app/(game)/politics/page.tsx
    - frontend/src/stores/politicsStore.ts
decisions:
  - "Used myOfficer.nationId (General type) with factionId cast fallback since OfficerStore returns General"
  - "Stub toast for intel purchase and election voting — backend endpoints not yet wired"
  - "Tab navigation allows cross-faction viewing (empire/alliance/fezzan) regardless of player faction"
metrics:
  duration: "~25 minutes"
  completed: "2026-04-06"
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 2
---

# Phase 06 Plan 07: Faction Politics UI Summary

**One-liner:** Faction-specific politics panels (Empire coup/nobility, Alliance democracy/elections, Fezzan loans/intel) with tab-routing in politics page.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | EmpirePoliticsPanel and AlliancePoliticsPanel | 83013115 | empire-politics-panel.tsx, alliance-politics-panel.tsx |
| 2 | FezzanPoliticsPanel and politics/page.tsx router | 55a86b3a | fezzan-politics-panel.tsx, politics/page.tsx, politicsStore.ts |

## What Was Built

### EmpirePoliticsPanel (`frontend/src/components/game/empire-politics-panel.tsx`)
- Props: `sessionId`, `officerId`
- Fetches `GET /api/{sessionId}/politics/empire` on mount
- CoupPhase badge: STABLE(green)/BREWING(yellow)/ESCALATED(orange)/CIVIL_WAR(red) with Korean label and description
- My nobility rank highlight at top when officer holds a rank
- Expandable nobility ranks table: 장교명 | 작위(Korean) | 봉토 수 — clicking row expands fief planet list with revenue
- "정치 커맨드 실행" button navigates to `/commands?group=POLITICS`

### AlliancePoliticsPanel (`frontend/src/components/game/alliance-politics-panel.tsx`)
- Props: `sessionId`, `officerId`
- Fetches `GET /api/{sessionId}/politics/alliance` on mount
- Democracy index progress bar (민주주의 지수) with 높음/보통/낮음 label
- Council seats grid: 국방/외교/내무/재무/정보/우주군사령/지상군사령 — officer's own seat highlighted in blue border
- Election schedule list with type label (의회선거/최고평의원선거), date, candidate count, stub "투표 참가" button (toast)

### FezzanPoliticsPanel (`frontend/src/components/game/fezzan-politics-panel.tsx`)
- Props: `sessionId`
- Fetches `GET /api/{sessionId}/politics/fezzan` on mount
- Header note: Fezzan is NPC-only faction
- Loan status table: 진영명 | 차관액 | 이자율 | 상환기한 | 상환여부 — overdue rows highlighted red
- Fezzan ending warning banner when any loan is overdue
- Intelligence market list with stub "구매" button (toast)

### politics/page.tsx (rewritten)
- Resolves factionType from `myOfficer.nationId` via `fetchFactionType`
- Tab nav: [제국 정치 / 동맹 정치 / 페잔 현황] — own faction tab marked with dot indicator
- Switches between EmpirePoliticsPanel / AlliancePoliticsPanel / FezzanPoliticsPanel
- Default tab matches player's own faction

### politicsStore.ts (updated)
- Added `factionType: string | null` state field
- Added `fetchFactionType(sessionId, factionId)` action fetching `/{sessionId}/factions/{factionId}`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Used nationId instead of factionId for General type**
- **Found during:** Task 2 TypeScript check
- **Issue:** `myOfficer` resolves to `General` type (from officerStore) which uses `nationId`, not `factionId`. The plan referenced `myOfficer.factionId` but the actual TypeScript type does not have this field.
- **Fix:** Used `nationId` with a type-cast fallback for future `factionId` field: `(myOfficer as unknown as { factionId?: number }).factionId ?? myOfficer?.nationId`
- **Files modified:** `frontend/src/app/(game)/politics/page.tsx`
- **Commit:** 55a86b3a

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| Election "투표 참가" button shows toast | alliance-politics-panel.tsx | Backend election voting endpoint not wired in this plan |
| Intel "구매" button shows toast | fezzan-politics-panel.tsx | Backend intel purchase endpoint not yet available |
| Empire politics data shape | empire-politics-panel.tsx | API endpoint `GET /api/{sessionId}/politics/empire` not yet implemented in backend Phase 2 |
| Alliance politics data shape | alliance-politics-panel.tsx | API endpoint `GET /api/{sessionId}/politics/alliance` not yet implemented |
| Fezzan politics data shape | fezzan-politics-panel.tsx | API endpoint `GET /api/{sessionId}/politics/fezzan` not yet implemented |

These stubs are intentional — the panels are UI-complete but will show API errors until backend Phase 2 (CMD-06) politics endpoints are implemented.

## Self-Check: PASSED

Files exist:
- `frontend/src/components/game/empire-politics-panel.tsx` — FOUND
- `frontend/src/components/game/alliance-politics-panel.tsx` — FOUND
- `frontend/src/components/game/fezzan-politics-panel.tsx` — FOUND
- `frontend/src/app/(game)/politics/page.tsx` — modified, FOUND
- `frontend/src/stores/politicsStore.ts` — modified, FOUND

Commits exist:
- `83013115` feat(06-07): add EmpirePoliticsPanel and AlliancePoliticsPanel — FOUND
- `55a86b3a` feat(06-07): FezzanPoliticsPanel, politics page router, politicsStore factionType — FOUND

TypeScript errors in our files: 0
