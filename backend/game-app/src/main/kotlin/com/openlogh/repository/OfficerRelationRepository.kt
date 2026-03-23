package com.openlogh.repository

import com.openlogh.entity.OfficerRelation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OfficerRelationRepository : JpaRepository<OfficerRelation, Long> {
    fun findBySessionId(sessionId: Long): List<OfficerRelation>

    @Query(
        """
        select r from OfficerRelation r
        where r.sessionId = :sessionId
          and (r.officerAId = :officerId or r.officerBId = :officerId)
        """,
    )
    fun findBySessionIdAndOfficerId(
        @Param("sessionId") sessionId: Long,
        @Param("officerId") officerId: Long,
    ): List<OfficerRelation>

    @Query(
        """
        select r from OfficerRelation r
        where r.sessionId = :sessionId
          and ((r.officerAId = :a and r.officerBId = :b)
            or (r.officerAId = :b and r.officerBId = :a))
        """,
    )
    fun findPair(
        @Param("sessionId") sessionId: Long,
        @Param("a") a: Long,
        @Param("b") b: Long,
    ): OfficerRelation?
}
