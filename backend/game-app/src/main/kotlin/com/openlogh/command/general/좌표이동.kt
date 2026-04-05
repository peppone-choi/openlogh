package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.NotBeNeutral
import com.openlogh.command.constraint.ReqGeneralCrew
import com.openlogh.entity.Officer
import kotlin.random.Random

class 좌표이동(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "좌표이동"

    override val fullConditionConstraints get() = listOf(NotBeNeutral(), ReqGeneralCrew())

    override fun getCost() = CommandCost(funds = 0, supplies = 30)

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
