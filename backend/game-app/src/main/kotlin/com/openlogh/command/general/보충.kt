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
 * 보충 (Replenishment) -- gin7: 兵站 - 補充
 *
 * Replenish damaged fleet units and crew from fleet warehouse.
 * Only fleet commander can execute.
 *
 * Rules:
 * - Must use exact same ship subtype (e.g., Cruiser I needs Cruiser I in warehouse)
 * - When replenishing ships, crew must also be replenished
 * - Crew replenishment is automatic based on ship crew efficiency ratio
 * - If no crew units in warehouse, all fleet units become non-functional
 *
 * Blocked during 할당 execution.
 */
class 보충(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "보충"

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
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        val fleetId = general.fleetId
        if (fleetId <= 0) {
            return CommandResult(false, listOf("<R>보충</> 실패 - 소속 부대가 없습니다."))
        }

        // Parse ship class to replenish
        val shipClassCode = (arg?.get("shipClass") as? Number)?.toInt()
            ?: (arg?.get("shipClassCode") as? Number)?.toInt()
        val shipClass = if (shipClassCode != null) {
            ShipClassType.fromCode(shipClassCode)
                ?: return CommandResult(false, listOf("<R>보충</> 실패 - 잘못된 함종 코드입니다."))
        } else {
            // Default to current officer's ship class
            ShipClassType.fromCode(general.shipClass.toInt())
                ?: ShipClassType.BATTLESHIP
        }

        val amount = (arg?.get("amount") as? Number)?.toInt()
            ?: return CommandResult(false, listOf("<R>보충</> 실패 - 보충할 수량을 지정해야 합니다."))

        if (amount <= 0) {
            return CommandResult(false, listOf("<R>보충</> 실패 - 보충할 수량은 1 이상이어야 합니다."))
        }

        // Build the result message for CommandResultApplicator
        val replenishDetail = buildString {
            append("""{"replenishment":{""")
            append(""""sessionId":${env.sessionId}""")
            append(""","fleetId":$fleetId""")
            append(""","shipClass":"${shipClass.name}"""")
            append(""","shipClassCode":${shipClass.code}""")
            append(""","amount":$amount""")
            append("""},"statChanges":{"experience":30,"commandExp":1}}""")
        }

        pushLog("${shipClass.displayName} ${amount}유닛을 부대 창고에서 보충했습니다. <1>$date</>")
        pushHistoryLog("${shipClass.displayName} ${amount}유닛 보충. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = replenishDetail,
        )
    }
}
