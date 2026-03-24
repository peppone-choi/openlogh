package com.openlogh.engine

import com.openlogh.entity.Fleet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 그리드 진입 제한 검증.
 * gin7 5.3: 1그리드 300유닛/1진영, 최대 2진영, 부분 진입 금지.
 */
@Service
class GridEntryValidator {
    companion object {
        private val log = LoggerFactory.getLogger(GridEntryValidator::class.java)
        const val MAX_UNITS_PER_FACTION = 300
        const val MAX_FACTIONS_PER_GRID = 2
    }

    data class GridState(
        val factionUnits: Map<Long, Int>, // factionId → total units
    )

    data class EntryResult(
        val allowed: Boolean,
        val reason: String = "",
    )

    /**
     * 함대가 특정 그리드에 진입 가능한지 검증.
     */
    fun canEnterGrid(fleet: Fleet, gridState: GridState): EntryResult {
        val factionId = fleet.factionId
        val fleetUnits = fleet.totalCombatShips() / 300 // 300척 = 1유닛

        // 현재 그리드의 진영 수 체크
        val factionsInGrid = gridState.factionUnits.keys.filter { (gridState.factionUnits[it] ?: 0) > 0 }
        val isNewFaction = factionId !in factionsInGrid

        if (isNewFaction && factionsInGrid.size >= MAX_FACTIONS_PER_GRID) {
            return EntryResult(false, "그리드 내 최대 진영 수(${MAX_FACTIONS_PER_GRID}) 초과")
        }

        // 동일 진영 유닛 수 체크 (부분 진입 금지)
        val currentUnits = gridState.factionUnits[factionId] ?: 0
        if (currentUnits + fleetUnits > MAX_UNITS_PER_FACTION) {
            return EntryResult(false, "진영 유닛 한도 초과 (현재: ${currentUnits}, 진입: ${fleetUnits}, 한도: ${MAX_UNITS_PER_FACTION})")
        }

        return EntryResult(true)
    }
}
