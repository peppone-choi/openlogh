package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.NationCommand
import com.openlogh.command.constraint.*
import com.openlogh.engine.EmperorConstants
import com.openlogh.entity.General
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_칭제(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "칭제"

    override val minConditionConstraints = listOf(
        BeChief(),
        EmperorSystemActive(),
        NationNotExempt(),
        NationNotEmperor(),
    )

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        EmperorSystemActive(), NationNotExempt(), NationNotEmperor(),
        ReqNationValue("level", "국가 규모", ">=", 8, "왕(8) 이상의 작위가 필요합니다."),
        ReqNationCityCount(20),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 12

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다."))
        val generalName = general.name
        val nationName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")

        n.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_EMPEROR
        n.meta[EmperorConstants.NATION_EMPEROR_TYPE] = EmperorConstants.TYPE_SELF_PROCLAIMED

        pushLog("<C><b>황제</b></>를 자칭하였습니다. <1>${formatDate()}</>")
        pushHistoryLog("<D><b>${nationName}</b></>에서 황제를 자칭")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} 황제를 자칭하였습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>에서 <C><b>황제</b></>를 자칭하였습니다!")
        pushGlobalHistoryLog("<C><b>【칭제】</b></><Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>에서 황제를 자칭하였습니다.")

        return CommandResult(true, logs)
    }
}
