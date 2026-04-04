package com.openlogh.controller

import com.openlogh.dto.OfficerPositionCard
import com.openlogh.engine.organization.PositionCardGrantMap
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.PositionCard
import com.openlogh.repository.PositionCardRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/position-cards")
class PositionCardController(
    private val positionCardRepository: PositionCardRepository,
) {
    @GetMapping("/{sessionId}/{officerId}")
    fun getOfficerCards(
        @PathVariable sessionId: Long,
        @PathVariable officerId: Long,
    ): ResponseEntity<List<OfficerPositionCard>> {
        val cards = positionCardRepository.findBySessionIdAndOfficerId(sessionId, officerId)
        val result = cards.map { card ->
            val type = PositionCardType.fromCode(card.positionType)
            OfficerPositionCard(
                id = card.id,
                positionType = card.positionType,
                positionNameKo = card.positionNameKo,
                category = type?.category?.name ?: "unknown",
                grantedCommands = type?.let { PositionCardGrantMap.getGrantedCommands(it.code).toList() } ?: emptyList(),
            )
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{sessionId}")
    fun getSessionCards(
        @PathVariable sessionId: Long,
    ): ResponseEntity<List<PositionCard>> {
        return ResponseEntity.ok(positionCardRepository.findBySessionId(sessionId))
    }
}
