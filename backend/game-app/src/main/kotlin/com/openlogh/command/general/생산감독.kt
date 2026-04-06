package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 생산감독 (Production Oversight) — gin7: 兵站 - 生産監督
 *
 * Boost planet's production output for the current period.
 * Uses administration stat to increase production tick bonus.
 */
class 생산감독(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "생산 감독"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
            SuppliedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = env.develCost, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.PCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val c = city ?: return CommandResult(
            success = false,
            logs = listOf("<R>생산 감독</> 실패 - 행성 정보를 불러올 수 없습니다.")
        )

        val adminStat = general.administration.toInt()
        val currentProd = c.production
        val maxProd = c.productionMax

        if (currentProd >= maxProd) {
            return CommandResult(
                success = false,
                logs = listOf("<R>생산 감독</> 실패 - 이미 최대 생산력입니다.")
            )
        }

        // Production boost = administration * 0.5 * random(0.8..1.2)
        val boost = (adminStat * 0.5 * (0.8 + rng.nextDouble() * 0.4)).roundToInt()
        val actualBoost = max(1, boost).coerceAtMost(maxProd - currentProd)
        val cost = getCost()
        val exp = (actualBoost * 0.7).toInt()

        pushLog("생산 감독으로 생산력이 <C>$actualBoost</> 상승했습니다. <1>$date</>")
        pushHistoryLog("생산 감독: 생산력 $actualBoost 상승. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"experience":$exp,"administrationExp":1},"cityChanges":{"agri":$actualBoost}}"""
        )
    }
}
