package com.openlogh.controller

import com.openlogh.dto.OfficerResponse
import com.openlogh.dto.PlanetResponse
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.PlanetService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class PlanetController(
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val planetService: PlanetService,
) {
    // GET /api/worlds/{worldId}/planets — 세계의 행성 목록
    @GetMapping("/worlds/{worldId}/planets")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<PlanetResponse>> {
        val planets = planetRepository.findBySessionId(worldId)
        return ResponseEntity.ok(planets.map { planet ->
            val region = planetService.canonicalRegionForDisplay(planet)
            PlanetResponse.from(planet, region)
        })
    }

    // GET /api/worlds/{worldId}/planets/visible — 가시 행성 목록
    @GetMapping("/worlds/{worldId}/planets/visible")
    fun listVisible(@PathVariable worldId: Long): ResponseEntity<List<PlanetResponse>> {
        // For now, return all planets; visibility filtering can be refined later
        val planets = planetRepository.findBySessionId(worldId)
        return ResponseEntity.ok(planets.map { planet ->
            val region = planetService.canonicalRegionForDisplay(planet)
            PlanetResponse.from(planet, region)
        })
    }

    // GET /api/planets/{id} — 행성 상세
    @GetMapping("/planets/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<PlanetResponse> {
        val planet = planetRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val region = planetService.canonicalRegionForDisplay(planet)
        return ResponseEntity.ok(PlanetResponse.from(planet, region))
    }

    // GET /api/planets/{planetId}/officers — 행성 소속 장교 목록
    @GetMapping("/planets/{planetId}/officers")
    fun listOfficers(@PathVariable planetId: Long): ResponseEntity<List<OfficerResponse>> {
        val officers = officerRepository.findByCityId(planetId)
        return ResponseEntity.ok(officers.map { OfficerResponse.from(it) })
    }
}
