@file:Suppress("ClassName", "unused")

package com.openlogh.command.general

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.*
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.engine.espionage.EspionageEngine
import com.openlogh.entity.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private val mapper = jacksonObjectMapper()

// ========== Helper base for domestic commands ==========

abstract class DomesticCommand(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {

    abstract val cityKey: String
    abstract val statKey: String
    open val debuffFront: Double = 0.5

    override fun getCost(): CommandCost = CommandCost(funds =env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        if (general.funds < getCost().funds) return ConstraintResult.Fail("자금이 부족합니다.")

        val (current, maxVal) = getCityValues(c)
        if (current >= maxVal) return ConstraintResult.Fail("이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    private fun getCityValues(c: City): Pair<Int, Int> = when (cityKey) {
        "agri" -> c.agri to c.agriMax
        "comm" -> c.comm to c.commMax
        "secu" -> c.secu to c.secuMax
        "def" -> c.def to c.defMax
        "wall" -> c.wall to c.wallMax
        "pop" -> c.pop to c.popMax
        else -> 0 to 0
    }

    private fun getStatValue(): Int = when (statKey) {
        "leadership" -> general.leadership.toInt()
        "command", "strength" -> general.command.toInt()
        "intelligence", "intel" -> general.intelligence.toInt()
        "politics" -> general.politics.toInt()
        "administration", "charm" -> general.administration.toInt()
        else -> 50
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city ?: return CommandResult(false, listOf("${formatDate()} 도시 정보 없음"))
        val (current, maxVal) = getCityValues(c)
        val stat = getStatValue()
        val trust = max(50f, c.trust).toDouble()

        var score = (stat * (trust / 100.0) * (0.8 + rng.nextDouble() * 0.4)).toInt()
        score = max(1, score)
        val politicsBonus = 1.0 + (general.politics - 50) / 500.0
        score = (score * politicsBonus).toInt()
        score = max(1, score)

        // Critical ratio
        val avg = (general.leadership + general.command + general.intelligence) / 3.0
        val statVal = getStatValue().toDouble()
        val ratio = min(avg / statVal, 1.2)
        var failRatio = (Math.pow(ratio / 1.2, 1.4) - 0.3).coerceIn(0.0, 0.5)
        var successRatio = (Math.pow(ratio / 1.2, 1.5) - 0.25).coerceIn(0.0, 0.5)
        if (trust < 80.0) successRatio *= trust / 80.0
        successRatio = successRatio.coerceIn(0.0, 1.0)
        failRatio = failRatio.coerceIn(0.0, 1.0 - successRatio)

        val roll = rng.nextDouble()
        val pick = when {
            roll < failRatio -> "fail"
            roll < failRatio + successRatio -> "success"
            else -> "normal"
        }

        val critMul = when (pick) {
            "success" -> 2.2 + rng.nextDouble() * 0.8
            "fail" -> 0.2 + rng.nextDouble() * 0.2
            else -> 1.0
        }
        score = (score * critMul).toInt()
        score = max(1, score)

        val fs = c.frontState.toInt()
        if (fs == 1 || fs == 3) score = (score * debuffFront).toInt()

        // Apply modifier if available
        services?.modifierService?.let { mod ->
            val ctx = mapOf("command" to actionName, "general" to general, "score" to score, "cityKey" to cityKey, "statKey" to statKey)
            val modResult = mod.applyDomesticScoreModifier(general, ctx)
            if (modResult != null) score = modResult
        }

        val newVal = min(maxVal, current + score)
        val delta = newVal - current

        val statExpKey = when (statKey) {
            "leadership" -> "leadershipExp"
            "command", "strength" -> "commandExp"
            "intelligence", "intel" -> "intelligenceExp"
            "politics" -> "politicsExp"
            "administration", "charm" -> "administrationExp"
            else -> "intelligenceExp"
        }
        val exp = (score * 0.7).toInt()

        val statChanges = mutableMapOf<String, Any>(
            "funds" to -getCost().funds,
            "experience" to exp,
            "dedication" to score,
            statExpKey to 1,
        )
        val cityChanges = mutableMapOf<String, Any>(cityKey to delta)

        val label = when (cityKey) {
            "agri" -> "농지 개간"
            "comm" -> "상업 투자"
            "secu" -> "치안 강화"
            "def" -> "수비 강화"
            "wall" -> "성벽 보수"
            "pop" -> "정착 장려"
            else -> actionName
        }

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to statChanges,
            "cityChanges" to cityChanges,
            "criticalResult" to pick,
        ))

        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} <C>$label</C> ${delta}만큼 증가시켰습니다."),
            message = msg,
        )
    }

}

// ========== 휴식 (Rest) ==========

class 휴식(
    general: General,
    env: CommandEnv,
    arg: Map<String, Any>? = null,
) : BaseCommand(general, env, arg) {
    override val actionName = "휴식"

    override suspend fun run(rng: Random): CommandResult {
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 아무것도 실행하지 않았습니다."),
        )
    }
}

// ========== Domestic Commands ==========

class che_농지개간(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "농지개간"
    override val cityKey = "agri"
    override val statKey = "intelligence"
}

class che_상업투자(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "상업투자"
    override val cityKey = "comm"
    override val statKey = "intelligence"
}

class che_치안강화(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "치안강화"
    override val cityKey = "secu"
    override val statKey = "command"
    override val debuffFront = 1.0
}

class che_수비강화(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "수비강화"
    override val cityKey = "def"
    override val statKey = "command"
}

class che_성벽보수(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "성벽보수"
    override val cityKey = "wall"
    override val statKey = "command"
    override val debuffFront = 0.25
}

