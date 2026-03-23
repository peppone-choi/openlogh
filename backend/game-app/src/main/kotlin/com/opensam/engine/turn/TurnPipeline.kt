package com.opensam.engine.turn

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Turn Pipeline Orchestrator.
 *
 * Collects all TurnStep beans (auto-registered via Spring) and executes them
 * in order. Each step is fault-tolerant: if one step fails, the pipeline logs
 * the error and continues to the next step (legacy parity — daemon.ts behavior).
 *
 * Legacy parity: src/daemon.ts + hwe/func_process.php execution order.
 */
@Component
class TurnPipeline(steps: List<TurnStep>) {

    private val log = LoggerFactory.getLogger(TurnPipeline::class.java)
    private val orderedSteps: List<TurnStep> = steps.sortedBy { it.order }

    /**
     * Execute all pipeline steps in order for the given turn context.
     * Returns the number of steps successfully executed.
     */
    fun execute(context: TurnContext): Int {
        var successCount = 0
        for (step in orderedSteps) {
            if (step.shouldSkip(context)) {
                log.debug("[TurnPipeline] Skipping step: {} (order={})", step.name, step.order)
                continue
            }
            try {
                val startMs = System.currentTimeMillis()
                step.execute(context)
                val elapsedMs = System.currentTimeMillis() - startMs
                successCount++
                log.debug("[TurnPipeline] Step {} completed in {}ms", step.name, elapsedMs)
            } catch (e: Exception) {
                log.error("[TurnPipeline] Step {} failed: {}", step.name, e.message, e)
                // Continue to next step (legacy behavior: each step is independent)
            }
        }
        return successCount
    }

    /**
     * Returns the ordered list of step names (for testing/verification).
     */
    fun stepNames(): List<String> = orderedSteps.map { it.name }

    /**
     * Returns the ordered list of step orders (for parity verification).
     */
    fun stepOrders(): List<Int> = orderedSteps.map { it.order }
}
