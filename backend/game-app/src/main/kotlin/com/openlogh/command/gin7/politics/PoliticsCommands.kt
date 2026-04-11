package com.openlogh.command.gin7.politics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

// ============================================================
// 정치커맨드 12종 (Politics Commands, PCP)
// cpCost=320 (통치목표만 cpCost=80), waitTime=0, duration=0
// gin7 manual appendix p69-78 — see data/commands.json for authoritative values.
// Only GovernanceGoalCommand currently overrides getCommandPointCost() — the
// broader CP system rebalance is tracked as phase v2.5-01.
// ============================================================

/**
 * 야회 (Banquet): 진영 지지도 보너스 증가
 */
class BanquetCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "야회"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val current = (nation.meta["approvalBonus"] as? Int) ?: 0
        nation.meta["approvalBonus"] = current + 5
        pushLog("야회를 개최했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 수렵 (Hunt): 장교 사기 증가
 */
class HuntCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "수렵"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.morale = minOf(100, general.morale + 10).toShort()
        pushLog("수렵을 통해 사기가 올랐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 회담 (Conference): 대상 진영과 외교 대화 상태로 전환
 */
class ConferenceCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "회담"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destFaction = destFaction ?: return CommandResult.fail("대상 진영이 없습니다.")
        destFaction.meta["diplomacyStatus"] = "TALK"
        pushLog("${destFaction.name}과(와) 회담을 개최했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 담화 (Address): 현재 행성 지지도 소폭 증가
 */
class AddressCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "담화"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val city = city ?: return CommandResult.fail("현재 위치한 행성이 없습니다.")
        city.approval = minOf(100f, city.approval + 3f)
        pushLog("담화를 발표하여 ${city.name}의 지지도가 높아졌다.")
        return CommandResult.success(logs)
    }
}

/**
 * 연설 (Speech): 행성 지지도 증가 + 진영 연설 보너스 기록
 */
class SpeechCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "연설"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val city = city ?: return CommandResult.fail("현재 위치한 행성이 없습니다.")
        city.approval = minOf(100f, city.approval + 5f)
        nation?.meta?.set("speechBonus", true)
        pushLog("연설을 통해 ${city.name}의 지지도가 크게 높아졌다.")
        return CommandResult.success(logs)
    }
}

/**
 * 국가목표 (National Goal): 진영 메타에 목표값 저장
 */
class NationalGoalCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "국가목표"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val goal = arg?.get("goal") as? String ?: "승리"
        nation.meta["goal"] = goal
        pushLog("국가 목표를 '${goal}'(으)로 설정했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 납입률변경 (Tax Rate Change): 진영 세율 변경
 */
class TaxRateChangeCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "납입률변경"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val rate = (arg?.get("taxRate") as? Number)?.toInt()
            ?: return CommandResult.fail("세율 미지정")
        if (rate !in 0..100) return CommandResult.fail("세율은 0~100 사이여야 합니다")
        nation.taxRate = rate.toShort()
        pushLog("납입률을 ${rate}%로 변경했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 관세율변경 (Tariff Rate Change): 진영 관세율 메타 변경
 */
class TariffRateChangeCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "관세율변경"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val rate = (arg?.get("tariffRate") as? Number)?.toInt()
            ?: return CommandResult.fail("관세율 미지정")
        if (rate !in 0..100) return CommandResult.fail("관세율은 0~100 사이여야 합니다")
        nation.meta["tariffRate"] = rate
        pushLog("관세율을 ${rate}%로 변경했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 분배 (Distribution): 자금을 대상 진영에 이전
 */
class DistributionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "분배"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val destFaction = destFaction ?: return CommandResult.fail("대상 진영이 없습니다.")
        val amount = (arg?.get("funds") as? Number)?.toInt()
            ?: return CommandResult.fail("분배 금액이 지정되지 않았습니다.")
        if (amount <= 0) return CommandResult.fail("분배 금액은 0보다 커야 합니다.")
        if (nation.funds < amount) return CommandResult.fail("자금이 부족합니다.")
        nation.funds -= amount
        destFaction.funds += amount
        pushLog("${destFaction.name}에 ${amount}의 자금을 분배했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 처단 (Execution): 대상 장교를 처단하고 진영에서 제거
 */
class ExecutionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "처단"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destOfficer = destOfficer ?: return CommandResult.fail("대상 장교가 없습니다.")
        destOfficer.meta["executed"] = true
        destOfficer.factionId = 0
        pushLog("${destOfficer.name}을(를) 처단했다.")
        pushGlobalHistoryLog("${general.name}이(가) ${destOfficer.name}을(를) 처단했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 외교 (Diplomacy): 대상 진영에 외교 행동 기록
 */
class DiplomacyCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "외교"

    override fun getCost(): CommandCost = CommandCost()
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val nation = nation ?: return CommandResult.fail("소속 진영이 없습니다.")
        val destFaction = destFaction ?: return CommandResult.fail("대상 진영이 없습니다.")
        val action = arg?.get("action") as? String ?: "교섭"
        nation.meta["diplomacyAction_${destFaction.id}"] = action
        pushLog("${destFaction.name}에 '${action}' 외교를 실시했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 통치목표 (Governance Goal): 행성 통치 목표 설정 (cpCost=80).
 * gin7 manual p72 — only Politics command with a non-default CP cost.
 * Gap analysis B1.
 */
class GovernanceGoalCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "통치목표"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPointCost(): Int = 80
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val city = city ?: return CommandResult.fail("현재 위치한 행성이 없습니다.")
        val goal = arg?.get("goal") as? String ?: "발전"
        city.meta["governanceGoal"] = goal
        pushLog("${city.name}의 통치 목표를 '${goal}'(으)로 설정했다.")
        return CommandResult.success(logs)
    }
}
