package com.openlogh.controller

import com.openlogh.dto.AdminDashboard
import com.openlogh.dto.AdminOfficerAction
import com.openlogh.dto.AdminOfficerSummary
import com.openlogh.dto.AdminUserSummary
import com.openlogh.dto.AdminUserAction
import com.openlogh.dto.BroadcastRequest
import com.openlogh.dto.FactionStatistic
import com.openlogh.dto.TimeControlRequest
import com.openlogh.service.AdminAuthorizationService
import com.openlogh.service.AdminService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val adminService: AdminService,
    private val adminAuthorizationService: AdminAuthorizationService,
) {
    private companion object {
        const val PERMISSION_OPEN_CLOSE = "openClose"
        const val PERMISSION_BLOCK_GENERAL = "blockGeneral"
    }

    @GetMapping("/dashboard")
    fun getDashboard(@RequestParam(required = false) sessionId: Long?): ResponseEntity<AdminDashboard> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getDashboard(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PatchMapping("/settings")
    fun updateSettings(
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody settings: Map<String, Any>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            if (!adminService.updateSettings(resolvedWorldId, settings)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/officers")
    fun listAllOfficers(@RequestParam(required = false) sessionId: Long?): ResponseEntity<List<AdminOfficerSummary>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.listAllOfficers(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/officers/{id}/action")
    fun officerAction(
        @PathVariable id: Long,
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody action: AdminOfficerAction,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_BLOCK_GENERAL)
            if (!adminService.officerAction(resolvedWorldId, id, action.type)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/statistics")
    fun getStatistics(@RequestParam(required = false) sessionId: Long?): ResponseEntity<List<FactionStatistic>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getStatistics(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/officers/{id}/logs")
    fun getOfficerLogs(
        @PathVariable id: Long,
        @RequestParam(required = false) sessionId: Long?,
    ): ResponseEntity<List<Any>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getGeneralLogs(resolvedWorldId, id))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/diplomacy")
    fun getDiplomacyMatrix(@RequestParam(required = false) sessionId: Long?): ResponseEntity<List<Any>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getDiplomacyMatrix(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/time-control")
    fun timeControl(
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody request: TimeControlRequest,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            if (!adminService.timeControl(resolvedWorldId, request)) {
                return ResponseEntity.notFound().build()
            }
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/users")
    fun listUsers(): ResponseEntity<List<AdminUserSummary>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        return ResponseEntity.ok(adminService.listUsers())
    }

    @PostMapping("/users/{id}/action")
    fun userAction(
        @PathVariable id: Long,
        @RequestBody action: AdminUserAction,
    ): ResponseEntity<Void> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireGlobalAdmin(loginId)
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        if (!adminService.userAction(loginId, id, action)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/write-log")
    fun writeLog(
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            val message = body["message"] ?: return ResponseEntity.badRequest().build()
            if (!adminService.writeLog(resolvedWorldId, message)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/officers/bulk-action")
    fun bulkOfficerAction(
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_BLOCK_GENERAL)
            val ids = readLongList(body["ids"]) ?: return ResponseEntity.badRequest().build()
            val type = body["type"] as? String ?: return ResponseEntity.badRequest().build()
            for (id in ids) {
                adminService.officerAction(resolvedWorldId, id, type)
            }
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/broadcast")
    fun broadcast(
        @RequestParam(required = false) sessionId: Long?,
        @RequestBody request: BroadcastRequest,
    ): ResponseEntity<Map<String, Boolean>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            adminService.broadcastMessage(resolvedWorldId, request.officerIds, request.message)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/force-rehall")
    fun forceRehall(@RequestParam(required = false) sessionId: Long?): ResponseEntity<Map<String, Int>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, sessionId, PERMISSION_OPEN_CLOSE)
            val result = adminService.forceRehall(resolvedWorldId) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(result)
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun currentLoginId(): String? {
        return SecurityContextHolder.getContext().authentication?.name
    }

    private fun readLongList(raw: Any?): List<Long>? {
        if (raw !is Iterable<*>) return null
        val out = mutableListOf<Long>()
        raw.forEach { entry ->
            val value = when (entry) {
                is Number -> entry.toLong()
                is String -> entry.toLongOrNull()
                else -> null
            } ?: return null
            out.add(value)
        }
        return out
    }
}
