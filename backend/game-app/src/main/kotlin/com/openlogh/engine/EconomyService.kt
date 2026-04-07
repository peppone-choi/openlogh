package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Message
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.round
import kotlin.math.sqrt

@Service
class EconomyService @Autowired constructor(
    private val worldPortFactory: JpaWorldPortFactory,
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val mapService: MapService,
    @Suppress("unused") private val historyService: HistoryService,
    @Suppress("unused") private val inheritanceService: InheritanceService,
) {
    constructor(
        planetRepository: PlanetRepository,
        factionRepository: FactionRepository,
        officerRepository: OfficerRepository,
        messageRepository: MessageRepository,
        mapService: MapService,
        historyService: HistoryService,
        inheritanceService: InheritanceService,
    ) : this(
        JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
        ),
        officerRepository,
        messageRepository,
        mapService,
        historyService,
        inheritanceService,
    )

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val NATION_LEVEL_THRESHOLDS = intArrayOf(0, 1, 2, 4, 6, 9, 12, 16, 20, 25)

        private val NATION_LEVEL_NAME = arrayOf(
            "방랑군", "도위", "주자사", "주목", "중랑장", "대장군", "대사마", "공", "왕", "황제"
        )

        fun getNationLevelName(level: Int): String =
            NATION_LEVEL_NAME.getOrElse(level) { "???" }
    }

    private data class DisasterOrBoomEntry(
        val stateCode: Short,
        val title: String,
        val body: String,
    )

    /**
     * TODO Phase 4: gin7 Gin7EconomyService로 대체 예정.
     * 삼국지 농업/상업 기반 경제 로직 제거됨.
     * 현재 stub — 아무 처리도 하지 않음.
     */
    @Transactional
    fun processMonthly(world: SessionState) {
        // gin7EconomyService.processMonthly()로 교체됨 — Gin7EconomyService.kt 참조
    }

    /**
     * TODO Phase 4: gin7 Gin7EconomyService로 대체 예정.
     * 삼국지 수입(agri/comm) 계산 로직 제거됨.
     * 현재 stub — 아무 처리도 하지 않음.
     */
    @Transactional
    fun preUpdateMonthly(world: SessionState) {
        // TODO Phase 4: gin7EconomyService.preUpdateMonthly(world)
    }

    /**
     * TODO Phase 4: gin7 Gin7EconomyService로 대체 예정.
     * 삼국지 반기 처리(semi-annual) 및 국가레벨 갱신 로직 제거됨.
     * 현재 stub — 아무 처리도 하지 않음.
     */
    @Transactional
    fun postUpdateMonthly(world: SessionState) {
        // TODO Phase 4: gin7EconomyService.postUpdateMonthly(world)
    }

    /**
     * Public entry point for per-turn supply state recalculation (traffic update).
     * Called by TurnService each turn to keep supply routes current.
     */
    @Transactional
    fun updateCitySupplyState(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }
        updateCitySupply(world, nations, cities, generals)
        saveCities(ports, cities)
        saveGenerals(ports, generals)
    }

    /**
     * Public entry point for event-driven income processing.
     * TODO Phase 4: wire to gin7 income calculation.
     */
    @Transactional
    fun processIncomeEvent(world: SessionState) {
        // TODO Phase 4: gin7EconomyService.processIncomeEvent(world)
        log.debug("[World {}] processIncomeEvent: stub (Phase 4 pending)", world.id)
    }

    /**
     * Public entry point for event-driven semi-annual processing.
     * TODO Phase 4: wire to gin7 semi-annual processing.
     */
    @Transactional
    fun processSemiAnnualEvent(world: SessionState) {
        // TODO Phase 4: gin7EconomyService.processSemiAnnualEvent(world)
        log.debug("[World {}] processSemiAnnualEvent: stub (Phase 4 pending)", world.id)
    }

    /**
     * Public entry point for event-driven nation level update.
     * TODO Phase 4: wire to gin7 faction rank calculation.
     */
    @Transactional
    fun updateNationLevelEvent(world: SessionState) {
        // TODO Phase 4: gin7EconomyService.updateNationLevelEvent(world)
        log.debug("[World {}] updateNationLevelEvent: stub (Phase 4 pending)", world.id)
    }

    // ── Supply state calculation (map-connectivity based — gin7 compatible) ──

    private fun updateCitySupply(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>) {
        val mapCode = (world.config["mapCode"] as? String) ?: "logh"
        val citiesByNation = cities.groupBy { it.factionId }
        val cityById = cities.associateBy { it.id }
        val generalsByCity = generals.groupBy { it.planetId }

        // Build map city ID <-> DB city ID lookups
        val dbToMapId = cities.associate { it.id to it.mapPlanetId }
        val mapToDbId = cities.associate { it.mapPlanetId to it.id }

        // Neutral cities are always supplied
        for (city in cities) {
            if (city.factionId == 0L) {
                city.supplyState = 1
            }
        }

        for (nation in nations) {
            val nationCities = citiesByNation[nation.id] ?: continue
            val suppliedMapIds = mutableSetOf<Int>()

            val capitalId = nation.capitalPlanetId
            val capitalMapId = if (capitalId != null) dbToMapId[capitalId] else null
            if (capitalMapId != null && capitalMapId != 0) {
                val queue = ArrayDeque<Int>()
                queue.add(capitalMapId)
                suppliedMapIds.add(capitalMapId)

                while (queue.isNotEmpty()) {
                    val currentMapId = queue.removeFirst()
                    val adjacentMapIds = try {
                        mapService.getAdjacentCities(mapCode, currentMapId)
                    } catch (e: Exception) {
                        log.warn("Failed to get adjacent cities for mapPlanetId={}: {}", currentMapId, e.message)
                        emptyList()
                    }
                    for (adjMapId in adjacentMapIds) {
                        if (adjMapId !in suppliedMapIds) {
                            val adjDbId = mapToDbId[adjMapId]
                            val adjCity = if (adjDbId != null) cityById[adjDbId] else null
                            if (adjCity != null && adjCity.factionId == nation.id) {
                                suppliedMapIds.add(adjMapId)
                                queue.add(adjMapId)
                            }
                        }
                    }
                }
            }

            val supplied = suppliedMapIds.mapNotNull { mapToDbId[it] }.toMutableSet()
            if (capitalId != null) supplied.add(capitalId)

            for (city in nationCities) {
                if (city.id in supplied) {
                    city.supplyState = 1
                } else {
                    city.supplyState = 0
                    city.population = (city.population * 0.9).toInt()
                    city.approval = city.approval * 0.9F
                    city.production = (city.production * 0.9).toInt()
                    city.commerce = (city.commerce * 0.9).toInt()
                    city.security = (city.security * 0.9).toInt()
                    city.orbitalDefense = (city.orbitalDefense * 0.9).toInt()
                    city.fortress = (city.fortress * 0.9).toInt()

                    val cityGenerals = generalsByCity[city.id] ?: emptyList()
                    for (general in cityGenerals) {
                        general.ships = (general.ships * 0.95).toInt()
                        general.morale = (general.morale * 0.95).toInt().coerceIn(0, 150).toShort()
                        general.training = (general.training * 0.95).toInt().coerceIn(0, 110).toShort()
                    }

                    if (city.approval < 30 && city.id != nation.capitalPlanetId) {
                        log.info("City {} (id={}) lost to isolation (trust={})", city.name, city.id, city.approval)
                        for (general in cityGenerals) {
                            if (general.officerPlanet == city.id.toInt()) {
                                general.officerLevel = 1
                                general.officerPlanet = 0
                            }
                        }
                        city.factionId = 0
                        city.officerSet = 0
                        city.conflict = mutableMapOf()
                        city.term = 0
                        city.frontState = 0
                    }
                }
            }
        }
    }

    /**
     * 연초 통계: 국가 국력(power)·장수수(gennum) 갱신.
     * TurnService에서 매년 1월에 호출.
     *
     * 국력 = (자원/100 + 기술 + 도시파워 + 장수능력 + 숙련/1000 + 경험공헌/100) / 10
     */
    @Transactional
    fun processYearlyStatistics(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val nations = ports.allFactions().map { it.toEntity() }
        val cities = ports.allPlanets().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }

        val citiesByNation = cities.groupBy { it.factionId }
        val generalsByNation = generals
            .filter { it.npcState.toInt() != 5 && it.npcState != SovereignConstants.NPC_STATE_EMPEROR }
            .groupBy { it.factionId }
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: world.id.toString()

        for (nation in nations) {
            if (nation.factionRank.toInt() == 0) continue

            val nationGenerals = generalsByNation[nation.id] ?: emptyList()
            val nationCities = citiesByNation[nation.id] ?: emptyList()

            // 자원: (국가금+쌀 + 장수금+쌀합) / 100
            val generalGoldRice = nationGenerals.sumOf { (it.funds + it.supplies).toLong() }
            val resource = ((nation.funds + nation.supplies).toLong() + generalGoldRice) / 100.0

            // 기술
            val tech = nation.techLevel.toDouble()

            // 도시파워: sum(pop) * sum(pop+production+commerce+security+fortress+orbitalDefense)
            //           / sum(popMax+productionMax+commerceMax+securityMax+fortressMax+orbitalDefenseMax) / 100
            val suppliedCities = nationCities.filter { it.supplyState.toInt() == 1 }
            val cityPower = if (suppliedCities.isNotEmpty()) {
                val popSum = suppliedCities.sumOf { it.population.toLong() }
                val valueSum = suppliedCities.sumOf { (it.population + it.production + it.commerce + it.security + it.fortress + it.orbitalDefense).toLong() }
                val maxSum = suppliedCities.sumOf { (it.populationMax + it.productionMax + it.commerceMax + it.securityMax + it.fortressMax + it.orbitalDefenseMax).toLong() }
                if (maxSum > 0) (popSum * valueSum).toDouble() / maxSum / 100.0 else 0.0
            } else 0.0

            // 장수능력
            val statPower = nationGenerals.sumOf { g ->
                val lead = g.leadership.toDouble()
                val str = g.command.toDouble()
                val intel = g.intelligence.toDouble()
                val npcMul = if (g.npcState < 2) 1.2 else 1.0
                val leaderCore = if (lead >= 40) lead else 0.0
                npcMul * leaderCore * 2 + (sqrt(intel * str) * 2 + lead / 2) / 2
            }

            // 숙련: sum(dex1..dex5) / 1000
            val dexSum = nationGenerals.sumOf { (it.dex1 + it.dex2 + it.dex3 + it.dex4 + it.dex5).toLong() }
            val dexPower = dexSum / 1000.0

            // 경험공헌: sum(experience+dedication) / 100
            val expDed = nationGenerals.sumOf { (it.experience + it.dedication).toLong() } / 100.0

            val rawPower = ((resource + tech + cityPower + statPower + dexPower + expDed) / 10.0)
            val rng = DeterministicRng.create(
                hiddenSeed,
                "nationPower",
                world.currentYear,
                world.currentMonth,
                nation.id,
            )
            val power = round(rawPower * rng.nextDouble(0.95, 1.05)).toInt()

            // 최대 국력 기록
            val prevMaxPower = (nation.meta["maxPower"] as? Number)?.toInt() ?: 0
            if (power > prevMaxPower) {
                nation.meta["maxPower"] = power
            }

            nation.militaryPower = power
        }

        saveNations(ports, nations)
        log.info("[World {}] Yearly statistics updated for {} nations", world.id, nations.size)
    }

    // ── Phase A3: processDisasterOrBoom ──

    fun processDisasterOrBoom(world: SessionState) {
        val startYear = try {
            (world.config["startYear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        } catch (e: Exception) {
            log.warn("Failed to resolve startYear from config: {}", e.message)
            world.currentYear.toInt()
        }

        // Skip first 3 years
        if (startYear + 3 > world.currentYear.toInt()) return

        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val month = world.currentMonth.toInt()

        // Reset disaster state
        for (city in cities) {
            if (city.state <= 10) {
                city.state = 0
            }
        }

        // Boom probability by month (4,7 = 25%, others = 0)
        val boomRate = when (month) {
            4, 7 -> 0.25
            else -> 0.0
        }

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "disaster",
            world.currentYear, world.currentMonth
        )
        val isGood = boomRate > 0 && rng.nextDouble() < boomRate

        val targetCities = mutableListOf<Planet>()
        for (city in cities) {
            val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax else 0.0
            val raiseProp = if (isGood) {
                0.02 + secuRatio * 0.05  // 2~7%
            } else {
                0.06 - secuRatio * 0.05  // 1~6%
            }
            if (rng.nextDouble() < raiseProp) {
                targetCities.add(city)
            }
        }

        if (targetCities.isEmpty()) {
            saveCities(ports, cities)
            return
        }

        val disasterEntries = mapOf(
            1 to listOf(
                DisasterOrBoomEntry(4, "【재난】", "역병이 발생하여 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "에너지 부족으로 주민들이 고통받고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
            4 to listOf(
                DisasterOrBoomEntry(7, "【재난】", "우주 방사선으로 인해 피해가 급증하고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(6, "【재난】", "소행성 충돌로 인해 피해가 속출하고 있습니다."),
            ),
            7 to listOf(
                DisasterOrBoomEntry(8, "【재난】", "자원 고갈로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(8, "【재난】", "흉작으로 굶어죽는 주민들이 늘어나고 있습니다."),
            ),
            10 to listOf(
                DisasterOrBoomEntry(3, "【재난】", "혹한 행성 환경으로 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "항성 폭풍으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "함대 봉쇄로 인해 행성이 황폐해지고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "반란군이 출현해 행성을 습격하고 있습니다."),
            ),
        )
        val boomEntries = mapOf(
            4 to DisasterOrBoomEntry(2, "【호황】", "호황으로 행성이 번창하고 있습니다."),
            7 to DisasterOrBoomEntry(1, "【풍작】", "풍년으로 행성이 번창하고 있습니다."),
        )

        if (isGood) {
            val entry = boomEntries[month] ?: boomEntries[4]!!
            for (city in targetCities) {
                val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax / 0.8 else 0.0
                val affectRatio = 1.01 + secuRatio.coerceIn(0.0, 1.0) * 0.04

                city.state = entry.stateCode
                city.population = (city.population * affectRatio).toInt().coerceAtMost(city.populationMax)
                city.approval = (city.approval * affectRatio.toFloat()).coerceAtMost(100F)
                city.production = (city.production * affectRatio).toInt().coerceAtMost(city.productionMax)
                city.commerce = (city.commerce * affectRatio).toInt().coerceAtMost(city.commerceMax)
                city.security = (city.security * affectRatio).toInt().coerceAtMost(city.securityMax)
                city.orbitalDefense = (city.orbitalDefense * affectRatio).toInt().coerceAtMost(city.orbitalDefenseMax)
                city.fortress = (city.fortress * affectRatio).toInt().coerceAtMost(city.fortressMax)
            }

            val cityNames = targetCities.joinToString(" ") { it.name }
            historyService.logWorldHistory(
                worldId = world.id.toLong(),
                message = "${entry.title} ${cityNames}에 ${entry.body}",
                year = world.currentYear.toInt(),
                month = month,
            )
        } else {
            val entries = disasterEntries[month] ?: disasterEntries[1]!!
            val entry = entries[rng.nextInt(entries.size)]
            for (city in targetCities) {
                val secuRatio = if (city.securityMax > 0) city.security.toDouble() / city.securityMax / 0.8 else 0.0
                val affectRatio = 0.8 + secuRatio.coerceIn(0.0, 1.0) * 0.15

                city.state = entry.stateCode
                city.population = (city.population * affectRatio).toInt()
                city.approval = city.approval * affectRatio.toFloat()
                city.production = (city.production * affectRatio).toInt()
                city.commerce = (city.commerce * affectRatio).toInt()
                city.security = (city.security * affectRatio).toInt()
                city.orbitalDefense = (city.orbitalDefense * affectRatio).toInt()
                city.fortress = (city.fortress * affectRatio).toInt()
            }

            val affectedCityIds = targetCities.map { it.id }
            val generals = officerRepository.findBySessionIdAndPlanetIdIn(world.id.toLong(), affectedCityIds)
            val injuryMessages = mutableListOf<Message>()
            for (general in generals) {
                if (rng.nextDouble() >= 0.3) continue
                val injuryAmount = rng.nextInt(1, 17)
                general.injury = (general.injury + injuryAmount).coerceIn(0, 80).toShort()
                general.ships = (general.ships * 0.98).toInt()
                general.morale = (general.morale * 0.98).toInt().coerceIn(0, 150).toShort()
                general.training = (general.training * 0.98).toInt().coerceIn(0, 110).toShort()
                injuryMessages += Message(
                    sessionId = world.id.toLong(),
                    mailboxCode = "general_action",
                    messageType = "log",
                    srcId = general.id,
                    destId = general.id,
                    payload = mutableMapOf(
                        "message" to "재난으로 인해 <R>부상</>을 당했습니다.",
                        "year" to world.currentYear.toInt(),
                        "month" to month,
                    ),
                )
            }
            officerRepository.saveAll(generals)
            if (injuryMessages.isNotEmpty()) {
                messageRepository.saveAll(injuryMessages)
            }

            val cityNames = targetCities.joinToString(" ") { it.name }
            historyService.logWorldHistory(
                worldId = world.id.toLong(),
                message = "${entry.title} ${cityNames}에 ${entry.body}",
                year = world.currentYear.toInt(),
                month = month,
            )
        }

        saveCities(ports, cities)
    }

    // ── Phase A3: randomizeCityTradeRate ──

    fun randomizeCityTradeRate(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "tradeRate",
            world.currentYear, world.currentMonth
        )

        // Level-based probability
        val probByLevel = mapOf(
            4 to 0.2, 5 to 0.4, 6 to 0.6, 7 to 0.8, 8 to 1.0
        )

        for (city in cities) {
            val prob = probByLevel[city.level.toInt()] ?: 0.0
            if (prob > 0 && rng.nextDouble() < prob) {
                city.tradeRoute = rng.nextInt(95, 106)  // 95~105 inclusive
            } else {
                city.tradeRoute = 100  // 확률 미달: 기본값으로 리셋
            }
        }

        saveCities(ports, cities)
    }

    private fun saveCities(writePort: WorldWritePort, cities: List<Planet>) {
        cities.forEach { writePort.putPlanet(it.toSnapshot()) }
    }

    private fun saveNations(writePort: WorldWritePort, nations: List<Faction>) {
        nations.forEach { writePort.putFaction(it.toSnapshot()) }
    }

    private fun saveGenerals(writePort: WorldWritePort, generals: List<Officer>) {
        generals.forEach { writePort.putOfficer(it.toSnapshot()) }
    }
}
