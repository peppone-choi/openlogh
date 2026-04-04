package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import com.openlogh.util.JosaUtil
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 출병 (Attack march) — Legacy parity: che_출병.php
 *
 * Route-finding (BFS-based shortest path to destCity):
 *  1. Among shortest-distance and shortest+1 cities, find enemy cities → attack
 *  2. If no enemy found, pick the nearest allied city → fallback to 이동 (inside run())
 */
class 출병(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "출병"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            val cost = getCost()
            return listOf(
                NotOpeningPart(relYear),
                NotSameDestCity(),
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralRice(cost.rice),
                AllowWar(),
                HasRouteWithEnemy(),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            val cost = getCost()
            return listOf(
                NotOpeningPart(relYear + 2),
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralCrew(),
                ReqGeneralRice(cost.rice),
            )
        }

    override fun getCost(): CommandCost {
        val rice = (general.crew / 100.0).roundToInt()
        return CommandCost(gold = 0, rice = rice)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    /**
     * 출병 실행:
     * - BFS shortest path to destCity, find candidate enemy cities (dist or dist+1)
     * - If nearest candidate is own nation → fallback to 이동 (alternative, handled by engine)
     * - Otherwise set city state=43,term=3 and trigger battle
     * - 병종숙련 += crew/100
     * - 500명 이상 & 훈련*사기 > 70*70 → inheritancePoint(active_action, +1)
     * - tryUniqueItemLottery after battle
     */
    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val finalTargetCityId = arg?.get("destCityID") as? Int
            ?: arg?.get("destCityId") as? Int
            ?: destCity?.id?.toInt() ?: 0
        val finalTargetCityName = destCity?.name ?: "알 수 없음"
        val attackerNationId = general.nationId

        // Resolve the actual attack target via BFS route-finding
        // The engine layer provides candidate cities through constraintEnv
        val resolvedCityId = resolveAttackTarget(finalTargetCityId, attackerNationId, rng)
        val resolvedCityName = getCityName(resolvedCityId?.toLong()) ?: finalTargetCityName
        val resolvedNationId = getCityNation(resolvedCityId?.toLong())

        // If resolved city belongs to own nation, this means no enemy on route → move instead
        // This shouldn't normally happen since HasRouteWithEnemy + getAlternativeCommand handles it,
        // but as a safety net:
        if (resolvedCityId != null && resolvedNationId == attackerNationId) {
            val josaRo = JosaUtil.pick(resolvedCityName, "로")
            if (finalTargetCityId == resolvedCityId) {
                pushLog("본국입니다. <G><b>${resolvedCityName}</b></>${josaRo} 이동합니다. <1>$date</>")
            } else {
                pushLog("가까운 경로에 적군 도시가 없습니다. <G><b>${resolvedCityName}</b></>${josaRo} 이동합니다. <1>$date</>")
            }
            pushHistoryLog("<G><b>${resolvedCityName}</b></>${josaRo} 이동합니다. <1>$date</>")
            pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 출병 경로를 변경해 <G><b>${resolvedCityName}</b></>${josaRo} 이동합니다.")
            return CommandResult(
                success = true,
                logs = logs,
                message = buildString {
                    append("""{"statChanges":{"cityId":"$resolvedCityId","experience":50,"leadershipExp":1}""")
                    append(""","alternative":"이동"}""")
                }
            )
        }

        val targetCityId = resolvedCityId ?: finalTargetCityId

        // Log if attacking a waypoint city rather than the final target
        if (resolvedCityId != null && resolvedCityId != finalTargetCityId) {
            val josaRo = JosaUtil.pick(finalTargetCityName, "로")
            val josaUl = JosaUtil.pick(resolvedCityName, "을")
            pushLog("<G><b>${finalTargetCityName}</b></>${josaRo} 가기 위해 <G><b>${resolvedCityName}</b></>${josaUl} 거쳐야 합니다. <1>$date</>")
        }
        pushHistoryLog("<G><b>${targetCityId}</b></> 방면으로 출병했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${finalTargetCityName}</b></> 방면으로 출병했습니다.")

        val cost = getCost()
        val dexGain = (general.crew / 100.0).roundToInt()

        // Inheritance point: 500명 이상, 훈련*사기 > 70*70
        val earnInheritance = general.crew > 500 &&
                general.train * general.atmos > 70 * 70

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"statChanges":{"rice":${-cost.rice}}""")
                append(""","dexChanges":{"crewType":${general.crewType},"amount":$dexGain}""")
                append(""","battleTriggered":true""")
                append(",\"targetCityId\":\"$targetCityId\"")
                append(""","cityStateUpdate":{"cityId":${targetCityId},"state":43,"term":3}""")
                if (earnInheritance) {
                    append(""","inheritancePoint":{"key":"active_action","amount":1}""")
                }
                append(""","tryUniqueLottery":true""")
                append("}")
            }
        )
    }

    /**
     * BFS route-finding to resolve the actual attack target city.
     * Legacy: searchDistanceListToDest + candidate selection logic in che_출병.php run().
     *
     * Steps:
     *  1. BFS from attacker's city to finalTargetCityId, restricted to allowed nations
     *  2. Collect cities at shortest distance (minDist) and minDist+1
     *  3. Among those, pick enemy cities as candidates
     *  4. If no enemy candidates at those distances, pick allied cities at minDist
     *  5. Randomly choose one candidate
     */
    private fun resolveAttackTarget(finalTargetCityId: Int, attackerNationId: Long, rng: Random): Int? {
        val adjacencyRaw = readMap(constraintEnv["mapAdjacency"]) ?: return null
        val cityNationByMapIdRaw = readMap(constraintEnv["cityNationByMapId"]) ?: return null
        val dbToMapIdRaw = readMap(constraintEnv["dbToMapId"]) ?: return null
        val mapToDbIdRaw = readMap(constraintEnv["mapToDbId"]) ?: return null

        val adjacency = mutableMapOf<Long, List<Long>>()
        adjacencyRaw.forEach { (k, v) ->
            val key = when (k) {
                is Number -> k.toLong()
                is String -> k.toLongOrNull()
                else -> null
            } ?: return@forEach
            val values = (v as? Iterable<*>)?.mapNotNull { elem ->
                when (elem) {
                    is Number -> elem.toLong()
                    is String -> elem.toLongOrNull()
                    else -> null
                }
            } ?: emptyList()
            adjacency[key] = values
        }

        val startMapCityId = when (val raw = dbToMapIdRaw[general.cityId] ?: dbToMapIdRaw[general.cityId.toString()]) {
            is Number -> raw.toLong()
            else -> return null
        }

        val atWarNationSet = when (val raw = constraintEnv["atWarNationIds"]) {
            is Set<*> -> raw.mapNotNull { (it as? Number)?.toLong() ?: (it as? String)?.toLongOrNull() }.toSet()
            is Iterable<*> -> raw.mapNotNull { (it as? Number)?.toLong() ?: (it as? String)?.toLongOrNull() }.toSet()
            else -> emptySet()
        }
        val allNationIds = cityNationByMapIdRaw.values.mapNotNull {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }.toSet()
        val allowedNationSet = allNationIds.filterTo(mutableSetOf()) { it !in atWarNationSet }
        allowedNationSet.add(attackerNationId)
        allowedNationSet.add(0L)

        val visited = mutableSetOf(startMapCityId)
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.addLast(startMapCityId to 0)

        val distanceList = sortedMapOf<Int, MutableList<Pair<Long, Long>>>()

        while (queue.isNotEmpty()) {
            val (curCity, dist) = queue.removeFirst()
            for (nextCity in adjacency[curCity].orEmpty()) {
                if (!visited.add(nextCity)) continue
                val nextNation = when (val raw = cityNationByMapIdRaw[nextCity] ?: cityNationByMapIdRaw[nextCity.toString()]) {
                    is Number -> raw.toLong()
                    else -> 0L
                }
                val nextDist = dist + 1
                distanceList.getOrPut(nextDist) { mutableListOf() }.add(nextCity to nextNation)

                if (nextNation in allowedNationSet) {
                    queue.addLast(nextCity to nextDist)
                }
            }
        }

        if (distanceList.isEmpty()) return null

        val candidateMapCities = mutableListOf<Long>()
        val minDist = distanceList.firstKey()

        for ((dist, cities) in distanceList) {
            if (dist > minDist + 1) break
            for ((mapCityId, nationId) in cities) {
                // Legacy parity: any city not owned by attacker is a valid target (including empty cities)
                if (nationId != attackerNationId) {
                    candidateMapCities.add(mapCityId)
                }
            }
            if (candidateMapCities.isNotEmpty()) break
        }

        if (candidateMapCities.isEmpty()) {
            for ((mapCityId, nationId) in distanceList[minDist].orEmpty()) {
                if (nationId == attackerNationId) {
                    candidateMapCities.add(mapCityId)
                }
            }
        }

        if (candidateMapCities.isEmpty()) return null

        val chosenMapCityId = candidateMapCities[rng.nextInt(candidateMapCities.size)]
        val dbCityId = when (val raw = mapToDbIdRaw[chosenMapCityId] ?: mapToDbIdRaw[chosenMapCityId.toString()]) {
            is Number -> raw.toInt()
            else -> return null
        }
        return dbCityId
    }

    private fun getCityName(cityId: Long?): String? {
        if (cityId == null) return null
        // Try to get from services
        return try {
            services?.cityRepository?.findById(cityId)?.orElse(null)?.name
        } catch (_: Exception) {
            null
        }
    }

    private fun getCityNation(cityId: Long?): Long {
        if (cityId == null) return 0L
        val cityNationById = readMap(constraintEnv["cityNationById"]) ?: return 0L
        val raw = cityNationById[cityId.toString()] ?: cityNationById[cityId] ?: return 0L
        return when (raw) {
            is Number -> raw.toLong()
            else -> 0L
        }
    }

    private fun readMap(raw: Any?): Map<*, *>? = raw as? Map<*, *>
}
