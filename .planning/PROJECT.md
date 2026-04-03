# OpenSamguk Legacy Parity

## What This Is

A web-based Three Kingdoms (samguk) strategy game ported from a legacy PHP codebase (devsam/core) to Spring Boot (Kotlin) + Next.js. v1.0 shipped with full legacy parity -- every game mechanic produces identical outcomes to the PHP implementation.

## Core Value

Every game mechanic must produce identical outcomes to the legacy PHP implementation given the same inputs -- commands, turn processing, battle resolution, NPC AI, and economy must match legacy behavior exactly.

## Requirements

### Validated

- ✓ Multi-process architecture (gateway-app + game-app) -- existing
- ✓ JWT authentication with login/registration/OTP -- existing
- ✓ World/lobby management with scenario selection -- existing
- ✓ WebSocket real-time updates (STOMP/SockJS) -- existing
- ✓ Frontend SPA with game dashboard, map, command UI -- existing
- ✓ Game data layer (officer ranks, items, game constants) -- existing
- ✓ Database migrations (Flyway) -- existing
- ✓ Deterministic game execution (LiteHashDRBG) -- v1.0 Phase 1
- ✓ Observable exception handling -- v1.0 Phase 1
- ✓ Deterministic entity processing order -- v1.0 Phase 1
- ✓ Short field overflow prevention -- v1.0 Phase 2
- ✓ Float-to-int rounding normalized to PHP behavior -- v1.0 Phase 2
- ✓ Integer division parity -- v1.0 Phase 2
- ✓ WarUnitTrigger framework + 10 combat triggers -- v1.0 Phases 3-4
- ✓ Battle formula and siege golden values locked -- v1.0 Phase 4
- ✓ Domestic modifier pipeline (specials, items, officer rank) -- v1.0 Phase 5
- ✓ Economy formula parity (tax, trade, supply, population, salary) -- v1.0 Phase 6
- ✓ 93 command parity (55 general + 38 nation) -- v1.0 Phase 7
- ✓ NPC AI parity (35+ methods matching GeneralAI.php) -- v1.0 Phase 8
- ✓ Turn engine completion (all stubs, step ordering locked) -- v1.0 Phase 9
- ✓ Diplomacy state machine + game-end conditions -- v1.0 Phase 10
- ✓ Scenario data parity (81 scenarios verified) -- v1.0 Phase 10
- ✓ Frontend display parity (28 pages audited, gaps filled) -- v1.0 Phase 11

### Active

(None -- next milestone requirements TBD via `/gsd:new-milestone`)

### Out of Scope

- New features beyond legacy parity -- focus is matching, not extending
- Mobile app -- web-only for now
- Chat system redesign -- keep legacy behavior
- Performance optimization beyond functional parity -- correctness first
- Security hardening (CORS, WebSocket auth) -- separate initiative after parity
- Visual/CSS parity -- functional parity only, UI is modernized

## Context

Shipped v1.0 Legacy Parity with 688 commits over 46 days.
Tech stack: Spring Boot 3 (Kotlin), Next.js 15, PostgreSQL 16, Redis 7.
Backend: gateway-app (auth/proxy) + game-app (game logic/turn engine) multi-process.
Frontend: React 19, Zustand, shadcn/ui, Konva (map), STOMP WebSocket.
Test approach: Golden-value parity tests with fixed-seed RNG across all game systems.

**Known tech debt:**
- Che*Trigger no-op objects in BattleTriggerRegistry
- NationAI.adjustTaxAndBill runtime-active but non-PHP-matching
- War term casualty extension marked @Disabled

## Constraints

- **Parity target**: `legacy-core/` PHP source is the single source of truth
- **Field naming**: Must follow core conventions (intel, crew/crewType/train/atmos)
- **Database**: PostgreSQL (legacy uses MariaDB)
- **Architecture**: Must maintain gateway-app + game-app split
- **NPC Token**: Redis-based (legacy uses select_npc_token DB table)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Code-only parity verification | Docs may be outdated; only PHP source is authoritative | ✓ Good -- caught multiple doc/code divergences |
| Fine-grained phase decomposition (11 phases) | 93 commands + engine + AI = many discrete parity checks | ✓ Good -- enabled parallel work and clear progress tracking |
| Golden-value test approach | Fixed-seed RNG + exact assertions for formula regression | ✓ Good -- most reliable parity verification method |
| LiteHashDRBG for deterministic RNG | PHP SHA-512 stream parity needed | ✓ Good -- zero non-determinism in game engine |
| kotlin.math.round over Math.round | Eliminates Long-to-Int narrowing | ✓ Good -- banker's rounding .5 divergence documented and accepted |
| Keep 5-stat extension | politics/charm are additive; legacy 3-stat formulas use only original 3 | ✓ Good |
| Nation level 10-tier extension | Intentional opensamguk enhancement over PHP 8-level | ✓ Good |
| Scenario stats use 삼국지14 values | Intentional update from legacy 3-stat values | ✓ Good -- divergence documented |
| RNG seed "disaster" not "disater" | PHP typo preserved in seed would require world data migration | ⚠️ Revisit -- documented divergence |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition:**
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions

**After each milestone:**
1. Full review of all sections
2. Core Value check
3. Audit Out of Scope reasoning
4. Update Context with current state

---
*Last updated: 2026-04-03 after v1.0 milestone*
