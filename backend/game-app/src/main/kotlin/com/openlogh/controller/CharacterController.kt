package com.openlogh.controller

import com.openlogh.dto.OfficerResponse
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.CharacterCreationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/character")
class CharacterController(
    private val characterCreationService: CharacterCreationService,
    private val appUserRepository: AppUserRepository,
    private val factionRepository: FactionRepository,
) {
    data class GenerateRequest(
        val sessionId: Long,
        val factionId: Long,
        val name: String,
        val originType: String,
        val stats: CharacterCreationService.StatAllocation,
        val planetId: Long,
    )

    data class SelectOriginalRequest(
        val sessionId: Long,
        val officerId: Long,
    )

    /** POST /api/character/generate -- 제네레이트 캐릭터 생성 */
    @PostMapping("/generate")
    fun generateCharacter(@RequestBody request: GenerateRequest): ResponseEntity<Any> {
        val userId = resolveUserId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val faction = factionRepository.findById(request.factionId).orElse(null)
            ?: return ResponseEntity.badRequest()
                .body(mapOf("error" to "진영을 찾을 수 없습니다."))

        return try {
            val officer = characterCreationService.createGeneratedOfficer(
                sessionId = request.sessionId,
                userId = userId,
                factionId = request.factionId,
                name = request.name,
                stats = request.stats,
                originType = request.originType,
                factionType = faction.factionType,
                planetId = request.planetId,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(OfficerResponse.from(officer))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "잘못된 요청입니다.")))
        }
    }

    /** POST /api/character/select-original -- 원작 캐릭터 선택 */
    @PostMapping("/select-original")
    fun selectOriginal(@RequestBody request: SelectOriginalRequest): ResponseEntity<Any> {
        val userId = resolveUserId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        return try {
            val officer = characterCreationService.selectOriginalOfficer(
                sessionId = request.sessionId,
                userId = userId,
                officerId = request.officerId,
            )
            ResponseEntity.ok(OfficerResponse.from(officer))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "잘못된 요청입니다.")))
        }
    }

    /** GET /api/character/available-originals -- 선택 가능한 원작 캐릭터 목록 */
    @GetMapping("/available-originals")
    fun getAvailableOriginals(
        @RequestParam sessionId: Long,
        @RequestParam factionId: Long,
    ): ResponseEntity<List<OfficerResponse>> {
        val officers = characterCreationService.getAvailableOriginals(sessionId, factionId)
        return ResponseEntity.ok(officers.map { OfficerResponse.from(it) })
    }

    private fun resolveUserId(): Long? {
        val loginId = SecurityContextHolder.getContext().authentication?.name ?: return null
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        return user.id
    }
}
