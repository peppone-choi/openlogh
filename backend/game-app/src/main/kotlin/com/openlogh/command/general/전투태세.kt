package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_TRAIN_BY_COMMAND = 100
private const val MAX_ATMOS_BY_COMMAND = 100
private const val PRE_REQ_TURN = 3

class 전투태세(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "전투태세"
    override val canDisplay = false

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                NotWanderingNation(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
                ReqGeneralTrainMargin(MAX_TRAIN_BY_COMMAND - 10),
                ReqGeneralAtmosMargin(MAX_ATMOS_BY_COMMAND - 10),
            )
        }

    override val minConditionConstraints: List<Constraint> = listOf(
        NotBeNeutral(),
        NotWanderingNation(),
        OccupiedCity(),
        ReqGeneralCrew(),
    )

    override fun getCost(): CommandCost {
        val crew = general.ships
        val techCost = getNationTechCost()
        val gold = (crew / 100.0 * 3 * techCost).roundToInt()
        return CommandCost(gold = gold, rice = 0)
    }

    override fun getPreReqTurn() = PRE_REQ_TURN
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val reqTurn = getPreReqTurn()
        val term = reqTurn

        // Term == reqTurn: completion
        pushLog("전투태세 완료! ($term/$reqTurn) <1>$date</>")
        pushHistoryLog("전투태세 완료! ($term/$reqTurn) <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 전투태세를 완료했습니다.")

        val exp = 100 * reqTurn
        val ded = 70 * reqTurn
        val dexGain = (general.ships / 100.0 * reqTurn).roundToInt()
        val trainTarget = MAX_TRAIN_BY_COMMAND - 5
        val atmosTarget = MAX_ATMOS_BY_COMMAND - 5
        val cost = getCost()

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"train":{"setMin":$trainTarget},"atmos":{"setMin":$atmosTarget},"experience":$exp,"dedication":$ded,"leadershipExp":$reqTurn},"dexChanges":{"crewType":${general.shipClass},"amount":$dexGain},"battleStanceTerm":$term,"completed":true}"""
        )
    }
}
