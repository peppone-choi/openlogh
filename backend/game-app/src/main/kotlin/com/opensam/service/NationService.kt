package com.opensam.service

import com.opensam.dto.NationPolicyInfo
import com.opensam.dto.OfficerInfo
import com.opensam.entity.Nation
import com.opensam.entity.General
import com.opensam.entity.WorldState
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.DiplomacyRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.HtmlUtils
@Service
class NationService(
    private val nationRepository: NationRepository,
    private val generalRepository: GeneralRepository,
    private val appUserRepository: AppUserRepository,
    private val officerRankService: OfficerRankService,
    private val cityRepository: CityRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val worldStateRepository: WorldStateRepository,
    private val mapService: MapService,
) {
    private val log = LoggerFactory.getLogger(NationService::class.java)

    data class MutationResult(
        val success: Boolean,
        val reason: String? = null,
        val availableCnt: Int? = null,
    )

    fun listByWorld(worldId: Long): List<Nation> {
        return nationRepository.findByWorldId(worldId)
    }

    fun getById(id: Long): Nation? {
        return nationRepository.findById(id).orElse(null)
    }

    // -- Policy --

    fun getPolicy(nationId: Long): NationPolicyInfo? {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return null
        return NationPolicyInfo(
            rate = nation.rate.toInt(),
            bill = nation.bill.toInt(),
            secretLimit = nation.secretLimit.toInt(),
            strategicCmdLimit = nation.strategicCmdLimit.toInt(),
            notice = readNationNoticeMessage(nation),
            scoutMsg = readNationScoutMessage(nation),
            blockWar = nation.warState.toInt() != 0,
            blockScout = nation.scoutLevel.toInt() != 0,
        )
    }

    fun updatePolicy(nationId: Long, rate: Int?, bill: Int?, secretLimit: Int?, strategicCmdLimit: Int?): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        rate?.let { nation.rate = it.toShort() }
        bill?.let { nation.bill = it.toShort() }
        secretLimit?.let { nation.secretLimit = it.toShort() }
        strategicCmdLimit?.let { nation.strategicCmdLimit = it.toShort() }
        nationRepository.save(nation)
        return true
    }

    fun verifyPolicyAccess(nationId: Long, loginId: String): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val general = generalRepository.findByWorldIdAndUserId(nation.worldId, user.id)
            .firstOrNull { it.nationId == nationId }
            ?: return false
        if (general.penalty["noTopSecret"] == true) {
            return false
        }
        if (general.officerLevel >= 5 && general.penalty["noChief"] == true) {
            return false
        }
        if (general.permission == "ambassador" && general.penalty["noAmbassador"] == true) {
            return false
        }
        return general.officerLevel >= 5 || general.permission == "ambassador"
    }

    fun resolvePolicyActor(nationId: Long, loginId: String): General? {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return null
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        return generalRepository.findByWorldIdAndUserId(nation.worldId, user.id)
            .firstOrNull { it.nationId == nationId }
    }

    fun updateBill(nationId: Long, amount: Int): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        nation.bill = amount.toShort()
        nationRepository.save(nation)
        return true
    }

    fun updateRate(nationId: Long, amount: Int): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        nation.rate = amount.toShort()
        nationRepository.save(nation)
        return true
    }

    fun updateSecretLimit(nationId: Long, amount: Int): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        nation.secretLimit = amount.toShort()
        nationRepository.save(nation)
        return true
    }

    fun updateNotice(nationId: Long, notice: String, authorGeneral: General? = null): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        val sanitizedNotice = sanitizeHtml(notice)
        nation.meta["notice"] = sanitizedNotice
        nation.meta["nationNotice"] = mutableMapOf<String, Any>(
            "date" to java.time.OffsetDateTime.now().toString(),
            "msg" to sanitizedNotice,
            "author" to (authorGeneral?.name ?: ""),
            "authorID" to (authorGeneral?.id ?: 0L),
        )
        nationRepository.save(nation)
        return true
    }

    fun updateScoutMsg(nationId: Long, scoutMsg: String): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        val sanitizedScoutMsg = sanitizeHtml(scoutMsg)
        nation.meta["scoutMsg"] = sanitizedScoutMsg
        nation.meta["scout_msg"] = sanitizedScoutMsg
        nationRepository.save(nation)
        return true
    }

    fun updateBlockScout(nationId: Long, value: Boolean): MutationResult {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return MutationResult(success = false)
        val world = worldStateRepository.findById(nation.worldId.toShort()).orElse(null)
            ?: return MutationResult(success = false)
        if (world.config["blockChangeScout"] == true) {
            return MutationResult(success = false, reason = "임관 설정을 바꿀 수 없도록 설정되어 있습니다.")
        }

        nation.scoutLevel = if (value) 1 else 0
        nationRepository.save(nation)
        return MutationResult(success = true)
    }

    fun updateBlockWar(nationId: Long, value: Boolean): MutationResult {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return MutationResult(success = false)
        val availableCnt = readInt(nation.meta["available_war_setting_cnt"]) ?: 0
        if (availableCnt <= 0) {
            return MutationResult(success = false, reason = "잔여 횟수가 부족합니다.")
        }

        nation.warState = if (value) 1 else 0
        nation.meta["available_war_setting_cnt"] = availableCnt - 1
        nationRepository.save(nation)
        return MutationResult(success = true, availableCnt = availableCnt - 1)
    }

    // -- Officers --

    fun getOfficers(nationId: Long): List<OfficerInfo> {
        val generals = generalRepository.findByNationId(nationId)
        return generals
            .filter { it.officerLevel > 0 }
            .sortedByDescending { it.officerLevel }
            .map { OfficerInfo(it.id, it.name, it.picture, it.officerLevel.toInt(), it.cityId) }
    }

    fun appointOfficer(nationId: Long, generalId: Long, officerLevel: Int, officerCity: Int?): Boolean {
        val general = generalRepository.findById(generalId).orElse(null) ?: return false
        
        // Validation checks
        if (general.nationId != nationId) {
            throw IllegalStateException("장수가 해당 국가에 속하지 않습니다.")
        }
        
        if (officerLevel < 1 || officerLevel > 11) {
            throw IllegalStateException("관직 레벨은 1-11 사이여야 합니다.")
        }
        
        if (officerLevel == 12) {
            throw IllegalStateException("군주는 유일하며 임명할 수 없습니다.")
        }

        if (officerLevel >= 5) {
            val existing = generalRepository.findByWorldId(general.worldId)
                .filter { it.nationId == nationId && it.officerLevel.toInt() == officerLevel && it.id != generalId }
            for (prev in existing) {
                prev.officerLevel = 1
                generalRepository.save(prev)
            }
        }
        
        general.officerLevel = officerLevel.toShort()
        if (officerCity != null) general.officerCity = officerCity
        generalRepository.save(general)
        return true
    }

    fun expelGeneral(nationId: Long, generalId: Long): Boolean {
        val general = generalRepository.findById(generalId).orElse(null) ?: return false
        if (general.nationId != nationId) return false
        general.nationId = 0
        general.officerLevel = 0
        general.troopId = 0
        generalRepository.save(general)
        return true
    }

    // -- NPC Policy --

    fun getNpcPolicy(nationId: Long): Map<String, Any>? {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return null
        val legacyPolicy = readStringAnyMap(nation.meta["npcPolicy"])
        val nationPolicy = readStringAnyMap(nation.meta["npcNationPolicy"])
        val priorityOnly = readStringAnyMap(nation.meta["npcPriority"])

        val merged = mutableMapOf<String, Any>()
        merged.putAll(legacyPolicy)
        merged.putAll(nationPolicy)

        if (merged["priority"] == null && priorityOnly["priority"] != null) {
            merged["priority"] = priorityOnly["priority"]!!
        }
        return merged
    }

    fun updateNpcPolicy(nationId: Long, policy: Map<String, Any>): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        nation.meta["npcNationPolicy"] = policy
        nation.meta["npcPolicy"] = policy
        nationRepository.save(nation)
        return true
    }

    fun updateNpcPriority(nationId: Long, priority: Map<String, Any>): Boolean {
        val nation = nationRepository.findById(nationId).orElse(null) ?: return false
        val nationPolicy = readStringAnyMap(nation.meta["npcNationPolicy"]).toMutableMap()
        priority["priority"]?.let { nationPolicy["priority"] = it }
        nation.meta["npcNationPolicy"] = nationPolicy
        nation.meta["npcPriority"] = priority
        nationRepository.save(nation)
        return true
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }

    private fun readNationNoticeMessage(nation: Nation): String {
        val legacyNotice = readStringAnyMap(nation.meta["nationNotice"])["msg"] as? String
        return legacyNotice ?: (nation.meta["notice"] as? String ?: "")
    }

    private fun readNationScoutMessage(nation: Nation): String {
        return nation.meta["scout_msg"] as? String
            ?: (nation.meta["scoutMsg"] as? String ?: "")
    }

    private fun readInt(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun sanitizeHtml(raw: String): String = HtmlUtils.htmlEscape(raw)

    /**
     * Recalculate war front status for all cities of a nation.
     * Legacy parity: SetNationFront() in func_gamerule.php
     *
     * front=0: rear (no front)
     * front=1: adjacent to imminent war city (선전포고, term<=5)
     * front=2: adjacent to neutral/empty city (peacetime only)
     * front=3: adjacent to active war city (전쟁, state=0 in legacy)
     */
    fun setNationFront(worldId: Long, nationId: Long) {
        if (nationId == 0L) return

        val world = worldStateRepository.findById(worldId.toShort()).orElse(null) ?: return
        val mapCode = (world.config["mapCode"] as? String) ?: "che"

        val allCities = cityRepository.findByWorldId(worldId)
        val nationCities = allCities.filter { it.nationId == nationId }
        if (nationCities.isEmpty()) return

        // Get all active diplomacy where this nation is involved
        val activeDiplomacy = diplomacyRepository.findByWorldIdAndIsDeadFalse(worldId)
        val warNations = mutableSetOf<Long>()      // active war (교전): front=3
        val imminentNations = mutableSetOf<Long>()  // imminent war (선포, term<=5): front=1

        for (d in activeDiplomacy) {
            val otherNationId = when {
                d.srcNationId == nationId -> d.destNationId
                d.destNationId == nationId -> d.srcNationId
                else -> continue
            }
            if (d.stateCode == "전쟁") {
                warNations.add(otherNationId)
            } else if (d.stateCode == "선전포고" && d.term <= 5) {
                imminentNations.add(otherNationId)
            }
        }

        // Collect adjacent map city IDs for each front type
        val adj3 = mutableSetOf<Int>()  // adjacent to active war cities
        val adj1 = mutableSetOf<Int>()  // adjacent to imminent war cities
        val adj2 = mutableSetOf<Int>()  // adjacent to neutral cities (peacetime only)

        // Find cities owned by war nations, get their adjacent map city IDs
        for (city in allCities) {
            if (city.nationId in warNations) {
                try {
                    val neighbors = mapService.getAdjacentCities(mapCode, city.mapCityId)
                    adj3.addAll(neighbors)
                } catch (e: Exception) {
                    log.warn("Failed to get adjacent cities for city {}: {}", city.id, e.message)
                }
            }
            if (city.nationId in imminentNations) {
                try {
                    val neighbors = mapService.getAdjacentCities(mapCode, city.mapCityId)
                    adj1.addAll(neighbors)
                } catch (e: Exception) {
                    log.warn("Failed to get adjacent cities for city {}: {}", city.id, e.message)
                }
            }
        }

        // Peacetime: if no war fronts, look for neutral (empty) city adjacency
        if (adj3.isEmpty() && adj1.isEmpty()) {
            for (city in allCities) {
                if (city.nationId == 0L) {
                    try {
                        val neighbors = mapService.getAdjacentCities(mapCode, city.mapCityId)
                        adj2.addAll(neighbors)
                    } catch (e: Exception) {
                        log.warn("Failed to get adjacent cities for city {}: {}", city.id, e.message)
                    }
                }
            }
        }

        // Reset all nation cities to front=0, then set by priority (3 > 2 > 1)
        for (city in nationCities) {
            city.frontState = 0
        }
        // front=1 first (lowest priority)
        for (city in nationCities) {
            if (city.mapCityId in adj1) city.frontState = 1
        }
        // front=2 overwrites 1
        for (city in nationCities) {
            if (city.mapCityId in adj2) city.frontState = 2
        }
        // front=3 overwrites all (highest priority)
        for (city in nationCities) {
            if (city.mapCityId in adj3) city.frontState = 3
        }

        cityRepository.saveAll(nationCities)
        val nationMapIds = nationCities.map { it.mapCityId }.toSet()
        log.info("Updated front state for nation {} — adj3={}, adj1={}, adj2={}",
            nationId, adj3.intersect(nationMapIds), adj1.intersect(nationMapIds), adj2.intersect(nationMapIds))
    }

    /**
     * Recalculate front state for ALL nations in a world.
     * Called during turn processing and after scenario creation.
     */
    fun recalcAllFronts(worldId: Long) {
        val nations = nationRepository.findByWorldId(worldId).filter { it.level > 0 }
        for (nation in nations) {
            setNationFront(worldId, nation.id)
        }
    }
}
