package com.openlogh.engine.ai

import com.openlogh.entity.*
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import org.springframework.stereotype.Service

import kotlin.math.sqrt
import kotlin.random.Random

@Service
class FactionAI(
    private val worldPortFactory: JpaWorldPortFactory,
) : FactionAIPort {
    override fun decideNationAction(nation: Faction, world: SessionState, rng: Random): String {
        val worldId = world.id.toLong()
        val ports = worldPortFactory.create(worldId)
        val nationCities = ports.planetsByFaction(nation.id).map { it.toEntity() }
        val nationGenerals = ports.officersByFaction(nation.id).map { it.toEntity() }
        val diplomacies = ports.activeDiplomacies().map { it.toEntity() }
        val policy = NpcPolicyBuilder.buildNationPolicy(nation.meta)

        val month = world.currentMonth.toInt()
        if (month % 3 == 0) {
            promoteEligibleGenerals(ports, nationGenerals, rng)
            adjustTaxAndBill(ports, nation, nationCities, nationGenerals)
        }

        val atWar = diplomacies.any {
            (it.stateCode == "선전포고" || it.stateCode == "전쟁") &&
                (it.srcFactionId == nation.id || it.destFactionId == nation.id)
        } || nation.warState > 0

        // At war: strategic commands
        if (atWar) {
            if (nation.strategicCmdLimit > 0) {
                val warActions = listOf("급습", "의병모집", "필사즉생")
                return warActions[rng.nextInt(warActions.size)]
            }
            return "Nation휴식"
        }

        // Consider war declaration before resource gate (war declaration is free)
        val allNations = ports.allFactions().map { it.toEntity() }
        val allCities = ports.allPlanets().map { it.toEntity() }
        val warTarget = pickWarTarget(nation, allNations, allCities, diplomacies, rng)
        if (warTarget != null) {
            nation.meta["aiWarTarget"] = mapOf("destNationId" to warTarget.id)
            ports.putFaction(nation.toSnapshot())
            return "선전포고"
        }

        // Low funds: skip expensive nation actions
        if (nation.funds < policy.reqNationGold || nation.supplies < policy.reqNationRice) {
            return "Nation휴식"
        }

        val candidates = linkedSetOf<String>()

        val assignmentNeeds = categorizeAssignmentNeeds(nation, nationCities, nationGenerals, atWar)
        if (assignmentNeeds.unassigned.isNotEmpty() || assignmentNeeds.needsFrontReinforcement) {
            selectAssignmentTarget(assignmentNeeds, nationGenerals, rng)?.let { target ->
                nation.meta["aiAssignmentTarget"] = mapOf(
                    "destGeneralID" to target.generalId,
                    "destCityID" to target.destCityId,
                    "reason" to target.reason,
                )
                ports.putFaction(nation.toSnapshot())
            }
            candidates.add("발령")
        }

        // Expand cities
        if (nation.funds > 5000) {
            val expansionTarget = selectExpansionTarget(nation, nationCities, rng)
            if (expansionTarget != null) {
                nation.meta["aiExpansionTarget"] = mapOf("destCityID" to expansionTarget.id)
                ports.putFaction(nation.toSnapshot())
                candidates.add("증축")
            }
        }

        // Reward generals with low dedication
        if (nation.funds > 3000) {
            val rewardTarget = selectRewardTarget(nationGenerals, rng)
            if (rewardTarget != null) {
                nation.meta["aiRewardTarget"] = mapOf("destGeneralID" to rewardTarget.id)
                ports.putFaction(nation.toSnapshot())
                candidates.add("포상")
            }
        }

        // Consider non-aggression pact (불가침제의)
        if (shouldConsiderNAP(nation, diplomacies, nationCities, rng)) {
            candidates.add("불가침제의")
        }

        // Consider war declaration via priority system (early return above handles urgent case)
        val secondWarTarget = pickWarTarget(nation, allNations, allCities, diplomacies, rng)
        if (secondWarTarget != null) {
            nation.meta["aiWarTarget"] = mapOf("destNationId" to secondWarTarget.id)
            ports.putFaction(nation.toSnapshot())
            candidates.add("선전포고")
        }

        // Consider capital relocation (천도)
        if (shouldConsiderCapitalMove(nation, nationCities)) {
            candidates.add("천도")
        }

        if (candidates.isEmpty()) {
            return "Nation휴식"
        }

        for (priority in policy.priority) {
            if (!policy.canDo(priority)) continue
            val mapped = mapNationPriorityToAction(priority) ?: continue
            if (mapped in candidates) return mapped
        }

        return candidates.first()
    }

    private fun promoteEligibleGenerals(writePort: WorldWritePort, nationGenerals: List<Officer>, rng: Random) {
        val candidates = nationGenerals.filter {
            it.npcState.toInt() != 5 &&
                it.officerLevel.toInt() in 1..3 &&
                it.dedication >= 200 &&
                targetOfficerLevel(it.dedication) > it.officerLevel.toInt()
        }.toMutableList()

        if (candidates.isEmpty()) return

        var promoted = 0
        while (promoted < 2 && candidates.isNotEmpty()) {
            val highestDedication = candidates.maxOf { it.dedication }
            val sameTier = candidates.filter { it.dedication == highestDedication }
            val picked = sameTier[rng.nextInt(sameTier.size)]

            val newLevel = targetOfficerLevel(picked.dedication)
            if (newLevel > picked.officerLevel.toInt() && newLevel <= 4) {
                picked.officerLevel = newLevel.coerceIn(0, 20).toShort()
                writePort.putOfficer(picked.toSnapshot())
                promoted += 1
            }

            candidates.removeIf { it.id == picked.id }
        }
    }

    private fun targetOfficerLevel(dedication: Int): Int {
        return when {
            dedication >= 600 -> 4
            dedication >= 400 -> 3
            dedication >= 200 -> 2
            else -> 1
        }
    }

    // TODO: This simplified tax/bill adjustment does NOT match PHP OfficerAI.php's
    //  chooseTexRate/chooseGoldBillRate/chooseRiceBillRate formulas. The PHP-matching
    //  implementations exist in OfficerAI.kt (chooseTexRate, chooseGoldBillRate,
    //  chooseRiceBillRate) but are called from OfficerAI.chooseNationTurn() which is
    //  currently NOT wired into TurnService. This method should be replaced by the
    //  OfficerAI rate choosers once chooseNationTurn is integrated into the turn pipeline.
    private fun adjustTaxAndBill(writePort: WorldWritePort, nation: Faction, nationCities: List<Planet>, nationGenerals: List<Officer>) {
        val totalResources = nation.funds + nation.supplies
        val totalBill = (nationGenerals.size + nationCities.size) * nation.taxRate.toInt().coerceAtLeast(0)

        var changed = false
        var newRateTmp = nation.conscriptionRateTmp.toInt()
        if (totalResources < 10000 && newRateTmp < 20) {
            newRateTmp += 1
            changed = true
        } else if (totalResources > 100000 && newRateTmp > 5) {
            newRateTmp -= 1
            changed = true
        }

        var newBill = nation.taxRate.toInt()
        if (totalBill > 0) {
            if (nation.funds < totalBill * 3 && newBill > 0) {
                newBill = (newBill - 5).coerceAtLeast(0)
                changed = true
            } else if (nation.funds > totalBill * 10 && newBill < 100) {
                newBill = (newBill + 5).coerceAtMost(100)
                changed = true
            }
        }

        if (changed) {
            nation.conscriptionRateTmp = newRateTmp.coerceIn(0, 100).toShort()
            nation.taxRate = newBill.coerceIn(0, 200).toShort()
            writePort.putFaction(nation.toSnapshot())
        }
    }

    private fun selectRewardTarget(nationGenerals: List<Officer>, rng: Random): Officer? {
        val playerCandidates = nationGenerals.filter {
            it.npcState.toInt() == 0 && it.dedication < 80
        }
        val npcCandidates = nationGenerals.filter {
            it.npcState.toInt() >= 2 && it.npcState.toInt() != 5 && it.dedication < 60
        }

        val pool = if (playerCandidates.isNotEmpty()) playerCandidates else npcCandidates
        if (pool.isEmpty()) return null

        val weightedPool = pool.map { general ->
            val baseDeficit = (100 - general.dedication).coerceAtLeast(1)
            val activityMultiplier = if (general.ships > 0) 1.5 else 1.0
            general to (baseDeficit * activityMultiplier)
        }

        return choiceByWeightPair(rng, weightedPool)
    }

    private fun selectExpansionTarget(nation: Faction, nationCities: List<Planet>, rng: Random): Planet? {
        val candidates = nationCities.filter { it.level < 5 }
        if (candidates.isEmpty()) return null

        val scored = candidates.map { city ->
            var score = (6 - city.level).toDouble()
            if (city.id == nation.capitalPlanetId) score += 3.0
            if (city.frontState > 0) score += 2.0
            if (city.frontState.toInt() == 0 && city.supplyState > 0) score += 1.5
            if (city.populationMax > 0) {
                score += city.population.toDouble() / city.populationMax
            }
            city to score
        }

        val maxScore = scored.maxOfOrNull { it.second } ?: return candidates[rng.nextInt(candidates.size)]
        val top = scored.filter { it.second == maxScore }.map { it.first }
        return top[rng.nextInt(top.size)]
    }

    private fun categorizeAssignmentNeeds(
        nation: Faction,
        nationCities: List<Planet>,
        nationGenerals: List<Officer>,
        atWar: Boolean,
    ): AssignmentNeeds {
        val capitalId = nation.capitalPlanetId
        val frontCities = nationCities.filter { it.frontState > 0 }
        val backCities = nationCities.filter { it.frontState.toInt() == 0 }
        val supplyCities = nationCities.filter { it.supplyState > 0 }
        val generalsByCity = nationGenerals
            .filter { it.npcState.toInt() != 5 }
            .groupBy { it.planetId }

        val undermannedFront = frontCities.filter { city ->
            val count = generalsByCity[city.id]?.size ?: 0
            count < 2
        }

        val rearSurplus = backCities.filter { city ->
            val count = generalsByCity[city.id]?.size ?: 0
            count >= 3 && city.id != capitalId
        }

        return AssignmentNeeds(
            unassigned = nationGenerals.filter {
                it.officerLevel.toInt() == 0 && it.npcState.toInt() != 5
            },
            undermannedFront = undermannedFront,
            rearSurplusCities = rearSurplus,
            supplyCities = supplyCities,
            needsFrontReinforcement = atWar && undermannedFront.isNotEmpty(),
        )
    }

    private fun selectAssignmentTarget(
        needs: AssignmentNeeds,
        nationGenerals: List<Officer>,
        rng: Random,
    ): AssignmentTarget? {
        val movableGenerals = nationGenerals.filter {
            it.npcState.toInt() != 5 && it.fleetId == 0L
        }

        if (needs.needsFrontReinforcement && needs.undermannedFront.isNotEmpty()) {
            val frontTarget = needs.undermannedFront[rng.nextInt(needs.undermannedFront.size)]

            val fromBack = movableGenerals.filter { gen ->
                needs.rearSurplusCities.any { it.id == gen.planetId }
            }
            val bestFromBack = fromBack.maxByOrNull { it.leadership.toInt() + it.ships / 100 }
            if (bestFromBack != null) {
                return AssignmentTarget(bestFromBack.id, frontTarget.id, "front_reinforcement")
            }

            val unassigned = needs.unassigned
            if (unassigned.isNotEmpty()) {
                val picked = unassigned.maxByOrNull { it.leadership.toInt() } ?: unassigned[rng.nextInt(unassigned.size)]
                return AssignmentTarget(picked.id, frontTarget.id, "front_unassigned")
            }
        }

        if (needs.unassigned.isNotEmpty()) {
            val supplyDest = needs.supplyCities.maxByOrNull { it.population }
            val picked = needs.unassigned[rng.nextInt(needs.unassigned.size)]
            if (supplyDest != null) {
                return AssignmentTarget(picked.id, supplyDest.id, "supply_assignment")
            }
        }

        return null
    }

    private fun <T> choiceByWeightPair(rng: Random, weighted: List<Pair<T, Double>>): T? {
        if (weighted.isEmpty()) return null
        val totalWeight = weighted.sumOf { it.second.coerceAtLeast(0.0) }
        if (totalWeight <= 0.0) return weighted[rng.nextInt(weighted.size)].first

        var roll = rng.nextDouble() * totalWeight
        for ((item, rawWeight) in weighted) {
            val weight = rawWeight.coerceAtLeast(0.0)
            roll -= weight
            if (roll <= 0.0) return item
        }
        return weighted.last().first
    }

    fun shouldDeclareWar(nation: Faction, targetNation: Faction, world: SessionState): Boolean {
        val ports = worldPortFactory.create(world.id.toLong())
        val nationCities = ports.planetsByFaction(nation.id).map { it.toEntity() }
        val targetCities = ports.planetsByFaction(targetNation.id).map { it.toEntity() }
        val nationGenerals = ports.officersByFaction(nation.id).map { it.toEntity() }
        val targetGenerals = ports.officersByFaction(targetNation.id).map { it.toEntity() }

        // Power comparison
        if (nation.militaryPower < targetNation.militaryPower) return false

        // Need sufficient cities and generals
        if (nationCities.size < 2) return false
        if (nationGenerals.size < targetGenerals.size) return false

        // Need sufficient resources
        if (nation.funds < 5000 || nation.supplies < 5000) return false

        return true
    }

    /**
     * Consider non-aggression pact when not at war and have neighbors without existing NAP.
     */
    private fun shouldConsiderNAP(
        nation: Faction,
        diplomacies: List<Diplomacy>,
        nationCities: List<Planet>,
        rng: Random,
    ): Boolean {
        // Don't propose NAP if low on resources
        if (nation.funds < 5000) return false

        // Find nations that already have diplomacy with us
        val existingDiploNationIds = diplomacies
            .filter { it.srcFactionId == nation.id || it.destFactionId == nation.id }
            .flatMap { listOf(it.srcFactionId, it.destFactionId) }
            .toSet()

        // We need neighboring nations without existing diplomacy
        val neighborCities = nationCities.filter { it.frontState > 0 }
        if (neighborCities.isEmpty()) return false

        // Low probability of NAP proposal
        return rng.nextInt(100) < 15
    }

    /**
     * Consider capital relocation when current capital is not the best city.
     * Per legacy: move capital based on population, development, and connectivity.
     */
    private fun shouldConsiderCapitalMove(nation: Faction, nationCities: List<Planet>): Boolean {
        val capitalId = nation.capitalPlanetId ?: return false
        if (nationCities.size < 2) return false
        val capital = nationCities.find { it.id == capitalId } ?: return false

        // Check if another city has significantly better population
        val bestCity = nationCities.maxByOrNull { it.population } ?: return false
        return bestCity.id != capital.id && bestCity.population > capital.population * 1.5
    }

    private fun pickWarTarget(
        nation: Faction,
        allNations: List<Faction>,
        allCities: List<Planet>,
        diplomacies: List<Diplomacy>,
        rng: Random,
    ): Faction? {
        if (nation.funds < 3000 || nation.supplies < 3000) return null

        val existingDiploNationIds = diplomacies
            .filter { it.srcFactionId == nation.id || it.destFactionId == nation.id }
            .flatMap { listOf(it.srcFactionId, it.destFactionId) }
            .toSet()

        val neighborNationIds = mutableSetOf<Long>()
        for (city in allCities) {
            if (city.factionId != nation.id && city.factionId != 0L && city.frontState > 0) {
                neighborNationIds.add(city.factionId)
            }
        }

        val targets = allNations.filter {
            it.id != nation.id &&
                it.factionRank > 0 &&
                it.id !in existingDiploNationIds &&
                it.militaryPower < nation.militaryPower &&
                neighborNationIds.contains(it.id)
        }

        if (targets.isEmpty()) return null
        if (rng.nextInt(100) >= 10) return null

        return choiceByWeightPair(rng, targets.map { it to 1.0 / sqrt(it.militaryPower.toDouble() + 1.0) })
    }

    private fun mapNationPriorityToAction(priority: String): String? {
        return when (priority) {
            "부대전방발령", "부대후방발령", "부대구출발령",
            "NPC전방발령", "NPC후방발령", "NPC내정발령",
            "유저장전방발령", "유저장후방발령" -> "발령"
            "NPC포상", "유저장포상" -> "포상"
            "NPC몰수" -> "몰수"
            "불가침제의" -> "불가침제의"
            "선전포고" -> "선전포고"
            "천도" -> "천도"
            else -> null
        }
    }

    private data class AssignmentNeeds(
        val unassigned: List<Officer>,
        val undermannedFront: List<Planet>,
        val rearSurplusCities: List<Planet>,
        val supplyCities: List<Planet>,
        val needsFrontReinforcement: Boolean,
    )

    private data class AssignmentTarget(
        val generalId: Long,
        val destCityId: Long,
        val reason: String,
    )
}
