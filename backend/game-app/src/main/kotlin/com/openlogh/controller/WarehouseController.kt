package com.openlogh.controller

import com.openlogh.entity.FleetWarehouse
import com.openlogh.entity.PlanetWarehouse
import com.openlogh.service.WarehouseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/warehouse")
class WarehouseController(
    private val warehouseService: WarehouseService,
) {
    // ===== Planet Warehouse =====

    @GetMapping("/planet/{planetId}")
    fun getPlanetWarehouse(
        @PathVariable planetId: Long,
        @RequestParam sessionId: Long,
    ): ResponseEntity<PlanetWarehouseDto> {
        val pw = warehouseService.getPlanetWarehouse(sessionId, planetId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(PlanetWarehouseDto.from(pw))
    }

    // ===== Fleet Warehouse =====

    @GetMapping("/fleet/{fleetId}")
    fun getFleetWarehouse(
        @PathVariable fleetId: Long,
        @RequestParam sessionId: Long,
    ): ResponseEntity<FleetWarehouseDto> {
        val fw = warehouseService.getFleetWarehouse(sessionId, fleetId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(FleetWarehouseDto.from(fw))
    }
}

// ===== DTOs =====

data class PlanetWarehouseDto(
    val id: Long,
    val sessionId: Long,
    val planetId: Long,
    val battleship: Int,
    val cruiser: Int,
    val destroyer: Int,
    val carrier: Int,
    val transport: Int,
    val hospital: Int,
    val crewGreen: Int,
    val crewNormal: Int,
    val crewVeteran: Int,
    val crewElite: Int,
    val supplies: Int,
    val missiles: Int,
    val hasShipyard: Boolean,
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        fun from(pw: PlanetWarehouse) = PlanetWarehouseDto(
            id = pw.id,
            sessionId = pw.sessionId,
            planetId = pw.planetId,
            battleship = pw.battleship,
            cruiser = pw.cruiser,
            destroyer = pw.destroyer,
            carrier = pw.carrier,
            transport = pw.transport,
            hospital = pw.hospital,
            crewGreen = pw.crewGreen,
            crewNormal = pw.crewNormal,
            crewVeteran = pw.crewVeteran,
            crewElite = pw.crewElite,
            supplies = pw.supplies,
            missiles = pw.missiles,
            hasShipyard = pw.hasShipyard,
            createdAt = pw.createdAt.toString(),
            updatedAt = pw.updatedAt.toString(),
        )
    }
}

data class FleetWarehouseDto(
    val id: Long,
    val sessionId: Long,
    val fleetId: Long,
    val battleship: Int,
    val cruiser: Int,
    val destroyer: Int,
    val carrier: Int,
    val transport: Int,
    val hospital: Int,
    val crewGreen: Int,
    val crewNormal: Int,
    val crewVeteran: Int,
    val crewElite: Int,
    val supplies: Int,
    val missiles: Int,
    val createdAt: String,
    val updatedAt: String,
) {
    companion object {
        fun from(fw: FleetWarehouse) = FleetWarehouseDto(
            id = fw.id,
            sessionId = fw.sessionId,
            fleetId = fw.fleetId,
            battleship = fw.battleship,
            cruiser = fw.cruiser,
            destroyer = fw.destroyer,
            carrier = fw.carrier,
            transport = fw.transport,
            hospital = fw.hospital,
            crewGreen = fw.crewGreen,
            crewNormal = fw.crewNormal,
            crewVeteran = fw.crewVeteran,
            crewElite = fw.crewElite,
            supplies = fw.supplies,
            missiles = fw.missiles,
            createdAt = fw.createdAt.toString(),
            updatedAt = fw.updatedAt.toString(),
        )
    }
}