class che_정착장려(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : DomesticCommand(general, env, arg) {
    override val actionName = "정착장려"
    override val cityKey = "pop"
    override val statKey = "leadership"

    override fun getCost(): CommandCost = CommandCost(supplies =env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        if (general.supplies < getCost().supplies) return ConstraintResult.Fail("군량이 부족합니다.")
        if (c.pop >= c.popMax) return ConstraintResult.Fail("이미 최대치입니다.")
        return ConstraintResult.Pass
    }
}

class che_주민선정(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "주민선정"

    override fun getCost(): CommandCost = CommandCost(supplies =env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.trust >= 100f) return ConstraintResult.Fail("이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val score = (general.administration.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val c = city!!
        val newTrust = min(100f, c.trust + score * 0.1f)
        val delta = newTrust - c.trust

        // Critical ratio
        val critRoll = rng.nextDouble()
        val pick = when {
            critRoll < 0.2 -> "fail"
            critRoll > 0.8 -> "success"
            else -> "normal"
        }

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to -getCost().supplies, "experience" to score, "dedication" to score, "administrationExp" to 1),
            "cityChanges" to mapOf("trust" to delta),
            "criticalResult" to pick,
        ))
        return CommandResult(true, listOf("${formatDate()} <C>주민 선정</C>으로 민심이 향상되었습니다."), message = msg)
    }
}

class che_기술연구(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "기술연구"

    override fun getCost(): CommandCost = CommandCost(funds =env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.funds < getCost().funds) return ConstraintResult.Fail("자금이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val score = (general.intelligence.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val techDelta = if (env.isTechLimited(nation?.techLevel?.toDouble() ?: 0.0)) {
            (score / 4).toDouble() / 10.0
        } else {
            score.toDouble() / 10.0
        }

        val critRoll = rng.nextDouble()
        val pick = when {
            critRoll < 0.2 -> "fail"
            critRoll > 0.8 -> "success"
            else -> "normal"
        }

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("funds" to -getCost().funds, "experience" to score, "dedication" to score, "intelligenceExp" to 1),
            "nationChanges" to mapOf("tech" to techDelta),
            "criticalResult" to pick,
        ))
        return CommandResult(true, listOf("${formatDate()} 기술 연구를 수행했습니다."), message = msg)
    }
}

// ========== Military Commands (Recruitment/Training) ==========

class che_모병(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "모병"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0
    private val crewType: Int get() = (arg?.get("crewType") as? Number)?.toInt() ?: 0

    private fun maxCrew(): Int {
        val cap = general.leadership.toInt() * 100
        val currentCrew = if (general.shipClass.toInt() == crewType) general.ships else 0
        return min(amount, max(0, cap - currentCrew))
    }

    override fun getCost(): CommandCost {
        val mc = maxCrew()
        val baseCost = mc / 10
        var cost = CommandCost(funds = baseCost * 2, supplies = baseCost / 10)
        services?.modifierService?.let { mod ->
            cost = mod.onCalcDomesticCost(general.personalCode, cost)
        }
        return cost
    }

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        val cost = getCost()
        if (general.funds < cost.funds) return ConstraintResult.Fail("자금이 부족합니다.")
        // PlanetFacilityService: 조병공창 보유 행성에서만 함선 건조 가능 (gin7 §5.5)
        services?.planetFacilityService?.let { facilityService ->
            if (!facilityService.hasShipyard(c)) return ConstraintResult.Fail("조병공창이 없습니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val mc = maxCrew()
        val isSame = general.shipClass.toInt() == crewType
        val cost = getCost()

        val statChanges = mutableMapOf<String, Any>("funds" to -cost.funds, "supplies" to -cost.supplies)

        if (isSame && general.ships > 0) {
            val oldCrew = general.ships
            val newCrew = oldCrew + mc
            val newTrain = ((general.training * oldCrew + 70 * mc) / newCrew.toDouble()).roundToInt().toShort()
            val newAtmos = ((general.morale * oldCrew + 70 * mc) / newCrew.toDouble()).roundToInt().toShort()
            statChanges["ships"] = mc
            statChanges["training"] = (newTrain - general.training).toInt()
            statChanges["morale"] = (newAtmos - general.morale).toInt()
            val msg = mapper.writeValueAsString(mapOf(
                "statChanges" to statChanges,
                "cityChanges" to mapOf("pop" to -mc),
            ))
            return CommandResult(true, listOf("${formatDate()} 추가모병 ${mc}명을 모병했습니다."), message = msg)
        } else {
            statChanges["ships"] = mc
            statChanges["shipClass"] = crewType
            statChanges["training"] = (70 - general.training.toInt())
            statChanges["morale"] = (70 - general.morale.toInt())
            val msg = mapper.writeValueAsString(mapOf(
                "statChanges" to statChanges,
                "cityChanges" to mapOf("pop" to -mc),
            ))
            return CommandResult(true, listOf("${formatDate()} 모병 ${mc}명을 모병했습니다."), message = msg)
        }
    }
}

class che_징병(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "징병"

    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0
    private val crewType: Int get() = (arg?.get("crewType") as? Number)?.toInt() ?: 0

    private fun maxCrew(): Int {
        val cap = general.leadership.toInt() * 100
        val currentCrew = if (general.shipClass.toInt() == crewType) general.ships else 0
        return min(amount, max(0, cap - currentCrew))
    }

    override fun getCost(): CommandCost {
        val mc = maxCrew()
        val baseCost = mc / 10
        var cost = CommandCost(funds = baseCost, supplies = mc / 100)
        services?.modifierService?.let { mod ->
            cost = mod.onCalcDomesticCost(general.personalCode, cost)
        }
        return cost
    }

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        if (c.pop < 30000) return ConstraintResult.Fail("인구가 너무 적어 징병할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val mc = maxCrew()
        val cost = getCost()
        val isSame = general.shipClass.toInt() == crewType
        val statChanges = mutableMapOf<String, Any>("funds" to -cost.funds, "supplies" to -cost.supplies)

        if (isSame && general.ships > 0) {
            val oldCrew = general.ships
            val newCrew = oldCrew + mc
            val newTrain = ((general.training * oldCrew + 40 * mc) / newCrew).toShort()
            val newAtmos = ((general.morale * oldCrew + 40 * mc) / newCrew).toShort()
            statChanges["ships"] = mc
            statChanges["training"] = (newTrain - general.training).toInt()
            statChanges["morale"] = (newAtmos - general.morale).toInt()
        } else {
            statChanges["ships"] = mc
            statChanges["shipClass"] = crewType
            statChanges["training"] = (40 - general.training.toInt())
            statChanges["morale"] = (40 - general.morale.toInt())
        }

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to statChanges,
            "cityChanges" to mapOf("pop" to -mc),
        ))
        return CommandResult(true, listOf("${formatDate()} 징병 ${mc}명을 징병했습니다."), message = msg)
    }
}

