package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.round
import kotlin.random.Random

private const val DEFAULT_MAX_TRAIN_BY_COMMAND = 100
private const val DEFAULT_MAX_ATMOS_BY_COMMAND = 100

class CR맹훈련(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "맹훈련"

    private val maxTrain: Int
        get() = if (env.maxTrainByCommand > 0) env.maxTrainByCommand else DEFAULT_MAX_TRAIN_BY_COMMAND

    private val maxAtmos: Int
        get() = if (env.maxAtmosByCommand > 0) env.maxAtmosByCommand else DEFAULT_MAX_ATMOS_BY_COMMAND

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            NotWanderingNation(),
            OccupiedCity(),
            ReqGeneralCrew(),
            ReqGeneralTrainMargin(maxTrain),
        )

    override val minConditionConstraints = listOf(
        NotBeNeutral(),
        NotWanderingNation(),
        OccupiedCity(),
    )

    override fun getCost() = CommandCost(supplies = 500)
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val crew = if (general.ships > 0) general.ships else 1
        val leadership = general.leadership.toInt()
        val trainDelta = if (env.trainDelta > 0) env.trainDelta else 0.05
        // Legacy: round(leadership * 100 / crew * trainDelta * 2 / 3)
        val rawScore = round((leadership * 100.0 / crew) * trainDelta * 2.0 / 3.0).toInt()
        val currentTrain = general.training.toInt()
        val currentAtmos = general.morale.toInt()
        val trainGain = minOf(rawScore, maxOf(0, maxTrain - currentTrain))
        val atmosGain = minOf(rawScore, maxOf(0, maxAtmos - currentAtmos))
        val cost = getCost()

        pushLog("훈련, 사기치가 <C>${rawScore}</> 상승했습니다. <1>$date</>")
        pushHistoryLog("훈련, 사기치가 <C>${rawScore}</> 상승했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 맹훈련을 실시했습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"rice":${-cost.supplies},"train":$trainGain,"atmos":$atmosGain,"experience":150,"dedication":100,"leadershipExp":1},"dexChanges":{"crewType":${general.shipClass},"amount":${rawScore * 2}}}"""
        )
    }
}
