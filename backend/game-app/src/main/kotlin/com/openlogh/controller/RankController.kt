package com.openlogh.controller

import com.openlogh.service.RankLadderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 계급 래더 REST API.
 *
 * 프론트엔드 조직도 및 래더 표시용 엔드포인트.
 */
@RestController
@RequestMapping("/api/rank")
class RankController(
    private val rankLadderService: RankLadderService,
) {
    /**
     * 특정 세션/진영/계급의 래더 조회.
     */
    @GetMapping("/ladder/{sessionId}")
    fun getRankLadder(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
        @RequestParam(required = false) rank: Int?,
    ): ResponseEntity<List<RankLadderEntry>> {
        if (rank != null) {
            val ladder = rankLadderService.getRankLadder(sessionId, factionId, rank)
            val entries = ladder.mapIndexed { index, officer ->
                RankLadderEntry(
                    officerId = officer.id,
                    officerName = officer.name,
                    rank = officer.rank.toInt(),
                    experience = officer.experience,
                    position = index + 1,
                    factionId = officer.factionId,
                )
            }
            return ResponseEntity.ok(entries)
        }
        // No rank filter: return all ranks (0-10)
        val allEntries = mutableListOf<RankLadderEntry>()
        for (r in 10 downTo 0) {
            val ladder = rankLadderService.getRankLadder(sessionId, factionId, r)
            ladder.forEachIndexed { index, officer ->
                allEntries.add(
                    RankLadderEntry(
                        officerId = officer.id,
                        officerName = officer.name,
                        rank = officer.rank.toInt(),
                        experience = officer.experience,
                        position = index + 1,
                        factionId = officer.factionId,
                    )
                )
            }
        }
        return ResponseEntity.ok(allEntries)
    }

    /**
     * 계급별 인원 제한 조회.
     */
    @GetMapping("/limits")
    fun getRankLimits(): ResponseEntity<Map<Int, Int>> {
        return ResponseEntity.ok(RankLadderService.RANK_LIMITS)
    }
}

data class RankLadderEntry(
    val officerId: Long,
    val officerName: String,
    val rank: Int,
    val experience: Int,
    val position: Int,
    val factionId: Long,
)
