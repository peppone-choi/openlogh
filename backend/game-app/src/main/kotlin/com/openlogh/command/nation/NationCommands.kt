@file:Suppress("ClassName", "unused")

package com.openlogh.command.nation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.*
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val mapper = jacksonObjectMapper()

// ========== Default ==========

class Nation휴식(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "휴식"

    override suspend fun run(rng: Random): CommandResult {
        return CommandResult(success = true, logs = emptyList())
    }
}

// ========== Resource Commands ==========

class che_감축(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "감축"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        if (c.level <= 1.toShort()) return ConstraintResult.Fail("이미 최소 등급입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city!!
        val n = nation!!
        c.level = (c.level - 1).toShort()
        // Reduce ALL 6 planet stats and their maxes
        c.populationMax = max(10000, c.populationMax - 10000)
        c.population = min(c.population, c.populationMax)
        c.productionMax = max(0, c.productionMax - 2000)
        c.production = min(c.production, c.productionMax)
        c.commerceMax = max(0, c.commerceMax - 2000)
        c.commerce = min(c.commerce, c.commerceMax)
        c.securityMax = max(0, c.securityMax - 2000)
        c.security = min(c.security, c.securityMax)
        c.orbitalDefenseMax = max(0, c.orbitalDefenseMax - 2000)
        c.orbitalDefense = min(c.orbitalDefense, c.orbitalDefenseMax)
        c.fortressMax = max(0, c.fortressMax - 2000)
        c.fortress = min(c.fortress, c.fortressMax)
        // Refund cost to faction
        n.funds += 500
        n.supplies += 500
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${c.name}을(를) 감축했습니다."),
        )
    }
}

class che_백성동원(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "백성동원"

    override fun getPreReqTurn(): Int = 1

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.strategicCmdLimit > 0) return ConstraintResult.Fail("전략 명령 대기 중입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dc = destCity ?: city!!

        // Boost orbital defense and fortress to 80% of max (no pop reduction)
        dc.orbitalDefense = max((dc.orbitalDefenseMax * 0.8).toInt(), dc.orbitalDefense)
        dc.fortress = max((dc.fortressMax * 0.8).toInt(), dc.fortress)

        n.strategicCmdLimit = 9

        // Scaled exp/ded: +5 * (getPreReqTurn() + 1)
        val expGain = 5 * (getPreReqTurn() + 1)
        general.experience += expGain
        general.dedication += expGain

        // Broadcast to faction officers via meta log entry
        val svc = services
        if (svc != null) {
            val factionOfficers = svc.generalRepository.findByNationId(general.factionId)
            for (officer in factionOfficers) {
                if (officer.id != general.id) {
                    officer.meta["lastBroadcast"] = "${general.name}이(가) ${dc.name}에서 백성동원을 실행했습니다."
                    svc.generalRepository.save(officer)
                }
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}에서 백성동원을 실행했습니다."),
        )
    }
}

// ========== Diplomacy Commands ==========

class che_선전포고(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "선전포고"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        val relYear = env.year - env.startYear
        if (relYear < 1) return ConstraintResult.Fail("오프닝 기간입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.declareWar(general.worldId, n.id, dn.id)
        general.experience += 50
        general.dedication += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 선전포고했습니다."),
        )
    }
}

class che_종전제의(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "종전제의"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeCeasefire(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 종전을 제의했습니다."),
        )
    }
}

class che_종전수락(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "종전수락"

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.acceptCeasefire(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}과(와) 종전을 수락했습니다."),
        )
    }
}

class che_불가침제의(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "불가침제의"

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeNonAggression(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 불가침을 제의했습니다."),
        )
    }
}

class che_불가침수락(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "불가침수락"

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        if (c.nationId != general.nationId) return ConstraintResult.Fail("아군 도시가 아닙니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.acceptNonAggression(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}과(와) 불가침을 수락했습니다."),
        )
    }
}

class che_불가침파기제의(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "불가침파기제의"

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeBreakNonAggression(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 불가침파기를 제의했습니다."),
        )
    }
}

class che_불가침파기수락(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "불가침파기수락"

    override fun getConstraints(): List<Constraint> = listOf(AllowDiplomacy(20))

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.acceptBreakNonAggression(general.worldId, n.id, dn.id)
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}과(와) 불가침을 파기했습니다."),
        )
    }
}

class che_이호경식(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "이호경식"

    override fun getPostReqTurn(): Int = 126

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        n.strategicCmdLimit = 9

        // C: Set diplomacy state=선전포고 (declared), adjust term
        val svc = services
        if (svc != null) {
            val sessionId = general.worldId
            val existing = svc.diplomacyService.getRelationsForNation(sessionId, dn.id)
                .filter { rel ->
                    (rel.srcFactionId == n.id && rel.destFactionId == dn.id) ||
                    (rel.srcFactionId == dn.id && rel.destFactionId == n.id)
                }
            val currentState = existing.firstOrNull { !it.isDead }
            if (currentState == null || currentState.stateCode == "평화") {
                // state==0 (peace): term = 3
                svc.diplomacyService.createRelation(sessionId, n.id, dn.id, "선전포고", 3)
            } else {
                // state already exists: term = term + 3
                val newTerm = (currentState.term + 3).toInt()
                svc.diplomacyService.createRelation(sessionId, n.id, dn.id, "선전포고", newTerm)
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 이호경식을 실행했습니다."),
        )
    }
}

// ========== Administration Commands ==========

class che_세율변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "세율변경"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val rate = (arg?.get("rate") as? Number)?.toShort() ?: n.taxRate
        n.taxRate = rate
        return CommandResult(true, listOf("${formatDate()} 세율을 변경했습니다."))
    }
}

class che_징병률변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "징병률변경"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val rate = (arg?.get("rate") as? Number)?.toShort() ?: n.conscriptionRate
        n.conscriptionRate = rate
        return CommandResult(true, listOf("${formatDate()} 징병률을 변경했습니다."))
    }
}

class che_국가해산(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "국가해산"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("disbandNation" to true))
        return CommandResult(true, listOf("${formatDate()} 국가를 해산했습니다."), message = msg)
    }
}

class che_항복(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "항복"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dn = destNation!!
        val msg = mapper.writeValueAsString(mapOf("surrenderTo" to dn.id))
        return CommandResult(true, listOf("${formatDate()} ${dn.name}에 항복했습니다."), message = msg)
    }
}
