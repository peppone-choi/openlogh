package com.openlogh.controller

import com.openlogh.command.CommandRegistry
import com.openlogh.command.CommandResult
import com.openlogh.dto.*
import com.openlogh.engine.RealtimeService
import com.openlogh.entity.OfficerTurn
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CommandController(
    private val officerTurnRepository: OfficerTurnRepository,
    private val officerRepository: OfficerRepository,
    private val realtimeService: RealtimeService,
    private val commandRegistry: CommandRegistry,
) {
    // GET /api/officers/{id}/turns — 예약된 턴 목록
    @GetMapping("/officers/{id}/turns")
    fun getGeneralTurns(@PathVariable id: Long): ResponseEntity<List<OfficerTurnResponse>> {
        val turns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        return ResponseEntity.ok(turns.map { OfficerTurnResponse.from(it) })
    }

    // POST /api/officers/{id}/turns — 턴 예약
    @PostMapping("/officers/{id}/turns")
    fun reserveGeneralTurns(
        @PathVariable id: Long,
        @RequestBody request: ReserveTurnsRequest,
    ): ResponseEntity<List<OfficerTurnResponse>> {
        val officer = officerRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val existing = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        val existingMap = existing.associateBy { it.turnIdx }

        for (entry in request.turns) {
            val turn = existingMap[entry.turnIdx]
            if (turn != null) {
                turn.actionCode = entry.actionCode
                turn.arg = entry.arg?.toMutableMap() ?: mutableMapOf()
                officerTurnRepository.save(turn)
            } else {
                officerTurnRepository.save(
                    OfficerTurn(
                        sessionId = officer.sessionId,
                        officerId = id,
                        turnIdx = entry.turnIdx,
                        actionCode = entry.actionCode,
                        arg = entry.arg?.toMutableMap() ?: mutableMapOf(),
                    )
                )
            }
        }

        val updated = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        return ResponseEntity.ok(updated.map { OfficerTurnResponse.from(it) })
    }

    // POST /api/officers/{id}/turns/push — 턴 밀기
    @PostMapping("/officers/{id}/turns/push")
    fun pushTurns(
        @PathVariable id: Long,
        @RequestBody request: PushRequest,
    ): ResponseEntity<List<OfficerTurnResponse>> {
        val turns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id).toMutableList()
        if (turns.isEmpty()) return ResponseEntity.ok(emptyList())

        val amount = request.amount
        // Shift turns down by amount
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
        officerTurnRepository.saveAll(turns)

        val updated = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        return ResponseEntity.ok(updated.map { OfficerTurnResponse.from(it) })
    }

    // POST /api/officers/{id}/turns/repeat — 턴 반복
    @PostMapping("/officers/{id}/turns/repeat")
    fun repeatTurns(
        @PathVariable id: Long,
        @RequestBody request: RepeatRequest,
    ): ResponseEntity<List<OfficerTurnResponse>> {
        val turns = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        if (turns.isEmpty()) return ResponseEntity.ok(emptyList())

        val first = turns.first()
        val count = request.count
        val template = first.actionCode
        val templateArg = first.arg.toMutableMap()

        for (i in 1..minOf(count, turns.size - 1)) {
            turns[i].actionCode = template
            turns[i].arg = templateArg.toMutableMap()
        }
        officerTurnRepository.saveAll(turns)

        val updated = officerTurnRepository.findByOfficerIdOrderByTurnIdx(id)
        return ResponseEntity.ok(updated.map { OfficerTurnResponse.from(it) })
    }

    // GET /api/officers/{id}/command-table — 개인 커맨드 테이블
    @GetMapping("/officers/{id}/command-table")
    fun getCommandTable(@PathVariable id: Long): ResponseEntity<Map<String, List<CommandTableEntry>>> {
        val names = commandRegistry.getGeneralCommandNames()
        val entries = names.map { name ->
            CommandTableEntry(
                actionCode = name,
                name = name,
                category = "general",
                enabled = true,
            )
        }
        return ResponseEntity.ok(mapOf("general" to entries))
    }

    // GET /api/officers/{id}/nation-command-table — 국가 커맨드 테이블
    @GetMapping("/officers/{id}/nation-command-table")
    fun getNationCommandTable(@PathVariable id: Long): ResponseEntity<Map<String, List<CommandTableEntry>>> {
        val names = commandRegistry.getNationCommandNames()
        val entries = names.map { name ->
            CommandTableEntry(
                actionCode = name,
                name = name,
                category = "nation",
                enabled = true,
            )
        }
        return ResponseEntity.ok(mapOf("nation" to entries))
    }

    // POST /api/officers/{id}/execute — 실시간 커맨드 실행
    @PostMapping("/officers/{id}/execute")
    fun executeCommand(
        @PathVariable id: Long,
        @RequestBody request: ExecuteRequest,
    ): ResponseEntity<CommandResult> {
        val result = realtimeService.submitCommand(id, request.actionCode, request.arg)
        return ResponseEntity.ok(result)
    }

    // POST /api/officers/{id}/execute-nation — 실시간 국가 커맨드 실행
    @PostMapping("/officers/{id}/execute-nation")
    fun executeNationCommand(
        @PathVariable id: Long,
        @RequestBody request: ExecuteRequest,
    ): ResponseEntity<CommandResult> {
        val result = realtimeService.submitNationCommand(id, request.actionCode, request.arg)
        return ResponseEntity.ok(result)
    }
}
