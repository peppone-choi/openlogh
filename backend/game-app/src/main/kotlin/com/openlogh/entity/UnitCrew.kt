package com.openlogh.entity

import com.openlogh.model.CrewSlotRole
import jakarta.persistence.*
import java.time.OffsetDateTime

/**
 * Tracks officer assignments to fleet crew slots.
 * Each fleet can have multiple crew members in different roles (commander, vice commander, etc.).
 */
@Entity
@Table(name = "unit_crew")
class UnitCrew(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "fleet_id", nullable = false)
    var fleetId: Long = 0,

    @Column(name = "officer_id", nullable = false)
    var officerId: Long = 0,

    @Column(name = "slot_role", nullable = false)
    var slotRole: String = "",

    @Column(name = "assigned_at", nullable = false)
    var assignedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    /** Returns the typed CrewSlotRole enum for this crew assignment. */
    fun getSlotRoleEnum(): CrewSlotRole = CrewSlotRole.valueOf(slotRole)
}
