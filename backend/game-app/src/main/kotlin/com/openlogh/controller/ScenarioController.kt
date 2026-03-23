package com.openlogh.controller

import com.openlogh.model.ScenarioInfo
import com.openlogh.service.ScenarioService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ScenarioController(
    private val scenarioService: ScenarioService,
) {
    @GetMapping("/scenarios")
    fun listScenarios(): ResponseEntity<List<ScenarioInfo>> {
        return ResponseEntity.ok(scenarioService.listScenarios())
    }
}
