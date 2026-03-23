package com.openlogh.engine.ai

import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class FactionAI(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val diplomacyRepository: DiplomacyRepository,
) {
    fun processAI(world: SessionState) {
        val sessionId = world.id.toLong()
        val factions = factionRepository.findBySessionId(sessionId)
        for (faction in factions) {
            if (faction.factionRank <= 0.toShort()) continue
            decideNationAction(faction, world, Random)
        }
    }

    fun shouldDeclareWar(nation: Faction, target: Faction, world: SessionState): Boolean {
        val myPlanets = planetRepository.findByFactionId(nation.id)
        val targetPlanets = planetRepository.findByFactionId(target.id)
        val myOfficers = officerRepository.findBySessionIdAndNationId(world.id.toLong(), nation.id)
        val targetOfficers = officerRepository.findBySessionIdAndNationId(world.id.toLong(), target.id)

        // Must have more cities and more officers
        if (myPlanets.size <= targetPlanets.size) return false
        if (myOfficers.size <= targetOfficers.size) return false

        // Must be significantly stronger
        if (nation.militaryPower <= target.militaryPower * 2) return false

        // Must have sufficient resources
        if (nation.funds < 10000 || nation.supplies < 10000) return false

        return true
    }

    fun decideNationAction(nation: Faction, world: SessionState, rng: Random): String {
        val sessionId = world.id.toLong()
        val policy = NpcPolicyBuilder.buildNationPolicy(nation.meta)

        // Resource check - rest if too poor
        if (nation.funds < policy.reqNationGold || nation.supplies < policy.reqNationRice) {
            return "Nation휴식"
        }

        val officers = officerRepository.findBySessionIdAndNationId(sessionId, nation.id)
        val planets = planetRepository.findByFactionId(nation.id)

        // War strategic actions
        if (nation.warState > 0.toShort() && nation.strategicCmdLimit > 0.toShort()) {
            val warActions = listOf("급습", "의병모집", "필사즉생")
            return warActions[rng.nextInt(warActions.size)]
        }
        if (nation.warState > 0.toShort()) {
            return "Nation휴식"
        }

        // Check for unassigned officers
        val unassigned = officers.filter { it.rank <= 0.toShort() && it.npcState.toInt() in 2..4 }
        if (unassigned.isNotEmpty()) {
            return "발령"
        }

        // Check for expandable cities
        val lowLevelPlanets = planets.filter { it.level < 5.toShort() }
        if (lowLevelPlanets.isNotEmpty() && nation.funds >= 10000) {
            return "증축"
        }

        // Check for low dedication officers
        val lowDedOfficers = officers.filter { it.dedication < 100 && it.rank > 0.toShort() }
        if (lowDedOfficers.isNotEmpty() && nation.funds >= 5000) {
            return "포상"
        }

        return "Nation휴식"
    }
}
