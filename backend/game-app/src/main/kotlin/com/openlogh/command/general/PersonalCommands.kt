@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import com.openlogh.service.CompatibilityType
import com.openlogh.service.ProposalOfficerData
import com.openlogh.service.ProposalService
import kotlin.math.max
import kotlin.random.Random

private val personalMapper = jacksonObjectMapper()

// ========== 퇴역 (Retirement) - PCP 0 ==========

class 퇴역(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "퇴역"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "retireToCommoner" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 퇴역했습니다."), message = msg)
    }
}

// ========== 지원전환 (Career Switch: military ↔ political) - PCP 160 ==========

class 지원전환(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "지원전환"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "careerSwitch" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 지원 전환을 신청했습니다."), message = msg)
    }
}

// ========== 망명 (Defection) - PCP 320 ==========

class 망명(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "망명"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dn = destNation ?: return ConstraintResult.Fail("목적지 국가가 없습니다.")
        if (dn.id == general.factionId) return ConstraintResult.Fail("현재 소속 국가입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dn = destNation!!
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50),
            "defection" to true,
            "targetFactionId" to dn.id.toString(),
            "clearAddressBook" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 망명했습니다."), message = msg)
    }
}

// ========== 회견 (Meeting/Audience) - PCP 40 ==========

class 회견(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "회견"

    override fun checkFullCondition(): ConstraintResult {
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        if (dg.planetId != general.planetId) return ConstraintResult.Fail("같은 행성에 있어야 합니다.")
        if (dg.id == general.id) return ConstraintResult.Fail("자기 자신과는 회견할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val friendshipDelta = max(1, (general.administration.toInt() * 0.05).toInt())
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20, "administrationExp" to 1),
            "mutualFriendshipDelta" to friendshipDelta,
            "targetOfficerId" to dg.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} ${dg.personalCode}와 회견했습니다."), message = msg)
    }
}

// ========== 수강 (Course Study at Officer Academy) - PCP 80 ==========

class 수강(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "수강"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        // PlanetFacilityService: 사관학교 보유 행성에서만 수강 가능 (gin7 §5.5)
        services?.planetFacilityService?.let { facilityService ->
            if (!facilityService.hasAcademy(c)) return ConstraintResult.Fail("사관학교가 없습니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val intelValue = general.intelligence.toInt()
        val score = (intelValue * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val roll = rng.nextInt(5)
        val statExpKey = when (roll) {
            0 -> "leadershipExp"
            1 -> "commandExp"
            2 -> "intelligenceExp"
            3 -> "politicsExp"
            else -> "administrationExp"
        }
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score, statExpKey to 2),
        ))
        return CommandResult(true, listOf("${formatDate()} 수강을 완료했습니다."), message = msg)
    }
}

// ========== 반의 (Rebellious Intent — Coup Step 1) - PCP 160 ==========

class 반의(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "반의"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30),
            "coupStep" to 1,
            "becomeCoupLeader" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 반의를 표명했습니다."), message = msg)
    }
}

// ========== 모의 (Conspiracy — Coup Step 2) - PCP 320 ==========

class 모의(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "모의"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        if (dg.planetId != general.planetId) return ConstraintResult.Fail("같은 행성에 있어야 합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50),
            "coupStep" to 2,
            "conspiracyTarget" to dg.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 모의를 진행했습니다."), message = msg)
    }
}

// ========== 설득 (Persuasion) - PCP 160 ==========

