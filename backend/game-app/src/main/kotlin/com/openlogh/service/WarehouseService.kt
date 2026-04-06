package com.openlogh.service

import com.openlogh.entity.FleetWarehouse
import com.openlogh.entity.PlanetWarehouse
import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClassType
import com.openlogh.model.UnitType
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.FleetWarehouseRepository
import com.openlogh.repository.PlanetWarehouseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Warehouse service for logistics operations.
 * Manages planet warehouses (행성창고) and fleet warehouses (부대창고).
 */
@Service
class WarehouseService(
    private val planetWarehouseRepository: PlanetWarehouseRepository,
    private val fleetWarehouseRepository: FleetWarehouseRepository,
    private val fleetRepository: FleetRepository,
) {
    // ===== Planet Warehouse =====

    /** Get or create a planet warehouse for the given session and planet. */
    fun getOrCreatePlanetWarehouse(sessionId: Long, planetId: Long): PlanetWarehouse {
        return planetWarehouseRepository.findBySessionIdAndPlanetId(sessionId, planetId)
            ?: planetWarehouseRepository.save(
                PlanetWarehouse(sessionId = sessionId, planetId = planetId)
            )
    }

    /** Get planet warehouse, returning null if not found. */
    fun getPlanetWarehouse(sessionId: Long, planetId: Long): PlanetWarehouse? {
        return planetWarehouseRepository.findBySessionIdAndPlanetId(sessionId, planetId)
    }

    /** Get all planet warehouses with shipyards in a session. */
    fun getShipyardPlanets(sessionId: Long): List<PlanetWarehouse> {
        return planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(sessionId)
    }

    // ===== Fleet Warehouse =====

    /** Get or create a fleet warehouse for the given session and fleet. */
    fun getOrCreateFleetWarehouse(sessionId: Long, fleetId: Long): FleetWarehouse {
        return fleetWarehouseRepository.findBySessionIdAndFleetId(sessionId, fleetId)
            ?: fleetWarehouseRepository.save(
                FleetWarehouse(sessionId = sessionId, fleetId = fleetId)
            )
    }

    /** Get fleet warehouse, returning null if not found. */
    fun getFleetWarehouse(sessionId: Long, fleetId: Long): FleetWarehouse? {
        return fleetWarehouseRepository.findBySessionIdAndFleetId(sessionId, fleetId)
    }

    // ===== Allocation (할당): Planet -> Fleet =====

    /**
     * Allocate ships and crew from planet warehouse to fleet warehouse.
     *
     * @param allowedUnitTypes which unit types the caller has authority to allocate to
     * @return description of what was allocated
     */
    @Transactional
    fun allocate(
        sessionId: Long,
        planetId: Long,
        fleetId: Long,
        shipAllocations: Map<ShipClassType, Int>,
        crewAllocations: Map<CrewProficiency, Int>,
        supplyAmount: Int,
        missileAmount: Int,
        allowedUnitTypes: Set<UnitType>,
    ): AllocationResult {
        // Validate fleet type against authority
        val fleet = fleetRepository.findById(fleetId).orElse(null)
            ?: return AllocationResult(false, "부대를 찾을 수 없습니다.")

        val fleetUnitType = UnitType.valueOf(fleet.unitType)
        if (fleetUnitType !in allowedUnitTypes) {
            return AllocationResult(
                false,
                "${fleetUnitType.description}에 대한 할당 권한이 없습니다."
            )
        }

        // Verify fleet is stationed at this planet
        if (fleet.planetId != planetId) {
            return AllocationResult(false, "부대가 해당 행성에 주둔하고 있지 않습니다.")
        }

        val pw = getOrCreatePlanetWarehouse(sessionId, planetId)
        val fw = getOrCreateFleetWarehouse(sessionId, fleetId)

        val transfers = mutableListOf<String>()

        // Transfer ships
        for ((shipClass, requested) in shipAllocations) {
            if (requested <= 0) continue
            val actual = pw.removeShips(shipClass, requested)
            if (actual > 0) {
                fw.addShips(shipClass, actual)
                transfers.add("${shipClass.displayName} ${actual}유닛")
            }
        }

        // Transfer crew
        for ((proficiency, requested) in crewAllocations) {
            if (requested <= 0) continue
            val actual = pw.removeCrew(proficiency, requested)
            if (actual > 0) {
                fw.addCrew(proficiency, actual)
                transfers.add("${proficiency.displayName} 승조원 ${actual}명")
            }
        }

        // Transfer supplies
        if (supplyAmount > 0) {
            val actual = supplyAmount.coerceAtMost(pw.supplies)
            if (actual > 0) {
                pw.supplies -= actual
                fw.supplies += actual
                transfers.add("물자 $actual")
            }
        }

        // Transfer missiles
        if (missileAmount > 0) {
            val actual = missileAmount.coerceAtMost(pw.missiles)
            if (actual > 0) {
                pw.missiles -= actual
                fw.missiles += actual
                transfers.add("미사일 $actual")
            }
        }

        if (transfers.isEmpty()) {
            return AllocationResult(false, "할당할 자원이 부족합니다.")
        }

        pw.touch()
        fw.touch()
        planetWarehouseRepository.save(pw)
        fleetWarehouseRepository.save(fw)

        return AllocationResult(true, transfers.joinToString(", ") + " 할당 완료")
    }

    // ===== Reorganization (재편성): Fleet <-> Fleet Warehouse =====

    /**
     * Move units between fleet's active roster and its fleet warehouse.
     * Only fleet commander can execute.
     *
     * @param toWarehouse true = fleet -> warehouse, false = warehouse -> fleet
     */
    @Transactional
    fun reorganize(
        sessionId: Long,
        fleetId: Long,
        shipClass: ShipClassType,
        amount: Int,
        toWarehouse: Boolean,
    ): AllocationResult {
        val fw = getOrCreateFleetWarehouse(sessionId, fleetId)
        val fleet = fleetRepository.findById(fleetId).orElse(null)
            ?: return AllocationResult(false, "부대를 찾을 수 없습니다.")

        if (amount <= 0) {
            return AllocationResult(false, "이동할 수량은 1 이상이어야 합니다.")
        }

        if (toWarehouse) {
            // Fleet -> Warehouse: reduce fleet active units
            if (fleet.currentUnits < amount) {
                return AllocationResult(false, "부대에 충분한 유닛이 없습니다.")
            }
            fleet.currentUnits -= amount
            fw.addShips(shipClass, amount)
        } else {
            // Warehouse -> Fleet: add to fleet active units
            val available = fw.getShipCount(shipClass)
            if (available < amount) {
                return AllocationResult(false, "창고에 ${shipClass.displayName}이(가) 부족합니다.")
            }
            if (fleet.currentUnits + amount > fleet.maxUnits) {
                return AllocationResult(false, "부대 최대 유닛 수를 초과합니다.")
            }
            fw.removeShips(shipClass, amount)
            fleet.currentUnits += amount
        }

        fw.touch()
        fleetWarehouseRepository.save(fw)
        fleetRepository.save(fleet)

        val direction = if (toWarehouse) "창고로 이동" else "부대로 편입"
        return AllocationResult(true, "${shipClass.displayName} ${amount}유닛 $direction")
    }

    // ===== Replenishment (보충): Fleet Warehouse -> Fleet =====

    /**
     * Replenish damaged fleet units and crew from fleet warehouse.
     * Must use exact same ship subtype. Crew is auto-replenished based on ship crew efficiency.
     *
     * @param shipClass the ship class to replenish
     * @param amount number of units to replenish
     */
    @Transactional
    fun replenish(
        sessionId: Long,
        fleetId: Long,
        shipClass: ShipClassType,
        amount: Int,
    ): AllocationResult {
        val fw = getOrCreateFleetWarehouse(sessionId, fleetId)
        val fleet = fleetRepository.findById(fleetId).orElse(null)
            ?: return AllocationResult(false, "부대를 찾을 수 없습니다.")

        if (amount <= 0) {
            return AllocationResult(false, "보충할 수량은 1 이상이어야 합니다.")
        }

        // Check available ships in warehouse
        val availableShips = fw.getShipCount(shipClass)
        val actualShips = amount.coerceAtMost(availableShips)
        if (actualShips <= 0) {
            return AllocationResult(false, "창고에 ${shipClass.displayName}이(가) 없습니다.")
        }

        // Check fleet can accept more units
        val canAccept = fleet.maxUnits - fleet.currentUnits
        val finalShips = actualShips.coerceAtMost(canAccept)
        if (finalShips <= 0) {
            return AllocationResult(false, "부대가 이미 만원입니다.")
        }

        // Auto-replenish crew: each ship unit needs crew
        // Crew efficiency ratio: 1 crew per ship unit
        val crewNeeded = finalShips
        val totalAvailableCrew = fw.totalCrew()

        if (totalAvailableCrew <= 0) {
            return AllocationResult(
                false,
                "창고에 승조원이 없습니다. 승조원 없이 함선은 운용할 수 없습니다."
            )
        }

        // Draw crew from best proficiency first (elite -> veteran -> normal -> green)
        var crewRemaining = crewNeeded.coerceAtMost(totalAvailableCrew)
        val crewDrawn = mutableMapOf<CrewProficiency, Int>()

        for (prof in CrewProficiency.entries.reversed()) {
            if (crewRemaining <= 0) break
            val available = fw.getCrewCount(prof)
            val draw = crewRemaining.coerceAtMost(available)
            if (draw > 0) {
                fw.removeCrew(prof, draw)
                crewDrawn[prof] = draw
                crewRemaining -= draw
            }
        }

        // Finalize based on crew actually available
        val actualReplenished = finalShips.coerceAtMost(crewNeeded - crewRemaining)

        fw.removeShips(shipClass, actualReplenished)
        fleet.currentUnits += actualReplenished

        fw.touch()
        fleetWarehouseRepository.save(fw)
        fleetRepository.save(fleet)

        val crewSummary = crewDrawn.entries.joinToString(", ") { "${it.key.displayName} ${it.value}명" }
        return AllocationResult(
            true,
            "${shipClass.displayName} ${actualReplenished}유닛 보충 (승조원: $crewSummary)"
        )
    }

    fun savePlanetWarehouse(pw: PlanetWarehouse): PlanetWarehouse {
        pw.touch()
        return planetWarehouseRepository.save(pw)
    }

    fun saveFleetWarehouse(fw: FleetWarehouse): FleetWarehouse {
        fw.touch()
        return fleetWarehouseRepository.save(fw)
    }
}

data class AllocationResult(
    val success: Boolean,
    val message: String,
)
