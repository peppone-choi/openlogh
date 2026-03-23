package com.openlogh.engine.war

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import kotlin.random.Random

data class WarUnitReport(
    val id: Long,
    val type: String,
    val name: String,
    val isAttacker: Boolean,
    val killed: Int,
    val dead: Int,
)

data class WarBattleOutcome(
    val attacker: Officer,
    val defenders: List<Officer>,
    val defenderCity: Planet,
    val logs: List<String>,
    val conquered: Boolean,
    val reports: List<WarUnitReport>,
)

data class WarAftermathConfig(
    val initialNationGenLimit: Int,
    val techLevelIncYear: Int,
    val initialAllowedTechLevel: Int,
    val maxTechLevel: Int,
    val defaultCityWall: Int,
    val baseGold: Int,
    val baseRice: Int,
    val castleCrewTypeId: Int,
)

data class WarTimeContext(
    val year: Int,
    val month: Int,
    val startYear: Int,
)

data class DiplomacyDelta(
    val factionId1: Long,
    val factionId2: Long,
    val delta: Int,
)

data class ConquestResult(
    val nationCollapsed: Boolean,
)

data class WarAftermathOutcome(
    val diplomacyDeltas: List<DiplomacyDelta>,
    val conquest: ConquestResult?,
)

data class WarAftermathInput(
    val battle: WarBattleOutcome,
    val attackerNation: Faction,
    val defenderNation: Faction,
    val attackerCity: Planet,
    val defenderCity: Planet,
    val nations: List<Faction>,
    val cities: List<Planet>,
    val generals: List<Officer>,
    val config: WarAftermathConfig,
    val time: WarTimeContext,
    val rng: Random = Random.Default,
)

class WarAftermath {

    fun resolveWarAftermath(input: WarAftermathInput): WarAftermathOutcome {
        val battle = input.battle

        // Update tech for both nations
        val attackerTech = (input.attackerNation.meta["tech"] as? Number)?.toInt() ?: 0
        input.attackerNation.meta["tech"] = attackerTech + 1
        val defenderTech = (input.defenderNation.meta["tech"] as? Number)?.toInt() ?: 0
        input.defenderNation.meta["tech"] = defenderTech + 1

        // Calculate dead civilians
        val attackerDead = battle.reports.filter { it.isAttacker }.sumOf { it.dead }
        val defenderKilled = battle.reports.filter { it.isAttacker }.sumOf { it.killed }

        val existingAttackerDead = (input.attackerCity.meta["dead"] as? Number)?.toInt() ?: 0
        val existingDefenderDead = (input.defenderCity.meta["dead"] as? Number)?.toInt() ?: 0

        input.attackerCity.meta["dead"] = existingAttackerDead + (attackerDead * 1.2).toInt()
        input.defenderCity.meta["dead"] = existingDefenderDead + (defenderKilled * 0.9).toInt()

        // Diplomacy deltas
        val diplomacyDeltas = listOf(
            DiplomacyDelta(input.attackerNation.id, input.defenderNation.id, -10),
            DiplomacyDelta(input.defenderNation.id, input.attackerNation.id, -10),
        )

        // Conquest
        var conquest: ConquestResult? = null
        if (battle.conquered) {
            input.defenderCity.factionId = input.attackerNation.id
            input.defenderCity.meta["conflict"] = "{}"

            val defenderCities = input.cities.filter {
                it.factionId == input.defenderNation.id && it.id != input.defenderCity.id
            }
            val nationCollapsed = defenderCities.isEmpty()

            if (nationCollapsed) {
                val goldReward = (input.defenderNation.funds / 2).coerceAtLeast(0)
                val riceReward = ((input.defenderNation.supplies - 2000) / 2).coerceAtLeast(0)
                input.attackerNation.funds += goldReward
                input.attackerNation.supplies += riceReward

                for (gen in input.generals.filter { it.factionId == input.defenderNation.id }) {
                    gen.experience = (gen.experience * 0.9).toInt()
                    gen.dedication = (gen.dedication * 0.5).toInt()
                }
            }

            conquest = ConquestResult(nationCollapsed = nationCollapsed)
        }

        return WarAftermathOutcome(
            diplomacyDeltas = diplomacyDeltas,
            conquest = conquest,
        )
    }
}
