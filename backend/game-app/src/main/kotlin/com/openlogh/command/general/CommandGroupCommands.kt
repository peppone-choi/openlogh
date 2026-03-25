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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intelligence.toInt()
        val score = (intelValue * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val targetId = destGeneral?.id?.toString() ?: (arg?.get("studentId") as? String ?: "0")
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score / 2, "intelligenceExp" to 1),
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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        // FleetFormationRules: 인구 비례 함대 편성 한도 확인 (gin7 §6.12)
        services?.fleetFormationRules?.let { rules ->
            val totalPop = (arg?.get("factionTotalPopulation") as? Number)?.toLong() ?: 0L
            val limits = rules.maxFleetsByPopulation(totalPop)
            val currentTransports = (arg?.get("currentTransportCount") as? Number)?.toInt() ?: 0
            if (currentTransports >= limits.transports) {
                return ConstraintResult.Fail("수송함대 편성 한도를 초과했습니다. (한도: ${limits.transports})")
            }
        }
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
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
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

// ========== 부대결성 (Fleet Formation) - MCP 320 ==========

class 부대결성(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "부대결성"

    private val fleetName: String get() = arg?.get("fleetName") as? String ?: "${general.name}함대"
    private val fleetType: String get() = arg?.get("fleetType") as? String ?: "fleet"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.fleetId != 0L) return ConstraintResult.Fail("이미 함대에 소속되어 있습니다.")
        // FleetFormationRules: 인구 비례 함대 편성 한도 확인 (gin7 §6.12)
        services?.fleetFormationRules?.let { rules ->
            val totalPop = (arg?.get("factionTotalPopulation") as? Number)?.toLong() ?: 0L
            val limits = rules.maxFleetsByPopulation(totalPop)
            val currentFleets = (arg?.get("currentFleetCount") as? Number)?.toInt() ?: 0
            val maxCount = when (fleetType) {
                "fleet" -> limits.fleets
                "transport" -> limits.transports
                "patrol" -> limits.patrols
                "ground" -> limits.groundForces
                else -> limits.fleets
            }
            if (currentFleets >= maxCount) {
                return ConstraintResult.Fail("${fleetType} 편성 한도를 초과했습니다. (한도: $maxCount)")
            }
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        // Fleet 엔티티 생성은 호출자(턴 엔진)가 처리. 여기서는 이벤트 메시지를 반환.
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50),
            "fleetFormation" to mapOf(
                "fleetName" to fleetName,
                "fleetType" to fleetType,
                "leaderOfficerId" to general.id.toString(),
                "factionId" to general.factionId.toString(),
                "planetId" to general.planetId.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${fleetName}을(를) 결성했습니다."), message = msg)
    }
}

// ========== 부대해산 (Fleet Dissolution) - MCP 160 ==========

class 부대해산(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "부대해산"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.fleetId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val fleetId = general.fleetId
        // Fleet 엔티티 삭제 및 소속 유닛 행성 창고 반환은 호출자가 처리.
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "fleetDissolution" to mapOf(
                "fleetId" to fleetId.toString(),
                "returnToPlanetId" to general.planetId.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 부대를 해산했습니다. 소속 유닛은 행성 창고로 반환됩니다."), message = msg)
    }
}

// ========== 할당 (Unit Assignment: planet warehouse → fleet) - MCP 160 ==========

class 할당(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "할당"

    private val unitType: String get() = arg?.get("unitType") as? String ?: "battleship"
    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.fleetId == 0L) return ConstraintResult.Fail("소속 함대가 없습니다.")
        if (amount <= 0) return ConstraintResult.Fail("할당 수량이 유효하지 않습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        // 실제 유닛 이동은 호출자(턴 엔진)가 처리
        val msg = cmdMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "unitAssignment" to mapOf(
                "unitType" to unitType,
                "amount" to amount,
                "fromPlanetId" to general.planetId.toString(),
                "toFleetId" to general.fleetId.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${unitType} ${amount}유닛을 함대에 할당했습니다."), message = msg)
    }
}
