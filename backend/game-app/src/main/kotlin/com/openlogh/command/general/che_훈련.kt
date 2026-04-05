package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.roundToInt
import kotlin.random.Random

// Default values; prefer env.maxTrainByCommand and env.trainDelta when available
private const val DEFAULT_MAX_TRAIN_BY_COMMAND = 100
private const val DEFAULT_TRAIN_DELTA = 30.0
private const val ATMOS_SIDE_EFFECT_RATE = 1.0

class che_훈련(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "훈련"

    private val maxTrain: Int
        get() = if (env.maxTrainByCommand > 0) env.maxTrainByCommand else DEFAULT_MAX_TRAIN_BY_COMMAND

    private val trainDelta: Double
        get() = if (env.trainDelta > 0) env.trainDelta else DEFAULT_TRAIN_DELTA

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            NotWanderingNation(),
            OccupiedCity(),
            ReqGeneralCrew(),
            ReqGeneralTrainMargin(maxTrain)
        )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val leadership = general.leadership.toInt()
        val crew = if (general.ships > 0) general.ships else 1
        val currentTrain = general.training.toInt()

        // Legacy: clamp(round(leadership * 100 / crew * trainDelta), 0, maxTrain - currentTrain)
        val rawScore = (leadership * 100.0 / crew) * trainDelta
        val maxPossible = maxOf(0, maxTrain - currentTrain)
        val score = minOf(maxOf(rawScore.roundToInt(), 0), maxPossible)

        // Legacy: atmos side effect = atmos * atmosSideEffectByTraining (default 0.9)
        val sideEffectRate = if (env.atmosSideEffectByTraining > 0) env.atmosSideEffectByTraining else ATMOS_SIDE_EFFECT_RATE
        val atmosAfter = maxOf(0, (general.morale * sideEffectRate).toInt())
        val atmosDelta = atmosAfter - general.morale.toInt()

        pushLog("훈련치가 <C>${score}</> 상승했습니다. <1>$date</>")
        pushHistoryLog("훈련치가 <C>${score}</> 상승했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 훈련을 실시했습니다.")

        val exp = 100
        val ded = 70

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"train":$score,"atmos":$atmosDelta,"experience":$exp,"dedication":$ded,"leadershipExp":1},"dexChanges":{"crewType":${general.shipClass},"amount":$score}}"""
        )
    }
}
