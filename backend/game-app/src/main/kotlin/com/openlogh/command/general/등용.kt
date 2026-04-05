package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class 등용(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "등용"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val constraints = mutableListOf(
                ReqEnvValue("join_mode", "!=", "onlyRandom", "랜덤 임관만 가능합니다"),
                NotBeNeutral(),
                OccupiedCity(),
                SuppliedCity(),
                ExistsDestGeneral(),
                DifferentNationDestGeneral(),
                ReqGeneralGold(getCost().funds),
            )
            if (destOfficer?.officerLevel?.toInt() == 20) {
                constraints.add(AlwaysFail("군주에게는 등용장을 보낼 수 없습니다."))
            }
            return constraints
        }

    override val minConditionConstraints = listOf(
        ReqEnvValue("join_mode", "!=", "onlyRandom", "랜덤 임관만 가능합니다"),
        NotBeNeutral(),
        OccupiedCity(),
        SuppliedCity(),
    )

    override fun getCost(): CommandCost {
        val develCost = env.develCost
        val dg = destOfficer
        if (dg == null) return CommandCost(funds = develCost)
        val reqGold = kotlin.math.round(develCost + (dg.experience + dg.dedication) / 1000.0).toInt() * 10
        return CommandCost(funds = reqGold)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val dg = destOfficer ?: return CommandResult(false, listOf("대상 장수를 찾을 수 없습니다."))
        val destName = dg.name
        val cost = getCost()
        val factionName = nation?.name ?: "알 수 없음"
        val josaRo = pickJosa(factionName, "로")

        services?.messageService?.sendMessage(
            worldId = env.sessionId,
            mailboxCode = "personal",
            mailboxType = "PRIVATE",
            messageType = "recruitment",
            srcId = general.id,
            destId = dg.id,
            payload = mapOf(
                "action" to "scout",
                "fromGeneralId" to general.id,
                "fromGeneralName" to general.name,
                "fromNationId" to general.factionId,
                "fromNationName" to factionName,
                "text" to "${factionName}${josaRo} 망명 권유 서신",
            ),
        )

        pushLog("<Y>${destName}</>에게 등용 권유 서신을 보냈습니다. <1>$date</>")
        pushHistoryLog("<Y>${destName}</>에게 등용 서신 발송")
        pushLog("_destGeneralLog:${dg.id}:<Y>${general.name}</>${pickJosa(general.name, "이")} 등용 서신을 보냈습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"experience":100,"dedication":200,"leadershipExp":1}}"""
        )
    }
}
