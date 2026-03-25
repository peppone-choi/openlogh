@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.espionage.*
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val espMapper = jacksonObjectMapper()

// ===== Helper: Officer → EspionageOfficerData =====

private fun General.toEspData(): EspionageOfficerData {
    @Suppress("UNCHECKED_CAST")
    val permits = (meta["arrestPermits"] as? Collection<*>)
        ?.mapNotNull { (it as? Number)?.toLong() }?.toSet() ?: emptySet()
    @Suppress("UNCHECKED_CAST")
    val execAuth = (meta["executeAuthority"] as? Collection<*>)
        ?.mapNotNull { (it as? Number)?.toLong() }?.toSet() ?: emptySet()
    return EspionageOfficerData(
        officerId = id, name = name,
        intelligence = intelligence.toInt(), rank = rank.toInt(),
        intelOps = intelOps, mobility = mobility.toInt(),
        attack = attack.toInt(), defense = defense.toInt(),
        planetId = planetId, fleetId = fleetId, factionId = factionId,
        injury = injury.toInt(),
        arrestPermits = permits, executeAuthority = execAuth,
    )
}

// ========== 일제수색 (Mass Search) - MCP 200 ==========

class che_일제수색(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "일제수색"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        @Suppress("UNCHECKED_CAST")
        val suspectData = (arg?.get("suspects") as? List<Map<String, Any>>)?.map {
            EspionageOfficerData(
                officerId = (it["officerId"] as Number).toLong(),
                name = it["name"] as? String ?: "",
                intelligence = (it["intelligence"] as? Number)?.toInt() ?: 50,
                rank = (it["rank"] as? Number)?.toInt() ?: 0,
                intelOps = (it["intelOps"] as? Number)?.toInt() ?: 0,
                mobility = (it["mobility"] as? Number)?.toInt() ?: 50,
                attack = (it["attack"] as? Number)?.toInt() ?: 50,
                defense = (it["defense"] as? Number)?.toInt() ?: 50,
                planetId = general.planetId, fleetId = 0, factionId = 0,
            )
        } ?: emptyList()

        val result = EspionageService.executeMassSearch(
            searcher = general.toEspData(),
            planetSecurity = c.security,
            suspects = suspectData,
            rng = rng,
        )
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "intelligenceExp" to 1),
            "massSearchResult" to mapOf(
                "discovered" to result.discovered,
                "count" to result.discovered.size,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${result.log}"), message = msg)
    }
}

// ========== 체포허가 (Arrest Permit) - PCP 800 ==========

class che_체포허가(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "체포허가"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val result = EspionageService.executeArrestPermit(
            issuer = general.toEspData(),
            targetId = dg.id,
            targetName = dg.name,
        )
        // 체포 허가 목록에 추가
        @Suppress("UNCHECKED_CAST")
        val permits = (general.meta.getOrPut("arrestPermits") { mutableListOf<Long>() } as MutableList<Any>)
        if (dg.id !in permits.mapNotNull { (it as? Number)?.toLong() }) {
            permits.add(dg.id)
        }
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "arrestPermit" to mapOf("targetId" to dg.id.toString()),
        ))
        return CommandResult(true, listOf("${formatDate()} ${result.log}"), message = msg)
    }
}

// ========== 집행명령 (Execute Order) - PCP 800 ==========

class che_집행명령(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "집행명령"

    private val executorId: Long get() = (arg?.get("executorId") as? Number)?.toLong() ?: 0L

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        if (executorId == 0L) return ConstraintResult.Fail("집행자를 지정해야 합니다.")
        @Suppress("UNCHECKED_CAST")
        val permits = (general.meta["arrestPermits"] as? Collection<*>)
            ?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
        if (dg.id !in permits) return ConstraintResult.Fail("체포 허가가 없는 대상입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "executeOrder" to mapOf(
                "targetId" to dg.id.toString(),
                "executorId" to executorId.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${dg.name}에 대한 집행 명령을 하달했습니다."), message = msg)
    }
}

// ========== 체포명령 (Arrest Order) - MCP 100 ==========

class che_체포명령(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "체포명령"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        if (dg.planetId != general.planetId) return ConstraintResult.Fail("같은 행성에 있어야 합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val result = EspionageService.executeArrestOrder(
            arrestor = general.toEspData(),
            target = dg.toEspData(),
            sameSpot = true,
            rng = rng,
        )
        if (result.arrested) {
            dg.blockState = 1 // 체포 상태
        }
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "intelligenceExp" to 1),
            "arrestResult" to mapOf(
                "targetId" to dg.id.toString(),
                "arrested" to result.arrested,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${result.log}"), message = msg)
    }
}

