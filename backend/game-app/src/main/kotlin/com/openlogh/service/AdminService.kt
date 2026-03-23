package com.openlogh.service

import com.openlogh.dto.AdminDashboard
import com.openlogh.dto.AdminOfficerSummary
import com.openlogh.dto.AdminUserAction
import com.openlogh.dto.AdminUserSummary
import com.openlogh.dto.AdminWorldInfo
import com.openlogh.entity.HallOfFame
import com.openlogh.dto.FactionStatistic
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
    private val worldStateRepository: SessionStateRepository,
    private val officerRepository: OfficerRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val appUserRepository: AppUserRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val hallOfFameRepository: HallOfFameRepository,
    private val messageRepository: MessageRepository,
    private val historyService: HistoryService,
) {
    private companion object {
        const val GRADE_SYSTEM_ADMIN = 6
        const val DEFAULT_BLOCK_KILL_TURN = 24
        const val INFINITE_KILL_TURN = 8000
    }

    fun getDashboard(sessionId: Long): AdminDashboard {
        val worlds = worldStateRepository.findAll()
        val world = worlds.firstOrNull { it.id.toLong() == sessionId }
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

    fun updateSettings(sessionId: Long, settings: Map<String, Any>): Boolean {
        val world = worldStateRepository.findById(sessionId.toShort()).orElse(null) ?: return false

        settings["turnTerm"]?.let {
            val minutes = (it as Number).toInt().coerceAtLeast(1)
            world.tickSeconds = minutes * 60
            world.config["turnTerm"] = minutes
            world.config["turnterm"] = minutes
        }
        settings["realtimeMode"]?.let { world.realtimeMode = it as Boolean }
        settings["commandPointRegenRate"]?.let { world.commandPointRegenRate = (it as Number).toInt() }

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

        worldStateRepository.save(world)
        return true
    }

    fun listAllOfficers(sessionId: Long): List<AdminOfficerSummary> {
        return officerRepository.findBySessionId(sessionId).map {
            AdminOfficerSummary(
                id = it.id,
                name = it.name,
                factionId = it.factionId,
                ships = it.ships,
                experience = it.experience,
                npcState = it.npcState.toInt(),
                blockState = it.blockState.toInt(),
                killTurn = it.killTurn?.toInt(),
            )
        }
    }

    @Transactional
    fun officerAction(sessionId: Long, id: Long, type: String): Boolean {
        val officer = officerRepository.findById(id).orElse(null) ?: return false
        if (officer.sessionId != sessionId) return false
        if (!applyGeneralAction(officer, type)) return false
        officerRepository.save(officer)
        return true
    }

    fun getStatistics(sessionId: Long): List<FactionStatistic> {
        val nations = factionRepository.findBySessionId(sessionId)
        return nations.map { nation ->
            val generals = officerRepository.findByNationId(nation.id)
            val cities = planetRepository.findByFactionId(nation.id)
            FactionStatistic(
                factionId = nation.id,
                name = nation.name,
                color = nation.color,
                factionRank = nation.factionRank.toInt(),
                funds = nation.funds,
                supplies = nation.supplies,
                techLevel = nation.techLevel,
                militaryPower = nation.militaryPower,
                officerCount = generals.size,
                planetCount = cities.size,
                totalShips = generals.sumOf { it.ships },
                totalPopulation = cities.sumOf { it.population },
            )
        }
    }

    fun getGeneralLogs(sessionId: Long, id: Long): List<Any> {
        return messageRepository.findBySessionIdAndMailboxCodeAndDestIdOrderBySentAtDesc(sessionId, "general_action", id)
    }

    fun getDiplomacyMatrix(sessionId: Long): List<Any> {
        return diplomacyRepository.findBySessionId(sessionId)
    }

    fun writeLog(sessionId: Long, message: String): Boolean {
        val world = worldStateRepository.findById(sessionId.toShort()).orElse(null) ?: return false
        historyService.logWorldHistory(
            sessionId = sessionId,
            message = message,
            year = world.currentYear.toInt(),
            month = world.currentMonth.toInt(),
        )
        return true
    }

    @Transactional
    fun forceRehall(sessionId: Long): Map<String, Int>? {
        val world = worldStateRepository.findById(sessionId.toShort()).orElse(null) ?: return null
        val isUnited = ((world.config["isunited"] as? Number)?.toInt())
            ?: ((world.config["isUnited"] as? Number)?.toInt())
            ?: 0
        require(isUnited != 0) { "아직 천통하지 않았습니다" }

        val generals = officerRepository.findBySessionId(sessionId)
        val eligibleHallGenerals = generals.filter { it.npcState.toInt() < 2 && it.age.toInt() >= 40 }
        eligibleHallGenerals.forEach { upsertHallOfFame(world, it) }

        return mapOf(
            "processedGenerals" to eligibleHallGenerals.size,
            "updatedUsers" to 0,
        )
    }

    fun timeControl(sessionId: Long, request: TimeControlRequest): Boolean {
        val world = worldStateRepository.findById(sessionId.toShort()).orElse(null) ?: return false

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
            if (!applyResourceDistribution(sessionId, it)) {
                return false
            }
        }

        worldStateRepository.save(world)
        return true
    }

    private fun applyResourceDistribution(sessionId: Long, request: ResourceDistributionRequest): Boolean {
        return when (request.target.lowercase()) {
            "all" -> {
                val generals = officerRepository.findBySessionId(sessionId)
                generals.forEach { general ->
                    general.funds += request.funds
                    general.supplies += request.supplies
                }
                officerRepository.saveAll(generals)
                true
            }
            "nations" -> {
                val nations = factionRepository.findBySessionId(sessionId)
                nations.forEach { nation ->
                    nation.funds += request.funds
                    nation.supplies += request.supplies
                }
                factionRepository.saveAll(nations)
                true
            }
            else -> false
        }
    }

    private fun applyGeneralAction(general: Officer, type: String): Boolean {
        when (type) {
            "block" -> {
                general.blockState = 1
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block2" -> {
                general.funds = 0
                general.supplies = 0
                general.blockState = 2
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block3" -> {
                general.funds = 0
                general.supplies = 0
                general.blockState = 3
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "unblock" -> general.blockState = 0
            "kill" -> {
                general.killTurn = 0
                general.turnTime = OffsetDateTime.now()
                upsertOfficerTurn(general, 0, "휴식", "휴식")
            }
            "killturnInfinite" -> general.killTurn = INFINITE_KILL_TURN.toShort()
            "resign" -> upsertOfficerTurn(general, 0, "che_하야", "하야")
            "wanderDismiss" -> {
                upsertOfficerTurn(general, 0, "che_방랑", "방랑")
                upsertOfficerTurn(general, 1, "che_해산", "해산")
            }
            "exp1000" -> general.experience += 1000
            "dedication1000" -> general.dedication += 1000
            "dex1_10000" -> general.dex1 += 10000
            "dex2_10000" -> general.dex2 += 10000
            "dex3_10000" -> general.dex3 += 10000
            "dex4_10000" -> general.dex4 += 10000
            "dex5_10000" -> general.dex5 += 10000
            else -> return false
        }

        return true
    }

    private fun upsertHallOfFame(world: com.openlogh.entity.SessionState, general: Officer) {
        val faction = factionRepository.findById(general.factionId).orElse(null)
        val serverId = (world.config["serverId"] as? String).orEmpty().ifBlank { world.name }
        val scenario = (world.meta["scenarioId"] as? Number)?.toInt() ?: 0
        val season = ((world.meta["season"] as? Number)?.toInt())?.takeIf { it > 0 } ?: 1
        val rank = general.meta["rank"] as? Map<*, *> ?: emptyMap<String, Any>()
        val warnum = (rank["warnum"] as? Number)?.toInt() ?: 0
        val killnum = (rank["killnum"] as? Number)?.toInt() ?: 0
        val firenum = (rank["firenum"] as? Number)?.toInt() ?: 0
        val killcrew = (rank["killcrew"] as? Number)?.toInt() ?: 0
        val deathcrew = (rank["deathcrew"] as? Number)?.toInt() ?: 0

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
            if ((type == "winrate" || type == "killrate") && warnum < 10) continue
            if (value <= 0.0) continue

            val aux = mutableMapOf<String, Any>(
                "name" to general.name,
                "nationName" to (faction?.name ?: "재야"),
                "bgColor" to (faction?.color ?: "#000000"),
                "fgColor" to (faction?.color ?: "#000000"),
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
                        generalNo = general.id,
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
        if (denominator <= 0) return 0.0
        return numerator.toDouble() / denominator.toDouble()
    }

    private fun upsertOfficerTurn(
        general: Officer,
        turnIdx: Int,
        actionCode: String,
        brief: String,
    ) {
        val turn = officerTurnRepository.findByOfficerIdOrderByTurnIdx(general.id)
            .firstOrNull { it.turnIdx.toInt() == turnIdx }
            ?: OfficerTurn(
                sessionId = general.sessionId,
                officerId = general.id,
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

    fun broadcastMessage(sessionId: Long, officerIds: List<Long>, message: String) {
        val messages = officerIds.map { officerId ->
            com.openlogh.entity.Message(
                sessionId = sessionId,
                mailboxCode = "private",
                messageType = "admin",
                srcId = 0,
                destId = officerId,
                payload = mutableMapOf(
                    "message" to message as Any,
                ),
            )
        }
        messageRepository.saveAll(messages)
    }
}
