package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 정찰 (Reconnaissance) -- gin7: 諜報 - 偵察
 *
 * Reveal enemy fleet positions and planet info in a target system.
 * Success probability based on intelligence stat.
 */
class 정찰(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "정찰"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            NotSameDestCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
        )

    override fun getCost() = CommandCost(funds = 50, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityId = destPlanet?.id ?: 0L
        val destCityName = destPlanet?.name ?: "알 수 없음"

        if (destCityId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>정찰</> 실패 - 목표 성계를 지정해야 합니다.")
            )
        }

        // Success probability: base 50% + intelligence/2 (cap at 95%)
        val successProb = (50.0 + general.intelligence.toDouble() / 2.0).coerceAtMost(95.0) / 100.0
        val succeeded = rng.nextDouble() < successProb

        val cost = getCost()

        if (!succeeded) {
            pushLog("<R>정찰 실패</> - <G><b>${destCityName}</b></> 방면의 정보를 수집하지 못했습니다. <1>$date</>")
            pushHistoryLog("${destCityName} 정찰 실패. <1>$date</>")
            return CommandResult(
                success = true, // Command executed but recon failed
                logs = logs,
                message = """{"statChanges":{"gold":${-cost.funds},"experience":20,"intelligenceExp":1}}"""
            )
        }

        // Gather intel about destination
        val dp = destPlanet
        val intelLines = mutableListOf<String>()
        if (dp != null) {
            intelLines.add("소속: ${if (dp.factionId == 0L) "무소속" else "진영 #${dp.factionId}"}")
            intelLines.add("인구: ${String.format("%,d", dp.population)}")
            intelLines.add("생산: ${dp.production}/${dp.productionMax}")
            intelLines.add("방어: 궤도방어 ${dp.orbitalDefense}, 요새 ${dp.fortress}")
            intelLines.add("치안: ${dp.security}")
        }

        val generalCount = getDestCityGeneralCount()
        val totalCrew = getDestCityTotalCrew()
        if (generalCount > 0) {
            intelLines.add("주둔 장교: ${generalCount}명, 함선: ${String.format("%,d", totalCrew)}척")
            val crewSummary = getDestCityCrewTypeSummary()
            if (crewSummary.isNotEmpty()) {
                intelLines.add("함종별: $crewSummary")
            }
        } else {
            intelLines.add("주둔 병력 없음")
        }

        val josaUl = pickJosa(destCityName, "을")
        pushLog("<G><b>${destCityName}</b></>${josaUl} 정찰했습니다. <1>$date</>")
        for (line in intelLines) {
            pushLog("  > $line")
        }
        pushHistoryLog("${destCityName} 정찰 성공. <1>$date</>")

        val exp = 60 + (general.intelligence / 5)
        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"gold":${-cost.funds},"experience":$exp,"intelligenceExp":1}""")
                append(""","reconResult":{"targetCityId":$destCityId,"generalCount":$generalCount,"totalCrew":$totalCrew}""")
                append("}")
            }
        )
    }
}
