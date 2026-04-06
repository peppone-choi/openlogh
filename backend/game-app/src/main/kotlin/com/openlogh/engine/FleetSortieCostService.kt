package com.openlogh.engine

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 함대 출격비용 계산 및 진영 자금 차감 서비스.
 *
 * gin7 경제 루프의 일부: 출격 중인 함대는 매 SORTIE_COST_INTERVAL_TICKS마다
 * 유닛 수에 비례한 유지비를 진영 자금에서 차감한다.
 * 사령관의 administration 스탯이 높을수록 비용이 절감된다 (최대 50%).
 */
@Service
class FleetSortieCostService(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** 함선 유닛당 기본 유지비 (자금) */
        const val BASE_COST_PER_UNIT = 10

        /** 1 게임일마다 비용 처리 (3600틱 = 1 게임일 at 24x speed) */
        const val SORTIE_COST_INTERVAL_TICKS = 3600L
    }

    /**
     * 출격 중인 함대의 유지비를 계산하여 진영 자금에서 차감한다.
     *
     * 출격 판정: fleet.meta["isSortie"] == true OR fleet.currentUnits > 0
     * 할인율: discount = (administration - 50).coerceAtLeast(0) / 100.0 * 0.5
     * 최대 50% 절감 (administration=100일 때)
     */
    @Transactional
    fun processSortieCost(sessionId: Long) {
        val allFleets = fleetRepository.findBySessionId(sessionId)

        // 출격 중 함대만 필터링: isSortie=true OR currentUnits > 0
        val sortieFleets = allFleets.filter { fleet ->
            fleet.meta["isSortie"] == true || fleet.currentUnits > 0
        }

        if (sortieFleets.isEmpty()) return

        // 진영별 그룹화
        val fleetsByFaction = sortieFleets.groupBy { it.factionId }

        val factionsToSave = mutableListOf<com.openlogh.entity.Faction>()

        for ((factionId, fleets) in fleetsByFaction) {
            val faction = factionRepository.findById(factionId).orElse(null) ?: continue

            // 총 유닛 수 집계
            val totalUnits = fleets.sumOf { it.currentUnits }
            if (totalUnits <= 0) continue

            // 기본 비용 계산
            val baseCost = totalUnits * BASE_COST_PER_UNIT

            // 사령관 administration으로 할인율 계산 (첫 번째 함대의 사령관 기준)
            // 각 함대별로 계산하지 않고 진영 전체 비용에 대표 사령관 적용
            val commanderId = fleets.first().leaderOfficerId
            val administration = officerRepository.findById(commanderId)
                .map { it.administration.toInt() }
                .orElse(50)

            val discount = (administration - 50).coerceAtLeast(0) / 100.0 * 0.5
            val finalCost = (baseCost * (1.0 - discount)).toInt()

            // 자금 차감 (마이너스 없음)
            val prevFunds = faction.funds
            faction.funds = (faction.funds - finalCost).coerceAtLeast(0)

            log.debug(
                "[Session {}] Sortie cost: faction {} units={} baseCost={} discount={:.1f}% finalCost={} funds: {}→{}",
                sessionId, factionId, totalUnits, baseCost, discount * 100, finalCost, prevFunds, faction.funds,
            )

            factionsToSave.add(faction)
        }

        if (factionsToSave.isNotEmpty()) {
            factionRepository.saveAll(factionsToSave)
        }
    }
}
