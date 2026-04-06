package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.model.OfficerStat
import com.openlogh.model.StatCategory
import org.springframework.stereotype.Service
import kotlin.math.floor

/**
 * Result of a CP deduction attempt.
 */
data class CpDeductionResult(
    val success: Boolean,
    val crossUsed: Boolean = false,
    val actualCost: Int = 0,
    val poolUsed: StatCategory? = null,
    val errorMessage: String? = null,
)

/**
 * Dual-pool command point service.
 *
 * Handles PCP (Political Command Points) and MCP (Military Command Points)
 * deduction with 2x cross-use penalty, and stat-based regeneration.
 */
@Service
class CpService {

    /**
     * Deduct command points from the officer's pool.
     *
     * 1. Try the primary pool matching [poolType].
     * 2. If insufficient, try the cross pool at 2x cost.
     * 3. If both insufficient, return failure without mutating the officer.
     */
    fun deductCp(officer: Officer, cost: Int, poolType: StatCategory): CpDeductionResult {
        val primaryPool = getPool(officer, poolType)
        val crossType = crossPool(poolType)
        val crossPoolValue = getPool(officer, crossType)

        // 1. Primary pool sufficient
        if (primaryPool >= cost) {
            setPool(officer, poolType, primaryPool - cost)
            return CpDeductionResult(
                success = true,
                crossUsed = false,
                actualCost = cost,
                poolUsed = poolType,
            )
        }

        // 2. Cross-use at 2x cost
        val crossCost = cost * 2
        if (crossPoolValue >= crossCost) {
            setPool(officer, crossType, crossPoolValue - crossCost)
            return CpDeductionResult(
                success = true,
                crossUsed = true,
                actualCost = crossCost,
                poolUsed = crossType,
            )
        }

        // 3. Insufficient
        val poolName = if (poolType == StatCategory.PCP) "PCP" else "MCP"
        return CpDeductionResult(
            success = false,
            crossUsed = false,
            actualCost = 0,
            poolUsed = null,
            errorMessage = "커맨드 포인트가 부족합니다. (필요: $cost $poolName, 보유: PCP ${officer.pcp}/${officer.pcpMax}, MCP ${officer.mcp}/${officer.mcpMax})",
        )
    }

    /**
     * Regenerate PCP and MCP for the officer based on their stats.
     *
     * - PCP regen = floor((politics + administration) / 20) + 1
     * - MCP regen = floor((command + mobility) / 20) + 1
     *
     * Each pool is capped at its max value.
     */
    fun regeneratePcpMcp(officer: Officer) {
        val pcpRegen = floor((officer.politics + officer.administration).toDouble() / 20.0).toInt() + 1
        val mcpRegen = floor((officer.command + officer.mobility).toDouble() / 20.0).toInt() + 1

        officer.pcp = (officer.pcp + pcpRegen).coerceAtMost(officer.pcpMax)
        officer.mcp = (officer.mcp + mcpRegen).coerceAtMost(officer.mcpMax)
    }

    /**
     * Returns the stats that gain experience when a command of the given pool type is used.
     */
    fun getExpStatsForPool(poolType: StatCategory): List<OfficerStat> {
        return when (poolType) {
            StatCategory.PCP -> OfficerStat.pcpStats()
            StatCategory.MCP -> OfficerStat.mcpStats()
        }
    }

    private fun getPool(officer: Officer, type: StatCategory): Int =
        when (type) {
            StatCategory.PCP -> officer.pcp
            StatCategory.MCP -> officer.mcp
        }

    private fun setPool(officer: Officer, type: StatCategory, value: Int) {
        when (type) {
            StatCategory.PCP -> officer.pcp = value
            StatCategory.MCP -> officer.mcp = value
        }
    }

    private fun crossPool(type: StatCategory): StatCategory =
        when (type) {
            StatCategory.PCP -> StatCategory.MCP
            StatCategory.MCP -> StatCategory.PCP
        }
}
