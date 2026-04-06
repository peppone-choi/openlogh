package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClassType
import com.openlogh.model.StatCategory
import com.openlogh.model.UnitType
import com.openlogh.model.PositionCard
import kotlin.random.Random

/**
 * 할당 (Allocation) -- gin7: 兵站 - 割当
 *
 * Transfer units (ships, ground troops, crew) and supplies from planet warehouse to fleet warehouse.
 *
 * Authority table:
 * - 통수본부작전1과장 (OPS_1ST_CHIEF): fleet only
 * - 통수본부작전2과장 (OPS_2ND_CHIEF): patrol, transport, ground only
 * - 통합작전본부3차장 (JOINT_OPS_VICE_3RD): all unit types
 * - Fleet/Fortress/Planet commanders with LOGISTICS group
 *
 * Blocked during 재편성/보충 execution.
 * 재편성/보충/반출입 blocked during 할당 execution.
 */
class 할당(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "할당"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            NotBeNeutral(),
            OccupiedCity(),
        )

    override fun getCost() = CommandCost(funds = 0, supplies = 0)
    override fun getCommandPointCost() = 1
    override fun getCommandPoolType() = StatCategory.MCP
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 1
    override fun getDuration() = 600

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val targetFleetId = (arg?.get("fleetId") as? Number)?.toLong()
            ?: return CommandResult(false, listOf("<R>할당</> 실패 - 대상 부대를 지정해야 합니다."))

        val c = city ?: return CommandResult(false, listOf("<R>할당</> 실패 - 행성 정보를 불러올 수 없습니다."))

        // Determine allowed unit types based on position cards
        val allowedUnitTypes = resolveAllowedUnitTypes()
        if (allowedUnitTypes.isEmpty()) {
            return CommandResult(false, listOf("<R>할당</> 실패 - 할당 권한이 없습니다."))
        }

        // Parse ship allocations
        val shipAllocations = mutableMapOf<ShipClassType, Int>()
        for (sc in ShipClassType.entries) {
            val amount = (arg?.get(sc.warehouseColumn) as? Number)?.toInt() ?: 0
            if (amount > 0) shipAllocations[sc] = amount
        }

        // Parse crew allocations
        val crewAllocations = mutableMapOf<CrewProficiency, Int>()
        for (cp in CrewProficiency.entries) {
            val amount = (arg?.get(cp.warehouseColumn) as? Number)?.toInt() ?: 0
            if (amount > 0) crewAllocations[cp] = amount
        }

        val supplyAmount = (arg?.get("supplies") as? Number)?.toInt() ?: 0
        val missileAmount = (arg?.get("missiles") as? Number)?.toInt() ?: 0

        if (shipAllocations.isEmpty() && crewAllocations.isEmpty() && supplyAmount <= 0 && missileAmount <= 0) {
            return CommandResult(false, listOf("<R>할당</> 실패 - 할당할 자원을 지정해야 합니다."))
        }

        // Build the result message with allocation details for CommandResultApplicator
        val allocDetail = buildString {
            append("""{"allocation":{""")
            append(""""sessionId":${env.sessionId}""")
            append(""","planetId":${c.id}""")
            append(""","fleetId":$targetFleetId""")

            // Ships
            if (shipAllocations.isNotEmpty()) {
                append(""","ships":{""")
                append(shipAllocations.entries.joinToString(",") { """"${it.key.warehouseColumn}":${it.value}""" })
                append("}")
            }

            // Crew
            if (crewAllocations.isNotEmpty()) {
                append(""","crew":{""")
                append(crewAllocations.entries.joinToString(",") { """"${it.key.warehouseColumn}":${it.value}""" })
                append("}")
            }

            if (supplyAmount > 0) append(""","supplies":$supplyAmount""")
            if (missileAmount > 0) append(""","missiles":$missileAmount""")

            // Allowed unit types for validation
            append(""","allowedUnitTypes":[""")
            append(allowedUnitTypes.joinToString(",") { """"${it.name}"""" })
            append("]")

            append("""},"statChanges":{"experience":40,"administrationExp":1}}""")
        }

        val summary = buildAllocSummary(shipAllocations, crewAllocations, supplyAmount, missileAmount)
        pushLog("행성 창고에서 부대 창고로 $summary 할당했습니다. <1>$date</>")
        pushHistoryLog("$summary 할당. <1>$date</>")

        return CommandResult(
            success = true,
            logs = logs,
            message = allocDetail,
        )
    }

    /**
     * Resolve which unit types the officer can allocate to based on position cards.
     */
    private fun resolveAllowedUnitTypes(): Set<UnitType> {
        val positionCardsRaw = general.meta["positionCards"]
        val heldCards = parsePositionCards(positionCardsRaw)
        val allowed = mutableSetOf<UnitType>()

        for (card in heldCards) {
            when (card) {
                // Empire: 통수본부작전1과장 -> fleet only
                PositionCard.OPS_1ST_CHIEF -> {
                    allowed.add(UnitType.FLEET)
                }
                // Empire: 통수본부작전2과장 -> patrol, transport, ground
                PositionCard.OPS_2ND_CHIEF -> {
                    allowed.addAll(listOf(UnitType.PATROL, UnitType.TRANSPORT, UnitType.GROUND))
                }
                // Alliance: 통합작전본부3차장 -> all unit types
                PositionCard.JOINT_OPS_VICE_3RD -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                // Alliance: 통합작전본부1/2차장 -> all unit types
                PositionCard.JOINT_OPS_VICE_1ST, PositionCard.JOINT_OPS_VICE_2ND -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                // Fleet/Fortress/Planet commanders -> their own unit type
                PositionCard.FLEET_COMMANDER, PositionCard.FLEET_ADJUTANT -> {
                    allowed.add(UnitType.FLEET)
                }
                PositionCard.TRANSPORT_COMMANDER -> {
                    allowed.add(UnitType.TRANSPORT)
                }
                PositionCard.FORTRESS_COMMANDER, PositionCard.FORTRESS_SECRETARY -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                PositionCard.PLANET_GOVERNOR -> {
                    allowed.addAll(listOf(UnitType.GARRISON))
                }
                // Logistics HQ (alliance)
                PositionCard.LOGISTICS_CHIEF, PositionCard.LOGISTICS_VICE, PositionCard.LOGISTICS_STAFF -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                // Defense dept (alliance)
                PositionCard.DEFENSE_DEPT_CHIEF -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                // Science/Tech director (empire)
                PositionCard.SCIENCE_TECH_DIRECTOR -> {
                    allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                }
                else -> {
                    // Check if card has LOGISTICS command group
                    if (com.openlogh.model.CommandGroup.LOGISTICS in card.commandGroups) {
                        allowed.addAll(UnitType.entries.filter { it != UnitType.SOLO })
                    }
                }
            }
        }

        return allowed
    }

    private fun parsePositionCards(raw: Any?): List<PositionCard> {
        if (raw == null) return PositionCard.defaults()
        val list = raw as? List<*> ?: return PositionCard.defaults()
        return list.mapNotNull { item ->
            when (item) {
                is String -> try { PositionCard.valueOf(item) } catch (_: Exception) { null }
                else -> null
            }
        }.ifEmpty { PositionCard.defaults() }
    }

    private fun buildAllocSummary(
        ships: Map<ShipClassType, Int>,
        crew: Map<CrewProficiency, Int>,
        supplies: Int,
        missiles: Int,
    ): String {
        val parts = mutableListOf<String>()
        for ((sc, amount) in ships) {
            parts.add("${sc.displayName} ${amount}유닛")
        }
        for ((cp, amount) in crew) {
            parts.add("${cp.displayName} 승조원 ${amount}명")
        }
        if (supplies > 0) parts.add("물자 $supplies")
        if (missiles > 0) parts.add("미사일 $missiles")
        return parts.joinToString(", ")
    }
}
