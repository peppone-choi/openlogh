package com.openlogh.controller

import com.openlogh.dto.OrgChartHolder
import com.openlogh.dto.OrgChartResponse
import com.openlogh.dto.PositionTypeInfo
import com.openlogh.engine.organization.PositionCardGrantMap
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PositionCardRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/org-chart")
class OrgChartController(
    private val positionCardRepository: PositionCardRepository,
    private val officerRepository: OfficerRepository,
) {
    @GetMapping("/{sessionId}")
    fun getOrgChart(@PathVariable sessionId: Long): ResponseEntity<OrgChartResponse> {
        // Single query: all position cards for the session
        val cards = positionCardRepository.findBySessionId(sessionId)

        // In-memory join with officers (avoids N+1)
        val officerIds = cards.map { it.officerId }.distinct()
        val officers = officerRepository.findAllById(officerIds).associateBy { it.id }

        val holders = cards.map { card ->
            val officer = officers[card.officerId]
            OrgChartHolder(
                positionType = card.positionType,
                positionNameKo = card.positionNameKo,
                category = PositionCardType.fromCode(card.positionType)?.category?.name ?: "unknown",
                officerId = officer?.id,
                officerName = officer?.name,
                officerPicture = officer?.picture,
                officerRank = officer?.rank?.toInt(),
                officerFactionId = officer?.factionId,
            )
        }

        // All known position types (for rendering empty/vacant slots)
        val allTypes = PositionCardType.entries.map { type ->
            PositionTypeInfo(
                code = type.code,
                displayName = type.displayName,
                category = type.category.name,
                minRank = type.minRank,
                grantedCommands = PositionCardGrantMap.getGrantedCommands(type.code).toList(),
            )
        }

        return ResponseEntity.ok(
            OrgChartResponse(
                sessionId = sessionId,
                holders = holders,
                allPositionTypes = allTypes,
            )
        )
    }
}
