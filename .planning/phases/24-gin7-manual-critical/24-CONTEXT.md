---
phase: 24-gin7-manual-critical
created: 2026-04-11
driver: gap analysis in docs/03-analysis/gin7-manual-complete-gap.analysis.md
depends_on:
  - 23-gin7-economy-port (v2.3 post-ship, stable)
milestone: v2.5
total_gaps: 9 (Critical only — High/Medium deferred to v2.6+)
---

# Phase 24 — gin7 Manual Critical Gap Closure

## Why this phase exists

On 2026-04-11 the CTO team produced `docs/03-analysis/gin7-manual-complete-gap.analysis.md`,
a 101-page transcription and cross-walk of the original gin7 (BOTHTEC 2004) manual against
the current OpenLOGH code base. That analysis surfaced 41 gaps — 9 Critical, 18 High, 14
Medium — across parts A (manual-declared "未実装"), B (strategic commands), C (tactical
engine), D (organization), and E (meta systems).

This Phase 24 targets **only the 9 Critical gaps**. High/Medium tiers are explicitly out of
scope and are scheduled for v2.6 and later phases (see gap analysis §10.2–10.3).

## Phase structure

Each Critical gap is broken into a sub-plan. Sub-plans `24-01`…`24-04` are "quick wins"
small enough to land in a single session; sub-plans `24-05`…`24-09` each require dedicated
design work because they touch the tactical engine, the faction model, or the officer
progression subsystem.

| Sub-plan  | Gap    | Title                                             | Scope    | Landing strategy |
|-----------|--------|---------------------------------------------------|----------|------------------|
| 24-01     | D2/E54 | Position card 16-slot cap                         | Small    | Code + test (LANDED in this session) |
| 24-02     | B1     | 統治目標 CP spot-fix (80)                          | Small    | Code + test (LANDED in this session) |
| 24-03     | B2     | 逮捕許可 CP spot-fix (800)                         | Small    | Code + test (LANDED in this session) |
| 24-04     | B3     | 執行命令 CP spot-fix (800)                         | Small    | Code + test (LANDED in this session) |
| 24-05     | (B*)   | Full CP rebalance — commands.json runtime binding | Medium   | Plan only — large surface area |
| 24-06     | E39    | Grid 300-unit/faction capacity enforcement        | Medium   | Plan only |
| 24-07     | A7/C4  | 戦死 → 帰還惑星 워프 (DeathInjurySystem wiring)     | Large    | Plan only |
| 24-08     | C1     | Tactical beam/gun/missile line-of-sight           | Large    | Plan only |
| 24-09     | A3     | 叙勲 medal system + 階級ラダー tiebreaker           | Large    | Plan only |
| 24-10     | E51    | Rebellion → 反乱軍 faction split wiring            | Large    | Plan only |

## Root cause discoveries from this session

Two findings from the spike are worth preserving at phase level because they reshape the
later sub-plans:

1. **CP cost plumbing is broken project-wide.** `BaseCommand.getCommandPointCost()` returns
   a hard-coded `1` by default and is **not** overridden by any of the 81 gin7 commands.
   `backend/shared/src/main/resources/data/commands.json` has the authoritative manual
   values (80 / 160 / 320 / 800 …) but `CommandRegistry.lookupById()` is not called
   anywhere in the runtime path. `RealtimeService.scheduleCommand()` line 317 does
   `command.getCommandPointCost().coerceAtLeast(1)` — today every strategic command
   therefore costs exactly 1 CP. This means gaps B1/B2/B3 from the gap analysis understated
   the problem: the numbers aren't just wrong, they aren't applied at all. Plan 24-05
   addresses this holistically.

2. **`DeathInjurySystem.kt` does not exist.** The gap analysis document claims it is
   present ("PARTIAL — exists but not wired"), but a repo-wide grep on
   `class DeathInjurySystem` returns zero hits. `TacticalBattleEngine:398` emits a generic
   InjuryEvent for command-stat attrition only. Plan 24-07 therefore creates the system
   from scratch rather than extending an existing one.

## Regression baseline

Pre-session baseline: 1136 tests passing in `backend/game-app` (from
`docs/03-analysis/legacy-parity.analysis.md`). All Phase 24 sub-plans MUST preserve this
baseline. Any sub-plan that would require altering a pre-existing test assertion needs
CTO sign-off first.

## Out of scope for Phase 24

- High-tier gaps (18 items) — see phase 25
- Medium-tier gaps (14 items) — see phase 26
- Appendix seed data verification (F1-F3) — separate phase
- Frontend UI changes for any of the above — phase 27 frontend-adaptation
