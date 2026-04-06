package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 재편성 — 함대 진형을 변경한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 *
 * 유효 진형: WEDGE(紡錘), BY_CLASS(艦種), MIXED(混成), THREE_COLUMN(三列)
 */
class ReorganizeCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "재편성"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    companion object {
        val VALID_FORMATIONS = setOf("WEDGE", "BY_CLASS", "MIXED", "THREE_COLUMN")
    }

    override suspend fun run(rng: Random): CommandResult {
        val requestedFormation = arg?.get("formation") as? String ?: "WEDGE"
        val formation = if (requestedFormation in VALID_FORMATIONS) requestedFormation else "WEDGE"

        val fleet = troop
        if (fleet != null) {
            fleet.meta["formation"] = formation
        } else {
            general.meta["formation"] = formation
        }

        pushLog("진형을 ${formation}으로 재편성했다.")

        return CommandResult(success = true, logs = logs)
    }
}
