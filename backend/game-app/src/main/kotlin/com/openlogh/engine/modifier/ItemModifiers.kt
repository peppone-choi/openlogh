package com.openlogh.engine.modifier

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.openlogh.model.CrewType
import kotlin.math.floor

object ItemModifiers {

    private val items: Map<String, ActionModifier>
    private val itemMeta: Map<String, ItemMeta>
    private val itemTriggerTypes: Map<String, String>
    private val itemKillRice: Map<String, Double>

    init {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val stream = ItemModifiers::class.java.classLoader.getResourceAsStream("data/items.json")

        if (stream == null) {
            // 삼국지 items.json removed in cleanup (commit aabd97d6, 2026-04-06).
            // gin7 LOGH 함선/장비 시스템은 별도 작업 — 그때까지 legacy 호출은 null 반환으로 graceful degrade.
            // 모든 호출부(ItemService, ModifierService, OfficerTrigger, FrontInfoService)는
            // 이미 null-safe(`?.let`, `?: itemCode`)하게 되어 있음.
            items = emptyMap()
            itemMeta = emptyMap()
            itemTriggerTypes = emptyMap()
            itemKillRice = emptyMap()
        } else {
            val data: Map<String, List<Map<String, Any>>> = mapper.readValue(
                stream, object : TypeReference<Map<String, List<Map<String, Any>>>>() {}
            )

            val resultItems = mutableMapOf<String, ActionModifier>()
            val resultMeta = mutableMapOf<String, ItemMeta>()
            val resultTriggerTypes = mutableMapOf<String, String>()
            val resultKillRice = mutableMapOf<String, Double>()

            // Weapons: strength = grade
            for (item in data["weapons"] ?: emptyList()) {
                val code = item["code"] as String
                val rawName = item["rawName"] as String
                val grade = (item["grade"] as Number).toInt()
                val name = "$rawName(+$grade)"
                resultItems[code] = StatItem(code, name, strength = grade.toDouble())
                resultMeta[code] = ItemMeta(code, rawName, "weapon", grade,
                    (item["cost"] as Number).toInt(), item["buyable"] as Boolean,
                    (item["rarity"] as Number).toInt())
            }

            // Books: intel = grade
            for (item in data["books"] ?: emptyList()) {
                val code = item["code"] as String
                val rawName = item["rawName"] as String
                val grade = (item["grade"] as Number).toInt()
                val name = "$rawName(+$grade)"
                resultItems[code] = StatItem(code, name, intel = grade.toDouble())
                resultMeta[code] = ItemMeta(code, rawName, "book", grade,
                    (item["cost"] as Number).toInt(), item["buyable"] as Boolean,
                    (item["rarity"] as Number).toInt())
            }

            // Horses: leadership = grade
            for (item in data["horses"] ?: emptyList()) {
                val code = item["code"] as String
                val rawName = item["rawName"] as String
                val grade = (item["grade"] as Number).toInt()
                val name = "$rawName(+$grade)"
                resultItems[code] = StatItem(code, name, leadership = grade.toDouble())
                resultMeta[code] = ItemMeta(code, rawName, "horse", grade,
                    (item["cost"] as Number).toInt(), item["buyable"] as Boolean,
                    (item["rarity"] as Number).toInt())
                (item["killRice"] as? Number)?.toDouble()?.let { resultKillRice[code] = it }
            }

            // Misc items
            for (item in data["misc"] ?: emptyList()) {
                val code = item["code"] as String
                val rawName = item["rawName"] as String
                val consumable = item["consumable"] as? Boolean ?: false
                val cost = (item["cost"] as Number).toInt()
                val buyable = item["buyable"] as? Boolean ?: false
                val rarity = (item["rarity"] as? Number)?.toInt() ?: 0
                val info = item["info"] as? String ?: ""
                val triggerType = item["triggerType"] as? String

                if (triggerType != null) {
                    resultTriggerTypes[code] = triggerType
                }
                (item["killRice"] as? Number)?.toDouble()?.let { resultKillRice[code] = it }

                if (consumable) {
                    resultItems[code] = ConsumableItem(
                        code = code,
                        name = "$rawName(${info.take(10)})",
                        maxUses = (item["maxUses"] as Number).toInt(),
                        effect = item["effect"] as String,
                        value = (item["value"] as Number).toInt(),
                    )
                } else {
                    val statMap = readNumberMap(item["stat"])
                    val opposeStatMap = readNumberMap(item["opposeStat"])

                    resultItems[code] = MiscItem(
                        code = code,
                        name = rawName,
                        statMods = statMap.mapValues { it.value.toDouble() },
                        opposeStatMods = opposeStatMap.mapValues { it.value.toDouble() },
                        triggerType = triggerType,
                    )
                }
                resultMeta[code] = ItemMeta(code, rawName, "misc", 0, cost, buyable, rarity,
                    consumable, info)
            }

            items = resultItems
            itemMeta = resultMeta
            itemTriggerTypes = resultTriggerTypes
            itemKillRice = resultKillRice
        }
    }

