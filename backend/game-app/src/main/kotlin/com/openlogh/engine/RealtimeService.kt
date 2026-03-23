package com.openlogh.engine

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.command.CommandResult
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.CommandLogDispatcher
import com.openlogh.service.GameConstService
import com.openlogh.service.GameEventService
import com.openlogh.service.ScenarioService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val gameConstService: GameConstService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(RealtimeService::class.java)
        private const val MAX_COMMAND_POINTS = 100
        private const val NATION_COMMAND_MIN_RANK: Short = 9
    }

    fun submitCommand(officerId: Long, commandName: String, params: Map<String, Any>?): CommandResult {
        val officer = officerRepository.findById(officerId).orElse(null)
            ?: return CommandResult(success = false, logs = listOf("Officer not found"))

        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null)
            ?: return CommandResult(success = false, logs = listOf("World not found"))

        if (!world.realtimeMode) {
            return CommandResult(success = false, logs = listOf("World is not in realtime mode"))
        }

        if (officer.commandEndTime != null && officer.commandEndTime!!.isAfter(OffsetDateTime.now())) {
            return CommandResult(success = false, logs = listOf("Command already in progress"))
        }

        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = factionRepository.findById(officer.factionId).orElse(null)

        val env = CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = (world.config["startYear"] as? Number)?.toInt() ?: 180,
            worldId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
        )

        return try {
            @Suppress("Since15")
            kotlinx.coroutines.runBlocking {
                commandExecutor.executeGeneralCommand(
                    actionCode = commandName,
                    general = officer,
                    env = env,
                    city = planet,
                    nation = faction,
                    arg = params,
                    registry = commandRegistry,
                )
            }
        } catch (e: Exception) {
            log.warn("Command execution failed for officer {}: {}", officerId, e.message)
            CommandResult(success = false, logs = listOf(e.message ?: "Command failed"))
        }
    }

    fun submitNationCommand(officerId: Long, commandName: String, params: Map<String, Any>?): CommandResult {
        val officer = officerRepository.findById(officerId).orElse(null)
            ?: return CommandResult(success = false, logs = listOf("Officer not found"))

        if (officer.rank < NATION_COMMAND_MIN_RANK) {
            return CommandResult(success = false, logs = listOf("권한이 부족합니다"))
        }

        val faction = factionRepository.findById(officer.factionId).orElse(null)
            ?: return CommandResult(success = false, logs = listOf("Faction not found"))

        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElse(null)
            ?: return CommandResult(success = false, logs = listOf("World not found"))

        val env = CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = (world.config["startYear"] as? Number)?.toInt() ?: 180,
            worldId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
        )

        return try {
            @Suppress("Since15")
            kotlinx.coroutines.runBlocking {
                commandExecutor.executeGeneralCommand(
                    actionCode = commandName,
                    general = officer,
                    env = env,
                    nation = faction,
                    arg = params,
                    registry = commandRegistry,
                )
            }
        } catch (e: Exception) {
            log.warn("Nation command failed for officer {}: {}", officerId, e.message)
            CommandResult(success = false, logs = listOf(e.message ?: "Command failed"))
        }
    }

    fun processCompletedCommands(world: SessionState) {
        val sessionId = world.id.toLong()
        val now = OffsetDateTime.now()
        val completedOfficers = officerRepository.findBySessionIdAndCommandEndTimeBefore(sessionId, now)

        for (officer in completedOfficers) {
            try {
                officer.commandEndTime = null
                officerRepository.save(officer)
                gameEventService.broadcastEvent(
                    sessionId,
                    "command_complete",
                    mapOf("officerId" to officer.id),
                )
            } catch (e: Exception) {
                log.warn("Error completing command for officer {}", officer.id, e)
            }
        }
    }

    fun regenerateCommandPoints(world: SessionState) {
        val sessionId = world.id.toLong()
        val officers = officerRepository.findBySessionId(sessionId)
        val regenRate = world.commandPointRegenRate

        for (officer in officers) {
            val newPoints = (officer.commandPoints + regenRate).coerceAtMost(MAX_COMMAND_POINTS)
            if (newPoints != officer.commandPoints) {
                officer.commandPoints = newPoints
                officerRepository.save(officer)
            }
        }
    }

    fun getRealtimeStatus(officerId: Long): Map<String, Any>? {
        val officer = officerRepository.findById(officerId).orElse(null) ?: return null
        return mapOf(
            "officerId" to officer.id,
            "commandPoints" to officer.commandPoints,
            "commandEndTime" to (officer.commandEndTime?.toString() ?: ""),
            "inProgress" to (officer.commandEndTime != null && officer.commandEndTime!!.isAfter(OffsetDateTime.now())),
        )
    }

    fun executePreOpenCommand(officerId: Long, commandName: String) {
        val officer = officerRepository.findById(officerId).orElseThrow()
        val world = sessionStateRepository.findById(officer.sessionId.toShort()).orElseThrow()
        val planet = planetRepository.findById(officer.planetId).orElse(null)
        val faction = factionRepository.findById(officer.factionId).orElse(null)

        val env = CommandEnv(
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
            startYear = (world.config["startYear"] as? Number)?.toInt() ?: 180,
            worldId = world.id.toLong(),
            realtimeMode = world.realtimeMode,
        )

        @Suppress("Since15")
        kotlinx.coroutines.runBlocking {
            commandExecutor.executeGeneralCommand(
                actionCode = commandName,
                general = officer,
                env = env,
                city = planet,
                nation = faction,
                registry = commandRegistry,
            )
        }
    }
}
