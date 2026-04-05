package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.roundToInt
import kotlin.random.Random

private const val DEBUFF_FRONT = 0.5
private const val EXP_RATE = 0.7 / 3.0
private const val DED_RATE = 1.0 / 3.0

class che_물자조달(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "물자조달"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            NotWanderingNation(),
            OccupiedCity(),
            SuppliedCity()
        )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val leadership = general.leadership.toInt()
        val strength = general.command.toInt()
        val intel = general.intelligence.toInt()

        val resourceType = if (rng.nextDouble() < 0.5) "gold" else "rice"
        val resName = if (resourceType == "gold") "금" else "쌀"

        // Legacy: score = (lead+str+intel) * getDomesticExpLevelBonus(explevel) * rng(0.8,1.2)
        var score = (leadership + strength + intel).toDouble() *
            DomesticUtils.getDomesticExpLevelBonus(general.expLevel.toInt()) *
            (0.8 + rng.nextDouble() * 0.4)

        // Legacy: successRatio/failRatio via onCalcDomestic, then weighted choice
        var successRatio = DomesticUtils.applyModifier(services, general, nation, "조달", "success", 0.1)
        var failRatio = DomesticUtils.applyModifier(services, general, nation, "조달", "fail", 0.3)
        val normalRatio = 1.0 - failRatio - successRatio

        val pick = DomesticUtils.choiceUsingWeight(rng, mapOf(
            "fail" to failRatio,
            "success" to successRatio,
            "normal" to normalRatio
        ))

        // Legacy: CriticalScoreEx — success=[2.2,3.0), fail=[0.2,0.4), normal=1.0
        score *= DomesticUtils.criticalScoreEx(rng, pick)

        // Legacy: onCalcDomestic('조달', 'score', score)
        score = DomesticUtils.applyModifier(services, general, nation, "조달", "score", score)

        val c = city
        if (c != null && (c.frontState.toInt() == 1 || c.frontState.toInt() == 3)) {
            var debuff = DEBUFF_FRONT
            // Capital city front debuff scaling: reduced penalty in early years
            if (nation?.capitalPlanetId == c.id) {
                val relYear = env.year - env.startYear
                if (relYear < 25) {
                    val debuffScale = maxOf(0, minOf(relYear - 5, 20)) * 0.05
                    debuff = (debuffScale * DEBUFF_FRONT) + (1 - debuffScale)
                }
            }
            score *= debuff
        }

        val finalScore = score.roundToInt()
        val exp = (finalScore * EXP_RATE).roundToInt()
        val ded = (finalScore * DED_RATE).roundToInt()

         val scoreText = "%,d".format(finalScore)
         val logMessage = when (pick) {
             "fail" -> "조달을 <R>실패</>하여 ${resName}을 <C>${scoreText}</> 조달했습니다. <1>$date</>"
             "success" -> "조달을 <S>성공</>하여 ${resName}을 <C>${scoreText}</> 조달했습니다. <1>$date</>"
             else -> "${resName}을 <C>${scoreText}</> 조달했습니다. <1>$date</>"
         }
        pushLog(logMessage)
        pushHistoryLog(logMessage)
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 물자조달을 실행했습니다.")

        // random stat exp weighted by stats
        val statWeights = listOf(
            "leadershipExp" to leadership,
            "strengthExp" to strength,
            "intelExp" to intel
        )
        val totalWeight = statWeights.sumOf { it.second }
        var roll = (rng.nextDouble() * totalWeight)
        var incStat = "leadershipExp"
        for ((key, weight) in statWeights) {
            roll -= weight
            if (roll < 0) { incStat = key; break }
        }

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"experience":$exp,"dedication":$ded,"$incStat":1},"nationChanges":{"$resourceType":$finalScore},"criticalResult":"$pick"}"""
        )
    }
}
