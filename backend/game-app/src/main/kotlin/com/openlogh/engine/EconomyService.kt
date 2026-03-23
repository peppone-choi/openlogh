package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.springframework.stereotype.Service
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

@Service
class EconomyService(
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val mapService: MapService,
    private val historyService: HistoryService,
    private val inheritanceService: InheritanceService,
) {
    fun processMonthly(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        val officers = officerRepository.findBySessionId(sessionId)

        val factionMap = factions.associateBy { it.id }

        // Monthly income
        processIncome(world, planets, factionMap, officers)

        // Semi-annual processing (January and July)
        if (world.currentMonth.toInt() == 1 || world.currentMonth.toInt() == 7) {
            processSemiAnnual(world, planets, factionMap, officers)
        }

        // Update faction rank
        updateFactionRank(factionMap, planets)

        // Save all
        planets.forEach { planetRepository.save(it) }
        factions.forEach { factionRepository.save(it) }
        officers.forEach { officerRepository.save(it) }
    }

    private fun processIncome(
        world: SessionState,
        planets: List<Planet>,
        factionMap: Map<Long, Faction>,
        officers: List<Officer>,
    ) {
        val factionIncome = mutableMapOf<Long, Pair<Int, Int>>() // factionId → (funds, supplies)

        for (planet in planets) {
            if (planet.factionId == 0L) continue
            if (planet.supplyState.toInt() == 0) continue

            val faction = factionMap[planet.factionId] ?: continue
            val isCapital = faction.capitalPlanetId == planet.id

            val baseFunds = planet.population / 100 + planet.commerce / 10
            val baseSupplies = planet.population / 100 + planet.production / 10
            val capitalBonus = if (isCapital) 1.2 else 1.0

            // 이데올로기 체제 보정 적용
            val doctrine = com.openlogh.engine.doctrine.FactionDoctrine.fromMeta(faction.meta)
            val commerceMod = 1.0 + (doctrine?.getCommerceModifier() ?: 0.0)
            val taxMod = 1.0 + (doctrine?.getTaxModifier() ?: 0.0)
            val supplyMod = 1.0 + (doctrine?.getSupplyModifier() ?: 0.0)

            val funds = floor(baseFunds * capitalBonus * commerceMod * taxMod).toInt()
            val supplies = floor(baseSupplies * capitalBonus * supplyMod).toInt()

            val (prevFunds, prevSupplies) = factionIncome.getOrDefault(planet.factionId, 0 to 0)
            factionIncome[planet.factionId] = (prevFunds + funds) to (prevSupplies + supplies)

            // War income: convert dead back to population
            if (planet.dead > 0) {
                val returned = (planet.dead * 0.2).toInt()
                planet.population = min(planet.population + returned + 20, planet.populationMax)
                val warFunds = planet.dead / 10
                val (curF, curS) = factionIncome.getOrDefault(planet.factionId, 0 to 0)
                factionIncome[planet.factionId] = (curF + warFunds) to curS
                planet.dead = 0
            }
        }

        // Apply income to factions
        for ((factionId, income) in factionIncome) {
            val faction = factionMap[factionId] ?: continue
            faction.funds += income.first
            faction.supplies += income.second
        }

        // Officer salary
        for (officer in officers) {
            if (officer.npcState.toInt() == 5) continue
            if (officer.factionId == 0L) continue
            val salaryFunds = officer.dedication / 100 + 10
            val salarySupplies = officer.dedication / 100 + 10
            officer.funds += salaryFunds
            officer.supplies += salarySupplies
        }
    }

    private fun processSemiAnnual(
        world: SessionState,
        planets: List<Planet>,
        factionMap: Map<Long, Faction>,
        officers: List<Officer>,
    ) {
        for (planet in planets) {
            if (planet.factionId != 0L) {
                // Supplied nation planet: growth without pre-decay
                val faction = factionMap[planet.factionId]
                val taxRate = faction?.conscriptionRateTmp?.toInt() ?: 10
                val genericRatio = (20 - taxRate) / 200.0
                val growthMultiplier = 1.0 + genericRatio

                // 이데올로기 체제 보정
                val doctrine = com.openlogh.engine.doctrine.FactionDoctrine.fromMeta(faction?.meta ?: emptyMap())
                val prodMod = 1.0 + (doctrine?.getProductionModifier() ?: 0.0)
                val commMod = 1.0 + (doctrine?.getCommerceModifier() ?: 0.0)
                val techMod = 1.0 + (doctrine?.getTechModifier() ?: 0.0)

                planet.production = min(planet.productionMax, floor(planet.production * growthMultiplier * prodMod).toInt())
                planet.commerce = min(planet.commerceMax, floor(planet.commerce * growthMultiplier * commMod).toInt())
                planet.security = min(planet.securityMax, floor(planet.security * growthMultiplier).toInt())
                planet.population = min(planet.populationMax, floor(planet.population * growthMultiplier).toInt())
            } else {
                // Neutral planet: decay by 0.99
                planet.production = floor(planet.production * 0.99).toInt()
                planet.commerce = floor(planet.commerce * 0.99).toInt()
                planet.security = floor(planet.security * 0.99).toInt()
                planet.population = floor(planet.population * 0.99).toInt()
                planet.approval = 50f
            }
        }

        // Resource decay for rich officers and factions
        for (officer in officers) {
            if (officer.funds > 10000) officer.funds = floor(officer.funds * 0.95).toInt()
            if (officer.supplies > 10000) officer.supplies = floor(officer.supplies * 0.95).toInt()
        }
        for (faction in factionMap.values) {
            if (faction.funds > 100000) faction.funds = floor(faction.funds * 0.98).toInt()
            if (faction.supplies > 100000) faction.supplies = floor(faction.supplies * 0.98).toInt()
        }
    }

    private fun updateFactionRank(factionMap: Map<Long, Faction>, planets: List<Planet>) {
        for (faction in factionMap.values) {
            val factionPlanets = planets.count { it.factionId == faction.id }
            val newRank = when {
                factionPlanets >= 5 -> maxOf(faction.factionRank.toInt(), 3)
                factionPlanets >= 3 -> maxOf(faction.factionRank.toInt(), 2)
                factionPlanets >= 1 -> maxOf(faction.factionRank.toInt(), 1)
                else -> faction.factionRank.toInt()
            }
            faction.factionRank = newRank.toShort()
        }
    }

    // Event-driven entry points (called by EventService)
    fun processIncomeEvent(world: SessionState) { processMonthly(world) }
    fun processSemiAnnualEvent(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId).associateBy { it.id }
        val officers = officerRepository.findBySessionId(sessionId)
        processSemiAnnual(world, planets, factions, officers)
        planets.forEach { planetRepository.save(it) }
        factions.values.forEach { factionRepository.save(it) }
        officers.forEach { officerRepository.save(it) }
    }

    fun updateCitySupplyState(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        for (planet in planets) {
            if (planet.factionId == 0L) {
                planet.supplyState = 0
            } else {
                planet.supplyState = 1
            }
            planetRepository.save(planet)
        }
    }

    fun updateNationLevelEvent(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        updateFactionRank(factions.associateBy { it.id }, planets)
        factions.forEach { factionRepository.save(it) }
    }

    fun preUpdateMonthly(world: SessionState) {
        updateCitySupplyState(world)
    }

    fun postUpdateMonthly(world: SessionState) {
        processMonthly(world)
    }

    fun processDisasterOrBoom(world: SessionState) {
        // Disaster/boom events - to be implemented
    }

    fun randomizeCityTradeRate(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)
        for (planet in planets) {
            val variation = (-5..5).random()
            planet.tradeRoute = (planet.tradeRoute + variation).coerceIn(80, 120)
            planetRepository.save(planet)
        }
    }

    private fun getDedLevel(dedication: Int): Int = ceil(sqrt(dedication / 100.0)).toInt()

    private fun calcCityGoldIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int): Double {
        val base = city.population / 100.0 + city.commerce / 10.0
        val securityBonus = 1.0 + (city.security.toDouble() / city.securityMax.toDouble()) * 0.1
        return base * securityBonus
    }

    private fun calcCityRiceIncome(city: Planet, officerCnt: Int, isCapital: Boolean, nationLevel: Int): Double {
        val base = city.population / 100.0 + city.production / 10.0
        val securityBonus = 1.0 + (city.security.toDouble() / city.securityMax.toDouble()) * 0.1
        return base * securityBonus
    }
}
