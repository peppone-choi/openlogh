package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 예산편성 (Budget Allocation) — gin7: 政治 - 予算編成
 *
 * Set faction tax rate and conscription rate.
 * Only faction leaders can execute.
 */
class 예산편성(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "예산 편성"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 2
    override fun getCommandPoolType() = StatCategory.PCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val newTaxRate = (arg?.get("taxRate") as? Number)?.toInt()
        val newConscriptionRate = (arg?.get("conscriptionRate") as? Number)?.toInt()

        val n = nation ?: return CommandResult(
            success = false,
            logs = listOf("<R>예산 편성</> 실패 - 소속 진영 없음")
        )

        if (newTaxRate == null && newConscriptionRate == null) {
            return CommandResult(
                success = false,
                logs = listOf("<R>예산 편성</> 실패 - 세율 또는 징병률을 지정해야 합니다.")
            )
        }

        val changes = mutableListOf<String>()

        if (newTaxRate != null) {
            val clamped = newTaxRate.coerceIn(0, 200)
            n.taxRate = clamped.toShort()
            changes.add("세율 ${clamped}%")
        }

        if (newConscriptionRate != null) {
            val clamped = newConscriptionRate.coerceIn(0, 50)
            n.conscriptionRate = clamped.toShort()
            changes.add("징병률 ${clamped}%")
        }

        val changeStr = changes.joinToString(", ")
        pushLog("예산을 편성했습니다: $changeStr <1>$date</>")
        pushHistoryLog("예산 편성: $changeStr <1>$date</>")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 예산을 편성했습니다: $changeStr")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"experience":80,"politicsExp":1,"administrationExp":1},"meritPoints":3}"""
        )
    }
}
