package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 임명 커맨드 — PCP 160 소모, destOfficer.positionCards에 positionCard 추가
 */
class AppointCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "임명"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val positionCard = arg?.get("positionCard") as? String
            ?: return CommandResult.fail("직위 카드 미지정")

        if (!target.positionCards.contains(positionCard)) {
            target.positionCards.add(positionCard)
        }

        pushLog("${target.name}이(가) ${positionCard} 직위에 임명됐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 파면 커맨드 — PCP 160 소모, destOfficer.positionCards에서 positionCard 제거
 */
class DismissCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "파면"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val positionCard = arg?.get("positionCard") as? String
            ?: return CommandResult.fail("직위 카드 미지정")

        target.positionCards.remove(positionCard)

        pushLog("${target.name}이(가) ${positionCard} 직위에서 파면됐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 사임 커맨드 — PCP 80 소모, general.positionCards에서 positionCard 제거 (자진 사임)
 */
class ResignCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "사임"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val positionCard = arg?.get("positionCard") as? String
            ?: return CommandResult.fail("직위 카드 미지정")

        general.positionCards.remove(positionCard)

        pushLog("${general.name}이(가) ${positionCard} 직위에서 사임했다.")
        return CommandResult.success(logs)
    }
}
