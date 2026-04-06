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
 * 통신방해 (Communication Jamming) -- gin7: 諜報 - 通信妨害
 *
 * Disrupts enemy communications in target system.
 * Reduces enemy command point recovery in affected area temporarily.
 */
class 통신방해(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "통신 방해"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            NotSameDestCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
        )

    override fun getCost() = CommandCost(funds = 100, supplies = 0)
    override fun getCommandPointCost() = 2
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 2
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val destCityId = destPlanet?.id ?: 0L
        val destCityName = destPlanet?.name ?: "알 수 없음"

        if (destCityId <= 0L) {
            return CommandResult(
                success = false,
                logs = listOf("<R>통신 방해</> 실패 - 목표 성계를 지정해야 합니다.")
            )
        }

        // Success probability: base 40% + intelligence/3 (cap at 85%)
        val successProb = (40.0 + general.intelligence.toDouble() / 3.0).coerceAtMost(85.0) / 100.0
        val succeeded = rng.nextDouble() < successProb

        val cost = getCost()

        if (!succeeded) {
            pushLog("<R>통신 방해 실패</> - <G><b>${destCityName}</b></> 방면의 통신을 방해하지 못했습니다. <1>$date</>")
            pushHistoryLog("${destCityName} 통신 방해 실패. <1>$date</>")
            return CommandResult(
                success = true,
                logs = logs,
                message = """{"statChanges":{"gold":${-cost.funds},"experience":30,"intelligenceExp":1}}"""
            )
        }

        // Jamming duration: 30 realtime minutes (1800 seconds)
        val jammingDuration = 1800

        val josaUl = pickJosa(destCityName, "을")
        pushLog("<G><b>${destCityName}</b></>${josaUl} 통신 방해했습니다. (${jammingDuration / 60}분간 CP 회복 저하) <1>$date</>")
        pushHistoryLog("${destCityName} 통신 방해 성공. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></> 방면의 통신을 방해했습니다.")

        val exp = 80 + (general.intelligence / 4)
        val meritGain = 5

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"gold":${-cost.funds},"experience":$exp,"intelligenceExp":1}""")
                append(""","meritPoints":$meritGain""")
                append(""","commJamming":{"targetCityId":$destCityId,"durationSeconds":$jammingDuration}""")
                append("}")
            }
        )
    }
}
