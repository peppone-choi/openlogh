@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.GridEntryValidator
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val opsMapper = jacksonObjectMapper()

// ========== 연료보급 (Fuel Replenishment) - MCP 80 ==========

class che_연료보급(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "연료보급"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val riceCost = max(1, general.ships / 200)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to -riceCost, "experience" to 20),
        ))
        return CommandResult(true, listOf("${formatDate()} 연료를 보급했습니다."), message = msg)
    }
}

// ========== 기본훈련 (Basic Training) - MCP 160 ==========

class che_기본훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "기본훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.training >= 100.toShort()) return ConstraintResult.Fail("훈련도가 이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.05).toInt())
        val trainDelta = min(100 - general.training.toInt(), rawDelta)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("training" to trainDelta, "experience" to 50, "leadershipExp" to 1),
        ))
        return CommandResult(true, listOf("${formatDate()} 기본 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 특수훈련 (Special Training) - MCP 320 ==========

class che_특수훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "특수훈련"

    override fun getCost(): CommandCost = CommandCost(supplies = max(1, general.ships / 100))

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.training >= 100.toShort()) return ConstraintResult.Fail("훈련도가 이미 최대치입니다.")
        if (general.supplies < getCost().supplies) return ConstraintResult.Fail("물자가 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.1).toInt())
        val trainDelta = min(100 - general.training.toInt(), rawDelta)
        val riceCost = getCost().supplies
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to -riceCost, "training" to trainDelta, "experience" to 100, "leadershipExp" to 1),
        ))
        return CommandResult(true, listOf("${formatDate()} 특수 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 맹훈련 (Intense Training) - MCP 480 ==========

class che_맹훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "맹훈련"

    override fun getCost(): CommandCost = CommandCost(supplies = max(1, general.ships / 50))

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.supplies < getCost().supplies) return ConstraintResult.Fail("물자가 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.2).toInt())
        val trainDelta = min(100 - general.training.toInt(), max(rawDelta, 10))
        val riceCost = getCost().supplies
        val moraleDelta = -max(1, (general.morale.toInt() * 0.1).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "supplies" to -riceCost,
                "training" to trainDelta,
                "morale" to moraleDelta,
                "experience" to 150,
                "leadershipExp" to 1,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 맹훈련을 실시했습니다."), message = msg)
    }
}

// ========== 정비 (Maintenance/Repair) - MCP 160 ==========

class che_정비(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "정비"

    override fun getCost(): CommandCost = CommandCost(funds = max(1, general.ships / 200))

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.funds < getCost().funds) return ConstraintResult.Fail("자금이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val goldCost = getCost().funds
        val repairDelta = max(1, (general.leadership.toInt() * 0.03).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "funds" to -goldCost,
                "training" to repairDelta,
                "experience" to 50,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 함선 정비를 실시했습니다."), message = msg)
    }
}

// ========== 지상작전개시 (Ground Operation Start) - MCP 320 ==========

class che_지상작전개시(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "지상작전개시"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 행성에는 작전을 개시할 수 없습니다.")
        // GridEntryValidator: 그리드 진입 제한 확인 (gin7 §5.3)
        services?.gridEntryValidator?.let { validator ->
            @Suppress("UNCHECKED_CAST")
            val factionUnitsRaw = arg?.get("gridFactionUnits") as? Map<Long, Int>
            if (factionUnitsRaw != null) {
                val fleet = com.openlogh.entity.Fleet().also { f ->
                    f.factionId = general.factionId
                    f.battleships = (general.ships / 300).coerceAtLeast(1)
                }
                val gridState = GridEntryValidator.GridState(factionUnitsRaw)
                val entry = validator.canEnterGrid(fleet, gridState)
                if (!entry.allowed) return ConstraintResult.Fail("그리드 진입 불가: ${entry.reason}")
            }
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 80),
            "groundOperationStart" to true,
            "targetPlanetId" to dc.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 지상 작전을 개시했습니다."), message = msg)
    }
}

