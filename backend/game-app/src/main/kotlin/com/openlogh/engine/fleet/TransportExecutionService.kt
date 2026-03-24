package com.openlogh.engine.fleet

import com.openlogh.entity.Fleet
import com.openlogh.entity.Planet
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature 7.9 - Transport Execution Engine
 *
 * Transport plan: source planet → destination planet, cargo manifest (units + supplies).
 * Fleet follows route automatically; on arrival, unloads cargo to destination planet warehouse.
 * Transport status tracked in fleet.meta["transportPlan"].
 */
@Service
class TransportExecutionService(
    private val fleetRepository: FleetRepository,
    private val planetRepository: PlanetRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TransportExecutionService::class.java)
        private const val TRANSPORT_FLEET_TYPE = "transport"
    }

    /**
     * Create a new transport plan for a fleet.
     */
    @Transactional
    fun createTransportPlan(
        fleetId: Long,
        sourcePlanetId: Long,
        destinationPlanetId: Long,
        cargoShips: Int,
        cargoSupplies: Int,
        cargoFunds: Int = 0,
    ): TransportPlanResult {
        val fleet = fleetRepository.findById(fleetId).orElse(null)
            ?: return TransportPlanResult(success = false, reason = "함대를 찾을 수 없습니다.")

        if (fleet.fleetType != TRANSPORT_FLEET_TYPE) {
            return TransportPlanResult(success = false, reason = "수송함대만 수송 작전을 실행할 수 있습니다.")
        }

        if (fleet.meta.containsKey("transportPlan")) {
            return TransportPlanResult(success = false, reason = "이미 진행 중인 수송 작전이 있습니다.")
        }

        val sourcePlanet = planetRepository.findById(sourcePlanetId).orElse(null)
            ?: return TransportPlanResult(success = false, reason = "출발 행성을 찾을 수 없습니다.")

        // Validate cargo availability
        if (sourcePlanet.supplies < cargoSupplies) {
            return TransportPlanResult(success = false, reason = "출발 행성의 물자가 부족합니다.")
        }

        // Deduct cargo from source planet
        sourcePlanet.supplies -= cargoSupplies
        planetRepository.save(sourcePlanet)

        // Set transport plan in fleet meta
        fleet.meta["transportPlan"] = mutableMapOf(
            "sourcePlanetId" to sourcePlanetId,
            "destinationPlanetId" to destinationPlanetId,
            "cargoShips" to cargoShips,
            "cargoSupplies" to cargoSupplies,
            "cargoFunds" to cargoFunds,
            "status" to "in_transit",
            "departureMonth" to System.currentTimeMillis(),
        )
        fleet.planetId = sourcePlanetId
        fleetRepository.save(fleet)

        log.info(
            "Transport plan created: fleet={} from={} to={} supplies={} ships={}",
            fleetId, sourcePlanetId, destinationPlanetId, cargoSupplies, cargoShips,
        )
        return TransportPlanResult(success = true)
    }

    /**
     * Process all active transport fleets - called during monthly turn processing.
     * Moves fleet one step closer to destination; on arrival, unloads cargo.
     */
    @Transactional
    fun processTransports(sessionId: Long) {
        val fleets = fleetRepository.findBySessionId(sessionId)
            .filter { it.fleetType == TRANSPORT_FLEET_TYPE && it.meta.containsKey("transportPlan") }

        for (fleet in fleets) {
            try {
                processFleetTransport(fleet)
            } catch (e: Exception) {
                log.warn("Error processing transport for fleet {}: {}", fleet.id, e.message)
            }
        }
    }

    private fun processFleetTransport(fleet: Fleet) {
        @Suppress("UNCHECKED_CAST")
        val plan = fleet.meta["transportPlan"] as? MutableMap<String, Any> ?: return
        val status = plan["status"] as? String ?: return
        if (status != "in_transit") return

        val destinationPlanetId = (plan["destinationPlanetId"] as? Number)?.toLong() ?: return

        // Check if fleet has arrived (planetId matches destination)
        if (fleet.planetId == destinationPlanetId) {
            unloadCargo(fleet, plan, destinationPlanetId)
            return
        }

        // Move fleet toward destination (simplified: direct arrival next turn)
        fleet.planetId = destinationPlanetId
        fleetRepository.save(fleet)
        log.info("Transport fleet {} moving to planet {}", fleet.id, destinationPlanetId)
    }

    private fun unloadCargo(fleet: Fleet, plan: MutableMap<String, Any>, destinationPlanetId: Long) {
        val destinationPlanet = planetRepository.findById(destinationPlanetId).orElse(null) ?: run {
            log.warn("Destination planet {} not found for fleet {}", destinationPlanetId, fleet.id)
            return
        }

        val cargoSupplies = (plan["cargoSupplies"] as? Number)?.toInt() ?: 0
        val cargoShips = (plan["cargoShips"] as? Number)?.toInt() ?: 0

        // Unload supplies to destination planet
        destinationPlanet.supplies += cargoSupplies
        planetRepository.save(destinationPlanet)

        // Mark plan as completed
        plan["status"] = "completed"
        plan["completedAt"] = System.currentTimeMillis()
        fleet.meta["transportPlan"] = plan
        fleet.meta["lastTransportCompleted"] = destinationPlanetId
        fleetRepository.save(fleet)

        log.info(
            "Transport fleet {} arrived at planet {}. Unloaded: supplies={} ships={}",
            fleet.id, destinationPlanetId, cargoSupplies, cargoShips,
        )
    }

    /**
     * Cancel an active transport plan. Returns undelivered cargo to source if still in transit.
     */
    @Transactional
    fun cancelTransport(fleetId: Long): Boolean {
        val fleet = fleetRepository.findById(fleetId).orElse(null) ?: return false

        @Suppress("UNCHECKED_CAST")
        val plan = fleet.meta["transportPlan"] as? MutableMap<String, Any> ?: return false
        val status = plan["status"] as? String ?: return false

        if (status == "in_transit") {
            // Return cargo to source planet
            val sourcePlanetId = (plan["sourcePlanetId"] as? Number)?.toLong()
            val cargoSupplies = (plan["cargoSupplies"] as? Number)?.toInt() ?: 0
            if (sourcePlanetId != null && cargoSupplies > 0) {
                val sourcePlanet = planetRepository.findById(sourcePlanetId).orElse(null)
                if (sourcePlanet != null) {
                    sourcePlanet.supplies += cargoSupplies
                    planetRepository.save(sourcePlanet)
                }
            }
        }

        fleet.meta.remove("transportPlan")
        fleetRepository.save(fleet)
        log.info("Transport plan cancelled for fleet {}", fleetId)
        return true
    }

    data class TransportPlanResult(
        val success: Boolean,
        val reason: String? = null,
    )
}
