package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class event_화륜차연구(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "화륜차 연구"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(),
        ReqNationAuxValue("can_화륜차사용", 0, "<", 1, "${actionName}가 이미 완료되었습니다."),
        ReqNationGold(1000 + 100000), ReqNationRice(1000 + 100000)
    )

    override val minConditionConstraints get() = fullConditionConstraints

    override fun getCost() = CommandCost(funds = 100000, supplies = 100000)
    override fun getPreReqTurn() = 23
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다"))
        val cost = getCost()
        n.funds -= cost.funds
        n.supplies -= cost.supplies
        n.meta["can_화륜차사용"] = 1

        val expDed = 100
        general.experience += expDed
        general.dedication += expDed

        val generalName = general.name
        val josaYi = JosaUtil.pick(generalName, "이")

        pushLog("<M>$actionName</> 완료")
        pushHistoryLog("<M>$actionName</> 완료")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <M>$actionName</> 완료")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <M>$actionName</> 완료")
        pushGlobalHistoryLog("<D><b>${nation?.name ?: "국가"}</b></>의 <M>$actionName</>가 완료되었습니다.")
        return CommandResult(true, logs)
    }
}
