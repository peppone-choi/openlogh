package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.engine.SovereignConstants
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_천자맞이(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

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
        val factionName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")

        val emperorGeneralId = (constraintEnv["wanderingEmperorGeneralId"] as? Number)?.toLong()
            ?: return CommandResult(false, listOf("유랑 중인 천자를 찾을 수 없습니다."))
        val emperorGeneral = services!!.officerRepository.findById(emperorGeneralId).orElse(null)
            ?: return CommandResult(false, listOf("천자 장수를 찾을 수 없습니다."))

        emperorGeneral.factionId = n.id
        emperorGeneral.planetId = general.planetId
        emperorGeneral.meta[SovereignConstants.GENERAL_EMPEROR_STATUS] = SovereignConstants.EMPEROR_ENTHRONED
        services!!.officerRepository.save(emperorGeneral)

        n.meta[SovereignConstants.NATION_IMPERIAL_STATUS] = SovereignConstants.STATUS_REGENT
        n.meta[SovereignConstants.NATION_EMPEROR_TYPE] = SovereignConstants.TYPE_LEGITIMATE

        val emperorName = emperorGeneral.name
        val josaReul = JosaUtil.pick(emperorName, "를")

        pushLog("<C><b>${emperorName}</b></>${josaReul} 맞이하여 옹립하였습니다. <1>${formatDate()}</>")
        pushHistoryLog("<C><b>${emperorName}</b></>${josaReul} 옹립")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <C><b>${emperorName}</b></>${josaReul} 옹립하였습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>에서 <C><b>${emperorName}</b></>${josaReul} 맞이하여 옹립하였습니다!")
        pushGlobalHistoryLog("<C><b>【옹립】</b></><Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>에서 <C><b>${emperorName}</b></>${josaReul} 옹립하였습니다.")

        return CommandResult(true, logs)
    }
}
