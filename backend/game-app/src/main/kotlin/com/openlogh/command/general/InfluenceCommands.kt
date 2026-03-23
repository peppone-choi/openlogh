@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val mapper = jacksonObjectMapper()

// ========== 야회 (Banquet) PCP 320 ==========
// ±influence based on attendees' responses

class 야회(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "야회"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val stat = general.politics.toInt()
        val base = (stat * 0.2 * (0.7 + rng.nextDouble() * 0.6)).toInt()
        val delta = if (rng.nextDouble() < 0.7) base else -base / 2
        general.influence = max(0, general.influence + delta)
        general.experience += base / 2

        val resultLabel = when {
            delta > 0 -> "성공적으로 개최되어 영향력이 증가했습니다."
            delta < 0 -> "분위기가 좋지 않아 영향력이 감소했습니다."
            else -> "무난하게 마무리되었습니다."
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>야회</C>가 $resultLabel"),
        )
    }
}

// ========== 수렵 (Hunting Party) PCP 320 ==========
// Fief planet only; +friendship with co-located officers, +influence

class 수렵(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "수렵"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.fiefOfficerId != general.id) return ConstraintResult.Fail("봉토 영주만 사용할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val stat = general.politics.toInt()
        val influenceDelta = (stat * 0.15 * (0.8 + rng.nextDouble() * 0.4)).toInt()
        general.influence = max(0, general.influence + influenceDelta)
        general.experience += influenceDelta

        val friendshipDelta = (10 + rng.nextInt(10))
        val dg = destGeneral
        val friendshipMsg = if (dg != null) {
            mapper.writeValueAsString(mapOf(
                "friendshipChanges" to mapOf(
                    "officerAId" to minOf(general.id, dg.id),
                    "officerBId" to maxOf(general.id, dg.id),
                    "delta" to friendshipDelta,
                )
            ))
        } else null

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>수렵</C>을 통해 영향력이 증가했습니다."),
            message = friendshipMsg,
        )
    }
}

// ========== 회담 (Conference) PCP 160 ==========
// ±influence

class 회담(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "회담"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val stat = general.politics.toInt()
        val base = (stat * 0.12 * (0.7 + rng.nextDouble() * 0.6)).toInt()
        val delta = if (rng.nextDouble() < 0.65) base else -base / 2
        general.influence = max(0, general.influence + delta)
        general.experience += base / 2

        val resultLabel = if (delta >= 0) "영향력이 증가했습니다." else "영향력이 감소했습니다."
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}과(와) <C>회담</C>으로 $resultLabel"),
        )
    }
}

// ========== 담화 (Dialogue) PCP 80 ==========
// ±friendship ±influence

class 담화(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "담화"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val stat = (general.politics.toInt() + general.administration.toInt()) / 2
        val base = (stat * 0.08 * (0.7 + rng.nextDouble() * 0.6)).toInt()

        val influenceDelta = if (rng.nextDouble() < 0.6) base else -base / 3
        general.influence = max(0, general.influence + influenceDelta)
        general.experience += base / 2

        val friendshipDelta = if (rng.nextDouble() < 0.7) (5 + rng.nextInt(8)) else -(1 + rng.nextInt(3))
        val msg = mapper.writeValueAsString(mapOf(
            "friendshipChanges" to mapOf(
                "officerAId" to minOf(general.id, dg.id),
                "officerBId" to maxOf(general.id, dg.id),
                "delta" to friendshipDelta,
            )
        ))

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}과(와) <C>담화</C>를 나눴습니다."),
            message = msg,
        )
    }
}

// ========== 연설 (Speech) PCP 160 ==========
// ±influence, ±planet approval

class 연설(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "연설"

    override fun checkFullCondition(): ConstraintResult {
        if (general.nationId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("행성 정보가 없습니다.")
        if (c.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val stat = general.politics.toInt()
        val base = (stat * 0.12 * (0.7 + rng.nextDouble() * 0.6)).toInt()

        val influenceDelta = if (rng.nextDouble() < 0.65) base else -base / 3
        general.influence = max(0, general.influence + influenceDelta)
        general.experience += base / 2

        val approvalDelta = if (influenceDelta >= 0) {
            (base * 0.05f).coerceAtMost(5f)
        } else {
            -(base * 0.02f).coerceAtMost(2f)
        }
        c.approval = (c.approval + approvalDelta).coerceIn(0f, 100f)

        val resultLabel = if (influenceDelta >= 0) "성공적인 연설로 민심과 영향력이 향상되었습니다."
        else "연설이 반감을 사 영향력이 감소했습니다."

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>연설</C>: $resultLabel"),
        )
    }
}
