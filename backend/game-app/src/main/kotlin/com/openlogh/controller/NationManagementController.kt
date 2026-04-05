package com.openlogh.controller

import com.openlogh.dto.AppointOfficerRequest
import com.openlogh.dto.ExpelRequest
import com.openlogh.dto.OfficerInfo
import com.openlogh.service.FactionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/nations/{nationId}")
class NationManagementController(
    private val factionService: FactionService,
) {
    @GetMapping("/officers")
    fun getOfficers(@PathVariable nationId: Long): ResponseEntity<List<OfficerInfo>> {
        return ResponseEntity.ok(factionService.getOfficers(nationId))
    }

    @PostMapping("/officers")
    fun appointOfficer(
        @PathVariable nationId: Long,
        @RequestBody request: AppointOfficerRequest,
    ): ResponseEntity<Void> {
        try {
            if (!factionService.appointOfficer(nationId, request.generalId, request.officerLevel, request.officerPlanet)) {
                return ResponseEntity.badRequest().build()
            }
            return ResponseEntity.ok().build()
        } catch (e: IllegalStateException) {
            return ResponseEntity.badRequest().build()
        }
    }

    @PostMapping("/expel")
    fun expelGeneral(
        @PathVariable nationId: Long,
        @RequestBody request: ExpelRequest,
    ): ResponseEntity<Void> {
        if (!factionService.expelOfficer(nationId, request.generalId)) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok().build()
    }
}
