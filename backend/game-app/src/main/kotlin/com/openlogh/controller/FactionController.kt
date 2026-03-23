package com.openlogh.controller

import com.openlogh.dto.FactionResponse
import com.openlogh.dto.OfficerResponse
import com.openlogh.dto.PlanetResponse
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.PlanetService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class FactionController(
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val planetService: PlanetService,
) {
    // GET /api/worlds/{worldId}/factions — 세계의 진영 목록
    @GetMapping("/worlds/{worldId}/factions")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<FactionResponse>> {
        val factions = factionRepository.findBySessionId(worldId)
        return ResponseEntity.ok(factions.map { FactionResponse.from(it) })
    }

    // GET /api/factions/{id} — 진영 상세
    @GetMapping("/factions/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<FactionResponse> {
        val faction = factionRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(FactionResponse.from(faction))
    }

    // GET /api/factions/{id}/officers — 진영 소속 장교 목록
    @GetMapping("/factions/{id}/officers")
    fun listOfficers(@PathVariable id: Long): ResponseEntity<List<OfficerResponse>> {
        val officers = officerRepository.findByFactionId(id)
        return ResponseEntity.ok(officers.map { OfficerResponse.from(it) })
    }

    // GET /api/factions/{id}/planets — 진영 소속 행성 목록
    @GetMapping("/factions/{id}/planets")
    fun listPlanets(@PathVariable id: Long): ResponseEntity<List<PlanetResponse>> {
        val planets = planetRepository.findByFactionId(id)
        return ResponseEntity.ok(planets.map { planet ->
            val region = planetService.canonicalRegionForDisplay(planet)
            PlanetResponse.from(planet, region)
        })
    }
}
