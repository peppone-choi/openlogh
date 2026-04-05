package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_ATMOS_BY_COMMAND = 100
private const val ATMOS_DELTA = 30.0
private const val TRAIN_SIDE_EFFECT_RATE = 1.0

class che_사기진작(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "사기진작"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                NotWanderingNation(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralGold(cost.funds),
                ReqGeneralAtmosMargin(MAX_ATMOS_BY_COMMAND)
            )
        }

    override fun getCost(): CommandCost {
        val gold = general.ships / 100
        return CommandCost(gold = gold)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val leadership = general.leadership.toInt()
        val crew = general.ships
        val currentAtmos = general.morale.toInt()

        val atmosDelta = if (env.atmosDelta > 0) env.atmosDelta else ATMOS_DELTA
        val maxAtmos = if (env.maxAtmosByCommand > 0) env.maxAtmosByCommand else MAX_ATMOS_BY_COMMAND
        val rawScore = (leadership * 100.0 / crew) * atmosDelta * DomesticUtils.statBonus(general.administration.toInt())
        val maxPossible = maxOf(0, maxAtmos - currentAtmos)
        val score = minOf(maxOf(rawScore.roundToInt(), 0), maxPossible)

        val trainSideEffectRate = if (env.trainSideEffectByAtmosTurn > 0) env.trainSideEffectByAtmosTurn else TRAIN_SIDE_EFFECT_RATE
        val sideEffect = maxOf(0, (general.training * trainSideEffectRate).toInt())

        val scoreText = "%,d".format(score)
        pushLog("사기치가 <C>${scoreText}</> 상승했습니다. <1>$date</>")
        pushHistoryLog("사기치가 <C>${scoreText}</> 상승했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 사기진작을 실시했습니다.")

        val exp = 100
        val ded = 70
        val cost = getCost()

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"atmos":$score,"train":${sideEffect - general.training},"experience":$exp,"dedication":$ded,"leadershipExp":1},"dexChanges":{"crewType":${general.shipClass},"amount":$score}}"""
        )
    }
}
