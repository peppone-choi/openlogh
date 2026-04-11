package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * Phase 24-31 (gaps C14/C15, gin7 매뉴얼 p52 육전대 운용):
 *
 * 원작 매뉴얼은 육전대 출격/철수를 단순 플래그가 아니라 "출격 상태 전환 +
 * 현재 stance 검증 + 소속 함대 보유 확인" 으로 정의한다. v2.4 까지는
 * `general.meta["groundForceStance"]` 를 무조건 덮어쓰기만 하고 아래 케이스를
 * 검증하지 않았다:
 *   · 이미 목표 상태면 no-op 로 조기 종료 (CP 낭비 방지)
 *   · 함대가 없는(단독) 장교의 출격 시도 거부
 *   · 출격/철수 횟수 카운터로 감사 추적 가능하게 축적
 *
 * 이 phase 는 두 커맨드를 공통 베이스로 묶어 위 규칙을 엔진 레벨에서 강제한다.
 * CP 비용은 CommandCostTable 에서 관리되므로 기존 pre-/post- 요건/풀은 유지.
 */
const val GROUND_FORCE_STANCE_DEPLOYED: String = "DEPLOYED"
const val GROUND_FORCE_STANCE_WITHDRAWN: String = "WITHDRAWN"

private fun currentStance(general: Officer): String =
    (general.meta["groundForceStance"] as? String) ?: GROUND_FORCE_STANCE_WITHDRAWN

private fun bumpSortieCounter(general: Officer, key: String) {
    val prev = (general.meta[key] as? Number)?.toInt() ?: 0
    general.meta[key] = prev + 1
}

/**
 * 육전대출격 — 탑승 중인 육전대를 출격 상태로 전환한다.
 * MCP 커맨드.
 */
class GroundForceDeployCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전대출격"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        if (troop == null) {
            return CommandResult.fail("함대가 없는 장교는 육전대를 출격시킬 수 없다.")
        }

        val stance = currentStance(general)
        if (stance == GROUND_FORCE_STANCE_DEPLOYED) {
            pushLog("${general.name}의 육전대는 이미 출격 상태이다.")
            return CommandResult(success = true, logs = logs)
        }

        general.meta["groundForceStance"] = GROUND_FORCE_STANCE_DEPLOYED
        bumpSortieCounter(general, "groundForceDeployCount")
        pushLog("${general.name}이(가) 육전대를 출격시켰다.")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 육전대철수 — 출격 중인 육전대를 함대로 복귀시킨다.
 * MCP 커맨드.
 */
class GroundForceWithdrawCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전대철수"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val stance = currentStance(general)
        if (stance == GROUND_FORCE_STANCE_WITHDRAWN) {
            pushLog("${general.name}의 육전대는 이미 철수 상태이다.")
            return CommandResult(success = true, logs = logs)
        }

        general.meta["groundForceStance"] = GROUND_FORCE_STANCE_WITHDRAWN
        bumpSortieCounter(general, "groundForceWithdrawCount")
        pushLog("${general.name}이(가) 육전대를 철수시켰다.")
        return CommandResult(success = true, logs = logs)
    }
}