class che_훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        if (general.training >= 100.toShort()) return ConstraintResult.Fail("훈련도가 이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val leadershipValue = general.leadership.toInt()
        val oldTrain = general.training.toInt()
        val rawDelta = 30
        val trainDelta = min(100 - oldTrain, max(0, rawDelta))
        val atmosAfter = max(0, (general.morale.toInt() * 1.0).toInt())
        val atmosDelta = atmosAfter - general.morale.toInt()

        val statChanges = mutableMapOf<String, Any>(
            "training" to trainDelta,
            "morale" to atmosDelta,
            "experience" to 100,
            "dedication" to 70,
            "leadershipExp" to 1,
        )

        val msg = mapper.writeValueAsString(mapOf("statChanges" to statChanges))
        return CommandResult(
            success = true,
            logs = listOf("${formatDate()} 훈련치를 올렸습니다."),
            message = msg,
        )
    }
}

class che_사기진작(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "사기진작"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        if (general.morale >= 100.toShort()) return ConstraintResult.Fail("사기가 이미 최대치입니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val crew = general.ships
        val goldCost = max(1, crew / 100)
        val atmosDelta = max(1, (general.leadership.toInt() * 0.03).toInt())
        val trainDelta = 0

        val statChanges = mutableMapOf<String, Any>(
            "funds" to -goldCost,
            "morale" to atmosDelta,
            "training" to trainDelta,
            "experience" to 100,
            "dedication" to 70,
        )

        val msg = mapper.writeValueAsString(mapOf("statChanges" to statChanges))
        return CommandResult(true, listOf("${formatDate()} 사기치를 올렸습니다."), message = msg)
    }
}

class che_소집해제(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "소집해제"

    override fun checkFullCondition(): ConstraintResult {
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val pop = general.ships
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("ships" to -pop, "training" to -(general.training.toInt()), "morale" to -(general.morale.toInt())),
            "cityChanges" to mapOf("pop" to pop),
        ))
        return CommandResult(true, listOf("${formatDate()} 소집해제 ${pop}명을 해산했습니다."), message = msg)
    }
}

class che_숙련전환(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "숙련전환"

    override fun getCost(): CommandCost = CommandCost(funds = env.develCost, supplies = env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (arg == null) return ConstraintResult.Pass // arg check is in run()
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.funds < getCost().funds) return ConstraintResult.Fail("자금이 부족합니다.")
        if (general.supplies < getCost().supplies) return ConstraintResult.Fail("군량이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        if (arg == null) {
            return CommandResult(false, listOf("${formatDate()} 인자가 없습니다"), message = null)
        }
        val srcType = (arg["srcArmType"] as? Number)?.toInt() ?: 1
        val destType = (arg["destArmType"] as? Number)?.toInt() ?: 2
        val srcKey = "dex$srcType"
        val destKey = "dex$destType"

        val srcVal = (general.meta[srcKey] as? Number)?.toInt() ?: 0
        val transfer = (srcVal * 0.4).toInt()
        val converted = (transfer * 0.9).toInt()

        val statChanges = mapOf(srcKey to -transfer, destKey to converted)
        val payload = mapOf("statChanges" to statChanges, "dexConversion" to mapOf("from" to srcKey, "to" to destKey, "transfer" to transfer, "converted" to converted))
        val msg = mapper.writeValueAsString(payload)
        return CommandResult(true, listOf("${formatDate()} 숙련 전환을 실시했습니다."), message = msg)
    }
}

