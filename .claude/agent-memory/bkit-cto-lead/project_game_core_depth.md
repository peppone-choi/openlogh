---
name: game-core-depth PDCA Cycle
description: PDCA Team Mode for CP cost system completion (112 commands), PositionCardType expansion (25->77), and planet defense hardcode removal
type: project
---

game-core-depth feature: 3 critical gaps in game core systems being addressed via PDCA Team Mode.

**Why:** 112 legacy commands return CP cost 0 (CommandExecutor.resolveCpCost() L365-371), breaking gin7's core balance mechanic. PositionCardType has 25 entries vs gin7's 100+ positions. TacticalBattleEngine L159-160 hardcodes orbitalDefense=100.0.

**How to apply:**
- GAP 1: LegacyCommandCpRegistry.kt maps all 112 commands to gin7 original CP costs (MCP 10-1280, PCP 0-800)
- GAP 2: PositionCardType expansion to 77 entries (Empire ~36, Alliance ~28, Common ~13)
- GAP 3: TacticalBattleEngine uses Planet entity values instead of hardcoded 100.0
- gin7 original data confirmed: 76 commands with exact CP costs provided by user
- Variable cost commands: 작전계획(10-1280), 발령(1-320), 작전철회(5-320) need arg-based calculation
- Plan document: docs/01-plan/features/game-core-depth.plan.md (2026-04-04)
- Design document: docs/02-design/features/game-core-depth.design.md (2026-04-04)
- Architecture: Option B (Clean Architecture) selected by user -- full layer separation
- New files (5): LegacyCommandCpRegistry.kt, CpCostResolver.kt, PositionCardGrantMap.kt, GroundAssaultInitializer.kt, enum refactor
- Modified files (4): CommandExecutor.kt, PositionCardSystem.kt, TacticalBattleEngine.kt, TacticalSessionManager.kt
- 4 modules: module-1 (CP cost), module-2 (position cards), module-3 (ground assault), module-4 (tests)
- Phase: Design completed, next: Do
