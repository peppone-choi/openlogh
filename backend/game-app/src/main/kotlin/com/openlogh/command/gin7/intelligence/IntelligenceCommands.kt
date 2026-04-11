package com.openlogh.command.gin7.intelligence

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

// ============================================================
// 첩보커맨드 14종 (Intelligence Commands, MCP)
// 모든 커맨드: override fun getCommandPoolType() = StatCategory.MCP
// ============================================================

/**
 * 일제수색 (General Search): 행성 소재 장교 전원에 수색 상태 기록 (cpCost=160)
 */
class GeneralSearchCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "일제수색"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanet = destPlanet ?: return CommandResult.fail("대상 행성이 없습니다.")
        destPlanetOfficers?.forEach { it.meta["searched"] = true }
        pushLog("${destPlanet.name}에서 일제 수색을 실시했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 체포허가 (Arrest Authorization): 대상 장교에 체포 영장 발부 (cpCost=800).
 * gin7 manual p76 — exceptionally high CP cost reflects political weight.
 * Gap analysis B2.
 */
class ArrestAuthorizationCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "체포허가"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPointCost(): Int = 800
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destOfficer = destOfficer ?: return CommandResult.fail("대상 장교가 없습니다.")
        destOfficer.meta["arrestWarrant"] = true
        pushLog("${destOfficer.name}에 대한 체포 허가를 내렸다.")
        return CommandResult.success(logs)
    }
}

/**
 * 집행명령 (Execution Order): 대상 장교에 집행 명령 부여 (cpCost=800).
 * gin7 manual p76 — mirrors ArrestAuthorization CP weight.
 * Gap analysis B3.
 */
class ExecutionOrderCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "집행명령"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPointCost(): Int = 800
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destOfficer = destOfficer ?: return CommandResult.fail("대상 장교가 없습니다.")
        destOfficer.meta["executionOrder"] = true
        pushLog("${destOfficer.name}에 대한 집행 명령을 내렸다.")
        return CommandResult.success(logs)
    }
}

/**
 * 체포명령 (Arrest Order): 대상 장교 체포 처리 (cpCost=160)
 */
class ArrestOrderCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "체포명령"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destOfficer = destOfficer ?: return CommandResult.fail("대상 장교가 없습니다.")
        destOfficer.meta["arrested"] = true
        pushLog("${destOfficer.name}에 대한 체포명령을 내렸다.")
        return CommandResult.success(logs)
    }
}

/**
 * 사열 (Inspection): 현재 행성 치안 증가 (cpCost=160)
 */
class InspectionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "사열"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val city = city ?: return CommandResult.fail("현재 위치한 행성이 없습니다.")
        city.security = minOf(100, city.security + 5)
        pushLog("${city.name}에서 사열을 실시하여 치안이 높아졌다.")
        return CommandResult.success(logs)
    }
}

/**
 * 습격 (Raid): 대상 행성 궤도방어 파괴 (cpCost=160)
 */
class RaidCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "습격"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanet = destPlanet ?: return CommandResult.fail("대상 행성이 없습니다.")
        val damage = rng.nextInt(100, 500)
        destPlanet.orbitalDefense = maxOf(0, destPlanet.orbitalDefense - damage)
        pushLog("${destPlanet.name}을(를) 습격하여 궤도방어를 ${damage}만큼 파괴했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 감시 (Surveillance): 대상 장교에 감시 상태 기록 (cpCost=160)
 */
class SurveillanceCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "감시"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destOfficer = destOfficer ?: return CommandResult.fail("대상 장교가 없습니다.")
        destOfficer.meta["underSurveillance"] = true
        pushLog("${destOfficer.name}에 대한 감시를 시작했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 잠입공작 (Infiltration Op): 자신의 잠입 행성 기록 (cpCost=160)
 */
class InfiltrationOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "잠입공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanet = destPlanet ?: return CommandResult.fail("대상 행성이 없습니다.")
        general.meta["infiltratedPlanet"] = destPlanet.id
        pushLog("${destPlanet.name}에 잠입 공작을 개시했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 탈출공작 (Escape Op): 잠입 상태 해제 및 원래 행성으로 복귀 (cpCost=160)
 */
class EscapeOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "탈출공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta.remove("infiltratedPlanet")
        general.planetId = city?.id ?: general.planetId
        pushLog("탈출 공작을 완료했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 정보공작 (Intelligence Op): 대상 진영 기밀 정보 수집 (cpCost=160)
 */
class IntelligenceOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "정보공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destFaction = destFaction ?: return CommandResult.fail("대상 진영이 없습니다.")
        val intel = mapOf("funds" to destFaction.funds, "techLevel" to destFaction.techLevel)
        general.meta["intelOn_${destFaction.id}"] = intel
        pushLog("${destFaction.name}에 대한 정보 공작을 완료했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 파괴공작 (Sabotage Op): 대상 행성 생산력 감소 (cpCost=160)
 */
class SabotageOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "파괴공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanet = destPlanet ?: return CommandResult.fail("대상 행성이 없습니다.")
        val damage = rng.nextInt(1, 5)
        destPlanet.production = maxOf(0, destPlanet.production - damage)
        pushLog("${destPlanet.name}에 파괴 공작을 실시하여 생산력을 ${damage}만큼 감소시켰다.")
        return CommandResult.success(logs)
    }
}

/**
 * 선동공작 (Agitation Op): 대상 행성 지지도 감소 (cpCost=160)
 */
class AgitationOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "선동공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanet = destPlanet ?: return CommandResult.fail("대상 행성이 없습니다.")
        val reduction = rng.nextInt(5, 20)
        destPlanet.approval = maxOf(0f, destPlanet.approval - reduction.toFloat())
        pushLog("${destPlanet.name}에 선동 공작을 실시하여 지지도를 ${reduction}만큼 감소시켰다.")
        return CommandResult.success(logs)
    }
}

/**
 * 침입공작 (Incursion Op): 대상 진영 침투 기록 (cpCost=320)
 */
class IncursionOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "침입공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destFaction = destFaction ?: return CommandResult.fail("대상 진영이 없습니다.")
        general.meta["incursionTarget"] = destFaction.id
        pushLog("${destFaction.name}에 침입 공작을 개시했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 귀환공작 (Return Op): 침입 상태 해제 및 복귀 (cpCost=320)
 */
class ReturnOpCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "귀환공작"

    override fun getCost(): CommandCost = CommandCost()
    override fun getCommandPoolType(): StatCategory = StatCategory.MCP
    override fun getPreReqTurn(): Int = 0
    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta.remove("incursionTarget")
        pushLog("귀환 공작을 완료했다.")
        return CommandResult.success(logs)
    }
}
