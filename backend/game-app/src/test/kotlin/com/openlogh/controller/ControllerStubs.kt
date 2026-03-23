@file:Suppress("unused")

package com.openlogh.controller

import com.openlogh.dto.BuildPoolGeneralRequest
import com.openlogh.dto.CreateGeneralRequest
import com.openlogh.dto.SelectNpcRequest
import com.openlogh.dto.UpdatePoolGeneralRequest
import com.openlogh.service.FrontInfoService
import com.openlogh.service.OfficerService
import com.openlogh.service.WorldService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

class GeneralController(
    private val generalService: OfficerService,
    private val frontInfoService: FrontInfoService,
    private val worldService: WorldService,
) {
    fun createGeneral(worldId: Long, req: CreateGeneralRequest): ResponseEntity<*> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build<Void>()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }
        return ResponseEntity.ok().build<Void>()
    }

    fun selectNpc(worldId: Long, req: SelectNpcRequest): ResponseEntity<*> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build<Void>()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }
        return ResponseEntity.ok().build<Void>()
    }

    fun selectFromPool(worldId: Long, req: SelectNpcRequest): ResponseEntity<*> {
        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build<Void>()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }
        return ResponseEntity.ok().build<Void>()
    }

    @PostMapping
    fun buildPoolGeneral(@PathVariable worldId: Long, @RequestBody req: BuildPoolGeneralRequest): ResponseEntity<*> =
        ResponseEntity.ok().build<Void>()

    @PutMapping
    fun updatePoolGeneral(
        @PathVariable worldId: Long,
        @PathVariable generalId: Long,
        @RequestBody req: UpdatePoolGeneralRequest,
    ): ResponseEntity<*> = ResponseEntity.ok().build<Void>()
}

class AccountController {
    @PostMapping
    fun uploadIcon(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    @DeleteMapping
    fun deleteIcon(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    @PostMapping
    fun syncIcon(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    @GetMapping
    fun getDetailedInfo(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    fun buildNationCandidate(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    fun instantRetreat(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    fun dieOnPrestart(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()
}

class DiplomacyController {
    @PostMapping
    fun respond(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    fun getRelations(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()

    fun getRelationsForNation(): ResponseEntity<*> = ResponseEntity.ok().build<Void>()
}
