package com.openlogh.controller

import com.openlogh.repository.BattleRecordRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/battles")
class BattleRecordController(
    private val battleRecordRepository: BattleRecordRepository,
) {

    /** 전투 기록 상세 조회 */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Any> {
        val record = battleRecordRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(record)
    }

    /** 세션(월드)별 전투 기록 목록 */
    @GetMapping("/session/{sessionId}")
    fun getBySession(@PathVariable sessionId: Long): ResponseEntity<Any> {
        val records = battleRecordRepository.findBySessionIdOrderByEndedAtDesc(sessionId)
        return ResponseEntity.ok(records.map { it.toSummary() })
    }

    /** 행성별 전투 기록 */
    @GetMapping("/planet/{planetId}")
    fun getByPlanet(@PathVariable planetId: Long): ResponseEntity<Any> {
        val records = battleRecordRepository.findByPlanetId(planetId)
        return ResponseEntity.ok(records.map { it.toSummary() })
    }

    /** 진영별 전투 기록 */
    @GetMapping("/faction/{factionId}")
    fun getByFaction(@PathVariable factionId: Long): ResponseEntity<Any> {
        val records = battleRecordRepository.findByAttackerFactionIdOrDefenderFactionId(factionId, factionId)
        return ResponseEntity.ok(records.map { it.toSummary() })
    }

    /** 리플레이 데이터 (전체 이벤트 로그 + 초기 배치) */
    @GetMapping("/{id}/replay")
    fun getReplayData(@PathVariable id: Long): ResponseEntity<Any> {
        val record = battleRecordRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf(
            "id" to record.id,
            "sessionCode" to record.sessionCode,
            "planetName" to record.planetName,
            "totalTurns" to record.totalTurns,
            "initialState" to record.initialState,
            "attackerOfficers" to record.attackerOfficers,
            "defenderOfficers" to record.defenderOfficers,
            "battleLog" to record.battleLog,
            "victoryType" to record.victoryType,
            "winnerFactionId" to record.winnerFactionId,
        ))
    }

    private fun com.openlogh.entity.BattleRecord.toSummary() = mapOf(
        "id" to id,
        "sessionCode" to sessionCode,
        "planetName" to planetName,
        "attackerFactionName" to attackerFactionName,
        "defenderFactionName" to defenderFactionName,
        "attackerWon" to attackerWon,
        "victoryType" to victoryType,
        "totalTurns" to totalTurns,
        "attackerShipsLost" to attackerShipsLost,
        "defenderShipsLost" to defenderShipsLost,
        "planetCaptured" to planetCaptured,
        "endedAt" to endedAt,
    )
}
