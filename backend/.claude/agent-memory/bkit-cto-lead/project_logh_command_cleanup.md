---
name: LOGH Command Registry Cleanup
description: Removed samguk legacy commands from CommandRegistry, leaving 112 LOGH-only commands. Class files retained for backward compatibility with AI/engine string references.
type: project
---

Cleaned CommandRegistry.kt from 194 commands to 112 LOGH-only commands on 2026-03-25.

**Why:** CTO directive to remove Three Kingdoms (samguk) legacy commands and keep only gin7-based LOGH commands. The game is a LOGH space opera, not Three Kingdoms.

**How to apply:**

- CommandRegistry.kt: 78 general + 34 nation = 112 total commands
- ArgSchemas.kt: Cleaned to match registry (removed recruit, trade, foundNation, donation, gift, equipment, npcAction, population, nationName, color schemas)
- Frontend command-arg-form.tsx: Cleaned COMMAND_ARGS, COMMAND_HELP, CITY_TARGET_COMMANDS; removed samguk-specific imports (CrewTypeBrowser, EquipmentBrowser, DeploymentSelector)
- Command class files (GeneralCommands.kt, NationCommands.kt, etc.) intentionally kept intact -- other engine/AI code references command names as strings
- Three samguk commands renamed to LOGH equivalents: 백성동원 -> 주민동원, 이호경식 -> 외교공작, 감축 (kept as-is)
