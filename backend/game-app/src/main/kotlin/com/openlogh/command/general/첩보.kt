package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class 첩보(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "첩보"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                NotOccupiedDestCity(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                ReqGeneralGold(cost.funds),
                ReqGeneralRice(cost.supplies),
            )
        }

    override fun getCost(): CommandCost {
        val cost = env.develCost * 3
        return CommandCost(funds = cost, supplies = cost)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val dc = destPlanet!!
        val destCityName = dc.name
        val destCityId = dc.id

        // Legacy: distance-based info levels
        val distance = calculateDistanceToCity(destCityId)

        // Legacy: global log visible to everyone
        pushGlobalLog("누군가가 <G><b>${destCityName}</b></>을 살피는 것 같습니다.")
        pushHistoryLog("<G><b>${destCityName}</b></>에 첩보를 시도했습니다. <1>$date</>")

        // Gather city info
        val popText = String.format("%,d", dc.population)
        val trustText = String.format("%.1f", dc.approval)
        val agriText = String.format("%,d", dc.production)
        val commText = String.format("%,d", dc.commerce)
        val secuText = String.format("%,d", dc.security)
        val defText = String.format("%,d", dc.orbitalDefense)
        val wallText = String.format("%,d", dc.fortress)

        val cityGeneralCount = getDestCityGeneralCount()
        val totalCrew = getDestCityTotalCrew()

        val cityBrief = "【<G>${destCityName}</>】주민:${popText}, 민심:${trustText}, 장수:${cityGeneralCount}, 병력:${totalCrew}"

        when {
            distance <= 1 -> {
                pushLog("<G><b>${destCityName}</b></>의 정보를 많이 얻었습니다. <1>$date</>")
                pushLog(cityBrief)
                pushLog("【<M>첩보</>】농업:${agriText}, 상업:${commText}, 치안:${secuText}, 수비:${defText}, 성벽:${wallText}")
                // Crew type breakdown
                val crewTypeSummary = getDestCityCrewTypeSummary()
                if (crewTypeSummary.isNotEmpty()) {
                    pushLog("【<S>병종</>】 $crewTypeSummary")
                }
                // Tech comparison
                val techComparison = getTechComparison()
                if (techComparison != null) {
                    pushLog(techComparison)
                }
            }
            distance == 2 -> {
                pushLog("<G><b>${destCityName}</b></>의 정보를 어느 정도 얻었습니다. <1>$date</>")
                pushLog(cityBrief)
                pushLog("【<M>첩보</>】농업:${agriText}, 상업:${commText}, 치안:${secuText}, 수비:${defText}, 성벽:${wallText}")
            }
            else -> {
                pushLog("<G><b>${destCityName}</b></>의 소문만 들을 수 있었습니다. <1>$date</>")
                pushLog(cityBrief)
            }
        }

        // Legacy: random exp/ded ranges
        val exp = rng.nextInt(1, 101)
        val ded = rng.nextInt(1, 71)
        val cost = getCost()

        // Legacy: inheritance point 0.5 (unique to 첩보)
        // Legacy: spy info update on nation
        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.funds},"rice":${-cost.supplies},"experience":$exp,"dedication":$ded,"leadershipExp":1,"inheritancePoint":0.5},"spyResult":{"destCityId":$destCityId,"distance":$distance},"nationChanges":{"spyUpdate":{"$destCityId":3}}}"""
        )
    }

    /**
     * Calculate BFS distance from general's city to target city.
     * Returns 3 if not reachable within range.
     */
    private fun calculateDistanceToCity(targetCityId: Long): Int {
        val adjacency = readAdjacency(env.gameStor["mapAdjacency"])
        if (adjacency.isEmpty()) return 3

        val startCityId = general.planetId
        if (startCityId == targetCityId) return 0

        val visited = mutableSetOf(startCityId)
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.addLast(startCityId to 0)

        while (queue.isNotEmpty()) {
            val (cityId, distance) = queue.removeFirst()
            if (distance >= 2) continue
            for (next in adjacency[cityId].orEmpty()) {
                if (next == targetCityId) return distance + 1
                if (!visited.add(next)) continue
                queue.addLast(next to (distance + 1))
            }
        }

        return 3
    }

    /**
     * Get tech comparison text between own nation and dest nation.
     * Legacy: 압도/우위/대등/열위/미미
     */
    private fun getTechComparison(): String? {
        val destFaction = destFaction ?: return null
        val ownNation = nation ?: return null
        if (destFaction.id == 0L || ownNation.id == 0L) return null

        val destTech = destFaction.techLevel.toInt()
        val ownTech = ownNation.techLevel.toInt()
        val techDiff = destTech - ownTech

        val techText = when {
            techDiff >= 1000 -> "<M>↑</>압도"
            techDiff >= 250 -> "<Y>▲</>우위"
            techDiff >= -250 -> "<W>↕</>대등"
            techDiff >= -1000 -> "<G>▼</>열위"
            else -> "<C>↓</>미미"
        }
         val destNationName = destFaction.name
         return "【<Y>${destNationName}</>】아국대비기술:${techText}"
    }

    private fun readAdjacency(raw: Any?): Map<Long, List<Long>> {
        if (raw !is Map<*, *>) return emptyMap()
        val result = mutableMapOf<Long, List<Long>>()
        raw.forEach { (k, v) ->
            val key = when (k) {
                is Number -> k.toLong()
                is String -> k.toLongOrNull()
                else -> null
            } ?: return@forEach
            val values = when (v) {
                is Iterable<*> -> v.mapNotNull { elem ->
                    when (elem) {
                        is Number -> elem.toLong()
                        is String -> elem.toLongOrNull()
                        else -> null
                    }
                }
                else -> emptyList()
            }
            result[key] = values
        }
        return result
    }
}
