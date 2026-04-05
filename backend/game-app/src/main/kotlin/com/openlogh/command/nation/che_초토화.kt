package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

private const val POST_REQ_TURN = 24
private const val PRE_REQ_TURN = 2

class che_초토화(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "초토화"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), OccupiedDestCity(), BeChief(),
        SuppliedCity(), SuppliedDestCity(),
        ReqNationValue("surlimit", "제한 턴", "==", 0, "외교제한 턴이 남아있습니다."),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN
    override fun getPostReqTurn() = POST_REQ_TURN

    /**
     * Calculate resource return amount based on city development.
     * Formula: pop/5 * product((res - max*0.5)/max + 0.8) for agri/comm/secu
     */
    private fun calcReturnAmount(): Int {
        val dc = destPlanet ?: return 0
        var amount = dc.population.toDouble() / 5.0
        val pairs = listOf(
            dc.production to dc.productionMax,
            dc.commerce to dc.commerceMax,
            dc.security to dc.securityMax,
        )
        for ((current, maxVal) in pairs) {
            if (maxVal <= 0) continue
            amount *= (current.toDouble() - maxVal * 0.5) / maxVal + 0.8
        }
        return max(0, floor(amount).toInt())
    }

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dc = destPlanet ?: return CommandResult(false, logs, "대상 도시 정보를 찾을 수 없습니다")

        // Check: not capital
        if (n.capitalPlanetId == dc.id) {
            return CommandResult(false, logs, "수도입니다.")
        }

        val returnAmount = calcReturnAmount()

        // Reduce experience of all officers (level >= 5) by 10% (excluding self)
        val nationGenerals = services?.officerRepository?.findByFactionId(n.id) ?: emptyList()
        for (gen in nationGenerals) {
            if (gen.id != general.id && gen.officerLevel >= 5) {
                gen.experience = (gen.experience * 0.9).toInt()
                services?.officerRepository?.save(gen)
            }
            // Increase betray for all except self
            if (gen.id != general.id) {
                gen.betray = (gen.betray + 1).toShort()
                services?.officerRepository?.save(gen)
            }
        }
        general.betray = (general.betray + 1).toShort()

        // Apply experience penalty to self, then add exp/ded
        general.experience = (general.experience * 0.9).toInt()
        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        // Reduce city values: max(max*0.1, current*0.2), wall uses 0.5
        dc.approval = max(50f, dc.approval)
        dc.population = max((dc.populationMax * 0.1).toInt(), (dc.population * 0.2).toInt())
        dc.production = max((dc.productionMax * 0.1).toInt(), (dc.production * 0.2).toInt())
        dc.commerce = max((dc.commerceMax * 0.1).toInt(), (dc.commerce * 0.2).toInt())
        dc.security = max((dc.securityMax * 0.1).toInt(), (dc.security * 0.2).toInt())
        dc.orbitalDefense = max((dc.orbitalDefenseMax * 0.1).toInt(), (dc.orbitalDefense * 0.2).toInt())
        dc.fortress = max((dc.fortressMax * 0.1).toInt(), (dc.fortress * 0.5).toInt())
        dc.factionId = 0
        dc.frontState = 0
        dc.conflict = mutableMapOf()

        // Return gold/rice to nation
        n.funds += returnAmount
        n.supplies += returnAmount

        // Apply surlimit
        val currentSurlimit = (n.meta["surlimit"] as? Number)?.toInt() ?: 0
        n.meta["surlimit"] = currentSurlimit + POST_REQ_TURN

        // Track 특성초토화 if level >= 8
        if (dc.level >= 8) {
            val current = (n.meta["did_특성초토화"] as? Number)?.toInt() ?: 0
            n.meta["did_특성초토화"] = current + 1
        }

        pushLog("<G><b>${dc.name}</b></>을 초토화했습니다. <1>$date</>")
        pushHistoryLog("<G><b>${dc.name}</b></>을 초토화했습니다. <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${dc.name}</b></>을 초토화했습니다.")
        pushGlobalHistoryLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <G><b>${dc.name}</b></>을 초토화했습니다.")
        return CommandResult(true, logs)
    }
}
