package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_종전제의(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "종전 제의"

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(), SuppliedCity(),
        ExistsDestNation(),
        AllowDiplomacyBetweenStatus(listOf(0, 1), "교전 또는 선전포고 상태가 아닙니다.")
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다"))
        val dn = destFaction ?: return CommandResult(false, listOf("대상 국가 정보를 찾을 수 없습니다"))

        val josaRo = JosaUtil.pick(dn.name, "로")
        pushLog("<D><b>${dn.name}</b></>${josaRo} 종전 제의 서신을 보냈습니다.<1>${formatDate()}</>")
        pushHistoryLog("<D><b>${dn.name}</b></>${josaRo} 종전 제의 서신을 보냈습니다.")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <D><b>${dn.name}</b></>${josaRo} 종전 제의를 보냈습니다.")
        pushDestNationalHistoryLog("<D><b>${n.name}</b></>의 <Y>${general.name}</>${pickJosa(general.name, "이")} 아국에 종전을 제의했습니다.")

        services!!.diplomacyService.proposeCeasefire(env.sessionId, n.id, dn.id)

        return CommandResult(true, logs)
    }
}
