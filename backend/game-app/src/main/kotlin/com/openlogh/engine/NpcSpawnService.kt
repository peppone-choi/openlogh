package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.MapService
import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.random.Random

@Service
class NpcSpawnService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val historyService: HistoryService,
    private val mapService: MapService,
) {
    fun processSpawns(world: SessionState) {}

    /**
     * Raise an invader NPC force at a neutral/border planet.
     * gin7: 이민족 침략 이벤트.
     */
    @Suppress("UNCHECKED_CAST")
    fun raiseInvader(world: SessionState, params: Map<String, Any>) {
        val sessionId = world.id.toLong()
        val rng = Random(world.currentYear * 100L + world.currentMonth)
        val planets = planetRepository.findBySessionId(sessionId)
        val neutralPlanets = planets.filter { it.factionId == 0L }
        val targetPlanet = neutralPlanets.randomOrNull() ?: planets.randomOrNull() ?: return

        val count = (params["count"] as? Number)?.toInt() ?: 3
        val cityParams = (params["cityParams"] as? Map<String, Any>) ?: emptyMap()

        buildNpcNation(world, rng, targetPlanet, cityParams, count, 1.0f)
        historyService.logWorldHistory(
            sessionId,
            "이민족 세력이 ${targetPlanet.name}에 출현했습니다.",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
    }

    /**
     * Create an NPC nation at a specified or random planet.
     * gin7: NPC 국가 생성 이벤트.
     */
    @Suppress("UNCHECKED_CAST")
    fun raiseNpcNation(world: SessionState, params: Map<String, Any>) {
        val sessionId = world.id.toLong()
        val rng = Random(world.currentYear * 100L + world.currentMonth + 7)
        val planets = planetRepository.findBySessionId(sessionId)

        val cityName = params["city"] as? String
        val targetPlanet = if (cityName != null) {
            planets.find { it.name == cityName }
        } else {
            planets.filter { it.factionId == 0L }.randomOrNull()
        } ?: return

        val count = (params["count"] as? Number)?.toInt() ?: 5
        val cityParams = (params["cityParams"] as? Map<String, Any>) ?: emptyMap()

        buildNpcNation(world, rng, targetPlanet, cityParams, count, 0.8f)
        historyService.logWorldHistory(
            sessionId,
            "새로운 세력이 ${targetPlanet.name}에서 건국되었습니다.",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
    }

    /**
     * Provide NPC troop leaders to factions that lack commanders.
     * gin7: 지휘관 부족 시 NPC 자동 보충.
     */
    fun provideNpcTroopLeader(world: SessionState, params: Map<String, Any>) {
        val sessionId = world.id.toLong()
        val rng = Random(world.currentYear * 100L + world.currentMonth + 13)
        val factions = factionRepository.findBySessionId(sessionId)
        val officers = officerRepository.findBySessionId(sessionId)

        for (faction in factions) {
            val factionOfficers = officers.filter { it.factionId == faction.id }
            val minOfficers = (params["minOfficers"] as? Number)?.toInt() ?: 5

            if (factionOfficers.size < minOfficers) {
                val deficit = minOfficers - factionOfficers.size
                val capitalPlanet = planetRepository.findById(faction.capitalPlanetId ?: continue).orElse(null) ?: continue

                for (i in 0 until deficit) {
                    val leadership = rng.nextInt(35, 70).toShort()
                    val command = rng.nextInt(35, 70).toShort()
                    val intelligence = rng.nextInt(35, 70).toShort()
                    val politics = derivePoliticsFromStats(leadership.toInt(), command.toInt(), intelligence.toInt(), rng).toShort()
                    val administration = deriveCharmFromStats(leadership.toInt(), command.toInt(), intelligence.toInt(), rng).toShort()

                    officerRepository.save(Officer(
                        sessionId = sessionId,
                        name = "지휘관_${faction.id}_${factionOfficers.size + i + 1}",
                        factionId = faction.id,
                        planetId = capitalPlanet.id,
                        leadership = leadership,
                        command = command,
                        intelligence = intelligence,
                        politics = politics,
                        administration = administration,
                        age = rng.nextInt(22, 45).toShort(),
                        npcState = 3,
                        rank = rng.nextInt(1, 5).toShort(),
                    ))
                }
            }
        }
    }

    private fun derivePoliticsFromStats(leadership: Int, strength: Int, intel: Int, rng: Random): Int {
        val base = intel * 0.4 + leadership * 0.3
        val variance = rng.nextInt(-15, 16)
        return (base + variance).roundToInt().coerceIn(30, 95)
    }

    private fun deriveCharmFromStats(leadership: Int, strength: Int, intel: Int, rng: Random): Int {
        val base = leadership * 0.3 + intel * 0.2 + strength * 0.1
        val variance = rng.nextInt(-15, 16)
        return (base + variance).roundToInt().coerceIn(30, 95)
    }

    @Suppress("unused")
    private fun buildNpcNation(
        world: SessionState,
        rng: Random,
        city: Planet,
        cityParams: Map<String, Any>,
        count: Int,
        statBonus: Float,
    ) {
        val sessionId = world.id.toLong()

        // Create faction
        val faction = factionRepository.save(Faction(
            sessionId = sessionId,
            name = "NPC세력",
            color = "#${rng.nextInt(0x1000000).toString(16).padStart(6, '0')}",
            capitalPlanetId = city.id,
            factionRank = 1,
        ))

        // Assign city to faction
        city.factionId = faction.id
        city.population = (cityParams["pop"] as? Number)?.toInt() ?: city.population
        city.production = (cityParams["agri"] as? Number)?.toInt() ?: city.production
        city.commerce = (cityParams["comm"] as? Number)?.toInt() ?: city.commerce
        city.security = (cityParams["secu"] as? Number)?.toInt() ?: city.security
        city.orbitalDefense = (cityParams["def"] as? Number)?.toInt() ?: city.orbitalDefense
        city.fortress = (cityParams["wall"] as? Number)?.toInt() ?: city.fortress
        planetRepository.save(city)

        // Create officers
        val killTurnForRuler: Short = 240
        for (i in 0 until count) {
            val isRuler = (i == 0)
            val leadership = rng.nextInt(40, 90).toShort()
            val command = rng.nextInt(40, 90).toShort()
            val intelligence = rng.nextInt(40, 90).toShort()
            val politics = derivePoliticsFromStats(leadership.toInt(), command.toInt(), intelligence.toInt(), rng).toShort()
            val administration = deriveCharmFromStats(leadership.toInt(), command.toInt(), intelligence.toInt(), rng).toShort()
            val age = rng.nextInt(20, 50).toShort()

            val officer = Officer(
                sessionId = sessionId,
                name = "NPC${faction.id}_${i + 1}",
                factionId = faction.id,
                planetId = city.id,
                leadership = leadership,
                command = command,
                intelligence = intelligence,
                politics = politics,
                administration = administration,
                age = age,
                npcState = 2,
                rank = if (isRuler) 20 else rng.nextInt(1, 10).toShort(),
            )

            if (isRuler) {
                officer.killTurn = killTurnForRuler
            } else {
                // Followers use deadYear-derived lifespan
                officer.deathYear = (world.currentYear + rng.nextInt(20, 60)).toShort()
            }

            officerRepository.save(officer)

            if (isRuler) {
                faction.supremeCommanderId = officer.id
                faction.officerCount = count
                factionRepository.save(faction)
            }
        }
    }
}
