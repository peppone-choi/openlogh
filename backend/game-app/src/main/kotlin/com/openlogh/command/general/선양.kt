package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class 선양(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "선양"

    override val fullConditionConstraints = listOf(
        BeLord(),
        ExistsDestGeneral(),
        FriendlyDestGeneral(),
    )

    override val minConditionConstraints = listOf(
        BeLord(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val dg = destOfficer ?: return CommandResult(success = false, logs = listOf("선양 대상 장수가 없습니다."))
        val destGeneralName = dg.name
        val factionName = nation?.name ?: "알 수 없음"
        val generalName = general.name

        // Legacy PHP: Check penalty keys (noChief, noFoundNation, noAmbassador)
        val penaltyKeys = listOf("noChief", "noFoundNation", "noAmbassador")
        val penalty = dg.meta["penalty"]
        if (penalty is Map<*, *>) {
            for (key in penaltyKeys) {
                if (penalty.containsKey(key)) {
                    pushLog("선양할 수 없는 장수입니다.")
                    return CommandResult(success = false, logs = logs)
                }
            }
        }

        // Global history log
        val josaYi = if (generalName.last().code % 28 != 0) "이" else ""
        pushLog("[GLOBAL_HISTORY]<Y><b>【선양】</b></><Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>의 군주 자리를 <Y>${destGeneralName}</>에게 선양했습니다.")
        // National history log
        pushLog("[NATIONAL_HISTORY]<Y>${generalName}</>${josaYi} <Y>${destGeneralName}</>에게 선양")
        // General action log
        pushLog("<Y>${destGeneralName}</>에게 군주의 자리를 물려줍니다. <1>$date</>")
        // General history log
        pushLog("[HISTORY]<D><b>${factionName}</b></>의 군주자리를 <Y>${destGeneralName}</>에게 선양")
        pushHistoryLog("<D><b>${factionName}</b></>의 군주자리를 <Y>${destGeneralName}</>에게 선양")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <Y>${destGeneralName}</>에게 군주 자리를 선양했습니다.")
        pushGlobalHistoryLog("<Y><b>【선양】</b></><Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>의 군주 자리를 <Y>${destGeneralName}</>에게 선양했습니다.")
        pushLog("_destGeneralLog:${dg.id}:<Y>${generalName}</>에게서 군주의 자리를 물려받습니다.")
        pushLog("_destGeneralHistory:${dg.id}:<D><b>${factionName}</b></>의 군주자리를 물려 받음")

        // Legacy PHP: experience *= 0.7
        val expMultiplier = 0.7

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"officerLevel":1,"officerPlanet":0,"experienceMultiplier":$expMultiplier},"nationChanges":{"chiefGeneralId":${dg.id}},"destGeneralChanges":{"generalId":${dg.id},"officerLevel":20,"officerPlanet":0}}"""
        )
    }
}
