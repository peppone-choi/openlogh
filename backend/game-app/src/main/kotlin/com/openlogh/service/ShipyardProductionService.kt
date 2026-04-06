package com.openlogh.service

import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClassType
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.PlanetWarehouseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Shipyard auto-production service (조병창 자동생산).
 *
 * Each planet/fortress with a shipyard automatically produces units.
 * - Production continues as long as ownership doesn't change.
 * - Auto-production doesn't affect planet tax revenue.
 * - Only GREEN-level crew from production.
 * - Neutral planets (factionId=0) do not produce.
 * - Per-planet ship class configured via planet.meta["shipyardClass"] (default: BATTLESHIP).
 *
 * Called by TickEngine every SHIPYARD_INTERVAL_TICKS (3600 ticks = 1 game day).
 */
@Service
class ShipyardProductionService(
    private val planetWarehouseRepository: PlanetWarehouseRepository,
    private val planetRepository: PlanetRepository,
) {
    private val log = LoggerFactory.getLogger(ShipyardProductionService::class.java)

    /**
     * Run auto-production for all shipyard planets in a session.
     * Called once per game day by the tick engine.
     *
     * Production formula: based on planet's production stat.
     * - Ship units produced = production / 200, coerceAtLeast(1)
     * - Crew produced = ship units produced (1 crew per ship unit, GREEN only)
     * - Supply output = production / 100, coerceAtLeast(1)
     * - Ship class = planet.meta["shipyardClass"] (default BATTLESHIP)
     * - Neutral planets (factionId=0) are skipped
     */
    @Transactional
    fun runProduction(sessionId: Long): List<ProductionReport> {
        val shipyardWarehouses = planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(sessionId)
        if (shipyardWarehouses.isEmpty()) return emptyList()

        val reports = mutableListOf<ProductionReport>()

        for (pw in shipyardWarehouses) {
            val planet = planetRepository.findById(pw.planetId).orElse(null) ?: continue

            // Skip neutral planets — no faction owns them
            if (planet.factionId == 0L) continue

            // Calculate production output
            val productionStat = planet.production.coerceAtLeast(0)
            val baseOutput = (productionStat / 200).coerceAtLeast(1)

            // Determine what to produce from planet meta (default: BATTLESHIP)
            val shipClassStr = (planet.meta["shipyardClass"] as? String) ?: "BATTLESHIP"
            val shipClass = try {
                ShipClassType.valueOf(shipClassStr)
            } catch (e: IllegalArgumentException) {
                ShipClassType.BATTLESHIP
            }

            // Produce ships
            pw.addShips(shipClass, baseOutput)

            // Produce crew (always GREEN)
            pw.addCrew(CrewProficiency.GREEN, baseOutput)

            // Produce supplies based on production
            val supplyOutput = (productionStat / 100).coerceAtLeast(1)
            pw.supplies += supplyOutput

            pw.touch()
            planetWarehouseRepository.save(pw)

            reports.add(
                ProductionReport(
                    planetId = pw.planetId,
                    planetName = planet.name,
                    factionId = planet.factionId,
                    shipsProduced = baseOutput,
                    shipClass = shipClass,
                    crewProduced = baseOutput,
                    suppliesProduced = supplyOutput,
                )
            )
        }

        if (reports.isNotEmpty()) {
            log.info(
                "Shipyard production complete for session {}: {} planets, {} total ships",
                sessionId, reports.size, reports.sumOf { it.shipsProduced }
            )
        }

        return reports
    }
}

data class ProductionReport(
    val planetId: Long,
    val planetName: String,
    val factionId: Long,
    val shipsProduced: Int,
    val shipClass: ShipClassType,
    val crewProduced: Int,
    val suppliesProduced: Int,
)
