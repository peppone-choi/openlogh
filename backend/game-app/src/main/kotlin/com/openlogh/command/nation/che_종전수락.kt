package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_종전수락(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "종전 수락"
    override val canDisplay = false
    override val isReservable = false

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), ExistsDestNation(), ExistsDestGeneral(),
        ReqDestNationGeneralMatch(),
        AllowDiplomacyBetweenStatus(listOf(0, 1), "교전 또는 선전포고 상태가 아닙니다.")
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다"))
        val dn = destFaction ?: return CommandResult(false, listOf("대상 국가 정보를 찾을 수 없습니다"))
        val dg = destOfficer ?: return CommandResult(false, listOf("대상 장수 정보를 찾을 수 없습니다"))

        val generalName = general.name
        val factionName = n.name
        val destNationName = dn.name

        val josaYiGeneral = JosaUtil.pick(generalName, "이")
        val josaYiNation = JosaUtil.pick(factionName, "이")

        services!!.diplomacyService.acceptCeasefire(env.sessionId, n.id, dn.id)

        // Update nation fronts
        services!!.factionService?.setNationFront(env.sessionId, n.id)
        services!!.factionService?.setNationFront(env.sessionId, dn.id)

        // General action + history logs
        val josaWaDest = JosaUtil.pick(destNationName, "와")
        pushLog("<D><b>${destNationName}</b></>${josaWaDest} 종전에 합의했습니다.")
        pushHistoryLog("<D><b>${destNationName}</b></>${josaWaDest} 종전 수락")

        // Global action + history
        pushGlobalActionLog("<Y>${generalName}</>${josaYiGeneral} <D><b>${destNationName}</b></>${josaWaDest} <M>종전 합의</> 하였습니다.")
        pushGlobalHistoryLog("<Y><b>【종전】</b></><D><b>${factionName}</b></>${josaYiNation} <D><b>${destNationName}</b></>${josaWaDest} <M>종전 합의</> 하였습니다.")

        // Own national history
        pushNationalHistoryLog("<D><b>${destNationName}</b></>${josaWaDest} 종전")

        // Dest general logs
        val josaWaSrc = JosaUtil.pick(factionName, "와")
        pushDestGeneralLog("<D><b>${factionName}</b></>${josaWaSrc} 종전에 성공했습니다.")
        pushDestGeneralHistoryLog("<D><b>${factionName}</b></>${josaWaSrc} 종전 성공")
        pushDestNationalHistoryLogFor(dn.id, "<D><b>${factionName}</b></>${josaWaSrc} 종전")

        return CommandResult(true, logs)
    }
}
