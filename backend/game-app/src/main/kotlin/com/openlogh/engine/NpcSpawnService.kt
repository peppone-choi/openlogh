package com.openlogh.engine

import com.openlogh.entity.*
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.MapService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.ln
import kotlin.math.round
import kotlin.random.Random

@Service
class NpcSpawnService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val historyService: HistoryService,
    private val mapService: MapService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MIN_DIST_USER_NATION = 3
        private const val MIN_DIST_NPC_NATION = 2
        private val NPC_NATION_COLORS = listOf(
            "#CC6600", "#996633", "#669966", "#336699",
            "#993366", "#CC9900", "#339966", "#666699",
        )
        /** Base crew type IDs by armType: 보병=1100, 궁병=1200, 기병=1300, 귀병=1400 */
        private val BASE_CREW_TYPES = intArrayOf(1100, 1200, 1300, 1400)
        // 국가 레벨별 부대장 NPC 최대 수 (레거시 ProvideNPCTroopLeader.php)
        private val MAX_NPC_TROOP_LEADERS = mapOf(
            1 to 0, 2 to 1, 3 to 3, 4 to 4, 5 to 6, 6 to 7, 7 to 9
        )
    }

    fun checkNpcSpawn(world: SessionState) {
        if (!isNpcNationSpawnEnabled(world)) {
            log.info("NPC nation spawning disabled for world {}", world.id)
            return
        }

        try {
            raiseNPCNation(world)
        } catch (e: Exception) {
            log.warn("raiseNPCNation failed: {}", e.message)
        }
    }

    /**
     * Create NPC nations in empty lv5-6 cities that are far enough from existing nations.
     * Based on legacy RaiseNPCNation.php
     */
    private fun raiseNPCNation(world: SessionState) {
        val worldId = world.id.toLong()
        val cities = planetRepository.findBySessionId(worldId)
        val mapCode = (world.config["mapCode"] as? String) ?: "che"

        // Find empty cities at level 5-6
        val emptyCities = cities.filter { it.factionId == 0L && it.level.toInt() in 5..6 }.toMutableList()
        if (emptyCities.isEmpty()) return

        val occupiedCityIds = cities.filter { it.factionId != 0L }.map { it.id }
        val npcCreatedCityIds = mutableListOf<Long>()

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "RaiseNPCNation",
            world.currentYear, world.currentMonth
        )

        // Calculate average stats for new NPC cities
        val avgCity = calcAverageCityStats(cities)
        val avgGenCount = calcAvgNationGeneralCount(worldId)
        val avgTech = calcAvgTech(worldId)

        // Shuffle empty cities
        emptyCities.shuffle(rng)

        // Build DB city ID -> map city ID lookup
        val dbToMapId = cities.associate { it.id to it.mapPlanetId }

        for (emptyCity in emptyCities) {
            // Check distance from occupied cities using map city IDs
            val tooCloseToUser = occupiedCityIds.any { occupiedId ->
                val fromMap = dbToMapId[emptyCity.id] ?: return@any false
                val toMap = dbToMapId[occupiedId] ?: return@any false
                mapService.getDistance(mapCode, fromMap, toMap).let { it in 0 until MIN_DIST_USER_NATION }
            }
            if (tooCloseToUser) continue

            // Check distance from newly created NPC cities
            val tooCloseToNpc = npcCreatedCityIds.any { npcCityId ->
                val fromMap = dbToMapId[emptyCity.id] ?: return@any false
                val toMap = dbToMapId[npcCityId] ?: return@any false
                mapService.getDistance(mapCode, fromMap, toMap).let { it in 0 until MIN_DIST_NPC_NATION }
            }
            if (tooCloseToNpc) continue

            // Create NPC nation
            buildNpcNation(world, rng, emptyCity, avgCity, avgGenCount, avgTech)
            npcCreatedCityIds.add(emptyCity.id)
        }

        for (npcCityId in npcCreatedCityIds) {
            val npcCity = cities.find { it.id == npcCityId } ?: continue
            historyService.logWorldHistory(
                worldId = worldId,
                message = "<S>◆</>ⓤ${npcCity.name}이(가) ${npcCity.name}에서 건국하였습니다.",
                year = world.currentYear.toInt(),
                month = world.currentMonth.toInt(),
            )
        }
        if (npcCreatedCityIds.isNotEmpty()) {
            log.info("Created {} NPC nations in world {}", npcCreatedCityIds.size, worldId)
        }
    }

    private fun buildNpcNation(
        world: SessionState,
        rng: Random,
        city: Planet,
        avgCity: Map<String, Int>,
        genCount: Int,
        avgTech: Float,
    ) {
        val worldId = world.id.toLong()
        val year = world.currentYear.toInt()
        val color = NPC_NATION_COLORS[rng.nextInt(NPC_NATION_COLORS.size)]

        // Save nation — DB generates ID via @GeneratedValue(IDENTITY)
        val nation = factionRepository.save(
            Faction(
                sessionId = worldId,
                name = "ⓤ${city.name}",
                color = color,
                capitalPlanetId = city.id,
                funds = 0,
                supplies = 2000,
                taxRate = 80,
                conscriptionRate = 20,
                conscriptionRateTmp = 20,
                chiefOfficerId = 0,
                techLevel = avgTech,
                factionRank = 2,
                factionType = "che_중립",
            )
        )

        // Assign city to nation
        city.factionId = nation.id
        city.approval = 100F
        // Set city stats to average
        city.population = avgCity["pop"]?.coerceAtMost(city.populationMax) ?: city.population
        city.production = avgCity["agri"]?.coerceAtMost(city.productionMax) ?: city.production
        city.commerce = avgCity["comm"]?.coerceAtMost(city.commerceMax) ?: city.commerce
        city.security = avgCity["secu"]?.coerceAtMost(city.securityMax) ?: city.security
        city.orbitalDefense = avgCity["def"]?.coerceAtMost(city.orbitalDefenseMax) ?: city.orbitalDefense
        city.fortress = avgCity["wall"]?.coerceAtMost(city.fortressMax) ?: city.fortress
        planetRepository.save(city)

        // Create ruler — DB generates ID
        val rulerStats = generateNpcStats(rng, 180)
        val ruler = officerRepository.save(
            Officer(
                sessionId = worldId,
                name = "${city.name}태수",
                factionId = nation.id,
                planetId = city.id,
                npcState = 6,
                bornYear = (year - 20).coerceIn(0, 32767).toShort(),
                deadYear = (year + 60).coerceIn(0, 32767).toShort(), // Legacy default: year+60 (GeneralBuilder.fillRemainSpecAsZero)
                leadership = rulerStats.first.coerceIn(0, 100).toShort(),
                command = rulerStats.second.coerceIn(0, 100).toShort(),
                intelligence = rulerStats.third.coerceIn(0, 100).toShort(),
                politics = derivePoliticsFromStats(rulerStats.first, rulerStats.second, rulerStats.third, rng).coerceIn(0, 100).toShort(),
                administration = deriveCharmFromStats(rulerStats.first, rulerStats.second, rulerStats.third, rng).coerceIn(0, 100).toShort(),
                officerLevel = 20,
                funds = 1000, supplies = 1000,
                ships = 1000,
                shipClass = BASE_CREW_TYPES[rng.nextInt(BASE_CREW_TYPES.size)].coerceIn(0, 32767).toShort(),
                training = 80,
                morale = 80,
                killTurn = 240,
            )
        )
        nation.chiefOfficerId = ruler.id
        factionRepository.save(nation)

        // Create NPC generals
        val npcCount = (genCount - 1).coerceAtLeast(2)
        for (i in 1..npcCount) {
            val stats = generateNpcStats(rng, 150)
            officerRepository.save(
                Officer(
                    sessionId = worldId,
                    name = "${city.name}장수$i",
                    factionId = nation.id,
                    planetId = city.id,
                    npcState = 6,
                    bornYear = (year - 20).coerceIn(0, 32767).toShort(),
                    deadYear = calcLogDeadYear(year, rng).coerceIn(0, 32767).toShort(),
                    leadership = stats.first.coerceIn(0, 100).toShort(),
                    command = stats.second.coerceIn(0, 100).toShort(),
                    intelligence = stats.third.coerceIn(0, 100).toShort(),
                    politics = derivePoliticsFromStats(stats.first, stats.second, stats.third, rng).coerceIn(0, 100).toShort(),
                    administration = deriveCharmFromStats(stats.first, stats.second, stats.third, rng).coerceIn(0, 100).toShort(),
                    funds = 1000, supplies = 1000,
                    ships = 500 + rng.nextInt(500),
                    shipClass = BASE_CREW_TYPES[rng.nextInt(BASE_CREW_TYPES.size)].coerceIn(0, 32767).toShort(),
                    training = 70,
                    morale = 70,
                )
            )
        }
    }

    private fun calcLogDeadYear(year: Int, rng: Random): Int {
        val rand = rng.nextInt(1, 1024).toDouble()
        return year + 10 + (60 * (1 - ln(rand) / ln(2.0) / 10)).toInt()
    }

    private fun derivePoliticsFromStats(leadership: Int, strength: Int, intel: Int, rng: Random): Int {
        val base = round(intel * 0.4 + leadership * 0.3 + rng.nextInt(-15, 16)).toInt()
        return base.coerceIn(30, 95)
    }

    private fun deriveCharmFromStats(leadership: Int, strength: Int, intel: Int, rng: Random): Int {
        val base = round(leadership * 0.3 + intel * 0.2 + strength * 0.1 + rng.nextInt(-15, 16)).toInt()
        return base.coerceIn(30, 95)
    }

    private fun generateNpcStats(rng: Random, totalAvg: Int): Triple<Int, Int, Int> {
        val variance = totalAvg / 6
        val stat1 = (totalAvg / 3 + rng.nextInt(-variance, variance + 1)).coerceIn(30, 100)
        val stat2 = (totalAvg / 3 + rng.nextInt(-variance, variance + 1)).coerceIn(30, 100)
        val stat3 = (totalAvg - stat1 - stat2).coerceIn(30, 100)
        return Triple(stat1, stat2, stat3)
    }

    private fun calcAverageCityStats(cities: List<Planet>): Map<String, Int> {
        val nationCities = cities.filter { it.factionId != 0L }
        if (nationCities.isEmpty()) {
            return mapOf("pop" to 5000, "agri" to 500, "comm" to 500, "secu" to 500, "def" to 500, "wall" to 500)
        }
        // Sort by stat sum, trim top/bottom round(count/6) outliers
        val sorted = nationCities.sortedBy { it.population + it.production + it.commerce + it.security + it.orbitalDefense + it.fortress }
        val trimCount = round(sorted.size / 6.0).toInt()
        val trimmed = if (sorted.size > trimCount * 2) {
            sorted.subList(trimCount, sorted.size - trimCount)
        } else {
            sorted
        }
        return mapOf(
            "pop" to trimmed.map { it.population }.average().toInt(),
            "agri" to trimmed.map { it.production }.average().toInt(),
            "comm" to trimmed.map { it.commerce }.average().toInt(),
            "secu" to trimmed.map { it.security }.average().toInt(),
            "def" to trimmed.map { it.orbitalDefense }.average().toInt(),
            "wall" to trimmed.map { it.fortress }.average().toInt(),
        )
    }

    private fun calcAvgNationGeneralCount(worldId: Long): Int {
        val nations = factionRepository.findBySessionId(worldId).filter { it.factionRank > 0 }
        if (nations.isEmpty()) return 5
        val generals = officerRepository.findBySessionId(worldId)
        val countsByNation = generals.groupBy { it.factionId }.mapValues { it.value.size }
        val counts = nations.mapNotNull { countsByNation[it.id] }.filter { it > 0 }
        return if (counts.isEmpty()) 5 else counts.average().toInt()
    }

    private fun calcAvgTech(worldId: Long): Float {
        val nations = factionRepository.findBySessionId(worldId).filter { it.factionRank > 0 }
        if (nations.isEmpty()) return 0f
        return nations.map { it.techLevel }.average().toFloat()
    }


    /**
     * 부대장 NPC를 국가 레벨에 맞게 보충.
     * Based on legacy ProvideNPCTroopLeader.php
     * npcState=5 장수를 국가별 최대치까지 생성, troopId = 자기 자신 (부대장)
     */
    fun provideNpcTroopLeaders(world: SessionState) {
        val worldId = world.id.toLong()
        val nations = factionRepository.findBySessionId(worldId)
        val allGenerals = officerRepository.findBySessionId(worldId)

        // 국가별 npcState=5 장수 수 집계
        val npc5CountByNation = allGenerals.filter { it.npcState.toInt() == 5 }
            .groupBy { it.factionId }
            .mapValues { it.value.size }

        for (nation in nations) {
            val level = nation.factionRank.toInt()
            if (level <= 0) continue
            val maxCount = MAX_NPC_TROOP_LEADERS[level] ?: 0
            var currentCount = npc5CountByNation[nation.id] ?: 0
            if (currentCount >= maxCount) continue

            val capitalCity = nation.capitalPlanetId ?: continue

            val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
            val rng = DeterministicRng.create(
                hiddenSeed, "troopLeader",
                world.currentYear, world.currentMonth,
                nation.id.toInt()
            )

            while (currentCount < maxCount) {
                val nameSeq = rng.nextInt(10000)
                val npc = officerRepository.save(
                    Officer(
                        sessionId = worldId,
                        name = "부대장${String.format("%04d", nameSeq)}",
                        factionId = nation.id,
                        planetId = capitalCity,
                        npcState = 5,
                        affinity = 999,
                        bornYear = (world.currentYear - 20).coerceIn(0, 32767).toShort(),
                        deadYear = (world.currentYear + 30).coerceIn(0, 32767).toShort(),
                        leadership = 10,
                        command = 10,
                        intelligence = 10,
                        politics = 10,
                        administration = 10,
                        funds = 0, supplies = 0,
                        ships = 0,
                        shipClass = 1,
                        training = 0,
                        morale = 0,
                        killTurn = 70,
                        personalCode = "che_은둔",
                    )
                )
                // 부대장 = troopId가 자기 자신
                npc.fleetId = npc.id
                officerRepository.save(npc)
                currentCount++
            }
        }
    }

    /**
     * Raise invader nations in all lv4 cities.
     * Based on legacy RaiseInvader.php - called as a special event, not every turn.
     */
    fun raiseInvader(world: SessionState) {
        if (!isInvaderSpawnEnabled(world)) {
            log.info("Invader spawning disabled for world {}", world.id)
            return
        }

        val worldId = world.id.toLong()
        val cities = planetRepository.findBySessionId(worldId)
        val lv4Cities = cities.filter { it.level.toInt() == 4 }
        if (lv4Cities.isEmpty()) return

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "RaiseInvader",
            world.currentYear, world.currentMonth
        )

        val existingNations = factionRepository.findBySessionId(worldId)
        val generals = officerRepository.findBySessionId(worldId)
        val avgStatTotal = if (generals.isNotEmpty()) {
            generals.filter { it.npcState < 4 }
                .map { it.leadership + it.command + it.intelligence }
                .average().toInt()
        } else 180
        val specAvg = avgStatTotal / 3
        val avgTech = calcAvgTech(worldId)
        val avgExp = generals.map { it.experience }.average().toInt()

        // Free all lv4 cities first
        for (city in lv4Cities) {
            if (city.factionId != 0L) {
                // Move capital away if needed
                val nation = existingNations.find { it.capitalPlanetId == city.id }
                if (nation != null) {
                    val otherCities = cities.filter { it.factionId == nation.id && it.id != city.id }
                    if (otherCities.isNotEmpty()) {
                        nation.capitalPlanetId = otherCities.maxByOrNull { it.population }?.id
                        factionRepository.save(nation)
                    }
                }
                city.factionId = 0
                city.frontState = 0
                city.supplyState = 1
                planetRepository.save(city)
            }
        }

        val invaderNationIds = mutableListOf<Long>()

        for (city in lv4Cities) {
            val invaderName = city.name
            val factionName = "ⓞ${invaderName}족"
            val npcEachCount = 10.coerceAtLeast((generals.count { it.npcState < 4 } / lv4Cities.size) * 2)

            // Create invader nation — DB generates ID
            val nation = factionRepository.save(
                Faction(
                    sessionId = worldId,
                    name = factionName,
                    color = "#800080",
                    capitalPlanetId = city.id,
                    funds = 9999999,
                    supplies = 9999999,
                    taxRate = 80,
                    conscriptionRate = 20,
                    conscriptionRateTmp = 20,
                    chiefOfficerId = 0,
                    techLevel = avgTech * 1.2f,
                    factionRank = 2,
                    factionType = "che_병가",
                )
            )
            invaderNationIds.add(nation.id)

            city.factionId = nation.id
            city.population = city.populationMax
            city.production = city.productionMax
            city.commerce = city.commerceMax
            city.security = city.securityMax
            planetRepository.save(city)

            // Create ruler
            val rulerLeadership = (specAvg * 1.8).toInt().coerceAtMost(100)
            val rulerStrength = (specAvg * 1.8).toInt().coerceAtMost(100)
            val rulerIntel = (specAvg * 1.2).toInt().coerceAtMost(100)
            val ruler = officerRepository.save(
                Officer(
                    sessionId = worldId,
                    name = "${invaderName}대왕",
                    factionId = nation.id,
                    planetId = city.id,
                    npcState = 9,
                    affinity = 999,
                    bornYear = (world.currentYear - 20).coerceIn(0, 32767).toShort(),
                    deadYear = (world.currentYear + 20).coerceIn(0, 32767).toShort(),
                    leadership = rulerLeadership.coerceIn(0, 100).toShort(),
                    command = rulerStrength.coerceIn(0, 100).toShort(),
                    intelligence = rulerIntel.coerceIn(0, 100).toShort(),
                    politics = derivePoliticsFromStats(rulerLeadership, rulerStrength, rulerIntel, rng).coerceIn(0, 100).toShort(),
                    administration = deriveCharmFromStats(rulerLeadership, rulerStrength, rulerIntel, rng).coerceIn(0, 100).toShort(),
                    officerLevel = 20,
                    experience = (avgExp * 1.2).toInt(),
                    funds = 99999, supplies = 99999,
                    ships = 5000,
                    shipClass = rng.nextInt(1, 5).coerceIn(0, 32767).toShort(),
                    training = 100,
                    morale = 100,
                )
            )
            nation.chiefOfficerId = ruler.id
            factionRepository.save(nation)

            // Create invader generals
            for (i in 1 until npcEachCount) {
                val leadership = rng.nextInt((specAvg * 1.2).toInt(), (specAvg * 1.4).toInt() + 1).coerceAtMost(100)
                val mainStat = rng.nextInt((specAvg * 1.2).toInt(), (specAvg * 1.4).toInt() + 1).coerceAtMost(100)
                val subStat = (specAvg * 3 - leadership - mainStat).coerceIn(30, 100)

                val (str, intel) = if (rng.nextBoolean()) {
                    mainStat to subStat  // warrior
                } else {
                    subStat to mainStat  // strategist
                }

                officerRepository.save(
                    Officer(
                        sessionId = worldId,
                        name = "${invaderName}장수$i",
                        factionId = nation.id,
                        planetId = city.id,
                        npcState = 9,
                        affinity = 999,
                        bornYear = (world.currentYear - 20).coerceIn(0, 32767).toShort(),
                        deadYear = (world.currentYear + 20).coerceIn(0, 32767).toShort(),
                        leadership = leadership.coerceIn(0, 100).toShort(),
                        command = str.coerceIn(0, 100).toShort(),
                        intelligence = intel.coerceIn(0, 100).toShort(),
                        politics = derivePoliticsFromStats(leadership, str, intel, rng).coerceIn(0, 100).toShort(),
                        administration = deriveCharmFromStats(leadership, str, intel, rng).coerceIn(0, 100).toShort(),
                        experience = avgExp,
                        funds = 99999, supplies = 99999,
                        ships = 3000 + rng.nextInt(2000),
                        shipClass = rng.nextInt(1, 5).coerceIn(0, 32767).toShort(),
                        training = 90,
                        morale = 90,
                    )
                )
            }
        }

        // Set mutual war declarations: 24 months
        // All existing nations vs all invader nations
        for (invNationId in invaderNationIds) {
            val invCity = lv4Cities.find { c -> factionRepository.findById(invNationId).orElse(null)?.capitalPlanetId == c.id }
            val invNation = factionRepository.findById(invNationId).orElse(null)
            if (invCity != null && invNation != null) {
                historyService.logWorldHistory(
                    worldId = worldId,
                    message = "<R>★</>${invNation.name}이(가) ${invCity.name}에 출현하였습니다!",
                    year = world.currentYear.toInt(),
                    month = world.currentMonth.toInt(),
                )
            }
        }
        if (invaderNationIds.isNotEmpty()) {
            log.info("Raised {} invader nations in world {}", invaderNationIds.size, worldId)
        }
    }

    private fun isNpcNationSpawnEnabled(world: SessionState): Boolean {
        return readBoolean(world.config["allowNpcNationSpawn"], defaultValue = true)
    }

    private fun isInvaderSpawnEnabled(world: SessionState): Boolean {
        return readBoolean(world.config["allowInvaderSpawn"], defaultValue = true)
    }

    private fun readBoolean(raw: Any?, defaultValue: Boolean): Boolean {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> defaultValue
        }
    }
}
