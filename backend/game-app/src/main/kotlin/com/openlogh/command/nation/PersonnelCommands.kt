@file:Suppress("ClassName", "unused")

package com.openlogh.command.nation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.organization.CommandGating
import com.openlogh.engine.organization.PositionCardType
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.random.Random

private val personnelMapper = jacksonObjectMapper()

// Peerage order for Empire (ascending rank)
private val NOBLE_RANKS = listOf("knight", "baron", "viscount", "count", "marquis", "duke", "grand_duke")

private fun peerageKo(code: String): String = when (code) {
    "knight" -> "제국기사"
    "baron" -> "남작"
    "viscount" -> "자작"
    "count" -> "백작"
    "marquis" -> "후작"
    "duke" -> "공작"
    "grand_duke" -> "대공"
    else -> code
}

// ========== 발탁 (Special Promotion) PCP 320 ==========
// Ladder-skip promotion; costs influence

class 발탁(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "발탁"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        dg.rank = (dg.rank + 1).toShort()
        dg.experience = 0
        dg.betray = (dg.betray + 1).toShort()
        general.influence = max(0, general.influence - 10)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) 특별 발탁했습니다."),
        )
    }
}

// ========== 강등 (Demotion) PCP 160 ==========
// Demote rank; reset dedication (공적)

class 강등(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "강등"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (dg.rank <= 0.toShort()) return ConstraintResult.Fail("이미 최하위 계급입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        dg.rank = (dg.rank - 1).toShort()
        dg.dedication = 0
        dg.betray = (dg.betray + 2).toShort()
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) 강등했습니다."),
        )
    }
}

// ========== 서작 (Peerage Grant) PCP 320 — Empire only ==========
// Grant noble title (제국기사 ~ 대공)

class 서작(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "서작"

    private val peerageCode: String get() = arg?.get("peerageCode") as? String ?: "knight"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.factionType != "empire") return ConstraintResult.Fail("제국 전용 커맨드입니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (peerageCode !in NOBLE_RANKS) return ConstraintResult.Fail("유효하지 않은 작위 코드입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        dg.peerage = peerageCode
        general.influence = max(0, general.influence - 5)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게 ${peerageKo(peerageCode)} 작위를 수여했습니다."),
        )
    }
}

// ========== 서훈 (Medal Award) PCP 160 ==========
// Award medal: +experience, -betray

class 서훈(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "서훈"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        dg.experience += 100
        dg.betray = max(0, dg.betray - 1).toShort()
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게 훈장을 수여했습니다."),
        )
    }
}

// ========== 사임 (Resignation) PCP 0 ==========
// Resign from current position (clears fleet assignment)

class 사임(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "사임"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        general.fleetId = 0
        general.dedication = 0
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${general.name}이(가) 직무에서 사임했습니다."),
        )
    }
}

// ========== 봉토수여 (Fief Grant) PCP 640 — Empire only ==========
// Grant a planet as fief to a baron+ officer; sets planet.fiefOfficerId

class 봉토수여(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "봉토수여"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.factionType != "empire") return ConstraintResult.Fail("제국 전용 커맨드입니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (dg.peerage == "none" || dg.peerage == "knight") return ConstraintResult.Fail("남작 이상 작위가 필요합니다")
        val dc = destCity ?: return ConstraintResult.Fail("봉토로 지정할 행성이 없습니다")
        if (dc.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다")
        if (dc.fiefOfficerId != null) return ConstraintResult.Fail("이미 봉토로 지정된 행성입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val dc = destCity!!
        dc.fiefOfficerId = dg.id
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}을(를) ${dg.name}의 봉토로 수여했습니다."),
        )
    }
}

// ========== 봉토직할 (Fief Reclaim) PCP 640 — Empire only ==========
// Reclaim fief back to direct imperial control; clears planet.fiefOfficerId

class 봉토직할(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "봉토직할"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.factionType != "empire") return ConstraintResult.Fail("제국 전용 커맨드입니다")
        val dc = destCity ?: return ConstraintResult.Fail("대상 행성이 없습니다")
        if (dc.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다")
        if (dc.fiefOfficerId == null) return ConstraintResult.Fail("봉토로 지정된 행성이 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        dc.fiefOfficerId = null
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}을(를) 직할령으로 전환했습니다."),
        )
    }
}

