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

class che_독립선언(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "독립 선언"

    override val minConditionConstraints = listOf(
        BeChief(),
        EmperorSystemActive(),
        NationIsVassal(),
    )

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        EmperorSystemActive(), NationIsVassal(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 12

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, listOf("국가 정보를 찾을 수 없습니다."))
        val generalName = general.name
        val nationName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")

        val suzerainId = (n.meta[EmperorConstants.NATION_SUZERAIN_ID] as? Number)?.toLong()
        val suzerainName = if (suzerainId != null && suzerainId > 0) {
            services!!.nationRepository.findById(suzerainId).orElse(null)?.name ?: "종주국"
        } else {
            "종주국"
        }

        n.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_INDEPENDENT
        n.meta.remove(EmperorConstants.NATION_SUZERAIN_ID)

        pushLog("<D><b>${suzerainName}</b></>으로부터 독립을 선언하였습니다. <1>${formatDate()}</>")
        pushHistoryLog("<D><b>${suzerainName}</b></>으로부터 독립 선언")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <D><b>${suzerainName}</b></>으로부터 독립을 선언하였습니다.")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${nationName}</b></>의 독립을 선언하였습니다!")
        pushGlobalHistoryLog("<R><b>【독립】</b></><D><b>${nationName}</b></>의 <Y>${generalName}</>${josaYi} <D><b>${suzerainName}</b></>으로부터 독립을 선언하였습니다.")

        if (suzerainId != null && suzerainId > 0) {
            val text = "【외교】${env.year}년 ${env.month}월:${nationName}이(가) 독립을 선언"
            services!!.messageService?.sendNationalMessage(
                worldId = env.worldId,
                srcNationId = n.id,
                destNationId = suzerainId,
                srcGeneralId = general.id,
                text = text
            )
        }

        return CommandResult(true, logs)
    }
}