class che_물자조달(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "물자조달"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val c = city ?: return CommandResult(false, listOf("${formatDate()} 도시 정보 없음"))
        val stat = general.administration.toInt()
        val trust = max(50f, c.trust).toDouble()

        var score = (stat * (trust / 100.0) * (0.8 + rng.nextDouble() * 0.4)).toInt()
        score = max(1, score)
        val politicsBonus = 1.0 + (general.politics - 50) / 500.0
        score = (score * politicsBonus).toInt()
        score = max(1, score)

        // Critical ratio
        val avg = (general.leadership + general.command + general.intelligence) / 3.0
        val statVal = stat.toDouble()
        val ratio = min(avg / statVal, 1.2)
        var failRatio = (Math.pow(ratio / 1.2, 1.4) - 0.3).coerceIn(0.0, 0.5)
        var successRatio = (Math.pow(ratio / 1.2, 1.5) - 0.25).coerceIn(0.0, 0.5)
        if (trust < 80.0) successRatio *= trust / 80.0
        successRatio = successRatio.coerceIn(0.0, 1.0)
        failRatio = failRatio.coerceIn(0.0, 1.0 - successRatio)

        val roll = rng.nextDouble()
        val pick = when {
            roll < failRatio -> "fail"
            roll < failRatio + successRatio -> "success"
            else -> "normal"
        }

        val critMul = when (pick) {
            "success" -> 2.2 + rng.nextDouble() * 0.8
            "fail" -> 0.2 + rng.nextDouble() * 0.2
            else -> 1.0
        }
        score = (score * critMul).toInt()
        score = max(1, score)

        val fs = c.frontState.toInt()
        if (fs == 1 || fs == 3) score = (score * 0.5).toInt()

        val isGold = rng.nextBoolean()
        val resourceKey = if (isGold) "funds" else "supplies"
        val statChanges = mapOf(resourceKey to score)
        val nationChanges = mapOf(resourceKey to score)
        val payload = mapOf("statChanges" to statChanges, "nationChanges" to nationChanges, "criticalResult" to pick)
        val msg = mapper.writeValueAsString(payload)
        return CommandResult(true, listOf("${formatDate()} 물자를 조달했습니다."), message = msg)
    }
}

class che_군량매매(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "군량매매"

    private val buyRice: Boolean get() = arg?.get("buyRice") as? Boolean ?: true
    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.trade <= 0) return ConstraintResult.Fail("교역로가 없습니다.")
        if (buyRice) {
            if (general.funds < amount) return ConstraintResult.Fail("자금이 부족합니다.")
        } else {
            if (general.supplies < amount) return ConstraintResult.Fail("군량이 부족합니다.")
        }
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        // Round amount up to nearest 100
        val tradeAmount = if (amount < 100) 100 else ((amount + 99) / 100) * 100
        val taxRate = 0.03
        val tax = (tradeAmount * taxRate).toInt()

        val statChanges: Map<String, Int>
        val nationTax: Map<String, Int>
        if (buyRice) {
            val goldCost = tradeAmount + tax
            statChanges = mapOf("funds" to -goldCost, "supplies" to tradeAmount)
            nationTax = mapOf("funds" to tax)
        } else {
            val riceCost = tradeAmount + tax
            statChanges = mapOf("supplies" to -riceCost, "funds" to tradeAmount)
            nationTax = mapOf("supplies" to tax)
        }

        val payload = mapOf("statChanges" to statChanges, "nationTax" to nationTax)
        val msg = mapper.writeValueAsString(payload)
        return CommandResult(true, listOf("${formatDate()} 군량 매매를 실시했습니다."), message = msg)
    }
}

class che_헌납(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "헌납"

    private val isGold: Boolean get() = arg?.get("isGold") as? Boolean ?: true
    private val amount: Int get() = (arg?.get("amount") as? Number)?.toInt() ?: 0

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (isGold && general.funds < amount) return ConstraintResult.Fail("자금이 부족합니다.")
        if (!isGold && general.supplies < amount) return ConstraintResult.Fail("군량이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val label = if (isGold) "금" else "쌀"
        val available = if (isGold) general.funds else general.supplies
        val actualAmount = amount.coerceAtMost(available)
        val statChanges = if (isGold) mapOf("funds" to -actualAmount) else mapOf("supplies" to -actualAmount)
        val nationChanges = if (isGold) mapOf("funds" to actualAmount) else mapOf("supplies" to actualAmount)
        val msg = mapper.writeValueAsString(mapOf("statChanges" to statChanges, "nationChanges" to nationChanges))
        return CommandResult(true, listOf("${formatDate()} ${label} ${actualAmount}을(를) 헌납했습니다."), message = msg)
    }
}

class che_단련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "단련"

    override fun getCost(): CommandCost = CommandCost(funds = env.develCost, supplies = env.develCost)

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val criticalRoll = rng.nextDouble()
        val pick: String
        val multiplier: Int
        when {
            criticalRoll < 0.33 -> { pick = "fail"; multiplier = 1 }
            criticalRoll > 0.66 -> { pick = "success"; multiplier = 3 }
            else -> { pick = "normal"; multiplier = 2 }
        }

        val baseScore = (general.ships.toDouble() * general.training * general.morale) / 200000.0
        val score = (baseScore * multiplier).roundToInt()

        val totalWeight = general.leadership + general.command + general.intelligence
        var roll = rng.nextDouble() * totalWeight
        val incStat: String
        when {
            run { roll -= general.leadership; roll < 0 } -> incStat = "leadershipExp"
            run { roll -= general.command; roll < 0 } -> incStat = "commandExp"
            else -> incStat = "intelligenceExp"
        }

        val statChanges = mutableMapOf<String, Any>(
            "funds" to -getCost().funds,
            "supplies" to -getCost().supplies,
            "experience" to 2,
            incStat to 1,
        )

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to statChanges,
            "dexChanges" to mapOf("shipClass" to general.shipClass.toInt(), "amount" to score),
            "criticalResult" to pick,
        ))
        return CommandResult(true, listOf("${formatDate()} 단련을 실시했습니다."), message = msg)
    }
}

// ========== Military Movement Commands ==========

class 출병(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "출병"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.id == general.planetId) return ConstraintResult.Fail("같은 도시로는 출병할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val riceCost = general.ships / 100
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("supplies" to -riceCost),
            "battleTriggered" to true,
            "targetCityId" to dc.id.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 출병했습니다."), message = msg)
    }
}

