package com.openlogh.controller

import com.openlogh.dto.CreateWorldRequest
import com.openlogh.dto.ResetWorldRequest
import com.openlogh.dto.WorldCityOwnershipSnapshotResponse
import com.openlogh.dto.WorldSnapshotResponse
import com.openlogh.dto.WorldStateResponse
import com.openlogh.service.AdminAuthorizationService
import com.openlogh.service.MapRecentService
import com.openlogh.service.PublicCachedMapService
import com.openlogh.service.ScenarioService
import com.openlogh.service.WorldService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class WorldController(
    private val scenarioService: ScenarioService,
    private val worldService: WorldService,
    private val adminAuthorizationService: AdminAuthorizationService,
    private val publicCachedMapService: PublicCachedMapService,
    private val mapRecentService: MapRecentService,
) {
    private fun parseBooleanFlag(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun currentLoginId(): String? = SecurityContextHolder.getContext().authentication?.name

    @GetMapping("/worlds")
    fun listWorlds(): ResponseEntity<List<WorldStateResponse>> {
        return ResponseEntity.ok(worldService.listWorlds().map { WorldStateResponse.from(it) })
    }

    @GetMapping("/worlds/{id}")
    fun getWorld(@PathVariable id: Short): ResponseEntity<WorldStateResponse> {
        val world = worldService.getWorld(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(WorldStateResponse.from(world))
    }

    @GetMapping("/worlds/{id}/summary")
    fun getWorldSummary(@PathVariable id: Short): ResponseEntity<Map<String, Any>> {
        val summary = worldService.getWorldSummary(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(summary)
    }

    @GetMapping("/worlds/{id}/snapshots")
    fun getWorldSnapshots(@PathVariable id: Short): ResponseEntity<List<WorldSnapshotResponse>> {
        val world = worldService.getWorld(id) ?: return ResponseEntity.notFound().build()
        val snapshots = worldService.getSnapshots(world.id.toLong())
            .sortedWith(compareBy<com.openlogh.entity.WorldHistory> { it.year }.thenBy { it.month }.thenBy { it.id })
            .map { history ->
                val cityOwnership = (history.payload["cities"] as? List<*>)
                    ?.mapNotNull { cityRaw ->
                        val cityMap = cityRaw as? Map<*, *> ?: return@mapNotNull null
                        val cityId = (cityMap["id"] as? Number)?.toLong() ?: return@mapNotNull null
                        val nationId = (cityMap["nationId"] as? Number)?.toLong() ?: 0L
                        WorldCityOwnershipSnapshotResponse(cityId = cityId, nationId = nationId)
                    }
                    ?: emptyList()

                val events = (history.payload["events"] as? List<*>)
                    ?.mapNotNull { it?.toString() }
                    ?: emptyList()

                WorldSnapshotResponse(
                    id = history.id,
                    worldId = history.sessionId,
                    year = history.year.toInt(),
                    month = history.month.toInt(),
                    createdAt = history.createdAt.toString(),
                    phase = history.payload["phase"] as? String,
                    season = history.payload["season"] as? String,
                    cityOwnership = cityOwnership,
                    events = events,
                )
            }
        return ResponseEntity.ok(snapshots)
    }

    @PostMapping("/worlds/{id}/snapshots/capture")
    fun captureWorldSnapshot(@PathVariable id: Short): ResponseEntity<WorldSnapshotResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val world = worldService.getWorld(id) ?: return ResponseEntity.notFound().build()
        val snapshot = worldService.captureSnapshot(world)
        val cityOwnership = (snapshot.payload["cities"] as? List<*>)
            ?.mapNotNull { cityRaw ->
                val cityMap = cityRaw as? Map<*, *> ?: return@mapNotNull null
                val cityId = (cityMap["id"] as? Number)?.toLong() ?: return@mapNotNull null
                val nationId = (cityMap["nationId"] as? Number)?.toLong() ?: 0L
                WorldCityOwnershipSnapshotResponse(cityId = cityId, nationId = nationId)
            }
            ?: emptyList()

        return ResponseEntity.status(HttpStatus.CREATED).body(
            WorldSnapshotResponse(
                id = snapshot.id,
                worldId = snapshot.sessionId,
                year = snapshot.year.toInt(),
                month = snapshot.month.toInt(),
                createdAt = snapshot.createdAt.toString(),
                phase = snapshot.payload["phase"] as? String,
                season = snapshot.payload["season"] as? String,
                cityOwnership = cityOwnership,
                events = (snapshot.payload["events"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList(),
            )
        )
    }

    @PostMapping("/worlds")
    fun createWorld(@Valid @RequestBody request: CreateWorldRequest): ResponseEntity<WorldStateResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val world = scenarioService.initializeWorld(
            scenarioCode = request.scenarioCode,
            tickSeconds = request.tickSeconds,
            commitSha = request.commitSha ?: "local",
            gameVersion = request.gameVersion ?: "dev",
            extendEnabled = request.extend,
            npcMode = request.npcMode,
            fiction = request.fiction,
            maxGeneral = request.maxGeneral,
            maxNation = request.maxNation,
            joinMode = request.joinMode,
            blockGeneralCreate = request.blockGeneralCreate,
            showImgLevel = request.showImgLevel,
            autorunUser = request.autorunUser,
            startTime = request.startTime,
            opentime = request.opentime,
        )
        if (!request.name.isNullOrBlank()) {
            world.name = request.name
            worldService.save(world)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(WorldStateResponse.from(world))
    }

    @DeleteMapping("/worlds/{id}")
    fun deleteWorld(@PathVariable id: Short): ResponseEntity<Void> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        worldService.deleteWorld(id)
        publicCachedMapService.evictCache(id)
        mapRecentService.evictCache(id.toLong())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/worlds/{id}/reset")
    fun resetWorld(
        @PathVariable id: Short,
        @RequestBody(required = false) body: ResetWorldRequest?,
    ): ResponseEntity<WorldStateResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val world = worldService.getWorld(id)
            ?: return ResponseEntity.notFound().build()
        val scenarioCode = body?.scenarioCode ?: world.scenarioCode
        val reset = scenarioService.reinitializeWorld(
            existingWorld = world,
            scenarioCode = scenarioCode,
            tickSeconds = world.tickSeconds.toInt(),
            extendEnabled = body?.extend ?: parseBooleanFlag(world.config["extend"] ?: world.config["extendedGeneral"]),
            npcMode = body?.npcMode,
            fiction = body?.fiction,
            maxGeneral = body?.maxGeneral,
            maxNation = body?.maxNation,
            joinMode = body?.joinMode,
            blockGeneralCreate = body?.blockGeneralCreate,
            showImgLevel = body?.showImgLevel,
            autorunUser = body?.autorunUser,
            startTime = body?.startTime,
            opentime = body?.opentime,
        )
        publicCachedMapService.evictCache(id)
        mapRecentService.evictCache(id.toLong())
        return ResponseEntity.ok(WorldStateResponse.from(reset))
    }
}
