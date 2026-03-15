package com.opensam.command.nation

import com.opensam.command.CommandCost
import com.opensam.command.CommandEnv
import com.opensam.command.CommandResult
import com.opensam.command.NationCommand
import com.opensam.command.constraint.*
import com.opensam.engine.EmperorConstants
import com.opensam.entity.General
import com.opensam.util.JosaUtil
import kotlin.random.Random

class che_신속(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "신속"

    override val minConditionConstraints = listOf(
        BeChief(),
        EmperorSystemActive(),
        NationNotExempt(),
        NationIsIndependent(),
    )

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        EmperorSystemActive(), NationNotExempt(), NationIsIndependent(),
        ExistsDestNation(), DestNationIsEmperor(), DifferentDestNation(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 12

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다."))
        val dn = destNation ?: return CommandResult(false, listOf("대상 국가 정보를 찾을 수 없습니다."))
        val generalName = general.name
        val nationName = n.name
        val destNationName = dn.name
        val josaYi = JosaUtil.pick(generalName, "이")
        val josaEge = JosaUtil.pick(destNationName, "에")

        n.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_VASSAL
        n.meta[EmperorConstants.NATION_SUZERAIN_ID] = dn.id

        pushLog("<D><b>${destNationName}</b></>${josaEge} 신속하였습니다. <1>${formatDate()}</>")
        pushHistoryLog("<D><b>${destNationName}</b></>${josaEge} 신속")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>${josaEge} 신속하였습니다.")
        pushDestNationalHistoryLog("<D><b>${nationName}</b></>의 <Y>${generalName}</>${josaYi} 아국에 신속하였습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>을(를) 이끌고 <D><b>${destNationName}</b></>${josaEge} <S>신속</>하였습니다.")
        pushGlobalHistoryLog("<S><b>【신속】</b></><D><b>${nationName}</b></>의 <Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>${josaEge} 신속하였습니다.")

        val text = "【외교】${env.year}년 ${env.month}월:${nationName}이(가) ${destNationName}에 신속"
        services!!.messageService?.sendNationalMessage(
            worldId = env.worldId,
            srcNationId = n.id,
            destNationId = dn.id,
            srcGeneralId = general.id,
            text = text
        )

        return CommandResult(true, logs)
    }
}
