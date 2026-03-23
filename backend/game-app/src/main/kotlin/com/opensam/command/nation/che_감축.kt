package com.opensam.command.nation

import com.opensam.command.CommandCost
import com.opensam.command.CommandEnv
import com.opensam.command.CommandResult
import com.opensam.command.NationCommand
import com.opensam.command.constraint.*
import com.opensam.entity.General
import com.opensam.service.CityService.Companion.EXPAND_CITY_COST_COEF
import com.opensam.service.CityService.Companion.EXPAND_CITY_DEFAULT_COST
import com.opensam.service.CityService.Companion.EXPAND_CITY_DEVEL_INCREASE
import com.opensam.service.CityService.Companion.EXPAND_CITY_POP_INCREASE
import com.opensam.service.CityService.Companion.EXPAND_CITY_WALL_INCREASE
import kotlin.math.max
import kotlin.random.Random

class che_감축(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

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
            val capitalCityId = n.capitalCityId
                ?: return CommandResult(false, logs, "방랑상태에서는 불가능합니다.")
            services?.cityRepository?.findById(capitalCityId)?.orElse(null)
                ?: return CommandResult(false, logs, "수도 정보를 찾을 수 없습니다")
        }
        val date = formatDate()

        if (capitalCity.level <= 1) {
            return CommandResult(false, logs, "더이상 감축할 수 없습니다.")
        }

        capitalCity.level = (capitalCity.level - 1).toShort()
        // PHP reduces all 6 stats and their maxes
        capitalCity.pop = max(capitalCity.pop - EXPAND_CITY_POP_INCREASE, 0)
        capitalCity.popMax -= EXPAND_CITY_POP_INCREASE
        capitalCity.agri = max(capitalCity.agri - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.agriMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.comm = max(capitalCity.comm - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.commMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.secu = max(capitalCity.secu - EXPAND_CITY_DEVEL_INCREASE, 0)
        capitalCity.secuMax -= EXPAND_CITY_DEVEL_INCREASE
        capitalCity.def = max(capitalCity.def - EXPAND_CITY_WALL_INCREASE, 0)
        capitalCity.defMax -= EXPAND_CITY_WALL_INCREASE
        capitalCity.wall = max(capitalCity.wall - EXPAND_CITY_WALL_INCREASE, 0)
        capitalCity.wallMax -= EXPAND_CITY_WALL_INCREASE

        // PHP refunds the cost back to the nation (gold/rice +)
        val cost = getCost()
        n.gold += cost.gold
        n.rice += cost.rice

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
