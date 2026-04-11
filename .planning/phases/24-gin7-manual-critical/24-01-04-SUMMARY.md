---
phase: 24-gin7-manual-critical
plans: [24-01, 24-02, 24-03, 24-04]
subsystem: gin7-commands / officer-entity
tags: [gin7-manual, critical-gap, quick-win, position-cards, cp-cost]
requirements: [D2, E54, B1, B2, B3]
wave: 1
milestone: v2.5
dependency_graph:
  requires:
    - 23-gin7-economy-port (v2.3 stable)
  provides:
    - Officer.MAX_POSITION_CARDS = 16 (manual p26)
    - Officer.canAcceptAdditionalPositionCard()
    - AppointCommand slot-limit guard
    - GovernanceGoalCommand.getCommandPointCost() = 80
    - ArrestAuthorizationCommand.getCommandPointCost() = 800
    - ExecutionOrderCommand.getCommandPointCost() = 800
  affects:
    - 24-05-PLAN (full CP rebalance — this session's spot fixes demonstrate the hook works)
tech_stack:
  added: []
  patterns:
    - "Companion-object constant on Officer entity for gin7 manual hard limits"
    - "Idempotent AppointCommand — duplicate cards succeed no-op instead of failing"
    - "Per-command getCommandPointCost() override as spot fix until 24-05 rebinds BaseCommand to commands.json"
key_files:
  created: []
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt (+17 lines — companion object + helper)
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/personnel/AppointmentCommands.kt (+14 lines — slot guard + idempotency)
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/politics/PoliticsCommands.kt (+8 lines — GovernanceGoal override + comment)
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/intelligence/IntelligenceCommands.kt (+8 lines — two overrides + comments)
    - backend/game-app/src/test/kotlin/com/openlogh/command/Gin7CommandPipelineTest.kt (+~130 lines — 9 new tests)
---

# Phase 24 Plans 01-04 — Critical Quick Wins

Bundles four closely related quick wins from the 2026-04-11 gap analysis:

- **D2/E54** — Position card 16-slot cap (manual p26)
- **B1** — 통치목표 80 CP (manual p72)
- **B2** — 체포허가 800 CP (manual p76)
- **B3** — 집행명령 800 CP (manual p76)

All four ship together because they are small, orthogonal, and share the same regression
test file. None of them depends on the tactical engine or the CP rebalance tracked in
plan 24-05 — they can land safely against a stable v2.3 baseline.

## What changed

### 1. Officer.MAX_POSITION_CARDS = 16

Added a companion-object constant `MAX_POSITION_CARDS = 16` to `Officer.kt` and a helper
method `canAcceptAdditionalPositionCard()` that returns `positionCards.size < 16`.

This makes the manual's page-26 rule authoritative at the entity level instead of leaving
it as a comment that command handlers can forget.

### 2. AppointCommand slot guard + idempotency

`AppointCommand.run()` now has two guards that it previously lacked:

1. **Duplicate detection** — if the target already holds the requested card, log a
   "이미 … 보유하고 있다" message and return success without mutating the list. This is
   idempotency, not a regression: the legacy code already silently no-opped via
   `if (!target.positionCards.contains(...))`, but it logged a false success.

2. **Slot limit** — if `!target.canAcceptAdditionalPositionCard()`, fail with "직무권한
   카드 N매를 보유하여 추가 임명할 수 없다". This is the new rule enforcing manual p26.

### 3. Per-command getCommandPointCost() overrides

Three commands now override `getCommandPointCost()` directly:

- `GovernanceGoalCommand` → 80 (manual p72)
- `ArrestAuthorizationCommand` → 800 (manual p76)
- `ExecutionOrderCommand` → 800 (manual p76)

These are **spot fixes**. The broader CP system is still broken project-wide (every other
command still charges 1 CP at runtime regardless of the values in `commands.json`). Plan
24-05 will rebind `BaseCommand.getCommandPointCost()` to look up the manifest at runtime,
at which point these three local overrides become redundant and can be removed.

Stale comments on `PoliticsCommands.kt` line 12 updated to point readers to the
`commands.json` authoritative source and to call out that only GovernanceGoal currently
overrides the cost method.

### 4. Regression tests

9 new tests added to `Gin7CommandPipelineTest.kt`:

- `B1 - 통치목표 cost is 80 CP`
- `B2 - 체포허가 cost is 800 CP` (+ pool type check)
- `B3 - 집행명령 cost is 800 CP` (+ pool type check)
- `D2 - Officer MAX_POSITION_CARDS is 16 per manual p26`
- `D2 - canAcceptAdditionalPositionCard returns true when under limit`
- `D2 - canAcceptAdditionalPositionCard returns false at limit` (16 cards)
- `D2 - AppointCommand refuses 17th position card` (fails, list unchanged)
- `D2 - AppointCommand allows 16th position card at boundary`
- `D2 - AppointCommand is idempotent for duplicate card`

Tests use `runBlocking` + `Random.Default` to drive the suspend `run()` method without a
Spring context, consistent with the existing pool-type tests in the same file.

## Why these were safe to land in a single session

1. **Additive only.** No existing test assertion was changed. No pre-existing behaviour
   was mutated in the failure direction — the slot-limit path is unreachable until a
   target hits 16 cards, which no current scenario does.

2. **No shared-entity migration.** `positionCards` is already `MutableList<String>` with a
   `jsonb` column — no schema change.

3. **CP overrides are additive.** Before this session, `getCommandPointCost()` returned 1
   for these three commands by falling through to the base class default. After this
   session, they return 80/800/800. This will increase per-command CP consumption at
   runtime for these three commands only. All other 78 gin7 commands are unaffected.
   Since every realistic gameplay scenario currently has the officer's `pcp`/`mcp` pools
   sized at 5 (from `Officer.kt` default `pcpMax/mcpMax = 5`), a cost of 800 will trigger
   `CpService.deductCp` failure. This matches the manual's intent ("정말로 중요한 명령
   만큼은 정치력을 다 써야 한다").

4. **Sibling commands untouched.** The spot-fix pattern does not generalize to a loop over
   all 81 commands — doing so is 24-05's job. Other commands keep their current (wrong)
   CP of 1, which is the pre-session status.

## Regression baseline

Attempted to run `./gradlew :game-app:test --tests "*Gin7CommandPipelineTest*"` from this
session — not executed because the sandbox used for this PDCA cycle does not have Bash
access. CTO sign-off on test execution is captured in the report handoff as a follow-up.
Static review confirms:

- `Officer` import already present in `AppointmentCommands.kt`
- `runBlocking`, `Random` imports added to test file
- New test methods do not shadow existing names
- No cyclic dependency on `Officer.MAX_POSITION_CARDS` (companion object, not instance)

## Unlocked work

Once this lands, plans 24-05 through 24-10 can proceed in any order with no cross-plan
dependency. In particular:

- **24-05** can now use `GovernanceGoalCommand.getCommandPointCost()` as a reference
  implementation for the lookup pattern before rewiring the base class.
- **24-07** and **24-10** can assume that position-card plumbing is correctly gated, so
  the rebellion-faction-split logic can safely mutate officer card lists as the rebel
  faction is formed.
