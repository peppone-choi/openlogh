@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.random.Random

private val cmdMapper = jacksonObjectMapper()

// ========== 작전계획 (Operation Planning) - MCP 320 ==========

class 작전계획(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "작전계획"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val targetId = destCity?.id?.toString() ?: (arg?.get("targetId") as? String ?: "0")
        val opType = arg?.get("opType") as? String ?: "점령"
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50),
            "operationPlan" to mapOf(
                "targetId" to targetId,
                "type" to opType,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 작전 계획을 수립했습니다."), message = msg)
    }
}

// ========== 작전철회 (Operation Cancellation) - MCP 160 ==========

class 작전철회(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "작전철회"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val operationId = arg?.get("operationId") as? String ?: "0"
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "cancelOperation" to mapOf("operationId" to operationId),
        ))
        return CommandResult(true, listOf("${formatDate()} 작전을 철회했습니다."), message = msg)
    }
}

// ========== 발령 (Issue Operation Order) - MCP 160 ==========
// Note: nationCommands has a separate "발령" (che_발령). This is the general-level order command.

class 발령(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "발령"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val destPlanetId = destCity?.id?.toString() ?: (arg?.get("destPlanetId") as? String ?: "0")
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "orderIssued" to mapOf(
                "targetOfficerId" to dg.id.toString(),
                "destPlanetId" to destPlanetId,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 발령을 내렸습니다."), message = msg)
    }
}

// ========== 강의 (Academy Lecture) - MCP 160 ==========

class 강의(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "강의"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intel.toInt()
        val score = (intelValue * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val targetId = destGeneral?.id?.toString() ?: (arg?.get("studentId") as? String ?: "0")
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score / 2, "intelExp" to 1),
            "lectureResult" to mapOf(
                "studentId" to targetId,
                "score" to score,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 강의를 실시했습니다."), message = msg)
    }
}

// ========== 수송계획 (Transport Plan) - MCP 320 ==========

class 수송계획(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "수송계획"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val resourceType = arg?.get("resourceType") as? String ?: "supplies"
        val amount = (arg?.get("amount") as? Number)?.toInt() ?: 0
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "transportPlan" to mapOf(
                "targetPlanetId" to dc.id.toString(),
                "resourceType" to resourceType,
                "amount" to amount,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 수송 계획을 수립했습니다."), message = msg)
    }
}

// ========== 수송중지 (Transport Cancellation) - MCP 160 ==========

class 수송중지(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "수송중지"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val transportId = arg?.get("transportId") as? String ?: "0"
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "cancelTransport" to mapOf("transportId" to transportId),
        ))
        return CommandResult(true, listOf("${formatDate()} 수송을 중지했습니다."), message = msg)
    }
}
