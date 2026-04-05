package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_불가침수락(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "불가침 수락"
    override val canDisplay = false
    override val isReservable = false

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(), SuppliedCity(),
        ExistsDestNation(), ExistsDestGeneral(),
        ReqDestNationGeneralMatch(),
        DisallowDiplomacyBetweenStatus(mapOf(0 to "교전 중입니다.", 1 to "선전포고 상태입니다.")),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다"))
        val dn = destFaction ?: return CommandResult(false, listOf("대상 국가 정보를 찾을 수 없습니다"))
        val dg = destOfficer ?: return CommandResult(false, listOf("대상 장수 정보를 찾을 수 없습니다"))

        services!!.diplomacyService.acceptNonAggression(env.sessionId, n.id, dn.id)

        val josaWaDest = JosaUtil.pick(dn.name, "와")
        val josaWaSrc = JosaUtil.pick(n.name, "와")
        pushLog("<D><b>${dn.name}</b></>${josaWaDest} 불가침에 성공했습니다.")
        pushHistoryLog("<D><b>${dn.name}</b></>${josaWaDest} 불가침 성공")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <D><b>${dn.name}</b></>${josaWaDest} 불가침에 합의했습니다.")
        pushDestNationalHistoryLog("<D><b>${n.name}</b></>${josaWaSrc} 불가침 조약을 체결했습니다.")

        pushDestGeneralLog("<D><b>${n.name}</b></>${josaWaSrc} 불가침에 성공했습니다.")
        pushDestGeneralHistoryLog("<D><b>${n.name}</b></>${josaWaSrc} 불가침 성공")

        return CommandResult(true, logs)
    }
}
