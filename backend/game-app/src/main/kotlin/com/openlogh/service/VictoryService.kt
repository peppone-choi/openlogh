package com.openlogh.service

import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 승패 판정 시스템.
 * gin7: 12.1~12.6 — 수도 점령, 영토 축소, 시간 만료, 결정적/한정적/국지적 승리
 */
@Service
class VictoryService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(VictoryService::class.java)
        const val DEFAULT_END_YEAR = 801
        const val DEFAULT_END_MONTH = 7
        const val DEFAULT_END_DAY = 27
    }

    enum class VictoryType { NONE, DECISIVE, LIMITED, LOCAL }

    data class VictoryResult(
        val isGameOver: Boolean,
        val winnerFactionType: String? = null,
        val loserFactionType: String? = null,
        val victoryType: VictoryType = VictoryType.NONE,
        val reason: String = "",
    )

    /**
     * 매 턴 호출: 승패 조건 체크.
     */
    fun checkVictoryConditions(world: SessionState): VictoryResult {
        val sessionId = world.id.toLong()
        val factions = factionRepository.findBySessionId(sessionId).filter { it.factionType in listOf("empire", "alliance") }
        if (factions.size < 2) return VictoryResult(false)

        val planets = planetRepository.findBySessionId(sessionId)
        val empire = factions.find { it.factionType == "empire" }
        val alliance = factions.find { it.factionType == "alliance" }
        if (empire == null || alliance == null) return VictoryResult(false)

        // 12.1 수도 점령 승리
        val empireCapital = planets.find { it.id == empire.capitalPlanetId }
        val allianceCapital = planets.find { it.id == alliance.capitalPlanetId }
        if (empireCapital != null && empireCapital.factionId != empire.id) {
            return evaluateVictory(world, "alliance", "empire", "수도 점령")
        }
        if (allianceCapital != null && allianceCapital.factionId != alliance.id) {
            return evaluateVictory(world, "empire", "alliance", "수도 점령")
        }

        // 12.2 영토 축소 승리 (3 성계 이하)
        val empirePlanets = planets.count { it.factionId == empire.id }
        val alliancePlanets = planets.count { it.factionId == alliance.id }
        if (empirePlanets <= 3) return evaluateVictory(world, "alliance", "empire", "영토 축소 (${empirePlanets}성계)")
        if (alliancePlanets <= 3) return evaluateVictory(world, "empire", "alliance", "영토 축소 (${alliancePlanets}성계)")

        // 12.3 시간 만료
        val endYear = (world.config["endYear"] as? Number)?.toInt() ?: DEFAULT_END_YEAR
        val endMonth = (world.config["endMonth"] as? Number)?.toInt() ?: DEFAULT_END_MONTH
        if (world.currentYear >= endYear && world.currentMonth >= endMonth) {
            val empirePop = planets.filter { it.factionId == empire.id }.sumOf { it.population.toLong() }
            val alliancePop = planets.filter { it.factionId == alliance.id }.sumOf { it.population.toLong() }
            val winner = if (empirePop > alliancePop) "empire" else "alliance" // 동수 시 동맹 승리
            val loser = if (winner == "empire") "alliance" else "empire"
            return evaluateVictory(world, winner, loser, "시간 만료 (인구: 제국=${empirePop} vs 동맹=${alliancePop})")
        }

        return VictoryResult(false)
    }

    /**
     * 승리 유형 판정: 결정적/한정적/국지적
     */
    private fun evaluateVictory(world: SessionState, winner: String, loser: String, reason: String): VictoryResult {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        val winFaction = factions.find { it.factionType == winner }
        val loseFaction = factions.find { it.factionType == loser }

        val totalPop = planets.sumOf { it.population.toLong() }.coerceAtLeast(1)
        val winnerPop = planets.filter { it.factionId == winFaction?.id }.sumOf { it.population.toLong() }
        val popRatio = winnerPop.toDouble() / totalPop

        val fleets = fleetRepository.findBySessionId(sessionId)
        val winnerUnits = fleets.filter { it.factionId == winFaction?.id }.sumOf { it.totalCombatShips().toLong() }.coerceAtLeast(1)
        val loserUnits = fleets.filter { it.factionId == loseFaction?.id }.sumOf { it.totalCombatShips().toLong() }.coerceAtLeast(1)
        val unitRatio = winnerUnits.toDouble() / loserUnits

        val hasCoup = (world.config["hasCoup"] as? Boolean) ?: false

        // 제국 전용: 황제/최고사령관이 적 수도 성계에 있어야 함
        val emperorAtEnemyCapital = if (winner == "empire") {
            val officers = officerRepository.findBySessionId(sessionId)
            val loseCapitalPlanetId = loseFaction?.capitalPlanetId
            officers.any { it.factionId == winFaction?.id && it.rank >= 9 && it.planetId == loseCapitalPlanetId }
        } else true

        val isDecisive = popRatio >= 0.9 && unitRatio >= 10.0 && !hasCoup && emperorAtEnemyCapital

        val victoryType = when {
            isDecisive -> VictoryType.DECISIVE
            reason.contains("수도 점령") -> VictoryType.LIMITED
            else -> VictoryType.LOCAL
        }

        log.info("Victory: {} wins ({}) - {} [pop={:.1f}%, units={:.1f}x, coup={}, emperor={}]",
            winner, victoryType, reason, popRatio * 100, unitRatio, hasCoup, emperorAtEnemyCapital)

        return VictoryResult(
            isGameOver = true,
            winnerFactionType = winner,
            loserFactionType = loser,
            victoryType = victoryType,
            reason = reason,
        )
    }
}
