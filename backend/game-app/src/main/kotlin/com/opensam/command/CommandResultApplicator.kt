package com.opensam.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.opensam.command.util.StatChangeUtil
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation

/**
 * CommandResult.message JSON을 파싱하여 엔티티에 적용하는 유틸리티.
 *
 * 일반 장수 커맨드는 run()에서 직접 엔티티를 수정하지 않고,
 * JSON 형태의 델타값을 반환한다. 이 클래스가 해당 델타를 실제 엔티티에 반영한다.
 *
 * 국가 커맨드는 run()에서 직접 엔티티를 수정하므로 이 클래스를 사용하지 않는다.
 */
object CommandResultApplicator {

    private val mapper = jacksonObjectMapper()

    /** SET 연산 필드 (delta가 아닌 직접 대입) */
    private val SET_FIELDS = setOf(
        "crewType",
        "nation",
        "officerLevel",
        "officerCity",
        "troop",
        "killTurn",
    )

    /**
     * CommandResult.message JSON을 파싱하여 엔티티들에 적용한다.
     * 실패한 커맨드나 message가 없는 경우는 무시한다.
     */
    fun apply(
        result: CommandResult,
        general: General,
        city: City?,
        nation: Nation?,
        destGeneral: General? = null,
        destCity: City? = null,
        destNation: Nation? = null,
    ) {
        val message = result.message ?: return
        if (!result.success) return

        val json: Map<String, Any> = try {
            mapper.readValue(message)
        } catch (_: Exception) {
            return
        }

        readStringAnyMap(json["statChanges"])?.let { applyStatChanges(general, it) }

        readStringAnyMap(json["cityChanges"])?.let {
            if (city != null) {
                applyCityChanges(city, it)
                if (readBoolean(it["claimCity"]) == true) {
                    val claimNationId = nation?.id ?: general.nationId
                    if (claimNationId > 0L) {
                        city.nationId = claimNationId
                    }
                }
            }
        }

        readStringAnyMap(json["nationChanges"])?.let {
            if (nation != null) applyNationChanges(nation, it)
        }

        readStringAnyMap(json["destGeneralChanges"])?.let {
            if (destGeneral != null) applyStatChanges(destGeneral, it)
        }

        readStringAnyMap(json["destCityChanges"])?.let {
            if (destCity != null) applyCityChanges(destCity, it)
        }

        readStringAnyMap(json["destNationChanges"])?.let {
            if (destNation != null) applyNationChanges(destNation, it)
        }

        readStringAnyMap(json["dexChanges"])?.let { applyDexChanges(general, it) }

        // Extra stat changes (e.g., 탈취 general's share of stolen resources)
        readStringAnyMap(json["extraStatChanges"])?.let { applyStatChanges(general, it) }

        (json["setPermission"] as? String)?.let { general.permission = it }
        (json["setBelong"] as? Number)?.let { general.belong = it.toShort() }
        (json["setMakeLimit"] as? Number)?.let { general.makeLimit = it.toShort() }

        if (json["leaveNation"] == true) {
            general.nationId = 0L
            general.troopId = 0L
        }

        if (json["resetOfficer"] == true) {
            general.officerLevel = 0
            general.officerCity = 0
            general.permission = "normal"
        }

        if (json["moveToCityOfLord"] == true) {
            val capitalId = destNation?.capitalCityId
            if (capitalId != null && capitalId > 0L) {
                general.cityId = capitalId
            }
        }

        // Consumable item: delete item on use (legacy: tryConsumeNow + deleteItem)
        if (json["consumeItem"] == true) {
            general.itemCode = "None"
        }

        // Own nation changes (e.g., 탈취 resource transfer to own nation)
        readStringAnyMap(json["ownNationChanges"])?.let {
            if (nation != null) applyNationChanges(nation, it)
        }

        // Handle cityId in statChanges → move general to new city (legacy: 이동/강행/귀환)
        readStringAnyMap(json["statChanges"])?.let { changes ->
            val cityIdRaw = changes["cityId"] ?: changes["city"]
            if (cityIdRaw != null) {
                val id = when (cityIdRaw) {
                    is Number -> cityIdRaw.toLong()
                    is String -> cityIdRaw.toLongOrNull()
                    else -> null
                }
                if (id != null && id > 0) {
                    general.cityId = id
                }
            }
        }

        // Handle inheritancePoint: {"key": "active_action", "amount": 1}
        readStringAnyMap(json["inheritancePoint"])?.let { ip ->
            val key = ip["key"] as? String ?: return@let
            val amount = (ip["amount"] as? Number)?.toInt() ?: 1
            val inheritMeta = getOrCreateMutableStringAnyMap(general.meta, "inheritancePoints")
            inheritMeta[key] = ((inheritMeta[key] as? Number)?.toInt() ?: 0) + amount
        }

        // Handle cityStateUpdate: {"cityId": X, "state": 43, "term": 3}
        // Updates the dest city's state/term for battle preparation
        readStringAnyMap(json["cityStateUpdate"])?.let { csu ->
            val targetCityId = when (val raw = csu["cityId"]) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            val stateVal = (csu["state"] as? Number)?.toShort()
            val termVal = (csu["term"] as? Number)?.toShort()
            if (targetCityId != null && destCity != null && destCity.id == targetCityId) {
                if (stateVal != null) destCity.state = stateVal
                if (termVal != null) destCity.term = termVal
            }
        }

        // Handle checkStatChange: check if stat exp crossed level threshold
        if (json["checkStatChange"] == true) {
            StatChangeUtil.checkStatChange(general)
        }
    }

