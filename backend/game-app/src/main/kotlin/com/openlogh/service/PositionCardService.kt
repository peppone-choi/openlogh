package com.openlogh.service

import com.openlogh.engine.organization.CommandGating
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.PositionCard
import com.openlogh.repository.PositionCardRepository
import org.springframework.stereotype.Service

/**
 * 직무카드 CRUD 파사드.
 *
 * position_card 테이블을 통해 모든 직무카드 읽기/쓰기를 처리한다.
 * 기존 officer.meta["positionCards"] JSONB 접근을 대체.
 *
 * HARD-03: Optimistic lock contention 해소를 위해
 * Officer 엔티티 외부의 별도 테이블로 카드 데이터 이관.
 */
@Service
class PositionCardService(
    private val positionCardRepository: PositionCardRepository,
) {
    /**
     * 장교가 보유한 직무카드 코드 목록 조회.
     * 카드가 없으면 기본 카드(개인 + 함장)를 반환한다.
     */
    fun getHeldCardCodes(sessionId: Long, officerId: Long): List<String> {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        return if (cards.isEmpty()) CommandGating.defaultCards()
        else cards.map { it.positionType }
    }

    /**
     * 직무카드 부여 (임명).
     * 이미 보유 중이면 무시(멱등), MAX_CARDS 초과 시 무시.
     */
    fun appointPosition(sessionId: Long, officerId: Long, cardType: PositionCardType) {
        val existing = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        if (existing.size >= CommandGating.MAX_CARDS_PER_OFFICER) return
        if (existing.any { it.positionType == cardType.code }) return
        positionCardRepository.save(
            PositionCard(
                officerId = officerId,
                sessionId = sessionId,
                positionType = cardType.code,
                positionNameKo = cardType.displayName,
            )
        )
    }

    /**
     * 직무카드 박탈 (파면).
     * 해당 카드를 보유하지 않으면 무시.
     */
    fun dismissPosition(sessionId: Long, officerId: Long, positionCode: String) {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        val target = cards.find { it.positionType == positionCode } ?: return
        positionCardRepository.delete(target)
    }

    /**
     * 승진/강등 시 직무카드 정리.
     * 개인(personal), 함장(captain), 봉토(fief_*) 카드만 유지하고 나머지 삭제.
     */
    fun revokeOnRankChange(sessionId: Long, officerId: Long) {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        val toDelete = cards.filter { card ->
            card.positionType !in setOf("personal", "captain") &&
                !card.positionType.startsWith("fief_")
        }
        if (toDelete.isNotEmpty()) positionCardRepository.deleteAll(toDelete)
    }

    /**
     * 장교의 현재 보유 카드 수 조회.
     */
    fun getCardCount(sessionId: Long, officerId: Long): Int {
        return positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId).size
    }
}
