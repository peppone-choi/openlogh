package com.openlogh.engine.strategic

import com.openlogh.engine.CommandPointService.CpType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * CP 비용/타입 결정 서비스.
 *
 * CommandExecutor로부터 위임받아 커맨드별 CP 비용을 결정.
 * 가변 비용 커맨드(작전계획, 작전철회, 장수발령)는 arg 기반 계산.
 *
 * Design Ref: §4.1 — CpCostResolver API
 * Design Ref: §2.2 — CP Cost Resolution Flow
 */
@Service
class CpCostResolver(
    private val registry: LegacyCommandCpRegistry = LegacyCommandCpRegistry,
) {

    private val log = LoggerFactory.getLogger(CpCostResolver::class.java)

    /**
     * 커맨드 코드 → CP 타입 결정.
     * Registry에 없으면 StrategicCommandRegistry 참조 후 fallback PCP.
     */
    fun resolveCpType(actionCode: String): CpType {
        val entry = registry.findByCode(actionCode)
        if (entry != null) return entry.cpType

        val strategicDef = StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.cpType

        log.warn("CP type not found for command: {}. Defaulting to PCP", actionCode)
        return CpType.PCP
    }

    /**
     * 커맨드 코드 → CP 비용 결정.
     * 가변 비용 커맨드는 arg에서 유닛 수를 추출하여 계산.
     */
    fun resolveCpCost(actionCode: String, arg: Map<String, Any>? = null): Int {
        val entry = registry.findByCode(actionCode)
        if (entry != null) {
            if (!entry.isVariable) return entry.baseCost
            return calculateVariableCost(entry, arg)
        }

        val strategicDef = StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.cpCost

        log.warn("CP cost not found for command: {}. Using cost=0 (free)", actionCode)
        return 0
    }

    /**
     * 커맨드 코드 → 직무카드 커맨드 그룹.
     */
    fun resolveCommandGroup(actionCode: String): String? {
        val entry = registry.findByCode(actionCode)
        if (entry?.commandGroup != null) return entry.commandGroup

        val strategicDef = StrategicCommandRegistry.findByCode(actionCode)
        if (strategicDef != null) return strategicDef.requiredCommandGroup

        return null
    }

    /**
     * 가변 비용 계산: costPerUnit * units, clamped to [minCost, maxCost].
     *
     * gin7: 작전계획 CP = 발동예정시기에 따라 변화 (10~1280)
     */
    private fun calculateVariableCost(entry: LegacyCommandCpRegistry.CpEntry, arg: Map<String, Any>?): Int {
        if (arg == null) {
            log.warn("Variable cost arg missing for command: {}. Using minCost={}", entry.actionCode, entry.minCost)
            return entry.minCost
        }

        val units = try {
            (arg["units"] as? Number)?.toInt()
                ?: (arg["count"] as? Number)?.toInt()
                ?: (arg["size"] as? Number)?.toInt()
                ?: 1
        } catch (e: Exception) {
            log.warn("Variable cost arg parse failed for command: {}. Using baseCost={}", entry.actionCode, entry.baseCost)
            return entry.baseCost
        }

        val cost = entry.costPerUnit * units
        return cost.coerceIn(entry.minCost, entry.maxCost)
    }
}