// ========== 지상전투개시 (Ground Battle Start) - MCP 160 ==========

class che_지상전투개시(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "지상전투개시"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 행성을 공격할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val riceCost = max(1, general.ships / 300)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to -riceCost, "experience" to 60),
            "groundBattleTriggered" to true,
            "targetPlanetId" to dc.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 지상 전투를 개시했습니다."), message = msg)
    }
}

// ========== 점령 (Capture) - MCP 320 ==========

class che_점령(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "점령"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 행성은 점령할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 200),
            "captureTriggered" to true,
            "targetPlanetId" to dc.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 점령 작전을 실시했습니다."), message = msg)
    }
}

// ========== 철수 (Withdrawal) - MCP 160 ==========

class che_철수(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "철수"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val atmosDelta = -max(1, (general.morale.toInt() * 0.1).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("morale" to atmosDelta, "experience" to 30),
            "withdrawalTriggered" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 철수했습니다."), message = msg)
    }
}

// ========== 후퇴 (Retreat) - MCP 80 ==========

class che_후퇴(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "후퇴"

    override fun checkFullCondition(): ConstraintResult {
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val atmosDelta = -max(1, (general.morale.toInt() * 0.2).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("morale" to atmosDelta, "experience" to 10),
            "retreatTriggered" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 긴급 후퇴했습니다."), message = msg)
    }
}

// ========== 육전대출격 (Ground Force Sortie) - MCP 160 ==========

class che_육전대출격(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "육전대출격"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50),
            "groundForceSortie" to true,
            "targetPlanetId" to dc.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 육전대를 출격시켰습니다."), message = msg)
    }
}

// ========== 육전대철수 (Ground Force Withdrawal) - MCP 80 ==========

class che_육전대철수(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "육전대철수"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "groundForceWithdrawal" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 육전대를 철수시켰습니다."), message = msg)
    }
}

// ========== 정찰 (Reconnaissance) - MCP 80 ==========

class che_정찰(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "정찰"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intelligence.toInt()
        val score = (intelValue * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val dc = destCity
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score / 2, "intelligenceExp" to 1),
            "reconResult" to mapOf(
                "targetPlanetId" to (dc?.id?.toString() ?: "0"),
                "score" to score,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 정찰을 실시했습니다."), message = msg)
    }
}

// ========== 워프항행 (Warp Navigation) - MCP 40 ==========

class che_워프항행(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "워프항행"

    private val destGridX: Int get() = (arg?.get("destGridX") as? Number)?.toInt() ?: -1
    private val destGridY: Int get() = (arg?.get("destGridY") as? Number)?.toInt() ?: -1

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.fleetId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        if (destGridX < 0 || destGridY < 0) return ConstraintResult.Fail("목적지 좌표가 유효하지 않습니다.")
        // GridEntryValidator: 그리드 진입 제한 확인 (gin7 §5.3)
        services?.gridEntryValidator?.let { validator ->
            @Suppress("UNCHECKED_CAST")
            val factionUnitsRaw = arg?.get("gridFactionUnits") as? Map<Long, Int>
            if (factionUnitsRaw != null) {
                val fleet = Fleet().also { f ->
                    f.factionId = general.factionId
                    f.battleships = (general.ships / 300).coerceAtLeast(1)
                }
                val gridState = GridEntryValidator.GridState(factionUnitsRaw)
                val entry = validator.canEnterGrid(fleet, gridState)
                if (!entry.allowed) return ConstraintResult.Fail("그리드 진입 불가: ${entry.reason}")
            }
        }
        // 항속(fuel) 확인: meta에서 fuel 값 체크
        val fuel = (general.meta["fuel"] as? Number)?.toInt() ?: 1000
        if (fuel < 100) return ConstraintResult.Fail("항속(연료)이 부족합니다. (보유: $fuel, 필요: 100)")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val fuel = (general.meta["fuel"] as? Number)?.toInt() ?: 1000
        val fuelCost = 100
        general.meta["fuel"] = fuel - fuelCost
        general.meta["gridX"] = destGridX
        general.meta["gridY"] = destGridY
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "warpNavigation" to mapOf(
                "destGridX" to destGridX,
                "destGridY" to destGridY,
                "fuelConsumed" to fuelCost,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 워프 항행으로 좌표($destGridX, $destGridY)로 이동했습니다."), message = msg)
    }
}

