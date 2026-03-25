@file:Suppress("ClassName", "unused")

package com.openlogh.command.nation

import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.espionage.ArrestAuthorityService
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// ========== 국가목표설정 (Set National Goal) PCP 320 ==========
// Sets the faction's strategic goal in meta

class 국가목표설정(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "국가목표설정"

    private val goal: String get() = arg?.get("goal") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (goal.isBlank()) return ConstraintResult.Fail("목표를 입력해야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["strategicGoal"] = goal
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 국가 전략 목표를 설정했습니다: $goal"),
        )
    }
}

// ========== 납입률변경 (Change Deposit Rate) PCP 160 ==========
// Changes the faction's internal tribute/deposit rate (stored in meta)

class 납입률변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "납입률변경"

    private val rate: Int get() = (arg?.get("rate") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (rate !in 0..100) return ConstraintResult.Fail("납입률은 0~100 사이여야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["depositRate"] = rate
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 납입률을 ${rate}%로 변경했습니다."),
        )
    }
}

// ========== 관세율변경 (Change Customs Rate) PCP 160 ==========
// Changes the faction's customs/tariff rate on trade routes (stored in meta)

class 관세율변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "관세율변경"

    private val rate: Int get() = (arg?.get("rate") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (nation == null) return ConstraintResult.Fail("국가 정보가 없습니다")
        if (rate !in 0..100) return ConstraintResult.Fail("관세율은 0~100 사이여야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["customsRate"] = rate
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 관세율을 ${rate}%로 변경했습니다."),
        )
    }
}

// ========== 분배 (Resource Distribution) PCP 320 ==========
// Distribute faction funds/supplies to a target officer

class 분배(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "분배"

    private val fundsAmount: Int get() = (arg?.get("fundsAmount") as? Number)?.toInt() ?: 0
    private val suppliesAmount: Int get() = (arg?.get("suppliesAmount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (fundsAmount > n.funds) return ConstraintResult.Fail("국고 자금이 부족합니다")
        if (suppliesAmount > n.supplies) return ConstraintResult.Fail("국가 물자가 부족합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dg = destGeneral!!
        if (fundsAmount > 0) {
            val take = min(fundsAmount, n.funds)
            n.funds -= take
            dg.funds += take
        }
        if (suppliesAmount > 0) {
            val take = min(suppliesAmount, n.supplies)
            n.supplies -= take
            dg.supplies += take
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게 자원을 분배했습니다."),
        )
    }
}

// ========== 처단 (Execute/Exile) PCP 640 ==========
// Execute or exile an arrested officer (blockState > 0)

class 처단(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "처단"

    private val isExecute: Boolean get() = arg?.get("isExecute") as? Boolean ?: true

    override fun checkFullCondition(): ConstraintResult {
        // ArrestAuthorityService: authority check (gin7 4.8, 9.9)
        // Sovereign (rank 20+) can always execute; others need arrest authority role
        if (general.officerLevel < 20.toShort()) {
            // Check if officer has arrest authority role (헌병총감, 내무상서, etc.)
            val authorityRoles = setOf(
                ArrestAuthorityService.ROLE_MILITARY_POLICE_CHIEF,
                ArrestAuthorityService.ROLE_INTERIOR_MINISTER,
                ArrestAuthorityService.ROLE_JUSTICE_MINISTER,
                ArrestAuthorityService.ROLE_MP_COMMANDER,
                ArrestAuthorityService.ROLE_LAW_ORDER_CHAIR,
            )
            val hasAuthority = general.personalCode in authorityRoles ||
                general.specialCode in authorityRoles ||
                general.special2Code in authorityRoles
            if (!hasAuthority) return ConstraintResult.Fail("처단 권한이 없습니다 (군주급 또는 체포 권한 보유자만 가능)")
        }
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.blockState <= 0.toShort()) return ConstraintResult.Fail("체포된 인물이 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val label: String
        if (isExecute) {
            dg.meta["terminated"] = "executed"
            dg.blockState = -1
            dg.npcState = -1
            label = "처형"
        } else {
            dg.factionId = 0
            dg.fleetId = 0
            dg.blockState = 0
            dg.betray = (dg.betray + 5).toShort()
            label = "추방"
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) ${label}했습니다."),
        )
    }
}

// ========== 외교 (Diplomacy) PCP 320 ==========
// Unified diplomacy command: declare war / propose ceasefire / propose non-aggression

class 외교(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "외교"

    private val diplomacyType: String get() = arg?.get("diplomacyType") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        if (diplomacyType !in listOf("war", "ceasefire", "nonaggression")) {
            return ConstraintResult.Fail("유효한 외교 유형을 지정해야 합니다 (war/ceasefire/nonaggression)")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        val svc = services?.diplomacyService

        val label = when (diplomacyType) {
            "war" -> {
                svc?.declareWar(general.worldId, n.id, dn.id)
                "선전포고"
            }
            "ceasefire" -> {
                svc?.proposeCeasefire(general.worldId, n.id, dn.id)
                "종전 제의"
            }
            "nonaggression" -> {
                svc?.proposeNonAggression(general.worldId, n.id, dn.id)
                "불가침 제의"
            }
            else -> diplomacyType
        }

        general.experience += 50
        general.dedication += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 ${label}했습니다."),
        )
    }
}

// ========== 통치목표 (Governance Goal) PCP 160 ==========
// Set planet-level governance goal in planet.meta

class 통치목표(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "통치목표"

    private val goal: String get() = arg?.get("goal") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dc = destCity ?: return ConstraintResult.Fail("대상 행성이 없습니다")
        if (dc.factionId != general.factionId) return ConstraintResult.Fail("아군 행성이 아닙니다")
        if (goal.isBlank()) return ConstraintResult.Fail("통치 목표를 입력해야 합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        dc.meta["governanceGoal"] = goal
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}의 통치 목표를 설정했습니다: $goal"),
        )
    }
}

// ========== 예산편성 (Budget Planning) PCP 320 ==========
// 국가 예산 관리. 부문별 예산 배분.

class 예산편성(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "예산편성"

    private val militaryPct: Int get() = (arg?.get("militaryPct") as? Number)?.toInt() ?: 40
    private val economyPct: Int get() = (arg?.get("economyPct") as? Number)?.toInt() ?: 30
    private val welfarePct: Int get() = (arg?.get("welfarePct") as? Number)?.toInt() ?: 30

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val total = militaryPct + economyPct + welfarePct
        if (total != 100) return ConstraintResult.Fail("예산 배분 합계가 100%여야 합니다 (현재: $total%)")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.meta["budget_military"] = militaryPct
        n.meta["budget_economy"] = economyPct
        n.meta["budget_welfare"] = welfarePct
        general.experience += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 예산을 편성했습니다. (군사: ${militaryPct}%, 경제: ${economyPct}%, 복지: ${welfarePct}%)"),
        )
    }
}

// ========== 제안공작 (Forced Proposal) PCP 320 ==========
// 정치공작 1,000 소모하여 제안 강제 수락.

class 제안공작(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "제안공작"

    private val proposalContent: String get() = arg?.get("proposalContent") as? String ?: ""

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.factionId != general.factionId) return ConstraintResult.Fail("같은 국가 소속이어야 합니다")
        if (general.politicalOps < 1000) return ConstraintResult.Fail("정치공작이 부족합니다 (필요: 1000, 보유: ${general.politicalOps})")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        general.politicalOps -= 1000
        general.experience += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 제안 공작으로 ${dg.name}에게 제안을 강제 수락시켰습니다. (정치공작 -1000)"),
        )
    }
}
