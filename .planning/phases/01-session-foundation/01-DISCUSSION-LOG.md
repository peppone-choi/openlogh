# Phase 1: Session Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-28
**Phase:** 01-session-foundation
**Areas discussed:** Faction join constraints, Session creation parameters, Offline officer state model, Re-entry rules (SESS-07)

---

## Faction Join Constraints

### Q1: Should faction sizes be balanced when players join a session?

| Option                    | Description                                                     | Selected |
| ------------------------- | --------------------------------------------------------------- | -------- |
| Free choice (Recommended) | Players pick any faction freely. Imbalance is part of the game. |          |
| Soft warning              | Free choice, but show a warning. No hard block.                 |          |
| Hard cap ratio            | Enforce a max ratio. Block joining the larger faction.          | ✓        |

**User's choice:** Hard cap ratio
**Notes:** None

### Q2: What max faction ratio should be enforced?

| Option                  | Description                              | Selected |
| ----------------------- | ---------------------------------------- | -------- |
| 3:2 ratio (Recommended) | Max 60% of players in one faction.       | ✓        |
| 2:1 ratio               | Max 67% in one faction. More lenient.    |          |
| Scenario-defined        | Each scenario defines its own max ratio. |          |

**User's choice:** 3:2 ratio
**Notes:** None

### Q3: What happens when a player tries to join the larger faction at cap?

| Option                           | Description                                | Selected |
| -------------------------------- | ------------------------------------------ | -------- |
| Block with message (Recommended) | Show message, player stays in lobby.       | ✓        |
| Auto-assign to smaller           | Automatically assign to other faction.     |          |
| Queue system                     | Enter waiting queue for preferred faction. |          |

**User's choice:** Block with message (메시지로 차단)
**Notes:** User requested all subsequent discussion in Korean (한글로!)

---

## Session Creation Parameters

### Q4: What settings should session creation provide?

| Option                      | Description                                                            | Selected |
| --------------------------- | ---------------------------------------------------------------------- | -------- |
| Scenario only (Recommended) | Scenario selection only, rest from scenario JSON defaults.             | ✓        |
| Scenario + basic options    | Scenario + session name, password, max players.                        |          |
| Full customization          | Scenario + name + password + max players + speed + victory conditions. |          |

**User's choice:** Scenario only
**Notes:** None

### Q5: What info should the lobby session list show?

| Option                       | Description                                                              | Selected |
| ---------------------------- | ------------------------------------------------------------------------ | -------- |
| Core info only (Recommended) | Scenario name, player count (Empire/Alliance each), game date, status.   | ✓        |
| Detailed info                | Above + creator, creation time, star systems per faction, battle status. |          |
| Minimal info                 | Scenario name, total player count, status only.                          |          |

**User's choice:** Core info only
**Notes:** None

---

## Offline Officer State Model

### Q6: How should offline officers appear to other players?

| Option                       | Description                                                 | Selected |
| ---------------------------- | ----------------------------------------------------------- | -------- |
| Same as online (Recommended) | No online/offline distinction. Faithful to gin7 original.   | ✓        |
| Offline icon                 | Show offline status icon on officer list/org chart.         |          |
| Visible to command only      | Only high-ranking officers can check online/offline status. |          |

**User's choice:** Same as online
**Notes:** None

### Q7: What is the scope of offline officer interaction in Phase 1?

| Option                            | Description                                                             | Selected |
| --------------------------------- | ----------------------------------------------------------------------- | -------- |
| CP recovery only (Recommended)    | Only CP recovery. Arrest/personnel/AI in later phases.                  |          |
| CP recovery + basic state changes | CP recovery + location/state changes continue (e.g., fleet in transit). | ✓        |

**User's choice:** CP recovery + basic state changes
**Notes:** User chose the broader scope option despite scope concern note

---

## Re-entry Rules (SESS-07)

### Q8: What conditions count as 'exclusion' from a session?

| Option                                | Description                                                                | Selected |
| ------------------------------------- | -------------------------------------------------------------------------- | -------- |
| Character death only (Recommended)    | Only death in combat (flagship destruction). Logout = offline persistence. | ✓        |
| Death + voluntary retirement          | Death + explicit 'retire' command.                                         |          |
| Death + retirement + forced expulsion | Above + admin/sovereign can forcefully expel.                              |          |

**User's choice:** Character death only
**Notes:** Phase 1 has no combat, so structure only — actual triggering comes in later phases

### Q9: Is a cooldown required before re-entry?

| Option                           | Description                                   | Selected |
| -------------------------------- | --------------------------------------------- | -------- |
| Immediate re-entry (Recommended) | No cooldown. gin7 manual doesn't mention one. | ✓        |
| 24 game-hours wait               | 1 real hour wait. Gives death some weight.    |          |
| 7 game-days wait                 | ~7 real hours wait. Serious penalty.          |          |

**User's choice:** Immediate re-entry
**Notes:** gin7 constraints (same faction, generate character only) still apply

---

## Claude's Discretion

- HARD-01/HARD-02 fix implementation approach
- Flyway migration structure for schema changes
- Whether to add faction ratio config to scenario JSON schema

## Deferred Ideas

None — discussion stayed within phase scope
