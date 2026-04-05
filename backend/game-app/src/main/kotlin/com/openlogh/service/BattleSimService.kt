package com.openlogh.service

import com.openlogh.dto.BattlePhaseDetail
import com.openlogh.dto.SimulateRequest
import com.openlogh.dto.SimulateResult
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.engine.war.BattleEngine
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BattleSimService {
    private val battleEngine = BattleEngine()

    private val terrainModifier = mapOf(
        "plain" to (1.0 to 1.0),
        "forest" to (0.92 to 1.12),
        "hill" to (0.95 to 1.08),
        "mountain" to (0.85 to 1.2),
        "river" to (0.88 to 1.15),
    )

    private val weatherModifier = mapOf(
        "clear" to (1.0 to 1.0),
        "rain" to (0.9 to 1.08),
        "snow" to (0.86 to 1.12),
        "storm" to (0.82 to 1.18),
    )

    fun simulate(request: SimulateRequest): SimulateResult {
        val defenders = if (request.defenders.isEmpty()) listOf(request.defender) else request.defenders

        val planet = Planet(
            id = 0,
            sessionId = request.defenderCity.worldId,
            name = request.defenderCity.name,
            level = request.defenderCity.level.toShort(),
            factionId = request.defenderCity.nationId,
            orbitalDefense = request.defenderCity.def,
            orbitalDefenseMax = request.defenderCity.def,
            fortress = request.defenderCity.wall,
            fortressMax = request.defenderCity.wall,
            population = 50000,
            populationMax = 50000,
        )

        val attackerGeneral = toOfficer(request.attacker)
        val attackerUnit = WarUnitOfficer(attackerGeneral)

        val defenderUnits = defenders.map { unitInfo -> WarUnitOfficer(toOfficer(unitInfo)) }

        val terrainKey = request.terrain.lowercase()
        val weatherKey = request.weather.lowercase()
        val (terrainAtk, terrainDef) = terrainModifier[terrainKey] ?: (1.0 to 1.0)
        val (weatherAtk, weatherDef) = weatherModifier[weatherKey] ?: (1.0 to 1.0)

        attackerUnit.attackMultiplier *= (terrainAtk * weatherAtk)
        attackerUnit.defenceMultiplier *= (terrainDef * weatherDef)

        defenderUnits.forEach {
            it.attackMultiplier *= (terrainDef * weatherDef)
            it.defenceMultiplier *= (terrainAtk * weatherAtk)
        }

        val randomSeed = request.attacker.name + request.defender.name + terrainKey + weatherKey
        val rng = LiteHashDRBG.build(randomSeed)

        val (result, phaseDetails) = if (request.detailed) {
            val r = battleEngine.resolveBattleWithPhases(attackerUnit, defenderUnits, planet, rng)
            r.battleResult to r.phaseDetails
        } else {
            battleEngine.resolveBattle(attackerUnit, defenderUnits, planet, rng) to null
        }

        val logs = mutableListOf<String>()
        logs.add("=== 전투 시뮬레이터(BattleEngine) ===")
        logs.add("지형: $terrainKey / 날씨: $weatherKey")
        logs.add("공격: ${attackerGeneral.name} (${attackerGeneral.ships}명)")
        defenders.forEachIndexed { idx, def -> logs.add("방어${idx + 1}: ${def.name} (${def.crew}명)") }
        logs.addAll(result.attackerLogs)

        val attackerRemaining = attackerGeneral.ships.coerceAtLeast(0)
        val defendersRemaining = defenderUnits.map { it.general.ships.coerceAtLeast(0) }
        val totalDefenderRemaining = defendersRemaining.sum()

        val winner = when {
            result.cityOccupied -> "공격측 승리(점령)"
            result.attackerWon && totalDefenderRemaining <= 0 -> "공격측 승리"
            !result.attackerWon && attackerRemaining <= 0 -> "방어측 승리"
            !result.attackerWon -> "방어측 우세"
            else -> "교착 상태"
        }
        logs.add("결과: $winner")

        return SimulateResult(
            winner = winner,
            attackerRemaining = attackerRemaining,
            defenderRemaining = defendersRemaining.firstOrNull() ?: 0,
            defendersRemaining = defendersRemaining,
            rounds = result.attackerLogs.size,
            terrain = terrainKey,
            weather = weatherKey,
            logs = logs,
            phaseDetails = phaseDetails,
        )
    }

    private fun toOfficer(info: com.openlogh.dto.SimUnitInfo): Officer {
        return Officer(
            id = 0,
            sessionId = 0,
            name = info.name,
            factionId = info.nationId,
            planetId = 0,
            leadership = info.leadership.toShort(),
            command = info.strength.toShort(),
            intelligence = info.intel.toShort(),
            ships = info.crew,
            shipClass = info.crewType.toShort(),
            training = info.train.toShort(),
            morale = info.atmos.toShort(),
            flagshipCode = info.weaponCode,
            equipCode = info.bookCode,
            engineCode = info.horseCode,
            specialCode = info.specialCode,
            special2Code = "None", supplies = 200000,
            funds = 200000,
            expLevel = 5,
            experience = 3000,
            dedication = 3000,
        )
    }
}