class 이동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "이동"

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity ?: return CommandResult(false, listOf("${formatDate()} 목적지가 없습니다."))
        val oldAtmos = general.morale.toInt()
        val newAtmos = max(20, oldAtmos - 5)
        val atmosDelta = newAtmos - oldAtmos
        val goldCost = env.develCost
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "planetId" to dc.id.toString(),
                "morale" to atmosDelta,
                "funds" to -goldCost,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 이동했습니다."), message = msg)
    }
}

class 집합(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "집합"

    @Suppress("UNCHECKED_CAST")
    override fun checkFullCondition(): ConstraintResult {
        val troopId = if (general.fleetId == general.id) general.id else general.fleetId
        if (troopId != general.id) return ConstraintResult.Fail("부대장만 사용할 수 있습니다.")
        val memberExists = env.gameStor["troopMemberExistsByTroopId"] as? Map<Long, Boolean> ?: emptyMap()
        if (memberExists[troopId] != true) return ConstraintResult.Fail("부대원이 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val svc = services
        var movedCount = 0
        val movedGenerals = mutableListOf<General>()
        if (svc != null) {
            val members = svc.generalRepository.findByTroopId(general.id)
            for (m in members) {
                if (m.id != general.id && m.planetId != general.planetId) {
                    m.planetId = general.planetId
                    movedCount++
                    movedGenerals.add(m)
                }
            }
        }
        destCityGenerals = movedGenerals
        val msg = mapper.writeValueAsString(mapOf(
            "troopAssembly" to true,
            "troopLeaderId" to general.id.toString(),
            "movedCount" to movedCount,
        ))
        return CommandResult(true, listOf("${formatDate()} 집합했습니다."), message = msg)
    }
}

class 귀환(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "귀환"

    override fun checkFullCondition(): ConstraintResult {
        val n = nation ?: return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.planetId == n.capitalCityId) return ConstraintResult.Fail("수도에서는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val moveTo = general.officerCity
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "moveTo" to moveTo.toString(),
        ))
        return CommandResult(true, listOf("${formatDate()} 귀환했습니다."), message = msg)
    }
}

class 접경귀환(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "접경귀환"
    override val canDisplay = false

    override fun checkFullCondition(): ConstraintResult {
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.factionId == general.factionId) return ConstraintResult.Fail("아군 도시에서는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun run(rng: Random): CommandResult {
        val mapAdj = env.gameStor["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val cityNationById = env.gameStor["cityNationById"] as? Map<Long, Long> ?: emptyMap()
        val citySupplyById = env.gameStor["citySupplyStateById"] as? Map<Long, Int> ?: emptyMap()

        // BFS to find nearest supplied friendly city
        val visited = mutableSetOf(general.planetId)
        val queue = ArrayDeque<Long>()
        queue.add(general.planetId)
        var targetCityId: Long? = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val neighbors = mapAdj[current] ?: continue
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    val ownerNation = cityNationById[neighbor] ?: 0L
                    if (ownerNation == general.factionId) {
                        val supply = citySupplyById[neighbor] ?: 0
                        if (supply > 0) {
                            targetCityId = neighbor
                            break
                        }
                    }
                    queue.add(neighbor)
                }
            }
            if (targetCityId != null) break
        }

        if (targetCityId == null) {
            return CommandResult(false, listOf("${formatDate()} 귀환할 수 있는 도시가 없습니다."))
        }

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "city" to targetCityId,
        ))
        return CommandResult(true, listOf("${formatDate()} 접경 귀환했습니다."), message = msg)
    }
}

class 강행(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "강행"

    @Suppress("UNCHECKED_CAST")
    override fun checkFullCondition(): ConstraintResult {
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        val mapAdj = env.gameStor["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = env.gameStor["dbToMapId"] as? Map<Long, Long> ?: emptyMap()
        val srcMapId = dbToMapId[general.planetId]
        val destMapId = dbToMapId[dc.id]
        if (srcMapId == null || destMapId == null) return ConstraintResult.Fail("거리를 계산할 수 없습니다.")
        val neighbors = mapAdj[srcMapId] ?: emptyList()
        if (destMapId !in neighbors) return ConstraintResult.Fail("거리가 너무 멉니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "planetId" to dc.id.toString(),
                "funds" to -500,
                "training" to -5,
                "morale" to -5,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 강행군을 실시했습니다."), message = msg)
    }
}

class 거병(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "거병"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId != 0L) return ConstraintResult.Fail("재야 상태가 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10, "officerLevel" to 20),
            "createWanderingNation" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 거병했습니다."), message = msg)
    }
}

class 전투태세(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "전투태세"
    override val canDisplay = false

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        if (general.training >= 70.toShort()) return ConstraintResult.Fail("훈련도가 충분합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "leadershipExp" to 3,
            ),
            "setMin" to 75,
        ))
        return CommandResult(true, listOf("${formatDate()} 전투태세를 갖추었습니다."), message = msg)
    }
}

// ========== Spy/Sabotage Commands ==========

