package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.service.CommandService
import com.openlogh.service.PersonnelService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * REST controller for rank & personnel management.
 *
 * Provides rank ladder view, personnel info, and promotion/demotion actions.
 * Promotion/demotion require position card authority validated in PersonnelService.
 */
@RestController
@RequestMapping("/api/world/{sessionId}/personnel")
class PersonnelController(
    private val personnelService: PersonnelService,
    private val commandService: CommandService,
) {

    /**
     * GET /api/world/{sessionId}/personnel/ladder/{factionId}
     * Returns the full rank ladder for a faction.
     */
    @GetMapping("/ladder/{factionId}")
    fun getRankLadder(
        @PathVariable sessionId: Long,
        @PathVariable factionId: Long,
    ): ResponseEntity<List<RankLadderEntryDto>> {
        val entries = personnelService.getRankLadder(sessionId, factionId)
        val dtos = entries.map {
            RankLadderEntryDto(
                officerId = it.officerId,
                name = it.name,
                rankTier = it.rankTier,
                rankTitle = it.rankTitle,
                rankTitleKo = it.rankTitleKo,
                meritPoints = it.meritPoints,
                famePoints = it.famePoints,
                totalStats = it.totalStats,
            )
        }
        return ResponseEntity.ok(dtos)
    }

    /**
     * GET /api/world/{sessionId}/personnel/info/{officerId}
     * Returns personnel info for an officer.
     */
    @GetMapping("/info/{officerId}")
    fun getPersonnelInfo(
        @PathVariable sessionId: Long,
        @PathVariable officerId: Long,
    ): ResponseEntity<PersonnelInfoDto> {
        return try {
            val info = personnelService.getPersonnelInfo(officerId, sessionId)
            ResponseEntity.ok(
                PersonnelInfoDto(
                    officerId = info.officerId,
                    name = info.name,
                    rankTier = info.rankTier,
                    rankTitle = info.rankTitle,
                    rankTitleKo = info.rankTitleKo,
                    meritPoints = info.meritPoints,
                    evaluationPoints = info.evaluationPoints,
                    famePoints = info.famePoints,
                    promotionEligible = info.promotionEligible,
                    nextRankTitle = info.nextRankTitle,
                    nextRankTitleKo = info.nextRankTitleKo,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * POST /api/world/{sessionId}/personnel/promote/{promoterId}
     * Promote an officer. Promoter must have authority (position card check).
     */
    @PostMapping("/promote/{promoterId}")
    fun promote(
        @PathVariable sessionId: Long,
        @PathVariable promoterId: Long,
        @RequestBody request: PromoteDemoteRequest,
    ): ResponseEntity<PersonnelActionResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(403).build()
        if (!commandService.verifyOwnership(promoterId, loginId)) {
            return ResponseEntity.status(403).body(
                PersonnelActionResponse(success = false, message = "권한이 없습니다.", updatedOfficer = null),
            )
        }

        return try {
            val officer = personnelService.promote(request.officerId, promoterId, sessionId)
            val info = personnelService.getPersonnelInfo(officer.id, sessionId)
            ResponseEntity.ok(
                PersonnelActionResponse(
                    success = true,
                    message = "${officer.name}이(가) ${info.rankTitleKo}(으)로 승진했습니다.",
                    updatedOfficer = PersonnelInfoDto(
                        officerId = info.officerId,
                        name = info.name,
                        rankTier = info.rankTier,
                        rankTitle = info.rankTitle,
                        rankTitleKo = info.rankTitleKo,
                        meritPoints = info.meritPoints,
                        evaluationPoints = info.evaluationPoints,
                        famePoints = info.famePoints,
                        promotionEligible = info.promotionEligible,
                        nextRankTitle = info.nextRankTitle,
                        nextRankTitleKo = info.nextRankTitleKo,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                PersonnelActionResponse(success = false, message = e.message ?: "잘못된 요청입니다.", updatedOfficer = null),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                PersonnelActionResponse(success = false, message = e.message ?: "승진 조건을 충족하지 않습니다.", updatedOfficer = null),
            )
        }
    }

    /**
     * POST /api/world/{sessionId}/personnel/demote/{demoterId}
     * Demote an officer. Demoter must have authority (position card check).
     */
    @PostMapping("/demote/{demoterId}")
    fun demote(
        @PathVariable sessionId: Long,
        @PathVariable demoterId: Long,
        @RequestBody request: PromoteDemoteRequest,
    ): ResponseEntity<PersonnelActionResponse> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(403).build()
        if (!commandService.verifyOwnership(demoterId, loginId)) {
            return ResponseEntity.status(403).body(
                PersonnelActionResponse(success = false, message = "권한이 없습니다.", updatedOfficer = null),
            )
        }

        return try {
            val officer = personnelService.demote(request.officerId, demoterId, sessionId)
            val info = personnelService.getPersonnelInfo(officer.id, sessionId)
            ResponseEntity.ok(
                PersonnelActionResponse(
                    success = true,
                    message = "${officer.name}이(가) ${info.rankTitleKo}(으)로 강등되었습니다.",
                    updatedOfficer = PersonnelInfoDto(
                        officerId = info.officerId,
                        name = info.name,
                        rankTier = info.rankTier,
                        rankTitle = info.rankTitle,
                        rankTitleKo = info.rankTitleKo,
                        meritPoints = info.meritPoints,
                        evaluationPoints = info.evaluationPoints,
                        famePoints = info.famePoints,
                        promotionEligible = info.promotionEligible,
                        nextRankTitle = info.nextRankTitle,
                        nextRankTitleKo = info.nextRankTitleKo,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                PersonnelActionResponse(success = false, message = e.message ?: "잘못된 요청입니다.", updatedOfficer = null),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(
                PersonnelActionResponse(success = false, message = e.message ?: "강등 조건을 충족하지 않습니다.", updatedOfficer = null),
            )
        }
    }
}
