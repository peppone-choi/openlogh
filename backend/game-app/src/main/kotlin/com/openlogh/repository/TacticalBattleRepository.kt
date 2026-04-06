package com.openlogh.repository

import com.openlogh.entity.TacticalBattle
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TacticalBattleRepository : JpaRepository<TacticalBattle, Long> {

    fun findBySessionIdAndPhase(sessionId: Long, phase: String): List<TacticalBattle>

    fun findBySessionId(sessionId: Long): List<TacticalBattle>

    fun findBySessionIdAndStarSystemIdAndPhaseNot(
        sessionId: Long,
        starSystemId: Long,
        phase: String,
    ): List<TacticalBattle>
}