class 화계(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "화계"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.factionId == 0L) return ConstraintResult.Fail("공백지에는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val goldCost = env.develCost * 5
        val riceCost = env.develCost * 5
        @Suppress("UNCHECKED_CAST")
        val mapAdj = env.gameStor["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = env.gameStor["dbToMapId"] as? Map<Long, Long> ?: emptyMap()
        val srcMapId = dbToMapId[general.planetId]
        val destMapId = dbToMapId[dc.id]
        val distance: Int = if (srcMapId != null && destMapId != null) {
            val neighbors = mapAdj[srcMapId] ?: emptyList()
            if (destMapId in neighbors) 1 else 2
        } else 99
        val baseProbability = 0.35
        val successProbability = (baseProbability / distance).coerceIn(0.05, 1.0)
        if (rng.nextDouble() >= successProbability) {
            val msg = mapper.writeValueAsString(mapOf(
                "statChanges" to mapOf("funds" to -goldCost, "supplies" to -riceCost, "experience" to 10, "intelligenceExp" to 1),
            ))
            return CommandResult(false, listOf("${formatDate()} 화계가 실패했습니다."), message = msg)
        }
        val score = (general.intelligence.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val agriDelta = -min(dc.agri, score)
        val commDelta = -min(dc.comm, score)
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("funds" to -goldCost, "supplies" to -riceCost, "experience" to score, "intelligenceExp" to 1),
            "destCityChanges" to mapOf("agri" to agriDelta, "comm" to commDelta),
        ))
        return CommandResult(true, listOf("${formatDate()} 화계를 실시했습니다."), message = msg)
    }
}

class 첩보(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "첩보"

    override fun checkFullCondition(): ConstraintResult {
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.factionId == general.factionId) return ConstraintResult.Fail("아군 도시에는 사용할 수 없습니다.")
        val costAmount = env.develCost * 3
        if (general.funds < costAmount) return ConstraintResult.Fail("자금이 부족합니다.")
        if (general.supplies < costAmount) return ConstraintResult.Fail("군량이 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val costAmount = env.develCost * 3

        // Use EspionageEngine for intelligence gathering (gin7 9.7)
        val intelOps = (general.meta["intelOps"] as? Number)?.toInt() ?: general.intelligence.toInt()
        val gathered = EspionageEngine.gatherIntel(
            intelOps = intelOps,
            intelligenceStat = general.intelligence.toInt(),
            rng = rng,
        )

        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("funds" to -costAmount, "supplies" to -costAmount, "experience" to 50, "intelligenceExp" to 1),
            "spyResult" to mapOf("pop" to dc.pop, "trust" to dc.trust),
            "intelGathered" to gathered,
        ))
        val categories = gathered.keys.joinToString(", ")
        val intelMsg = if (gathered.isNotEmpty()) " 수집 정보: $categories" else " 유효한 정보를 수집하지 못했습니다."
        return CommandResult(true, listOf("${formatDate()} 첩보 활동을 했습니다.$intelMsg"), message = msg)
    }
}

class 선동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "선동"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.factionId == 0L) return ConstraintResult.Fail("공백지에는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val score = (general.leadership.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val secuDelta = -min(dc.secu, score)
        val trustDelta = -(score * 0.1f)
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score, "leadershipExp" to 1),
            "destCityChanges" to mapOf("secu" to secuDelta, "trust" to trustDelta),
        ))
        return CommandResult(true, listOf("${formatDate()} 선동을 실시했습니다."), message = msg)
    }
}

class 탈취(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "탈취"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.factionId == 0L) return ConstraintResult.Fail("공백지에는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val score = (general.command.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val commDelta = -min(dc.comm, score)
        val agriDelta = -min(dc.agri, score)
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score, "commandExp" to 1),
            "destCityChanges" to mapOf("comm" to commDelta, "agri" to agriDelta),
        ))
        return CommandResult(true, listOf("${formatDate()} 탈취를 실시했습니다."), message = msg)
    }
}

class 파괴(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "파괴"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        val c = city ?: return ConstraintResult.Fail("도시 정보가 없습니다.")
        if (c.supplyState.toInt() == 0) return ConstraintResult.Fail("보급 상태가 아닙니다.")
        val dc = destCity ?: return ConstraintResult.Fail("목적지 도시가 없습니다.")
        if (dc.factionId == 0L) return ConstraintResult.Fail("공백지에는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dc = destCity!!
        val score = (general.command.toInt() * (0.8 + rng.nextDouble() * 0.4)).toInt()
        val defDelta = -min(dc.def, score)
        val wallDelta = -min(dc.wall, score)
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to score, "commandExp" to 1),
            "destCityChanges" to mapOf("def" to defDelta, "wall" to wallDelta),
        ))
        return CommandResult(true, listOf("${formatDate()} 파괴 공작을 실시했습니다."), message = msg)
    }
}

// ========== Recovery & Wandering ==========

class 요양(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "요양"
    override suspend fun run(rng: Random): CommandResult {
        val injuryDelta = -(general.injury.toInt())
        val expDelta = 10
        val dedDelta = 7
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "injury" to injuryDelta,
                "experience" to expDelta,
                "dedication" to dedDelta,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} 요양을 마쳤습니다."), message = msg)
    }
}

class 방랑(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "방랑"
    override val canDisplay = false

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주만 사용할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 5),
            "becomeWanderer" to true,
            "releaseAllCities" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 방랑을 계속합니다."), message = msg)
    }
}

class 견문(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "견문"
    override suspend fun run(rng: Random): CommandResult {
        val statChanges = mutableMapOf<String, Any>("experience" to 20)

        // Random stat exp increase
        val roll = rng.nextInt(5)
        when (roll) {
            0 -> statChanges["leadershipExp"] = 1
            1 -> statChanges["commandExp"] = 1
            2 -> statChanges["intelligenceExp"] = 1
            3 -> statChanges["politicsExp"] = 1
            4 -> statChanges["administrationExp"] = 1
        }

        // Random funds/supplies change
        val goldDelta = rng.nextInt(201) - 100
        val riceDelta = rng.nextInt(201) - 100
        if (goldDelta != 0) statChanges["funds"] = goldDelta
        if (riceDelta != 0) statChanges["supplies"] = riceDelta

        // Small chance of injury
        val injuryRoll = rng.nextDouble()
        if (injuryRoll < 0.05) {
            statChanges["injury"] = 50
        } else if (injuryRoll < 0.1) {
            statChanges["injury"] = 20
        }

        val msg = mapper.writeValueAsString(mapOf("statChanges" to statChanges))
        return CommandResult(true, listOf("${formatDate()} 견문을 넓혔습니다."), message = msg)
    }
}

