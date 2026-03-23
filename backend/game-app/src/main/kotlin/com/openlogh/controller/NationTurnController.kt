package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.entity.FactionTurn
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class NationTurnController(
    private val factionTurnRepository: FactionTurnRepository,
    private val factionRepository: FactionRepository,
) {
    // GET /api/nations/{nationId}/turns — 국가 턴 목록
    @GetMapping("/nations/{nationId}/turns")
    fun getNationTurns(
        @PathVariable nationId: Long,
        @RequestParam(required = false, defaultValue = "0") officerLevel: Short,
    ): ResponseEntity<List<FactionTurnResponse>> {
        val turns = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        return ResponseEntity.ok(turns.map { FactionTurnResponse.from(it) })
    }

    // POST /api/nations/{nationId}/turns — 국가 턴 예약
    @PostMapping("/nations/{nationId}/turns")
    fun reserveNationTurns(
        @PathVariable nationId: Long,
        @RequestParam(required = false) generalId: Long?,
        @RequestBody request: ReserveTurnsRequest,
    ): ResponseEntity<List<FactionTurnResponse>> {
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val officerLevel: Short = 0
        val existing = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        val existingMap = existing.associateBy { it.turnIdx }

        for (entry in request.turns) {
            val turn = existingMap[entry.turnIdx]
            if (turn != null) {
                turn.actionCode = entry.actionCode
                turn.arg = entry.arg?.toMutableMap() ?: mutableMapOf()
                factionTurnRepository.save(turn)
            } else {
                factionTurnRepository.save(
                    FactionTurn(
                        sessionId = faction.sessionId,
                        factionId = nationId,
                        officerLevel = officerLevel,
                        turnIdx = entry.turnIdx,
                        actionCode = entry.actionCode,
                        arg = entry.arg?.toMutableMap() ?: mutableMapOf(),
                    )
                )
            }
        }

        val updated = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        return ResponseEntity.ok(updated.map { FactionTurnResponse.from(it) })
    }

    // POST /api/nations/{nationId}/turns/push — 국가 턴 밀기
    @PostMapping("/nations/{nationId}/turns/push")
    fun pushNationTurns(
        @PathVariable nationId: Long,
        @RequestParam(required = false) generalId: Long?,
        @RequestBody request: PushRequest,
    ): ResponseEntity<List<FactionTurnResponse>> {
        val officerLevel: Short = 0
        val turns = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
            .toMutableList()
        if (turns.isEmpty()) return ResponseEntity.ok(emptyList())

        val amount = request.amount
        for (i in turns.indices.reversed()) {
            if (i + amount < turns.size) {
                turns[i + amount].actionCode = turns[i].actionCode
                turns[i + amount].arg = turns[i].arg.toMutableMap()
            }
        }
        for (i in 0 until minOf(amount, turns.size)) {
            turns[i].actionCode = "휴식"
            turns[i].arg = mutableMapOf()
        }
        factionTurnRepository.saveAll(turns)

        val updated = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        return ResponseEntity.ok(updated.map { FactionTurnResponse.from(it) })
    }

    // POST /api/nations/{nationId}/turns/repeat — 국가 턴 반복
    @PostMapping("/nations/{nationId}/turns/repeat")
    fun repeatNationTurns(
        @PathVariable nationId: Long,
        @RequestParam(required = false) generalId: Long?,
        @RequestBody request: RepeatRequest,
    ): ResponseEntity<List<FactionTurnResponse>> {
        val officerLevel: Short = 0
        val turns = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        if (turns.isEmpty()) return ResponseEntity.ok(emptyList())

        val first = turns.first()
        val count = request.count
        val template = first.actionCode
        val templateArg = first.arg.toMutableMap()

        for (i in 1..minOf(count, turns.size - 1)) {
            turns[i].actionCode = template
            turns[i].arg = templateArg.toMutableMap()
        }
        factionTurnRepository.saveAll(turns)

        val updated = factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(nationId, officerLevel)
        return ResponseEntity.ok(updated.map { FactionTurnResponse.from(it) })
    }
}
