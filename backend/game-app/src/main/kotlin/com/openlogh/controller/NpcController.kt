package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.repository.AppUserRepository
import com.openlogh.service.OfficerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api")
class NpcController(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val appUserRepository: AppUserRepository,
    private val officerService: OfficerService,
) {
    // In-memory NPC token store (simple implementation)
    private val npcTokenStore = ConcurrentHashMap<String, NpcTokenData>()

    private data class NpcTokenData(
        val worldId: Long,
        val loginId: String,
        val npcIds: MutableList<Long>,
        val validUntil: Instant,
        val keepCount: Int = 0,
    )

    // POST /api/worlds/{worldId}/npc-token — NPC 토큰 발급
    @PostMapping("/worlds/{worldId}/npc-token")
    fun generateToken(@PathVariable worldId: Long): ResponseEntity<NpcTokenResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val allOfficers = officerRepository.findBySessionId(worldId)
        val npcs = allOfficers.filter { it.npcState > 0.toShort() && it.userId == null }
        val factions = factionRepository.findBySessionId(worldId)
        val factionMap = factions.associateBy { it.id }

        val selected = npcs.shuffled().take(5)
        val nonce = UUID.randomUUID().toString()
        val validUntil = Instant.now().plusSeconds(300)

        npcTokenStore[nonce] = NpcTokenData(
            worldId = worldId,
            loginId = loginId,
            npcIds = selected.map { it.id }.toMutableList(),
            validUntil = validUntil,
        )

        val cards = selected.map { officer ->
            val faction = factionMap[officer.factionId]
            NpcCard(
                id = officer.id,
                name = officer.name,
                picture = officer.picture,
                imageServer = officer.imageServer,
                leadership = officer.leadership,
                command = officer.command,
                intelligence = officer.intelligence,
                politics = officer.politics,
                administration = officer.administration,
                factionId = officer.factionId,
                nationName = faction?.name ?: "",
                nationColor = faction?.color ?: "",
                personality = officer.personalCode,
                special = officer.specialCode,
                special2 = officer.special2Code,
            )
        }

        return ResponseEntity.ok(
            NpcTokenResponse(
                nonce = nonce,
                npcs = cards,
                validUntil = validUntil,
                pickMoreAfter = Instant.now().plusSeconds(60),
                keepCount = 0,
            )
        )
    }

    // POST /api/worlds/{worldId}/npc-token/refresh — NPC 토큰 갱신
    @PostMapping("/worlds/{worldId}/npc-token/refresh")
    fun refreshToken(
        @PathVariable worldId: Long,
        @RequestBody request: RefreshNpcTokenRequest,
    ): ResponseEntity<NpcTokenResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val tokenData = npcTokenStore[request.nonce]
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).build()

        if (tokenData.loginId != loginId || tokenData.worldId != worldId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val allOfficers = officerRepository.findBySessionId(worldId)
        val npcs = allOfficers.filter { it.npcState > 0.toShort() && it.userId == null }
        val factions = factionRepository.findBySessionId(worldId)
        val factionMap = factions.associateBy { it.id }

        val keepIds = request.keepIds.toSet()
        val kept = npcs.filter { it.id in keepIds }
        val available = npcs.filter { it.id !in keepIds && it.id !in tokenData.npcIds }
        val newPicks = available.shuffled().take(5 - kept.size)
        val selected = kept + newPicks

        val validUntil = Instant.now().plusSeconds(300)
        tokenData.npcIds.clear()
        tokenData.npcIds.addAll(selected.map { it.id })

        val newNonce = UUID.randomUUID().toString()
        npcTokenStore.remove(request.nonce)
        npcTokenStore[newNonce] = NpcTokenData(
            worldId = worldId,
            loginId = loginId,
            npcIds = selected.map { it.id }.toMutableList(),
            validUntil = validUntil,
            keepCount = kept.size,
        )

        val cards = selected.map { officer ->
            val faction = factionMap[officer.factionId]
            NpcCard(
                id = officer.id,
                name = officer.name,
                picture = officer.picture,
                imageServer = officer.imageServer,
                leadership = officer.leadership,
                command = officer.command,
                intelligence = officer.intelligence,
                politics = officer.politics,
                administration = officer.administration,
                factionId = officer.factionId,
                nationName = faction?.name ?: "",
                nationColor = faction?.color ?: "",
                personality = officer.personalCode,
                special = officer.specialCode,
                special2 = officer.special2Code,
            )
        }

        return ResponseEntity.ok(
            NpcTokenResponse(
                nonce = newNonce,
                npcs = cards,
                validUntil = validUntil,
                pickMoreAfter = Instant.now().plusSeconds(60),
                keepCount = kept.size,
            )
        )
    }

    // POST /api/worlds/{worldId}/npc-select — NPC 선택 (토큰 기반)
    @PostMapping("/worlds/{worldId}/npc-select")
    fun selectNpc(
        @PathVariable worldId: Long,
        @RequestBody request: SelectNpcWithTokenRequest,
    ): ResponseEntity<Any> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        val tokenData = npcTokenStore[request.nonce]
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid or expired token"))

        if (tokenData.loginId != loginId || tokenData.worldId != worldId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        if (Instant.now().isAfter(tokenData.validUntil)) {
            npcTokenStore.remove(request.nonce)
            return ResponseEntity.badRequest().body(mapOf("error" to "Token expired"))
        }

        if (request.officerId !in tokenData.npcIds) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Officer not in token pool"))
        }

        val officer = officerRepository.findById(request.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = appUserRepository.findByLoginId(loginId)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        // Assign NPC to user
        officer.userId = user.id
        officer.npcState = 0
        officer.ownerName = user.displayName
        officerRepository.save(officer)

        npcTokenStore.remove(request.nonce)

        return ResponseEntity.ok(
            SelectNpcResult(
                success = true,
                officer = OfficerResponse.from(officer),
            )
        )
    }

    // GET /api/worlds/{worldId}/available-npcs — 선택 가능한 NPC 목록
    @GetMapping("/worlds/{worldId}/available-npcs")
    fun listAvailableNpcs(@PathVariable worldId: Long): ResponseEntity<List<OfficerResponse>> {
        val allOfficers = officerRepository.findBySessionId(worldId)
        val npcs = allOfficers.filter { it.npcState > 0.toShort() && it.userId == null }
        return ResponseEntity.ok(npcs.map { OfficerResponse.from(it) })
    }

    private fun currentLoginId(): String? =
        SecurityContextHolder.getContext().authentication?.name
}
