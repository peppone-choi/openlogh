package com.openlogh.controller

import com.openlogh.dto.SimulateRequest
import com.openlogh.dto.SimulateResult
import com.openlogh.service.BattleSimService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/battle")
class BattleSimController(
    private val battleSimService: BattleSimService,
) {
    @PostMapping("/simulate")
    fun simulate(@RequestBody request: SimulateRequest): ResponseEntity<SimulateResult> {
        return ResponseEntity.ok(battleSimService.simulate(request))
    }
}
