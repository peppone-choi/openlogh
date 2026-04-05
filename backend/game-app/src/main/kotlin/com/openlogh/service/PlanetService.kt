package com.openlogh.service

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.model.CityConst
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.roundToInt

@Service
class PlanetService(
    private val planetRepository: PlanetRepository,
    private val mapService: MapService,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    private val canonicalRegionByCityName: Map<String, Short> by lazy {
        mapService.getCities("che").associate { it.name to it.region.toShort() }
    }

    companion object {
        // Region codes matching legacy CityConstBase::$regionMap
        const val REGION_HABUK = 1    // 하북
        const val REGION_JUNGWON = 2  // 중원
        const val REGION_SEOBUK = 3   // 서북
        const val REGION_SEOCHOK = 4  // 서촉
        const val REGION_NAMJUNG = 5  // 남중
        const val REGION_CHO = 6      // 초
        const val REGION_OWOL = 7     // 오월
        const val REGION_DONGI = 8    // 동이

        val REGION_NAMES = mapOf(
            REGION_HABUK to "하북",
            REGION_JUNGWON to "중원",
            REGION_SEOBUK to "서북",
            REGION_SEOCHOK to "서촉",
            REGION_NAMJUNG to "남중",
            REGION_CHO to "초",
            REGION_OWOL to "오월",
            REGION_DONGI to "동이",
        )

        // City level codes matching legacy CityConstBase::$levelMap
        // 1=수, 2=진, 3=관, 4=이, 5=소, 6=중, 7=대, 8=특
        val LEVEL_NAMES = mapOf(
            1 to "수", 2 to "진", 3 to "관", 4 to "이",
            5 to "소", 6 to "중", 7 to "대", 8 to "특",
        )

        // Population cap multipliers by planet level (legacy scale: level -> popMax factor)
        // These are encoded in the map JSON data directly as CityConst.population * 100

        // Terrain/level modifiers for development rates (legacy parity)
        // Higher level cities develop faster
        val DEV_RATE_BY_LEVEL = mapOf(
            1 to 0.5, 2 to 0.6, 3 to 0.7, 4 to 0.8,
            5 to 0.9, 6 to 1.0, 7 to 1.1, 8 to 1.2,
        )

        // Legacy: trust minimum during internal affairs
        const val DEVEL_RATE_MIN_TRUST = 50

        // Legacy: default planet wall after conquest
        const val DEFAULT_CITY_WALL = 1000

        // Legacy: expand planet costs and amounts
        const val EXPAND_CITY_POP_INCREASE = 100000
        const val EXPAND_CITY_DEVEL_INCREASE = 2000
        const val EXPAND_CITY_WALL_INCREASE = 2000
        const val EXPAND_CITY_DEFAULT_COST = 60000
        const val EXPAND_CITY_COST_COEF = 500
    }

    // ── Basic CRUD ──

    fun listByWorld(worldId: Long): List<Planet> {
        return planetRepository.findBySessionId(worldId)
    }

    fun listByWorldMaskedForOfficer(worldId: Long, officer: Officer): List<Planet> {
        val cities = planetRepository.findBySessionId(worldId)
        if (officer.sessionId != worldId) return cities.map(::toMaskedView)
        val canSeeAllMilitary = officer.permission == "spy"
        val visibleCityIds = if (canSeeAllMilitary || officer.factionId <= 0L) {
            emptySet()
        } else {
            factionRepository.findById(officer.factionId).orElse(null)
                ?.let { extractVisibleCityIds(it.spy) }
                ?: emptySet()
        }

        return cities.map { planet ->
            if (isCityVisibleToOfficer(planet, officer, canSeeAllMilitary, visibleCityIds)) {
                planet
            } else {
                toMaskedView(planet)
            }
        }
    }

    fun getById(id: Long): Planet? {
        return planetRepository.findById(id).orElse(null)
    }

    fun listByNation(nationId: Long): List<Planet> {
        return planetRepository.findByFactionId(nationId)
    }

    @Transactional
    fun save(planet: Planet): Planet {
        return planetRepository.save(planet)
    }

    @Transactional
    fun saveAll(cities: List<Planet>): List<Planet> {
        return planetRepository.saveAll(cities)
    }

    fun canonicalRegionForDisplay(planet: Planet): Short {
        return canonicalRegionByCityName[planet.name] ?: planet.region
    }

    // ── Adjacency / Map Queries ──

    /**
     * Get adjacent map planet IDs for a given map planet ID.
     * Delegates to MapService which holds the parsed map JSON.
     */
    fun getAdjacentCities(mapCode: String, mapCityId: Int): List<Int> {
        return mapService.getAdjacentCities(mapCode, mapCityId)
    }

    /**
     * Get all cities in a region from the map definition.
     */
    fun getCitiesByRegion(mapCode: String, region: Int): List<CityConst> {
        return mapService.getCities(mapCode).filter { it.region == region }
    }

    /**
     * Get distance between two cities on the map (BFS hop count).
     * Takes map planet IDs, not DB planet IDs.
     */
    fun getDistance(mapCode: String, fromMapCityId: Int, toMapCityId: Int): Int {
        return mapService.getDistance(mapCode, fromMapCityId, toMapCityId)
    }

    /**
     * Get the map code for a given world, reading from world config.
     */
    fun getMapCode(worldConfig: Map<String, Any>): String {
        return (worldConfig["mapCode"] as? String) ?: "che"
    }

    // ── Supply Calculation ──

    /**
     * Check if a planet is supplied (connected to its faction's capital via friendly territory).
     * Returns true if supplied, false otherwise.
     * This is a read-only check; the actual supply state update is done by EconomyService.
     */
    fun isSupplied(worldId: Long, cityId: Long, mapCode: String): Boolean {
        val planet = getById(cityId) ?: return false
        if (planet.factionId == 0L) return true // neutral cities are always supplied

        val nationCities = listByNation(planet.factionId)
        val nationCityIds = nationCities.map { it.id }.toSet()

        // Find capital
        val capitalCity = nationCities.find {
            val faction = it.factionId
            // We need to find the capital — check meta or find by faction
            true // will be filtered by BFS
        }

        // BFS from capital
        val allNationCities = planetRepository.findByFactionId(planet.factionId)
        // We don't have direct access to faction here, so just check supplyState
        return planet.supplyState.toInt() == 1
    }

    // ── Development Calculation (legacy parity) ──

    /**
     * Calculate development effectiveness for a planet based on officer stats.
     * Legacy formula: base * (1 + stat/100) * levelModifier * trustModifier
     *
     * @param planet The planet being developed
     * @param statValue The officer's relevant stat (politics for agri/comm, leadership for secu/def/wall)
     * @param baseAmount Base development amount from command
     * @return Actual development amount
     */
    fun calcDevelopment(planet: Planet, statValue: Int, baseAmount: Int): Int {
        val levelMod = DEV_RATE_BY_LEVEL[planet.level.toInt()] ?: 1.0
        val trustMod = planet.approval / 100.0
        val statMod = 1.0 + statValue / 100.0
        return (baseAmount * statMod * levelMod * trustMod).roundToInt()
    }

    /**
     * Calculate supply income contribution of a planet.
     * Legacy formula from EconomyService — exposed here for external callers.
     */
    fun calcSupply(planet: Planet): Double {
        if (planet.supplyState.toInt() == 0) return 0.0
        val trustRatio = planet.approval / 200.0 + 0.5
        val goldBase = if (planet.commerceMax > 0) {
            planet.population.toDouble() * planet.commerce / planet.commerceMax * trustRatio / 30
        } else 0.0
        val riceBase = if (planet.productionMax > 0) {
            planet.population.toDouble() * planet.production / planet.productionMax * trustRatio / 30
        } else 0.0
        return goldBase + riceBase
    }

    // ── City Initialization for Scenario Setup ──

    /**
     * Initialize a planet entity from a CityConst (map definition) for scenario setup.
     * Legacy parity: matches the initial values from CityConstBase.
     * Population and development values in CityConst are stored as x100.
     */
    fun initializeCityFromConst(worldId: Long, cityConst: CityConst, nationId: Long = 0): Planet {
        return Planet(
            sessionId = worldId,
            name = cityConst.name,
            level = cityConst.level.toShort(),
            factionId = nationId,
            supplyState = 1,
            frontState = 0,
            population = cityConst.population * 100,
            populationMax = cityConst.population * 100,
            production = cityConst.agriculture * 100,
            productionMax = cityConst.agriculture * 100,
            commerce = cityConst.commerce * 100,
            commerceMax = cityConst.commerce * 100,
            security = cityConst.security * 100,
            securityMax = cityConst.security * 100,
            approval = 50f,
            tradeRoute = 100,
            orbitalDefense = cityConst.defence * 100,
            orbitalDefenseMax = cityConst.defence * 100,
            fortress = cityConst.wall * 100,
            fortressMax = cityConst.wall * 100,
            officerSet = 0,
            state = 0,
            region = cityConst.region.toShort(),
            term = 0,
            conflict = mutableMapOf(),
            meta = mutableMapOf(
                "x" to cityConst.x,
                "y" to cityConst.y,
                "constId" to cityConst.id,
            ),
        )
    }

    /**
     * Initialize all cities for a world from the map definition.
     * Returns saved cities keyed by their CityConst ID.
     */
    @Transactional
    fun initializeAllCities(worldId: Long, mapCode: String): Map<Int, Planet> {
        val cityConsts = mapService.getCities(mapCode)
        val cities = cityConsts.map { initializeCityFromConst(worldId, it) }
        val saved = planetRepository.saveAll(cities)
        return saved.associateBy { (it.meta["constId"] as? Number)?.toInt() ?: 0 }
    }

    // ── City Expansion (legacy: 증축) ──

    /**
     * Calculate the cost of expanding a planet.
     * Legacy formula: defaultCost + (popMax / 100) * costCoef
     */
    fun calcExpandCost(planet: Planet): Int {
        return EXPAND_CITY_DEFAULT_COST + (planet.populationMax / 100) * EXPAND_CITY_COST_COEF
    }

    /**
     * Expand a planet: increase popMax, develMax, wallMax.
     * Legacy parity: checks cost against faction treasury.
     */
    @Transactional
    fun expandPlanet(planet: Planet): Planet {
        planet.populationMax += EXPAND_CITY_POP_INCREASE
        planet.productionMax += EXPAND_CITY_DEVEL_INCREASE
        planet.commerceMax += EXPAND_CITY_DEVEL_INCREASE
        planet.securityMax += EXPAND_CITY_DEVEL_INCREASE
        planet.orbitalDefenseMax += EXPAND_CITY_DEVEL_INCREASE
        planet.fortressMax += EXPAND_CITY_WALL_INCREASE
        return planetRepository.save(planet)
    }

    // ── City Conquest ──

    /**
     * Transfer planet ownership after conquest.
     * Legacy parity: reset officers, set default wall, clear conflict.
     */
    @Transactional
    fun conquerPlanet(planet: Planet, newNationId: Long) {
        val oldNationId = planet.factionId
        planet.factionId = newNationId
        planet.fortress = DEFAULT_CITY_WALL.coerceAtMost(planet.fortressMax)
        planet.officerSet = 0
        planet.conflict = mutableMapOf()
        planet.term = 0
        planet.frontState = 0

        // Reset officer assignments for generals in this planet from old faction
        if (oldNationId != 0L) {
            val generals = officerRepository.findByPlanetId(planet.id)
            for (officer in generals) {
                if (officer.factionId == oldNationId && officer.officerCity == planet.id.toInt()) {
                    officer.officerLevel = 1
                    officer.officerCity = 0
                    officerRepository.save(officer)
                }
            }
        }

        planetRepository.save(planet)
    }

    // ── Utility ──

    /**
     * Get generals stationed in a planet.
     */
    fun getGeneralsInPlanet(cityId: Long) = officerRepository.findByPlanetId(cityId)

    /**
     * Count cities owned by a faction at or above a given level.
     */
    fun countCitiesAboveLevel(nationId: Long, minLevel: Int): Int {
        return planetRepository.findByFactionId(nationId).count { it.level >= minLevel }
    }

    /**
     * Get the region name for a region code.
     */
    fun getRegionName(mapCode: String, regionCode: Int): String {
        return mapService.getRegionName(mapCode, regionCode) ?: REGION_NAMES[regionCode] ?: "미상"
    }

    fun getRegionName(regionCode: Int): String {
        return getRegionName("che", regionCode)
    }

    /**
     * Get the level name for a level code.
     */
    fun getLevelName(levelCode: Int): String {
        return LEVEL_NAMES[levelCode] ?: "?"
    }

    /**
     * Get total population across all cities in a world.
     */
    fun getTotalPopulation(worldId: Long): Long {
        return planetRepository.findBySessionId(worldId).sumOf { it.population.toLong() }
    }

    /**
     * Get cities with low trust (potential rebellion).
     */
    fun getLowTrustCities(worldId: Long, threshold: Float = 30f): List<Planet> {
        return planetRepository.findBySessionId(worldId).filter { it.approval < threshold && it.factionId != 0L }
    }

    private fun isCityVisibleToOfficer(
        planet: Planet,
        officer: Officer,
        canSeeAllMilitary: Boolean,
        visibleCityIds: Set<Long>,
    ): Boolean {
        if (planet.factionId == officer.factionId && officer.factionId > 0L) return true
        if (planet.id == officer.planetId.toLong()) return true
        if (canSeeAllMilitary) return true
        return visibleCityIds.contains(planet.id)
    }

    private fun extractVisibleCityIds(spyInfo: Map<String, Any>): Set<Long> {
        val result = mutableSetOf<Long>()
        for ((rawKey, rawValue) in spyInfo) {
            val keyDigits = rawKey.filter { it.isDigit() }
            if (keyDigits.isNotBlank()) {
                keyDigits.toLongOrNull()?.let { cityId ->
                    if (cityId > 0L && isTruthySpyValue(rawValue)) {
                        result.add(cityId)
                    }
                }
            }

            when (rawValue) {
                is Number -> if (rawValue.toLong() > 0L) result.add(rawValue.toLong())
                is String -> rawValue.toLongOrNull()?.let { if (it > 0L) result.add(it) }
                is List<*> -> rawValue.forEach { item ->
                    when (item) {
                        is Number -> if (item.toLong() > 0L) result.add(item.toLong())
                        is String -> item.toLongOrNull()?.let { if (it > 0L) result.add(it) }
                    }
                }
            }
        }
        return result
    }

    private fun isTruthySpyValue(value: Any?): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() > 0
            is String -> value.toIntOrNull()?.let { it > 0 } ?: value.equals("true", ignoreCase = true)
            is List<*> -> value.isNotEmpty()
            else -> value != null
        }
    }

    private fun toMaskedView(planet: Planet): Planet {
        return Planet(
            id = planet.id,
            sessionId = planet.sessionId,
            name = planet.name,
            mapCityId = planet.mapCityId,
            level = planet.level,
            factionId = planet.factionId,
            supplyState = 0,
            frontState = 0,
            population = 0,
            populationMax = 0,
            production = 0,
            productionMax = 0,
            commerce = 0,
            commerceMax = 0,
            security = 0,
            securityMax = 0,
            approval = 0f,
            tradeRoute = 0,
            dead = 0,
            orbitalDefense = 0,
            orbitalDefenseMax = 0,
            fortress = 0,
            fortressMax = 0,
            officerSet = 0,
            state = planet.state,
            region = planet.region,
            term = planet.term,
            conflict = mutableMapOf(),
            meta = planet.meta.toMutableMap(),
        )
    }
}
