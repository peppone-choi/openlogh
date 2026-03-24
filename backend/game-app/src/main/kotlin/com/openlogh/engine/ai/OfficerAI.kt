package com.openlogh.engine.ai

import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.MapService
import org.springframework.stereotype.Service
import java.util.*
import kotlin.random.Random

@Service
class OfficerAI(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val diplomacyRepository: DiplomacyRepository,
    private val mapService: MapService,
) {
    constructor(factory: JpaWorldPortFactory, mapService: MapService) : this(
        officerRepository = factory.generalRepository,
        planetRepository = factory.cityRepository,
        factionRepository = factory.nationRepository,
        diplomacyRepository = factory.diplomacyRepository,
        mapService = mapService,
    )

    fun classifyGeneral(officer: Officer, rng: Random = Random.Default, minNPCWarLeadership: Int = 40): Int {
        var flags = 0
        if (officer.command >= officer.intelligence) flags = flags or GeneralType.WARRIOR.flag
        if (officer.intelligence >= officer.command) flags = flags or GeneralType.STRATEGIST.flag
        if (officer.leadership >= minNPCWarLeadership) flags = flags or GeneralType.COMMANDER.flag
        return flags
    }

    fun calcDiplomacyState(faction: Faction?, diplomacies: List<Diplomacy>): DiplomacyState {
        if (faction == null) return DiplomacyState.PEACE
        if (faction.warState > 0) return DiplomacyState.AT_WAR
        val relevant = diplomacies.filter { it.srcFactionId == faction.id || it.destFactionId == faction.id }
        if (relevant.any { it.stateCode == "선전포고" }) return DiplomacyState.AT_WAR
        if (relevant.any { it.stateCode == "종전제의" }) {
            val totalShips = officerRepository.findByWorldIdAndNationId(faction.sessionId, faction.id).sumOf { it.ships }
            return if (totalShips < 3000) DiplomacyState.RECRUITING else DiplomacyState.DECLARED
        }
        return DiplomacyState.PEACE
    }
    fun processAI(world: SessionState) {
        val sessionId = world.id.toLong()
        val officers = officerRepository.findBySessionId(sessionId)
        val npcOfficers = officers.filter { it.npcState.toInt() in 2..4 && it.factionId != 0L }
        for (officer in npcOfficers) {
            decideAndExecute(officer, world)
        }
    }

    fun decideAndExecute(officer: Officer, world: SessionState, rng: Random = Random): String {
        // Troop leaders just assemble
        if (officer.npcState.toInt() == 5) return "집합"

        // Injured officers recover
        if (officer.injury > 0) return "요양"

        // Honor reserved command BEFORE any repo lookups (covers wanderers too)
        val reserved = officer.meta["reservedCommand"] as? String
        if (reserved != null && reserved != "휴식") {
            officer.meta.remove("reservedCommand")
            return reserved
        }

        val city = planetRepository.findById(officer.planetId)
        if (city.isEmpty) return "휴식"
        val planet = city.get()

        // Wandering officer (no faction)
        if (officer.factionId == 0L) {
            return doWanderingAction(officer, world, planet, rng)
        }

        val faction = factionRepository.findById(officer.factionId).orElse(null)

        val sessionId = world.id.toLong()
        val diplomacies = diplomacyRepository.findBySessionIdAndIsDeadFalse(sessionId)
        val isAtWar = diplomacies.any {
            (it.srcFactionId == officer.factionId || it.destFactionId == officer.factionId) &&
                it.stateCode in listOf("전쟁", "선전포고")
        }

        // War mode: handle recruitment, training, and combat regardless of frontState
        if (isAtWar && officer.ships > 0) {
            return doWarAction(officer, world, planet, faction, rng)
        }

        // Normal domestic
        return doNormalDomestic(world, officer, planet, faction) ?: "휴식"
    }

    private fun doWanderingAction(
        officer: Officer,
        world: SessionState,
        planet: com.openlogh.entity.Planet,
        rng: Random,
    ): String {
        // Check if NPC should rise (create new faction)
        val action = doRise(world, officer, rng)
        if (action != null) return action

        return "휴식"
    }

    private fun doRise(world: SessionState, officer: Officer, rng: Random): String? {
        val startYear = (world.config["startyear"] as? Number)?.toInt()
            ?: (world.config["startYear"] as? Number)?.toInt()
            ?: return null
        val relYear = world.currentYear - startYear
        if (relYear < 1) return null

        val statThreshold = 70
        if (officer.leadership < statThreshold || officer.command < statThreshold || officer.intelligence < statThreshold) {
            return null
        }

        val allPlanets = planetRepository.findBySessionId(world.id.toLong())
        val currentPlanet = allPlanets.find { it.id == officer.planetId } ?: return null

        if (currentPlanet.level >= 5) {
            // Major city: check for open major city in connected neighbors (not self)
            @Suppress("UNCHECKED_CAST")
            val connections = currentPlanet.meta["connections"] as? List<Number> ?: return null
            val neighborIds = connections.map { it.toLong() }.toSet()
            val hasOpenMajorCity = allPlanets.any {
                it.id in neighborIds && it.factionId == 0L && it.level >= 5
            }
            if (!hasOpenMajorCity) return null
            // Deterministic: no random check at major city
        } else {
            // Non-major city: 50% skip then ~1.5% final chance
            if (rng.nextDouble() > 0.5) return null
            if (rng.nextDouble() >= 0.015) return null
        }

        officer.meta["aiAction"] = "거병"
        return "거병"
    }

    private fun doNormalDomestic(
        world: SessionState,
        officer: Officer,
        planet: com.openlogh.entity.Planet,
        faction: com.openlogh.entity.Faction?,
    ): String? {
        if (faction == null) return null

        // Trade resources if imbalanced
        val tradeAction = doTradeResources(world, officer, planet, faction)
        if (tradeAction != null) return tradeAction

        // Warrior type: recruitment and training take priority over development
        val isWarrior = officer.command >= officer.intelligence
        if (isWarrior) {
            val trainCost = officer.leadership.toInt() * 3
            if (officer.ships < officer.leadership.toInt() * 10) {
                return if (officer.funds >= trainCost + trainCost * 6) "모병" else "징병"
            }
            if (officer.training < 60) return "훈련"
        }

        // Domestic development priorities (threshold: 60% of max)
        if (planet.production < planet.productionMax * 0.6) return "농지개간"
        if (planet.commerce < planet.commerceMax * 0.6) return "상업투자"
        if (planet.security < planet.securityMax * 0.6) return "치안강화"

        // Tech research
        val startYear = (world.config["startyear"] as? Number)?.toInt()
            ?: (world.config["startYear"] as? Number)?.toInt()
            ?: 180
        val relYear = world.currentYear - startYear
        val techLimit = (relYear / 5 + 1) * 1000.0
        if (faction.techLevel < techLimit && officer.intelligence >= 60) {
            return "기술연구"
        }

        return null
    }

    private fun doTradeResources(
        world: SessionState,
        officer: Officer,
        planet: com.openlogh.entity.Planet,
        faction: com.openlogh.entity.Faction?,
    ): String? {
        val funds = officer.funds
        val supplies = officer.supplies

        @Suppress("UNCHECKED_CAST")
        val rankStats = officer.meta["rank"] as? Map<String, Any>
        val deathCrew = (rankStats?.get("deathcrew") as? Number)?.toInt() ?: 0

        // Weight toward buying supplies if high death rate
        val supplyWeight = if (deathCrew > 10000) 1.5 else 1.0

        if (funds > supplies * supplyWeight * 3 && supplies < 2000) {
            val amount = maxOf(100, (funds / 3 - (supplies / supplyWeight).toInt()).coerceAtMost(2000))
            officer.meta["aiArg"] = mutableMapOf<String, Any>("isBuy" to true, "amount" to amount)
            return "군량매매"
        }
        if (supplies > funds * 3 && funds < 2000) {
            val amount = maxOf(100, (supplies - funds).coerceAtMost(2000))
            officer.meta["aiArg"] = mutableMapOf<String, Any>("isBuy" to false, "amount" to amount)
            return "군량매매"
        }
        return null
    }

    private fun doWarAction(
        officer: Officer,
        world: SessionState,
        planet: com.openlogh.entity.Planet,
        faction: com.openlogh.entity.Faction?,
        rng: Random,
    ): String {
        // Recruitment priority when ships are low
        val trainCost = officer.leadership.toInt() * 3
        if (officer.ships < officer.leadership.toInt() * 10) {
            return if (officer.funds >= trainCost + trainCost * 6) "모병" else "징병"
        }

        // Training and morale maintenance
        if (officer.training < 60) return "훈련"
        if (officer.morale < 70) return "사기진작"

        // Attack if at an attackable front
        return if (planet.frontState >= 2.toShort()) "출병" else "훈련"
    }

    fun autoPromoteLord(officers: List<Officer>): Officer? {
        if (officers.any { it.rank >= 20 }) return null
        val best = officers
            .filter { it.npcState > 0 }
            .maxByOrNull { it.leadership.toInt() + it.command.toInt() + it.intelligence.toInt() }
            ?: return null
        best.rank = 20
        return best
    }

    fun choosePromotion(ctx: AIContext, rng: Random) {}

    fun chooseGoldBillRate(ctx: AIContext, cities: List<Planet>, policy: NpcNationPolicy): Int {
        val n = cities.size.coerceAtLeast(1)
        val cityIncomeBill = cities.sumOf { c ->
            if (c.commerceMax > 0) (c.commerce.toDouble() / c.commerceMax * c.approval).toInt() else 0
        } / n
        val warIncomeBill = cities.sumOf { c ->
            if (c.population > 0) (c.dead.toDouble() / c.population * 1000).toInt() else 0
        } / n
        val dedicationBill = ctx.general.dedication / 100
        val bill = cityIncomeBill + warIncomeBill + dedicationBill
        ctx.nation.taxRate = bill.toShort()
        return bill
    }

    fun chooseRiceBillRate(ctx: AIContext, cities: List<Planet>, policy: NpcNationPolicy): Int {
        val n = cities.size.coerceAtLeast(1)
        val conscriptionRateTmp = ctx.nation.conscriptionRateTmp.toInt()
        val agricultureBill = cities.sumOf { c ->
            if (c.productionMax > 0) (c.production.toDouble() / c.productionMax * c.approval).toInt() else 0
        } / n
        val wallBill = cities.sumOf { c ->
            val fortBill = if (c.fortressMax > 0) (c.fortress.toDouble() / c.fortressMax * conscriptionRateTmp).toInt() else 0
            val secBill = if (c.securityMax > 0) (c.security.toDouble() / c.securityMax * conscriptionRateTmp).toInt() else 0
            fortBill + secBill
        } / n
        val dedicationBill = ctx.general.dedication / 100
        val bill = agricultureBill + wallBill + dedicationBill
        ctx.nation.taxRate = bill.toShort()
        return bill
    }

    private fun getNationChiefLevel(level: Int): Int = when {
        level >= 6 -> 5
        level >= 4 -> 7
        level >= 2 -> 9
        else -> 11
    }
}