class 설득(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "설득"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!

        // Use ProposalService for acceptance probability (gin7 4.4)
        val proposerData = ProposalOfficerData(
            officerId = general.id, name = general.name,
            rank = general.rank.toInt(), politics = general.politics.toInt(),
        )
        val targetData = ProposalOfficerData(
            officerId = dg.id, name = dg.name,
            rank = dg.rank.toInt(), politics = dg.politics.toInt(),
        )
        val friendship = (general.meta["friendship_${dg.id}"] as? Number)?.toInt() ?: 50
        val compatCode = general.meta["compat_${dg.id}"] as? String ?: "neutral"
        val result = ProposalService.evaluateProposal(
            proposer = proposerData,
            target = targetData,
            friendship = friendship,
            compatibility = CompatibilityType.fromCode(compatCode),
            rng = rng,
        )

        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 40, "administrationExp" to 1),
            "persuasionTarget" to dg.id.toString(),
            "persuasionSuccess" to result.accepted,
            "persuasionProbability" to result.probability,
        ))
        val resultMsg = if (result.accepted) "설득에 성공했습니다." else "설득에 실패했습니다."
        return CommandResult(true, listOf("${formatDate()} $resultMsg (확률: ${String.format("%.1f", result.probability * 100)}%)"), message = msg)
    }
}

// ========== 반란참가 (Join Rebellion/Coup) - PCP 0 ==========

class 반란참가(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "반란참가"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "joinCoup" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 반란에 참가했습니다."), message = msg)
    }
}

// ========== 자금투입 (Fund Injection) - PCP 160 ==========

class 자금투입(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "자금투입"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.funds < amount) return ConstraintResult.Fail("자금이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val actualAmount = amount.coerceAtMost(general.funds)
        val targetType = arg?.get("targetType") as? String ?: "planet"
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("funds" to -actualAmount, "experience" to 30, "politicsExp" to 1),
            "fundInjection" to mapOf("amount" to actualAmount, "targetType" to targetType),
        ))
        return CommandResult(true, listOf("${formatDate()} ${actualAmount}의 자금을 투입했습니다."), message = msg)
    }
}

// ========== 기함구매 (Flagship Purchase) - PCP 320 ==========

class 기함구매(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "기함구매"

    private val flagshipCode: String get() = arg?.get("flagshipCode") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (flagshipCode.isEmpty()) return ConstraintResult.Fail("기함 코드가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20, "flagshipCode" to flagshipCode),
            "flagshipPurchase" to mapOf("code" to flagshipCode),
        ))
        return CommandResult(true, listOf("${formatDate()} 기함을 구매했습니다."), message = msg)
    }
}

// ========== 제안 (Proposal: subordinate → superior) - PCP 0 ==========
// ProposalService 연동. 하급→상급 제안.

class 제안(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "제안"

    private val proposalType: String get() = arg?.get("proposalType") as? String ?: "general"
    private val proposalContent: String get() = arg?.get("proposalContent") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("제안 대상이 없습니다.")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("같은 국가 소속이어야 합니다.")
        if (dg.rank <= general.rank) return ConstraintResult.Fail("상급자에게만 제안할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val proposerData = ProposalOfficerData(
            officerId = general.id, name = general.name,
            rank = general.rank.toInt(), politics = general.politics.toInt(),
        )
        val targetData = ProposalOfficerData(
            officerId = dg.id, name = dg.name,
            rank = dg.rank.toInt(), politics = dg.politics.toInt(),
        )
        val friendship = (general.meta["friendship_${dg.id}"] as? Number)?.toInt() ?: 50
        val compatCode = general.meta["compat_${dg.id}"] as? String ?: "neutral"
        val result = ProposalService.evaluateProposal(
            proposer = proposerData,
            target = targetData,
            friendship = friendship,
            compatibility = CompatibilityType.fromCode(compatCode),
            rng = rng,
        )
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 30, "politicsExp" to 1),
            "proposal" to mapOf(
                "type" to proposalType,
                "content" to proposalContent,
                "targetId" to dg.id.toString(),
                "accepted" to result.accepted,
                "probability" to result.probability,
            ),
        ))
        val resultMsg = if (result.accepted) "제안이 수락되었습니다." else "제안이 거부되었습니다."
        return CommandResult(true, listOf("${formatDate()} $resultMsg (확률: ${String.format("%.1f", result.probability * 100)}%)"), message = msg)
    }
}

// ========== 명령 (Order: superior → subordinate) - PCP 0 ==========
// 상급→하급 명령 하달. 복종 확률은 계급차/충성도에 의존.

