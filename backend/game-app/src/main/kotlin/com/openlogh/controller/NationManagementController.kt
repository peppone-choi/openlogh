package com.openlogh.controller

import com.openlogh.dto.AppointOfficerRequest
import com.openlogh.dto.ExpelRequest
import com.openlogh.dto.OfficerInfo
import com.openlogh.dto.SetPermissionRequest
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/factions/{nationId}")
class NationManagementController(
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
) {
    @GetMapping("/officers")
    fun listOfficers(@PathVariable nationId: Long): ResponseEntity<List<OfficerInfo>> {
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val officers = officerRepository.findBySessionIdAndNationId(faction.sessionId, nationId)
        val infos = officers.map {
            OfficerInfo(
                id = it.id,
                name = it.name,
                picture = it.picture,
                rank = it.rank.toInt(),
                planetId = it.planetId,
            )
        }
        return ResponseEntity.ok(infos)
    }

    @PostMapping("/officers")
    fun appointOfficer(
        @PathVariable nationId: Long,
        @RequestBody req: AppointOfficerRequest,
    ): ResponseEntity<Void> {
        val officer = officerRepository.findById(req.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (officer.factionId != nationId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        officer.rank = req.rank.toShort()
        officerRepository.save(officer)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/expel")
    fun expel(
        @PathVariable nationId: Long,
        @RequestBody req: ExpelRequest,
    ): ResponseEntity<Void> {
        val officer = officerRepository.findById(req.officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        if (officer.factionId != nationId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        officer.factionId = 0
        officer.rank = 1
        officerRepository.save(officer)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/permissions")
    fun setPermissions(
        @PathVariable nationId: Long,
        @RequestBody req: SetPermissionRequest,
    ): ResponseEntity<Void> {
        val currentLoginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val faction = factionRepository.findById(nationId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val permission = if (req.isAmbassador) "ambassador" else ""
        for (id in req.generalIds) {
            val officer = officerRepository.findById(id).orElse(null) ?: continue
            if (officer.factionId != nationId) continue
            officer.permission = permission
            officerRepository.save(officer)
        }
        return ResponseEntity.ok().build()
    }

    private fun currentLoginId(): String? {
        return SecurityContextHolder.getContext().authentication?.name
    }
}
