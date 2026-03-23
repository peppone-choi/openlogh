@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val opsMapper = jacksonObjectMapper()

// ========== 연료보급 (Fuel Replenishment) - MCP 80 ==========

class che_연료보급(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "연료보급"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val riceCost = max(1, general.crew / 200)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("rice" to -riceCost, "experience" to 20),
        ))
        return CommandResult(true, listOf("${formatDate()} 연료를 보급했습니다."), message = msg)
    }
}

// ========== 기본훈련 (Basic Training) - MCP 160 ==========

class che_기본훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "기본훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.train >= 100.toShort()) return ConstraintResult.Fail("훈련도가 이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.05).toInt())
        val trainDelta = min(100 - general.train.toInt(), rawDelta)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("train" to trainDelta, "experience" to 50, "leadershipExp" to 1),
        ))
        return CommandResult(true, listOf("${formatDate()} 기본 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 특수훈련 (Special Training) - MCP 320 ==========

class che_특수훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "특수훈련"

    override fun getCost(): CommandCost = CommandCost(rice = max(1, general.crew / 100))

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.train >= 100.toShort()) return ConstraintResult.Fail("훈련도가 이미 최대치입니다.")
        if (general.rice < getCost().rice) return ConstraintResult.Fail("물자가 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.1).toInt())
        val trainDelta = min(100 - general.train.toInt(), rawDelta)
        val riceCost = getCost().rice
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("rice" to -riceCost, "train" to trainDelta, "experience" to 100, "leadershipExp" to 1),
        ))
        return CommandResult(true, listOf("${formatDate()} 특수 훈련을 실시했습니다."), message = msg)
    }
}

// ========== 맹훈련 (Intense Training) - MCP 480 ==========

class che_맹훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "맹훈련"

    override fun getCost(): CommandCost = CommandCost(rice = max(1, general.crew / 50))

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.rice < getCost().rice) return ConstraintResult.Fail("물자가 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val rawDelta = max(1, (leadershipValue * 0.2).toInt())
        val trainDelta = min(100 - general.train.toInt(), max(rawDelta, 10))
        val riceCost = getCost().rice
        val moraleDelta = -max(1, (general.atmos.toInt() * 0.1).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "rice" to -riceCost,
                "train" to trainDelta,
                "atmos" to moraleDelta,
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

    override fun getCost(): CommandCost = CommandCost(gold = max(1, general.crew / 200))

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        if (general.gold < getCost().gold) return ConstraintResult.Fail("자금이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val goldCost = getCost().gold
        val repairDelta = max(1, (general.leadership.toInt() * 0.03).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "gold" to -goldCost,
                "train" to repairDelta,
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.nationId == general.nationId) return ConstraintResult.Fail("아군 행성에는 작전을 개시할 수 없습니다.")
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.nationId == general.nationId) return ConstraintResult.Fail("아군 행성을 공격할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val riceCost = max(1, general.crew / 300)
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("rice" to -riceCost, "experience" to 60),
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.nationId == general.nationId) return ConstraintResult.Fail("아군 행성은 점령할 수 없습니다.")
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val atmosDelta = -max(1, (general.atmos.toInt() * 0.1).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("atmos" to atmosDelta, "experience" to 30),
            "withdrawalTriggered" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 철수했습니다."), message = msg)
    }
}

// ========== 후퇴 (Retreat) - MCP 80 ==========

class che_후퇴(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "후퇴"

    override fun checkFullCondition(): ConstraintResult {
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val atmosDelta = -max(1, (general.atmos.toInt() * 0.2).toInt())
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("atmos" to atmosDelta, "experience" to 10),
            "retreatTriggered" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 긴급 후퇴했습니다."), message = msg)
    }
}

// ========== 육전대출격 (Ground Force Sortie) - MCP 160 ==========

class che_육전대출격(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "육전대출격"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.crew == 0) return ConstraintResult.Fail("함선이 없습니다.")
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intel.toInt()
        val score = (intelValue * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val dc = destCity
        val msg = opsMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score / 2, "intelExp" to 1),
            "reconResult" to mapOf(
                "targetPlanetId" to (dc?.id?.toString() ?: "0"),
                "score" to score,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 정찰을 실시했습니다."), message = msg)
    }
}
