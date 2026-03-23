package com.openlogh.controller

import com.openlogh.dto.CreateOfficerRequest
import com.openlogh.dto.OfficerResponse
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.service.OfficerService
import com.openlogh.service.WorldService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class OfficerController(
    private val officerService: OfficerService,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val worldService: WorldService,
) {
    // GET /api/worlds/{worldId}/officers — 세계의 장교 목록
    @GetMapping("/worlds/{worldId}/officers")
    fun listByWorld(@PathVariable worldId: Long): ResponseEntity<List<OfficerResponse>> {
        val officers = officerRepository.findBySessionId(worldId)
        return ResponseEntity.ok(officers.map { OfficerResponse.from(it) })
    }

    // GET /api/worlds/{worldId}/officers/me — 내 장교
    @GetMapping("/worlds/{worldId}/officers/me")
    fun getMyOfficer(@PathVariable worldId: Long): ResponseEntity<OfficerResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val user = appUserRepository.findByLoginId(loginId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val officers = officerRepository.findBySessionIdAndUserId(worldId, user.id)
        val officer = officers.firstOrNull()
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OfficerResponse.from(officer))
    }

    // GET /api/officers/{id} — 장교 상세
    @GetMapping("/officers/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<OfficerResponse> {
        val officer = officerRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(OfficerResponse.from(officer))
    }

    // POST /api/worlds/{worldId}/officers — 장교 생성
    @PostMapping("/worlds/{worldId}/officers")
    fun createOfficer(
        @PathVariable worldId: Long,
        @RequestBody req: CreateOfficerRequest,
    ): ResponseEntity<Any> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val world = worldService.getWorld(worldId.toShort())
            ?: return ResponseEntity.notFound().build()
        if (worldService.getGamePhase(world) == WorldService.PHASE_CLOSED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("error" to "Game is closed"))
        }

        return try {
            val officer = officerService.createOfficer(worldId, loginId, req)
                ?: return ResponseEntity.badRequest()
                    .body(mapOf("error" to "Cannot create officer"))
            ResponseEntity.status(HttpStatus.CREATED).body(OfficerResponse.from(officer))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }

    private fun currentLoginId(): String? =
        SecurityContextHolder.getContext().authentication?.name
}
