package com.openlogh.controller

import com.openlogh.dto.TournamentBracketMatchResponse
import com.openlogh.dto.TournamentInfoResponse
import com.openlogh.dto.TournamentRegisterRequest
import com.openlogh.dto.CreateTournamentRequest
import com.openlogh.repository.TournamentRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worlds/{worldId}")
class TournamentController(
    private val tournamentRepository: TournamentRepository,
    private val sessionStateRepository: SessionStateRepository,
) {
    @GetMapping("/tournament")
    fun getInfo(@PathVariable worldId: Long): ResponseEntity<TournamentInfoResponse> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val entries = tournamentRepository.findBySessionIdOrderByRoundAscBracketPositionAsc(worldId)
        val bracket = entries.map {
            TournamentBracketMatchResponse(
                round = it.round.toInt(),
                match = it.bracketPosition.toInt(),
                p1 = it.officerId,
                p2 = it.opponentId ?: 0,
                winner = if (it.result > 0.toShort()) it.officerId else if (it.result < 0.toShort()) it.opponentId else null,
            )
        }
        val participants = entries.map { it.officerId }.distinct()
        @Suppress("UNCHECKED_CAST")
        val state = (world.meta["tournamentState"] as? Number)?.toInt() ?: 0

        return ResponseEntity.ok(TournamentInfoResponse(state = state, bracket = bracket, participants = participants))
    }

    @PostMapping("/tournament")
    fun create(
        @PathVariable worldId: Long,
        @RequestBody req: CreateTournamentRequest,
    ): ResponseEntity<Map<String, Any>> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()
        world.meta["tournamentState"] = 1
        world.meta["tournamentType"] = req.type
        sessionStateRepository.save(world)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/tournament/register")
    fun register(
        @PathVariable worldId: Long,
        @RequestBody req: TournamentRegisterRequest,
    ): ResponseEntity<Map<String, Boolean>> {
        // TODO: validate officer eligibility, add to tournament bracket
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/tournament/advance")
    fun advance(@PathVariable worldId: Long): ResponseEntity<Map<String, Any>> {
        // TODO: advance tournament to next phase
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/tournament/message")
    fun sendMessage(
        @PathVariable worldId: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Map<String, Boolean>> {
        // TODO: broadcast tournament message
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/tournament/start")
    fun start(@PathVariable worldId: Long): ResponseEntity<Map<String, Any>> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()
        world.meta["tournamentState"] = 2
        sessionStateRepository.save(world)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/tournament/finalize")
    fun finalize(@PathVariable worldId: Long): ResponseEntity<Map<String, Any>> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return ResponseEntity.notFound().build()
        world.meta["tournamentState"] = 0
        sessionStateRepository.save(world)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}
