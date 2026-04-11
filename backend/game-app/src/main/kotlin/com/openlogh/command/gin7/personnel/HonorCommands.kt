package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 서작 커맨드 — PCP 160 소모, destOfficer.meta["title"] 설정
 */
class GrantTitleCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "서작"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val title = arg?.get("title") as? String ?: "귀족"

        target.meta["title"] = title

        pushLog("${target.name}에게 ${title} 작위가 수여됐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 서훈 커맨드 (gin7 manual p35, Phase 24-09 A3).
 *
 * PCP 160 consumed. Increments `destOfficer.medalRank` / `medalCount` so the
 * rank ladder's 제3법칙 (medal tiebreaker) is actually populated.
 *
 * Accepted args:
 *   - decoration (String, optional): display name for the log line
 *   - medalRank (Int, optional): rank of this medal (default = one above
 *     the recipient's current medalRank, clamped to `Short.MAX_VALUE`)
 *
 * Effects:
 *   - destOfficer.medalCount += 1
 *   - destOfficer.medalRank = max(destOfficer.medalRank, requestedRank)
 *   - destOfficer.meta["decoration"] set to the display name for legacy readers.
 */
class AwardDecorationCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "서훈"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPointCost(): Int = 160

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val decoration = arg?.get("decoration") as? String ?: "훈장"
        val requestedRank = (arg?.get("medalRank") as? Number)?.toInt()
            ?: (target.medalRank.toInt() + 1)

        val newMedalCount = (target.medalCount.toInt() + 1).coerceAtMost(Short.MAX_VALUE.toInt())
        val newMedalRank = maxOf(target.medalRank.toInt(), requestedRank)
            .coerceIn(0, Short.MAX_VALUE.toInt())

        target.medalCount = newMedalCount.toShort()
        target.medalRank = newMedalRank.toShort()
        target.meta["decoration"] = decoration

        pushLog("${target.name}에게 ${decoration}이(가) 수여됐다. (등급 $newMedalRank, 총 ${newMedalCount}회)")
        return CommandResult.success(logs)
    }
}
