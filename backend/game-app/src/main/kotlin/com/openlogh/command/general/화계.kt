package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.engine.modifier.ConsumableItem
import com.openlogh.engine.modifier.DomesticContext
import com.openlogh.engine.modifier.ItemModifiers
import com.openlogh.engine.modifier.StatContext
import com.openlogh.entity.Officer
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val SABOTAGE_DEFAULT_PROB = 0.35
private const val MAX_SUCCESS_PROB = 0.5
private const val INJURY_MAX = 80
private const val INJURY_PROB_DEFAULT = 0.3

open class 화계(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "화계"

    protected open val statType: String = "intel"
    protected open val injuryGeneral: Boolean = true

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                SuppliedCity(),
                NotOccupiedDestCity(),
                NotNeutralDestCity(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
                DisallowDiplomacyBetweenStatus(mapOf(7 to "불가침국입니다.")),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                SuppliedCity(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override fun getCost(): CommandCost {
        val cost = env.develCost * 5
        return CommandCost(funds = cost, supplies = cost)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    protected fun getStatScore(): Int {
        val mods = services?.modifierService?.getModifiers(general, nation) ?: emptyList()
        val base = StatContext(
            leadership = general.leadership.toDouble(),
            strength = general.command.toDouble(),
            intel = general.intelligence.toDouble(),
        )
        val modified = services?.modifierService?.applyStatModifiers(mods, base) ?: base
        return when (statType) {
            "leadership" -> modified.leadership.toInt()
            "strength" -> modified.strength.toInt()
            else -> modified.intel.toInt()
        }
    }

    protected fun getStatExpKey(): String = when (statType) {
        "leadership" -> "leadershipExp"
        "strength" -> "strengthExp"
        else -> "intelExp"
    }

    /**
     * Calculate attack success probability based on attacker stat.
     * Legacy: genScore / sabotageProbCoefByStat, then onCalcDomestic('계략', 'success', prob)
     */
    protected fun calcAttackProb(): Double {
        var prob = getStatScore() / env.sabotageProbCoefByStat.toDouble()

        // Apply onCalcDomestic modifiers (legacy: $general->onCalcDomestic('계략', 'success', $prob))
        val baseCtx = DomesticContext(successMultiplier = prob, actionCode = "계략")
        val modified = modifiers.fold(baseCtx) { ctx, modifier -> modifier.onCalcDomestic(ctx) }
        prob = modified.successMultiplier

        return prob
    }

    /**
     * Calculate defence probability based on dest city generals.
     * Legacy factors:
     * - max stat of defending generals / sabotageProbCoefByStat
     * - onCalcStat(sabotageDefence) correction per defender
     * - log2(affectCount + 1) - 1.25 * sabotageDefenceCoefByGeneralCnt
     * - city secu / secu_max / 5 (up to 20%p)
     * - supplied city: +0.1
     */
    protected fun calcDefenceProb(): Double {
        val dc = destPlanet ?: return 0.0
        val destNationId = dc.factionId
        val defenders = destPlanetOfficers ?: emptyList()
        val destFaction = if (destNationId != 0L) {
            services?.factionRepository?.findById(destNationId)?.orElse(null)
        } else null

        var maxStat = 0
        var probCorrection = 0.0
        var affectCount = 0

        for (defender in defenders) {
            if (defender.factionId != destNationId) continue
            affectCount++
            val defStat = when (statType) {
                "leadership" -> defender.leadership.toInt()
                "strength" -> defender.command.toInt()
                else -> defender.intelligence.toInt()
            }
            maxStat = max(maxStat, defStat)

            // Legacy: $probCorrection = $destOfficer->onCalcStat($destOfficer, 'sabotageDefence', $probCorrection)
            // Apply per-defender sabotageDefence modifier
            val defenderNation = if (defender.factionId == destNationId) destFaction else if (defender.factionId != 0L) {
                services?.factionRepository?.findById(defender.factionId)?.orElse(null)
            } else null
            val defenderModifiers = services?.modifierService?.getModifiers(defender, defenderNation) ?: emptyList()
            val baseStat = StatContext(sabotageDefence = probCorrection)
            val modifiedStat = defenderModifiers.fold(baseStat) { stat, mod -> mod.onCalcStat(stat) }
            probCorrection = modifiedStat.sabotageDefence
        }

        var prob = maxStat / env.sabotageProbCoefByStat.toDouble()
        prob += probCorrection
        prob += (ln((affectCount + 1).toDouble()) / ln(2.0) - 1.25) * env.sabotageDefenceCoefByGeneralCnt
        prob += if (dc.securityMax > 0) dc.security.toDouble() / dc.securityMax / 5.0 else 0.0
        prob += if (dc.supplyState > 0) 0.1 else 0.0

        return prob
    }

    /**
     * Affect the destination city on successful sabotage.
     * Returns a map of city field deltas (e.g., "agri" to -amount).
     * Keys starting with "_" are metadata, not city changes.
     */
    protected open fun affectDestCity(rng: Random, injuryCount: Int): Map<String, Any> {
        val dc = destPlanet!!
        val agriAmount = min(
            max(rng.nextInt(env.sabotageDamageMin, env.sabotageDamageMax + 1), 0),
            dc.production
        )
        val commAmount = min(
            max(rng.nextInt(env.sabotageDamageMin, env.sabotageDamageMax + 1), 0),
            dc.commerce
        )

        pushGlobalActionLog("<G><b>${dc.name}</b></>${josa(dc.name, "이")} 불타고 있습니다.")
        pushLog("<G><b>${dc.name}</b></>에 ${actionName}${josa(actionName, "이")} 성공했습니다. <1>${formatDate()}</>")
        pushLog("도시의 농업이 <C>${agriAmount}</>, 상업이 <C>${commAmount}</>만큼 감소하고, 장수 <C>${injuryCount}</>명이 부상 당했습니다.")

        return mapOf("agri" to -agriAmount, "comm" to -commAmount, "state" to 32)
    }

    /**
     * Calculate and apply injury to dest city generals (legacy: SabotageInjury).
     * Legacy: for each defender in same nation, roll injuryProb (default 0.3, modified by onCalcStat).
     * On hit: injury +1..16 (capped at 80), crew*0.98, atmos*0.98, train*0.98.
     *
     * Directly modifies the defender General entities (saved by CommandExecutor).
     * Returns the number of injured generals.
     */
    protected fun calculateAndApplyInjuries(rng: Random): Int {
        if (!injuryGeneral) return 0

        val dc = destPlanet ?: return 0
        val defenders = destPlanetOfficers ?: emptyList()
        val destFaction = if (dc.factionId != 0L) {
            services?.factionRepository?.findById(dc.factionId)?.orElse(null)
        } else null
        var injuryCount = 0

        for (defender in defenders) {
            if (defender.factionId != dc.factionId) continue

            // Legacy: injuryProb = 0.3, then onCalcStat($general, 'injuryProb', $injuryProb)
            val defenderNation = if (defender.factionId == dc.factionId) destFaction else if (defender.factionId != 0L) {
                services?.factionRepository?.findById(defender.factionId)?.orElse(null)
            } else null
            val defenderModifiers = services?.modifierService?.getModifiers(defender, defenderNation) ?: emptyList()
            val baseInjuryStat = StatContext(injuryProb = INJURY_PROB_DEFAULT)
            val modifiedInjuryStat = defenderModifiers.fold(baseInjuryStat) { stat, mod -> mod.onCalcStat(stat) }
            val injuryProb = modifiedInjuryStat.injuryProb
            if (rng.nextDouble() >= injuryProb) continue

            val injuryAmount = rng.nextInt(1, 17) // 1-16
            defender.injury = min(defender.injury.toInt() + injuryAmount, INJURY_MAX).toShort()
            defender.ships = (defender.ships * 0.98).toInt()
            defender.morale = (defender.morale * 0.98).toInt().toShort()
            defender.training = (defender.training * 0.98).toInt().toShort()

            injuryCount++
        }

        return injuryCount
    }

    /**
     * Check if the general's consumable item triggers on sabotage success.
     * Legacy: $itemObj->tryConsumeNow($general, 'OfficerCommand', '계략')
     * Returns true if item should be consumed, plus the item's display name.
     */
    protected fun checkConsumableItem(): Pair<Boolean, String?> {
        if (general.accessoryCode == "None") return Pair(false, null)

        val itemCode = general.accessoryCode
        val itemModifier = modifiers.find { it.code == itemCode }

        if (itemModifier is ConsumableItem && itemModifier.effect == "sabotageSuccess") {
            val meta = ItemModifiers.getMeta(itemCode)
            return Pair(true, meta?.rawName ?: itemCode)
        }

        return Pair(false, null)
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val dc = destPlanet!!
        val destCityName = dc.name

        // Distance factor (legacy: searchDistance, default 99 if not found)
        val dist = getDistanceTo(dc.id) ?: 99

        val attackProb = calcAttackProb()
        val defenceProb = calcDefenceProb()
        var prob = SABOTAGE_DEFAULT_PROB + attackProb - defenceProb
        prob /= dist
        prob = max(0.0, min(MAX_SUCCESS_PROB, prob))

        val cost = getCost()

        if (rng.nextDouble() >= prob) {
            pushLog("<G><b>${destCityName}</b></>에 ${actionName}${josa(actionName, "이")} 실패했습니다. <1>$date</>")
            pushHistoryLog("<G><b>${destCityName}</b></>에 ${actionName}${josa(actionName, "이")} 실패했습니다. <1>$date</>")
            pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>에 ${actionName}${josa(actionName, "을")} 시도했으나 실패했습니다.")

            val exp = rng.nextInt(1, 101)
            val ded = rng.nextInt(1, 71)

            return CommandResult(
                success = false,
                logs = logs,
                message = """{"statChanges":{"gold":${-cost.funds},"rice":${-cost.supplies},"experience":$exp,"dedication":$ded,"${getStatExpKey()}":1}}"""
            )
        }

        // Calculate and apply injuries to defending generals (modifies entities directly)
        val injuryCount = calculateAndApplyInjuries(rng)

        val cityChanges = affectDestCity(rng, injuryCount)

        val exp = rng.nextInt(201, 301)
        val ded = rng.nextInt(141, 211)

        // Check consumable item (legacy: tryConsumeNow + deleteItem)
        val (consumeItem, consumeItemName) = checkConsumableItem()
        if (consumeItem && consumeItemName != null) {
            pushLog("<C>${consumeItemName}</>${josa(consumeItemName, "을")} 사용!")
        }

        // Build city changes JSON (non-underscore keys for destCityChanges)
        val publicCityChanges = cityChanges.filterKeys { !it.startsWith("_") }
        pushHistoryLog("<G><b>${destCityName}</b></>에 ${actionName}${josa(actionName, "이")} 성공했습니다. <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${destCityName}</b></>에 ${actionName}${josa(actionName, "을")} 성공했습니다.")

        // Extra metadata from affectDestCity (탈취 resource transfer etc.)
        val extraMap = cityChanges.filterKeys { it.startsWith("_") }

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"gold":${-cost.funds},"rice":${-cost.supplies},"experience":$exp,"dedication":$ded,"${getStatExpKey()}":1}""")

                // destCityChanges
                append(""","destCityChanges":{"cityId":${dc.id}""")
                for ((key, value) in publicCityChanges) {
                    when (value) {
                        is Double -> append(",\"$key\":$value")
                        is Float -> append(",\"$key\":$value")
                        else -> append(",\"$key\":$value")
                    }
                }
                append("}")

                // Own nation resource changes (탈취)
                val ownNationGold = extraMap["_nationShareGold"]
                val ownNationRice = extraMap["_nationShareRice"]
                if (ownNationGold != null || ownNationRice != null) {
                    append(",\"ownNationChanges\":{")
                    val parts = mutableListOf<String>()
                    if (ownNationGold != null) parts.add("\"gold\":$ownNationGold")
                    if (ownNationRice != null) parts.add("\"rice\":$ownNationRice")
                    append(parts.joinToString(","))
                    append("}")
                }

                // Dest nation resource changes (탈취)
                val stolenGold = extraMap["_stolenGold"]
                val stolenRice = extraMap["_stolenRice"]
                val isSupplied = extraMap["_supplied"]
                if (isSupplied != null && stolenGold != null && stolenRice != null) {
                    append(",\"destNationChanges\":{\"gold\":${-(stolenGold as Number).toInt()},\"rice\":${-(stolenRice as Number).toInt()}}")
                }

                // General resource share (탈취: general gets 30%)
                val generalGold = extraMap["_generalShareGold"]
                val generalRice = extraMap["_generalShareRice"]
                if (generalGold != null || generalRice != null) {
                    // Add to statChanges is already emitted, so we use a secondary statChanges merge
                    // Actually, append general gold/rice as additional statChanges
                    // We can't re-emit statChanges, so embed in extra
                    append(",\"extraStatChanges\":{")
                    val parts = mutableListOf<String>()
                    if (generalGold != null) parts.add("\"gold\":$generalGold")
                    if (generalRice != null) parts.add("\"rice\":$generalRice")
                    append(parts.joinToString(","))
                    append("}")
                }

                // Consumable item
                if (consumeItem) {
                    append(",\"consumeItem\":true")
                }

                append("}")
            }
        )
    }
}
