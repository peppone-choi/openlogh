package com.opensam.controller

import com.opensam.dto.CityResponse
import com.opensam.service.CityService
import com.opensam.service.GeneralService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CityController(
    private val cityService: CityService,
    private val generalService: GeneralService,
) {
    @GetMapping("/worlds/{worldId}/cities")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<CityResponse>> {
        return ResponseEntity.ok(cityService.listByWorld(worldId).map { CityResponse.from(it) })
    }

    @GetMapping("/worlds/{worldId}/cities/visible")
    fun listVisibleByWorld(@PathVariable worldId: Long): ResponseEntity<List<CityResponse>> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val myGeneral = generalService.getMyGeneral(worldId, loginId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(cityService.listByWorldMaskedForGeneral(worldId, myGeneral).map { CityResponse.from(it) })
    }

    @GetMapping("/cities/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<CityResponse> {
        val city = cityService.getById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(CityResponse.from(city))
    }

    @GetMapping("/nations/{nationId}/cities")
    fun listByNation(@PathVariable nationId: Long): ResponseEntity<List<CityResponse>> {
        return ResponseEntity.ok(cityService.listByNation(nationId).map { CityResponse.from(it) })
    }
}