    private fun applyStatChanges(general: General, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            val value = (rawValue as? Number) ?: continue
            if (key == "experienceMultiplier") {
                general.experience = maxOf(0, (general.experience * value.toDouble()).toInt())
                continue
            }
            if (key in SET_FIELDS) {
                applyStatSet(general, key, value.toInt())
            } else {
                applyStatDelta(general, key, value.toInt())
            }
        }
    }

    private fun applyStatSet(general: General, key: String, value: Int) {
        when (key) {
            "crewType" -> general.crewType = value.toShort()
            "nation" -> general.nationId = value.toLong()
            "officerLevel" -> general.officerLevel = value.toShort()
            "officerCity" -> general.officerCity = value
            "troop" -> general.troopId = value.toLong()
            "killTurn" -> general.killTurn = value.toShort()
        }
    }

    private fun applyStatDelta(general: General, key: String, delta: Int) {
        when (key) {
            "gold" -> general.gold = maxOf(0, general.gold + delta)
            "rice" -> general.rice = maxOf(0, general.rice + delta)
            "crew" -> general.crew = maxOf(0, general.crew + delta)
            "train" -> general.train = maxOf(0, minOf(100, general.train + delta)).toShort()
            "atmos" -> general.atmos = maxOf(0, minOf(100, general.atmos + delta)).toShort()
            "experience" -> general.experience = maxOf(0, general.experience + delta)
            "dedication" -> general.dedication = maxOf(0, general.dedication + delta)
            "leadershipExp" -> general.leadershipExp = (general.leadershipExp + delta).toShort()
            "strengthExp" -> general.strengthExp = (general.strengthExp + delta).toShort()
            "intelExp" -> general.intelExp = (general.intelExp + delta).toShort()
            "injury" -> general.injury = maxOf(0, minOf(100, general.injury + delta)).toShort()
            "belong" -> general.belong = (general.belong + delta).toShort()
            "betray" -> general.betray = (general.betray + delta).toShort()
        }
    }

    private fun applyCityChanges(city: City, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            val num = rawValue as? Number ?: continue
            when (key) {
                // trust uses float delta for decimal precision (legacy: 선동 민심 1 decimal)
                "trust" -> {
                    val delta = num.toFloat()
                    city.trust = maxOf(0f, minOf(100f, city.trust + delta))
                }
                else -> {
                    val delta = num.toInt()
                    when (key) {
                        "agri" -> city.agri = maxOf(0, minOf(city.agriMax, city.agri + delta))
                        "comm" -> city.comm = maxOf(0, minOf(city.commMax, city.comm + delta))
                        "secu" -> city.secu = maxOf(0, minOf(city.secuMax, city.secu + delta))
                        "def" -> city.def = maxOf(0, minOf(city.defMax, city.def + delta))
                        "wall" -> city.wall = maxOf(0, minOf(city.wallMax, city.wall + delta))
                        "pop" -> city.pop = maxOf(0, minOf(city.popMax, city.pop + delta))
                        "dead" -> city.dead = maxOf(0, city.dead + delta)
                        "trade" -> city.trade = maxOf(0, city.trade + delta)
                        "state" -> city.state = delta.toShort()
                    }
                }
            }
        }
    }

    private fun applyNationChanges(nation: Nation, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            when (key) {
                "gold" -> (rawValue as? Number)?.toInt()?.let { nation.gold = maxOf(0, nation.gold + it) }
                "rice" -> (rawValue as? Number)?.toInt()?.let { nation.rice = maxOf(0, nation.rice + it) }
                "tech" -> (rawValue as? Number)?.toInt()?.let { nation.tech = maxOf(0f, nation.tech + it) }
                "power" -> (rawValue as? Number)?.toInt()?.let { nation.power = maxOf(0, nation.power + it) }
                "chiefGeneralId" -> (rawValue as? Number)?.toLong()?.let { nation.chiefGeneralId = it }
                "nationName", "name" -> (rawValue as? String)?.takeIf { it.isNotBlank() }?.let { nation.name = it }
                "nationType", "type", "typeCode" -> (rawValue as? String)?.let { nation.typeCode = normalizeNationTypeCode(it) }
                "level" -> (rawValue as? Number)?.toInt()?.let { nation.level = it.toShort() }
                "capital", "capitalCityId" -> (rawValue as? Number)?.toLong()?.let { nation.capitalCityId = it }
                "color" -> (rawValue as? String)?.takeIf { it.isNotBlank() }?.let { nation.color = it }
                "colorType" -> {
                    val idx = (rawValue as? Number)?.toInt() ?: (rawValue as? String)?.toIntOrNull()
                    if (idx != null) {
                        nation.color = resolveNationColor(idx)
                    }
                }
                "secretLimit" -> (rawValue as? Number)?.toInt()?.let { nation.secretLimit = it.toShort() }
                "can_국기변경" -> (rawValue as? Number)?.toInt()?.let { nation.meta["can_국기변경"] = it }
                "can_무작위수도이전" -> (rawValue as? Number)?.toInt()?.let { nation.meta["can_무작위수도이전"] = it }
                "aux" -> {
                    val aux = readStringAnyMap(rawValue) ?: emptyMap()
                    if (aux.isNotEmpty()) {
                        nation.meta.putAll(aux)
                    }
                }
            }
        }
    }

    private fun readBoolean(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> when (raw.trim().lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun normalizeNationTypeCode(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "che_군벌"
        return if (trimmed.startsWith("che_")) trimmed else "che_$trimmed"
    }

    private fun resolveNationColor(colorType: Int): String {
        return NATION_COLORS.getOrElse(colorType) { NATION_COLORS.first() }
    }

    /** 병종 숙련도 변경. crewType → dex1~5 매핑. */
    private fun applyDexChanges(general: General, changes: Map<String, Any>) {
        val crewType = (changes["crewType"] as? Number)?.toInt() ?: return
        val amount = (changes["amount"] as? Number)?.toInt() ?: return
        when (crewType) {
            0 -> general.dex1 = maxOf(0, general.dex1 + amount)
            1 -> general.dex2 = maxOf(0, general.dex2 + amount)
            2 -> general.dex3 = maxOf(0, general.dex3 + amount)
            3 -> general.dex4 = maxOf(0, general.dex4 + amount)
            4 -> general.dex5 = maxOf(0, general.dex5 + amount)
        }
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun getOrCreateMutableStringAnyMap(container: MutableMap<String, Any>, key: String): MutableMap<String, Any> {
        val current = container[key]
        if (current is MutableMap<*, *>) {
            val typed = mutableMapOf<String, Any>()
            current.forEach { (k, v) ->
                if (k is String && v != null) {
                    typed[k] = v
                }
            }
            container[key] = typed
            return typed
        }

        if (current is Map<*, *>) {
            val typed = mutableMapOf<String, Any>()
            current.forEach { (k, v) ->
                if (k is String && v != null) {
                    typed[k] = v
                }
            }
            container[key] = typed
            return typed
        }

        val created = mutableMapOf<String, Any>()
        container[key] = created
        return created
    }

    private val NATION_COLORS = listOf(
        "#FF0000", "#800000", "#A0522D", "#FF6347", "#FFA500",
        "#FFDAB9", "#FFD700", "#FFFF00", "#7CFC00", "#00FF00",
        "#808000", "#008000", "#2E8B57", "#008080", "#20B2AA",
        "#6495ED", "#7FFFD4", "#AFEEEE", "#87CEEB", "#00FFFF",
        "#00BFFF", "#0000FF", "#000080", "#483D8B", "#7B68EE",
        "#BA55D3", "#800080", "#FF00FF", "#FFC0CB", "#F5F5DC",
        "#E0FFFF", "#FFFFFF", "#A9A9A9",
    )
}
