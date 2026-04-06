package com.openlogh.controller

import com.openlogh.model.ScenarioInfo
import com.openlogh.service.ScenarioService
import com.openlogh.service.SelectPoolService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ScenarioController(
    private val scenarioService: ScenarioService,
    private val selectPoolService: SelectPoolService,
) {
    @GetMapping("/scenarios")
    fun listScenarios(): ResponseEntity<List<ScenarioInfo>> {
        return ResponseEntity.ok(scenarioService.listScenarios())
    }

    /** List only LOGH scenarios (logh_* prefix) */
    @GetMapping("/scenarios/logh")
    fun listLoghScenarios(): ResponseEntity<List<ScenarioInfo>> {
        val all = scenarioService.listScenarios()
        return ResponseEntity.ok(all.filter { it.code.startsWith("logh_") })
    }

    /** Get detail for a specific scenario, including faction balance info */
    @GetMapping("/scenarios/{code}")
    fun getScenarioDetail(@PathVariable code: String): ResponseEntity<ScenarioDetailResponse> {
        return try {
            val info = scenarioService.listScenarios().find { it.code == code }
                ?: return ResponseEntity.notFound().build()
            val data = scenarioService.getScenario(code)
            val factions = data.nation.map { row ->
                val name = row[0] as? String ?: ""
                val color = row.getOrNull(1) as? String ?: ""
                val description = row.getOrNull(4)?.toString() ?: ""
                val cities = (row.lastOrNull { it is List<*> } as? List<*>)?.size ?: 0
                ScenarioFactionInfo(name, color, description, cities)
            }
            val originalCharacters = data.general.mapNotNull { row ->
                val name = row.getOrNull(1) as? String ?: return@mapNotNull null
                val nationIdx = (row.getOrNull(3) as? Number)?.toInt() ?: 0
                val factionName = if (nationIdx > 0 && nationIdx <= factions.size) factions[nationIdx - 1].name else "무소속"
                ScenarioCharacterInfo(name, factionName, nationIdx)
            }
            ResponseEntity.ok(ScenarioDetailResponse(info, factions, originalCharacters))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    /** List available original characters for a world's select pool */
    @GetMapping("/worlds/{worldId}/select-pool")
    fun listSelectPool(@PathVariable worldId: Long): ResponseEntity<List<SelectPoolEntry>> {
        val pool = selectPoolService.listAvailable(worldId)
        return ResponseEntity.ok(pool.map { sp ->
            SelectPoolEntry(
                id = sp.id,
                uniqueName = sp.uniqueName,
                info = sp.info,
                reserved = sp.ownerId != null,
            )
        })
    }
}

data class ScenarioFactionInfo(
    val name: String,
    val color: String,
    val description: String,
    val systemCount: Int,
)

data class ScenarioCharacterInfo(
    val name: String,
    val factionName: String,
    val factionIndex: Int,
)

data class ScenarioDetailResponse(
    val scenario: ScenarioInfo,
    val factions: List<ScenarioFactionInfo>,
    val originalCharacters: List<ScenarioCharacterInfo>,
)

data class SelectPoolEntry(
    val id: Long,
    val uniqueName: String,
    val info: Map<String, Any>,
    val reserved: Boolean,
)
