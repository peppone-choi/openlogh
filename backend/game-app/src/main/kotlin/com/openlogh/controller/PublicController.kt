package com.openlogh.controller

import com.openlogh.dto.GalaxyMapDto
import com.openlogh.dto.PublicCachedMapResponse
import com.openlogh.service.PublicCachedGalaxyService
import com.openlogh.service.PublicCachedMapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/public")
class PublicController(
    private val publicCachedMapService: PublicCachedMapService,
    private val publicCachedGalaxyService: PublicCachedGalaxyService,
) {
    @GetMapping("/cached-map")
    fun getCachedMap(@RequestParam worldId: Short? = null): ResponseEntity<PublicCachedMapResponse> {
        return ResponseEntity.ok(publicCachedMapService.getCachedMap(worldId))
    }

    @GetMapping("/cached-galaxy")
    fun getCachedGalaxy(@RequestParam worldId: Short? = null): ResponseEntity<GalaxyMapDto> {
        return ResponseEntity.ok(publicCachedGalaxyService.getCachedGalaxy(worldId))
    }
}
