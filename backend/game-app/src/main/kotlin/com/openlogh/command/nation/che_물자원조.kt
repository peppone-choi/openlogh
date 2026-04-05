package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val POST_REQ_TURN = 12

class che_물자원조(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "원조"

    override val fullConditionConstraints = listOf(
        ExistsDestNation(), OccupiedCity(), BeChief(),
        SuppliedCity(), DifferentDestNation(),
        ReqNationValue("surlimit", "외교제한", "==", 0, "외교제한중입니다."),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = POST_REQ_TURN

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dn = destFaction ?: return CommandResult(false, logs, "대상 국가 정보를 찾을 수 없습니다")

        // Parse amountList [goldAmount, riceAmount] matching PHP/TS
        val amountList = readNumberList(arg?.get("amountList"))
        val goldAmount: Int
        val riceAmount: Int
        if (amountList.size == 2) {
            goldAmount = amountList[0].toInt()
            riceAmount = amountList[1].toInt()
        } else {
            goldAmount = (arg?.get("goldAmount") as? Number)?.toInt() ?: 0
            riceAmount = (arg?.get("riceAmount") as? Number)?.toInt() ?: 0
        }

        if (goldAmount <= 0 && riceAmount <= 0) {
            return CommandResult(false, logs, "원조 금액이 없습니다")
        }

        val actualGold = goldAmount.coerceIn(0, n.funds)
        val actualRice = riceAmount.coerceIn(0, n.supplies)

        n.funds -= actualGold
        n.supplies -= actualRice
        dn.funds += actualGold
        dn.supplies += actualRice

        // Set surlimit (diplomacy cooldown)
        val currentSurlimit = (n.meta["surlimit"] as? Number)?.toInt() ?: 0
        n.meta["surlimit"] = currentSurlimit + POST_REQ_TURN

        general.experience += 5
        general.dedication += 5

        pushLog("<D><b>${dn.name}</b></>로 금<C>$actualGold</> 쌀<C>$actualRice</>을 지원했습니다. <1>$date</>")
        pushHistoryLog("<D><b>${dn.name}</b></>로 금<C>$actualGold</> 쌀<C>$actualRice</>을 지원했습니다. <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <D><b>${dn.name}</b></>에 물자를 지원했습니다.")
        return CommandResult(true, logs)
    }

    private fun readNumberList(raw: Any?): List<Number> {
        if (raw !is Iterable<*>) return emptyList()
        return raw.mapNotNull { it as? Number }
    }
}