// ========== 사열 (Inspection) - MCP 150 ==========

class che_사열(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "사열"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        // 반란 징후 탐지: destCityGenerals에서 rebellionIntent 보유자 탐색
        val discovered = mutableListOf<String>()
        destCityGenerals?.forEach { officer ->
            val intent = (officer.meta["rebellionIntent"] as? Number)?.toInt() ?: 0
            if (intent > 0) {
                val found = EspionageEngine.attemptInspection(
                    rebellionIntent = intent,
                    inspectorIntelligence = general.intelligence.toInt(),
                    rng = rng,
                )
                if (found) discovered.add(officer.name)
            }
        }
        val logMsg = if (discovered.isNotEmpty()) {
            "사열 결과: ${discovered.joinToString(", ")}에게서 반란 징후가 감지되었습니다."
        } else {
            "사열 결과: 이상 없음."
        }
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "intelligenceExp" to 1),
            "inspectionResult" to mapOf("discovered" to discovered),
        ))
        return CommandResult(true, listOf("${formatDate()} $logMsg"), message = msg)
    }
}

// ========== 습격 (Raid) - MCP 300 ==========

class che_습격(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "습격"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        if (dg.planetId != general.planetId) return ConstraintResult.Fail("같은 행성에 있어야 합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val result = EspionageService.executeRaid(
            raider = general.toEspData(),
            target = dg.toEspData(),
            sameSpot = true,
            rng = rng,
        )
        if (result.hit) {
            dg.injury = (dg.injury + result.injuryDealt).coerceAtMost(100).toShort()
        }
        if (result.counterInjury > 0) {
            general.injury = (general.injury + result.counterInjury).coerceAtMost(100).toShort()
        }
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "attackExp" to 1),
            "raidResult" to mapOf(
                "targetId" to dg.id.toString(),
                "hit" to result.hit,
                "injuryDealt" to result.injuryDealt,
                "counterInjury" to result.counterInjury,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${result.log}"), message = msg)
    }
}

// ========== 감시 (Surveillance) - MCP 100 ==========

class che_감시(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "감시"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val surveillance = EspionageService.startSurveillance(
            watcher = general.toEspData(),
            target = dg.toEspData(),
        )
        // 감시 상태를 meta에 기록
        @Suppress("UNCHECKED_CAST")
        val watches = (general.meta.getOrPut("surveillance") { mutableListOf<Map<String, Any>>() }
                as MutableList<Map<String, Any>>)
        watches.add(mapOf(
            "targetOfficerId" to dg.id,
            "startTurn" to (env.year * 12 + env.month),
        ))
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30, "intelligenceExp" to 1),
            "surveillanceStarted" to mapOf("targetId" to dg.id.toString()),
        ))
        return CommandResult(true, listOf("${formatDate()} ${dg.name}에 대한 감시를 시작했습니다."), message = msg)
    }
}

// ========== 잠입공작 (Infiltration) - MCP 200 ==========

class che_잠입공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "잠입공작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 행성에는 잠입할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val success = EspionageEngine.attemptInfiltration(
            intelOps = general.intelOps,
            targetSecurity = dc.security,
            targetIntelligence = (arg?.get("targetBestIntelligence") as? Number)?.toInt() ?: 50,
            rng = rng,
        )
        if (success) {
            general.meta["infiltrationState"] = InfiltrationState.INFILTRATING.code
            general.meta["infiltratedPlanetId"] = dc.id
        } else {
            general.meta["infiltrationState"] = InfiltrationState.DETECTED.code
        }
        val logMsg = if (success) "잠입 공작에 성공했습니다." else "잠입 공작이 발각되었습니다!"
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 60, "intelligenceExp" to 2),
            "infiltrationResult" to mapOf(
                "success" to success,
                "targetPlanetId" to dc.id.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} $logMsg"), message = msg)
    }
}

// ========== 탈출공작 (Exfiltration) - MCP 100 ==========

