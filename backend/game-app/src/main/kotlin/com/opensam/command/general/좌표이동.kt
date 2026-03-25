package com.opensam.command.general

import com.opensam.command.CommandCost
import com.opensam.command.CommandEnv
import com.opensam.command.CommandResult
import com.opensam.command.GeneralCommand
import com.opensam.command.constraint.NotBeNeutral
import com.opensam.command.constraint.ReqGeneralCrew
import com.opensam.entity.General
import kotlin.random.Random

class 좌표이동(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "좌표이동"

    override val fullConditionConstraints get() = listOf(NotBeNeutral(), ReqGeneralCrew())

    override fun getCost() = CommandCost(gold = 0, rice = 30)

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val destX = (arg?.get("destX") as? Number)?.toFloat()
        val destY = (arg?.get("destY") as? Number)?.toFloat()
        if (destX == null || destY == null) return CommandResult.fail("목표 좌표를 지정해주세요.")
        if (destX < 0 || destX > 700 || destY < 0 || destY > 500) return CommandResult.fail("유효하지 않은 좌표입니다.")

        general.destX = destX
        general.destY = destY
        pushLog("<C>${general.name}</>${josa(general.name, "이가")} (${destX.toInt()}, ${destY.toInt()}) 방면으로 이동합니다.")
        return CommandResult.success(logs)
    }
}
