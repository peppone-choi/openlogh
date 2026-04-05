package com.openlogh.controller

import com.openlogh.dto.CityResponse
import com.openlogh.service.PlanetService
import com.openlogh.service.OfficerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CityController(
    private val planetService: PlanetService,
    private val officerService: OfficerService,
) {
    @GetMapping("/worlds/{worldId}/cities")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<CityResponse>> {
        return ResponseEntity.ok(
            planetService.listByWorld(worldId).map { city ->
                CityResponse.from(city, planetService.canonicalRegionForDisplay(city))
            },
        )
    }

    @GetMapping("/worlds/{worldId}/cities/visible")
    fun listVisibleByWorld(@PathVariable worldId: Long): ResponseEntity<List<CityResponse>> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val myGeneral = officerService.getMyOfficer(worldId, loginId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(
            planetService.listByWorldMaskedForOfficer(worldId, myGeneral).map { city ->
                CityResponse.from(city, planetService.canonicalRegionForDisplay(city))
            },
        )
    }

    @GetMapping("/cities/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<CityResponse> {
        val city = planetService.getById(id)
            ?: return ResponseEntity.notFound().build()
        val loginId = SecurityContextHolder.getContext().authentication?.name
        val maskedCity = if (loginId != null) {
            val myGeneral = officerService.getMyOfficer(city.sessionId, loginId)
            if (myGeneral != null) {
                planetService.listByWorldMaskedForOfficer(city.sessionId, myGeneral)
                    .find { it.id == id } ?: city
            } else city
        } else city
        return ResponseEntity.ok(CityResponse.from(maskedCity, planetService.canonicalRegionForDisplay(maskedCity)))
    }

    @GetMapping("/nations/{nationId}/cities")
    fun listByNation(@PathVariable nationId: Long): ResponseEntity<List<CityResponse>> {
        return ResponseEntity.ok(
            planetService.listByNation(nationId).map { city ->
                CityResponse.from(city, planetService.canonicalRegionForDisplay(city))
            },
        )
    }
}
