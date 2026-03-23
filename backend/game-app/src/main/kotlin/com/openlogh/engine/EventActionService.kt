package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.entity.RankData
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.ScenarioService
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class EventActionService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val eventRepository: EventRepository,
    private val officerTurnRepository: OfficerTurnRepository,
    private val messageRepository: MessageRepository,
    private val bettingRepository: BettingRepository,
    private val betEntryRepository: BetEntryRepository,
    private val historyService: HistoryService,
    private val scenarioService: ScenarioService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val rankDataRepository: RankDataRepository,
    private val inheritanceService: InheritanceService,
) {
    fun addGlobalBetray(world: SessionState, cnt: Int = 1, ifMax: Int = 0) {
        val officers = officerRepository.findBySessionId(world.id.toLong())
        for (officer in officers) {
            if (officer.betray <= ifMax.toShort()) {
                officer.betray = (officer.betray + cnt).toShort()
            }
        }
        officerRepository.saveAll(officers)
    }

    fun blockScoutAction(world: SessionState, blockChangeScout: Boolean = false) {
        val factions = factionRepository.findBySessionId(world.id.toLong())
        for (faction in factions) {
            faction.scoutLevel = 1
        }
        factionRepository.saveAll(factions)
        if (blockChangeScout) {
            world.config["blockChangeScout"] = true
        }
    }

    fun unblockScoutAction(world: SessionState, blockChangeScout: Boolean = true) {
        val factions = factionRepository.findBySessionId(world.id.toLong())
        for (faction in factions) {
            faction.scoutLevel = 0
        }
        factionRepository.saveAll(factions)
        if (!blockChangeScout) {
            world.config["blockChangeScout"] = false
        }
    }

    fun changeCity(world: SessionState, target: String?, changes: Map<String, Any>) {
        val planets = planetRepository.findBySessionId(world.id.toLong())
        val filtered = when (target) {
            "free" -> planets.filter { it.factionId == 0L }
            else -> planets
        }
        for (planet in filtered) {
            for ((key, value) in changes) {
                applyCityChange(planet, key, value)
            }
        }
        planetRepository.saveAll(filtered)
    }

    private fun applyCityChange(planet: com.openlogh.entity.Planet, key: String, value: Any) {
        when (key) {
            "trust", "approval" -> {
                val v = when (value) {
                    is String -> if (value.startsWith("+")) planet.approval + value.substring(1).toFloat() else value.toFloat()
                    is Number -> value.toFloat()
                    else -> return
                }
                planet.approval = min(v, 100f)
            }
            "trade", "tradeRoute" -> {
                planet.tradeRoute = when (value) {
                    is String -> if (value.startsWith("+")) planet.tradeRoute + value.substring(1).toInt() else value.toInt()
                    is Number -> value.toInt()
                    else -> return
                }
            }
            "agri", "production" -> {
                val v = parseIntChange(planet.production, value)
                planet.production = min(v, planet.productionMax)
            }
            "comm", "commerce" -> {
                val v = parseIntChange(planet.commerce, value)
                planet.commerce = min(v, planet.commerceMax)
            }
            "secu", "security" -> {
                val v = parseIntChange(planet.security, value)
                planet.security = min(v, planet.securityMax)
            }
            "pop", "population" -> {
                val v = parseIntChange(planet.population, value)
                planet.population = min(v, planet.populationMax)
            }
        }
    }

    private fun parseIntChange(current: Int, value: Any): Int {
        return when (value) {
            is String -> when {
                value.startsWith("+") -> current + value.substring(1).toInt()
                value.startsWith("-") -> current - value.substring(1).toInt()
                else -> value.toInt()
            }
            is Number -> value.toInt()
            else -> current
        }
    }

    fun newYear(world: SessionState) {
        val officers = officerRepository.findBySessionId(world.id.toLong())
        for (officer in officers) {
            officer.age = (officer.age + 1).toShort()
            if (officer.factionId != 0L) {
                officer.belong = (officer.belong + 1).toShort()
            }
        }
        officerRepository.saveAll(officers)
        historyService.logWorldHistory(
            world.id.toLong(),
            "연호 ${world.currentYear}년",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
    }

    fun resetOfficerLock(world: SessionState) {
        val planets = planetRepository.findBySessionId(world.id.toLong())
        for (planet in planets) {
            planet.garrisonSet = 0
        }
        planetRepository.saveAll(planets)

        val factions = factionRepository.findBySessionId(world.id.toLong())
        for (faction in factions) {
            faction.meta.remove("chiefSet")
        }
        factionRepository.saveAll(factions)
    }

    fun processWarIncome(world: SessionState) {
        val planets = planetRepository.findBySessionId(world.id.toLong())
        val factions = factionRepository.findBySessionId(world.id.toLong())
        val factionMap = factions.associateBy { it.id }
        for (planet in planets) {
            if (planet.dead > 0 && planet.factionId != 0L) {
                val returned = (planet.dead * 0.2).toInt()
                planet.population += returned
                val warFunds = planet.dead / 10
                factionMap[planet.factionId]?.let { it.funds += warFunds }
                planet.dead = 0
            }
        }
        planetRepository.saveAll(planets)
        factionRepository.saveAll(factions)
    }

    fun autoDeleteInvader(world: SessionState, nationId: Long, currentEventId: Long) {
        val faction = factionRepository.findById(nationId)
        if (faction.isEmpty) {
            eventRepository.deleteById(currentEventId)
            return
        }
        // If faction exists and at war, keep the event alive
        officerRepository.findByNationId(nationId)
    }

    fun regNPC(world: SessionState, params: Map<String, Any>) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val officer = Officer(
            sessionId = sessionId,
            name = params["name"] as? String ?: "NPC",
            factionId = (params["nationId"] as? Number)?.toLong() ?: 0L,
            leadership = (params["leadership"] as? Number)?.toShort() ?: 50,
            command = (params["strength"] as? Number)?.toShort() ?: 50,
            intelligence = (params["intel"] as? Number)?.toShort() ?: 50,
            npcState = 2,
        )
        val cityName = params["city"] as? String
        val cityId = params["cityId"] as? Number
        if (cityId != null) {
            officer.planetId = cityId.toLong()
        } else if (cityName != null) {
            val planet = planets.find { it.name == cityName }
            if (planet != null) officer.planetId = planet.id
        }
        officerRepository.save(officer)
    }

    fun regNeutralNPC(world: SessionState, params: Map<String, Any>) {
        val officer = Officer(
            sessionId = world.id.toLong(),
            name = params["name"] as? String ?: "NPC",
            npcState = 6,
        )
        officerRepository.save(officer)
    }

    fun lostUniqueItem(world: SessionState, lostProb: Double = 0.1) {
        val buyableItems = setOf("숫돌", "도박", "명마")
        val officers = officerRepository.findBySessionId(world.id.toLong())
        for (officer in officers) {
            if (officer.npcState > 1.toShort()) continue
            if (officer.accessoryCode == "None") continue
            if (officer.accessoryCode in buyableItems) continue
            if (Math.random() < lostProb) {
                officer.accessoryCode = "None"
                officerRepository.save(officer)
            }
        }
    }

    fun mergeInheritPointRank(world: SessionState) {
        val sessionId = world.id.toLong()
        val officers = officerRepository.findBySessionId(sessionId)
        val oldEntries = rankDataRepository.findBySessionIdAndCategory(sessionId, "inherit")
        if (oldEntries.isNotEmpty()) {
            rankDataRepository.deleteAll(oldEntries)
        }
        val newEntries = officers.map { officer ->
            val score = inheritanceService.getInheritanceScore(officer)
            RankData(sessionId = sessionId, factionId = officer.factionId, category = "inherit", score = score)
        }
        rankDataRepository.saveAll(newEntries)
    }

    fun createManyNPC(world: SessionState, npcCount: Int, fillCnt: Int) {
        if (npcCount == 0 && fillCnt == 0) return
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val existingOfficers = officerRepository.findBySessionId(sessionId)
        val neutralPlanets = planets.filter { it.factionId == 0L }
        val total = if (npcCount > 0) npcCount else fillCnt - existingOfficers.size
        if (total <= 0) return

        for (i in 0 until total) {
            val planet = neutralPlanets.randomOrNull() ?: planets.randomOrNull()
            officerRepository.save(Officer(
                sessionId = sessionId,
                name = "NPC${existingOfficers.size + i + 1}",
                planetId = planet?.id ?: 0,
                npcState = 3,
                leadership = (40..80).random().toShort(),
                command = (40..80).random().toShort(),
                intelligence = (40..80).random().toShort(),
            ))
        }
    }

    fun assignGeneralSpeciality(world: SessionState) {
        val startYear = (world.config["startYear"] as? Number)?.toInt() ?: 184
        if (world.currentYear < startYear + 3) return
        val officers = officerRepository.findBySessionId(world.id.toLong())
        specialAssignmentService.checkAndAssignSpecials(world, officers)
        officerRepository.saveAll(officers)
    }
}
