package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.ShipClassType
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 재편성 (Reorganization) -- gin7: 兵站 - 再編成
 *
 * Move units between fleet active roster and fleet warehouse (at planet/fortress).
 * Only fleet commander can execute.
 * Multiple unit types movable simultaneously.
 * Each ship unit needs matching crew units.
 *
 * Blocked during 할당 execution.
 */
class 재편성(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "재편성"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        // Direction: true = fleet -> warehouse, false = warehouse -> fleet
        val toWarehouse = (arg?.get("toWarehouse") as? Boolean) ?: false

        val fleetId = general.fleetId
        if (fleetId <= 0) {
            return CommandResult(false, listOf("<R>재편성</> 실패 - 소속 부대가 없습니다."))
        }

        // Parse multiple ship class transfers
        val transfers = mutableMapOf<ShipClassType, Int>()
        for (sc in ShipClassType.entries) {
            val amount = (arg?.get(sc.warehouseColumn) as? Number)?.toInt() ?: 0
            if (amount > 0) transfers[sc] = amount
        }

        if (transfers.isEmpty()) {
            return CommandResult(false, listOf("<R>재편성</> 실패 - 이동할 유닛을 지정해야 합니다."))
        }

        // Build the result message for CommandResultApplicator
        val reorgDetail = buildString {
            append("""{"reorganization":{""")
            append(""""sessionId":${env.sessionId}""")
            append(""","fleetId":$fleetId""")
            append(""","toWarehouse":$toWarehouse""")
            append(""","transfers":{""")
            append(transfers.entries.joinToString(",") { """"${it.key.warehouseColumn}":${it.value}""" })
            append("}")
            append("""},"statChanges":{"experience":30,"commandExp":1}}""")
        }

        val direction = if (toWarehouse) "부대에서 창고로" else "창고에서 부대로"
        val summary = transfers.entries.joinToString(", ") { "${it.key.displayName} ${it.value}유닛" }

        pushLog("$direction $summary 재편성했습니다. <1>$date</>")
        pushHistoryLog("$direction $summary 재편성. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = reorgDetail,
        )
    }
}
