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

class che_선양요구(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "선양 요구"

    override val minConditionConstraints = listOf(
        BeChief(),
        EmperorSystemActive(),
        NationHasEmperorGeneral(),
    )

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        EmperorSystemActive(), NationNotExempt(),
        NationIsEmperor(), NationHasEmperorGeneral(),
        ReqNationValue("level", "국가 규모", ">=", 6, "왕(6) 이상의 작위가 필요합니다."),
        ReqNationCityCount(20),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 24

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다."))
        val generalName = general.name
        val nationName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")

        val emperorGeneralId = (env.gameStor["emperorGeneralId"] as? Number)?.toLong()
            ?: return CommandResult(false, listOf("천자 정보를 찾을 수 없습니다."))
        val emperorGeneral = services!!.generalRepository.findById(emperorGeneralId).orElse(null)
            ?: return CommandResult(false, listOf("천자 장수를 찾을 수 없습니다."))

        val emperorName = emperorGeneral.name

        emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS] = EmperorConstants.EMPEROR_ABDICATED
        emperorGeneral.npcState = 0
        services!!.generalRepository.save(emperorGeneral)

        n.meta[EmperorConstants.NATION_EMPEROR_TYPE] = EmperorConstants.TYPE_LEGITIMATE

        pushLog("<C><b>${emperorName}</b></>에게 선양을 받아 제위에 올랐습니다. <1>${formatDate()}</>")
        pushHistoryLog("<C><b>${emperorName}</b></>에게 선양을 받음")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <C><b>${emperorName}</b></>에게 선양을 받아 제위에 올랐습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <C><b>${emperorName}</b></>에게 선양을 받아 <D><b>${nationName}</b></>에서 제위에 올랐습니다!")
        pushGlobalHistoryLog("<C><b>【선양】</b></><C><b>${emperorName}</b></>${JosaUtil.pick(emperorName, "이")} <Y>${generalName}</>에게 선양하였습니다.")

        return CommandResult(true, logs)
    }
}
