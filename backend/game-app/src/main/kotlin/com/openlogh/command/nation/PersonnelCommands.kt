@file:Suppress("ClassName", "unused")

package com.openlogh.command.nation

import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.random.Random

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
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
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
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
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
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
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
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
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
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        general.troopId = 0
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
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
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
