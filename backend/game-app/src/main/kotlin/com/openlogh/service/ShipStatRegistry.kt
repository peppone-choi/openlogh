package com.openlogh.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct

/**
 * ship_stats_empire.json / ship_stats_alliance.json / ground_unit_stats.json을
 * 애플리케이션 시작 시 로드하여 in-memory 캐시에 보관한다.
 *
 * JSON 구조:
 *   { "shipClasses": [ { "classId": "battleship", "subtypes": [ { "subtype": "I", ... } ] } ] }
 *
 * 내부 키 형태: "BATTLESHIP_I", "CRUISER_II" 등 (ShipSubtype enum 이름과 일치).
 * TacticalBattleService가 전투 초기화 시 ShipUnit 서브타입 스탯을 조회하는 데 사용된다.
 */
@Component
class ShipStatRegistry {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = jacksonObjectMapper()

    /** 서브타입 코드(String) → 스탯 맵 */
    private val empireStats   = mutableMapOf<String, ShipSubtypeStat>()
    private val allianceStats = mutableMapOf<String, ShipSubtypeStat>()
    private val groundStats   = mutableMapOf<String, GroundUnitStat>()

    @PostConstruct
    fun load() {
        loadShipStats("data/ship_stats_empire.json",   empireStats)
        loadShipStats("data/ship_stats_alliance.json", allianceStats)
        loadGroundStats("data/ground_unit_stats.json")
        log.info("ShipStatRegistry loaded: empire={}, alliance={}, ground={}",
            empireStats.size, allianceStats.size, groundStats.size)
    }

    /**
     * JSON 내 subtype 값("I", "II", "fast" 등)을 ShipSubtype enum 이름으로 변환.
     * 예: classId="battleship", subtype="I"  → "BATTLESHIP_I"
     *     classId="battleship", subtype="fast" → "FAST_BATTLESHIP"
     */
    private fun buildSubtypeKey(classId: String, subtypeStr: String): String {
        val cls = classId.uppercase()
        val sub = subtypeStr.uppercase()
        return when {
            sub.all { it.isDigit() || it == '_' } -> "${cls}_${sub}"
            // Named subtypes: e.g. fast → FAST_BATTLESHIP, strike → STRIKE_CRUISER
            sub == "FAST"        -> "FAST_${cls}"
            sub == "STRIKE"      -> "STRIKE_${cls}"
            sub == "TORPEDO_BOAT_CARRIER" -> "TORPEDO_BOAT_CARRIER"
            sub == "TORPEDO"     -> "TORPEDO_BOAT_CARRIER"
            else                 -> "${cls}_${sub}"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadShipStats(path: String, target: MutableMap<String, ShipSubtypeStat>) {
        try {
            val stream = javaClass.classLoader.getResourceAsStream(path)
                ?: run {
                    log.warn("ShipStatRegistry: resource not found: $path")
                    return
                }
            val root: Map<String, Any> = mapper.readValue(stream)
            val shipClasses = root["shipClasses"] as? List<*> ?: return

            for (classEntry in shipClasses) {
                val classMap = classEntry as? Map<*, *> ?: continue
                val classId  = classMap["classId"] as? String ?: continue
                val subtypes = classMap["subtypes"] as? List<*> ?: continue

                for (subtypeEntry in subtypes) {
                    val st = subtypeEntry as? Map<*, *> ?: continue
                    val subtypeStr = st["subtype"] as? String ?: continue
                    val key = buildSubtypeKey(classId, subtypeStr)

                    // armor / shield nested objects
                    val armorMap   = st["armor"]  as? Map<*, *>
                    val shieldMap  = st["shield"] as? Map<*, *>
                    val beamMap    = st["beam"]   as? Map<*, *>
                    val gunMap     = st["gun"]    as? Map<*, *>

                    val armorFront   = (armorMap?.get("front")      as? Number)?.toInt() ?: 0
                    val shieldCap    = (shieldMap?.get("capacity")  as? Number)?.toInt() ?: 0
                    val beamDmg      = (beamMap?.get("damage")      as? Number)?.toInt() ?: 0
                    val gunDmg       = (gunMap?.get("damage")       as? Number)?.toInt() ?: 0
                    val maxSpeed     = (st["maxSpeed"]              as? Number)?.toInt() ?: 0
                    val crewPerUnit  = (st["crewPerUnit"]           as? Number)?.toInt() ?: 0
                    val cargoCapacity = (st["cargoCapacity"]        as? Number)?.toInt() ?: 0

                    target[key] = ShipSubtypeStat(
                        armor          = armorFront,
                        shield         = shieldCap,
                        weaponPower    = beamDmg + gunDmg,
                        speed          = maxSpeed,
                        crewCapacity   = crewPerUnit,
                        supplyCapacity = cargoCapacity,
                        beamPower      = beamDmg,
                        gunPower       = gunDmg,
                        hullPoints     = armorFront,  // approximation until hull field added to JSON
                    )
                }
            }
        } catch (e: Exception) {
            log.warn("ShipStatRegistry: failed to load $path — ${e.message}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadGroundStats(path: String) {
        try {
            val stream = javaClass.classLoader.getResourceAsStream(path)
                ?: run {
                    log.warn("ShipStatRegistry: resource not found: $path")
                    return
                }
            val root: Map<String, Any> = mapper.readValue(stream)
            // Structure: { "factions": { "empire": { "units": [ { "typeId": "...", ... } ] } } }
            val factions = root["factions"] as? Map<*, *>
            factions?.forEach { (_, factionVal) ->
                val factionMap = factionVal as? Map<*, *> ?: return@forEach
                val units = factionMap["units"] as? List<*> ?: return@forEach
                for (unitEntry in units) {
                    val u = unitEntry as? Map<*, *> ?: continue
                    val typeId  = u["typeId"] as? String ?: continue
                    val atk     = (u["groundAttack"]  as? Number)?.toInt() ?: 0
                    val def     = (u["groundDefense"] as? Number)?.toInt() ?: 0
                    groundStats[typeId] = GroundUnitStat(attack = atk, defense = def, morale = 50)
                }
            }
        } catch (e: Exception) {
            log.warn("ShipStatRegistry: failed to load $path — ${e.message}")
        }
    }

    /**
     * 함종 서브타입 스탯 조회.
     * @param subtypeName ShipSubtype.name (예: "BATTLESHIP_I")
     * @param factionType "empire" 또는 "alliance"
     */
    fun getShipStat(subtypeName: String, factionType: String): ShipSubtypeStat? =
        when (factionType.lowercase()) {
            "empire"   -> empireStats[subtypeName]   ?: allianceStats[subtypeName]
            "alliance" -> allianceStats[subtypeName] ?: empireStats[subtypeName]
            else       -> empireStats[subtypeName]   ?: allianceStats[subtypeName]
        }

    fun getGroundStat(typeName: String): GroundUnitStat? = groundStats[typeName]

    fun allEmpireStats():   Map<String, ShipSubtypeStat> = empireStats.toMap()
    fun allAllianceStats(): Map<String, ShipSubtypeStat> = allianceStats.toMap()
}

data class ShipSubtypeStat(
    val armor: Int,
    val shield: Int,
    val weaponPower: Int,
    val speed: Int,
    val crewCapacity: Int,
    val supplyCapacity: Int,
    val beamPower: Int,
    val gunPower: Int,
    val hullPoints: Int,
)

data class GroundUnitStat(
    val attack: Int,
    val defense: Int,
    val morale: Int,
)
