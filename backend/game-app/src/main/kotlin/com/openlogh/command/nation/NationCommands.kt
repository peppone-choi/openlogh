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

class che_포상(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "포상"

    private val isGold: Boolean get() = arg?.get("isGold") as? Boolean ?: true
    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val label = if (isGold) "금" else "쌀"
        if (isGold) {
            dg.funds += amount
        } else {
            dg.supplies += amount
        }
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게 ${label} ${amount}을(를) 수여했습니다."),
        )
    }
}

class che_몰수(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "몰수"

    private val isGold: Boolean get() = arg?.get("isGold") as? Boolean ?: true
    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dg = destGeneral ?: return ConstraintResult.Fail("대상 장수가 없습니다")
        if (dg.nationId != general.nationId) return ConstraintResult.Fail("아군 장수가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val n = nation!!
        if (isGold) {
            val take = min(amount, dg.funds)
            dg.funds -= take
            n.funds += take
        } else {
            val take = min(amount, dg.supplies)
            dg.supplies -= take
            n.supplies += take
        }
        dg.betray = (dg.betray + 1).toShort()
        val label = if (isGold) "금" else "쌀"
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}에게서 ${label}을(를) 몰수했습니다."),
        )
    }
}

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
        c.popMax = max(10000, c.popMax - 10000)
        n.funds -= 500
        n.supplies -= 500
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${c.name}을(를) 감축했습니다."),
        )
    }
}

class che_증축(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "증축"

    private fun totalCost(): Int {
        val c = destCity ?: city ?: return 0
        return env.develCost * 500 + 60000
    }

    override fun getCost(): CommandCost {
        val cost = totalCost()
        return CommandCost(funds = cost, supplies = cost)
    }

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val cost = getCost()
        if (n.funds < cost.gold) return ConstraintResult.Fail("국고 자금이 부족합니다")
        if (n.supplies < cost.rice) return ConstraintResult.Fail("병량이 부족합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = destCity ?: city!!
        val n = nation!!
        val cost = getCost()
        c.level = (c.level + 1).toShort()
        c.popMax += 100000
        c.agriMax += 2000
        c.commMax += 2000
        c.secuMax += 2000
        c.defMax += 2000
        c.wallMax += 2000
        n.funds -= cost.gold
        n.supplies -= cost.rice
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${c.name}을(를) 증축했습니다."),
        )
    }
}

class che_발령(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "발령"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다")
        if (destCity == null) return ConstraintResult.Fail("목적지 도시가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val dc = destCity!!
        dg.cityId = dc.id
        dg.troopId = 0
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) ${dc.name}(으)로 발령했습니다."),
        )
    }
}

class che_천도(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "천도"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다")
        if (dc.nationId != general.nationId) return ConstraintResult.Fail("아군 도시가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val n = nation!!
        n.capitalCityId = dc.id
        n.funds -= 2000
        n.supplies -= 2000
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}(으)로 천도했습니다."),
        )
    }
}

class che_백성동원(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "백성동원"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.strategicCmdLimit > 0) return ConstraintResult.Fail("전략 명령 대기 중입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dc = destCity ?: city!!
        val popToRecruit = dc.pop / 5 * 4
        dc.pop -= popToRecruit

        n.strategicCmdLimit = 9

        // Create NPC soldiers
        val svc = services
        if (svc != null) {
            val npc1 = Officer(
                sessionId = general.sessionId,
                name = "의용군1",
                factionId = general.factionId,
                planetId = dc.id,
                ships = popToRecruit / 2,
                turnTime = java.time.OffsetDateTime.now(),
            )
            val npc2 = Officer(
                sessionId = general.sessionId,
                name = "의용군2",
                factionId = general.factionId,
                planetId = dc.id,
                ships = popToRecruit / 2,
                turnTime = java.time.OffsetDateTime.now(),
            )
            svc.generalRepository.save(npc1)
            svc.generalRepository.save(npc2)
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}에서 백성을 동원했습니다."),
        )
    }
}

class che_물자원조(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "물자원조"

    private val goldAmount: Int get() = (arg?.get("goldAmount") as? Number)?.toInt() ?: 0
    private val riceAmount: Int get() = (arg?.get("riceAmount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dn = destNation ?: return ConstraintResult.Fail("대상 국가가 없습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (dn.id == n.id) return ConstraintResult.Fail("같은 국가에 지원할 수 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        n.funds -= goldAmount
        n.supplies -= riceAmount
        dn.funds += goldAmount
        dn.supplies += riceAmount
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 물자를 지원했습니다."),
        )
    }
}

class che_국기변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "국기변경"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val colorType = arg?.get("colorType") as? String ?: n.color
        n.color = colorType
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 국기를 변경했습니다."),
        )
    }
}

