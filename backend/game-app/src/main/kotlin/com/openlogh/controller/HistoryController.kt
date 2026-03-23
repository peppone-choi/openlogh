package com.openlogh.controller

import com.openlogh.dto.RecordResponse
import com.openlogh.dto.YearbookSummaryResponse
import com.openlogh.service.HistoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class HistoryController(
    private val historyService: HistoryService,
    private val worldStateRepository: com.openlogh.repository.SessionStateRepository,
) {
    @GetMapping("/worlds/{sessionId}/history")
    fun getWorldHistory(
        @PathVariable sessionId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<List<RecordResponse>> {
        val data = if (year != null && month != null) {
            historyService.getByYearMonth(sessionId, year, month)
        } else {
            historyService.getWorldHistory(sessionId)
        }
        val world = worldStateRepository.findById(sessionId.toShort()).orElse(null)
        val currentYear = world?.currentYear?.toInt() ?: Int.MAX_VALUE
        val currentMonth = world?.currentMonth?.toInt() ?: 12
        val filtered = data.filter { record ->
            record.year < currentYear || (record.year == currentYear && record.month <= currentMonth)
        }
        return ResponseEntity.ok(filtered.map { RecordResponse.from(it) })
    }

    @GetMapping("/worlds/{sessionId}/records")
    fun getWorldRecords(@PathVariable sessionId: Long): ResponseEntity<List<RecordResponse>> {
        return ResponseEntity.ok(historyService.getWorldRecords(sessionId).map { RecordResponse.from(it) })
    }

    @GetMapping("/officers/{officerId}/records")
    fun getOfficerRecords(@PathVariable officerId: Long): ResponseEntity<List<RecordResponse>> {
        return ResponseEntity.ok(historyService.getOfficerRecords(officerId).map { RecordResponse.from(it) })
    }

    @GetMapping("/worlds/{sessionId}/history/yearbook")
    fun getYearbook(
        @PathVariable sessionId: Long,
        @RequestParam year: Int,
    ): ResponseEntity<YearbookSummaryResponse> {
        val yearbook = historyService.getYearbook(sessionId, year) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(YearbookSummaryResponse.from(sessionId, yearbook))
    }
}
