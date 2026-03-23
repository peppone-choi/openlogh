package com.openlogh.controller

import com.openlogh.dto.FrontInfoResponse
import com.openlogh.service.FrontInfoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class FrontInfoController(
    private val frontInfoService: FrontInfoService,
) {
    @GetMapping("/worlds/{worldId}/front-info")
    fun getFrontInfo(
        @PathVariable worldId: Long,
        @RequestParam(required = false) lastRecordId: Long?,
        @RequestParam(required = false) lastHistoryId: Long?,
    ): ResponseEntity<FrontInfoResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            ResponseEntity.ok(
                frontInfoService.getFrontInfo(worldId.toShort(), loginId, lastRecordId, lastHistoryId)
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    private fun currentLoginId(): String? =
        SecurityContextHolder.getContext().authentication?.name
}