class che_국호변경(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "국호변경"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val nationName = arg?.get("nationName") as? String ?: n.name
        n.name = nationName
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 국호를 ${nationName}(으)로 변경했습니다."),
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

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeCeasefire(general.worldId, n.id, dn.id)
        general.experience += 50
        general.dedication += 50
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
        general.experience += 50
        general.dedication += 50
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

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeNonAggression(general.worldId, n.id, dn.id)
        general.experience += 50
        general.dedication += 50
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
        general.experience += 50
        general.dedication += 50
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

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        services?.diplomacyService?.proposeBreakNonAggression(general.worldId, n.id, dn.id)
        general.experience += 50
        general.dedication += 50
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
        general.experience += 50
        general.dedication += 50
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}과(와) 불가침을 파기했습니다."),
        )
    }
}

// ========== Strategic Commands ==========

class che_급습(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "급습"

    override fun getPostReqTurn(): Int = 40

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        n.strategicCmdLimit = 9

        // Reduce all non-aggression treaty terms by 3
        val relations = services?.diplomacyService?.getRelationsForNation(general.worldId, dn.id) ?: emptyList()
        for (rel in relations) {
            if (rel.stateCode == "불가침") {
                rel.term = max(0, rel.term - 3).toShort()
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dn.name}에 급습을 실행했습니다."),
        )
    }
}

class che_수몰(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "수몰"

    override fun getPreReqTurn(): Int = 2
    override fun getPostReqTurn(): Int = 20

    override fun getConstraints(): List<Constraint> = listOf(
        BeLord(),
    )

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다")
        @Suppress("UNCHECKED_CAST")
        val atWarNationIds = constraintEnv["atWarNationIds"] as? Set<Long> ?: emptySet()
        if (dc.nationId !in atWarNationIds) return ConstraintResult.Fail("교전중인 국가의 도시가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dc = destCity!!
        n.strategicCmdLimit = 9
        dc.def = dc.def / 5
        dc.wall = dc.wall / 5
        val popLoss = dc.pop / 2
        dc.pop = dc.pop / 2
        dc.dead = dc.dead + popLoss / 10
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}에 수몰을 실행했습니다."),
        )
    }
}

class che_허보(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "허보"

    override fun getPreReqTurn(): Int = 1
    override fun getPostReqTurn(): Int = 20

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destCity == null) return ConstraintResult.Fail("목적지 도시가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dc = destCity!!
        n.strategicCmdLimit = 9

        val svc = services ?: return CommandResult(true, listOf("${formatDate()} 허보 실행."))

        // Move enemy generals (not troop leaders) to another city
        val cityGenerals = svc.generalRepository.findByCityId(dc.id)
        val enemyCities = svc.cityRepository.findByNationId(dc.nationId).filter { it.id != dc.id }
        val fallbackCity = enemyCities.firstOrNull()

        if (fallbackCity != null) {
            val enemyGenerals = cityGenerals.filter { it.nationId == dc.nationId }
            for (eg in enemyGenerals) {
                eg.cityId = fallbackCity.id
                if (eg.troopId != 0L && eg.troopId != eg.id) {
                    // Troop member - remove from troop
                    eg.troopId = 0
                }
                svc.generalRepository.save(eg)
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}에 허보를 실행했습니다."),
        )
    }
}

class che_초토화(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "초토화"

    override fun getPreReqTurn(): Int = 2
    override fun getPostReqTurn(): Int = 24

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다")
        if (dc.nationId != general.nationId) return ConstraintResult.Fail("아군 도시가 아닙니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        dc.agri = dc.agri / 2
        dc.comm = dc.comm / 2
        dc.pop = dc.pop / 2
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dc.name}을(를) 초토화했습니다."),
        )
    }
}

class che_필사즉생(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "필사즉생"

    override fun getPreReqTurn(): Int = 1
    override fun getPostReqTurn(): Int = 12

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.strategicCmdLimit > 0) return ConstraintResult.Fail("전략 명령 대기 중입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.strategicCmdLimit = 9
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 필사즉생을 선포했습니다."),
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

        // Find a third nation to provoke war between destNation and them
        val svc = services
        if (svc != null) {
            val allNations = svc.nationRepository.findByWorldId(general.worldId)
            val target = allNations.firstOrNull { it.id != n.id && it.id != dn.id && it.level > 0 }
            if (target != null) {
                svc.diplomacyService.declareWar(general.worldId, dn.id, target.id)
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 이호경식을 실행했습니다."),
        )
    }
}

class che_피장파장(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "피장파장"

    override fun getPreReqTurn(): Int = 1
    override fun getPostReqTurn(): Int = 8

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.strategicCmdLimit > 0) return ConstraintResult.Fail("전략 명령 대기 중입니다")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val dn = destNation!!
        n.strategicCmdLimit = 9
        dn.strategicCmdLimit = 9
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 피장파장을 실행했습니다."),
        )
    }
}

