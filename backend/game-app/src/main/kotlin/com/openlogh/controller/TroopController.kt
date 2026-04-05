package com.openlogh.controller

import com.openlogh.dto.CreateTroopRequest
import com.openlogh.dto.RenameTroopRequest
import com.openlogh.dto.TroopActionRequest
import com.openlogh.dto.TroopResponse
import com.openlogh.dto.TroopWithMembers
import com.openlogh.service.FleetService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class TroopController(
    private val fleetService: FleetService,
) {
    @GetMapping("/nations/{nationId}/troops")
    fun listByNation(@PathVariable nationId: Long): ResponseEntity<List<TroopWithMembers>> {
        return ResponseEntity.ok(fleetService.listByNation(nationId))
    }

    @PostMapping("/troops")
    fun create(@RequestBody request: CreateTroopRequest): ResponseEntity<TroopResponse> {
        val troop = fleetService.create(request.worldId, request.leaderGeneralId, request.nationId, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(TroopResponse.from(troop))
    }

    @PostMapping("/troops/{id}/join")
    fun join(@PathVariable id: Long, @RequestBody request: TroopActionRequest): ResponseEntity<Void> {
        if (!fleetService.join(id, request.generalId)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/troops/{id}/exit")
    fun exit(@PathVariable id: Long, @RequestBody request: TroopActionRequest): ResponseEntity<Void> {
        if (!fleetService.exit(request.generalId)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/troops/{id}/kick")
    fun kick(@PathVariable id: Long, @RequestBody request: TroopActionRequest): ResponseEntity<Void> {
        if (!fleetService.exit(request.generalId)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/troops/{id}")
    fun rename(@PathVariable id: Long, @RequestBody request: RenameTroopRequest): ResponseEntity<TroopResponse> {
        val troop = fleetService.rename(id, request.name)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(TroopResponse.from(troop))
    }

    @DeleteMapping("/troops/{id}")
    fun disband(@PathVariable id: Long): ResponseEntity<Void> {
        if (!fleetService.disband(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }
}
