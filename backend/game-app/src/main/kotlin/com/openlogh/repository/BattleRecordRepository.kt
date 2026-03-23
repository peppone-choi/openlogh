package com.openlogh.repository

import com.openlogh.entity.BattleRecord
import org.springframework.data.jpa.repository.JpaRepository

interface BattleRecordRepository : JpaRepository<BattleRecord, Long> {
    fun findBySessionId(sessionId: Long): List<BattleRecord>
    fun findBySessionCode(sessionCode: String): BattleRecord?
    fun findByPlanetId(planetId: Long): List<BattleRecord>
    fun findByAttackerFactionIdOrDefenderFactionId(attackerFactionId: Long, defenderFactionId: Long): List<BattleRecord>
    fun findBySessionIdOrderByEndedAtDesc(sessionId: Long): List<BattleRecord>
}
