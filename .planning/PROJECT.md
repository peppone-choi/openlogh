# OpenSamguk Legacy Parity

## What This Is

A web-based Three Kingdoms (samguk) strategy game being ported from a legacy PHP codebase (devsam/core) to a modern Spring Boot (Kotlin) + Next.js stack. The project already has substantial infrastructure and game logic implemented, but needs systematic verification and completion of parity gaps against the legacy PHP source.

## Core Value

Every game mechanic must produce identical outcomes to the legacy PHP implementation given the same inputs -- commands, turn processing, battle resolution, NPC AI, and economy must match legacy behavior exactly.

## Requirements

### Validated

- ✓ Multi-process architecture (gateway-app + game-app) -- existing
- ✓ JWT authentication with login/registration/OTP -- existing
- ✓ World/lobby management with scenario selection -- existing
- ✓ Turn engine with pipeline steps (9 ordered steps) -- existing
- ✓ Command system skeleton (60 general + 43 nation commands) -- existing
- ✓ Economy system (tax, trade, supply, population) -- existing
- ✓ War/battle engine with unit types and terrain -- existing
- ✓ NPC AI with strategic/tactical decision making -- existing
- ✓ Scenario loading (80+ scenarios, 9 maps) -- existing
- ✓ WebSocket real-time updates (STOMP/SockJS) -- existing
- ✓ Frontend SPA with game dashboard, map, command UI -- existing
- ✓ Game data layer (officer ranks, items, game constants) -- existing
- ✓ Diplomacy system with state transitions -- existing
- ✓ Database migrations (Flyway) -- existing
- ✓ Deterministic game execution (LiteHashDRBG, no java.util.Random) -- Phase 1
- ✓ Observable exception handling (SLF4J logging in all engine catch blocks) -- Phase 1
- ✓ Deterministic entity processing order (sort tiebreakers) -- Phase 1
- ✓ Short field overflow prevention (coerceIn guards on all .toShort() sites) -- Phase 2
- ✓ Float-to-int rounding normalized to match PHP behavior (Math.round → kotlin.math.round) -- Phase 2
- ✓ Integer division parity with PHP intdiv() confirmed -- Phase 2

### Active

- [ ] Turn engine stub completion (updateOnline, checkOverhead, checkWander, updateGeneralNumber)
- [ ] Battle special modifier triggers (14+ WarUnitTrigger implementations)
- [ ] Command logic parity -- verify each of 93 commands matches legacy output
- [ ] NPC AI parity -- verify decision trees match legacy GeneralAI.php
- [ ] Turn processing parity -- verify turn step ordering and side effects match legacy daemon.ts
- [ ] Economy formula parity -- verify tax/trade/supply/population calculations
- [ ] War resolution parity -- verify battle formulas and outcomes
- [ ] Diplomacy parity -- verify state transitions and conditions
- [ ] Event/disaster system parity -- verify random event triggers and effects
- [ ] Unification/game-end condition parity
- [ ] Frontend display parity -- verify game information display matches legacy UI
- [ ] Scenario data parity -- verify NPC stats, city data, initial conditions

### Out of Scope

- New features beyond legacy parity -- focus is matching, not extending
- Mobile app -- web-only for now
- Multiplayer lobby chat redesign -- keep legacy behavior
- Performance optimization beyond functional parity -- correctness first
- Security hardening (CORS, WebSocket auth) -- separate initiative after parity

## Context

- **Legacy source**: `legacy-core/` directory contains the full PHP codebase (devsam/core) as the parity target
- **Parity standard**: Same input must produce same output. Documentation is not trusted; only code-to-code comparison counts.
- **3-stat vs 5-stat**: Legacy uses 3 stats (leadership/strength/intel); OpenSamguk extends with politics/charm. Parity logic must use the original 3 where legacy does.
- **Existing verify skills**: Multiple verification skills exist (verify-command-parity, verify-logic-parity, verify-daemon-parity, etc.) for systematic checking
- **Known gaps from codebase analysis**: Turn engine stubs, battle special modifiers, duplicate AuthService
- **Phase 1 complete**: All game logic now uses deterministic RNG, exceptions are logged, entity processing order is deterministic
- **Phase 2 complete**: All Short field assignments guarded against overflow, rounding normalized to PHP behavior, 200-turn golden snapshot baseline established

## Constraints

- **Parity target**: `legacy-core/` PHP source is the single source of truth -- not docs, not assumptions
- **Field naming**: Must follow core conventions (intel not intelligence, crew/crewType/train/atmos)
- **Database**: PostgreSQL (legacy uses MariaDB) -- SQL differences must be handled
- **Architecture**: Must maintain gateway-app + game-app split
- **NPC Token**: Redis-based (legacy uses select_npc_token DB table)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Code-only parity verification | Docs may be outdated or wrong; only PHP source is authoritative | -- Pending |
| Fine-grained phase decomposition | 93 commands + engine + AI = many discrete parity checks needed | -- Pending |
| Systematic verification before new features | Parity gaps cause gameplay inconsistency | -- Pending |
| Keep 5-stat extension | politics/charm are additive; legacy 3-stat formulas use only original 3 | ✓ Good |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check -- still the right priority?
3. Audit Out of Scope -- reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-01 after Phase 2 completion*
