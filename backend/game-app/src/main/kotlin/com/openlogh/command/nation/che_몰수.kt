package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val MIN_AMOUNT = 100
private const val MAX_AMOUNT = 100000

class che_몰수(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "몰수"

    override val fullConditionConstraints = listOf(
        NotBeNeutral(), OccupiedCity(), BeChief(),
        NotOpeningPart(env.year - env.startYear), SuppliedCity(),
        ExistsDestGeneral(), FriendlyDestGeneral()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destGen = destOfficer ?: return CommandResult(false, logs, "대상 장수 정보를 찾을 수 없습니다")

        if (destGen.id == general.id) {
            return CommandResult(false, logs, "본인입니다")
        }

        val isGold = arg?.get("isGold") as? Boolean ?: true
        val rawAmount = ((arg?.get("amount") as? Number)?.toInt() ?: MIN_AMOUNT).coerceIn(MIN_AMOUNT, MAX_AMOUNT)
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")

        val resName: String
        val actual: Int
        if (isGold) {
            actual = rawAmount.coerceAtMost(destGen.funds)
            destGen.funds -= actual
            n.funds += actual
            resName = "금"
        } else {
            actual = rawAmount.coerceAtMost(destGen.supplies)
            destGen.supplies -= actual
            n.supplies += actual
            resName = "쌀"
        }

        destGen.betray = (destGen.betray + 1).toShort()
        pushLog("<Y>${destGen.name}</>에게서 $resName <C>$actual</>을 몰수했습니다. <1>$date</>")
        pushHistoryLog("<Y>${destGen.name}</>에게서 $resName <C>$actual</>을 몰수했습니다. <1>$date</>")
        pushDestGeneralLog("${resName} <C>$actual</>을 몰수당했습니다. <1>$date</>")
        return CommandResult(true, logs)
    }
}
