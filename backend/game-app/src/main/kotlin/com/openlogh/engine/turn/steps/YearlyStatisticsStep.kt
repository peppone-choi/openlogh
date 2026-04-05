package com.openlogh.engine.turn.steps

import com.openlogh.engine.EconomyService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.service.InheritanceService
import org.springframework.stereotype.Component

/**
 * Step 800: Yearly statistics — runs only in month 1.
 *
 * Legacy: checkStatistic parity.
 * Processes yearly economy stats and accrues inheritance points
 * for belong-years and dex accumulation.
 */
@Component
class YearlyStatisticsStep(
    private val economyService: EconomyService,
    private val worldPortFactory: JpaWorldPortFactory,
    private val inheritanceService: InheritanceService,
) : TurnStep {
    override val name = "YearlyStatistics"
    override val order = 800

    override fun shouldSkip(context: TurnContext): Boolean {
        return context.world.currentMonth.toInt() != 1
    }

    override fun execute(context: TurnContext) {
        economyService.processYearlyStatistics(context.world)

        val ports = worldPortFactory.create(context.worldId)
        val generals = ports.allOfficers().map { it.toEntity() }
        accrueYearlyInheritancePoints(generals)
        generals.forEach { ports.putOfficer(it.toSnapshot()) }
    }

    private fun accrueYearlyInheritancePoints(generals: List<com.openlogh.entity.Officer>) {
        for (general in generals) {
            if (general.npcState.toInt() >= 2) continue

            val currentBelongYear = general.belong.toInt().coerceAtLeast(0)
            val storedMaxBelongYear = readInt(general.meta["inherit_max_belong_year"]) ?: 0
            if (currentBelongYear > storedMaxBelongYear) {
                inheritanceService.accruePoints(general, "max_belong", currentBelongYear - storedMaxBelongYear)
                general.meta["inherit_max_belong_year"] = currentBelongYear
            }

            val dexSum = general.dex1 + general.dex2 + general.dex3 + general.dex4 + general.dex5
            val currentDexPoint = (dexSum * 0.001).toInt()
            val storedDexPoint = readInt(general.meta["inherit_dex_point_total"]) ?: 0
            if (currentDexPoint > storedDexPoint) {
                inheritanceService.accruePoints(general, "dex", currentDexPoint - storedDexPoint)
                general.meta["inherit_dex_point_total"] = currentDexPoint
            }
        }
    }

    private fun readInt(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }
}
