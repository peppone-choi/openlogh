package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DEFAULT_TRAIN_LOW = 40
private const val DEFAULT_ATMOS_LOW = 40
private const val SCORE_DIVISOR = 200000.0

class che_단련(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "단련"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                ReqGeneralCrew(),
                ReqGeneralStatValue({ it.training.toInt() }, "훈련", DEFAULT_TRAIN_LOW),
                ReqGeneralStatValue({ it.morale.toInt() }, "사기", DEFAULT_ATMOS_LOW),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies)
            )
        }

    override fun getCost() = CommandCost(funds = env.develCost, supplies = env.develCost)
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val crew = general.ships
        val train = general.training.toInt()
        val atmos = general.morale.toInt()

        // Legacy parity: fail < 0.33, success > 0.66, otherwise normal
        val criticalRoll = rng.nextDouble()
        val pick: String
        val multiplier: Int
        when {
            criticalRoll < 0.33 -> { pick = "fail"; multiplier = 1 }
            criticalRoll > 0.66 -> { pick = "success"; multiplier = 3 }
            else -> { pick = "normal"; multiplier = 2 }
        }

        val baseScore = (crew.toDouble() * train * atmos) / SCORE_DIVISOR
        val score = (baseScore * multiplier).roundToInt()
        val scoreText = "%,d".format(score)

        val armTypeName = getCrewTypeName(general.shipClass.toInt()) ?: "병사"

        val logMessage = when (pick) {
             "fail" -> "단련이 <R>지지부진</>하여 ${armTypeName} 숙련도가 <C>${scoreText}</> 향상되었습니다. <1>$date</>"
             "success" -> "단련이 <S>일취월장</>하여 ${armTypeName} 숙련도가 <C>${scoreText}</> 향상되었습니다. <1>$date</>"
             else -> "${armTypeName} 숙련도가 <C>${scoreText}</> 향상되었습니다. <1>$date</>"
         }
        pushLog(logMessage)
        pushHistoryLog(logMessage)
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 단련을 수행했습니다.")

        val exp = crew / 400
        val cost = getCost()

        // random stat exp weighted by stats
        val leadership = general.leadership.toInt()
        val strength = general.command.toInt()
        val intel = general.intelligence.toInt()
        val statWeights = listOf(
            "leadershipExp" to leadership,
            "strengthExp" to strength,
            "intelExp" to intel
        )
        val totalWeight = statWeights.sumOf { it.second }
        var roll = rng.nextDouble() * totalWeight
        var incStat = "leadershipExp"
        for ((key, weight) in statWeights) {
            roll -= weight
            if (roll < 0) { incStat = key; break }
        }

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"rice":${-cost.supplies},"experience":$exp,"$incStat":1},"dexChanges":{"crewType":${general.shipClass},"amount":$score},"criticalResult":"$pick"}"""
        )
    }
}
