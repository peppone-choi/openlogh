package com.openlogh.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openlogh.command.util.StatChangeUtil
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction

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
        "officerPlanet",
        "troop",
        "killTurn",
    )

    /**
     * CommandResult.message JSON을 파싱하여 엔티티들에 적용한다.
     * 실패한 커맨드나 message가 없는 경우는 무시한다.
     */
    fun apply(
        result: CommandResult,
        general: Officer,
        city: Planet?,
        nation: Faction?,
        destOfficer: Officer? = null,
        destPlanet: Planet? = null,
        destFaction: Faction? = null,
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
                    val claimNationId = nation?.id ?: general.factionId
                    if (claimNationId > 0L) {
                        city.factionId = claimNationId
                    }
                }
            }
        }

        readStringAnyMap(json["nationChanges"])?.let {
            if (nation != null) applyNationChanges(nation, it)
        }

        readStringAnyMap(json["destGeneralChanges"])?.let {
            if (destOfficer != null) applyStatChanges(destOfficer, it)
        }

        readStringAnyMap(json["destCityChanges"])?.let {
            if (destPlanet != null) applyCityChanges(destPlanet, it)
        }

        readStringAnyMap(json["destNationChanges"])?.let {
            if (destFaction != null) applyNationChanges(destFaction, it)
        }

        readStringAnyMap(json["dexChanges"])?.let { applyDexChanges(general, it) }

        // Extra stat changes (e.g., 탈취 general's share of stolen resources)
        readStringAnyMap(json["extraStatChanges"])?.let { applyStatChanges(general, it) }

        (json["setPermission"] as? String)?.let { general.permission = it }
        (json["setBelong"] as? Number)?.let { general.belong = it.toShort() }
        (json["setMakeLimit"] as? Number)?.let { general.makeLimit = it.toShort() }

        if (json["leaveNation"] == true) {
            general.factionId = 0L
            general.fleetId = 0L
        }

        if (json["resetOfficer"] == true) {
            general.officerLevel = 0
            general.officerPlanet = 0
            general.permission = "normal"
        }

        if (json["moveToCityOfLord"] == true) {
            val capitalId = destFaction?.capitalPlanetId
            if (capitalId != null && capitalId > 0L) {
                general.planetId = capitalId
            }
        }

        // Consumable item: delete item on use (legacy: tryConsumeNow + deleteItem)
        if (json["consumeItem"] == true) {
            general.accessoryCode = "None"
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
                    general.planetId = id
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
            if (targetCityId != null && destPlanet != null && destPlanet.id == targetCityId) {
                if (stateVal != null) destPlanet.state = stateVal
                if (termVal != null) destPlanet.term = termVal
            }
        }

        // Handle checkStatChange: check if stat exp crossed level threshold
        if (json["checkStatChange"] == true) {
            StatChangeUtil.checkStatChange(general)
        }
    }

    private fun applyStatChanges(general: Officer, changes: Map<String, Any>) {
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

    private fun applyStatSet(general: Officer, key: String, value: Int) {
        when (key) {
            "crewType" -> general.shipClass = value.toShort()
            "nation" -> general.factionId = value.toLong()
            "officerLevel" -> general.officerLevel = value.toShort()
            "officerPlanet" -> general.officerPlanet = value
            "troop" -> general.fleetId = value.toLong()
            "killTurn" -> general.killTurn = value.toShort()
        }
    }

    private fun applyStatDelta(general: Officer, key: String, delta: Int) {
        when (key) {
            "gold" -> general.funds = maxOf(0, general.funds + delta)
            "rice" -> general.supplies = maxOf(0, general.supplies + delta)
            "crew" -> general.ships = maxOf(0, general.ships + delta)
            "train" -> general.training = maxOf(0, minOf(100, general.training + delta)).toShort()
            "atmos" -> general.morale = maxOf(0, minOf(100, general.morale + delta)).toShort()
            "experience" -> general.experience = maxOf(0, general.experience + delta)
            "dedication" -> general.dedication = maxOf(0, general.dedication + delta)
            "leadershipExp" -> general.leadershipExp = (general.leadershipExp + delta).toShort()
            "strengthExp" -> general.commandExp = (general.commandExp + delta).toShort()
            "intelExp" -> general.intelligenceExp = (general.intelligenceExp + delta).toShort()
            "injury" -> general.injury = maxOf(0, minOf(100, general.injury + delta)).toShort()
            "belong" -> general.belong = (general.belong + delta).toShort()
            "betray" -> general.betray = (general.betray + delta).toShort()
        }
    }

    private fun applyCityChanges(city: Planet, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            val num = rawValue as? Number ?: continue
            when (key) {
                // trust uses float delta for decimal precision (legacy: 선동 민심 1 decimal)
                "trust" -> {
                    val delta = num.toFloat()
                    city.approval = maxOf(0f, minOf(100f, city.approval + delta))
                }
                else -> {
                    val delta = num.toInt()
                    when (key) {
                        "agri" -> city.production = maxOf(0, minOf(city.productionMax, city.production + delta))
                        "comm" -> city.commerce = maxOf(0, minOf(city.commerceMax, city.commerce + delta))
                        "secu" -> city.security = maxOf(0, minOf(city.securityMax, city.security + delta))
                        "def" -> city.orbitalDefense = maxOf(0, minOf(city.orbitalDefenseMax, city.orbitalDefense + delta))
                        "wall" -> city.fortress = maxOf(0, minOf(city.fortressMax, city.fortress + delta))
                        "pop" -> city.population = maxOf(0, minOf(city.populationMax, city.population + delta))
                        "dead" -> city.dead = maxOf(0, city.dead + delta)
                        "trade" -> city.tradeRoute = maxOf(0, city.tradeRoute + delta)
                        "state" -> city.state = delta.toShort()
                    }
                }
            }
        }
    }

    private fun applyNationChanges(nation: Faction, changes: Map<String, Any>) {
        for ((key, rawValue) in changes) {
            when (key) {
                "gold" -> (rawValue as? Number)?.toInt()?.let { nation.funds = maxOf(0, nation.funds + it) }
                "rice" -> (rawValue as? Number)?.toInt()?.let { nation.supplies = maxOf(0, nation.supplies + it) }
                "tech" -> (rawValue as? Number)?.toInt()?.let { nation.techLevel = maxOf(0f, nation.techLevel + it) }
                "power" -> (rawValue as? Number)?.toInt()?.let { nation.militaryPower = maxOf(0, nation.militaryPower + it) }
                "chiefGeneralId" -> (rawValue as? Number)?.toLong()?.let { nation.chiefOfficerId = it }
                "factionName", "name" -> (rawValue as? String)?.takeIf { it.isNotBlank() }?.let { nation.name = it }
                "nationType", "type", "typeCode" -> (rawValue as? String)?.let { nation.factionType = normalizeNationTypeCode(it) }
                "level" -> (rawValue as? Number)?.toInt()?.let { nation.factionRank = it.toShort() }
                "capital", "capitalCityId" -> (rawValue as? Number)?.toLong()?.let { nation.capitalPlanetId = it }
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
    private fun applyDexChanges(general: Officer, changes: Map<String, Any>) {
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
