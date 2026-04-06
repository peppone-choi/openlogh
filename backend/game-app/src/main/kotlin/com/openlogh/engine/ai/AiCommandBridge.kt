package com.openlogh.engine.ai

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.model.PositionCardRegistry
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Bridge between the AI utility scorer and the CommandExecutor pipeline.
 *
 * Selects the best-scoring available gin7 command for an offline player officer
 * via UtilityScorer, then executes it through CommandExecutor (with full CP
 * deduction, cooldown, and result broadcast).
 *
 * Falls back to "대기" (always-allowed rest action) when:
 *   - No accessible commands are available
 *   - Top-3 candidates all fail validation
 *   - Any unexpected exception occurs
 */
@Service
class AiCommandBridge(
    private val commandExecutor: CommandExecutor,
    private val commandRegistry: CommandRegistry,
) {
    private val logger = LoggerFactory.getLogger(AiCommandBridge::class.java)

    companion object {
        private const val FALLBACK_COMMAND = "대기"
        private const val TOP_CANDIDATES = 3
    }

    /**
     * Execute the best-scoring available gin7 command for this officer.
     * Returns the command name that was attempted (or "대기" on fallback).
     */
    fun executeAiCommand(officer: Officer, world: SessionState, trait: PersonalityTrait): String {
        return try {
            val env = buildCommandEnv(world)

            // Collect all registered officer commands
            val registeredCommands = commandRegistry.getGeneralCommandNames().toList()

            // Rank candidates by utility score (filters by position card access internally)
            val candidates = UtilityScorer.rankCandidates(
                availableCommands = registeredCommands,
                officer = officer,
                trait = trait,
                commandGroupOf = { cmd -> PositionCardRegistry.getCommandGroup(cmd).name },
            )

            // Try top-N candidates in order
            val topCandidates = candidates.take(TOP_CANDIDATES)
            for (candidate in topCandidates) {
                try {
                    val result = runBlocking {
                        commandExecutor.executeOfficerCommand(
                            actionCode = candidate.commandName,
                            general = officer,
                            env = env,
                        )
                    }
                    if (result.success) {
                        return candidate.commandName
                    }
                } catch (e: Exception) {
                    logger.debug(
                        "AI command {} failed for officer {}: {}",
                        candidate.commandName, officer.id, e.message
                    )
                }
            }

            // Fallback: execute 대기
            executeFallback(officer, env)
        } catch (e: Exception) {
            logger.warn("AiCommandBridge error for officer {}: {}", officer.id, e.message)
            FALLBACK_COMMAND
        }
    }

    private fun executeFallback(officer: Officer, env: CommandEnv): String {
        return try {
            runBlocking {
                commandExecutor.executeOfficerCommand(
                    actionCode = FALLBACK_COMMAND,
                    general = officer,
                    env = env,
                )
            }
            FALLBACK_COMMAND
        } catch (e: Exception) {
            logger.debug("Fallback 대기 also failed for officer {}: {}", officer.id, e.message)
            FALLBACK_COMMAND
        }
    }

    private fun buildCommandEnv(world: SessionState): CommandEnv = CommandEnv(
        year = world.currentYear.toInt(),
        month = world.currentMonth.toInt(),
        startYear = world.currentYear.toInt(),
        sessionId = world.id.toLong(),
        realtimeMode = true,
    )
}
