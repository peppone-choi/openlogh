package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.LastTurn
import com.openlogh.command.NationCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import com.openlogh.service.CityService.Companion.EXPAND_CITY_COST_COEF
import com.openlogh.service.CityService.Companion.EXPAND_CITY_DEFAULT_COST
import com.openlogh.service.CityService.Companion.EXPAND_CITY_DEVEL_INCREASE
import com.openlogh.service.CityService.Companion.EXPAND_CITY_POP_INCREASE
import com.openlogh.service.CityService.Companion.EXPAND_CITY_WALL_INCREASE
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class che_증축(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : NationCommand(general, env, arg) {

    override val actionName = "증축"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCostAmount()
            val baseGold = (env.gameStor["baseGold"] as? Number)?.toInt()
                ?: (env.gameStor["basegold"] as? Number)?.toInt() ?: 0
            val baseRice = (env.gameStor["baseRice"] as? Number)?.toInt()
                ?: (env.gameStor["baserice"] as? Number)?.toInt() ?: 2000
            val levelConstraints = buildList {
                val destLevel = destCity?.level?.toInt() ?: city?.level?.toInt()
                if (destLevel != null && destLevel <= 3) {
                    add(AlwaysFail("수진, 진, 관문에서는 불가능합니다."))
                }
                if (destLevel != null && destLevel >= 8) {
                    add(AlwaysFail("더이상 증축할 수 없습니다."))
                }
            }
            return listOf(
                OccupiedCity(),
                BeChief(),
                SuppliedCity(),
            ) + levelConstraints + listOf(
                ReqNationGold(baseGold + cost),
                ReqNationRice(baseRice + cost),
            )
        }

    private fun getCostAmount(): Int {
        return env.develCost * EXPAND_CITY_COST_COEF + EXPAND_CITY_DEFAULT_COST
    }

    override fun getCost(): CommandCost {
        val amount = getCostAmount()
        return CommandCost(gold = amount, rice = amount)
    }

    override fun getPreReqTurn() = 5
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val c = destCity ?: city ?: return CommandResult(false, logs, "수도 도시 정보를 찾을 수 없습니다")

        if (c.level < 4.toShort()) {
            return CommandResult(false, logs, "수진, 진, 관문에서는 불가능합니다.")
        }
        if (c.level >= 8.toShort()) {
            return CommandResult(false, logs, "더이상 증축할 수 없습니다.")
        }

        val date = formatDate()
        val cost = getCost()

        n.gold -= cost.gold
        n.rice -= cost.rice

        c.level = (c.level + 1).toShort()
        c.popMax += EXPAND_CITY_POP_INCREASE
        c.agriMax += EXPAND_CITY_DEVEL_INCREASE
        c.commMax += EXPAND_CITY_DEVEL_INCREASE
        c.secuMax += EXPAND_CITY_DEVEL_INCREASE
        c.defMax += EXPAND_CITY_WALL_INCREASE
        c.wallMax += EXPAND_CITY_WALL_INCREASE

        val currentCapSet = (n.meta["capSet"] as? Number)?.toInt() ?: 0
        n.meta["capSet"] = currentCapSet + 1

        val expDed = 5 * (getPreReqTurn() + 1)
        general.experience += expDed
        general.dedication += expDed

        val inheritMeta = getOrCreateMutableStringAnyMap(general.meta, "inheritancePoints")
        inheritMeta["active_action"] = ((inheritMeta["active_action"] as? Number)?.toInt() ?: 0) + 1

        val generalName = general.name
        val nationName = n.name
        val josaUl = JosaUtil.pick(c.name, "을")
        val josaYi = JosaUtil.pick(generalName, "이")
        val josaYiNation = JosaUtil.pick(nationName, "이")

        pushLog("<G><b>${c.name}</b></>${josaUl} 증축했습니다. <1>$date</>")
        pushHistoryLog("<G><b>${c.name}</b></>${josaUl} <M>증축</>")
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <G><b>${c.name}</b></>${josaUl} <M>증축</>")
        pushGlobalActionLog("<Y>${generalName}</>${josaYi} <G><b>${c.name}</b></>${josaUl} <M>증축</>하였습니다.")
        pushGlobalHistoryLog("<C><b>【증축】</b></><D><b>${nationName}</b></>${josaYiNation} <G><b>${c.name}</b></>${josaUl} <M>증축</>하였습니다.")

        // Reset lastTurn (completed)
        general.lastTurn = LastTurn(actionName, arg, 0).toMap()

        return CommandResult(true, logs)
    }

    private fun getOrCreateMutableStringAnyMap(container: MutableMap<String, Any>, key: String): MutableMap<String, Any> {
        val current = container[key]
        val typed = mutableMapOf<String, Any>()
        if (current is Map<*, *>) {
            current.forEach { (k, v) ->
                if (k is String && v != null) {
                    typed[k] = v
                }
            }
        }
        container[key] = typed
        return typed
    }
}
