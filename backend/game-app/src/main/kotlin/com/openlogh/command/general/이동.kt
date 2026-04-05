package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.max
import kotlin.random.Random

private const val ATMOS_DECREASE_ON_MOVE = 5
private const val MIN_ATMOS = 20

class 이동(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "이동"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotSameDestCity(),
                NearCity(1),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override fun getCost() = CommandCost(gold = env.develCost, rice = 0)

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityName = destPlanet?.name ?: "알 수 없음"
        val destCityId = destPlanet?.id ?: 0L

        // Legacy PHP uses JosaUtil for 로/으로
        pushLog("<G><b>${destCityName}</b></>(으)로 이동했습니다. <1>$date</>")
        pushHistoryLog("<G><b>${destCityName}</b></>(으)로 이동했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>(으)로 이동했습니다.")

        val exp = 50
        val cost = getCost()
        val newAtmos = max(MIN_ATMOS, general.morale.toInt() - ATMOS_DECREASE_ON_MOVE)
        val atmosDelta = newAtmos - general.morale.toInt()

        // Legacy PHP: if officer_level==20 and nation.factionRank==0 (roaming), move all nation generals
        val isRoamingLeader = general.officerLevel.toInt() == 20 && (nation?.level?.toInt() ?: 1) == 0
        val roamingMoveJson = if (isRoamingLeader) {
            ""","roamingMove":{"nationId":${nation?.id ?: 0},"destCityId":"$destCityId","destCityName":"$destCityName"}"""
        } else ""

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"cityId":"$destCityId","gold":${-cost.funds},"atmos":$atmosDelta,"experience":$exp,"leadershipExp":1},"tryUniqueLottery":true$roamingMoveJson}"""
        )
    }
}
