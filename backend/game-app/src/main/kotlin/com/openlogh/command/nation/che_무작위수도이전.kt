package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class che_무작위수도이전(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "무작위 수도 이전"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeLord(), SuppliedCity(),
        BeOpeningPart(env.year - env.startYear + 1)
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 1
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val oldCityId = n.capitalPlanetId

        // Prefer precomputed neutral city ids from game storage (useful for deterministic tests)
        val neutralCityIds = (env.gameStor["neutralCities"] as? Iterable<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            ?.toList()
            ?: emptyList()

        val destPlanet = if (neutralCityIds.isNotEmpty()) {
            val pickId = neutralCityIds[rng.nextInt(neutralCityIds.size)]
            services!!.planetRepository.findById(pickId)?.orElse(null)
        } else {
            // Fallback: find neutral cities with level 5-6
            val allCities = services!!.planetRepository.findBySessionId(env.sessionId) ?: emptyList()
            val neutralCities = allCities.filter { it.factionId == 0L && it.level in 5..6 }
            if (neutralCities.isEmpty()) {
                null
            } else {
                neutralCities[rng.nextInt(neutralCities.size)]
            }
        }

        if (destPlanet == null) {
            pushLog("이동할 수 있는 도시가 없습니다. <1>$date</>")
            return CommandResult(false, logs, "이동할 수 있는 도시가 없습니다")
        }

        // Set new capital city to nation ownership
        destPlanet.factionId = n.id
        n.capitalPlanetId = destPlanet.id
        services!!.planetRepository.save(destPlanet)

        // Decrement aux counter
        val canCount = (n.meta["can_무작위수도이전"] as? Number)?.toInt() ?: 0
        n.meta["can_무작위수도이전"] = canCount - 1

        // Release old capital to neutral
        if (oldCityId != null) {
            val oldCity = services!!.planetRepository.findById(oldCityId)?.orElse(null)
            if (oldCity != null) {
                oldCity.factionId = 0
                oldCity.frontState = 0
                oldCity.officerSet = 0
                services!!.planetRepository.save(oldCity)
            }
        }

        // Move all nation generals to new capital
        val nationGenerals = services!!.officerRepository.findBySessionIdAndFactionId(env.sessionId, n.id) ?: emptyList()
        for (g in nationGenerals) {
            g.planetId = destPlanet.id
        }

        general.experience += 5 * (getPreReqTurn() + 1)
        general.dedication += 5 * (getPreReqTurn() + 1)

        pushLog("<G><b>${destPlanet.name}</b></>으로 국가를 옮겼습니다. <1>$date</>")
        pushHistoryLog("<G><b>${destPlanet.name}</b></>으로 국가를 옮겼습니다. <1>$date</>")
        pushGlobalHistoryLog("<S><b>【천도】</b></><D><b>${n.name}</b></>${pickJosa(n.name, "이")} <G><b>${destPlanet.name}</b></>으로 수도를 옮겼습니다.")
        return CommandResult(true, logs)
    }
}
