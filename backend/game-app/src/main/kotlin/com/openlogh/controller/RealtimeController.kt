package com.openlogh.controller

import com.openlogh.command.CommandResult
import com.openlogh.dto.RealtimeExecuteRequest
import com.openlogh.engine.RealtimeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/realtime")
class RealtimeController(
    private val realtimeService: RealtimeService,
) {
    // POST /api/realtime/execute — 실시간 커맨드 실행
    @PostMapping("/execute")
    fun execute(@RequestBody request: RealtimeExecuteRequest): ResponseEntity<CommandResult> {
        val result = realtimeService.submitCommand(
            request.officerId,
            request.actionCode,
            request.arg,
        )
        return ResponseEntity.ok(result)
    }

    // GET /api/realtime/status/{generalId} — 실시간 상태 조회
    @GetMapping("/status/{generalId}")
    fun getStatus(@PathVariable generalId: Long): ResponseEntity<Any> {
        val status = realtimeService.getRealtimeStatus(generalId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(status)
    }
}
