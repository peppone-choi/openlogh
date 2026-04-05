package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.entity.HallOfFame
import com.openlogh.entity.Faction
import com.openlogh.entity.OldOfficer
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerAccessLogRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.HallOfFameRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.OldOfficerRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.service.GameConstService
import com.openlogh.service.HistoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 장수 유지보수: 매 턴 장수 나이/경험/헌신/부상/은퇴/사망 처리
 * Legacy parity: TurnExecutionHelper.php lines 180-229
 */
@Service
class OfficerMaintenanceService(
    private val gameConstService: GameConstService,
    private val hallOfFameRepository: HallOfFameRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val fleetRepository: FleetRepository,
    private val oldGeneralRepository: OldOfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val officerAccessLogRepository: OfficerAccessLogRepository,
    private val historyService: HistoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun processGeneralMaintenance(world: SessionState, generals: List<Officer>) {
        val isUnited = (world.config["isUnited"] as? Number)?.toInt() ?: 0
        val retirementYear = try {
            gameConstService.getInt("retirementYear")
        } catch (e: Exception) {
            log.warn("Failed to resolve retirementYear from config: {}", e.message)
            80 // 기본값
        }

        for (general in generals) {
            // 이미 사망/은퇴한 장수는 스킵
            if (general.npcState.toInt() == 5 || general.npcState.toInt() == -1) continue

            // === 삭턴 장수 처리 (killturn <= 0) ===
            // legacy TurnExecutionHelper.php lines 184-206
            if (general.killTurn != null && general.killTurn!! <= 0) {
                if (general.npcState.toInt() == 1 && general.deadYear > world.currentYear) {
                    // NPC유저(npcType==1): 유체이탈 → 원래 NPC로 전환
                    val remainingYears = general.deadYear - world.currentYear
                    general.killTurn = (remainingYears * 12).coerceIn(-32768, 32767).toShort()
                    general.npcState = (general.npcOrg ?: 2).toInt().coerceIn(-1, 9).toShort()
                    general.userId = null
                    general.defenceTrain = 80
                    general.ownerName = ""
                    general.commandEndTime = null
                    clearGeneralQueuesAndAccessLogs(general.id)
                    log.info("장수 {} (id={}): NPC유저 유체이탈, NPC로 전환 (npcState={})",
                        general.name, general.id, general.npcState)
                } else {
                    // 그 외: 사망 처리
                    killGeneral(general, world, generals)
                    log.info("장수 {} (id={}): 삭턴 만료로 사망", general.name, general.id)
                    continue
                }
            }

            // === 은퇴 처리 ===
            // legacy TurnExecutionHelper.php lines 208-216
            if (general.age >= retirementYear && general.npcState.toInt() == 0) {
                if (isUnited == 0 && general.userId != null && general.npcState < 2) {
                    checkHall(general, world)
                }
                rebirthGeneral(general)
                log.info("장수 {} (id={}): 은퇴 (나이={}, 은퇴나이={})",
                    general.name, general.id, general.age, retirementYear)
                continue
            }

            // === 나이 증가 (1월) ===
            if (world.currentMonth.toInt() == 1) {
                general.age = (general.age + 1).coerceIn(0, 120).toShort()
            }

            // === 기본 월간 경험치 ===
            general.experience += 10
            val newExpLevel = calcExpLevel(general.experience).coerceIn(0, 255).toShort()
            if (general.expLevel != newExpLevel) {
                general.expLevel = newExpLevel
            }

            // === 헌신도 감쇠 ===
            if (general.dedication > 0) {
                general.dedication = (general.dedication * 0.99).toInt()
                val newDedLevel = calcDedLevel(general.dedication).coerceIn(0, 30).toShort()
                if (general.dedLevel != newDedLevel) {
                    general.dedLevel = newDedLevel
                }
            }

            // === 부상 자연회복 ===
            if (general.injury > 0) {
                general.injury = (general.injury - 1).coerceAtLeast(0).toShort()
            }

            // === NPC 장수 수명 체크 (deadYear) ===
            if (general.npcState >= 2 &&
                general.npcState != SovereignConstants.NPC_STATE_EMPEROR &&
                world.currentYear >= general.deadYear
            ) {
                killGeneral(general, world, generals)
                log.info("장수 {} (id={}): 수명 만료로 사망 (deadYear={})",
                    general.name, general.id, general.deadYear)
            }
        }
    }

    /**
     * 장수 사망 처리: 국가에서 해제하고 npcState=5로 설정
     * legacy: Officer::kill() 패러티
     */
    fun killGeneral(general: Officer, world: SessionState, allGenerals: List<Officer>) {
        val originNationId = general.factionId
        val nation = originNationId.takeIf { it > 0 }?.let { factionRepository.findById(it).orElse(null) }

        if ((general.officerLevel.toInt() == 20 || nation?.chiefOfficerId == general.id) && originNationId > 0) {
            val successor = selectNextRuler(world, general, allGenerals)
            if (successor != null) {
                successor.officerLevel = 20
                successor.officerCity = 0
                if (nation != null) {
                    nation.chiefOfficerId = successor.id
                }
            } else if (nation != null) {
                collapseNation(world, nation, general, allGenerals)
            }
        }

        disbandLedTroop(general, allGenerals)
        clearGeneralQueuesAndAccessLogs(general.id)
        upsertOldGeneral(readServerId(world), general, world)

        if (nation != null && nation.officerCount > 0) {
            nation.officerCount -= 1
        }

        general.npcState = 5
        general.userId = null
        general.factionId = 0
        general.officerLevel = 0
        general.officerCity = 0
        general.killTurn = null
        general.blockState = 0
        general.fleetId = 0
        general.commandEndTime = null

    }

    /**
     * 장수 환생 처리: 은퇴 후 새 캐릭터로 재시작할 수 있도록 초기화
     * legacy: Officer::rebirth() 패러티
     */
    private fun rebirthGeneral(general: Officer) {
        general.leadership = scaledStatWithFloor(general.leadership, 0.85, 10)
        general.command = scaledStatWithFloor(general.command, 0.85, 10)
        general.intelligence = scaledStatWithFloor(general.intelligence, 0.85, 10)
        general.injury = 0
        general.experience = (general.experience * 0.5).toInt()
        general.dedication = (general.dedication * 0.5).toInt()
        general.age = 20
        general.specAge = 0
        general.spec2Age = 0
        general.dex1 = (general.dex1 * 0.5).toInt()
        general.dex2 = (general.dex2 * 0.5).toInt()
        general.dex3 = (general.dex3 * 0.5).toInt()
        general.dex4 = (general.dex4 * 0.5).toInt()
        general.dex5 = (general.dex5 * 0.5).toInt()
        general.meta["rank"] = resetRankMeta(general.meta["rank"])
        general.meta.remove("rebirth_available")
        general.meta.remove("retired_year")
        general.meta.remove("retired_month")
    }

    private fun checkHall(general: Officer, world: SessionState) {
        val nation = factionRepository.findById(general.factionId).orElse(null)
        val serverId = readString(world.config, "serverId").ifBlank { world.name }
        val scenario = readNumber(world.meta, "scenarioId")
        val season = readNumber(world.meta, "season").takeIf { it > 0 } ?: 1
        val rank = asMap(general.meta["rank"])
        val warnum = readNumber(rank, "warnum")
        val killnum = readNumber(rank, "killnum")
        val firenum = readNumber(rank, "firenum")
        val killcrew = readNumber(rank, "killcrew")
        val deathcrew = readNumber(rank, "deathcrew")

        val hallValues = linkedMapOf(
            "experience" to general.experience.toDouble(),
            "dedication" to general.dedication.toDouble(),
            "warnum" to warnum.toDouble(),
            "killnum" to killnum.toDouble(),
            "firenum" to firenum.toDouble(),
            "winrate" to rate(killnum, warnum),
            "killrate" to rate(killcrew, deathcrew),
        )

        for ((type, value) in hallValues) {
            if ((type == "winrate" || type == "killrate") && warnum < 10) {
                continue
            }
            if (value <= 0.0) {
                continue
            }

            val aux = mutableMapOf<String, Any>(
                "name" to general.name,
                "nationName" to (nation?.name ?: "재야"),
                "bgColor" to (nation?.color ?: "#000000"),
                "fgColor" to (nation?.color ?: "#000000"),
                "picture" to general.picture,
                "imgsvr" to general.imageServer,
            )

            val existing = hallOfFameRepository.findByServerIdAndTypeAndGeneralNo(serverId, type, general.id)
            if (existing == null) {
                hallOfFameRepository.save(
                    HallOfFame(
                        serverId = serverId,
                        season = season,
                        scenario = scenario,
                        officerNo = general.id,
                        type = type,
                        value = value,
                        owner = general.userId?.toString(),
                        aux = aux,
                    )
                )
            } else if (value > existing.value) {
                existing.value = value
                existing.owner = general.userId?.toString()
                existing.aux = aux
                hallOfFameRepository.save(existing)
            }
        }
    }

    private fun rate(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) {
            return 0.0
        }
        return numerator.toDouble() / denominator.toDouble()
    }

    private fun readNumber(map: Map<String, Any>, key: String): Int {
        return (map[key] as? Number)?.toInt() ?: 0
    }

    private fun readString(map: Map<String, Any>, key: String): String {
        return map[key] as? String ?: ""
    }

    private fun asMap(value: Any?): Map<String, Any> {
        if (value !is Map<*, *>) return emptyMap()
        val typed = mutableMapOf<String, Any>()
        value.forEach { (k, v) ->
            if (k is String && v != null) {
                typed[k] = v
            }
        }
        return typed
    }

    private fun scaledStatWithFloor(value: Short, ratio: Double, minValue: Int): Short {
        return (value * ratio).toInt().coerceIn(minValue, 100).toShort()
    }

    private fun resetRankMeta(value: Any?): MutableMap<String, Any> {
        val rank = asMap(value)
        return rank.keys.associateWith { 0 as Any }.toMutableMap()
    }

    private fun clearGeneralQueuesAndAccessLogs(generalId: Long) {
        officerTurnRepository.deleteByOfficerId(generalId)
        val accessLogs = officerAccessLogRepository.findByOfficerId(generalId)
        if (accessLogs.isNotEmpty()) {
            officerAccessLogRepository.deleteAll(accessLogs)
        }
    }

    private fun collapseNation(world: SessionState, nation: Faction, deadGeneral: Officer, allGenerals: List<Officer>) {
        logNationCollapse(world, nation, deadGeneral)

        allGenerals.asSequence()
            .filter { it.factionId == nation.id && it.id != deadGeneral.id }
            .forEach { general ->
                general.belong = 0
                general.fleetId = 0
                general.officerLevel = 0
                general.officerCity = 0
                general.factionId = 0
                general.permission = "normal"
            }

        planetRepository.findByFactionId(nation.id).forEach { city ->
            city.factionId = 0
            city.frontState = 0
        }

        val nationTroops = fleetRepository.findByFactionId(nation.id)
        if (nationTroops.isNotEmpty()) {
            fleetRepository.deleteAll(nationTroops)
        }

        factionTurnRepository.findBySessionId(world.id.toLong())
            .filter { it.factionId == nation.id }
            .forEach { factionTurnRepository.delete(it) }

        diplomacyRepository.findBySessionId(world.id.toLong())
            .filter { it.srcFactionId == nation.id || it.destFactionId == nation.id }
            .forEach {
                it.isDead = true
                it.isShowing = false
            }

        nation.capitalPlanetId = null
        nation.chiefOfficerId = 0
        nation.officerCount = 0
        nation.militaryPower = 0
        nation.factionRank = 0
    }

    private fun logNationCollapse(world: SessionState, nation: Faction, deadGeneral: Officer) {
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()
        historyService.logWorldHistory(
            world.id.toLong(),
            "【멸망】 ${nation.name} 세력이 군주 ${deadGeneral.name}의 사망으로 멸망했습니다.",
            year,
            month,
        )
        historyService.logNationHistory(
            world.id.toLong(),
            nation.id,
            "군주 ${deadGeneral.name}의 사망으로 세력이 멸망했습니다.",
            year,
            month,
        )
    }

    private fun disbandLedTroop(general: Officer, allGenerals: List<Officer>) {
        val troopId = resolveLedTroopId(general) ?: return
        allGenerals.filter { it.fleetId == troopId }.forEach { it.fleetId = 0 }

        fleetRepository.findById(troopId).orElse(null)
            ?.takeIf { it.leaderOfficerId == general.id }
            ?.let { fleetRepository.delete(it) }
    }

    private fun resolveLedTroopId(general: Officer): Long? {
        val troopId = general.fleetId
        if (troopId <= 0L) {
            return null
        }
        if (troopId == general.id) {
            return troopId
        }
        val troop = fleetRepository.findById(troopId).orElse(null) ?: return null
        return troopId.takeIf { troop.leaderOfficerId == general.id }
    }

    private fun selectNextRuler(world: SessionState, deadGeneral: Officer, allGenerals: List<Officer>): Officer? {
        val nationCandidates = allGenerals.filter {
            it.id != deadGeneral.id &&
                it.factionId == deadGeneral.factionId &&
                it.officerLevel.toInt() != 20 &&
                it.npcState.toInt() != 5
        }
        if (nationCandidates.isEmpty()) {
            return null
        }

        if (!isFictionWorld(world) && deadGeneral.npcState.toInt() > 0) {
            // npcState 1..3 = regular NPC, 6 = NPC nation general, 9 = invader
            // Exclude 5 (dead/troop leader, already filtered above)
            val npcCandidates = nationCandidates
                .filter { it.npcState.toInt() >= 1 }
                .sortedWith(
                    compareBy<Officer>(
                        { affinityDistance(deadGeneral.affinity.toInt(), it.affinity.toInt()) },
                        { -it.officerLevel.toInt() },
                        { it.id },
                    )
                )
            if (npcCandidates.isNotEmpty()) {
                return npcCandidates.first()
            }
        }

        val highOfficer = nationCandidates
            .filter { it.officerLevel.toInt() >= 9 }
            .sortedWith(compareByDescending<Officer> { it.officerLevel.toInt() }.thenBy { it.id })
            .firstOrNull()
        if (highOfficer != null) {
            return highOfficer
        }

        return nationCandidates
            .sortedWith(compareByDescending<Officer> { it.dedication }.thenBy { it.id })
            .firstOrNull()
    }

    /**
     * Legacy: getExpLevel() in func_converter.php
     * exp < 1000: exp / 100; else: toInt(sqrt(exp/10)), clamped 0–255
     */
    private fun calcExpLevel(experience: Int): Int {
        val level = if (experience < 1000) {
            experience / 100
        } else {
            sqrt(experience / 10.0).toInt()
        }
        return level.coerceIn(0, 255)
    }

    /**
     * Legacy: getDedLevel() in func_converter.php
     * ceil(sqrt(dedication) / 10), clamped 0–30
     */
    private fun calcDedLevel(dedication: Int): Int {
        return ceil(sqrt(dedication.toDouble()) / 10).toInt().coerceIn(0, 30)
    }

    private fun affinityDistance(base: Int, other: Int): Int {
        val diff = abs(base - other)
        return if (diff > 75) 150 - diff else diff
    }

    private fun isFictionWorld(world: SessionState): Boolean {
        val fiction = (world.meta["fiction"] as? Number)?.toInt()
            ?: (world.config["fiction"] as? Number)?.toInt()
            ?: 0
        return fiction != 0
    }

    private fun readServerId(world: SessionState): String {
        return readString(world.config, "serverId").ifBlank { world.name }
    }

    private fun upsertOldGeneral(serverId: String, general: Officer, world: SessionState) {
        val oldGeneral = oldGeneralRepository.findByServerIdAndOfficerNo(serverId, general.id) ?: OldOfficer(
            serverId = serverId,
            officerNo = general.id,
        )
        oldGeneral.owner = general.userId?.toString()
        oldGeneral.name = general.name
        oldGeneral.lastYearMonth = world.currentYear.toInt() * 100 + world.currentMonth.toInt()
        oldGeneral.turnTime = general.turnTime
        oldGeneral.data = mutableMapOf(
            "id" to general.id,
            "name" to general.name,
            "nationId" to general.factionId,
            "cityId" to general.planetId,
            "troopId" to general.fleetId,
            "officerLevel" to general.officerLevel,
            "dedication" to general.dedication,
            "experience" to general.experience,
            "leadership" to general.leadership,
            "strength" to general.command,
            "intel" to general.intelligence,
            "politics" to general.politics,
            "charm" to general.administration,
            "gold" to general.funds,
            "rice" to general.supplies,
            "crew" to general.ships,
            "crewType" to general.shipClass,
            "train" to general.training,
            "atmos" to general.morale,
            "age" to general.age,
            "startAge" to general.startAge,
            "npcState" to general.npcState,
            "personalCode" to general.personalCode,
            "specialCode" to general.specialCode,
            "special2Code" to general.special2Code,
            "turnTime" to general.turnTime.toString(),
            "meta" to general.meta.toMutableMap(),
        )
        oldGeneralRepository.save(oldGeneral)
    }
}