    private fun readNumberMap(raw: Any?): Map<String, Number> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Number>()
        raw.forEach { (key, value) ->
            if (key is String && value is Number) {
                result[key] = value
            }
        }
        return result
    }

    fun get(code: String): ActionModifier? = items[code]
    fun getAll(): Map<String, ActionModifier> = items
    fun getMeta(code: String): ItemMeta? = itemMeta[code]
    fun getAllMeta(): Map<String, ItemMeta> = itemMeta
    fun getByCategory(category: String): List<ItemMeta> =
        itemMeta.values.filter { it.category == category }

    fun getTriggerType(code: String): String? = itemTriggerTypes[code]

    fun getKillRice(code: String): Double = itemKillRice[code] ?: 1.0

    /**
     * Legacy battle trigger lookup -- stub.
     * TODO: gin7 전술전 트리거 시스템으로 대체 예정. BattleTrigger/BattleTriggerRegistry 삭제됨.
     */
    fun getBattleTriggerType(code: String): String? = itemTriggerTypes[code]
}

data class ItemMeta(
    val code: String,
    val rawName: String,
    val category: String,
    val grade: Int,
    val cost: Int,
    val buyable: Boolean,
    val rarity: Int,
    val consumable: Boolean = false,
    val info: String = "",
)

class StatItem(
    override val code: String,
    override val name: String,
    private val leadership: Double = 0.0,
    private val strength: Double = 0.0,
    private val intel: Double = 0.0,
    private val dodge: Double = 0.0,
    private val critical: Double = 0.0,
    private val magic: Double = 0.0,
    private val warPower: Double = 1.0,
) : ActionModifier {
    override fun onCalcStat(stat: StatContext) = stat.copy(
        leadership = stat.leadership + leadership,
        strength = stat.strength + strength,
        intel = stat.intel + intel,
        dodgeChance = stat.dodgeChance + dodge,
        criticalChance = stat.criticalChance + critical,
        magicChance = stat.magicChance + magic,
        warPower = stat.warPower * warPower,
    )
}

data class ConsumableItem(
    override val code: String,
    override val name: String,
    val maxUses: Int,
    val effect: String,
    val value: Int,
) : ActionModifier

