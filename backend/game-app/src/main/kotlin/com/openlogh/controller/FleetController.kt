package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.entity.Fleet
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class FleetController(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
) {
    // GET /api/nations/{nationId}/troops — 진영의 함대 목록 (멤버 포함)
    @GetMapping("/nations/{nationId}/troops")
    fun listByFaction(@PathVariable nationId: Long): ResponseEntity<List<FleetWithMembers>> {
        val fleets = fleetRepository.findByFactionId(nationId)
        val result = fleets.map { fleet ->
            val members = officerRepository.findByTroopId(fleet.id)
            FleetWithMembers(
                fleet = FleetResponse.from(fleet),
                members = members.map { FleetMemberInfo(it.id, it.name, it.picture, it.rank) },
            )
        }
        return ResponseEntity.ok(result)
    }

    // POST /api/troops — 함대 생성
    @PostMapping("/troops")
    fun create(@RequestBody request: CreateFleetRequest): ResponseEntity<FleetResponse> {
        val leader = officerRepository.findById(request.leaderOfficerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val fleet = Fleet(
            sessionId = request.sessionId,
            leaderGeneralId = request.leaderOfficerId,
            factionId = request.factionId,
            name = request.name,
            fleetType = request.fleetType,
            planetId = leader.planetId,
        )
        val saved = fleetRepository.save(fleet)

        // Assign leader to fleet
        leader.fleetId = saved.id
        officerRepository.save(leader)

        return ResponseEntity.status(HttpStatus.CREATED).body(FleetResponse.from(saved))
    }

    // POST /api/troops/{id}/join — 함대 합류
    @PostMapping("/troops/{id}/join")
    fun join(
        @PathVariable id: Long,
        @RequestBody request: FleetActionRequest,
    ): ResponseEntity<Void> {
        val fleet = fleetRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val officer = officerRepository.findById(request.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        officer.fleetId = fleet.id
        officerRepository.save(officer)
        return ResponseEntity.ok().build()
    }

    // POST /api/troops/{id}/exit — 함대 이탈
    @PostMapping("/troops/{id}/exit")
    fun exit(
        @PathVariable id: Long,
        @RequestBody request: FleetActionRequest,
    ): ResponseEntity<Void> {
        val officer = officerRepository.findById(request.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        officer.fleetId = 0
        officerRepository.save(officer)
        return ResponseEntity.ok().build()
    }

    // POST /api/troops/{id}/kick — 함대 추방
    @PostMapping("/troops/{id}/kick")
    fun kick(
        @PathVariable id: Long,
        @RequestBody request: FleetActionRequest,
    ): ResponseEntity<Void> {
        val officer = officerRepository.findById(request.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        officer.fleetId = 0
        officerRepository.save(officer)
        return ResponseEntity.ok().build()
    }

    // PATCH /api/troops/{id} — 함대 이름 변경
    @PatchMapping("/troops/{id}")
    fun rename(
        @PathVariable id: Long,
        @RequestBody request: RenameFleetRequest,
    ): ResponseEntity<FleetResponse> {
        val fleet = fleetRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        fleet.name = request.name
        val saved = fleetRepository.save(fleet)
        return ResponseEntity.ok(FleetResponse.from(saved))
    }

    // DELETE /api/troops/{id} — 함대 해산
    @DeleteMapping("/troops/{id}")
    fun disband(@PathVariable id: Long): ResponseEntity<Void> {
        val fleet = fleetRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Remove all members from fleet
        val members = officerRepository.findByTroopId(id)
        for (member in members) {
            member.fleetId = 0
            officerRepository.save(member)
        }

        fleetRepository.delete(fleet)
        return ResponseEntity.ok().build()
    }
}
