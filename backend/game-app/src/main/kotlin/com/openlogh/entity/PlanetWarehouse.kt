package com.openlogh.entity

import com.openlogh.model.CrewProficiency
import com.openlogh.model.ShipClassType
import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * Planet warehouse (행성창고): stores produced units and supplies at a planet.
 * Managed by central government; supplies flow from here to fleet warehouses via allocation.
 */
@Entity
@Table(
    name = "planet_warehouse",
    uniqueConstraints = [UniqueConstraint(columnNames = ["session_id", "planet_id"])]
)
class PlanetWarehouse(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "planet_id", nullable = false)
    var planetId: Long = 0,

    // Ship counts by class (unit count, each unit = 300 ships)
    @Column(nullable = false)
    var battleship: Int = 0,

    @Column(nullable = false)
    var cruiser: Int = 0,

    @Column(nullable = false)
    var destroyer: Int = 0,

    @Column(nullable = false)
    var carrier: Int = 0,

    @Column(nullable = false)
    var transport: Int = 0,

    @Column(nullable = false)
    var hospital: Int = 0,

    // Crew counts by proficiency
    @Column(name = "crew_green", nullable = false)
    var crewGreen: Int = 0,

    @Column(name = "crew_normal", nullable = false)
    var crewNormal: Int = 0,

    @Column(name = "crew_veteran", nullable = false)
    var crewVeteran: Int = 0,

    @Column(name = "crew_elite", nullable = false)
    var crewElite: Int = 0,

    // Resources
    @Column(nullable = false)
    var supplies: Int = 0,

    @Column(nullable = false)
    var missiles: Int = 0,

    // Shipyard
    @Column(name = "has_shipyard", nullable = false)
    var hasShipyard: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** Get ship count for a specific class. */
    fun getShipCount(shipClass: ShipClassType): Int = when (shipClass) {
        ShipClassType.BATTLESHIP -> battleship
        ShipClassType.CRUISER -> cruiser
        ShipClassType.DESTROYER -> destroyer
        ShipClassType.CARRIER -> carrier
        ShipClassType.TRANSPORT -> transport
        ShipClassType.HOSPITAL -> hospital
    }

    /** Set ship count for a specific class. */
    fun setShipCount(shipClass: ShipClassType, count: Int) {
        when (shipClass) {
            ShipClassType.BATTLESHIP -> battleship = count
            ShipClassType.CRUISER -> cruiser = count
            ShipClassType.DESTROYER -> destroyer = count
            ShipClassType.CARRIER -> carrier = count
            ShipClassType.TRANSPORT -> transport = count
            ShipClassType.HOSPITAL -> hospital = count
        }
    }

    /** Add ships of a specific class. */
    fun addShips(shipClass: ShipClassType, amount: Int) {
        setShipCount(shipClass, getShipCount(shipClass) + amount)
    }

    /** Remove ships of a specific class. Returns actual removed count. */
    fun removeShips(shipClass: ShipClassType, amount: Int): Int {
        val current = getShipCount(shipClass)
        val actual = amount.coerceAtMost(current)
        setShipCount(shipClass, current - actual)
        return actual
    }

    /** Get crew count for a specific proficiency. */
    fun getCrewCount(proficiency: CrewProficiency): Int = when (proficiency) {
        CrewProficiency.GREEN -> crewGreen
        CrewProficiency.NORMAL -> crewNormal
        CrewProficiency.VETERAN -> crewVeteran
        CrewProficiency.ELITE -> crewElite
    }

    /** Set crew count for a specific proficiency. */
    fun setCrewCount(proficiency: CrewProficiency, count: Int) {
        when (proficiency) {
            CrewProficiency.GREEN -> crewGreen = count
            CrewProficiency.NORMAL -> crewNormal = count
            CrewProficiency.VETERAN -> crewVeteran = count
            CrewProficiency.ELITE -> crewElite = count
        }
    }

    /** Add crew of a specific proficiency. */
    fun addCrew(proficiency: CrewProficiency, amount: Int) {
        setCrewCount(proficiency, getCrewCount(proficiency) + amount)
    }

    /** Remove crew of a specific proficiency. Returns actual removed count. */
    fun removeCrew(proficiency: CrewProficiency, amount: Int): Int {
        val current = getCrewCount(proficiency)
        val actual = amount.coerceAtMost(current)
        setCrewCount(proficiency, current - actual)
        return actual
    }

    /** Total crew across all proficiency levels. */
    fun totalCrew(): Int = crewGreen + crewNormal + crewVeteran + crewElite

    /** Total ship units across all classes. */
    fun totalShips(): Int = battleship + cruiser + destroyer + carrier + transport + hospital

    /** Touch updated timestamp. */
    fun touch() {
        updatedAt = OffsetDateTime.now()
    }
}