class che_탈출공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "탈출공작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val state = general.meta["infiltrationState"] as? String
        if (state != InfiltrationState.INFILTRATING.code && state != InfiltrationState.DETECTED.code) {
            return ConstraintResult.Fail("잠입 상태가 아닙니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city
        val planetSecurity = c?.security ?: 50
        val success = EspionageEngine.attemptExfiltration(
            escapeeIntelOps = general.intelOps,
            escapeMobility = general.mobility.toInt(),
            planetSecurity = planetSecurity,
            rng = rng,
        )
        if (success) {
            general.meta["infiltrationState"] = InfiltrationState.ESCAPED.code
            general.meta.remove("infiltratedPlanetId")
        }
        val logMsg = if (success) "탈출에 성공했습니다." else "탈출에 실패했습니다."
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "intelligenceExp" to 1),
            "exfiltrationResult" to mapOf("success" to success),
        ))
        return CommandResult(true, listOf("${formatDate()} $logMsg"), message = msg)
    }
}

// ========== 정보공작 (Intelligence Gathering) - MCP 150 ==========

class che_정보공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "정보공작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val state = general.meta["infiltrationState"] as? String
        if (state != InfiltrationState.INFILTRATING.code) {
            return ConstraintResult.Fail("잠입 상태에서만 실행할 수 있습니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val gathered = EspionageEngine.gatherIntel(
            intelOps = general.intelOps,
            intelligenceStat = general.intelligence.toInt(),
            rng = rng,
        )
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "intelligenceExp" to 2),
            "intelGathered" to gathered,
        ))
        val categories = gathered.keys.joinToString(", ")
        return CommandResult(true, listOf("${formatDate()} 정보 공작 실행. 수집 카테고리: $categories"), message = msg)
    }
}

// ========== 파괴공작 (Sabotage) - MCP 400 ==========

class che_파괴공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "파괴공작"

    private val targetType: String get() = arg?.get("targetType") as? String ?: "facility"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val state = general.meta["infiltrationState"] as? String
        if (state != InfiltrationState.INFILTRATING.code) {
            return ConstraintResult.Fail("잠입 상태에서만 실행할 수 있습니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val damageRate = EspionageEngine.calculateSabotageDamage(
            intelOps = general.intelOps,
            rng = rng,
        )
        val damagePct = String.format("%.1f", damageRate * 100)
        // 파괴 후 발각 확률 증가
        general.meta["infiltrationState"] = if (rng.nextDouble() < 0.4) {
            InfiltrationState.DETECTED.code
        } else {
            InfiltrationState.INFILTRATING.code
        }
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 80, "intelligenceExp" to 2),
            "sabotageResult" to mapOf(
                "targetType" to targetType,
                "damageRate" to damageRate,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 파괴 공작 실행. ${targetType} 피해율: $damagePct%"), message = msg)
    }
}

// ========== 선동공작 (Propaganda) - MCP 200 ==========

class che_선동공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "선동공작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("대상 행성이 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 행성에는 선동할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val approvalLoss = EspionageEngine.calculatePropagandaEffect(
            intelOps = general.intelOps,
            politicsStat = general.politics.toInt(),
            targetApproval = dc.approval,
            rng = rng,
        )
        dc.approval = (dc.approval - approvalLoss).coerceAtLeast(0f)
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "politicsExp" to 1, "intelligenceExp" to 1),
            "propagandaResult" to mapOf(
                "targetPlanetId" to dc.id.toString(),
                "approvalLoss" to approvalLoss,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 선동 공작으로 지지율이 ${String.format("%.1f", approvalLoss)} 하락했습니다."), message = msg)
    }
}

// ========== 귀환공작 (Return Operation) - MCP 100 ==========
// 잠입 완료 후 아군 영토로 안전 귀환.

class che_귀환공작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "귀환공작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val state = general.meta["infiltrationState"] as? String
        if (state != InfiltrationState.INFILTRATING.code && state != InfiltrationState.ESCAPED.code) {
            return ConstraintResult.Fail("잠입/탈출 상태에서만 실행할 수 있습니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val success = EspionageEngine.attemptExfiltration(
            escapeeIntelOps = general.intelOps,
            escapeMobility = general.mobility.toInt(),
            planetSecurity = (city?.security ?: 50),
            rng = rng,
        )
        if (success) {
            general.meta["infiltrationState"] = InfiltrationState.NONE.code
            general.meta.remove("infiltratedPlanetId")
            // 귀환 행성으로 이동
            val returnPlanetId = (general.meta["returnPlanetId"] as? Number)?.toLong()
            if (returnPlanetId != null && returnPlanetId > 0) {
                general.planetId = returnPlanetId
            }
        }
        val logMsg = if (success) "아군 영토로 무사히 귀환했습니다." else "귀환에 실패했습니다."
        val msg = espMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "intelligenceExp" to 1),
            "returnResult" to mapOf("success" to success),
        ))
        return CommandResult(true, listOf("${formatDate()} $logMsg"), message = msg)
    }
}
