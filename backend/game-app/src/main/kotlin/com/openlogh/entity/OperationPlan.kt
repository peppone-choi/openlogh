package com.openlogh.entity

import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.model.OperationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Phase 12 D-01: OperationPlan first-class entity.
 *
 * Represents a strategic operation issued by a faction: target one star system,
 * one mission objective (CONQUEST/DEFENSE/SWEEP), and a set of participating fleets.
 *
 * D-02: multiple simultaneous operations per faction allowed.
 * D-03: one target star system per operation.
 * D-04: one fleet belongs to at most one operation (enforced at application layer).
 */
@Entity
@Table(name = "operation_plan")
class OperationPlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0L,

    @Column(name = "faction_id", nullable = false)
    var factionId: Long = 0L,

    @Column(name = "name", nullable = false, length = 128)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "objective", nullable = false, length = 16)
    var objective: MissionObjective = MissionObjective.DEFENSE,

    @Column(name = "target_star_system_id", nullable = false)
    var targetStarSystemId: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: OperationStatus = OperationStatus.PENDING,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_fleet_ids", columnDefinition = "jsonb", nullable = false)
    var participantFleetIds: MutableList<Long> = mutableListOf(),

    @Column(name = "scale", columnDefinition = "SMALLINT", nullable = false)
    var scale: Short = 1,

    @Column(name = "issued_by_officer_id", nullable = false)
    var issuedByOfficerId: Long = 0L,

    @Column(name = "issued_at_tick", nullable = false)
    var issuedAtTick: Long = 0L,

    /** D-05: column added but consumption deferred to a later phase. */
    @Column(name = "expected_completion_tick")
    var expectedCompletionTick: Long? = null,

    /** D-18: DEFENSE stability counter — ticks with no enemies at target. */
    @Column(name = "stability_tick_counter", nullable = false)
    var stabilityTickCounter: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
