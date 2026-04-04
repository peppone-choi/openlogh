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

class che_천자맞이(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "천자 맞이"

    override val minConditionConstraints = listOf(
        BeChief(),
        EmperorSystemActive(),
        NationNotExempt(),
    )

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        EmperorSystemActive(), NationNotExempt(),
        WanderingEmperorExists(),
        WanderingEmperorInTerritory(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 12

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다."))
        val generalName = general.name
        val nationName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")

        val emperorGeneralId = (constraintEnv["wanderingEmperorGeneralId"] as? Number)?.toLong()
            ?: return CommandResult(false, listOf("유랑 중인 천자를 찾을 수 없습니다."))
        val emperorGeneral = services!!.generalRepository.findById(emperorGeneralId).orElse(null)
            ?: return CommandResult(false, listOf("천자 장수를 찾을 수 없습니다."))

        emperorGeneral.nationId = n.id
        emperorGeneral.cityId = general.cityId
        emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS] = EmperorConstants.EMPEROR_ENTHRONED
        services!!.generalRepository.save(emperorGeneral)

        n.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_REGENT
        n.meta[EmperorConstants.NATION_EMPEROR_TYPE] = EmperorConstants.TYPE_LEGITIMATE

        val emperorName = emperorGeneral.name
        val josaReul = JosaUtil.pick(emperorName, "를")

        pushLog("<C><b>${emperorName}</b></>${josaReul} 맞이하여 옹립하였습니다. <1>${formatDate()}</>")
        pushHistoryLog("<C><b>${emperorName}</b></>${josaReul} 옹립")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <C><b>${emperorName}</b></>${josaReul} 옹립하였습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>에서 <C><b>${emperorName}</b></>${josaReul} 맞이하여 옹립하였습니다!")
        pushGlobalHistoryLog("<C><b>【옹립】</b></><Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>에서 <C><b>${emperorName}</b></>${josaReul} 옹립하였습니다.")

        return CommandResult(true, logs)
    }
}
