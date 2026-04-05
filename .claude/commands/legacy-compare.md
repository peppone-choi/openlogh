Compare current implementation against legacy PHP source for parity.

## Arguments

$ARGUMENTS = the feature or file to compare (e.g., "전투 처리", "GeneralAI", "Command/General/che\_정찰")

## Steps

1. Identify the relevant legacy PHP file(s) in `legacy-core/` based on the argument
2. Read the legacy PHP implementation
3. Find the corresponding Kotlin/TypeScript implementation in `backend/` or `frontend/`
4. Compare logic, data flow, and output for parity
5. Report:
    - **Matched**: Logic that is correctly ported
    - **Missing**: Logic in legacy not yet implemented
    - **Diverged**: Logic that differs (with explanation of whether intentional)

## Key Mappings

| Legacy Path                  | New Path                                |
| ---------------------------- | --------------------------------------- |
| `hwe/sammo/Command/General/` | `backend/game-app/.../command/general/` |
| `hwe/sammo/Command/Nation/`  | `backend/game-app/.../command/nation/`  |
| `hwe/sammo/API/`             | `backend/gateway-app/.../api/`          |
| `hwe/func.php`               | Various service classes                 |
| `hwe/GeneralAI.php`          | NPC AI service                          |
| `hwe/process_war.php`        | War processing engine                   |

## Rules

- Trust code, not documentation
- 3-stat system: leadership, strength, intel (core original)
- politics, charm are opensamguk extensions
