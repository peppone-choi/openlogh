# Phase 2: Numeric Type Safety - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-31
**Phase:** 02-numeric-type-safety
**Areas discussed:** Guard Placement Strategy, Truncation Parity Approach, Integer Division Handling, Testing Strategy
**Mode:** --auto (all decisions auto-selected)

---

## Guard Placement Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Entity setter guards | Centralized coerceIn in entity property setters | |
| Domain boundary guards | coerceIn at service methods where values are assigned back to entities | ✓ |
| Every arithmetic site | Guards at each of 100+ computation locations | |

**User's choice:** [auto] Domain boundary guards (recommended default)
**Notes:** Follows existing ItemService.kt pattern. Entity setter guards would mask upstream bugs. Per-site guards are too fragile and numerous (100+ locations). Domain boundary approach balances safety with debuggability.

---

## Truncation Parity Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Cross-language test harness | Run PHP + Kotlin side-by-side for each formula | |
| Golden value table comparison | Pre-computed PHP edge case results compared in Kotlin unit tests | ✓ |
| Manual call-site audit | Review each of 100+ sites individually without automated comparison | |

**User's choice:** [auto] Golden value table comparison (recommended default)
**Notes:** Three different rounding methods found in codebase: `Math.round().toInt()`, `roundToInt()`, `.toInt()`. PHP uses half-away-from-zero. Golden value tables efficiently cover edge cases (0.5, 1.5, -0.5, -1.5, 2.5) without requiring a PHP runtime. Priority files: EconomyService (20+), GeneralAI (30+), BattleEngine (80 divisions).

---

## Integer Division Handling

| Option | Description | Selected |
|--------|-------------|----------|
| phpIntdiv() wrapper for all divisions | Wrap all 900+ division operations in a utility function | |
| Verify-and-document approach | Audit for negative-value edge cases, add targeted guards only | ✓ |
| Ignore (assume positive-only) | Skip integer division audit entirely | |

**User's choice:** [auto] Verify-and-document approach (recommended default)
**Notes:** Kotlin `/` and PHP `intdiv()` both truncate toward zero for positive values. Game stats are overwhelmingly non-negative. Wrapping 900+ divisions is unnecessary overhead. Audit identifies the few paths where negative values are possible (gold can go negative) and adds guards only there.

---

## Testing Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Property-based tests (jqwik) | Generate random inputs to find edge cases | |
| 200-turn simulation golden snapshot | Fixed-seed simulation compared against PHP-derived expected values | ✓ |
| Per-fix unit tests only | Test each individual rounding/overflow fix in isolation | |

**User's choice:** [auto] 200-turn simulation golden snapshot (recommended default)
**Notes:** Directly maps to success criterion #2 ("200-turn economic simulation produces the same cumulative values"). Supplemented with per-fix unit tests for each rounding/overflow change. jqwik property testing deferred — Kotlin 2.1 compatibility unconfirmed (STATE.md concern).

---

## Claude's Discretion

- File audit ordering within each requirement (prioritize by parity impact)
- Whether to create shared NumericUtils.kt or inline guards
- Golden value test granularity (per-field vs aggregate)
- Math.round() vs roundToInt() normalization grouping

## Deferred Ideas

None — discussion stayed within phase scope
