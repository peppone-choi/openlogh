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
        city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
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
