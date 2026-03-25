package com.openlogh.controller

import com.openlogh.dto.CreateWorldRequest
import com.openlogh.dto.ResetWorldRequest
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.ScenarioService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

data class WorldStateResponse(
    val id: Short,
    val name: String,
    val scenarioCode: String,
    val commitSha: String,
    val gameVersion: String,
    val currentYear: Short,
    val currentMonth: Short,
    val tickSeconds: Int,
    val realtimeMode: Boolean,
    val commandPointRegenRate: Int,
    val config: Map<String, Any>,
    val meta: Map<String, Any>,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun from(e: SessionState) = WorldStateResponse(
            id = e.id,
            name = e.name,
            scenarioCode = e.scenarioCode,
            commitSha = e.commitSha,
            gameVersion = e.gameVersion,
            currentYear = e.currentYear,
            currentMonth = e.currentMonth,
            tickSeconds = e.tickSeconds,
            realtimeMode = e.realtimeMode,
            commandPointRegenRate = e.commandPointRegenRate,
            config = e.config,
            meta = e.meta,
            updatedAt = e.updatedAt,
        )
    }
}

@RestController
@RequestMapping("/api/worlds")
class WorldCreationController(
    private val scenarioService: ScenarioService,
    private val sessionStateRepository: SessionStateRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun createWorld(@Valid @RequestBody request: CreateWorldRequest): ResponseEntity<WorldStateResponse> {
        return try {
            val world = scenarioService.initializeWorld(
                scenarioCode = request.scenarioCode,
                tickSeconds = request.tickSeconds,
                commitSha = request.commitSha,
                gameVersion = request.gameVersion,
                extendEnabled = request.extend,
                npcMode = request.npcMode,
                fiction = request.fiction,
                maxGeneral = request.maxOfficer,
                maxNation = request.maxFaction,
                joinMode = request.joinMode,
                blockGeneralCreate = request.blockOfficerCreate,
                showImgLevel = request.showImgLevel,
                autorunUser = request.autorunUser,
                startTime = request.startTime,
                opentime = request.opentime,
            )

            if (request.name != null) {
                world.name = request.name
                sessionStateRepository.save(world)
            }

            log.info("World created: id={}, scenario={}", world.id, world.scenarioCode)
            ResponseEntity.status(HttpStatus.CREATED).body(WorldStateResponse.from(world))
        } catch (e: IllegalArgumentException) {
            log.error("createWorld bad request: {}", e.message)
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error("createWorld error: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/{id}")
    fun getWorld(@PathVariable id: Short): ResponseEntity<WorldStateResponse> {
        val world = sessionStateRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(WorldStateResponse.from(world))
    }

    @PostMapping("/{id}/reset")
    fun resetWorld(
        @PathVariable id: Short,
        @RequestBody(required = false) body: ResetWorldRequest?,
    ): ResponseEntity<WorldStateResponse> {
        val world = sessionStateRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        return try {
            val scenarioCode = body?.scenarioCode ?: world.scenarioCode
            val reset = scenarioService.reinitializeWorld(
                existingWorld = world,
                scenarioCode = scenarioCode,
                extendEnabled = body?.extend,
                npcMode = body?.npcMode,
                fiction = body?.fiction,
                maxGeneral = body?.maxOfficer,
                maxNation = body?.maxFaction,
                joinMode = body?.joinMode,
                blockGeneralCreate = body?.blockOfficerCreate,
                showImgLevel = body?.showImgLevel,
                autorunUser = body?.autorunUser,
                startTime = body?.startTime,
                opentime = body?.opentime,
            )

            log.info("World reset: id={}, scenario={}", reset.id, reset.scenarioCode)
            ResponseEntity.ok(WorldStateResponse.from(reset))
        } catch (e: IllegalArgumentException) {
            log.error("resetWorld bad request: {}", e.message)
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            log.error("resetWorld error: {}", e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteWorld(@PathVariable id: Short): ResponseEntity<Void> {
        if (!sessionStateRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        sessionStateRepository.deleteById(id)
        log.info("World deleted: id={}", id)
        return ResponseEntity.noContent().build()
    }
}