// ========== 승진 (Promotion) PCP 160 ==========
// 인사권자가 실행. 1계급 상승, 공적 0 리셋. RankLadderService 연동.

class 승진(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "승진"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (dg.rank >= 10.toShort()) return ConstraintResult.Fail("이미 최고 계급입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val prevRank = dg.rank.toInt()
        dg.rank = (prevRank + 1).toShort()
        dg.experience = 0 // 공적 리셋
        val msg = personnelMapper.writeValueAsString(mapOf(
            "promotion" to mapOf(
                "officerId" to dg.id.toString(),
                "fromRank" to prevRank,
                "toRank" to prevRank + 1,
            ),
        ))
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) 승진시켰습니다. (계급 $prevRank → ${prevRank + 1})"),
            message = msg,
        )
    }
}

// ========== 임명 (Appointment — Position Card Grant) PCP 160 ==========
// 직무카드 부여. PositionCardSystem 연동.

class 임명(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "임명"

    private val positionCode: String get() = arg?.get("positionCode") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (positionCode.isEmpty()) return ConstraintResult.Fail("임명할 직무 코드가 없습니다")
        val cardType = PositionCardType.fromCode(positionCode)
            ?: return ConstraintResult.Fail("유효하지 않은 직무 코드입니다: $positionCode")
        if (dg.rank < cardType.minRank) {
            return ConstraintResult.Fail("계급이 부족합니다. (필요: ${cardType.minRank}, 현재: ${dg.rank})")
        }
        @Suppress("UNCHECKED_CAST")
        val currentCards = (dg.meta["positionCards"] as? List<*>)?.size ?: 2
        if (!CommandGating.canAddCard(currentCards)) {
            return ConstraintResult.Fail("직무카드 보유 한도(${CommandGating.MAX_CARDS_PER_OFFICER})를 초과합니다")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        @Suppress("UNCHECKED_CAST")
        val cards = (dg.meta["positionCards"] as? MutableList<String>)
            ?: mutableListOf("personal", "captain").also { dg.meta["positionCards"] = it }
        if (positionCode !in cards) {
            cards.add(positionCode)
        }
        val cardType = PositionCardType.fromCode(positionCode)!!
        val msg = personnelMapper.writeValueAsString(mapOf(
            "appointment" to mapOf(
                "officerId" to dg.id.toString(),
                "positionCode" to positionCode,
                "positionName" to cardType.displayName,
            ),
        ))
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) ${cardType.displayName}(으)로 임명했습니다."),
            message = msg,
        )
    }
}

// ========== 파면 (Dismissal — Position Card Revoke) PCP 160 ==========
// 직무카드 박탈.

class 파면(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "파면"

    private val positionCode: String get() = arg?.get("positionCode") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        if (positionCode.isEmpty()) return ConstraintResult.Fail("파면할 직무 코드가 없습니다")
        @Suppress("UNCHECKED_CAST")
        val cards = (dg.meta["positionCards"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        if (positionCode !in cards) return ConstraintResult.Fail("해당 직무를 보유하고 있지 않습니다")
        if (positionCode == "personal" || positionCode == "captain") {
            return ConstraintResult.Fail("기본 카드(개인/함장)는 파면할 수 없습니다")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        @Suppress("UNCHECKED_CAST")
        val cards = (dg.meta["positionCards"] as? MutableList<String>) ?: return CommandResult(
            success = false,
            logs = listOf("${formatDate()} 직무카드 정보가 없습니다."),
        )
        cards.remove(positionCode)
        val cardType = PositionCardType.fromCode(positionCode)
        val displayName = cardType?.displayName ?: positionCode
        dg.betray = (dg.betray + 1).toShort()
        val msg = personnelMapper.writeValueAsString(mapOf(
            "dismissal" to mapOf(
                "officerId" to dg.id.toString(),
                "positionCode" to positionCode,
                "positionName" to displayName,
            ),
        ))
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) ${displayName} 직에서 파면했습니다."),
            message = msg,
        )
    }
}
