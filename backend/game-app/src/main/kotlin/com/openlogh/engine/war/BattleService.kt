package com.openlogh.engine.war

import com.openlogh.engine.DiplomacyService
import kotlin.math.hypot
import com.openlogh.engine.EventService
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.tactical.TacticalSessionManager
import com.openlogh.entity.OldFaction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.GameConstService
import com.openlogh.service.GameEventService
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BattleService(
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
    private val messageRepository: MessageRepository,
    private val oldFactionRepository: OldFactionRepository,
    private val fleetRepository: FleetRepository,
    private val factionTurnRepository: FactionTurnRepository,
    private val eventService: EventService,
    private val diplomacyService: DiplomacyService,
    private val modifierService: ModifierService,
    private val gameConstService: GameConstService,
    private val gameEventService: GameEventService,
    private val historyService: HistoryService,
    private val inheritanceService: InheritanceService,
    private val tacticalSessionManager: TacticalSessionManager,
) {
    companion object {
        private val log = LoggerFactory.getLogger(BattleService::class.java)
    }

    private val engine = BattleEngine()

    fun processBattles(world: SessionState) {
        val sessionId = world.id.toLong()
        val allOfficers = officerRepository.findBySessionId(sessionId)

        // Find officers with ships that are stationed on planets
        val armedOfficers = allOfficers.filter {
            it.factionId != 0L && it.ships > 0 && it.locationState == "planet"
        }
        if (armedOfficers.isEmpty()) return

        // Group by planet to find conflicts
        val byPlanet = armedOfficers.groupBy { it.planetId }

        for ((planetId, officers) in byPlanet) {
            val planet = planetRepository.findById(planetId).orElse(null) ?: continue
            if (planet.factionId == 0L) continue

            // Find officers on this planet belonging to a faction different from the planet owner
            val attackers = officers
                .filter { it.factionId != planet.factionId }
                .filter { diplomacyService.areAtWar(sessionId, it.factionId, planet.factionId) }
                .sortedByDescending { it.command.toInt() + it.leadership.toInt() }

            if (attackers.isEmpty()) continue

            // 행성 수비 장교 (방어측) — C9: qualification filters
            val defenders = officers.filter {
                it.factionId == planet.factionId &&
                it.ships > 0 &&
                it.supplies > it.ships / 100 &&
                it.training >= it.defenceTrain &&
                it.morale >= it.defenceTrain
            }

            // 한 명이라도 온라인(플레이어)이면 전술 전투 세션 생성
            val anyOnline = attackers.any { it.userId != null } || defenders.any { it.userId != null }

            if (anyOnline) {
                try {
                    val sessionCode = tacticalSessionManager.createSession(
                        gameSessionId = sessionId,
                        attackerOfficerIds = attackers.map { it.id },
                        defenderOfficerIds = defenders.map { it.id },
                        planetId = planetId,
                    )
                    log.info("Tactical battle triggered: planet={}, sessionCode={}", planet.name, sessionCode)
                    gameEventService.broadcastEvent(
                        worldId = sessionId,
                        eventType = "tactical_battle_started",
                        payload = mapOf(
                            "sessionCode" to sessionCode,
                            "planetName" to planet.name,
                            "planetId" to planetId,
                            "attackerNames" to attackers.map { it.name },
                            "defenderNames" to defenders.map { it.name },
                        ),
                    )
                    continue // 전술 전투로 전환, 자동 전투 스킵
                } catch (e: Exception) {
                    log.warn("Failed to create tactical session, falling back to auto-battle: {}", e.message)
                }
            }

            // 한쪽이라도 오프라인 → 기존 자동 전투
            log.info("Auto-battle on planet={} (faction={}), {} attackers", planet.name, planet.factionId, attackers.size)

            for (attacker in attackers) {
                if (attacker.ships <= 0) continue
                // Planet may have changed ownership from a prior attacker this tick
                if (attacker.factionId == planet.factionId) continue

                val result = executeBattle(attacker, planet, world)

                if (result.attackerDamageDealt > 0 || result.defenderDamageDealt > 0) {
                    gameEventService.broadcastBattleResult(
                        worldId = sessionId,
                        attackerName = attacker.name,
                        planetName = planet.name,
                        attackerWon = result.attackerWon,
                        cityOccupied = result.cityOccupied,
                        attackerDamageDealt = result.attackerDamageDealt,
                        defenderDamageDealt = result.defenderDamageDealt,
                    )
                }

                // If planet was occupied, remaining attackers from other factions may need to fight the new owner
                if (result.cityOccupied) break
            }
        }
    }

    fun executeBattle(attacker: Officer, city: Planet, world: SessionState): BattleResult {
        val attackerFaction = factionRepository.findById(attacker.factionId).orElse(null)
            ?: return noBattle()
        val defenderFaction = factionRepository.findById(city.factionId).orElse(null)
            ?: return noBattle()

        val defenders = officerRepository.findByCityId(city.id)
            .filter { it.factionId == city.factionId && it.ships > 0 }
            .map { WarUnitGeneral(it, defenderFaction.techLevel) }

        val attackerUnit = WarUnitGeneral(attacker, attackerFaction.techLevel)

        val rng = Random(generateSeed(world))
        val result = engine.resolveBattle(attackerUnit, defenders, city, rng)

        if (result.cityOccupied) {
            handleCityOccupation(city, attacker, attackerFaction, defenderFaction, world)
        }

        officerRepository.save(attacker)

        return result
    }

    private fun handleCityOccupation(
        city: Planet,
        attacker: Officer,
        attackerFaction: com.openlogh.entity.Faction,
        defenderFaction: com.openlogh.entity.Faction,
        world: SessionState,
    ) {
        val oldFactionId = city.factionId

        // 6단계 점령 후처리
        val occupationResult = com.openlogh.engine.strategic.DetailedOccupationProcess.processOccupation(
            isCapital = defenderFaction.capitalPlanetId == city.id,
            hasArsenal = ((city.meta["facilities"] as? List<*>)?.any {
                (it as? Map<*, *>)?.get("type") == "arsenal"
            }) == true,
            garrisonStrength = city.garrisonSet,
            rng = Random,
        )

        city.factionId = attackerFaction.id
        city.approval = 0f
        city.supplyState = 1
        city.term = 0
        city.garrisonSet = 0
        city.production = (city.production * (if (occupationResult.facilitiesCaptured > 0) 0.8 else 0.7)).toInt()
        city.commerce = (city.commerce * 0.7).toInt()
        city.security = (city.security * 0.7).toInt()

        planetRepository.save(city)

        eventService.dispatchEvents(world, "OCCUPY_CITY")

        historyService.logWorldHistory(
            world.id.toLong(),
            "${attacker.name}이(가) ${city.name}을(를) 정복했습니다.",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )

        val remainingCities = planetRepository.findByFactionId(oldFactionId)

        if (defenderFaction.capitalPlanetId == city.id) {
            if (remainingCities.isEmpty()) {
                handleNationDestruction(defenderFaction, attackerFaction, world)
            } else {
                handleCapitalRelocation(defenderFaction, remainingCities, world)
            }
        } else if (remainingCities.isEmpty()) {
            handleNationDestruction(defenderFaction, attackerFaction, world)
        }
    }

    private fun handleCapitalRelocation(
        defenderFaction: com.openlogh.entity.Faction,
        remainingCities: List<Planet>,
        world: SessionState,
    ) {
        // C10: relocate to closest planet by coordinates; fall back to highest population
        val capitalId = defenderFaction.capitalPlanetId
        val oldCapital = if (capitalId != null) planetRepository.findById(capitalId).orElse(null) else null
        val newCapital = if (oldCapital != null) {
            val ox = (oldCapital.meta["x"] as? Number)?.toDouble()
            val oy = (oldCapital.meta["y"] as? Number)?.toDouble()
            if (ox != null && oy != null) {
                remainingCities.minByOrNull { p ->
                    val px = (p.meta["x"] as? Number)?.toDouble() ?: Double.MAX_VALUE
                    val py = (p.meta["y"] as? Number)?.toDouble() ?: Double.MAX_VALUE
                    hypot(px - ox, py - oy)
                }
            } else {
                remainingCities.maxByOrNull { it.population }
            }
        } else {
            remainingCities.maxByOrNull { it.population }
        } ?: return
        defenderFaction.capitalPlanetId = newCapital.id

        defenderFaction.funds /= 2
        defenderFaction.supplies /= 2

        val nationals = officerRepository.findByNationId(defenderFaction.id)
        for (gen in nationals) {
            gen.morale = (gen.morale * 0.8).toInt().toShort()
            officerRepository.save(gen)
        }

        factionRepository.save(defenderFaction)
    }

    private fun handleNationDestruction(
        defenderFaction: com.openlogh.entity.Faction,
        attackerFaction: com.openlogh.entity.Faction,
        world: SessionState,
    ) {
        eventService.dispatchEvents(world, "DESTROY_NATION")

        historyService.logWorldHistory(
            world.id.toLong(),
            "${defenderFaction.name}이(가) 멸망했습니다.",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )
        historyService.logNationHistory(
            world.id.toLong(),
            attackerFaction.id,
            "${defenderFaction.name}을(를) 정복했습니다.",
            world.currentYear.toInt(),
            world.currentMonth.toInt(),
        )

        val goldReward = (defenderFaction.funds / 2).coerceAtLeast(0)
        val riceReward = (defenderFaction.supplies / 2).coerceAtLeast(0)
        attackerFaction.funds += goldReward
        attackerFaction.supplies += riceReward
        factionRepository.save(attackerFaction)

        val defGenerals = officerRepository.findByNationId(defenderFaction.id)
        for (gen in defGenerals) {
            gen.factionId = 0
            gen.rank = 0
            gen.belong = 0
            gen.fleetId = 0

            val reduceFactor = 0.5 + Random.nextDouble() * 0.3
            gen.funds = (gen.funds * reduceFactor).toInt()
            gen.supplies = (gen.supplies * reduceFactor).toInt()

            gen.experience = (gen.experience * 0.9).toInt()
            gen.dedication = (gen.dedication * 0.5).toInt()

            if (gen.npcState in 2..8 && gen.npcState.toInt() != 5) {
                gen.meta["autoJoinNationId"] = attackerFaction.id
                gen.meta["autoJoinDelay"] = Random.nextInt(13)
            }

            officerRepository.save(gen)
        }

        val orphanedCities = planetRepository.findByFactionId(defenderFaction.id)
        for (c in orphanedCities) {
            c.factionId = 0
            c.frontState = 0
            planetRepository.save(c)
        }

        val fleets = fleetRepository.findByFactionId(defenderFaction.id)
        if (fleets.isNotEmpty()) {
            fleetRepository.deleteAll(fleets)
        }

        factionTurnRepository.deleteByFactionId(defenderFaction.id)

        diplomacyService.killAllRelationsForNation(world.id.toLong(), defenderFaction.id)

        oldFactionRepository.save(
            OldFaction(
                serverId = world.id.toString(),
                nation = defenderFaction.id,
                data = mutableMapOf("name" to defenderFaction.name, "color" to defenderFaction.color),
            ),
        )

        factionRepository.delete(defenderFaction)
    }

    private fun generateSeed(world: SessionState): Long {
        val hiddenSeed = world.config["hiddenSeed"]?.toString() ?: "default"
        return hiddenSeed.hashCode().toLong() + world.currentYear * 100 + world.currentMonth
    }

    private fun noBattle() = BattleResult(
        attackerWon = false,
        cityOccupied = false,
        attackerDamageDealt = 0,
        defenderDamageDealt = 0,
    )
}
