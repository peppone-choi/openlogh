package com.opensam.service

import com.opensam.dto.AdminDashboard
import com.opensam.dto.AdminGeneralSummary
import com.opensam.dto.AdminUserAction
import com.opensam.dto.AdminUserSummary
import com.opensam.dto.AdminWorldInfo
import com.opensam.entity.HallOfFame
import com.opensam.dto.NationStatistic
import com.opensam.dto.ResourceDistributionRequest
import com.opensam.dto.TimeControlRequest
import com.opensam.entity.General
import com.opensam.entity.GeneralTurn
import com.opensam.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class AdminService(
    private val worldStateRepository: WorldStateRepository,
    private val generalRepository: GeneralRepository,
    private val generalTurnRepository: GeneralTurnRepository,
    private val nationRepository: NationRepository,
    private val cityRepository: CityRepository,
    private val appUserRepository: AppUserRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val hallOfFameRepository: HallOfFameRepository,
    private val messageRepository: MessageRepository,
    private val eventActionService: com.opensam.engine.EventActionService,
    private val inheritanceService: InheritanceService,
) {
    private companion object {
        const val GRADE_SYSTEM_ADMIN = 6
        const val DEFAULT_BLOCK_KILL_TURN = 24
        const val INFINITE_KILL_TURN = 8000
    }

    fun getDashboard(worldId: Long): AdminDashboard {
        val worlds = worldStateRepository.findAll()
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

    fun updateSettings(worldId: Long, settings: Map<String, Any>): Boolean {
        val world = worldStateRepository.findById(worldId.toShort()).orElse(null) ?: return false

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

        worldStateRepository.save(world)
        return true
    }

    fun listAllGenerals(worldId: Long): List<AdminGeneralSummary> {
        return generalRepository.findByWorldId(worldId).map {
            AdminGeneralSummary(
                id = it.id,
                name = it.name,
                nationId = it.nationId,
                crew = it.crew,
                experience = it.experience,
                npcState = it.npcState.toInt(),
                blockState = it.blockState.toInt(),
                killTurn = it.killTurn?.toInt(),
            )
        }
    }

    @Transactional
    fun generalAction(worldId: Long, id: Long, type: String): Boolean {
        val general = generalRepository.findById(id).orElse(null) ?: return false
        if (general.worldId != worldId) return false
        if (!applyGeneralAction(general, type)) return false
        generalRepository.save(general)
        return true
    }

    fun getStatistics(worldId: Long): List<NationStatistic> {
        val nations = nationRepository.findByWorldId(worldId)
        return nations.map { nation ->
            val generals = generalRepository.findByNationId(nation.id)
            val cities = cityRepository.findByNationId(nation.id)
            NationStatistic(
                nationId = nation.id,
                name = nation.name,
                color = nation.color,
                level = nation.level.toInt(),
                gold = nation.gold,
                rice = nation.rice,
                tech = nation.tech,
                power = nation.power,
                genCount = generals.size,
                cityCount = cities.size,
                totalCrew = generals.sumOf { it.crew },
                totalPop = cities.sumOf { it.pop },
            )
        }
    }

    fun getGeneralLogs(worldId: Long, id: Long): List<Any> {
        return messageRepository.findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId, "general_action", id)
    }

    fun getDiplomacyMatrix(worldId: Long): List<Any> {
        return diplomacyRepository.findByWorldId(worldId)
    }

    fun writeLog(worldId: Long, message: String): Boolean {
        val world = worldStateRepository.findById(worldId.toShort()).orElse(null) ?: return false
        val msg = com.opensam.entity.Message(
            worldId = worldId,
            mailboxCode = "world_history",
            mailboxType = "PUBLIC",
            messageType = "admin_log",
            payload = mutableMapOf(
                "message" to (message as Any),
                "year" to (world.currentYear.toInt() as Any),
                "month" to (world.currentMonth.toInt() as Any),
            ),
        )
        messageRepository.save(msg)
        return true
    }

    @Transactional
    fun forceRehall(worldId: Long): Map<String, Int>? {
        val world = worldStateRepository.findById(worldId.toShort()).orElse(null) ?: return null
        val isUnited = ((world.config["isunited"] as? Number)?.toInt())
            ?: ((world.config["isUnited"] as? Number)?.toInt())
            ?: 0
        require(isUnited != 0) { "아직 천통하지 않았습니다" }

        val generals = generalRepository.findByWorldId(worldId)
        val eligibleHallGenerals = generals.filter { it.npcState.toInt() < 2 && it.age.toInt() >= 40 }
        eligibleHallGenerals.forEach { upsertHallOfFame(world, it) }

        eventActionService.mergeInheritPointRank(world)
        val updatedUsers = inheritanceService.forceReapplyMergedPoints(worldId)

        return mapOf(
            "processedGenerals" to eligibleHallGenerals.size,
            "updatedUsers" to updatedUsers,
        )
    }

    fun timeControl(worldId: Long, request: TimeControlRequest): Boolean {
        val world = worldStateRepository.findById(worldId.toShort()).orElse(null) ?: return false

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
        request.distribute?.let {
            if (!applyResourceDistribution(worldId, it)) {
                return false
            }
        }

        worldStateRepository.save(world)
        return true
    }

    private fun applyResourceDistribution(worldId: Long, request: ResourceDistributionRequest): Boolean {
        return when (request.target.lowercase()) {
            "all" -> {
                val generals = generalRepository.findByWorldId(worldId)
                generals.forEach { general ->
                    general.gold += request.gold
                    general.rice += request.rice
                }
                generalRepository.saveAll(generals)
                true
            }
            "nations" -> {
                val nations = nationRepository.findByWorldId(worldId)
                nations.forEach { nation ->
                    nation.gold += request.gold
                    nation.rice += request.rice
                }
                nationRepository.saveAll(nations)
                true
            }
            else -> false
        }
    }

    private fun applyGeneralAction(general: General, type: String): Boolean {
        when (type) {
            "block" -> {
                general.blockState = 1
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block2" -> {
                general.gold = 0
                general.rice = 0
                general.blockState = 2
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "block3" -> {
                general.gold = 0
                general.rice = 0
                general.blockState = 3
                general.killTurn = DEFAULT_BLOCK_KILL_TURN.toShort()
            }
            "unblock" -> general.blockState = 0
            "kill" -> {
                general.killTurn = 0
                general.turnTime = OffsetDateTime.now()
                upsertGeneralTurn(general, 0, "휴식", "휴식")
            }
            "killturnInfinite" -> general.killTurn = INFINITE_KILL_TURN.toShort()
            "resign" -> upsertGeneralTurn(general, 0, "che_하야", "하야")
            "wanderDismiss" -> {
                upsertGeneralTurn(general, 0, "che_방랑", "방랑")
                upsertGeneralTurn(general, 1, "che_해산", "해산")
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

    private fun upsertHallOfFame(world: com.opensam.entity.WorldState, general: General) {
        val nation = nationRepository.findById(general.nationId).orElse(null)
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

    private fun upsertGeneralTurn(
        general: General,
        turnIdx: Int,
        actionCode: String,
        brief: String,
    ) {
        val turn = generalTurnRepository.findByGeneralIdOrderByTurnIdx(general.id)
            .firstOrNull { it.turnIdx.toInt() == turnIdx }
            ?: GeneralTurn(
                worldId = general.worldId,
                generalId = general.id,
                turnIdx = turnIdx.toShort(),
            )

        turn.actionCode = actionCode
        turn.arg = mutableMapOf()
        turn.brief = brief
        generalTurnRepository.save(turn)
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

    fun broadcastMessage(worldId: Long, generalIds: List<Long>, message: String) {
        val messages = generalIds.map { generalId ->
            com.opensam.entity.Message(
                worldId = worldId,
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
