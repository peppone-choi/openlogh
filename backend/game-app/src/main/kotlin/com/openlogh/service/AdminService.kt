package com.openlogh.service

import com.openlogh.dto.AdminDashboard
import com.openlogh.dto.AdminGeneralSummary
import com.openlogh.dto.AdminUserAction
import com.openlogh.dto.AdminUserSummary
import com.openlogh.dto.AdminWorldInfo
import com.openlogh.entity.HallOfFame
import com.openlogh.dto.NationStatistic
import com.openlogh.dto.ResourceDistributionRequest
import com.openlogh.dto.TimeControlRequest
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class AdminService(
    private val sessionStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val appUserRepository: AppUserRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val hallOfFameRepository: HallOfFameRepository,
    private val messageRepository: MessageRepository,
    private val eventActionService: com.openlogh.engine.EventActionService,
    private val inheritanceService: InheritanceService,
    private val historyService: HistoryService,
) {
    private companion object {
        const val GRADE_SYSTEM_ADMIN = 6
        const val DEFAULT_BLOCK_KILL_TURN = 24
        const val INFINITE_KILL_TURN = 8000
    }

    fun getDashboard(worldId: Long): AdminDashboard {
        val worlds = sessionStateRepository.findAll()
        val world = worlds.firstOrNull { it.id.toLong() == worldId }
        return AdminDashboard(
            worldCount = worlds.size,
            currentWorld = world?.let {
                val config = it.config.toMutableMap()
                config["turnTerm"] = it.tickSeconds / 60
                config["turnterm"] = it.tickSeconds / 60
                AdminWorldInfo(
                    id = it.id,
                    year = it.currentYear,
                    month = it.currentMonth,
                    scenarioCode = it.scenarioCode,
                    realtimeMode = it.realtimeMode,
                    tickSeconds = it.tickSeconds,
                    commandPointRegenRate = it.commandPointRegenRate,
                    config = config,
                )
            },
        )
    }

    @Transactional
    fun updateSettings(worldId: Long, settings: Map<String, Any>): Boolean {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return false

        // Column-level fields (not stored in config JSONB)
        settings["turnTerm"]?.let {
            val minutes = (it as Number).toInt().coerceAtLeast(1)
            world.tickSeconds = minutes * 60
            world.config["turnTerm"] = minutes
            world.config["turnterm"] = minutes
        }
        settings["realtimeMode"]?.let { world.realtimeMode = it as Boolean }
        settings["commandPointRegenRate"]?.let { world.commandPointRegenRate = (it as Number).toInt() }

        // Config JSONB keys — pass through everything except column-level fields
        val columnKeys = setOf("turnTerm", "realtimeMode", "commandPointRegenRate")
        for ((key, value) in settings) {
            if (key !in columnKeys) {
                if (key == "extend") {
                    val enabled = when (value) {
                        is Boolean -> value
                        is Number -> value.toInt() != 0
                        is String -> value.equals("true", ignoreCase = true) || value == "1"
                        else -> false
                    }
                    world.config["extend"] = enabled
                    world.config["extendedGeneral"] = if (enabled) 1 else 0
                } else if (key == "extendedGeneral") {
                    val enabled = when (value) {
                        is Boolean -> value
                        is Number -> value.toInt() != 0
                        is String -> value.equals("true", ignoreCase = true) || value == "1"
                        else -> false
                    }
                    world.config["extend"] = enabled
                    world.config["extendedGeneral"] = if (enabled) 1 else 0
                } else if (key == "startYear") {
                    world.config["startYear"] = value
                    world.config["startyear"] = value
                } else {
                    world.config[key] = value
                }
            }
        }

        sessionStateRepository.save(world)
        return true
    }

    fun listAllGenerals(worldId: Long): List<AdminGeneralSummary> {
        return officerRepository.findBySessionId(worldId).map {
            AdminGeneralSummary(
                id = it.id,
                name = it.name,
                nationId = it.factionId,
                crew = it.ships,
                experience = it.experience,
                npcState = it.npcState.toInt(),
                blockState = it.blockState.toInt(),
                killTurn = it.killTurn?.toInt(),
            )
        }
    }

    @Transactional
    fun generalAction(worldId: Long, id: Long, type: String): Boolean {
        val officer = officerRepository.findById(id).orElse(null) ?: return false
        if (officer.sessionId != worldId) return false
        if (!applyGeneralAction(officer, type)) return false
        officerRepository.save(officer)
        return true
    }

    fun getStatistics(worldId: Long): List<NationStatistic> {
        val nations = factionRepository.findBySessionId(worldId)
        return nations.map { faction ->
            val generals = officerRepository.findByFactionId(faction.id)
            val cities = planetRepository.findByFactionId(faction.id)
            NationStatistic(
                nationId = faction.id,
                name = faction.name,
                color = faction.color,
                level = faction.factionRank.toInt(),
                gold = faction.funds, rice = faction.supplies,
                tech = faction.techLevel,
                power = faction.militaryPower,
                genCount = generals.size,
                cityCount = cities.size,
                totalCrew = generals.sumOf { it.ships },
                totalPop = cities.sumOf { it.population },
            )
        }
    }

    fun getGeneralLogs(worldId: Long, id: Long): List<Any> {
        return messageRepository.findBySessionIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId, "general_action", id)
    }

    fun getDiplomacyMatrix(worldId: Long): List<Any> {
        return diplomacyRepository.findBySessionId(worldId)
    }

    fun writeLog(worldId: Long, message: String): Boolean {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return false
        historyService.logWorldHistory(
            worldId = worldId,
            message = message,
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
        )
        return true
    }

    @Transactional
    fun forceRehall(worldId: Long): Map<String, Int>? {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return null
        val isUnited = ((world.config["isunited"] as? Number)?.toInt())
            ?: ((world.config["isUnited"] as? Number)?.toInt())
            ?: 0
        require(isUnited != 0) { "아직 천통하지 않았습니다" }

        val generals = officerRepository.findBySessionId(worldId)
        val eligibleHallGenerals = generals.filter { it.npcState.toInt() < 2 && it.age.toInt() >= 40 }
        eligibleHallGenerals.forEach { upsertHallOfFame(world, it) }

        eventActionService.mergeInheritPointRank(world)
        val updatedUsers = inheritanceService.forceReapplyMergedPoints(worldId)

        return mapOf(
            "processedGenerals" to eligibleHallGenerals.size,
            "updatedUsers" to updatedUsers,
        )
    }

    @Transactional
    fun timeControl(worldId: Long, request: TimeControlRequest): Boolean {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return false

        request.year?.let { world.currentYear = it.toShort() }
        request.month?.let { world.currentMonth = it.toShort() }
        request.startYear?.let {
            world.config["startYear"] = it
            world.config["startyear"] = it
        }
        request.locked?.let { world.config["locked"] = it }
        request.turnTerm?.let {
            val minutes = it.coerceAtLeast(1)
            world.tickSeconds = minutes * 60
            world.config["turnTerm"] = minutes
            world.config["turnterm"] = minutes
        }
        request.auctionSync?.let { world.config["auctionSync"] = it }
        request.auctionCloseMinutes?.let { world.config["auctionCloseMinutes"] = it.coerceAtLeast(1) }
        request.opentime?.let { world.config["opentime"] = it }
        request.startTime?.let { world.config["startTime"] = it }
        request.reserveOpen?.let { world.config["reserveOpen"] = it }
        request.preReserveOpen?.let { world.config["preReserveOpen"] = it }
        request.distribute?.let {
            if (!applyResourceDistribution(worldId, it)) {
                return false
            }
        }

        sessionStateRepository.save(world)
        return true
    }

    private fun applyResourceDistribution(worldId: Long, request: ResourceDistributionRequest): Boolean {
        return when (request.target.lowercase()) {
            "all" -> {
                val generals = officerRepository.findBySessionId(worldId)
                generals.forEach { officer ->
                    officer.funds += request.gold
                    officer.supplies += request.rice
                }
                officerRepository.saveAll(generals)
                true
            }
            "nations" -> {
                val nations = factionRepository.findBySessionId(worldId)
                nations.forEach { faction ->
                    faction.funds += request.gold
                    faction.supplies += request.rice
                }
                factionRepository.saveAll(nations)
                true
            }
            else -> false
        }
    }

    private fun applyGeneralAction(officer: Officer, type: String): Boolean {
        when (type) {
            "block" -> {
                officer.blockState = 1
                officer.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block2" -> {
                officer.funds = 0
                officer.supplies = 0
                officer.blockState = 2
                officer.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block3" -> {
                officer.funds = 0
                officer.supplies = 0
                officer.blockState = 3
                officer.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "unblock" -> officer.blockState = 0
            "kill" -> {
                officer.killTurn = 0
                officer.turnTime = OffsetDateTime.now()
                upsertOfficerTurn(officer, 0, "휴식", "휴식")
            }
            "killturnInfinite" -> officer.killTurn = INFINITE_KILL_TURN.toShort()
            "resign" -> upsertOfficerTurn(officer, 0, "che_하야", "하야")
            "wanderDismiss" -> {
                upsertOfficerTurn(officer, 0, "che_방랑", "방랑")
                upsertOfficerTurn(officer, 1, "che_해산", "해산")
            }
            "exp1000" -> officer.experience += 1000
            "dedication1000" -> officer.dedication += 1000
            "dex1_10000" -> officer.dex1 += 10000
            "dex2_10000" -> officer.dex2 += 10000
            "dex3_10000" -> officer.dex3 += 10000
            "dex4_10000" -> officer.dex4 += 10000
            "dex5_10000" -> officer.dex5 += 10000
            else -> return false
        }

        return true
    }

    private fun upsertHallOfFame(world: com.openlogh.entity.SessionState, officer: Officer) {
        val faction = factionRepository.findById(officer.factionId).orElse(null)
        val serverId = (world.config["serverId"] as? String).orEmpty().ifBlank { world.name }
        val scenario = (world.meta["scenarioId"] as? Number)?.toInt() ?: 0
        val season = ((world.meta["season"] as? Number)?.toInt())?.takeIf { it > 0 } ?: 1
        val rank = officer.meta["rank"] as? Map<*, *> ?: emptyMap<String, Any>()
        val warnum = (rank["warnum"] as? Number)?.toInt() ?: 0
        val killnum = (rank["killnum"] as? Number)?.toInt() ?: 0
        val firenum = (rank["firenum"] as? Number)?.toInt() ?: 0
        val killcrew = (rank["killcrew"] as? Number)?.toInt() ?: 0
        val deathcrew = (rank["deathcrew"] as? Number)?.toInt() ?: 0

        val hallValues = linkedMapOf(
            "experience" to officer.experience.toDouble(),
            "dedication" to officer.dedication.toDouble(),
            "warnum" to warnum.toDouble(),
            "killnum" to killnum.toDouble(),
            "firenum" to firenum.toDouble(),
            "winrate" to rate(killnum, warnum),
            "killrate" to rate(killcrew, deathcrew),
        )

        for ((type, value) in hallValues) {
            if ((type == "winrate" || type == "killrate") && warnum < 10) continue
            if (value <= 0.0) continue

            val aux = mutableMapOf<String, Any>(
                "name" to officer.name,
                "factionName" to (faction?.name ?: "재야"),
                "bgColor" to (faction?.color ?: "#000000"),
                "fgColor" to (faction?.color ?: "#000000"),
                "picture" to officer.picture,
                "imgsvr" to officer.imageServer,
            )

            val existing = hallOfFameRepository.findByServerIdAndTypeAndOfficerNo(serverId, type, officer.id)
            if (existing == null) {
                hallOfFameRepository.save(
                    HallOfFame(
                        serverId = serverId,
                        season = season,
                        scenario = scenario,
                        officerNo = officer.id,
                        type = type,
                        value = value,
                        owner = officer.userId?.toString(),
                        aux = aux,
                    )
                )
            } else if (value > existing.value) {
                existing.value = value
                existing.owner = officer.userId?.toString()
                existing.aux = aux
                hallOfFameRepository.save(existing)
            }
        }
    }

    private fun rate(numerator: Int, denominator: Int): Double {
        if (denominator <= 0) return 0.0
        return numerator.toDouble() / denominator.toDouble()
    }

    private fun upsertOfficerTurn(
        officer: Officer,
        turnIdx: Int,
        actionCode: String,
        brief: String,
    ) {
        val turn = officerTurnRepository.findByOfficerIdOrderByTurnIdx(officer.id)
            .firstOrNull { it.turnIdx.toInt() == turnIdx }
            ?: OfficerTurn(
                sessionId = officer.sessionId,
                officerId = officer.id,
                turnIdx = turnIdx.toShort(),
            )

        turn.actionCode = actionCode
        turn.arg = mutableMapOf()
        turn.brief = brief
        officerTurnRepository.save(turn)
    }

    fun listUsers(): List<AdminUserSummary> {
        return appUserRepository.findAll().map {
            AdminUserSummary(
                id = it.id,
                loginId = it.loginId,
                displayName = it.displayName,
                role = it.role,
                grade = it.grade.toInt(),
                createdAt = it.createdAt,
                lastLoginAt = it.lastLoginAt,
            )
        }
    }

    @Transactional
    fun userAction(actorLoginId: String, id: Long, action: AdminUserAction): Boolean {
        val actor = appUserRepository.findByLoginId(actorLoginId) ?: return false
        val actorGrade = actor.grade.toInt()
        if (actorGrade < GRADE_SYSTEM_ADMIN) {
            return false
        }

        val user = appUserRepository.findById(id).orElse(null) ?: return false
        val targetGrade = user.grade.toInt()
        if (targetGrade >= actorGrade) {
            return false
        }

        when (action.type) {
            "delete" -> {
                appUserRepository.delete(user)
                return true
            }
            "setAdmin", "removeAdmin", "setGrade" -> {
                val nextGrade = resolveNextGrade(action) ?: return false
                if (nextGrade !in 0..7) {
                    return false
                }
                if (nextGrade >= actorGrade) {
                    return false
                }
                user.grade = nextGrade.toShort()
                user.role = if (nextGrade >= 5) "ADMIN" else "USER"
            }
            else -> return false
        }
        appUserRepository.save(user)
        return true
    }

    private fun resolveNextGrade(action: AdminUserAction): Int? {
        return when (action.type) {
            "setAdmin" -> 6
            "removeAdmin" -> 1
            "setGrade" -> action.grade
            else -> null
        }
    }

    @Transactional
    fun broadcastMessage(worldId: Long, generalIds: List<Long>, message: String) {
        val messages = generalIds.map { generalId ->
            com.openlogh.entity.Message(
                sessionId = worldId,
                mailboxCode = "private",
                messageType = "admin",
                srcId = 0,
                destId = generalId,
                payload = mutableMapOf(
                    "message" to message as Any,
                ),
            )
        }
        messageRepository.saveAll(messages)
    }
}
