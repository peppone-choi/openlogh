package com.openlogh.service

import com.openlogh.dto.NationPolicyInfo
import com.openlogh.dto.OfficerInfo
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.HtmlUtils
@Service
class FactionService(
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val officerRankService: OfficerRankService,
    private val planetRepository: PlanetRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val mapService: MapService,
) {
    private val log = LoggerFactory.getLogger(FactionService::class.java)

    data class MutationResult(
        val success: Boolean,
        val reason: String? = null,
        val availableCnt: Int? = null,
    )

    fun listByWorld(worldId: Long): List<Faction> {
        return factionRepository.findBySessionId(worldId)
    }

    fun getById(id: Long): Faction? {
        return factionRepository.findById(id).orElse(null)
    }

    @Transactional
    fun updateAbbreviation(nationId: Long, abbreviation: String): Faction? {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return null
        faction.abbreviation = abbreviation.take(2)
        return factionRepository.save(faction)
    }

    // -- Policy --

    fun getPolicy(nationId: Long): NationPolicyInfo? {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return null
        return NationPolicyInfo(
            rate = faction.conscriptionRate.toInt(),
            bill = faction.taxRate.toInt(),
            secretLimit = faction.secretLimit.toInt(),
            strategicCmdLimit = faction.strategicCmdLimit.toInt(),
            notice = readNationNoticeMessage(faction),
            scoutMsg = readNationScoutMessage(faction),
            blockWar = faction.warState.toInt() != 0,
            blockScout = faction.scoutLevel.toInt() != 0,
        )
    }

    @Transactional
    fun updatePolicy(nationId: Long, rate: Int?, bill: Int?, secretLimit: Int?, strategicCmdLimit: Int?): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        rate?.let { faction.conscriptionRate = it.toShort() }
        bill?.let { faction.taxRate = it.toShort() }
        secretLimit?.let { faction.secretLimit = it.toShort() }
        strategicCmdLimit?.let { faction.strategicCmdLimit = it.toShort() }
        factionRepository.save(faction)
        return true
    }

    fun verifyPolicyAccess(nationId: Long, loginId: String): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val officer = officerRepository.findBySessionIdAndUserId(faction.sessionId, user.id)
            .firstOrNull { it.factionId == nationId }
            ?: return false
        if (officer.penalty["noTopSecret"] == true) {
            return false
        }
        if (officer.officerLevel >= 5 && officer.penalty["noChief"] == true) {
            return false
        }
        if (officer.permission == "ambassador" && officer.penalty["noAmbassador"] == true) {
            return false
        }
        return officer.officerLevel >= 5 || officer.permission == "ambassador"
    }

    fun resolvePolicyActor(nationId: Long, loginId: String): Officer? {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return null
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        return officerRepository.findBySessionIdAndUserId(faction.sessionId, user.id)
            .firstOrNull { it.factionId == nationId }
    }

    @Transactional
    fun updateBill(nationId: Long, amount: Int): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        faction.taxRate = amount.toShort()
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateRate(nationId: Long, amount: Int): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        faction.conscriptionRate = amount.toShort()
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateSecretLimit(nationId: Long, amount: Int): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        faction.secretLimit = amount.toShort()
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateNotice(nationId: Long, notice: String, authorGeneral: Officer? = null): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        val sanitizedNotice = sanitizeHtml(notice)
        faction.meta["notice"] = sanitizedNotice
        faction.meta["nationNotice"] = mutableMapOf<String, Any>(
            "date" to java.time.OffsetDateTime.now().toString(),
            "msg" to sanitizedNotice,
            "author" to (authorGeneral?.name ?: ""),
            "authorID" to (authorGeneral?.id ?: 0L),
        )
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateScoutMsg(nationId: Long, scoutMsg: String): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        val sanitizedScoutMsg = sanitizeHtml(scoutMsg)
        faction.meta["scoutMsg"] = sanitizedScoutMsg
        faction.meta["scout_msg"] = sanitizedScoutMsg
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateBlockScout(nationId: Long, value: Boolean): MutationResult {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return MutationResult(success = false)
        val world = sessionStateRepository.findById(faction.sessionId.toShort()).orElse(null)
            ?: return MutationResult(success = false)
        if (world.config["blockChangeScout"] == true) {
            return MutationResult(success = false, reason = "임관 설정을 바꿀 수 없도록 설정되어 있습니다.")
        }

        faction.scoutLevel = if (value) 1 else 0
        factionRepository.save(faction)
        return MutationResult(success = true)
    }

    @Transactional
    fun updateBlockWar(nationId: Long, value: Boolean): MutationResult {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return MutationResult(success = false)
        val availableCnt = readInt(faction.meta["available_war_setting_cnt"]) ?: 0
        if (availableCnt <= 0) {
            return MutationResult(success = false, reason = "잔여 횟수가 부족합니다.")
        }

        faction.warState = if (value) 1 else 0
        faction.meta["available_war_setting_cnt"] = availableCnt - 1
        factionRepository.save(faction)
        return MutationResult(success = true, availableCnt = availableCnt - 1)
    }

    // -- Officers --

    fun getOfficers(nationId: Long): List<OfficerInfo> {
        val generals = officerRepository.findByFactionId(nationId)
        return generals
            .filter { it.officerLevel > 0 }
            .sortedByDescending { it.officerLevel }
            .map { OfficerInfo(it.id, it.name, it.picture, it.officerLevel.toInt(), it.planetId) }
    }

    @Transactional
    fun appointOfficer(nationId: Long, generalId: Long, officerLevel: Int, officerPlanet: Int?): Boolean {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        
        if (officer.factionId != nationId) {
            throw IllegalStateException("장수가 해당 국가에 속하지 않습니다.")
        }
        
        if (officerLevel < 1 || officerLevel > 20) {
            throw IllegalStateException("관직 레벨은 1-20 사이여야 합니다.")
        }
        
        if (officerLevel == 20) {
            throw IllegalStateException("군주는 유일하며 임명할 수 없습니다.")
        }

        val officerRankKey = faction.meta["officerRankKey"] as? String
        val isAvailable = officerRankService.isOfficerLevelAvailable(
            officerLevel = officerLevel,
            nationLevel = faction.factionRank.toInt(),
            officerRankKey = officerRankKey
        )
        
        if (!isAvailable) {
            val nationTitle = officerRankService.getNationTitle(faction.factionRank.toInt()) ?: "레벨 ${faction.factionRank}"
            throw IllegalStateException("${nationTitle} 국가에서는 해당 관직을 임명할 수 없습니다.")
        }

        if (officerLevel >= 5) {
            val existing = officerRepository.findBySessionId(officer.sessionId)
                .filter { it.factionId == nationId && it.officerLevel.toInt() == officerLevel && it.id != generalId }
            for (prev in existing) {
                prev.officerLevel = 1
                officerRepository.save(prev)
            }
        }
        
        officer.officerLevel = officerLevel.toShort()
        if (officerPlanet != null) officer.officerPlanet = officerPlanet
        officerRepository.save(officer)
        return true
    }

    @Transactional
    fun expelOfficer(nationId: Long, generalId: Long): Boolean {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
        if (officer.factionId != nationId) return false
        officer.factionId = 0
        officer.officerLevel = 0
        officer.fleetId = 0
        officerRepository.save(officer)
        return true
    }

    // -- NPC Policy --

    fun getNpcPolicy(nationId: Long): Map<String, Any>? {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return null
        val legacyPolicy = readStringAnyMap(faction.meta["npcPolicy"])
        val nationPolicy = readStringAnyMap(faction.meta["npcNationPolicy"])
        val priorityOnly = readStringAnyMap(faction.meta["npcPriority"])

        val merged = mutableMapOf<String, Any>()
        merged.putAll(legacyPolicy)
        merged.putAll(nationPolicy)

        if (merged["priority"] == null && priorityOnly["priority"] != null) {
            merged["priority"] = priorityOnly["priority"]!!
        }
        return merged
    }

    @Transactional
    fun updateNpcPolicy(nationId: Long, policy: Map<String, Any>): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        faction.meta["npcNationPolicy"] = policy
        faction.meta["npcPolicy"] = policy
        factionRepository.save(faction)
        return true
    }

    @Transactional
    fun updateNpcPriority(nationId: Long, priority: Map<String, Any>): Boolean {
        val faction = factionRepository.findById(nationId).orElse(null) ?: return false
        val nationPolicy = readStringAnyMap(faction.meta["npcNationPolicy"]).toMutableMap()
        priority["priority"]?.let { nationPolicy["priority"] = it }
        faction.meta["npcNationPolicy"] = nationPolicy
        faction.meta["npcPriority"] = priority
        factionRepository.save(faction)
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

    private fun readNationNoticeMessage(faction: Faction): String {
        val legacyNotice = readStringAnyMap(faction.meta["nationNotice"])["msg"] as? String
        return legacyNotice ?: (faction.meta["notice"] as? String ?: "")
    }

    private fun readNationScoutMessage(faction: Faction): String {
        return faction.meta["scout_msg"] as? String
            ?: (faction.meta["scoutMsg"] as? String ?: "")
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
     * Recalculate war front status for all cities of a faction.
     * Legacy parity: SetNationFront() in func_gamerule.php
     *
     * front=0: rear (no front)
     * front=1: adjacent to imminent war planet (선전포고, term<=5)
     * front=2: adjacent to neutral/empty planet (peacetime only)
     * front=3: adjacent to active war planet (전쟁, state=0 in legacy)
     */
    @Transactional
    fun setNationFront(worldId: Long, nationId: Long) {
        if (nationId == 0L) return

        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return
        val mapCode = (world.config["mapCode"] as? String) ?: "che"

        val allCities = planetRepository.findBySessionId(worldId)
        val nationCities = allCities.filter { it.factionId == nationId }
        if (nationCities.isEmpty()) return

        // Get all active diplomacy where this faction is involved
        val activeDiplomacy = diplomacyRepository.findBySessionIdAndIsDeadFalse(worldId)
        val warNations = mutableSetOf<Long>()      // active war (교전): front=3
        val imminentNations = mutableSetOf<Long>()  // imminent war (선포, term<=5): front=1

        for (d in activeDiplomacy) {
            val otherNationId = when {
                d.srcFactionId == nationId -> d.destFactionId
                d.destFactionId == nationId -> d.srcFactionId
                else -> continue
            }
            if (d.stateCode == "전쟁") {
                warNations.add(otherNationId)
            } else if (d.stateCode == "선전포고" && d.term <= 5) {
                imminentNations.add(otherNationId)
            }
        }

        // Collect adjacent map planet IDs for each front type
        val adj3 = mutableSetOf<Int>()  // adjacent to active war cities
        val adj1 = mutableSetOf<Int>()  // adjacent to imminent war cities
        val adj2 = mutableSetOf<Int>()  // adjacent to neutral cities (peacetime only)

        // Find cities owned by war nations, get their adjacent map planet IDs
        for (planet in allCities) {
            if (planet.factionId in warNations) {
                try {
                    val neighbors = mapService.getAdjacentCities(mapCode, planet.mapPlanetId)
                    adj3.addAll(neighbors)
                } catch (e: Exception) {
                    log.warn("Failed to get adjacent cities for planet {}: {}", planet.id, e.message)
                }
            }
            if (planet.factionId in imminentNations) {
                try {
                    val neighbors = mapService.getAdjacentCities(mapCode, planet.mapPlanetId)
                    adj1.addAll(neighbors)
                } catch (e: Exception) {
                    log.warn("Failed to get adjacent cities for planet {}: {}", planet.id, e.message)
                }
            }
        }

        // Peacetime: if no war fronts, look for neutral (empty) planet adjacency
        if (adj3.isEmpty() && adj1.isEmpty()) {
            for (planet in allCities) {
                if (planet.factionId == 0L) {
                    try {
                        val neighbors = mapService.getAdjacentCities(mapCode, planet.mapPlanetId)
                        adj2.addAll(neighbors)
                    } catch (e: Exception) {
                        log.warn("Failed to get adjacent cities for planet {}: {}", planet.id, e.message)
                    }
                }
            }
        }

        // Reset all faction cities to front=0, then set by priority (3 > 2 > 1)
        for (planet in nationCities) {
            planet.frontState = 0
        }
        // front=1 first (lowest priority)
        for (planet in nationCities) {
            if (planet.mapPlanetId in adj1) planet.frontState = 1
        }
        // front=2 overwrites 1
        for (planet in nationCities) {
            if (planet.mapPlanetId in adj2) planet.frontState = 2
        }
        // front=3 overwrites all (highest priority)
        for (planet in nationCities) {
            if (planet.mapPlanetId in adj3) planet.frontState = 3
        }

        planetRepository.saveAll(nationCities)
        val nationMapIds = nationCities.map { it.mapPlanetId }.toSet()
        log.info("Updated front state for faction {} — adj3={}, adj1={}, adj2={}",
            nationId, adj3.intersect(nationMapIds), adj1.intersect(nationMapIds), adj2.intersect(nationMapIds))
    }

    /**
     * Recalculate front state for ALL nations in a world.
     * Called during turn processing and after scenario creation.
     */
    @Transactional
    fun recalcAllFronts(worldId: Long) {
        val nations = factionRepository.findBySessionId(worldId).filter { it.factionRank > 0 }
        for (faction in nations) {
            setNationFront(worldId, faction.id)
        }
    }
}