// ========== Political Commands ==========

class 등용(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "등용"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("statChanges" to mapOf("experience" to 50, "dedication" to 50)))
        return CommandResult(true, listOf("${formatDate()} 등용을 시도했습니다."), message = msg)
    }
}

class 등용수락(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "등용수락"
    override val canDisplay = false
    override val isReservable = false

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId != 0L) return ConstraintResult.Fail("이미 소속 국가가 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dn = destNation ?: return CommandResult(false, listOf("${formatDate()} 대상 국가 없음"))
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "dedication" to 50),
            "nation" to dn.id,
        ))
        return CommandResult(true, listOf("${formatDate()} 등용 수락했습니다."), message = msg)
    }
}

class 임관(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "임관"


    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId != 0L) return ConstraintResult.Fail("이미 소속 국가가 있습니다.")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dn = destNation!!
        dn.gennum += 1
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "dedication" to 50),
        ))
        return CommandResult(true, listOf("${formatDate()} 임관했습니다."), message = msg)
    }
}

class 랜덤임관(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "랜덤임관"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId != 0L) return ConstraintResult.Fail("이미 소속 국가가 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val svc = services ?: return CommandResult(false, listOf("${formatDate()} 서비스 없음"))
        val nations = svc.nationRepository.findByWorldId(general.worldId).filter { it.level > 0 && it.scoutLevel.toInt() == 0 }
        if (nations.isEmpty()) return CommandResult(false, listOf("${formatDate()} 임관 가능한 국가가 없습니다."))

        val generals = svc.generalRepository.findByWorldId(general.worldId)
        val lords = generals.filter { it.officerLevel >= 20.toShort() }

        data class Candidate(val nation: Nation, val lord: General?, val distance: Int)
        val candidates = nations.map { n ->
            val lord = lords.find { it.factionId == n.id }
            val dist = if (lord != null) kotlin.math.abs(general.affinity - lord.affinity).toInt() else 999
            Candidate(n, lord, dist)
        }.sortedBy { it.distance }

        val chosen = candidates.first()
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "dedication" to 50),
            "nation" to chosen.nation.id,
            "officerLevel" to 1,
            "officerCity" to 0,
            "belong" to 1,
            "troop" to 0,
            "planetId" to (chosen.lord?.planetId?.toString() ?: "0"),
            "tryUniqueLottery" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} 랜덤 임관했습니다."), message = msg)
    }
}

class 장수대상임관(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "장수대상임관"

    override var destGeneral: General? = null

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId != 0L) return ConstraintResult.Fail("이미 소속 국가가 있습니다.")
        if (destNation == null) return ConstraintResult.Fail("대상 국가가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dn = destNation!!
        val dg = destGeneral
        dn.gennum += 1
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 50, "dedication" to 50),
            "city" to (dg?.planetId ?: 0),
        ))
        return CommandResult(true, listOf("${formatDate()} 장수대상 임관했습니다."), message = msg)
    }
}

class 하야(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "하야"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.officerLevel >= 20.toShort()) return ConstraintResult.Fail("군주는 하야할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val penaltyRate = general.betray.toDouble() * 0.1
        val expLoss = -(general.experience * penaltyRate).toInt()
        val dedLoss = -(general.dedication * penaltyRate).toInt()
        val statChanges = mutableMapOf<String, Any>(
            "experience" to expLoss,
            "dedication" to dedLoss,
            "betray" to 1,
        )
        val msg = mapper.writeValueAsString(mapOf("statChanges" to statChanges))
        return CommandResult(true, listOf("${formatDate()} 하야했습니다."), message = msg)
    }
}

class 은퇴(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "은퇴"

    override fun checkFullCondition(): ConstraintResult {
        if (general.age < 50.toShort()) return ConstraintResult.Fail("나이가 부족합니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("rebirth" to true))
        return CommandResult(true, listOf("${formatDate()} 은퇴했습니다."), message = msg)
    }
}

class 건국(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "건국"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주 등급이 아닙니다.")
        val n = nation
        if (n != null && n.level > 0) return ConstraintResult.Fail("이미 국가에 소속되어 있습니다.")
        val relYear = env.year - env.startYear
        if (relYear >= 1) return ConstraintResult.Fail("오프닝 기간이 지났습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val yearMonth = env.year * 12 + env.month
        val initYearMonth = env.startYear * 12 + 1
        if (yearMonth <= initYearMonth) {
            return CommandResult(false, listOf("${formatDate()} 다음 턴부터 건국 가능합니다."))
        }
        val nationName = arg?.get("nationName") as? String ?: "신국"
        val nationType = arg?.get("nationType") as? String ?: "도적"
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 100),
            "nationFoundation" to true,
            "foundNation" to mapOf("nationName" to nationName, "nationType" to nationType),
        ))
        return CommandResult(true, listOf("${formatDate()} <C>${nationName}</C>을(를) 건국했습니다!"), message = msg)
    }
}

