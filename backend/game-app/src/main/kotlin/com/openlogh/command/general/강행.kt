package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.math.max
import kotlin.random.Random

private const val STAT_DECREASE = 5
private const val MIN_STAT = 20

class 강행(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "강행"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotSameDestCity(),
                NearCity(3),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override fun getCost() = CommandCost(funds = env.develCost * 5, supplies = 0)

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityName = destPlanet?.name ?: "알 수 없음"
        val destCityId = destPlanet?.id ?: 0L
        val josaRo = JosaUtil.pick(destCityName, "로")

        pushLog("<G><b>${destCityName}</b></>${josaRo} 강행했습니다. <1>$date</>")
        pushHistoryLog("<G><b>${destCityName}</b></>${josaRo} 강행했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>${josaRo} 강행했습니다.")

        val exp = 100
        val cost = getCost()
        val newTrain = max(MIN_STAT, general.training.toInt() - STAT_DECREASE)
        val newAtmos = max(MIN_STAT, general.morale.toInt() - STAT_DECREASE)
        val trainDelta = newTrain - general.training.toInt()
        val atmosDelta = newAtmos - general.morale.toInt()

        // Legacy parity: 방랑군(nation.factionRank==0) 군주(officerLevel==20)가 강행하면
        // 같은 세력의 모든 장수를 함께 이동시킨다.
        val isWanderingLord = general.officerLevel.toInt() == 20 && nation?.factionRank?.toInt() == 0
        val wanderingJson = if (isWanderingLord) {
            ""","wanderingNationMove":{"destCityId":"$destCityId","nationId":"${general.factionId}","destCityName":"$destCityName","logMessage":"방랑군 세력이 <G><b>${destCityName}</b></>${josaRo} 강행했습니다."}"""
        } else ""

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"cityId":"$destCityId","gold":${-cost.funds},"train":$trainDelta,"atmos":$atmosDelta,"experience":$exp,"leadershipExp":1},"tryUniqueLottery":true$wanderingJson}"""
        )
    }
}
