package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.engine.modifier.StatContext
import com.openlogh.entity.Officer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

abstract class DomesticCommand(
    general: Officer, env: CommandEnv, arg: Map<String, Any>? = null
) : OfficerCommand(general, env, arg) {

    abstract val cityKey: String
    abstract val statKey: String

    /** The action key used for onCalcDomestic modifier matching (legacy: $actionKey). */
    abstract val actionKey: String

    open val debuffFront: Double = 0.5

    override val minConditionConstraints: List<Constraint>
        get() = listOf(NotBeNeutral(), OccupiedCity())

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                NotWanderingNation(),
                OccupiedCity(),
                SuppliedCity(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
                RemainCityCapacity(cityKey, actionName)
            )
        }

    override fun getCost(): CommandCost {
        // Legacy: onCalcDomestic(actionKey, 'cost', develcost)
        val gold = DomesticUtils.applyModifier(services, general, nation, actionKey, "cost", env.develCost.toDouble())
        return CommandCost(funds = gold.roundToInt())
    }
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    protected fun getStat(): Int {
        val mods = services?.modifierService?.getModifiers(general, nation) ?: emptyList()
        val base = StatContext(
            leadership = general.leadership.toDouble(),
            strength = general.command.toDouble(),
            intel = general.intelligence.toDouble(),
        )
        val modified = services?.modifierService?.applyStatModifiers(mods, base) ?: base
        return when (statKey) {
            "leadership" -> modified.leadership.toInt()
            "strength" -> modified.strength.toInt()
            "intel" -> modified.intel.toInt()
            "politics" -> general.politics.toInt()
            "charm" -> general.administration.toInt()
            else -> modified.intel.toInt()
        }
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val stat = getStat()
        val trust = maxOf(50.0, (city?.approval ?: 50F).toDouble())

        // Legacy: base score = stat * trust/100 * getDomesticExpLevelBonus * rng(0.8..1.2)
        var score = stat.toDouble() * (trust / 100.0) *
            DomesticUtils.getDomesticExpLevelBonus(general.expLevel.toInt()) *
            (0.8 + rng.nextDouble() * 0.4)

        score *= DomesticUtils.statBonus(general.politics.toInt())
        score *= DomesticUtils.applyModifier(services, general, nation, actionKey, "score", 1.0)
        score = max(1.0, score)
        var scoreIntWork = score.toInt()

        // Legacy parity: CriticalRatioDomestic — ratio = avg(leadership,strength,intel) / stat
        // Use modified stats (same modifier pass as getStat)
        val mods = services?.modifierService?.getModifiers(general, nation) ?: emptyList()
        val baseStatCtx = StatContext(
            leadership = general.leadership.toDouble(),
            strength = general.command.toDouble(),
            intel = general.intelligence.toDouble(),
        )
        val modifiedStatCtx = services?.modifierService?.applyStatModifiers(mods, baseStatCtx) ?: baseStatCtx
        val leadership = modifiedStatCtx.leadership
        val strength = modifiedStatCtx.strength
        val intel = modifiedStatCtx.intel
        val avg = (leadership + strength + intel) / 3.0
        val statValue = when (statKey) {
            "leadership" -> leadership
            "strength" -> strength
            "intel" -> intel
            else -> intel
        }
        val ratio = min(avg / statValue, 1.2)
        var failRatio = ((ratio / 1.2).pow(1.4) - 0.3).coerceIn(0.0, 0.5)
        var successRatio = ((ratio / 1.2).pow(1.5) - 0.25).coerceIn(0.0, 0.5)

        // Legacy: trust scaling — only applies when trust < 80
        if (trust < 80.0) {
            successRatio *= trust / 80.0
        }

        // Apply onCalcDomestic 'success'/'fail' modifiers
        successRatio = DomesticUtils.applyModifier(services, general, nation, actionKey, "success", successRatio)
        failRatio = DomesticUtils.applyModifier(services, general, nation, actionKey, "fail", failRatio)

        successRatio = successRatio.coerceIn(0.0, 1.0)
        failRatio = failRatio.coerceIn(0.0, 1.0 - successRatio)

        // Legacy parity: choiceUsingWeight three-way weighted random
        val roll = rng.nextDouble()
        val pick = when {
            roll < failRatio -> "fail"
            roll < failRatio + successRatio -> "success"
            else -> "normal"
        }

        // Legacy parity: CriticalScoreEx — success=[2.2,3.0), fail=[0.2,0.4), normal=1.0
        val criticalMultiplier = when (pick) {
            "success" -> 2.2 + rng.nextDouble() * 0.8
            "fail" -> 0.2 + rng.nextDouble() * 0.2
            else -> 1.0
        }
        scoreIntWork = (scoreIntWork * criticalMultiplier).toInt()

        val scoreInt = max(1, scoreIntWork)
        val exp = (scoreInt * 0.7).toInt()
        val ded = scoreInt

        // Legacy parity: updateMaxDomesticCritical on success, reset on non-success
        val maxCriticalJson = if (pick == "success") {
            ""","maxDomesticCritical":$scoreInt"""
        } else {
            ""","maxDomesticCritical":0"""
        }

        val josaUl = pickJosa(actionName, "을")
        val logMessage = when (pick) {
            "fail" -> "${actionName}${josaUl} <R>실패</>하여 <C>$scoreInt</> 상승했습니다. <1>$date</>"
            "success" -> "${actionName}${josaUl} <S>성공</>하여 <C>$scoreInt</> 상승했습니다. <1>$date</>"
            else -> "${actionName}${josaUl} 하여 <C>$scoreInt</> 상승했습니다. <1>$date</>"
        }
        pushLog(logMessage)
        pushHistoryLog(logMessage)
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${actionName}${josaUl} 실행했습니다.")

        // Legacy parity: front line debuff with capital scaling
        var finalScore = scoreInt
        val c = city
        if (c != null && (c.frontState.toInt() == 1 || c.frontState.toInt() == 3)) {
            var actualDebuff = debuffFront

            if (nation?.capitalPlanetId == c.id?.toLong()) {
                val relYear = env.year - env.startYear
                if (relYear < 25) {
                    val debuffScale = (maxOf(0, relYear - 5).coerceAtMost(20)) * 0.05
                    actualDebuff = (debuffScale * debuffFront) + (1 - debuffScale)
                }
            }

            finalScore = (finalScore * actualDebuff).toInt()
        }

        val currentValue = when (cityKey) {
            "agri" -> c?.production ?: 0
            "comm" -> c?.commerce ?: 0
            "secu" -> c?.security ?: 0
            "def" -> c?.orbitalDefense ?: 0
            "wall" -> c?.fortress ?: 0
            else -> 0
        }
        val maxValue = when (cityKey) {
            "agri" -> c?.productionMax ?: 1000
            "comm" -> c?.commerceMax ?: 1000
            "secu" -> c?.securityMax ?: 1000
            "def" -> c?.orbitalDefenseMax ?: 1000
            "wall" -> c?.fortressMax ?: 1000
            else -> 1000
        }
        val newValue = minOf(maxValue, currentValue + finalScore)
        val actualDelta = newValue - currentValue

        val statExpKey = "${statKey}Exp"

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-getCost().funds},"experience":$exp,"dedication":$ded,"$statExpKey":1},"cityChanges":{"$cityKey":$actualDelta},"criticalResult":"$pick"$maxCriticalJson}"""
        )
    }
}
