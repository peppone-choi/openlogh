package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 계급 래더 시스템 + 자동 승진/강등.
 * gin7: 3.4 래더 5법칙, 3.6 자동 승진, 3.8 자동 강등
 */
@Service
class RankLadderService(
    private val officerRepository: OfficerRepository,
    private val positionCardService: PositionCardService? = null,
) {
    companion object {
        private val log = LoggerFactory.getLogger(RankLadderService::class.java)

        /** 계급별 인원 제한 (rank index → max count, 0=무제한) */
        val RANK_LIMITS = mapOf(
            10 to 5,   // 원수
            9 to 5,    // 상급대장
            8 to 10,   // 대장
            7 to 20,   // 중장
            6 to 40,   // 소장
            5 to 80,   // 준장
        )
        // 대령(4) 이하: 무제한

        /** 자동 승진 대상: 대령(4) 이하 */
        const val AUTO_PROMOTION_MAX_RANK = 4

        /** 자동 승진/강등 주기: 30 게임일 = 1 게임 월 */
        const val AUTO_CYCLE_GAME_DAYS = 30
    }

    /**
     * 래더 5법칙 정렬: 동일 계급 내 순위.
     * 1. 공적(experience) 내림차순
     * 2. 작위(peerage) 내림차순
     * 3. 훈장(medalCount) 내림차순
     * 4. 영향력(influence) 내림차순
     * 5. 능력합계 내림차순
     */
    fun getRankLadder(sessionId: Long, factionId: Long, rank: Int): List<Officer> {
        val officers = officerRepository.findBySessionId(sessionId)
            .filter { it.factionId == factionId && it.rank.toInt() == rank && (it.killTurn ?: 0) <= 0 }

        return officers.sortedWith(
            compareByDescending<Officer> { it.experience }
                .thenByDescending { peerageScore(it) }
                .thenByDescending { (it.meta["medalCount"] as? Number)?.toInt() ?: 0 }
                .thenByDescending { it.influence }
                .thenByDescending { totalStats(it) }
        )
    }

    /**
     * 래더 내 순위 (1-based).
     */
    fun getLadderPosition(officer: Officer): Int {
        val ladder = getRankLadder(officer.sessionId, officer.factionId, officer.rank.toInt())
        return ladder.indexOfFirst { it.id == officer.id } + 1
    }

    /**
     * 매월 호출: 자동 승진 처리.
     * 대령 이하 각 계급에서 래더 1위를 자동 승진.
     */
    fun processAutoPromotion(sessionId: Long) {
        val officers = officerRepository.findBySessionId(sessionId)
        val factionIds = officers.map { it.factionId }.distinct().filter { it > 0 }

        for (factionId in factionIds) {
            for (rank in 0..AUTO_PROMOTION_MAX_RANK) {
                val nextRank = rank + 1
                val limit = RANK_LIMITS[nextRank] ?: Int.MAX_VALUE
                val currentCount = officers.count { it.factionId == factionId && it.rank.toInt() == nextRank && (it.killTurn ?: 0) <= 0 }

                if (currentCount >= limit) continue // 상위 계급 인원 초과

                val ladder = getRankLadder(sessionId, factionId, rank)
                if (ladder.isEmpty()) continue

                val top = ladder.first()
                val avgMerit = ladder.map { it.experience }.average().toInt()

                // 자동 승진
                top.rank = nextRank.toShort()
                top.experience = avgMerit // 래더 평균 공적 부여

                // 승진 시 직무카드 정리 (개인/함장/봉토 제외)
                positionCardService?.revokeOnRankChange(sessionId, top.id)

                officerRepository.save(top)

                log.info("Auto-promotion: {} (rank {} → {}, merit set to {})", top.name, rank, nextRank, avgMerit)
            }
        }
    }

    /**
     * 매월 호출: 자동 강등 처리.
     * 인원 초과 시 래더 최하위를 강등.
     */
    fun processAutoDemotion(sessionId: Long) {
        val officers = officerRepository.findBySessionId(sessionId).filter { (it.killTurn ?: 0) <= 0 }
        val factionIds = officers.map { it.factionId }.distinct().filter { it > 0 }

        for (factionId in factionIds) {
            for ((rank, limit) in RANK_LIMITS) {
                val atRank = officers.filter { it.factionId == factionId && it.rank.toInt() == rank }
                if (atRank.size <= limit) continue

                val ladder = getRankLadder(sessionId, factionId, rank)
                val excess = atRank.size - limit

                // 래더 최하위부터 강등
                for (i in 0 until excess) {
                    val demotee = ladder[ladder.size - 1 - i]
                    demotee.rank = (rank - 1).toShort()
                    demotee.experience = 100 // 강등 시 공적 100 설정

                    // 강등 시 직무카드 정리 (개인/함장/봉토 제외)
                    positionCardService?.revokeOnRankChange(sessionId, demotee.id)

                    officerRepository.save(demotee)
                    log.info("Auto-demotion: {} (rank {} → {}, excess at rank)", demotee.name, rank, rank - 1)
                }
            }
        }
    }

    /**
     * 인사권 검증: 승진 가능 여부 확인.
     * 1. 인사권자가 적절한 직무카드를 보유하고 있는지
     * 2. 대상 계급이 인사권자의 권한 범위 내인지
     * 3. 대상 계급의 인원 제한에 여유가 있는지
     *
     * @param sessionId 세션 ID
     * @param promoterId 승진 실행자의 장교 ID
     * @param factionId 진영 ID
     * @param targetRank 승진 목표 계급
     * @return true if promotion is allowed
     */
    fun canPromote(sessionId: Long, promoterId: Long, factionId: Long, targetRank: Int): Boolean {
        val pcs = positionCardService ?: return false
        val promoterCards = pcs.getHeldCardCodes(sessionId, promoterId)

        // 인사권 카드 보유 확인 + 권한 범위 판정
        val maxPromotableRank = getMaxPromotableRank(promoterCards)
        if (maxPromotableRank < 0) return false // 인사권 없음
        if (targetRank > maxPromotableRank) return false // 권한 범위 초과

        // 인원 제한 확인
        val limit = RANK_LIMITS[targetRank] ?: return true // 제한 없는 계급
        val officers = officerRepository.findBySessionId(sessionId)
        val currentCount = officers.count {
            it.factionId == factionId && it.rank.toInt() == targetRank && (it.killTurn ?: 0) <= 0
        }
        return currentCount < limit
    }

    /**
     * 인사권 카드 기반 최대 승진 가능 계급 반환.
     * @return 최대 승진 가능 계급, 인사권 없으면 -1
     */
    private fun getMaxPromotableRank(heldCards: List<String>): Int {
        var maxRank = -1
        for (code in heldCards) {
            val rank = when (code) {
                "emperor", "chairman" -> 10  // 모든 계급 승진 가능
                "military_minister", "defense_committee_chair" -> 8  // 대장까지
                "personnel_chief", "personnel_department_head" -> 6  // 소장까지
                else -> -1
            }
            if (rank > maxRank) maxRank = rank
        }
        return maxRank
    }

    private fun peerageScore(officer: Officer): Int = when (officer.peerage) {
        "duke" -> 6; "marquis" -> 5; "count" -> 4
        "viscount" -> 3; "baron" -> 2; "knight" -> 1
        else -> 0
    }

    private fun totalStats(officer: Officer): Int =
        officer.leadership.toInt() + officer.command.toInt() + officer.intelligence.toInt() +
        officer.politics.toInt() + officer.administration.toInt() + officer.mobility.toInt() +
        officer.attack.toInt() + officer.defense.toInt()
}