class MiscItem(
    override val code: String,
    override val name: String,
    private val statMods: Map<String, Double> = emptyMap(),
    private val opposeStatMods: Map<String, Double> = emptyMap(),
    val triggerType: String? = null,
) : ActionModifier {
    // TODO Phase 3: 삼국지 병종 상성(CrewType) 로직 제거됨. gin7 함종 상성으로 대체 예정.
    private fun parseCrewType(raw: String): CrewType? = null

    private fun isRegionalOrCityCrewType(raw: String): Boolean = false

    override fun onCalcStat(stat: StatContext): StatContext {
        var s = stat
        statMods["leadership"]?.let { s = s.copy(leadership = s.leadership + it) }
        statMods["strength"]?.let { s = s.copy(strength = s.strength + it) }
        statMods["intel"]?.let { s = s.copy(intel = s.intel + it) }
        statMods["leadershipPercent"]?.let { s = s.copy(leadership = s.leadership * (1.0 + it)) }
        statMods["dodgeChance"]?.let { s = s.copy(dodgeChance = s.dodgeChance + it) }
        statMods["criticalChance"]?.let { s = s.copy(criticalChance = s.criticalChance + it) }
        statMods["bonusTrain"]?.let { s = s.copy(bonusTrain = s.bonusTrain + it) }
        statMods["bonusAtmos"]?.let { s = s.copy(bonusAtmos = s.bonusAtmos + it) }
        statMods["magicTrialProb"]?.let { s = s.copy(magicTrialProb = s.magicTrialProb + it) }
        statMods["magicSuccessProb"]?.let { s = s.copy(magicSuccessProb = s.magicSuccessProb + it) }
        statMods["magicSuccessDamage"]?.let { s = s.copy(magicSuccessDamage = s.magicSuccessDamage * it) }
        statMods["dexMultiplier"]?.let { s = s.copy(dexMultiplier = s.dexMultiplier * it) }
        statMods["expMultiplier"]?.let { s = s.copy(expMultiplier = s.expMultiplier * it) }
        statMods["injuryProb"]?.let { s = s.copy(injuryProb = s.injuryProb + it) }
        statMods["initWarPhase"]?.let { s = s.copy(initWarPhase = s.initWarPhase + it) }
        statMods["sabotageDefence"]?.let { s = s.copy(sabotageDefence = s.sabotageDefence + it) }

        if (triggerType == "progressiveStat") {
            val relYear = (s.year - s.startYear).coerceAtLeast(0)
            val progressiveBonus = floor(relYear / 4.0).coerceAtMost(12.0)
            if (progressiveBonus > 0) {
                if (statMods.containsKey("leadership")) {
                    s = s.copy(leadership = s.leadership + progressiveBonus)
                }
                if (statMods.containsKey("strength")) {
                    s = s.copy(strength = s.strength + progressiveBonus)
                }
                if (statMods.containsKey("intel")) {
                    s = s.copy(intel = s.intel + progressiveBonus)
                }
            }
        }

        // TODO Phase 3: 삼국지 병종 상성(typeAdvantage, antiRegional) 제거됨. gin7 함종 상성으로 대체 예정.
        // parseCrewType() and isRegionalOrCityCrewType() are stubs returning null/false.

        if (triggerType == "perseverance") {
            val leadership = s.leadership.coerceAtLeast(1.0)
            val crew = (s.hpRatio.coerceIn(0.0, 1.0) * leadership * 100.0).coerceAtLeast(0.0)
            val crewRatio = (crew / (leadership * 100.0)).coerceIn(0.0, 1.0)
            s = s.copy(warPower = s.warPower * (1.0 + 0.6 * (1.0 - crewRatio)))
        }

        if (triggerType == "charge" && s.isAttacker) {
            s = s.copy(warPower = s.warPower * 1.05)
        }

        if (triggerType == "unmatched" && s.isAttacker) {
            s = s.copy(warPower = s.warPower * 1.05)
        }

        if (triggerType == "demonSlayer" && isRegionalOrCityCrewType(s.opponentCrewType)) {
            s = s.copy(warPower = s.warPower * 1.2)
        }

        if (triggerType == "cavalrySkill") {
            s = s.copy(warPower = s.warPower * if (s.isAttacker) 1.2 else 1.1)
        }

        if (triggerType == "footmanSkill") {
            s = s.copy(warPower = s.warPower * if (s.isAttacker) 0.9 else 0.8)
        }

        if (triggerType == "siegeSkill" && isRegionalOrCityCrewType(s.opponentCrewType)) {
            s = s.copy(warPower = s.warPower * 2.0)
        }

        return s
    }

    override fun onCalcDomestic(ctx: DomesticContext): DomesticContext {
        var c = ctx
        statMods["domesticSuccess"]?.let { c = c.copy(successMultiplier = c.successMultiplier + it) }
        statMods["domesticSabotageSuccess"]?.let {
            if (c.actionCode == "계략") c = c.copy(successMultiplier = c.successMultiplier + it)
        }
        statMods["domesticSupplySuccess"]?.let {
            if (c.actionCode == "조달") c = c.copy(successMultiplier = c.successMultiplier + it)
        }
        statMods["domesticSupplyScore"]?.let {
            if (c.actionCode == "조달") c = c.copy(scoreMultiplier = c.scoreMultiplier * it)
        }

        if (triggerType == "recruit") {
            when (c.actionCode) {
                "징병", "모병" -> c = c.copy(trainMultiplier = 70.0, atmosMultiplier = 84.0)
                "징집인구" -> c = c.copy(scoreMultiplier = 0.0)
            }
        }

        return c
    }

    override fun onCalcStrategic(ctx: StrategicContext): StrategicContext {
        var c = ctx
        statMods["strategicDelay"]?.let { c = c.copy(delayMultiplier = c.delayMultiplier * it) }
        return c
    }

    override fun onCalcOpposeStat(stat: StatContext): StatContext {
        var s = stat
        for ((key, value) in opposeStatMods) {
            when (key) {
                "warPower" -> s = s.copy(warPower = s.warPower * value)
                "criticalChance" -> s = s.copy(criticalChance = s.criticalChance + value)
                "magicSuccessProb" -> s = s.copy(magicSuccessProb = s.magicSuccessProb + value)
                "dodgeChance" -> s = s.copy(dodgeChance = s.dodgeChance + value)
            }
        }
        // TODO Phase 3: 삼국지 typeAdvantage 병종 상성 방어 로직 제거됨. gin7 함종 상성으로 대체 예정.
        if (triggerType == "antiRegional" && isRegionalOrCityCrewType(s.opponentCrewType)) {
            s = s.copy(warPower = s.warPower * 0.85)
        }

        if (triggerType == "demonSlayer" && isRegionalOrCityCrewType(s.opponentCrewType)) {
            s = s.copy(warPower = s.warPower * 0.8)
        }

        if (triggerType == "footmanSkill") {
            s = s.copy(warPower = s.warPower * if (s.isAttacker) 0.9 else 0.8)
        }

        return s
    }

}