// ========== 성계내항행 (Inter-System Navigation) - MCP 160 ==========

class che_성계내항행(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "성계내항행"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.id == general.planetId) return ConstraintResult.Fail("이미 같은 행성에 있습니다.")
        return ConstraintResult.Pass
    }

    override fun getPostReqTurn(): Int = 8 // 대기 8게임분

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "interSystemNavigation" to mapOf(
                "destPlanetId" to dc.id.toString(),
                "waitTime" to 8,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 성계 내 항행을 개시합니다. (목적지: ${dc.name})"), message = msg)
    }
}

// ========== 군기유지 (Discipline Maintenance) - MCP 80 ==========

class che_군기유지(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "군기유지"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val disciplineBonus = max(1, (leadershipValue * 0.08).toInt())
        val moraleDelta = min(100 - general.morale.toInt(), disciplineBonus)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("morale" to moraleDelta, "experience" to 30, "leadershipExp" to 1),
            "disciplineApplied" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 군기를 유지하여 혼란 발생률이 감소했습니다."), message = msg)
    }
}

// ========== 경계출동 (Patrol Deployment) - MCP 160 ==========

class che_경계출동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "경계출동"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val commandValue = general.command.toInt()
        val secuDelta = max(1, (commandValue * 0.1).toInt())
        c.security = min(c.securityMax, c.security + secuDelta)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "commandExp" to 1),
            "patrolDeployed" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 경계 출동으로 치안이 증가했습니다."), message = msg)
    }
}

// ========== 무력진압 (Suppression) - MCP 160 ==========

class che_무력진압(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "무력진압"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val commandValue = general.command.toInt()
        val secuDelta = max(2, (commandValue * 0.2).toInt())
        c.security = min(c.securityMax, c.security + secuDelta)
        val approvalLoss = -(max(1, (secuDelta * 0.3).toInt())).toFloat()
        c.approval = (c.approval + approvalLoss).coerceAtLeast(0f)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30, "commandExp" to 1),
            "suppressionApplied" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 무력 진압으로 치안이 증가했지만 지지율이 하락했습니다."), message = msg)
    }
}

// ========== 분열행진 (Parade March) - MCP 160 ==========

class che_분열행진(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "분열행진"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val leadershipValue = general.leadership.toInt()
        val approvalGain = max(1f, (leadershipValue * 0.08f))
        c.approval = (c.approval + approvalGain).coerceAtMost(100f)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30, "leadershipExp" to 1),
            "paradeApplied" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 분열 행진으로 주민 지지율이 증가했습니다."), message = msg)
    }
}

// ========== 징발 (Requisition) - MCP 160 ==========

class che_징발(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "징발"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId == general.factionId) return ConstraintResult.Fail("아군 행성에서는 징발할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val commandValue = general.command.toInt()
        val seized = max(1, (c.commerce * 0.1 * (0.5 + commandValue * 0.01)).toInt())
        val approvalLoss = -(max(1, (seized * 0.5).toInt())).toFloat()
        c.approval = (c.approval + approvalLoss).coerceAtLeast(0f)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to seized, "experience" to 30, "commandExp" to 1),
            "requisitionResult" to mapOf("seized" to seized),
        ))
        return CommandResult(true, listOf("${formatDate()} 징발로 물자 ${seized}을(를) 획득했습니다."), message = msg)
    }
}

// ========== 육전훈련 (Ground Combat Training) - MCP 80 ==========

