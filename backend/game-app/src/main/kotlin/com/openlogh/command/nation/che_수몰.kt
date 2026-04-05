package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

private const val INITIAL_NATION_GEN_LIMIT = 10
private const val STRATEGIC_GLOBAL_DELAY = 9
private const val PRE_REQ_TURN = 2
private const val DAMAGE_RATE = 0.2

class che_수몰(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "수몰"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(),
        NotNeutralDestCity(), NotOccupiedDestCity(),
        BattleGroundCity(), AvailableStrategicCommand()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN

    override fun getPostReqTurn(): Int {
        return 20
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dc = destPlanet ?: return CommandResult(false, logs, "대상 도시 정보를 찾을 수 없습니다")

        val destNationId = dc.factionId
        val generalName = general.name
        val nationName = n.name

        val josaYi = JosaUtil.pick(generalName, "이")
        val josaYiNation = JosaUtil.pick(nationName, "이")

        // Experience and dedication: 5 * (preReqTurn + 1)
        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        pushLog("수몰 발동! <1>$date</>")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <G><b>${dc.name}</b></>에 <M>수몰</>을 발동했습니다.")
        pushGlobalHistoryLog("<Y>${generalName}</>${josaYi} <G><b>${dc.name}</b></>에 <M>수몰</>을 발동했습니다.")

        val broadcastMessage = "<Y>${generalName}</>${josaYi} <G><b>${dc.name}</b></>에 <M>수몰</>을 발동하였습니다."

        // Broadcast to own nation generals
        broadcastToNationGenerals(n.id, general.id, broadcastMessage)

        // Broadcast to dest nation generals
        val destBroadcastMessage = "<G><b>${dc.name}</b></>에 <M>수몰</>이 발동되었습니다."
        broadcastToNationGenerals(destNationId, null, destBroadcastMessage)

        // Dest nation history
        pushDestNationalHistoryLogFor(destNationId,
            "<D><b>${nationName}</b></>의 <Y>${generalName}</>${josaYi} 아국의 <G><b>${dc.name}</b></>에 <M>수몰</>을 발동")

        val beforePop = dc.population
        dc.orbitalDefense = (dc.orbitalDefense * DAMAGE_RATE).toInt()
        dc.fortress = (dc.fortress * DAMAGE_RATE).toInt()
        dc.population = (dc.population * 0.5).toInt()
        dc.dead += ((beforePop - dc.population) * 0.1).toInt()

        // General history + national history
        pushHistoryLog("<G><b>${dc.name}</b></>에 <M>수몰</>을 발동")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <G><b>${dc.name}</b></>에 <M>수몰</>을 발동")

        // Strategic command limit
        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        return CommandResult(true, logs)
    }
}
