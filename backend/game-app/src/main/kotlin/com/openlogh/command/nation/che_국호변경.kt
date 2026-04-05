package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class che_국호변경(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "국호변경"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(), SuppliedCity()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val newName = (arg?.get("factionName") as? String)
            ?: return CommandResult(false, logs, "국호가 지정되지 않았습니다")
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")

        if (newName.isBlank() || newName.length > 8) {
            return CommandResult(false, logs, "유효하지 않은 국호입니다")
        }

        // Check duplicate nation name
        val existingNation = services?.factionRepository?.findBySessionIdAndName(env.sessionId, newName)
        if (existingNation != null) {
            pushLog("이미 같은 국호를 가진 곳이 있습니다. 국호변경 실패 <1>$date</>")
            return CommandResult(false, logs, "이미 같은 국호를 가진 곳이 있습니다")
        }

        val newAbbr = (arg?.get("abbreviation") as? String)?.take(2)?.ifBlank { null }
            ?: newName.take(1)
        n.name = newName
        n.abbreviation = newAbbr
        n.meta["can_국호변경"] = 0

        general.experience += 5
        general.dedication += 5

        pushLog("국호를 <D><b>$newName</b></>으로 변경합니다. <1>$date</>")
        pushHistoryLog("국호를 <D><b>$newName</b></>으로 변경합니다. <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 국호를 <D><b>$newName</b></>으로 변경했습니다.")
        pushGlobalHistoryLog("<D><b>${n.name}</b></>의 국호가 <D><b>$newName</b></>으로 변경되었습니다.")
        return CommandResult(true, logs)
    }
}
