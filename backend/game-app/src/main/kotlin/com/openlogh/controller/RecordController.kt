package com.openlogh.controller

import com.openlogh.service.RecordService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/records")
class RecordController(
    private val recordService: RecordService,
) {
    @GetMapping("/officer-actions")
    fun getOfficerActions(
        @RequestParam officerId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getGeneralActions(officerId, beforeId, limit)

    @GetMapping("/officer-records")
    fun getOfficerRecords(
        @RequestParam officerId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getOfficerRecords(officerId, beforeId, limit)

    @GetMapping("/world-records")
    fun getWorldRecords(
        @RequestParam sessionId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getWorldRecords(sessionId, beforeId, limit)

    @GetMapping("/world-history")
    fun getWorldHistory(
        @RequestParam sessionId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getWorldHistory(sessionId, beforeId, limit)

    @GetMapping("/faction-history")
    fun getFactionHistory(
        @RequestParam factionId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getNationHistory(factionId, beforeId, limit)

    @GetMapping("/battle-results")
    fun getBattleResults(
        @RequestParam officerId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getBattleResults(officerId, beforeId, limit)

    @GetMapping("/battle-details")
    fun getBattleDetails(
        @RequestParam officerId: Long,
        @RequestParam(required = false) beforeId: Long?,
        @RequestParam(required = false) limit: Int?,
    ) = recordService.getBattleDetails(officerId, beforeId, limit)

    @GetMapping("/old-logs")
    fun getOldLogs(
        @RequestParam officerId: Long,
        @RequestParam targetId: Long,
        @RequestParam type: String,
        @RequestParam toId: Long,
    ) = recordService.getOldLogs(officerId, targetId, type, toId)
}
