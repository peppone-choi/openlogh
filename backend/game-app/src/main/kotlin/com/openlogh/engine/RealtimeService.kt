package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandResult
import com.openlogh.command.CommandRegistry
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.trigger.TriggerCaller
import com.openlogh.engine.trigger.TriggerEnv
import com.openlogh.engine.trigger.buildPreTurnTriggers
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.CommandLogDispatcher
import com.openlogh.service.CpService
import com.openlogh.service.GameEventService
import com.openlogh.service.ScenarioService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RealtimeService(
    private val officerRepository: OfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val commandExecutor: CommandExecutor,
    private val commandRegistry: CommandRegistry,
    private val gameEventService: GameEventService,
    private val scenarioService: ScenarioService,
    private val modifierService: ModifierService,
    private val commandLogDispatcher: CommandLogDispatcher,
    private val gameConstService: com.openlogh.service.GameConstService,
    private val cpService: CpService,
) {
    private val logger = LoggerFactory.getLogger(RealtimeService::class.java)

    @Transactional
    fun submitCommand(generalId: Long, actionCode: String, arg: Map<String, Any>?): CommandResult {
        val general = officerRepository.findById(generalId).orElseThrow {
            IllegalArgumentException("General not found: $generalId")
        }

        val world = sessionStateRepository.findById(general.sessionId.toShort()).orElseThrow {
            IllegalArgumentException("World not found: ${general.sessionId}")
        }
        if (!world.realtimeMode) {
            return CommandResult(success = false, logs = listOf("This world is not in realtime mode."))
        }

        if (general.commandEndTime != null && general.commandEndTime!!.isAfter(OffsetDateTime.now())) {
            return CommandResult(success = false, logs = listOf("Command already in progress."))
        }

        return scheduleCommand(general, world, actionCode, arg, isNationCommand = false)
    }

    @Transactional
    fun submitNationCommand(generalId: Long, actionCode: String, arg: Map<String, Any>?): CommandResult {
        val general = officerRepository.findById(generalId).orElseThrow {
            IllegalArgumentException("General not found: $generalId")
        }

        if (general.officerLevel < 5) {
            return CommandResult(success = false, logs = listOf("국가 명령 권한이 없습니다."))
        }

        val world = sessionStateRepository.findById(general.sessionId.toShort()).orElseThrow {
            IllegalArgumentException("World not found: ${general.sessionId}")
        }
        if (!world.realtimeMode) {
            return CommandResult(success = false, logs = listOf("This world is not in realtime mode."))
        }

        if (general.commandEndTime != null && general.commandEndTime!!.isAfter(OffsetDateTime.now())) {
            return CommandResult(success = false, logs = listOf("Command already in progress."))
        }

        return scheduleCommand(general, world, actionCode, arg, isNationCommand = true)
    }

    /**
     * Execute a command immediately during pre-open phase.
     * Legacy parity: BuildNationCandidate / DieOnPrestart run instantly,
     * bypassing realtimeMode check and scheduling (turn daemon skips pre-open worlds).
     */
    @Transactional
    fun executePreOpenCommand(generalId: Long, actionCode: String, arg: Map<String, Any>? = null): CommandResult {
        val general = officerRepository.findById(generalId).orElseThrow {
            IllegalArgumentException("General not found: $generalId")
        }
        val world = sessionStateRepository.findById(general.sessionId.toShort()).orElseThrow {
            IllegalArgumentException("World not found: ${general.sessionId}")
        }
        val env = buildCommandEnv(world)
        val city = planetRepository.findById(general.planetId).orElse(null)
        val nation = if (general.factionId != 0L) {
            factionRepository.findById(general.factionId).orElse(null)
        } else null

        val command = commandRegistry.createOfficerCommand(actionCode, general, env, arg)
        command.city = city
        command.nation = nation
        commandExecutor.hydrateCommandForConstraintCheck(command, general, env, arg)

        val conditionResult = command.checkFullCondition()
        if (conditionResult is ConstraintResult.Fail) {
            return CommandResult(success = false, logs = listOf(conditionResult.reason))
        }

        val result = runBlocking {
            commandExecutor.executeOfficerCommand(actionCode, general, env, arg, city, nation)
        }

        if (result.logs.isNotEmpty()) {
            try {
                commandLogDispatcher.dispatchLogs(
                    worldId = world.id.toLong(),
                    generalId = general.id,
                    nationId = if (general.factionId != 0L) general.factionId else null,
                    year = env.year,
                    month = env.month,
                    logs = result.logs,
                )
            } catch (e: Exception) { logger.warn("Failed to push realtime result: {}", e.message) }
        }

        gameEventService.sendToOfficer(general.id, mapOf(
            "type" to "command_completed",
            "actionCode" to actionCode,
            "success" to result.success,
            "logs" to result.logs,
        ))

        return result
    }

    @Transactional
    fun processCompletedCommands(world: SessionState) {
        val now = OffsetDateTime.now()
        val generals = officerRepository.findBySessionIdAndCommandEndTimeBefore(world.id.toLong(), now)

        for (general in generals) {
            try {
                val turns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(general.id)
                if (turns.isEmpty()) {
                    general.commandEndTime = null
                    officerRepository.save(general)
                    continue
                }

                val gt = turns.first()
                val env = buildCommandEnv(world)
                val city = planetRepository.findById(general.planetId).orElse(null)
                val nation = if (general.factionId != 0L) {
                    factionRepository.findById(general.factionId).orElse(null)
                } else null

                val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
                val rng = DeterministicRng.create(
                    hiddenSeed, "realtime_complete", general.id, world.currentYear, world.currentMonth, gt.actionCode
                )

                firePreTurnTriggers(world, general, nation)

                val result = runBlocking {
                    if (commandRegistry.hasNationCommand(gt.actionCode)) {
                        commandExecutor.executeFactionCommand(gt.actionCode, general, env, gt.arg, city, nation, rng)
                    } else {
                        commandExecutor.executeOfficerCommand(gt.actionCode, general, env, gt.arg, city, nation, rng)
                    }
                }

                if (result.logs.isNotEmpty()) {
                    val env2 = buildCommandEnv(world)
                    try {
                        commandLogDispatcher.dispatchLogs(
                            worldId = world.id.toLong(),
                            generalId = general.id,
                            nationId = if (general.factionId != 0L) general.factionId else null,
                            year = env2.year,
                            month = env2.month,
                            logs = result.logs,
                        )
                    } catch (e: Exception) { logger.warn("Failed to push realtime result: {}", e.message) }
                }

                officerTurnRepository.delete(gt)
                general.commandEndTime = null
                general.updatedAt = OffsetDateTime.now()
                officerRepository.save(general)

                gameEventService.sendToOfficer(general.id, mapOf(
                    "type" to "command_completed",
                    "actionCode" to gt.actionCode,
                    "success" to result.success,
                    "logs" to result.logs
                ))
            } catch (e: Exception) {
                logger.error("Error processing completed command for general ${general.id}: ${e.message}", e)
            }
        }
    }

    @Transactional
    fun regenerateCommandPoints(world: SessionState) {
        val generals = officerRepository.findBySessionId(world.id.toLong())
        for (general in generals) {
            val oldPcp = general.pcp
            val oldMcp = general.mcp
            cpService.regeneratePcpMcp(general)
            if (general.pcp != oldPcp || general.mcp != oldMcp) {
                general.commandPoints = general.pcp + general.mcp  // backward compat
                officerRepository.save(general)
            }
        }
    }

    fun getRealtimeStatus(generalId: Long): Map<String, Any?>? {
        val general = officerRepository.findById(generalId).orElse(null) ?: return null
        val now = OffsetDateTime.now()
        val remainingSeconds = if (general.commandEndTime != null && general.commandEndTime!!.isAfter(now)) {
            java.time.Duration.between(now, general.commandEndTime).seconds
        } else {
            0L
        }
        return mapOf(
            "generalId" to general.id,
            "commandPoints" to general.commandPoints,
            "commandEndTime" to general.commandEndTime,
            "remainingSeconds" to remainingSeconds,
            "pcp" to general.pcp,
            "mcp" to general.mcp,
            "pcpMax" to general.pcpMax,
            "mcpMax" to general.mcpMax,
        )
    }

    private fun buildCommandEnv(world: SessionState): CommandEnv {
        val startYear = try {
            scenarioService.getScenario(world.scenarioCode).startYear
        } catch (e: Exception) {
            logger.warn("Failed to resolve startYear for scenario {}: {}", world.scenarioCode, e.message)
            world.currentYear.toInt()
        }

        return CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = startYear,
            sessionId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
            trainDelta = gameConstService.getDouble("trainDelta"),
            atmosDelta = gameConstService.getDouble("atmosDelta"),
            maxTrainByCommand = gameConstService.getInt("maxTrainByCommand"),
            maxAtmosByCommand = gameConstService.getInt("maxAtmosByCommand"),
            atmosSideEffectByTraining = gameConstService.getDouble("atmosSideEffectByTraining"),
            trainSideEffectByAtmosTurn = gameConstService.getDouble("trainSideEffectByAtmosTurn"),
        )
    }

    private fun scheduleCommand(
        general: com.openlogh.entity.Officer,
        world: SessionState,
        actionCode: String,
        arg: Map<String, Any>?,
        isNationCommand: Boolean,
    ): CommandResult {
        val env = buildCommandEnv(world)
        val city = planetRepository.findById(general.planetId).orElse(null)
        val nation = if (general.factionId != 0L) {
            factionRepository.findById(general.factionId).orElse(null)
        } else null

        val command = if (isNationCommand) {
            commandRegistry.createFactionCommand(actionCode, general, env, arg)
                ?: return CommandResult(success = false, logs = listOf("알 수 없는 국가 명령: $actionCode"))
        } else {
            commandRegistry.createOfficerCommand(actionCode, general, env, arg)
        }

        command.city = city
        command.nation = nation
        commandExecutor.hydrateCommandForConstraintCheck(command, general, env, arg)

        val conditionResult = command.checkFullCondition()
        if (conditionResult is ConstraintResult.Fail) {
            if (!isNationCommand) {
                val altCode = command.getAlternativeCommand()
                if (altCode != null && altCode != actionCode) {
                    return scheduleCommand(general, world, altCode, arg, isNationCommand = false)
                }
            }
            return CommandResult(success = false, logs = listOf(conditionResult.reason))
        }

        val commandPointCost = command.getCommandPointCost().coerceAtLeast(1)
        val poolType = command.getCommandPoolType()
        val deduction = cpService.deductCp(general, commandPointCost, poolType)
        if (!deduction.success) {
            return CommandResult(
                success = false,
                logs = listOf(deduction.errorMessage!!)
            )
        }

        val duration = command.getDuration().coerceAtLeast(1)

        general.commandPoints = general.pcp + general.mcp  // backward compat
        general.commandEndTime = OffsetDateTime.now().plusSeconds(duration.toLong())
        general.updatedAt = OffsetDateTime.now()
        officerRepository.save(general)

        officerTurnRepository.deleteByOfficerId(general.id)
        officerTurnRepository.save(
            OfficerTurn(
                sessionId = general.sessionId,
                officerId = general.id,
                turnIdx = 0,
                actionCode = actionCode,
                arg = arg?.toMutableMap() ?: mutableMapOf(),
                brief = command.actionName,
            )
        )

        gameEventService.sendToOfficer(
            general.id,
            mapOf(
                "type" to "command_scheduled",
                "actionCode" to actionCode,
                "name" to command.actionName,
                "commandPointCost" to commandPointCost,
                "durationSeconds" to duration,
                "commandEndTime" to general.commandEndTime,
                "remainingCommandPoints" to general.commandPoints,
                "pcp" to general.pcp,
                "mcp" to general.mcp,
                "pcpMax" to general.pcpMax,
                "mcpMax" to general.mcpMax,
                "crossUsed" to deduction.crossUsed,
            )
        )

        return CommandResult(
            success = true,
            logs = listOf("${command.actionName} 명령이 접수되었습니다. ${duration}초 후 실행됩니다."),
        )
    }

    private fun firePreTurnTriggers(world: SessionState, general: com.openlogh.entity.Officer, nation: Faction?) {
        val modifiers = modifierService.getModifiers(general, nation)
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()
        val preTurnRng = DeterministicRng.create(
            hiddenSeed, "preTurnTrigger", general.id, world.currentYear, world.currentMonth
        )
        val triggers = buildPreTurnTriggers(general, modifiers, rng = preTurnRng)
        if (triggers.isEmpty()) return

        val caller = TriggerCaller()
        caller.addAll(triggers)
        caller.fire(
            TriggerEnv(
                worldId = world.id.toLong(),
                year = world.currentYear.toInt(),
                month = world.currentMonth.toInt(),
                generalId = general.id,
            )
        )
    }
}
