package com.openlogh.controller

import com.openlogh.dto.BuildPoolGeneralRequest
import com.openlogh.dto.CreateGeneralRequest
import com.openlogh.dto.FrontInfoResponse
import com.openlogh.dto.GeneralResponse
import com.openlogh.dto.SelectNpcRequest
import com.openlogh.dto.UpdatePoolGeneralRequest
import com.openlogh.service.FrontInfoService
import com.openlogh.service.OfficerService
import com.openlogh.service.WorldService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class GeneralController(
    private val officerService: OfficerService,
    private val frontInfoService: FrontInfoService,
    private val worldService: WorldService,
) {
    @GetMapping("/worlds/{worldId}/front-info")
    fun getFrontInfo(
        @PathVariable worldId: Long,
        @RequestParam(required = false) lastRecordId: Long?,
        @RequestParam(required = false) lastHistoryId: Long?,
    ): ResponseEntity<FrontInfoResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(frontInfoService.getFrontInfo(worldId, loginId, lastRecordId, lastHistoryId))
    }

    @GetMapping("/worlds/{worldId}/generals")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(officerService.listBySession(worldId).map { GeneralResponse.from(it) })
    }

    @GetMapping("/worlds/{worldId}/generals/me")
    fun getMyOfficer(@PathVariable worldId: Long): ResponseEntity<GeneralResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.getMyOfficer(worldId, loginId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(GeneralResponse.from(general))
    }

    @GetMapping("/generals/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<GeneralResponse> {
        val general = officerService.getById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(GeneralResponse.from(general))
    }

    @GetMapping("/nations/{nationId}/generals")
    fun listByNation(@PathVariable nationId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(officerService.listByNation(nationId).map { GeneralResponse.from(it) })
    }

    @GetMapping("/cities/{cityId}/generals")
    fun listByCity(@PathVariable cityId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(officerService.listByPlanet(cityId).map { GeneralResponse.from(it) })
    }

    @PostMapping("/worlds/{worldId}/generals")
    fun createGeneral(
        @PathVariable worldId: Long,
        @RequestBody request: CreateGeneralRequest,
    ): ResponseEntity<GeneralResponse> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.createOfficer(worldId, loginId, request)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.status(HttpStatus.CREATED).body(GeneralResponse.from(general))
    }

    @GetMapping("/worlds/{worldId}/available-npcs")
    fun listAvailableNpcs(@PathVariable worldId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(officerService.listAvailableNpcs(worldId).map { GeneralResponse.from(it) })
    }

    @PostMapping("/worlds/{worldId}/select-npc")
    fun selectNpc(
        @PathVariable worldId: Long,
        @RequestBody request: SelectNpcRequest,
    ): ResponseEntity<GeneralResponse> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.possessNpc(worldId, loginId, request.generalId)
            ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(GeneralResponse.from(general))
    }

    @GetMapping("/worlds/{worldId}/pool")
    fun listPool(@PathVariable worldId: Long): ResponseEntity<List<GeneralResponse>> {
        return ResponseEntity.ok(officerService.listPool(worldId).map { GeneralResponse.from(it) })
    }

    @PostMapping("/worlds/{worldId}/pool")
    fun buildPoolGeneral(
        @PathVariable worldId: Long,
        @RequestBody request: BuildPoolGeneralRequest,
    ): ResponseEntity<GeneralResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.buildPoolOfficer(worldId, loginId, request)
            ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.status(HttpStatus.CREATED).body(GeneralResponse.from(general))
    }

    @PutMapping("/worlds/{worldId}/pool/{generalId}")
    fun updatePoolGeneral(
        @PathVariable worldId: Long,
        @PathVariable generalId: Long,
        @RequestBody request: UpdatePoolGeneralRequest,
    ): ResponseEntity<GeneralResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.updatePoolOfficer(
            worldId, loginId, generalId,
            request.leadership, request.strength, request.intel, request.politics, request.charm,
        ) ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(GeneralResponse.from(general))
    }

    @PostMapping("/worlds/{worldId}/select-pool")
    fun selectFromPool(
        @PathVariable worldId: Long,
        @RequestBody request: SelectNpcRequest,
    ): ResponseEntity<GeneralResponse> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val general = officerService.selectFromPool(worldId, loginId, request.generalId)
            ?: return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(GeneralResponse.from(general))
    }
}
