package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_COST_COEF
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_DEFAULT_COST
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_DEVEL_INCREASE
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_POP_INCREASE
import com.openlogh.service.PlanetService.Companion.EXPAND_CITY_WALL_INCREASE
import kotlin.math.max
import kotlin.random.Random

class che_감축(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "감축"

    override val fullConditionConstraints = listOf(
        OccupiedCity(),
        BeChief(),
        SuppliedCity(),
        // Capital level > 4 and > original level checked in run()
    )

    // PHP: amount = develcost * expandCityCostCoef + expandCityDefaultCost / 2
    private fun getCostAmount(): Int = env.develCost * EXPAND_CITY_COST_COEF + EXPAND_CITY_DEFAULT_COST / 2

    override fun getCost(): CommandCost {
        val amount = getCostAmount()
        return CommandCost(gold = amount, rice = amount)
    }

    override fun getPreReqTurn() = 5
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val capitalCity = city ?: run {
            val capitalCityId = n.capitalPlanetId
                ?: return CommandResult(false, logs, "방랑상태에서는 불가능합니다.")
            services?.planetRepository?.findById(capitalCityId)?.orElse(null)
                ?: return CommandResult(false, logs, "수도 정보를 찾을 수 없습니다")
        }
        val date = formatDate()

        if (capitalCity.level <= 1) {
            return CommandResult(false, logs, "더이상 감축할 수 없습니다.")
        }

        capitalCity.level = (capitalCity.level - 1).toShort()
        // PHP reduces all 6 stats and their maxes
        capitalCity.population = max(capitalCity.population - EXPAND_CITY_POP_INCREASE, 0)
        capitalCity.populationMax -= EXPAND_CITY_POP_INCREASE
        capitalCity.production = max(capitalCity.production - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.productionMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.commerce = max(capitalCity.commerce - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.commerceMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.security = max(capitalCity.security - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.securityMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.orbitalDefense = max(capitalCity.orbitalDefense - EXPAND_CITY_WALL_INCREASE, 0)
        capitalCity.orbitalDefenseMax -= EXPAND_CITY_WALL_INCREASE
        capitalCity.fortress = max(capitalCity.fortress - EXPAND_CITY_WALL_INCREASE, 0)
        capitalCity.fortressMax -= EXPAND_CITY_WALL_INCREASE

        // PHP refunds the cost back to the nation (gold/rice +)
        val cost = getCost()
        n.funds += cost.funds
        n.supplies += cost.supplies

        // Increment capset (nation meta)
        val capset = (n.meta["capset"] as? Number)?.toInt() ?: 0
        n.meta["capset"] = capset + 1

        general.experience += 5 * (getPreReqTurn() + 1)
        general.dedication += 5 * (getPreReqTurn() + 1)

        pushLog("<G><b>${capitalCity.name}</b></>을 감축했습니다. <1>$date</>")
        pushHistoryLog("<G><b>${capitalCity.name}</b></>을 감축했습니다. <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${capitalCity.name}</b></>을 감축했습니다.")

        return CommandResult(true, logs)
    }
}
