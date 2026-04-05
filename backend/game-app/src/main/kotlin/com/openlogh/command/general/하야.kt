package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val DEFAULT_GOLD = 1000
private const val DEFAULT_RICE = 1000
private const val MAX_BETRAY_COUNT = 10

class 하야(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "하야"

    override val fullConditionConstraints = listOf(
        NotBeNeutral(),
        NotLord(),
    )

    override val minConditionConstraints = listOf(
        NotBeNeutral(),
        NotLord(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val currentNation = nation
        val nationName = currentNation?.name ?: "소속국"
        val generalName = general.name

        val betrayCount = general.betray.toInt()
        val penaltyRate = 0.1 * betrayCount
        val newExp = floor(general.experience * (1.0 - penaltyRate)).toInt()
        val newDed = floor(general.dedication * (1.0 - penaltyRate)).toInt()
        val expLoss = general.experience.toInt() - newExp
        val dedLoss = general.dedication.toInt() - newDed
        val goldToNation = max(0, general.funds - DEFAULT_GOLD)
        val riceToNation = max(0, general.supplies - DEFAULT_RICE)
        val newBetray = min(betrayCount + 1, MAX_BETRAY_COUNT)
        val isTroopLeader = general.fleetId == general.id

        pushLog("<D><b>${nationName}</b></>에서 하야했습니다. <1>$date</>")
        pushHistoryLog("<D><b>${nationName}</b></>에서 하야")
        pushGlobalLog("<Y>${generalName}</>${josa(generalName, "이")} <D><b>${nationName}</b></>에서 <R>하야</>했습니다.")

        if (currentNation != null) {
            currentNation.officerCount = max(0, currentNation.officerCount - 1)
        }

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"experience":${-expLoss},"dedication":${-dedLoss}""")
                append(""","gold":${-goldToNation},"rice":${-riceToNation},"betray":${newBetray - betrayCount}}""")
                append(""","nationChanges":{"gold":$goldToNation,"rice":$riceToNation}""")
                append(""","leaveNation":true,"resetOfficer":true""")
                append(""","setPermission":"normal","setBelong":0,"setMakeLimit":12""")
                append(""","disbandTroop":$isTroopLeader""")
                append(""","inheritancePoint":{"key":"active_action","amount":1}""")
                append("}")
            }
        )
    }
}
