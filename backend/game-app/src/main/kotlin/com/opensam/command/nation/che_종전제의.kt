package com.opensam.command.nation

import com.opensam.command.CommandCost
import com.opensam.command.CommandEnv
import com.opensam.command.CommandResult
import com.opensam.command.NationCommand
import com.opensam.command.constraint.*
import com.opensam.entity.General
import com.opensam.util.JosaUtil
import kotlin.random.Random

class che_종전제의(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "종전 제의"

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(), SuppliedCity(),
        ExistsDestNation()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다"))
        val dn = destNation ?: return CommandResult(false, listOf("대상 국가 정보를 찾을 수 없습니다"))

        val josaRo = JosaUtil.pick(dn.name, "로")
        pushLog("<D><b>${dn.name}</b></>${josaRo} 종전 제의 서신을 보냈습니다.<1>${formatDate()}</>")
        pushHistoryLog("<D><b>${dn.name}</b></>${josaRo} 종전 제의 서신을 보냈습니다.")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <D><b>${dn.name}</b></>${josaRo} 종전 제의를 보냈습니다.")
        pushDestNationalHistoryLog("<D><b>${n.name}</b></>의 <Y>${general.name}</>${pickJosa(general.name, "이")} 아국에 종전을 제의했습니다.")

        services!!.diplomacyService.proposeCeasefire(env.worldId, n.id, dn.id)
        general.experience += 50
        general.dedication += 50

        return CommandResult(true, logs)
    }
}
