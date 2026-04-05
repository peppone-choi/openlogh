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
    private val sessionStateRepository: com.openlogh.repository.SessionStateRepository,
) {
    @GetMapping("/worlds/{worldId}/history")
    fun getWorldHistory(
        @PathVariable worldId: Long,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
    ): ResponseEntity<List<RecordResponse>> {
        val data = if (year != null && month != null) {
            historyService.getByYearMonth(worldId, year, month)
        } else {
            historyService.getWorldHistory(worldId)
        }
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
        val currentYear = world?.currentYear?.toInt() ?: Int.MAX_VALUE
        val currentMonth = world?.currentMonth?.toInt() ?: 12
        val filtered = data.filter { record ->
            record.year < currentYear || (record.year == currentYear && record.month <= currentMonth)
        }
        return ResponseEntity.ok(filtered.map { RecordResponse.from(it) })
    }

    @GetMapping("/worlds/{worldId}/records")
    fun getWorldRecords(@PathVariable worldId: Long): ResponseEntity<List<RecordResponse>> {
        return ResponseEntity.ok(historyService.getWorldRecords(worldId).map { RecordResponse.from(it) })
    }

    @GetMapping("/generals/{generalId}/records")
    fun getGeneralRecords(@PathVariable generalId: Long): ResponseEntity<List<RecordResponse>> {
        return ResponseEntity.ok(historyService.getGeneralRecords(generalId).map { RecordResponse.from(it) })
    }

    @GetMapping("/worlds/{worldId}/history/yearbook")
    fun getYearbook(
        @PathVariable worldId: Long,
        @RequestParam year: Int,
    ): ResponseEntity<YearbookSummaryResponse> {
        val yearbook = historyService.getYearbook(worldId, year) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(YearbookSummaryResponse.from(worldId, yearbook))
    }
}