class che_육전훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "육전훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leaderVal = general.leadership.toInt()
        val delta = max(1, (leaderVal * 0.05).toInt())
        val gcBefore = general.groundCombat.toInt()
        general.groundCombat = min(100, gcBefore + delta).toShort()
        general.groundCombatExp = (general.groundCombatExp + 2).toShort()
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40),
        ))
        return CommandResult(true, listOf("${formatDate()} 육전 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 공전훈련 (Fighter Training) - MCP 80 ==========

class che_공전훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "공전훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leaderVal = general.leadership.toInt()
        val delta = max(1, (leaderVal * 0.05).toInt())
        val fsBefore = general.fighterSkill.toInt()
        general.fighterSkill = min(100, fsBefore + delta).toShort()
        general.fighterSkillExp = (general.fighterSkillExp + 2).toShort()
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40),
        ))
        return CommandResult(true, listOf("${formatDate()} 공전 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 특별경비 (Special Guard) - MCP 160 ==========

class che_특별경비(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "특별경비"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val defenseValue = general.defense.toInt()
        val secuDelta = max(1, (defenseValue * 0.12).toInt())
        c.security = min(c.securityMax, c.security + secuDelta)
        // 습격/잠입 방어 보너스를 meta에 기록
        c.meta["specialGuardActive"] = true
        c.meta["specialGuardBonus"] = secuDelta
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "defenseExp" to 1),
            "specialGuardApplied" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 특별 경비를 실시하여 습격/잠입 방어가 강화되었습니다."), message = msg)
    }
}

// ========== 통신방해 (Communication Jamming) - MCP 160 ==========

class che_통신방해(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "통신방해"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intelligence.toInt()
        val duration = max(1, (intelValue * 0.05).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "intelligenceExp" to 1),
            "commJamming" to mapOf("duration" to duration),
        ))
        return CommandResult(true, listOf("${formatDate()} 통신 방해를 개시했습니다. (${duration}턴 지속)"), message = msg)
    }
}

// ========== 위장함대 (Decoy Fleet) - MCP 320 ==========

class che_위장함대(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "위장함대"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intelligence.toInt()
        val deception = max(1, (intelValue * 0.08).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "intelligenceExp" to 1),
            "decoyFleet" to mapOf("deceptionLevel" to deception),
        ))
        return CommandResult(true, listOf("${formatDate()} 위장 함대를 편성했습니다."), message = msg)
    }
}

// ========== 병기연습 (War Game) - MCP 20 ==========

class che_병기연습(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "병기연습"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val roll = rng.nextInt(4)
        val (expKey, expLabel) = when (roll) {
            0 -> "commandExp" to "지휘"
            1 -> "mobilityExp" to "기동"
            2 -> "attackExp" to "공격"
            else -> "defenseExp" to "방어"
        }
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30, expKey to 2),
        ))
        return CommandResult(true, listOf("${formatDate()} 병기 연습(시뮬레이터 훈련)으로 ${expLabel} 경험을 쌓았습니다."), message = msg)
    }
}

// ========== 육전전술훈련 (Ground Tactics Training) - MCP 100 ==========

class che_육전전술훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "육전전술훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val gcDelta = max(1, (general.leadership.toInt() * 0.08).toInt())
        general.groundCombat = min(100, general.groundCombat.toInt() + gcDelta).toShort()
        general.groundCombatExp = (general.groundCombatExp + 3).toShort()
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 60),
            "tacticsLearned" to "ground",
        ))
        return CommandResult(true, listOf("${formatDate()} 육전 전술 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 공전전술훈련 (Fighter Tactics Training) - MCP 100 ==========

class che_공전전술훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "공전전술훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val fsDelta = max(1, (general.leadership.toInt() * 0.08).toInt())
        general.fighterSkill = min(100, general.fighterSkill.toInt() + fsDelta).toShort()
        general.fighterSkillExp = (general.fighterSkillExp + 3).toShort()
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 60),
            "tacticsLearned" to "fighter",
        ))
        return CommandResult(true, listOf("${formatDate()} 공전 전술 훈련을 실시했습니다."), message = msg)
    }
}
