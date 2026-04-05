package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.engine.modifier.IncomeContext
import com.openlogh.engine.modifier.NationTypeModifiers
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
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Service
class EconomyService @Autowired constructor(
    private val worldPortFactory: JpaWorldPortFactory,
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val mapService: MapService,
    private val historyService: HistoryService,
    private val inheritanceService: InheritanceService,
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
        private const val BASE_GOLD = 0
        private const val BASE_RICE = 2000
        private const val BASE_POP_INCREASE = 5000
        private const val MAX_DED_LEVEL = 30

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

    @Transactional
    fun processMonthly(world: SessionState) {
        preUpdateMonthly(world)
        postUpdateMonthly(world)
    }

    @Transactional
    fun preUpdateMonthly(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }

        processIncome(world, nations, cities, generals)
        processWarIncome(nations, cities)

        saveCities(ports, cities)
        saveNations(ports, nations)
        saveGenerals(ports, generals)
    }

    @Transactional
    fun postUpdateMonthly(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }

        if (world.currentMonth.toInt() == 1 || world.currentMonth.toInt() == 7) {
            processSemiAnnual(world, nations, cities, generals)
        }

        updateCitySupply(world, nations, cities, generals)
        updateNationLevel(world, nations, cities, generals)

        saveCities(ports, cities)
        saveNations(ports, nations)
        saveGenerals(ports, generals)
    }

    // ── Phase A1: processIncome (legacy formula) ──

    // resourceType: "gold" = gold only, "rice" = rice only, null/"all" = both (legacy: gold spring, rice autumn)
    private fun processIncome(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>, resourceType: String = "all") {
        val citiesByNation = cities.groupBy { it.factionId }
        val generalsByNation = generals
            .filter { it.npcState.toInt() != 5 && it.npcState != SovereignConstants.NPC_STATE_EMPEROR }
            .groupBy { it.factionId }

        // Count officers (officer_level 2-4) per city who are in their assigned city
        val officerCountByCity = generals
            .filter { it.officerLevel in 2..4 && it.planetId == it.officerCity.toLong() }
            .groupBy { it.planetId }
            .mapValues { it.value.size }

        for (nation in nations) {
            val nationCities = citiesByNation[nation.id] ?: continue
            val nationGenerals = generalsByNation[nation.id] ?: emptyList()
            val taxRate = nation.conscriptionRateTmp.toDouble()
            val nationLevel = nation.factionRank.toInt().coerceAtLeast(1)

            // Get nation type modifier (applied per-city, legacy parity: onCalcNationalIncome per city)
            val nationTypeMod = NationTypeModifiers.get(nation.factionType)
            val baseIncomeCtx = if (nationTypeMod != null) nationTypeMod.onCalcIncome(IncomeContext()) else IncomeContext()

            // Calculate city-level income with per-city modifier (H1)
            var totalGoldIncome = 0.0
            var totalRiceIncome = 0.0

            for (city in nationCities) {
                if (city.supplyState.toInt() == 0) continue
                val officerCnt = officerCountByCity[city.id] ?: 0
                val isCapital = city.id == nation.capitalPlanetId

                if (resourceType != "rice") {
                    totalGoldIncome += calcCityGoldIncome(city, officerCnt, isCapital, nationLevel, baseIncomeCtx.goldMultiplier)
                }
                if (resourceType != "gold") {
                    totalRiceIncome += calcCityRiceIncome(city, officerCnt, isCapital, nationLevel, baseIncomeCtx.riceMultiplier)
                    totalRiceIncome += calcCityWallIncome(city, officerCnt, isCapital, nationLevel, baseIncomeCtx.riceMultiplier)
                }
            }

            // Apply tax rate multiplier
            val goldIncome = (totalGoldIncome * taxRate / 20).toInt()
            val riceIncome = (totalRiceIncome * taxRate / 20).toInt()

            // Add income to nation treasury
            if (resourceType != "rice") nation.funds += goldIncome
            if (resourceType != "gold") nation.supplies += riceIncome

            // Calculate bill/salary
            val totalBill = nationGenerals.sumOf { getBill(it.dedication) }
            val goldOutcome = (nation.taxRate.toDouble() / 100 * totalBill).toInt()
            val riceOutcome = (nation.taxRate.toDouble() / 100 * totalBill).toInt()

            // Pay salaries (gold)
            val goldRatio = if (totalBill == 0) 0.0
            else if (nation.funds < BASE_GOLD) 0.0
            else if (nation.funds - BASE_GOLD < goldOutcome) {
                val realOutcome = nation.funds - BASE_GOLD
                nation.funds = BASE_GOLD
                realOutcome.toDouble() / totalBill
            } else {
                nation.funds -= goldOutcome
                goldOutcome.toDouble() / totalBill
            }

            // Pay salaries (rice)
            val riceRatio = if (totalBill == 0) 0.0
            else if (nation.supplies < BASE_RICE) 0.0
            else if (nation.supplies - BASE_RICE < riceOutcome) {
                val realOutcome = nation.supplies - BASE_RICE
                nation.supplies = BASE_RICE
                realOutcome.toDouble() / totalBill
            } else {
                nation.supplies -= riceOutcome
                riceOutcome.toDouble() / totalBill
            }

            // Distribute salary to each general
            for (general in nationGenerals) {
                val bill = getBill(general.dedication)
                general.funds += (bill * goldRatio).toInt()
                general.supplies += (bill * riceRatio).toInt()
            }
        }
    }

    private fun calcCityGoldIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int, multiplier: Double = 1.0): Double {
        if (city.commerceMax == 0) return 0.0
        val trustRatio = city.approval / 200.0 + 0.5
        var income = city.population.toDouble() * city.commerce / city.commerceMax * trustRatio / 30
        income *= 1 + city.security.toDouble() / city.securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officerCnt)
        if (isCapital) {
            income *= 1 + 1.0 / 3 / nationLevel
        }
        return income * multiplier
    }

    private fun calcCityRiceIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int, multiplier: Double = 1.0): Double {
        if (city.productionMax == 0) return 0.0
        val trustRatio = city.approval / 200.0 + 0.5
        var income = city.population.toDouble() * city.production / city.productionMax * trustRatio / 30
        income *= 1 + city.security.toDouble() / city.securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officerCnt)
        if (isCapital) {
            income *= 1 + 1.0 / 3 / nationLevel
        }
        return income * multiplier
    }

    private fun calcCityWallIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int, multiplier: Double = 1.0): Double {
        if (city.fortressMax == 0) return 0.0
        var income = city.orbitalDefense.toDouble() * city.fortress / city.fortressMax / 3
        income *= 1 + city.security.toDouble() / city.securityMax.coerceAtLeast(1) / 10
        income *= 1.05.pow(officerCnt)
        if (isCapital) {
            income *= 1 + 1.0 / 3 / nationLevel
        }
        return income * multiplier
    }

    private fun getDedLevel(dedication: Int): Int {
        return ceil(sqrt(dedication.toDouble()) / 10).toInt().coerceIn(0, MAX_DED_LEVEL)
    }

    private fun getBill(dedication: Int): Int {
        return getDedLevel(dedication) * 200 + 400
    }

    private fun processWarIncome(nations: List<Faction>, cities: List<Planet>) {
        val nationMap = nations.associateBy { it.id }

        for (city in cities) {
            if (city.dead > 0) {
                val nation = nationMap[city.factionId] ?: continue
                nation.funds += (city.dead / 10)
                val popGain = (city.dead * 0.2).toInt().coerceAtMost((city.populationMax - city.population).coerceAtLeast(0))
                city.population += popGain
                city.dead = 0
            }
        }
    }

    // ── Phase A2: processSemiAnnual (legacy formula) ──

    private fun processSemiAnnual(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>) {
        // 1. Reset dead and apply 0.99 decay to ALL cities unconditionally
        // Legacy ProcessSemiAnnual.php:75-82: decay is applied to ALL cities first,
        // then popIncrease() applies growth ONLY to supplied nation cities.
        // Neutral cities additionally get trust reset to 50.
        val citiesByNation = cities.filter { it.factionId != 0L }.groupBy { it.factionId }

        for (city in cities) {
            city.dead = 0
            // 0.99 decay on ALL cities (legacy parity: ProcessSemiAnnual.php:75-82)
            city.production = (city.production * 0.99).toInt()
            city.commerce = (city.commerce * 0.99).toInt()
            city.security = (city.security * 0.99).toInt()
            city.orbitalDefense = (city.orbitalDefense * 0.99).toInt()
            city.fortress = (city.fortress * 0.99).toInt()
            if (city.factionId == 0L) {
                // Neutral city: trust reset to 50 (legacy func_time_event.php:43)
                city.approval = 50F
            }
        }

        // 2. Population and infrastructure growth per nation (supplied cities only)
        val nationMap = nations.associateBy { it.id }

        for ((nationId, nationCities) in citiesByNation) {
            val nation = nationMap[nationId] ?: continue
            val taxRate = nation.conscriptionRateTmp.toDouble()

            val popRatio = (30 - taxRate) / 200
            val genericRatio = (20 - taxRate) / 200

            // Nation type pop growth modifier
            val nationTypeMod = NationTypeModifiers.get(nation.factionType)
            var incomeCtx = IncomeContext()
            if (nationTypeMod != null) {
                incomeCtx = nationTypeMod.onCalcIncome(incomeCtx)
            }

            for (city in nationCities) {
                if (city.supplyState.toInt() != 1) continue

                // Population growth (with nation type modifier)
                val rawPopGrowth = if (popRatio >= 0) {
                    BASE_POP_INCREASE + (city.population * (1 + popRatio * (1 + city.security.toDouble() / city.securityMax.coerceAtLeast(1) / 10))).toInt()
                } else {
                    BASE_POP_INCREASE + (city.population * (1 + popRatio * (1 - city.security.toDouble() / city.securityMax.coerceAtLeast(1) / 10))).toInt()
                }
                val popDelta = rawPopGrowth - city.population
                val popGrowth = city.population + (popDelta * incomeCtx.popGrowthMultiplier).toInt()
                city.population = popGrowth.coerceAtMost(city.populationMax)

                // Infrastructure growth
                city.production = (city.production * (1 + genericRatio)).toInt().coerceAtMost(city.productionMax)
                city.commerce = (city.commerce * (1 + genericRatio)).toInt().coerceAtMost(city.commerceMax)
                city.security = (city.security * (1 + genericRatio)).toInt().coerceAtMost(city.securityMax)
                city.orbitalDefense = (city.orbitalDefense * (1 + genericRatio)).toInt().coerceAtMost(city.orbitalDefenseMax)
                city.fortress = (city.fortress * (1 + genericRatio)).toInt().coerceAtMost(city.fortressMax)

                // Trust adjustment
                city.approval = (city.approval + (20 - taxRate).toFloat()).coerceIn(0F, 100F)
            }
        }

        // 4. General maintenance: resource decay
        for (general in generals) {
            // Gold: > 10000 → 3%, > 1000 → 1%
            if (general.funds > 10000) {
                general.funds = (general.funds * 0.97).toInt()
            } else if (general.funds > 1000) {
                general.funds = (general.funds * 0.99).toInt()
            }
            // Rice: > 10000 → 3%, > 1000 → 1%
            if (general.supplies > 10000) {
                general.supplies = (general.supplies * 0.97).toInt()
            } else if (general.supplies > 1000) {
                general.supplies = (general.supplies * 0.99).toInt()
            }
        }

        // 5. Nation maintenance: resource decay
        for (nation in nations) {
            // Gold: > 100000 → 5%, > 10000 → 3%, > 1000 → 1%
            nation.funds = when {
                nation.funds > 100000 -> (nation.funds * 0.95).toInt()
                nation.funds > 10000 -> (nation.funds * 0.97).toInt()
                nation.funds > 1000 -> (nation.funds * 0.99).toInt()
                else -> nation.funds
            }
            // Rice
            nation.supplies = when {
                nation.supplies > 100000 -> (nation.supplies * 0.95).toInt()
                nation.supplies > 10000 -> (nation.supplies * 0.97).toInt()
                nation.supplies > 1000 -> (nation.supplies * 0.99).toInt()
                else -> nation.supplies
            }
        }
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
     */
    @Transactional
    fun processIncomeEvent(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }
        processIncome(world, nations, cities, generals)
        saveCities(ports, cities)
        saveNations(ports, nations)
        saveGenerals(ports, generals)
    }

    /**
     * Public entry point for event-driven semi-annual processing.
     */
    @Transactional
    fun processSemiAnnualEvent(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }
        processSemiAnnual(world, nations, cities, generals)
        saveCities(ports, cities)
        saveNations(ports, nations)
        saveGenerals(ports, generals)
    }

    /**
     * Public entry point for event-driven nation level update.
     */
    @Transactional
    fun updateNationLevelEvent(world: SessionState) {
        val ports = worldPortFactory.create(world.id.toLong())
        val cities = ports.allPlanets().map { it.toEntity() }
        val nations = ports.allFactions().map { it.toEntity() }
        val generals = ports.allOfficers().map { it.toEntity() }
        updateNationLevel(world, nations, cities, generals)
        saveNations(ports, nations)
    }

    // ── Phase A2: updateCitySupply (+ trust < 30 neutral conversion) ──

    private fun updateCitySupply(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>) {
        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val citiesByNation = cities.groupBy { it.factionId }
        val cityById = cities.associateBy { it.id }
        val generalsByCity = generals.groupBy { it.planetId }

        // Build map city ID <-> DB city ID lookups
        val dbToMapId = cities.associate { it.id to it.mapCityId }
        val mapToDbId = cities.associate { it.mapCityId to it.id }

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
                        log.warn("Failed to get adjacent cities for mapCityId={}: {}", currentMapId, e.message)
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
                            if (general.officerCity == city.id.toInt()) {
                                general.officerLevel = 1
                                general.officerCity = 0
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

    // ── Phase A2: updateNationLevel (legacy thresholds + rewards) ──

    private fun updateNationLevel(world: SessionState, nations: List<Faction>, cities: List<Planet>, generals: List<Officer>) {
        val citiesByNation = cities.groupBy { it.factionId }
        val generalsByNation = generals
            .filter { it.npcState.toInt() != 5 && it.npcState != SovereignConstants.NPC_STATE_EMPEROR }
            .groupBy { it.factionId }

        for (nation in nations) {
            val nationCities = citiesByNation[nation.id] ?: continue
            val highCities = nationCities.count { it.level >= 4 }

            // Find highest level matching threshold
            var newLevel = 0
            for (level in NATION_LEVEL_THRESHOLDS.indices) {
                if (highCities >= NATION_LEVEL_THRESHOLDS[level]) {
                    newLevel = level
                }
            }

            if (newLevel > nation.factionRank.toInt()) {
                val oldLevel = nation.factionRank.toInt()
                nation.factionRank = newLevel.coerceIn(0, 9).toShort()
                nation.funds += newLevel * 1000
                nation.supplies += newLevel * 1000

                // Legacy parity: log 【작위】 history for nation level-up
                val nationName = nation.name
                val lordName = generalsByNation[nation.id]
                    ?.firstOrNull { it.officerLevel.toInt() == 20 }?.name ?: "군주"
                val oldLevelText = getNationLevelName(oldLevel)
                val newLevelText = getNationLevelName(newLevel)

                val globalMsg = when (newLevel) {
                    9 -> "【작위】 ${nationName} $oldLevelText ${lordName}이(가) ${newLevelText}로 옹립되었습니다."
                    8 -> "【작위】 ${nationName}의 ${lordName}이(가) ${newLevelText}로 책봉되었습니다."
                    in 3..7 -> "【작위】 ${nationName}의 ${lordName}이(가) ${newLevelText}로 임명되었습니다."
                    2 -> "【작위】 ${lordName}이(가) 독립하여 ${nationName}(이)라는 ${newLevelText}로 나섰습니다."
                    else -> "【작위】 ${nationName}의 ${lordName}이(가) ${newLevelText}로 승격되었습니다."
                }
                val nationMsg = when (newLevel) {
                    9 -> "${nationName} $oldLevelText ${lordName}이(가) ${newLevelText}로 옹립"
                    8 -> "${nationName}의 ${lordName}이(가) ${newLevelText}로 책봉"
                    in 3..7 -> "${nationName}의 ${lordName}이(가) ${newLevelText}로 임명됨"
                    2 -> "${lordName}이(가) 독립하여 ${nationName}(이)라는 ${newLevelText}로 나서다"
                    else -> "${nationName}의 ${lordName}이(가) ${newLevelText}로 승격됨"
                }

                historyService.logWorldHistory(
                    worldId = world.id.toLong(),
                    message = globalMsg,
                    year = world.currentYear.toInt(),
                    month = world.currentMonth.toInt(),
                )
                historyService.logNationHistory(
                    worldId = world.id.toLong(),
                    nationId = nation.id,
                    message = nationMsg,
                    year = world.currentYear.toInt(),
                    month = world.currentMonth.toInt(),
                )

                log.info("Nation {} leveled up to {} ({}) (reward: gold={}, rice={})",
                    nation.name, newLevel, newLevelText, newLevel * 1000, newLevel * 1000)

                for (general in generalsByNation[nation.id].orEmpty()) {
                    inheritanceService.accruePoints(general, "unifier", 1)
                }
            }
        }
    }

    /**
     * 연초 통계: 국가 국력(power)·장수수(gennum) 갱신 및 연감 기록.
     * legacy checkStatistic() + postUpdateMonthly() 패러티.
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

            // 도시파워: sum(pop) * sum(pop+agri+comm+secu+wall+def) / sum(popMax+agriMax+commMax+secuMax+wallMax+defMax) / 100
            val suppliedCities = nationCities.filter { it.supplyState.toInt() == 1 }
            val cityPower = if (suppliedCities.isNotEmpty()) {
                val popSum = suppliedCities.sumOf { it.population.toLong() }
                val valueSum = suppliedCities.sumOf { (it.population + it.production + it.commerce + it.security + it.fortress + it.orbitalDefense).toLong() }
                val maxSum = suppliedCities.sumOf { (it.populationMax + it.productionMax + it.commerceMax + it.securityMax + it.fortressMax + it.orbitalDefenseMax).toLong() }
                if (maxSum > 0) (popSum * valueSum).toDouble() / maxSum / 100.0 else 0.0
            } else 0.0

            // 장수능력: (npcMul * leaderCore * 2 + (sqrt(intel*str)*2 + lead/2)/2) 합
            val statPower = nationGenerals.sumOf { g ->
                val lead = g.leadership.toDouble()
                val str = g.command.toDouble()
                val intel = g.intelligence.toDouble()
                val npcMul = if (g.npcState < 2) 1.2 else 1.0
                val leaderCore = if (lead >= 40) lead else 0.0
                npcMul * leaderCore * 2 + (kotlin.math.sqrt(intel * str) * 2 + lead / 2) / 2
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
                DisasterOrBoomEntry(4, "【재난】", "역병이 발생하여 도시가 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "지진으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "추위가 풀리지 않아 얼어죽는 백성들이 늘어나고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "황건적이 출현해 도시를 습격하고 있습니다."),
            ),
            4 to listOf(
                DisasterOrBoomEntry(7, "【재난】", "홍수로 인해 피해가 급증하고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "지진으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(6, "【재난】", "태풍으로 인해 피해가 속출하고 있습니다."),
            ),
            7 to listOf(
                DisasterOrBoomEntry(8, "【재난】", "메뚜기 떼가 발생하여 도시가 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "지진으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(8, "【재난】", "흉년이 들어 굶어죽는 백성들이 늘어나고 있습니다."),
            ),
            10 to listOf(
                DisasterOrBoomEntry(3, "【재난】", "혹한으로 도시가 황폐해지고 있습니다."),
                DisasterOrBoomEntry(5, "【재난】", "지진으로 피해가 속출하고 있습니다."),
                DisasterOrBoomEntry(3, "【재난】", "눈이 많이 쌓여 도시가 황폐해지고 있습니다."),
                DisasterOrBoomEntry(9, "【재난】", "황건적이 출현해 도시를 습격하고 있습니다."),
            ),
        )
        val boomEntries = mapOf(
            4 to DisasterOrBoomEntry(2, "【호황】", "호황으로 도시가 번창하고 있습니다."),
            7 to DisasterOrBoomEntry(1, "【풍작】", "풍작으로 도시가 번창하고 있습니다."),
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
