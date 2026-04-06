package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val FLAGSHIP_DEFAULT_PRICE = 5000

/**
 * 기함구매 커맨드 — PCP 80 소모, flagshipCode 변경 (가격 5000, Phase 7 밸런싱 예정)
 */
class FlagshipPurchaseCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "기함구매"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val flagshipCode = arg?.get("flagshipCode") as? String
            ?: return CommandResult.fail("기함 코드 미지정")

        if (general.funds < FLAGSHIP_DEFAULT_PRICE) {
            return CommandResult.fail("자금 부족 (보유: ${general.funds}, 필요: $FLAGSHIP_DEFAULT_PRICE)")
        }

        general.funds -= FLAGSHIP_DEFAULT_PRICE
        general.flagshipCode = flagshipCode

        pushLog("${general.name}이(가) ${flagshipCode} 기함을 구매했다.")
        return CommandResult.success(logs)
    }
}
