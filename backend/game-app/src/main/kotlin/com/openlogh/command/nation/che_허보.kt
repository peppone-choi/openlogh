package com.openlogh.command.nation

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.FactionCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val PRE_REQ_TURN = 1
private const val DEFAULT_GLOBAL_DELAY: Short = 9

class che_허보(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : FactionCommand(general, env, arg) {

    override val actionName = "허보"

    override val fullConditionConstraints = listOf(
        OccupiedCity(), BeChief(),
        NotNeutralDestCity(), NotOccupiedDestCity(),
        AvailableStrategicCommand()
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = PRE_REQ_TURN
    override fun getPostReqTurn() = 20

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val n = nation ?: return CommandResult(false, logs, "국가 정보를 찾을 수 없습니다")
        val dc = destPlanet ?: return CommandResult(false, logs, "대상 도시 정보를 찾을 수 없습니다")

        val expDed = 5 * (PRE_REQ_TURN + 1)
        general.experience += expDed
        general.dedication += expDed

        // Set strategic command limit
        n.strategicCmdLimit = DEFAULT_GLOBAL_DELAY.toShort()

        // Move enemy generals at destPlanet to random supply cities of their nation
        val enemyGenerals = services?.officerRepository?.findByPlanetId(dc.id)
            ?.filter { it.factionId == dc.factionId } ?: emptyList()
        val enemySupplyCities = services?.planetRepository?.findByFactionId(dc.factionId)
            ?.filter { it.supplyState > 0 && it.id != dc.id } ?: emptyList()

        var moved = 0
        for (gen in enemyGenerals) {
            if (enemySupplyCities.isEmpty()) continue
            val targetCity = enemySupplyCities[rng.nextInt(enemySupplyCities.size)]
            gen.planetId = targetCity.id
            if (gen.fleetId != gen.id) {
                gen.fleetId = 0
            }
            services?.officerRepository?.save(gen)
            moved++
        }

        pushLog("$actionName 발동! <1>$date</>")
        pushHistoryLog("$actionName 발동! <1>$date</>")
        pushGlobalLog("<Y>${general.name}</>${pickJosa(general.name, "이")} $actionName 전략을 발동했습니다.")
        return CommandResult(true, logs)
    }
}
