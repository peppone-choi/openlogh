# Milestones

## v1.0 Legacy Parity -- SHIPPED 2026-04-03

**Delivered:** Complete legacy parity -- every game mechanic produces identical outcomes to the devsam/core PHP implementation.

- 11 phases, 29 plans, 56 requirements
- 688 commits, 1,814 files changed, +372K/-278K lines
- Timeline: 46 days (2026-02-16 to 2026-04-03)

**Key accomplishments:**
1. Deterministic game engine (LiteHashDRBG, deterministic entity ordering)
2. Numeric type safety (Short overflow guards, PHP rounding/division parity)
3. Complete battle system (WarUnitTrigger framework, 10 combat triggers, ArmType matrix + siege golden values)
4. Full command + modifier parity (93 commands golden-value verified with item/special/rank modifiers)
5. NPC AI + turn engine (35+ AI methods matching GeneralAI.php, all stubs implemented, step ordering locked)
6. Diplomacy, scenario data, and frontend display parity (81 scenarios verified, 28 pages audited)

**Archive:** [milestones/v1.0-ROADMAP.md](milestones/v1.0-ROADMAP.md) | [milestones/v1.0-REQUIREMENTS.md](milestones/v1.0-REQUIREMENTS.md)