class 명령(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "명령"

    private val orderContent: String get() = arg?.get("orderContent") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dg = destGeneral ?: return ConstraintResult.Fail("명령 대상이 없습니다.")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("같은 국가 소속이어야 합니다.")
        if (dg.rank >= general.rank) return ConstraintResult.Fail("하급자에게만 명령할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val friendship = (general.meta["friendship_${dg.id}"] as? Number)?.toInt() ?: 50
        val loyalty = (dg.meta["loyalty"] as? Number)?.toInt() ?: 50
        val obedience = com.openlogh.engine.organization.ProposalSystem.calculateObedienceChance(
            commanderRank = general.rank.toInt(),
            subordinateRank = dg.rank.toInt(),
            loyalty = loyalty,
            friendship = friendship,
        )
        val obeyed = rng.nextDouble() < obedience
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 20),
            "order" to mapOf(
                "content" to orderContent,
                "targetId" to dg.id.toString(),
                "obeyed" to obeyed,
                "probability" to obedience,
            ),
        ))
        val resultMsg = if (obeyed) "명령이 이행되었습니다." else "명령이 거부되었습니다."
        return CommandResult(true, listOf("${formatDate()} $resultMsg (복종률: ${String.format("%.1f", obedience * 100)}%)"), message = msg)
    }
}

// ========== 귀환설정 (Return Setting) - PCP 0 ==========
// 기함 격침 시 귀환 행성 설정.

class 귀환설정(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "귀환설정"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("귀환 행성을 지정해야 합니다.")
        if (dc.factionId != general.factionId) return ConstraintResult.Fail("아군 행성만 귀환지로 설정할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        general.meta["returnPlanetId"] = dc.id
        val msg = personalMapper.writeValueAsString(mapOf(
            "returnSetting" to mapOf("planetId" to dc.id.toString(), "planetName" to dc.name),
        ))
        return CommandResult(true, listOf("${formatDate()} 귀환 행성을 ${dc.name}(으)로 설정했습니다."), message = msg)
    }
}

// ========== 원거리이동 (Long-range Move) - PCP 10 ==========
// 행성 내 시설간 이동 (다른 행성의 시설로).

class 원거리이동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "원거리이동"

    private val destFacility: String get() = arg?.get("destFacility") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 행성이 없습니다.")
        if (dc.id == general.planetId) return ConstraintResult.Fail("같은 행성 내에서는 근거리 이동을 사용하세요.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "longRangeMove" to mapOf(
                "destPlanetId" to dc.id.toString(),
                "destFacility" to destFacility,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} ${dc.name}(으)로 원거리 이동을 개시합니다."), message = msg)
    }
}

// ========== 근거리이동 (Short-range Move) - PCP 5 ==========
// 시설 내 스팟간 이동.

class 근거리이동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "근거리이동"

    private val destSpot: String get() = arg?.get("destSpot") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (destSpot.isEmpty()) return ConstraintResult.Fail("이동할 스팟을 지정해야 합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        general.meta["currentSpot"] = destSpot
        val msg = personalMapper.writeValueAsString(mapOf(
            "shortRangeMove" to mapOf("destSpot" to destSpot),
        ))
        return CommandResult(true, listOf("${formatDate()} 스팟 $destSpot(으)로 이동했습니다."), message = msg)
    }
}

// ========== 반란 (Rebellion/Coup) - PCP 640 ==========
// 쿠데타 실행. CoupExecutionService 연동.

class 반란(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "반란"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val coupLeader = general.meta["coupLeader"] as? Boolean ?: false
        if (!coupLeader) return ConstraintResult.Fail("반의를 먼저 표명해야 합니다.")
        val coupStep = (general.meta["rebellionIntent"] as? Number)?.toInt() ?: 0
        if (coupStep < 1) return ConstraintResult.Fail("모의가 충분하지 않습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = personalMapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 100),
            "coupExecution" to mapOf(
                "leaderId" to general.id.toString(),
                "factionId" to general.factionId.toString(),
                "planetId" to general.planetId.toString(),
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 반란을 개시했습니다!"), message = msg)
    }
}
