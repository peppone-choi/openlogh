package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.math.roundToInt
import kotlin.random.Random

private const val INITIAL_NATION_GEN_LIMIT = 10
private const val STRATEGIC_GLOBAL_DELAY = 9
private const val PRE_REQ_TURN = 2
private const val NPC_TYPE = 4

class che_의병모집(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "의병모집"

    override val fullConditionConstraints = listOf(
        BeChief(), NotBeNeutral(), OccupiedCity(),
        AvailableStrategicCommand(),
        NotOpeningPart(env.year - env.startYear)
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN

    override fun getPostReqTurn(): Int {
        return 100
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val c = city ?: return CommandResult(false, logs, "도시 정보를 찾을 수 없습니다")

        val generalName = general.name
        val nationName = n.name
        val josaYi = JosaUtil.pick(generalName, "이")
        val josaYiNation = JosaUtil.pick(nationName, "이")
        val josaUl = JosaUtil.pick(actionName, "을")

        // Experience and dedication: 5 * (preReqTurn + 1)
        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        pushLog("$actionName 발동! <1>$date</>")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <M>${actionName}</>${josaUl} 발동하였습니다.")
        pushGlobalHistoryLog("<Y>${generalName}</>${josaYi} <M>${actionName}</>${josaUl} 발동했습니다.")

        // Broadcast to own nation generals
        val broadcastMessage = "<Y>${generalName}</>${josaYi} <M>${actionName}</>${josaUl} 발동하였습니다."
        broadcastToNationGenerals(n.id, general.id, broadcastMessage)

        // History logs
        pushHistoryLog("<M>${actionName}</>${josaUl} 발동")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <M>${actionName}</>${josaUl} 발동")

        c.population = (c.population * 0.5).toInt()

        // Calculate NPC count: 3 + round(avgGenCount / 8)
        val avgGenCount = services!!.factionRepository.getAverageGennum(env.sessionId)
        val createGenCount = 3 + (avgGenCount / 8.0).roundToInt()

        val avgStats = runCatching { services!!.officerRepository.getAverageStats(env.sessionId, n.id) }.getOrNull()
        val avgExperience = avgStats?.experience ?: 0
        val avgDedication = avgStats?.dedication ?: 0

        val officerPoolService = services!!.officerPoolService
        if (officerPoolService != null) {
            for (i in 1..createGenCount) {
                officerPoolService.pickAndCreateNpc(
                    worldId = env.sessionId,
                    nationId = n.id,
                    cityId = c.id,
                    npcType = NPC_TYPE,
                    birthYear = env.year - 20,
                    deathYear = env.year + 10,
                    killTurn = rng.nextInt(64, 71),
                    gold = 1000,
                    rice = 1000,
                    experience = avgExperience,
                    dedication = avgDedication,
                    specAge = 19,
                    rng = rng
                )
            }
        } else {
            repeat(createGenCount) { idx ->
                val npc = Officer(
                    sessionId = env.sessionId,
                    name = "의병${idx + 1}",
                    factionId = n.id,
                    planetId = c.id,
                    npcState = NPC_TYPE.toShort(),
                    bornYear = (env.year - 20).toShort(),
                    deadYear = (env.year + 10).toShort(),
                    officerLevel = 1,
                    gold = 1000,
                    rice = 1000,
                    experience = avgExperience,
                    dedication = avgDedication,
                )
                services!!.officerRepository.save(npc)
            }
        }

        // Update nation gennum and strategic limit
        n.officerCount = n.officerCount + createGenCount
        n.strategicCmdLimit = STRATEGIC_GLOBAL_DELAY.toShort()

        return CommandResult(true, logs)
    }
}