class 무작위건국(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "무작위건국"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주 등급이 아닙니다.")
        val n = nation
        if (n != null && n.level > 0) return ConstraintResult.Fail("이미 국가에 소속되어 있습니다.")
        val relYear = env.year - env.startYear
        if (relYear < 1) return ConstraintResult.Fail("오프닝 기간에는 사용할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val nationName = arg?.get("nationName") as? String ?: "신국"
        val nationType = arg?.get("nationType") as? String ?: "도적"
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 100),
            "foundNation" to mapOf("nationName" to nationName, "nationType" to nationType),
        ))
        return CommandResult(true, listOf("${formatDate()} <C>${nationName}</C>을(를) 건국했습니다!"), message = msg)
    }
}

class 모반시도(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "모반시도"
    override val canDisplay = false

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel >= 20.toShort()) return ConstraintResult.Fail("군주는 모반할 수 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "rebellionResult" to "attempted",
        ))
        return CommandResult(true, listOf("${formatDate()} 모반을 시도했습니다."), message = msg)
    }
}

class 선양(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "선양"
    override var destGeneral: General? = null

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주만 선양할 수 있습니다.")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val dg = destGeneral!!
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("officerLevel" to -(general.officerLevel - 1)),
            "destGeneralChanges" to mapOf("officerLevel" to 20),
        ))
        return CommandResult(true, listOf("${formatDate()} 선양했습니다."), message = msg)
    }
}

class 해산(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "해산"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주만 해산할 수 있습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("disbandNation" to true))
        return CommandResult(true, listOf("${formatDate()} 국가를 해산했습니다."), message = msg)
    }
}

class 인재탐색(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "인재탐색"

    override fun getCost(): CommandCost = CommandCost(funds =100)

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("statChanges" to mapOf("funds" to -100, "experience" to 10)))
        return CommandResult(true, listOf("${formatDate()} 인재를 탐색했습니다."), message = msg)
    }
}

class 증여(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "증여"
    override var destGeneral: General? = null

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (destGeneral == null) return ConstraintResult.Fail("대상 장수가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val isGold = arg?.get("isGold") as? Boolean ?: true
        val amount = (arg?.get("amount") as? Number)?.toInt() ?: 0
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to if (isGold) mapOf("funds" to -amount) else mapOf("supplies" to -amount),
            "destGeneralChanges" to if (isGold) mapOf("funds" to amount) else mapOf("supplies" to amount),
        ))
        return CommandResult(true, listOf("${formatDate()} 증여했습니다."), message = msg)
    }
}

class 장비매매(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "장비매매"

    override suspend fun run(rng: Random): CommandResult {
        if (arg == null || arg.isEmpty()) {
            return CommandResult(false, listOf("${formatDate()} 인자가 없습니다."))
        }
        val msg = mapper.writeValueAsString(mapOf("statChanges" to mapOf("experience" to 10)))
        return CommandResult(true, listOf("${formatDate()} 장비를 매매했습니다."), message = msg)
    }
}

class 내정특기초기화(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "내정특기초기화"

    override fun checkFullCondition(): ConstraintResult {
        if (general.specialCode == "None") return ConstraintResult.Fail("초기화할 특기가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("statChanges" to mapOf("specialCode" to "None")))
        return CommandResult(true, listOf("${formatDate()} 내정 특기를 초기화했습니다."), message = msg)
    }
}

class 전투특기초기화(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "전투특기초기화"

    override fun checkFullCondition(): ConstraintResult {
        if (general.special2Code == "None") return ConstraintResult.Fail("초기화할 특기가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf("statChanges" to mapOf("special2Code" to "None")))
        return CommandResult(true, listOf("${formatDate()} 전투 특기를 초기화했습니다."), message = msg)
    }
}

// ========== Special Commands ==========

class NPC능동(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "NPC능동"

    override fun checkFullCondition(): ConstraintResult {
        if (general.npcState.toInt() == 0) return ConstraintResult.Fail("NPC가 아닙니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 10),
            "npcAction" to (arg?.get("optionText") ?: "행동"),
        ))
        return CommandResult(true, listOf("${formatDate()} NPC 능동 행동을 실시했습니다."), message = msg)
    }
}

class CR건국(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "CR건국"

    override fun checkFullCondition(): ConstraintResult {
        if (general.officerLevel < 20.toShort()) return ConstraintResult.Fail("군주 등급이 아닙니다.")
        if (general.factionId != 0L) return ConstraintResult.Fail("이미 소속 국가가 있습니다.")
        val relYear = env.year - env.startYear
        if (relYear >= 1) return ConstraintResult.Fail("오프닝 기간이 지났습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val nationName = arg?.get("nationName") as? String ?: "신국"
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf("experience" to 100),
            "nationFoundation" to true,
        ))
        return CommandResult(true, listOf("${formatDate()} CR건국을 실시했습니다."), message = msg)
    }
}

class CR맹훈련(general: General, env: CommandEnv, arg: Map<String, Any>? = null) : BaseCommand(general, env, arg) {
    override val actionName = "CR맹훈련"

    override fun checkFullCondition(): ConstraintResult {
        if (general.factionId == 0L) return ConstraintResult.Fail("소속 국가가 없습니다.")
        if (general.ships == 0) return ConstraintResult.Fail("병사가 없습니다.")
        return ConstraintResult.Pass
    }

    override suspend fun run(rng: Random): CommandResult {
        val riceCost = general.ships / 2
        val msg = mapper.writeValueAsString(mapOf(
            "statChanges" to mapOf(
                "supplies" to -riceCost,
                "training" to 5,
                "experience" to 100,
            ),
        ))
        return CommandResult(true, listOf("${formatDate()} CR맹훈련을 실시했습니다."), message = msg)
    }
}
