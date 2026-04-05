package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class NPC능동(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "NPC능동"

    override val fullConditionConstraints = listOf(
        MustBeNPC(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val optionText = arg?.get("optionText") as? String
            ?: return CommandResult(success = false, logs = listOf("인자가 없습니다."))

        if (optionText == "순간이동") {
            val destCityId = (arg?.get("destCityId") ?: arg?.get("destCityID"))
                ?: return CommandResult(success = false, logs = listOf("목적지가 없습니다."))

            pushLog("NPC 전용 명령을 이용해 도시#${destCityId}로 이동했습니다.")
            pushHistoryLog("NPC 전용 명령을 이용해 도시#${destCityId}로 이동했습니다.")
            pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} NPC 명령으로 이동했습니다.")

            return CommandResult(
                success = true,
                logs = logs,
                message = """{"statChanges":{"city":$destCityId},"npcAction":"teleport","destCityID":$destCityId}"""
            )
        }

        return CommandResult(success = false, logs = listOf("알 수 없는 NPC 명령입니다."))
    }
}
