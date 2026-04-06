package com.openlogh.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openlogh.engine.SovereignConstants
import com.openlogh.entity.*
import com.openlogh.model.ScenarioData
import com.openlogh.model.ScenarioInfo
import com.openlogh.repository.*
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.random.Random

@Service
class ScenarioService(
    private val objectMapper: ObjectMapper,
    @Value("\${game.commit-sha:local}") private val defaultCommitSha: String,
    @Value("\${game.version:dev}") private val defaultGameVersion: String,
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val eventRepository: EventRepository,
    private val mapService: MapService,
    private val historyService: HistoryService,
    private val selectPoolRepository: com.openlogh.repository.SelectPoolRepository,
    private val entityManager: EntityManager,
    private val starSystemService: StarSystemService,
) {
    private val scenarios = mutableMapOf<String, ScenarioData>()
    @Volatile
    private var scenariosLoaded = false
    private val log = LoggerFactory.getLogger(javaClass)

    /** Name→picture lookup from the reference scenario (scenario_1010) for fallback when picture is missing. */
    private val referencePictureByName: Map<String, String> by lazy {
        loadAllScenarios()
        val refScenario = scenarios["1010"] ?: return@lazy emptyMap()
        val map = mutableMapOf<String, String>()
        for (section in listOf(refScenario.general, refScenario.generalEx, refScenario.generalNeutral)) {
            for (row in section) {
                val name = (row.getOrNull(1) as? String) ?: continue
                val pic = row.getOrNull(2)
                if (pic != null && pic.toString().isNotEmpty() && pic.toString() != "-1" && pic.toString() != "null") {
                    map.putIfAbsent(name, pic.toString())
                }
            }
        }
        map
    }

    /** Legacy CityConstBase::$buildInit — level-based initial values for new cities. */
    private data class CityInit(val pop: Int, val agri: Int, val comm: Int, val secu: Int, val def: Int, val wall: Int)

    private val CITY_LEVEL_INIT = mapOf(
        1 to CityInit(5000, 100, 100, 100, 500, 500),
        2 to CityInit(5000, 100, 100, 100, 500, 500),
        3 to CityInit(10000, 100, 100, 100, 1000, 1000),
        4 to CityInit(50000, 1000, 1000, 1000, 1000, 1000),
        5 to CityInit(100000, 1000, 1000, 1000, 2000, 2000),
        6 to CityInit(100000, 1000, 1000, 1000, 3000, 3000),
        7 to CityInit(150000, 1000, 1000, 1000, 4000, 4000),
        8 to CityInit(150000, 1000, 1000, 1000, 5000, 5000),
    )
    private val DEFAULT_CITY_INIT = CityInit(50000, 1000, 1000, 1000, 1000, 1000)
    fun listScenarios(): List<ScenarioInfo> {
        loadAllScenarios()
        return scenarios.map { (code, data) ->
            ScenarioInfo(code, data.title, data.startYear)
        }.sortedBy { it.code }
    }

    fun getScenario(code: String): ScenarioData {
        loadAllScenarios()
        return scenarios[code] ?: throw IllegalArgumentException("Scenario not found: $code")
    }

    @Transactional
    fun initializeWorld(
        scenarioCode: String,
        tickSeconds: Int = 300,
        commitSha: String? = null,
        gameVersion: String? = null,
        extendEnabled: Boolean? = null,
        npcMode: Int? = null,
        fiction: Int? = null,
        maxGeneral: Int? = null,
        maxNation: Int? = null,
        joinMode: String? = null,
        blockGeneralCreate: Int? = null,
        showImgLevel: Int? = null,
        autorunUser: List<String>? = null,
        startTime: String? = null,
        opentime: String? = null,
    ): SessionState {
        val scenario = getScenario(scenarioCode)
        val resolvedCommitSha = commitSha?.takeIf { it.isNotBlank() } ?: defaultCommitSha
        val resolvedGameVersion = gameVersion?.takeIf { it.isNotBlank() } ?: defaultGameVersion

        val mapName = scenario.map?.mapName ?: "che"
        val extendedGeneralEnabled = extendEnabled ?: readExtendedGeneralFlag(scenario.const)
        val hiddenSeed = java.util.UUID.randomUUID().toString()
        val initRandom = Random(hiddenSeed.hashCode().toLong())
        val world = sessionStateRepository.save(
            SessionState(
                scenarioCode = scenarioCode,
                commitSha = resolvedCommitSha,
                gameVersion = resolvedGameVersion,
                currentYear = scenario.startYear.toShort(),
                currentMonth = 1,
                tickSeconds = tickSeconds,
                config = buildInitialConfig(
                    scenario = scenario,
                    mapName = mapName,
                    hiddenSeed = hiddenSeed,
                    extendedGeneralEnabled = extendedGeneralEnabled,
                    tickSeconds = tickSeconds,
                    npcMode = npcMode,
                    fiction = fiction,
                    maxGeneral = maxGeneral,
                    maxNation = maxNation,
                    joinMode = joinMode,
                    blockGeneralCreate = blockGeneralCreate,
                    showImgLevel = showImgLevel,
                    autorunUser = autorunUser,
                    startTime = startTime,
                    opentime = opentime,
                ),
            )
        )
        val worldId = world.id.toLong()

        // 1. Create cities from map data
        val mapCities = try { mapService.getCities(mapName) } catch (_: Exception) { mapService.getCities("che") }
        val cityEntities = mapCities.map { mc ->
            val init = CITY_LEVEL_INIT[mc.level] ?: DEFAULT_CITY_INIT
            Planet(
                sessionId = worldId,
                name = mc.name,
                mapPlanetId = mc.id,
                level = mc.level.toShort(),
                population = init.pop,
                populationMax = mc.population,
                production = init.agri,
                productionMax = mc.agriculture,
                commerce = init.comm,
                commerceMax = mc.commerce,
                security = init.secu,
                securityMax = mc.security,
                approval = 50f,
                orbitalDefense = init.def,
                orbitalDefenseMax = mc.defence,
                fortress = init.wall,
                fortressMax = mc.wall,
                region = mc.region.toShort(),
            )
        }
        val savedCities = planetRepository.saveAll(cityEntities)
        val cityNameToId = savedCities.associate { it.name to it.id }
        val allCityIds = cityNameToId.values.toList()

        // 2. Create nations and assign cities
        val nationEntities = scenario.nation.map { nationRow ->
            val faction = parseFaction(nationRow, worldId)
            val nationCityNames = readStringList(nationRow.lastOrNull { it is List<*> })
            val nationCities = nationCityNames.mapNotNull { cityNameToId[it] }
            if (nationCities.isNotEmpty()) {
                faction.capitalPlanetId = nationCities.first()
            }
            faction
        }
        val savedNations = factionRepository.saveAll(nationEntities)
        val nationIdxToDbId = mutableMapOf<Int, Long>()
        val nationCityIds = mutableMapOf<Long, MutableList<Long>>()
        val citiesToUpdate = mutableListOf<Planet>()
        for ((idx, saved) in savedNations.withIndex()) {
            val nationIdx = idx + 1
            nationIdxToDbId[nationIdx] = saved.id
            val nationCityNames = readStringList(scenario.nation[idx].lastOrNull { it is List<*> })
            val nationCities = nationCityNames.mapNotNull { cityNameToId[it] }
            nationCityIds[saved.id] = nationCities.toMutableList()
            for (cid in nationCities) {
                val planet = savedCities.find { it.id == cid }
                if (planet != null) {
                    planet.factionId = saved.id
                    citiesToUpdate.add(planet)
                }
            }
        }
        if (citiesToUpdate.isNotEmpty()) planetRepository.saveAll(citiesToUpdate)

        // 2.5 Initialize star systems (LOGH maps only)
        if (mapName == "logh") {
            val regions = mapService.getRegions(mapName)
            val factionMap = mutableMapOf<String, Long>()
            for (nation in savedNations) {
                // Match faction name to region name for mapping
                for ((_, regionName) in regions) {
                    if (nation.name.contains(regionName) || regionName.contains(nation.name)) {
                        factionMap[regionName] = nation.id
                    }
                }
            }
            val starSystemMap = starSystemService.initializeStarSystems(worldId, mapName, factionMap)

            // Update planets with star system IDs by matching mapPlanetId to mapStarId
            val planetsToLink = mutableListOf<Planet>()
            for (planet in savedCities) {
                val starSystem = starSystemMap[planet.mapPlanetId]
                if (starSystem != null) {
                    planet.starSystemId = starSystem.id
                    planetsToLink.add(planet)
                }
            }
            if (planetsToLink.isNotEmpty()) planetRepository.saveAll(planetsToLink)
            log.info("[World {}] Initialized {} star systems with {} routes", worldId, starSystemMap.size,
                starSystemService.getRoutes(worldId).size)
        }

        val generalsToSave = mutableListOf<Officer>()
        var delayedNpcCount = 0

        fun collectGenerals(rows: List<List<Any?>>, defaultNpcState: Short) {
            for (generalRow in rows) {
                val officer = parseOfficer(
                    row = generalRow,
                    worldId = worldId,
                    nationIdxToDbId = nationIdxToDbId,
                    nationCityIds = nationCityIds,
                    cityNameToId = cityNameToId,
                    allCityIds = allCityIds,
                    rng = initRandom,
                    startYear = scenario.startYear,
                    defaultNpcState = defaultNpcState,
                )
                if (shouldSpawnScenarioOfficer(officer, scenario.startYear)) {
                    generalsToSave.add(officer)
                } else {
                    delayedNpcCount++
                }
            }
        }

        collectGenerals(scenario.general, 2)
        if (extendedGeneralEnabled) collectGenerals(scenario.generalEx, 2)
        collectGenerals(scenario.generalNeutral, 6)
        officerRepository.saveAll(generalsToSave)

        if (delayedNpcCount > 0) {
            log.info(
                "[World {}] Delayed {} underage scenario NPC(s) for future yearly spawn",
                worldId,
                delayedNpcCount,
            )
        }

        val allGenerals = officerRepository.findBySessionId(worldId)
        val nationsToUpdate = mutableListOf<Faction>()
        for ((_, nationDbId) in nationIdxToDbId) {
            val ruler = allGenerals
                .filter { it.factionId == nationDbId && it.officerLevel >= 20 }
                .maxByOrNull { it.officerLevel }
            if (ruler != null) {
                val faction = savedNations.find { it.id == nationDbId }
                if (faction != null) {
                    faction.chiefOfficerId = ruler.id
                    nationsToUpdate.add(faction)
                }
            }
        }
        if (nationsToUpdate.isNotEmpty()) factionRepository.saveAll(nationsToUpdate)

        // 5. Create diplomacy
        val diplomacies = buildScenarioDiplomacies(scenario.diplomacy, nationIdxToDbId, worldId)
        if (diplomacies.isNotEmpty()) diplomacyRepository.saveAll(diplomacies)

        applyScenarioEmperorSettings(
            scenario = scenario,
            world = world,
            nationIdxToDbId = nationIdxToDbId,
            nations = savedNations,
            generals = allGenerals,
        )

        seedScenarioHistory(worldId, scenario.history, scenario.startYear, 1)
        seedGeneralPool(worldId)

        if (scenario.events.isNotEmpty()) {
            val events = convertLegacyEvents(scenario.events, worldId)
            if (events.isNotEmpty()) {
                eventRepository.saveAll(events)
                log.info("[World {}] Loaded {} scenario events", worldId, events.size)
            }
        }

        return world
    }

    @Transactional
    fun reinitializeWorld(
        existingWorld: SessionState,
        scenarioCode: String,
        tickSeconds: Int = existingWorld.tickSeconds,
        extendEnabled: Boolean? = null,
        npcMode: Int? = null,
        fiction: Int? = null,
        maxGeneral: Int? = null,
        maxNation: Int? = null,
        joinMode: String? = null,
        blockGeneralCreate: Int? = null,
        showImgLevel: Int? = null,
        autorunUser: List<String>? = null,
        startTime: String? = null,
        opentime: String? = null,
    ): SessionState {
        val worldId = existingWorld.id.toLong()
        log.info("[World {}] Reinitializing with scenario '{}'", worldId, scenarioCode)

        val tables = listOf(
            "general_turn", "nation_turn", "troop", "diplomacy",
            "bet_entry", "betting", "auction_bid", "auction",
            "board_comment", "board", "vote_cast", "vote",
            "tournament", "rank_data", "world_history", "yearbook_history",
            "general_access_log", "general_record", "traffic_snapshot",
            "records", "message", "event", "nation_flag",
            "officer", "planet", "faction",
        )
        entityManager.unwrap(Session::class.java).doWork { connection ->
            connection.createStatement().use { stmt ->
                for (table in tables) {
                    stmt.addBatch("DELETE FROM $table WHERE world_id = $worldId")
                }
                stmt.executeBatch()
            }
        }
        entityManager.flush()

        val scenario = getScenario(scenarioCode)
        val mapName = scenario.map?.mapName ?: "che"
        val extendedGeneralEnabled = extendEnabled ?: readExtendedGeneralFlag(scenario.const)
        val hiddenSeed = java.util.UUID.randomUUID().toString()
        val initRandom = Random(hiddenSeed.hashCode().toLong())

        existingWorld.scenarioCode = scenarioCode
        existingWorld.currentYear = scenario.startYear.toShort()
        existingWorld.currentMonth = 1
        existingWorld.tickSeconds = tickSeconds
        existingWorld.config = buildInitialConfig(
            scenario = scenario,
            mapName = mapName,
            hiddenSeed = hiddenSeed,
            extendedGeneralEnabled = extendedGeneralEnabled,
            tickSeconds = tickSeconds,
            npcMode = npcMode,
            fiction = fiction,
            maxGeneral = maxGeneral,
            maxNation = maxNation,
            joinMode = joinMode,
            blockGeneralCreate = blockGeneralCreate,
            showImgLevel = showImgLevel,
            autorunUser = autorunUser,
            startTime = startTime,
            opentime = opentime,
        )
        existingWorld.meta = mutableMapOf()
        existingWorld.updatedAt = OffsetDateTime.now()
        sessionStateRepository.save(existingWorld)

        val mapCities = try { mapService.getCities(mapName) } catch (_: Exception) { mapService.getCities("che") }
        val reinitCityEntities = mapCities.map { mc ->
            val init = CITY_LEVEL_INIT[mc.level] ?: DEFAULT_CITY_INIT
            Planet(
                sessionId = worldId,
                name = mc.name,
                mapPlanetId = mc.id,
                level = mc.level.toShort(),
                population = init.pop,
                populationMax = mc.population,
                production = init.agri,
                productionMax = mc.agriculture,
                commerce = init.comm,
                commerceMax = mc.commerce,
                security = init.secu,
                securityMax = mc.security,
                approval = 50f,
                orbitalDefense = init.def,
                orbitalDefenseMax = mc.defence,
                fortress = init.wall,
                fortressMax = mc.wall,
                region = mc.region.toShort(),
            )
        }
        val reinitSavedCities = planetRepository.saveAll(reinitCityEntities)
        val cityNameToId = reinitSavedCities.associate { it.name to it.id }
        val allCityIds = cityNameToId.values.toList()

        val nationEntities = scenario.nation.map { nationRow ->
            val faction = parseFaction(nationRow, worldId)
            val nationCityNames = readStringList(nationRow.getOrNull(8))
            val nationCities = nationCityNames.mapNotNull { cityNameToId[it] }
            if (nationCities.isNotEmpty()) {
                faction.capitalPlanetId = nationCities.first()
            }
            faction
        }
        val reinitSavedNations = factionRepository.saveAll(nationEntities)
        val nationIdxToDbId = mutableMapOf<Int, Long>()
        val nationCityIds = mutableMapOf<Long, MutableList<Long>>()
        val reinitCitiesToUpdate = mutableListOf<Planet>()
        for ((idx, saved) in reinitSavedNations.withIndex()) {
            val nationIdx = idx + 1
            nationIdxToDbId[nationIdx] = saved.id
            val nationCityNames = readStringList(scenario.nation[idx].getOrNull(8))
            val nationCities = nationCityNames.mapNotNull { cityNameToId[it] }
            nationCityIds[saved.id] = nationCities.toMutableList()
            for (cid in nationCities) {
                val planet = reinitSavedCities.find { it.id == cid }
                if (planet != null) {
                    planet.factionId = saved.id
                    reinitCitiesToUpdate.add(planet)
                }
            }
        }
        if (reinitCitiesToUpdate.isNotEmpty()) planetRepository.saveAll(reinitCitiesToUpdate)

        val generalsToSave = mutableListOf<Officer>()
        var delayedNpcCount = 0
        fun collectGenerals(rows: List<List<Any?>>, defaultNpcState: Short) {
            for (generalRow in rows) {
                val officer = parseOfficer(generalRow, worldId, nationIdxToDbId, nationCityIds, cityNameToId, allCityIds, initRandom, scenario.startYear, defaultNpcState)
                if (shouldSpawnScenarioOfficer(officer, scenario.startYear)) generalsToSave.add(officer) else delayedNpcCount++
            }
        }
        collectGenerals(scenario.general, 2)
        if (extendedGeneralEnabled) collectGenerals(scenario.generalEx, 2)
        collectGenerals(scenario.generalNeutral, 6)
        officerRepository.saveAll(generalsToSave)
        if (delayedNpcCount > 0) log.info("[World {}] Delayed {} underage scenario NPC(s) for future yearly spawn", worldId, delayedNpcCount)

        val allGenerals = officerRepository.findBySessionId(worldId)
        val reinitNationsToUpdate = mutableListOf<Faction>()
        for ((_, nationDbId) in nationIdxToDbId) {
            val ruler = allGenerals
                .filter { it.factionId == nationDbId && it.officerLevel >= 20 }
                .maxByOrNull { it.officerLevel }
            if (ruler != null) {
                val faction = reinitSavedNations.find { it.id == nationDbId }
                if (faction != null) {
                    faction.chiefOfficerId = ruler.id
                    reinitNationsToUpdate.add(faction)
                }
            }
        }
        if (reinitNationsToUpdate.isNotEmpty()) factionRepository.saveAll(reinitNationsToUpdate)

        val diplomacies = buildScenarioDiplomacies(scenario.diplomacy, nationIdxToDbId, worldId)
        if (diplomacies.isNotEmpty()) diplomacyRepository.saveAll(diplomacies)

        applyScenarioEmperorSettings(
            scenario = scenario,
            world = existingWorld,
            nationIdxToDbId = nationIdxToDbId,
            nations = reinitSavedNations,
            generals = allGenerals,
        )

        seedScenarioHistory(worldId, scenario.history, scenario.startYear, 1)

        if (scenario.events.isNotEmpty()) {
            val events = convertLegacyEvents(scenario.events, worldId)
            if (events.isNotEmpty()) {
                eventRepository.saveAll(events)
                log.info("[World {}] Loaded {} scenario events", worldId, events.size)
            }
        }

        log.info("[World {}] Reinitialized successfully (same ID preserved)", worldId)
        return existingWorld
    }

    private fun buildScenarioDiplomacies(
        diplomacyRows: List<List<Any>>,
        nationIdxToDbId: Map<Int, Long>,
        worldId: Long,
    ): List<Diplomacy> {
        return diplomacyRows.mapNotNull { diploRow ->
            if (diploRow.size < 4) return@mapNotNull null
            val srcIdx = (diploRow[0] as Number).toInt()
            val destIdx = (diploRow[1] as Number).toInt()
            val stateType = (diploRow[2] as Number).toInt()
            val term = (diploRow[3] as Number).toInt()
            val srcId = nationIdxToDbId[srcIdx] ?: return@mapNotNull null
            val destId = nationIdxToDbId[destIdx] ?: return@mapNotNull null
            Diplomacy(
                sessionId = worldId,
                srcFactionId = srcId,
                destFactionId = destId,
                stateCode = when (stateType) {
                    0 -> "전쟁"
                    1 -> "선전포고"
                    7 -> "불가침"
                    else -> "통상"
                },
                term = term.toShort(),
            )
        }
    }

    private fun applyScenarioEmperorSettings(
        scenario: ScenarioData,
        world: SessionState,
        nationIdxToDbId: Map<Int, Long>,
        nations: List<Faction>,
        generals: List<Officer>,
    ) {
        world.meta[SovereignConstants.WORLD_EMPEROR_SYSTEM] = true

        val emperorConfig = scenario.emperor ?: return
        val generalName = emperorConfig["generalName"]?.toString()?.takeIf { it.isNotBlank() } ?: return
        val nationIdx = parseInt(emperorConfig["nationIdx"]) ?: 0
        val emperorStatus = emperorConfig["status"]?.toString() ?: SovereignConstants.EMPEROR_ENTHRONED

        val isWandering = emperorStatus == SovereignConstants.EMPEROR_WANDERING || nationIdx == 0
        val nationId = if (isWandering) 0L else (nationIdxToDbId[nationIdx] ?: return)

        val emperorGeneral = generals.firstOrNull { it.name == generalName && it.factionId == nationId } ?: return
        emperorGeneral.meta[SovereignConstants.GENERAL_EMPEROR_STATUS] = emperorStatus
        emperorGeneral.npcState = SovereignConstants.NPC_STATE_EMPEROR

        world.meta[SovereignConstants.WORLD_EMPEROR_GENERAL_ID] = emperorGeneral.id

        if (!isWandering) {
            val emperorNation = nations.firstOrNull { it.id == nationId } ?: return
            emperorNation.meta[SovereignConstants.NATION_IMPERIAL_STATUS] = SovereignConstants.STATUS_EMPEROR
            emperorNation.meta[SovereignConstants.NATION_EMPEROR_TYPE] = SovereignConstants.TYPE_LEGITIMATE
            // Ensure emperor is placed at faction capital
            if (emperorNation.capitalPlanetId != null && emperorNation.capitalPlanetId!! > 0) {
                emperorGeneral.planetId = emperorNation.capitalPlanetId!!
            }
        }
    }

    private fun seedGeneralPool(worldId: Long) {
        try {
            val resource = org.springframework.core.io.ClassPathResource("data/general_pool.json")
            if (!resource.exists()) return
            val json = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readValue(
                resource.inputStream,
                Map::class.java,
            )
            val columns = (json["columns"] as? List<*>)?.map { it.toString() } ?: return
            val data = json["data"] as? List<*> ?: return

            selectPoolRepository.deleteBySessionId(worldId)

            val pools = data.mapNotNull { row ->
                if (row !is List<*> || row.size != columns.size) return@mapNotNull null
                val info = mutableMapOf<String, Any>()
                columns.forEachIndexed { idx, col -> row[idx]?.let { info[col] = it } }
                val uniqueName = info["generalName"]?.toString() ?: return@mapNotNull null
                info["uniqueName"] = uniqueName
                SelectPool(sessionId = worldId, uniqueName = uniqueName, info = info)
            }
            if (pools.isNotEmpty()) {
                selectPoolRepository.saveAll(pools)
                log.info("[World {}] Loaded {} officer pool entries", worldId, pools.size)
            }
        } catch (e: Exception) {
            log.warn("[World {}] Failed to load officer pool: {}", worldId, e.message)
        }
    }

    private val historyDateRegex = Regex("(\\d+)년\\s*(\\d+)월")
    private val historyDatePrefixRegex = Regex("^(<[^>]*>.*?</>)?\\s*\\d+년\\s*\\d+월\\s*:?\\s*")

    private fun seedScenarioHistory(worldId: Long, historyLines: List<String>, defaultYear: Int, defaultMonth: Int) {
        for (line in historyLines) {
            // Parse year/month from text, strip date prefix to avoid double display
            val match = historyDateRegex.find(line)
            val year = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: defaultYear
            val month = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: defaultMonth
            val cleaned = historyDatePrefixRegex.replace(line, "")
            historyService.logWorldHistory(worldId, cleaned.ifBlank { line }, year, month, scenarioInit = true)
        }
    }

    private val TWO_CHAR_SURNAMES = setOf(
        "공손", "사마", "제갈", "하후", "선우", "황보", "독고", "남궁", "동방", "황건",
    )

    private val SPECIAL_ABBR = mapOf(
        "한나라" to "한", "헌제" to "헌", "소제" to "소", "영제" to "영",
    )

    private fun deriveAbbreviation(factionName: String): String {
        SPECIAL_ABBR[factionName]?.let { return it }
        val twoChar = factionName.take(2)
        if (twoChar in TWO_CHAR_SURNAMES) return twoChar
        return factionName.take(1)
    }

    private fun parseFaction(row: List<Any>, worldId: Long): Faction {
        val typeRaw = row[6].toString()
        val typeCode = if (typeRaw.contains("_")) typeRaw else "che_$typeRaw"
        val description = row.getOrNull(4)?.toString() ?: ""
        val factionName = row[0] as String
        val explicitAbbr = row.getOrNull(9)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val specialKey = row.getOrNull(10)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        
        val meta = mutableMapOf<String, Any>(
            "scoutMsg" to description,
            "scout_msg" to description,
        )
        if (specialKey != null) {
            meta["officerRankKey"] = specialKey
        }
        
        return Faction(
            sessionId = worldId,
            name = factionName,
            abbreviation = explicitAbbr ?: deriveAbbreviation(factionName),
            color = row[1] as String,
            funds = (row[2] as Number).toInt(), supplies = (row[3] as Number).toInt(),
            taxRate = 100,
            conscriptionRate = 15,
            conscriptionRateTmp = 15,
            techLevel = (row[5] as Number).toFloat(),
            factionType = typeCode,
            factionRank = (row[7] as Number).toShort(),
            meta = meta,
        )
    }

    private fun parseOfficer(
        row: List<Any?>,
        worldId: Long,
        nationIdxToDbId: Map<Int, Long>,
        nationCityIds: Map<Long, List<Long>>,
        cityNameToId: Map<String, Long>,
        allCityIds: List<Long>,
        rng: Random,
        startYear: Int,
        defaultNpcState: Short,
    ): Officer {
        val affinity = (row[0] as? Number)?.toShort() ?: 0
        val name = row[1] as String
        val rawPic = row[2]?.toString()?.takeIf { it.isNotEmpty() && it != "null" && it != "-1" }
        val picture = rawPic ?: referencePictureByName[name] ?: ""

        val nationIdx = (row[3] as? Number)?.toInt() ?: 0
        val nationId = if (nationIdx > 0) nationIdxToDbId[nationIdx] ?: 0L else 0L

        val leadership = (row[5] as Number).toShort()
        val strength = (row[6] as Number).toShort()
        val intel = (row[7] as Number).toShort()

        val hasFiveStatTuple = row.getOrNull(10) is Number && row.getOrNull(11) is Number && row.getOrNull(12) is Number
        val hasLegacyThreeStatWithOfficer = row.getOrNull(8) is Number && row.getOrNull(9) is Number && row.getOrNull(10) is Number
        val hasLegacyThreeStatWithoutOfficer = row.getOrNull(8) is Number && row.getOrNull(9) is Number

        val (politics, charm, officerLevel, bornYear, deadYear, personalityIndex, specialIndex) = when {
            hasFiveStatTuple -> TupleLayout(
                politics = (row[8] as Number).toShort(),
                charm = (row[9] as Number).toShort(),
                officerLevel = (row[10] as Number).toShort(),
                bornYear = (row[11] as Number).toShort(),
                deadYear = (row[12] as Number).toShort(),
                personalityIndex = 13,
                specialIndex = 14,
            )
            hasLegacyThreeStatWithOfficer -> TupleLayout(
                politics = intel,
                charm = intel,
                officerLevel = (row[8] as Number).toShort(),
                bornYear = (row[9] as Number).toShort(),
                deadYear = (row[10] as Number).toShort(),
                personalityIndex = 11,
                specialIndex = 12,
            )
            hasLegacyThreeStatWithoutOfficer -> TupleLayout(
                politics = intel,
                charm = intel,
                officerLevel = 0,
                bornYear = (row[8] as Number).toShort(),
                deadYear = (row[9] as Number).toShort(),
                personalityIndex = 10,
                specialIndex = 11,
            )
            else -> throw IllegalArgumentException("Unsupported officer tuple format: $row")
        }

        val personality = row.getOrNull(personalityIndex)?.toString()
        val special = row.getOrNull(specialIndex)?.toString()

        val rowCityId = resolveCityId(row.getOrNull(4), cityNameToId)
        val cityId: Long = if (rowCityId != null) {
            rowCityId
        } else if (nationId > 0L) {
            val cities = nationCityIds[nationId] ?: emptyList()
            pickRandomOrZero(cities, rng, allCityIds)
        } else {
            pickRandomOrZero(allCityIds, rng)
        }

        val minimumAge = if (nationId == 0L) 14.toShort() else 20.toShort()
        val age = (startYear - bornYear).toShort().coerceAtLeast(minimumAge)

        return Officer(
            sessionId = worldId,
            name = name,
            factionId = nationId,
            planetId = cityId,
            affinity = affinity,
            bornYear = bornYear,
            deadYear = deadYear,
            picture = picture,
            leadership = leadership,
            command = strength,
            intelligence = intel,
            politics = politics,
            administration = charm,
            officerLevel = officerLevel,
            npcState = defaultNpcState,
            age = age,
            startAge = age,
            personalCode = personality ?: "None",
            specialCode = special ?: "None",
            turnTime = OffsetDateTime.now(),
        )
    }

    private data class TupleLayout(
        val politics: Short,
        val charm: Short,
        val officerLevel: Short,
        val bornYear: Short,
        val deadYear: Short,
        val personalityIndex: Int,
        val specialIndex: Int,
    )

    private fun shouldSpawnScenarioOfficer(officer: Officer, year: Int): Boolean {
        if (officer.factionId != 0L) {
            return true
        }
        val appearYear = officer.bornYear.toInt() + 14
        if (year < appearYear) {
            return false
        }
        if (year >= officer.deadYear.toInt()) {
            return false
        }
        return true
    }

    fun spawnScenarioNpcGeneralsForYear(world: SessionState): Int {
        val scenario = getScenario(world.scenarioCode)
        val worldId = world.id.toLong()
        val currentYear = world.currentYear.toInt()
        val mapName =
            (world.config["mapCode"] as? String)
                ?: scenario.map?.mapName
                ?: "che"
        val extendedGeneralEnabled =
            parseBooleanFlag(world.config["extend"] ?: world.config["extendedGeneral"])
                ?: readExtendedGeneralFlag(scenario.const)

        val allCities = planetRepository.findBySessionId(worldId)
        if (allCities.isEmpty()) return 0
        val cityNameToId = allCities.associate { it.name to it.id }
        val allCityIds = allCities.map { it.id }

        val nationByName = factionRepository.findBySessionId(worldId).associateBy { it.name }
        val nationIdxToDbId = mutableMapOf<Int, Long>()
        for ((idx, nationRow) in scenario.nation.withIndex()) {
            val factionName = nationRow.getOrNull(0) as? String ?: continue
            val nationId = nationByName[factionName]?.id ?: continue
            nationIdxToDbId[idx + 1] = nationId
        }
        val nationCityIds = allCities
            .filter { it.factionId > 0L }
            .groupBy { it.factionId }
            .mapValues { (_, cities) -> cities.map { it.id } }

        val existingKeys = officerRepository.findBySessionId(worldId)
            .map { ScenarioGeneralKey.fromOfficer(it) }
            .toMutableSet()

        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val spawnedGenerals = mutableListOf<Officer>()

        fun spawnRows(rows: List<List<Any?>>, defaultNpcState: Short, source: String) {
            for ((idx, row) in rows.withIndex()) {
                val rng = Random("$hiddenSeed:$source:$idx:$currentYear:$mapName".hashCode().toLong())
                val officer = parseOfficer(
                    row = row,
                    worldId = worldId,
                    nationIdxToDbId = nationIdxToDbId,
                    nationCityIds = nationCityIds,
                    cityNameToId = cityNameToId,
                    allCityIds = allCityIds,
                    rng = rng,
                    startYear = currentYear,
                    defaultNpcState = defaultNpcState,
                )

                if (officer.factionId != 0L) continue
                if (!shouldSpawnScenarioOfficer(officer, currentYear)) continue

                val key = ScenarioGeneralKey.fromOfficer(officer)
                if (!existingKeys.add(key)) continue

                spawnedGenerals.add(officer)
            }
        }

        spawnRows(scenario.general, 2, "officer")
        if (extendedGeneralEnabled) {
            spawnRows(scenario.generalEx, 2, "generalEx")
        }
        spawnRows(scenario.generalNeutral, 6, "generalNeutral")
        if (spawnedGenerals.isNotEmpty()) officerRepository.saveAll(spawnedGenerals)

        return spawnedGenerals.size
    }

    private data class ScenarioGeneralKey(
        val name: String,
        val picture: String,
        val affinity: Short,
        val bornYear: Short,
        val deadYear: Short,
        val nationId: Long,
    ) {
        companion object {
            fun fromOfficer(officer: Officer): ScenarioGeneralKey {
                return ScenarioGeneralKey(
                    name = officer.name,
                    picture = officer.picture,
                    affinity = officer.affinity,
                    bornYear = officer.bornYear,
                    deadYear = officer.deadYear,
                    nationId = officer.factionId,
                )
            }
        }
    }

    /**
     * Build initial config map matching legacy ResetHelper::buildScenario() game_env keys.
     * Both camelCase and lowercase variants are set for keys that the codebase reads with
     * inconsistent casing (e.g. startYear / startyear, npcMode / npcmode).
     */
    private fun buildInitialConfig(
        scenario: ScenarioData,
        mapName: String,
        hiddenSeed: String,
        extendedGeneralEnabled: Boolean,
        tickSeconds: Int,
        npcMode: Int?,
        fiction: Int?,
        maxGeneral: Int?,
        maxNation: Int?,
        joinMode: String?,
        blockGeneralCreate: Int?,
        showImgLevel: Int?,
        autorunUser: List<String>?,
        startTime: String? = null,
        opentime: String? = null,
    ): MutableMap<String, Any> {
        val turnterm = (tickSeconds / 60).coerceAtLeast(1)
        val resolvedNpcMode = npcMode ?: 0
        val resolvedFiction = fiction ?: scenario.fiction
        val resolvedMaxGeneral = maxGeneral
            ?: (scenario.const["defaultMaxGeneral"] as? Number)?.toInt()
            ?: 500
        val resolvedMaxNation = maxNation
            ?: (scenario.const["defaultMaxNation"] as? Number)?.toInt()
            ?: 55
        val resolvedJoinMode = joinMode ?: "full"
        val resolvedBlockGeneralCreate = blockGeneralCreate ?: 0
        val resolvedShowImgLevel = showImgLevel ?: 0
        // Legacy formula: 4800 / turnterm, halved again (/3) when npcMode==1
        val killturn = if (resolvedNpcMode == 1) 4800 / turnterm / 3 else 4800 / turnterm
        // Legacy: (year - startyear + 10) * 2; at init year==startyear so develcost = 20
        val develcost = 20
        val unitSet = scenario.map?.unitSet ?: mapName

        val config = mutableMapOf<String, Any>(
            "mapCode" to mapName,
            "mapName" to mapName,
            "unitSet" to unitSet,
            "startyear" to scenario.startYear,
            "startYear" to scenario.startYear,
            "hiddenSeed" to hiddenSeed,
            "extend" to extendedGeneralEnabled,
            "extendedGeneral" to if (extendedGeneralEnabled) 1 else 0,
            "turnterm" to turnterm,
            "turnTerm" to turnterm,
            "killturn" to killturn,
            "npcmode" to resolvedNpcMode,
            "npcMode" to resolvedNpcMode,
            "allowNpcNationSpawn" to true,
            "allowInvaderSpawn" to true,
            "fiction" to resolvedFiction,
            "isFiction" to resolvedFiction,
            "maxGeneral" to resolvedMaxGeneral,
            "maxNation" to resolvedMaxNation,
            "joinMode" to resolvedJoinMode,
            "blockGeneralCreate" to resolvedBlockGeneralCreate,
            "showImgLevel" to resolvedShowImgLevel,
            "isunited" to 0,
            "isUnited" to 0,
            "develcost" to develcost,
            "develCost" to develcost,
            "refreshLimit" to 30000,
            "genius" to 5,
            "msg" to "공지사항",
            "serverCnt" to 1,
            "phase" to "normal",
            "finished" to false,
        )
        // opentime = 정식 오픈 시각. 가오픈은 startTime~opentime 사이.
        // startTime이 미래면 예약중(reserved), startTime~opentime이면 가오픈(pre_open).
        if (opentime != null) {
            config["opentime"] = opentime
        }
        if (startTime != null) {
            config["startTime"] = startTime
        }

        if (autorunUser != null) {
            config["autorun_user"] = autorunUser
        }
        scenario.const["availableGeneralCommand"]?.let { config["availableGeneralCommand"] = it }
        scenario.const["availableChiefCommand"]?.let { config["availableChiefCommand"] = it }
        return config
    }

    private fun convertLegacyEvents(scenarioEvents: List<List<Any>>, worldId: Long): List<Event> {
        return scenarioEvents.mapNotNull { eventArr ->
            if (eventArr.size < 4) return@mapNotNull null
            val targetCode = eventArr[0] as? String ?: return@mapNotNull null
            val priority = (eventArr[1] as? Number)?.toShort() ?: 0
            val condition = convertLegacyCondition(eventArr[2])

            val actions = (3 until eventArr.size).mapNotNull { i ->
                val raw = eventArr[i]
                if (raw is List<*>) convertLegacyAction(raw) else null
            }
            if (actions.isEmpty()) return@mapNotNull null

            val action: Map<String, Any> = if (actions.size == 1) actions[0]
                else mapOf("type" to "compound", "actions" to actions)

            Event(
                sessionId = worldId,
                targetCode = targetCode,
                priority = priority,
                condition = condition.toMutableMap(),
                action = action.toMutableMap(),
            )
        }
    }

    private fun convertLegacyCondition(raw: Any): Map<String, Any> {
        if (raw is Boolean) return mapOf("type" to if (raw) "always_true" else "always_false")
        if (raw !is List<*> || raw.isEmpty()) return mapOf("type" to "always_true")
        val type = raw[0] as? String ?: return mapOf("type" to "always_true")
        return when (type) {
            "Date" -> {
                val cmp = raw.getOrNull(1) as? String ?: "=="
                val year = raw.getOrNull(2) as? Number
                val month = raw.getOrNull(3) as? Number
                when {
                    year == null && month != null ->
                        mapOf("type" to "date_month", "month" to month.toInt())
                    year != null && month != null -> when (cmp) {
                        ">=" -> mapOf("type" to "date_after", "year" to year.toInt(), "month" to month.toInt())
                        else -> mapOf("type" to "date", "year" to year.toInt(), "month" to month.toInt())
                    }
                    else -> mapOf("type" to "always_true")
                }
            }
            "DateRelative" -> {
                val yearOffset = (raw.getOrNull(2) as? Number)?.toInt() ?: 0
                val month = (raw.getOrNull(3) as? Number)?.toInt() ?: 1
                mapOf("type" to "date_relative", "yearOffset" to yearOffset, "month" to month)
            }
            "RemainNation" -> {
                val cmp = raw.getOrNull(1) as? String ?: "<="
                val count = (raw.getOrNull(2) as? Number)?.toInt() ?: 1
                mapOf("type" to "remain_nation", "count" to count, "operator" to cmp)
            }
            "and", "or" -> {
                val conditions = raw.drop(1).mapNotNull { it?.let { convertLegacyCondition(it) } }
                mapOf("type" to type, "conditions" to conditions)
            }
            else -> {
                log.warn("Unknown legacy condition type: {}", type)
                mapOf("type" to "always_true")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertLegacyAction(raw: List<*>): Map<String, Any>? {
        if (raw.isEmpty()) return null
        val name = raw[0] as? String ?: return null
        return when (name) {
            "CreateManyNPC" -> mapOf(
                "type" to "create_many_npc",
                "npcCount" to ((raw.getOrNull(1) as? Number)?.toInt() ?: 10),
                "fillCnt" to ((raw.getOrNull(2) as? Number)?.toInt() ?: 0),
            )
            "RaiseNPCNation" -> mapOf("type" to "raise_npc_nation")
            "OpenNationBetting" -> mapOf(
                "type" to "open_nation_betting",
                "nationCnt" to ((raw.getOrNull(1) as? Number)?.toInt() ?: 1),
                "bonusPoint" to ((raw.getOrNull(2) as? Number)?.toInt() ?: 0),
            )
            "BlockScoutAction" -> mapOf("type" to "block_scout_action")
            "UnblockScoutAction" -> mapOf("type" to "unblock_scout_action")
            "DeleteEvent" -> mapOf("type" to "delete_self")
            "ChangeCity" -> mapOf(
                "type" to "change_city",
                "target" to (raw.getOrNull(1) ?: "all"),
                "changes" to (raw.getOrNull(2) ?: emptyMap<String, Any>()),
            )
            "LostUniqueItem" -> mapOf(
                "type" to "lost_unique_item",
                "lostProb" to ((raw.getOrNull(1) as? Number)?.toDouble() ?: 0.1),
            )
            "ProcessWarIncome" -> mapOf("type" to "process_war_income")
            "MergeInheritPointRank" -> mapOf("type" to "merge_inherit_point_rank")
            "ProcessSemiAnnual" -> mapOf(
                "type" to "process_semi_annual",
                "resource" to ((raw.getOrNull(1) as? String) ?: "gold"),
            )
            "ProcessIncome" -> mapOf(
                "type" to "process_income",
                "resource" to ((raw.getOrNull(1) as? String) ?: "gold"),
            )
            "ResetOfficerLock" -> mapOf("type" to "reset_officer_lock")
            "RaiseDisaster" -> mapOf("type" to "raise_disaster")
            "RandomizeCityTradeRate" -> mapOf("type" to "randomize_trade_rate")
            "NewYear" -> mapOf("type" to "new_year")
            "AssignGeneralSpeciality" -> mapOf("type" to "assign_general_speciality")
            "NoticeToHistoryLog" -> mapOf(
                "type" to "log",
                "message" to ((raw.getOrNull(1) as? String) ?: ""),
            )
            "UpdateNationLevel" -> mapOf("type" to "update_nation_level")
            "ProvideNPCTroopLeader" -> mapOf("type" to "provide_npc_troop_leader")
            "AddGlobalBetray" -> mapOf(
                "type" to "add_global_betray",
                "cnt" to ((raw.getOrNull(1) as? Number)?.toInt() ?: 1),
                "ifMax" to ((raw.getOrNull(2) as? Number)?.toInt() ?: 0),
            )
            else -> {
                log.warn("Unknown legacy action: {}", name)
                null
            }
        }
    }

    private fun parseBooleanFlag(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> {
                when (raw.trim().lowercase()) {
                    "1", "true", "yes", "on" -> true
                    "0", "false", "no", "off" -> false
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun readExtendedGeneralFlag(const: Map<String, Any>): Boolean {
        val candidates = listOf(const["extendedGeneral"], const["extended_general"], const["extend"])
        for (raw in candidates) {
            when (raw) {
                is Boolean -> return raw
                is Number -> return raw.toInt() != 0
                is String -> {
                    val normalized = raw.trim().lowercase()
                    if (normalized in setOf("1", "true", "yes", "on")) return true
                    if (normalized in setOf("0", "false", "no", "off")) return false
                }
            }
        }
        return true
    }

    private fun resolveCityId(raw: Any?, cityNameToId: Map<String, Long>): Long? {
        return when (raw) {
            is Number -> raw.toLong().takeIf { it > 0L }
            is String -> cityNameToId[raw]
            else -> null
        }
    }

    private fun pickRandomOrZero(values: List<Long>, rng: Random, fallback: List<Long> = emptyList()): Long {
        if (values.isNotEmpty()) {
            return values[rng.nextInt(values.size)]
        }
        if (fallback.isNotEmpty()) {
            return fallback[rng.nextInt(fallback.size)]
        }
        return 0L
    }

    private fun loadAllScenarios() {
        if (scenariosLoaded) return
        val resolver = PathMatchingResourcePatternResolver()
        val resources = resolver.getResources("classpath*:data/scenarios/scenario_*.json")
        for (resource in resources) {
            val filename = resource.filename ?: continue
            val code = filename.removePrefix("scenario_").removeSuffix(".json")
            val data: ScenarioData = objectMapper.readValue(resource.inputStream)
            scenarios[code] = data
        }
        scenariosLoaded = true
    }

    private fun loadDefaults(): ScenarioData {
        val resource = PathMatchingResourcePatternResolver()
            .getResource("classpath*:data/scenarios/default.json")
        return try {
            objectMapper.readValue(resource.inputStream)
        } catch (_: Exception) {
            ScenarioData()
        }
    }

    private fun readStringList(raw: Any?): List<String> {
        if (raw !is Collection<*>) return emptyList()
        return raw.mapNotNull { it as? String }
    }

    private fun parseInt(raw: Any?): Int? {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }
}