class che_의병모집(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "의병모집"

    override fun getPreReqTurn(): Int = 2
    override fun getPostReqTurn(): Int = 100

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val relYear = env.year - env.startYear
        if (relYear < 1) return ConstraintResult.Fail("오프닝 기간입니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val c = city!!
        val recruits = c.pop / 2
        c.pop -= recruits
        n.strategicCmdLimit = 9

        val svc = services
        if (svc != null) {
            repeat(3) { i ->
                val npc = Officer(
                    sessionId = general.sessionId,
                    name = "의병${i + 1}",
                    factionId = general.factionId,
                    planetId = c.id,
                    ships = recruits / 3,
                    turnTime = java.time.OffsetDateTime.now(),
                )
                svc.generalRepository.save(npc)
            }
        }

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 의병을 모집했습니다."),
        )
    }
}

// ========== Research Commands ==========

abstract class ResearchCommand(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    abstract val costAmount: Int
    abstract val preReq: Int
    abstract val nationMetaKey: String
    abstract val label: String

    override fun getCost(): CommandCost = CommandCost(gold = costAmount, rice = costAmount)
    override fun getPreReqTurn(): Int = preReq

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        val n = nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        if (n.funds <= costAmount) return ConstraintResult.Fail("국고 자금이 부족합니다")
        if (n.supplies <= costAmount) return ConstraintResult.Fail("병량이 부족합니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        n.funds -= costAmount
        n.supplies -= costAmount
        n.meta[nationMetaKey] = 1
        general.experience = 100
        general.dedication = 100
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${label} 완료!"),
        )
    }
}

class event_극병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "극병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_극병사용"
    override val label = "극병 연구"
}

class event_대검병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "대검병연구"
    override val costAmount = 50000
    override val preReq = 11
    override val nationMetaKey = "can_대검병사용"
    override val label = "대검병 연구"
}

class event_무희연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "무희연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_무희사용"
    override val label = "무희 연구"
}

class event_산저병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "산저병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_산저병사용"
    override val label = "산저병 연구"
}

class event_상병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "상병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_상병사용"
    override val label = "상병 연구"
}

class event_원융노병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "원융노병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_원융노병사용"
    override val label = "원융노병 연구"
}

class event_음귀병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "음귀병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_음귀병사용"
    override val label = "음귀병 연구"
}

class event_화륜차연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "화륜차연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_화륜차사용"
    override val label = "화륜차 연구"
}

class event_화시병연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : ResearchCommand(general, env, arg) {
    override val actionName = "화시병연구"
    override val costAmount = 100000
    override val preReq = 23
    override val nationMetaKey = "can_화시병사용"
    override val label = "화시병 연구"
}

// ========== Special Commands ==========

class che_무작위수도이전(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "무작위수도이전"

    override fun getPreReqTurn(): Int = 1

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val svc = services ?: return CommandResult(false, listOf("${formatDate()} 서비스 없음"))

        @Suppress("UNCHECKED_CAST")
        val neutralCities = env.gameStor["neutralCities"] as? List<*> ?: emptyList<Any>()
        val targetCityId = (neutralCities.firstOrNull() as? Number)?.toLong()
            ?: return CommandResult(false, listOf("${formatDate()} 이전 가능한 도시가 없습니다."))

        val targetCity = svc.cityRepository.findById(targetCityId).orElse(null)
            ?: return CommandResult(false, listOf("${formatDate()} 도시를 찾을 수 없습니다."))

        targetCity.nationId = general.nationId
        n.capitalCityId = targetCity.id
        svc.cityRepository.save(targetCity)

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${targetCity.name}(으)로 국가를 옮겼습니다."),
        )
    }
}

class che_부대탈퇴지시(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "부대탈퇴지시"

    override fun checkFullCondition(): ConstraintResult {
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        dg.troopId = 0
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} ${dg.name}을(를) 부대에서 탈퇴시켰습니다."),
        )
    }
}

class cr_인구이동(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "인구이동"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun getCost(): CommandCost {
        val cost = amount / 100
        return CommandCost(funds = cost, supplies = cost)
    }

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        if (destCity == null) return ConstraintResult.Fail("목적지 도시가 없습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        val c = city!!
        val dc = destCity!!
        val cost = getCost()
        n.funds -= cost.gold
        n.supplies -= cost.rice
        c.pop -= amount
        dc.pop += amount
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 인구 ${amount}명을 이동시켰습니다."),
        )
    }
}

// ========== Additional Nation Commands (to reach 43 total) ==========

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

class che_외교초기화(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : NationCommand(general, env, arg) {
    override val actionName = "외교초기화"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val n = nation!!
        services?.diplomacyService?.killAllRelationsForNation(general.worldId, n.id)
        return CommandResult(true, listOf("${formatDate()} 외교 관계를 초기화했습니다."))
    }
}
