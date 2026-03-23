package com.openlogh.service

import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import org.springframework.stereotype.Service

@Service
class OfficerRankService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    companion object {
        private val EMPIRE_RANKS = mapOf(
            0 to "무품관",
            1 to "소위",
            2 to "소령",
            3 to "중령",
            4 to "대령",
            5 to "준장",
            6 to "소장",
            7 to "중장",
            8 to "대장",
            9 to "상급대장",
            10 to "원수",
        )

        private val RANK_LIMITS = mapOf(
            10 to 5,  // Reichsmarschall
            9 to 5,   // Fleet Admiral
            8 to 10,  // Admiral
            7 to 20,  // Vice Admiral
            6 to 40,  // Rear Admiral
            5 to 80,  // Commodore
        )
    }

    fun getRankTitle(rank: Int, factionType: Int): String {
        return EMPIRE_RANKS[rank] ?: "무품관"
    }

    fun getRankLimit(rank: Int): Int {
        return RANK_LIMITS[rank] ?: Int.MAX_VALUE
    }
}
