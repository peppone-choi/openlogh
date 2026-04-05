package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val GENERAL_MINIMUM_GOLD = 100
private const val GENERAL_MINIMUM_RICE = 100
private const val MAX_RESOURCE_ACTION_AMOUNT = 10000

class che_헌납(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "헌납"

    private val isGold: Boolean
        get() = arg?.get("isGold") as? Boolean ?: true

    private val donateAmount: Int
        get() {
            val raw = (arg?.get("amount") as? Number)?.toInt() ?: 100
            val rounded = (raw / 100) * 100
            return maxOf(100, minOf(rounded, MAX_RESOURCE_ACTION_AMOUNT))
        }

    override val fullConditionConstraints: List<Constraint>
        get() {
            val constraints = mutableListOf<Constraint>(
                NotBeNeutral(),
                OccupiedCity(),
                SuppliedCity()
            )
            if (isGold) {
                constraints.add(ReqGeneralGold(GENERAL_MINIMUM_GOLD))
            } else {
                constraints.add(ReqGeneralRice(GENERAL_MINIMUM_RICE))
            }
            return constraints
        }

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val gold = isGold
        val resKey = if (gold) "gold" else "rice"
        val resName = if (gold) "금" else "쌀"

        val currentRes = if (gold) general.funds else general.supplies
        val amount = minOf(donateAmount, currentRes)

        pushLog("$resName <C>${amount}</>을 헌납했습니다. <1>$date</>")
        pushHistoryLog("$resName <C>${amount}</>을 헌납했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${resName}을 헌납했습니다.")

        val exp = 70
        val ded = 100

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"$resKey":${-amount},"experience":$exp,"dedication":$ded,"leadershipExp":1},"nationChanges":{"$resKey":$amount}}"""
        )
    }
}
