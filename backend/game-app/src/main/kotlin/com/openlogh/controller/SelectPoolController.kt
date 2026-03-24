package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.service.OfficerService
import com.openlogh.service.ReregistrationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class SelectPoolController(
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val officerService: OfficerService,
    private val reregistrationService: ReregistrationService,
) {
    // GET /api/worlds/{worldId}/pool — 추첨 풀 장교 목록
    @GetMapping("/worlds/{worldId}/pool")
    fun listPool(@PathVariable worldId: Long): ResponseEntity<List<OfficerResponse>> {
        val officers = officerRepository.findBySessionId(worldId)
            .filter { it.npcState == 5.toShort() }
        return ResponseEntity.ok(officers.map { OfficerResponse.from(it) })
    }

    // POST /api/worlds/{worldId}/pool — 풀 장교 생성
    @PostMapping("/worlds/{worldId}/pool")
    fun buildPoolOfficer(
        @PathVariable worldId: Long,
        @RequestBody request: BuildPoolOfficerRequest,
    ): ResponseEntity<Any> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            val officer = officerService.buildPoolOfficer(worldId, loginId, request)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Cannot create pool officer"))
            ResponseEntity.status(HttpStatus.CREATED).body(OfficerResponse.from(officer))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }

    // PUT /api/worlds/{worldId}/pool/{generalId} — 풀 장교 스탯 수정
    @PutMapping("/worlds/{worldId}/pool/{generalId}")
    fun updatePoolOfficer(
        @PathVariable worldId: Long,
        @PathVariable generalId: Long,
        @RequestBody request: UpdatePoolOfficerRequest,
    ): ResponseEntity<OfficerResponse> {
        val officer = officerRepository.findById(generalId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (officer.sessionId != worldId || officer.npcState != 5.toShort()) {
            return ResponseEntity.notFound().build()
        }

        officer.leadership = request.leadership
        officer.command = request.command
        officer.intelligence = request.intelligence
        officer.politics = request.politics
        officer.administration = request.administration
        val saved = officerRepository.save(officer)
        return ResponseEntity.ok(OfficerResponse.from(saved))
    }

    // POST /api/worlds/{worldId}/select-pool — 풀에서 장교 선택
    @PostMapping("/worlds/{worldId}/select-pool")
    fun selectFromPool(
        @PathVariable worldId: Long,
        @RequestBody request: SelectNpcRequest,
    ): ResponseEntity<Any> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val user = appUserRepository.findByLoginId(loginId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val officer = officerRepository.findById(request.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (officer.sessionId != worldId || officer.npcState != 5.toShort()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Officer not in pool"))
        }

        // Check user doesn't already have an officer in this world
        val existing = officerRepository.findBySessionIdAndUserId(worldId, user.id)
        if (existing.isNotEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Already have an officer"))
        }

        // Re-registration restriction check (Feature 1.7)
        val reregCheck = reregistrationService.canReregister(
            worldId, user.id, officer.factionId, officer.personalCode,
        )
        if (!reregCheck.allowed) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to (reregCheck.reason ?: "재가입 제한")))
        }

        officer.userId = user.id
        officer.npcState = 0
        officer.ownerName = user.displayName
        officerRepository.save(officer)

        return ResponseEntity.ok(OfficerResponse.from(officer))
    }

    // POST /api/worlds/{worldId}/select-npc — NPC 직접 선택 (레거시)
    @PostMapping("/worlds/{worldId}/select-npc")
    fun selectNpc(
        @PathVariable worldId: Long,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Any> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val user = appUserRepository.findByLoginId(loginId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val generalId = (body["generalId"] as? Number)?.toLong()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "generalId required"))

        val officer = officerRepository.findById(generalId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (officer.sessionId != worldId) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Officer not in this world"))
        }

        if (officer.userId != null) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Officer already claimed"))
        }

        // Check user doesn't already have an officer
        val existing = officerRepository.findBySessionIdAndUserId(worldId, user.id)
        if (existing.isNotEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Already have an officer"))
        }

        officer.userId = user.id
        officer.npcState = 0
        officer.ownerName = user.displayName
        officerRepository.save(officer)

        return ResponseEntity.ok(OfficerResponse.from(officer))
    }

    private fun currentLoginId(): String? =
        SecurityContextHolder.getContext().authentication?.name
}
