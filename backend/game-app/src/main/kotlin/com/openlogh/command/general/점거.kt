package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 점거 (Occupation/Seizure) -- gin7: 作戦 - 占領
 *
 * Occupy an undefended or weakly defended planet.
 * Changes planet ownership on success.
 * Requires fleet presence and target must have weak/no garrison.
 */
class 점거(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "점거"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            return listOf(
                NotOpeningPart(relYear),
                NotBeNeutral(),
                ReqGeneralCrew(),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            return listOf(
                NotOpeningPart(relYear + 2),
                NotBeNeutral(),
                ReqGeneralCrew(),
            )
        }

    override fun getCost(): CommandCost {
        val supplies = (general.ships / 100.0).roundToInt().coerceAtLeast(1)
        return CommandCost(funds = 0, supplies = supplies)
    }

    override fun getCommandPointCost() = 2
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 2
    override fun getDuration() = 900

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityId = destPlanet?.id ?: 0L
        val destCityName = destPlanet?.name ?: "알 수 없음"

        if (destCityId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>점거</> 실패 - 목표를 지정해야 합니다.")
            )
        }

        val dp = destPlanet ?: return CommandResult(
            success = false,
            logs = listOf("<R>점거</> 실패 - 목표 행성 정보를 불러올 수 없습니다.")
        )

        // Cannot occupy own faction's planet
        if (dp.factionId == general.factionId && dp.factionId != 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>점거</> 실패 - 자국 행성은 점거할 수 없습니다.")
            )
        }

        // Check garrison strength
        val defenderCount = getDestCityGeneralCount()
        val defenderCrew = getDestCityTotalCrew()
        val orbitalDefense = dp.orbitalDefense
        val fortress = dp.fortress

        // If strong defense, occupation fails
        val totalDefense = defenderCrew + orbitalDefense * 10 + fortress * 20
        val attackPower = general.ships + (general.command * 10)

        if (totalDefense > attackPower) {
            val josaUl = pickJosa(destCityName, "을")
            pushLog("<R>점거 실패</> - <G><b>${destCityName}</b></>${josaUl} 점거하지 못했습니다. 방어가 너무 강력합니다. <1>$date</>")
            pushHistoryLog("${destCityName} 점거 실패 (방어력 우세). <1>$date</>")
            val cost = getCost()
            return CommandResult(
                success = true,
                logs = logs,
                message = """{"statChanges":{"rice":${-cost.supplies},"experience":50,"commandExp":1},"meritPoints":2}"""
            )
        }

        // Occupation success
        val cost = getCost()
        val previousFactionId = dp.factionId

        val josaUl = pickJosa(destCityName, "을")
        pushLog("<S><G><b>${destCityName}</b></>${josaUl} 점거했습니다!</> <1>$date</>")
        pushHistoryLog("${destCityName} 점거 성공. <1>$date</>")
        pushGlobalActionLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>${josaUl} 점거했습니다.")
        pushNationalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${destCityName}${josaUl} 점거했습니다.")

        if (previousFactionId > 0L) {
            pushDestNationalHistoryLogFor(previousFactionId, "<G><b>${destCityName}</b></>${pickJosa(destCityName, "이")} 적에게 점거되었습니다.")
        }

        val exp = 200
        val meritGain = 20

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"rice":${-cost.supplies},"experience":$exp,"commandExp":1,"leadershipExp":1}""")
                append(""","meritPoints":$meritGain""")
                append(""","cityChanges":{"claimCity":true}""")
                append(""","occupationResult":{"targetCityId":$destCityId,"previousFactionId":$previousFactionId}""")
                append("}")
            }
        )
    }
}
