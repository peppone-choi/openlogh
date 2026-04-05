package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

private const val INITIAL_NATION_GEN_LIMIT = 10
private const val STRATEGIC_GLOBAL_DELAY = 9
private const val PRE_REQ_TURN = 0

class che_이호경식(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "이호경식"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(), ExistsDestNation(),
        AvailableStrategicCommand()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN

    override fun getPostReqTurn(): Int {
        val genCount = max(nation?.officerCount ?: 1, INITIAL_NATION_GEN_LIMIT)
        return (sqrt(genCount * 16.0) * 10).roundToInt()
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dn = destFaction ?: return CommandResult(false, logs, "대상 국가 정보를 찾을 수 없습니다")

        val generalName = general.name
        val nationName = n.name
        val destNationName = dn.name

        val josaYi = JosaUtil.pick(generalName, "이")
        val josaYiNation = JosaUtil.pick(nationName, "이")
        val josaUl = JosaUtil.pick(actionName, "을")

        // Experience and dedication: 5 * (preReqTurn + 1)
        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        pushLog("$actionName 발동! <1>$date</>")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>에 <M>${actionName}</>${josaUl} 발동하였습니다.")
        pushGlobalHistoryLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>에 <M>${actionName}</>${josaUl} 발동했습니다.")

        // Broadcast to own nation generals
        val broadcastMessage = "<Y>${generalName}</>${josaYi} <G><b>${destNationName}</b></>에 <M>${actionName}</>${josaUl} 발동하였습니다."
        broadcastToNationGenerals(n.id, general.id, broadcastMessage)

        // Broadcast to dest nation generals
        val destBroadcastMessage = "<D><b>${nationName}</b></>${josaYiNation} 아국에 <M>${actionName}</>${josaUl} 발동하였습니다."
        broadcastToNationGenerals(dn.id, null, destBroadcastMessage)

        // Dest nation history
        pushDestNationalHistoryLogFor(dn.id,
            "<D><b>${nationName}</b></>의 <Y>${generalName}</>${josaYi} 아국에 <M>${actionName}</>${josaUl} 발동")

        // General and nation history
        pushHistoryLog("<D><b>${destNationName}</b></>에 <M>${actionName}</>${josaUl} 발동")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>에 <M>${actionName}</>${josaUl} 발동")

        // Strategic command limit
        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        // PHP: term = IF(state=0, 3, term+3), state=1 (declared)
        // Get current diplomacy state between the two nations
        val currentDiplomacy = services!!.diplomacyService.getDiplomacyState(env.sessionId, n.id, dn.id)
        val currentState = currentDiplomacy?.state ?: 2 // default neutral
        val currentTerm = currentDiplomacy?.term ?: 0
        val newTerm = if (currentState == 0) 3 else currentTerm + 3
        services!!.diplomacyService.setDiplomacyState(env.sessionId, n.id, dn.id, state = 1, term = newTerm)

        // Update nation fronts
        services!!.factionService?.setNationFront(env.sessionId, n.id)
        services!!.factionService?.setNationFront(env.sessionId, dn.id)

        return CommandResult(true, logs)
    }
}
