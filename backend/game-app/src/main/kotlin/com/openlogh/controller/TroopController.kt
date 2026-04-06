package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.model.CrewSlotRole
import com.openlogh.model.UnitType
import com.openlogh.repository.FleetRepository
import com.openlogh.service.FleetService
import com.openlogh.service.FormationCapService
import com.openlogh.service.UnitCrewService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class TroopController(
    private val fleetService: FleetService,
    private val formationCapService: FormationCapService,
    private val unitCrewService: UnitCrewService,
    private val fleetRepository: FleetRepository,
) {
    // ========== New unit management endpoints ==========

    @PostMapping("/units")
    fun createUnit(@RequestBody request: CreateUnitRequest): ResponseEntity<UnitResponse> {
        val unitType = try {
            UnitType.valueOf(request.unitType)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val fleet = fleetService.create(
            worldId = request.sessionId,
            leaderGeneralId = request.commanderOfficerId,
            nationId = request.factionId,
            name = request.name,
            unitType = unitType,
            planetId = request.planetId,
        )
        val crew = unitCrewService.getCrewRoster(fleet.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(UnitResponse.from(fleet, crew))
    }

    @GetMapping("/factions/{factionId}/units")
    fun listUnits(
        @PathVariable factionId: Long,
        @RequestParam sessionId: Long,
    ): ResponseEntity<List<UnitResponse>> {
        val units = fleetService.listByFaction(sessionId, factionId)
        return ResponseEntity.ok(units.map { fleet ->
            val crew = unitCrewService.getCrewRoster(fleet.id)
            UnitResponse.from(fleet, crew)
        })
    }

    @GetMapping("/factions/{factionId}/formation-caps")
    fun getFormationCaps(
        @PathVariable factionId: Long,
        @RequestParam sessionId: Long,
    ): ResponseEntity<FormationCapResponse> {
        val caps = formationCapService.getFormationCaps(sessionId, factionId)
        val entries = caps.map { (type, cap) -> type.name to FormationCapEntry.from(cap) }.toMap()
        return ResponseEntity.ok(FormationCapResponse(entries))
    }

    @PostMapping("/units/{id}/crew")
    fun assignCrew(
        @PathVariable id: Long,
        @RequestBody request: AssignCrewRequest,
    ): ResponseEntity<CrewMemberResponse> {
        val fleet = fleetRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val slotRole = try {
            CrewSlotRole.valueOf(request.slotRole)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val crew = unitCrewService.assignCrew(fleet, request.officerId, slotRole)
        return ResponseEntity.status(HttpStatus.CREATED).body(CrewMemberResponse.from(crew))
    }

    @DeleteMapping("/units/{id}/crew/{officerId}")
    fun removeCrew(
        @PathVariable id: Long,
        @PathVariable officerId: Long,
    ): ResponseEntity<Void> {
        unitCrewService.removeCrew(id, officerId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/units/{id}/crew")
    fun getCrewRoster(@PathVariable id: Long): ResponseEntity<List<CrewMemberResponse>> {
        val crew = unitCrewService.getCrewRoster(id)
        return ResponseEntity.ok(crew.map { CrewMemberResponse.from(it) })
    }

    // ========== Legacy /troops endpoints (deprecated, delegate to new methods) ==========

    @Deprecated("Use GET /factions/{factionId}/units")
    @GetMapping("/nations/{nationId}/troops")
    fun listByNation(@PathVariable nationId: Long): ResponseEntity<List<TroopWithMembers>> {
        return ResponseEntity.ok(fleetService.listByNation(nationId))
    }

    @Deprecated("Use POST /units")
    @PostMapping("/troops")
    @Suppress("DEPRECATION")
    fun create(@RequestBody request: CreateTroopRequest): ResponseEntity<TroopResponse> {
        val troop = fleetService.create(request.worldId, request.leaderGeneralId, request.nationId, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(TroopResponse.from(troop))
    }

    @Deprecated("Use POST /units/{id}/crew")
    @PostMapping("/troops/{id}/join")
    fun join(@PathVariable id: Long, @RequestBody request: TroopActionRequest): ResponseEntity<Void> {
        if (!fleetService.join(id, request.generalId)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @Deprecated("Use DELETE /units/{id}/crew/{officerId}")
    @PostMapping("/troops/{id}/exit")
    fun exit(@PathVariable id: Long, @RequestBody request: TroopActionRequest): ResponseEntity<Void> {
        if (!fleetService.exit(request.generalId)) return ResponseEntity.notFound().build()
        return ResponseEntity.ok().build()
    }

    @Deprecated("Use DELETE /units/{id}/crew/{officerId}")
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
