package com.opensam.engine.turn.steps

import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 100: Execute all general commands (including AI).
 *
 * Legacy: executeGeneralCommandUntil() in TurnService.
 * This step is a marker — actual command execution remains in TurnService.processWorld()
 * because it requires complex state (command queues, AI decisions, battle triggers)
 * that is tightly coupled to the per-general loop. The pipeline delegates back
 * to TurnService for this step.
 *
 * Note: This step is intentionally a no-op in the pipeline. The actual command
 * execution happens before the pipeline runs in TurnService.processWorld().
 */
@Component
class ExecuteGeneralCommandsStep : TurnStep {
    override val name = "ExecuteGeneralCommands"
    override val order = 100

    override fun execute(context: TurnContext) {
        // Intentional no-op: command execution is handled by TurnService directly
        // before the pipeline runs, due to its complex per-general loop structure.
        // This step exists for pipeline ordering documentation and parity verification.
    }
}
