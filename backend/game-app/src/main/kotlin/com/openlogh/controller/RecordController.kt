package com.openlogh.controller

import com.openlogh.service.RecordService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/records")
class RecordController(
    private val recordService: RecordService,
) {
    @GetMapping("/general-actions")
    fun getGeneralActions(
        @RequestParam generalId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getGeneralActions(generalId, beforeId, limit)

    @GetMapping("/general-records")
    fun getGeneralRecords(
        @RequestParam generalId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getGeneralRecords(generalId, beforeId, limit)

    @GetMapping("/world-records")
    fun getWorldRecords(
        @RequestParam worldId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getWorldRecords(worldId, beforeId, limit)

    @GetMapping("/world-history")
    fun getWorldHistory(
        @RequestParam worldId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getWorldHistory(worldId, beforeId, limit)

    @GetMapping("/nation-history")
    fun getNationHistory(
        @RequestParam nationId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getNationHistory(nationId, beforeId, limit)

    @GetMapping("/battle-results")
    fun getBattleResults(
        @RequestParam generalId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getBattleResults(generalId, beforeId, limit)

    @GetMapping("/battle-details")
    fun getBattleDetails(
        @RequestParam generalId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getBattleDetails(generalId, beforeId, limit)

    @GetMapping("/old-logs")
    fun getOldLogs(
        @RequestParam generalId: Long,
        @RequestParam targetId: Long,
        @RequestParam type: String,
        @RequestParam toId: Long,
    ) = recordService.getOldLogs(generalId, targetId, type, toId)
}
