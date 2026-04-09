package com.openlogh.repository

import com.openlogh.entity.OperationPlan
import com.openlogh.model.OperationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * Phase 12 Plan 12-01: OperationPlan repository.
 *
 * Uses JpaRepository derived queries + one native JSONB membership query for
 * production PostgreSQL lookups by fleetId. H2 integration tests must use
 * findBySessionIdAndStatus + Kotlin-side filtering (H2 does not support the
 * @> JSONB operator).
 */
@Repository
interface OperationPlanRepository : JpaRepository<OperationPlan, Long> {
    fun findBySessionIdAndStatus(sessionId: Long, status: OperationStatus): List<OperationPlan>

    fun findBySessionIdAndFactionIdAndStatusIn(
        sessionId: Long,
        factionId: Long,
        statuses: List<OperationStatus>,
    ): List<OperationPlan>

    /**
     * PostgreSQL-only JSONB membership query.
     * DO NOT call from H2 integration tests — use findBySessionIdAndStatus + Kotlin filter instead.
     */
    @Query(
        value = """
            SELECT * FROM operation_plan
            WHERE session_id = :sessionId
              AND status IN ('PENDING', 'ACTIVE')
              AND participant_fleet_ids @> CAST(:fleetIdJson AS jsonb)
        """,
        nativeQuery = true,
    )
    fun findActiveOrPendingByFleetIdNative(
        @Param("sessionId") sessionId: Long,
        @Param("fleetIdJson") fleetIdJson: String,
    ): List<OperationPlan>
}
