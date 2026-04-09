package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Fleet

/**
 * Phase 13 SAI-02 — Greedy fleet allocator for strategic operations.
 *
 * Pure object (no Spring DI) following the UtilityScorer pattern.
 *
 * Per D-09: 전력 기반 최적 배정 — 적 전력의 SUPERIORITY_MARGIN(1.3x)에 도달하는 최소 함대 조합을
 * greedy 방식으로 선정. 도달하면 즉시 멈춰 나머지 함대는 방어 예비로 남긴다.
 *
 * Greedy is preferred over knapsack because:
 *   1. Game AI does not need optimal — "good enough" is sufficient.
 *   2. Knapsack adds O(n*W) complexity for marginal benefit.
 *   3. Greedy ranks by individual power, which mirrors how a human commander would
 *      pick the strongest available fleets first.
 */
object FleetAllocator {

    /** 30% superiority margin over enemy power required to commit. Per D-09. */
    const val SUPERIORITY_MARGIN: Double = 1.3

    data class AllocationResult(
        val selectedFleetIds: List<Long>,
        val allocatedPower: Double,
    )

    /**
     * Greedily select fleets in descending power order until cumulative power
     * reaches the target threshold (requiredEnemyPower * SUPERIORITY_MARGIN), or
     * the available fleets are exhausted.
     *
     * @param availableFleets all fleets currently free to be assigned
     * @param requiredEnemyPower the enemy power that must be overcome
     * @param fleetPower function returning each fleet's power; defaults to currentUnits-based
     */
    fun allocateFleets(
        availableFleets: List<Fleet>,
        requiredEnemyPower: Double,
        fleetPower: (Fleet) -> Double = { it.currentUnits.toDouble() },
    ): AllocationResult {
        if (availableFleets.isEmpty()) {
            return AllocationResult(emptyList(), 0.0)
        }

        val target = requiredEnemyPower * SUPERIORITY_MARGIN
        val sorted = availableFleets.sortedByDescending(fleetPower)

        val selected = mutableListOf<Long>()
        var cumulative = 0.0
        for (fleet in sorted) {
            if (cumulative >= target) break
            selected += fleet.id
            cumulative += fleetPower(fleet)
        }

        return AllocationResult(
            selectedFleetIds = selected,
            allocatedPower = cumulative,
        )
    }
}
