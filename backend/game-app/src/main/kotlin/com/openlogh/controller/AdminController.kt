package com.openlogh.controller

import com.openlogh.dto.AdminDashboard
import com.openlogh.dto.AdminGeneralAction
import com.openlogh.dto.AdminGeneralSummary
import com.openlogh.dto.AdminUserSummary
import com.openlogh.dto.AdminUserAction
import com.openlogh.dto.BroadcastRequest
import com.openlogh.dto.NationStatistic
import com.openlogh.dto.TimeControlRequest
import com.openlogh.service.AdminAuthorizationService
import com.openlogh.service.AdminService
import com.openlogh.service.SelectPoolService
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
    private val selectPoolService: SelectPoolService,
) {
    private companion object {
        const val PERMISSION_OPEN_CLOSE = "openClose"
        const val PERMISSION_BLOCK_GENERAL = "blockGeneral"
    }

    @GetMapping("/dashboard")
    fun getDashboard(@RequestParam(required = false) worldId: Long?): ResponseEntity<AdminDashboard> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getDashboard(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PatchMapping("/settings")
    fun updateSettings(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody settings: Map<String, Any>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            if (!adminService.updateSettings(resolvedWorldId, settings)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/generals")
    fun listAllGenerals(@RequestParam(required = false) worldId: Long?): ResponseEntity<List<AdminGeneralSummary>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.listAllGenerals(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/generals/{id}/action")
    fun generalAction(
        @PathVariable id: Long,
        @RequestParam(required = false) worldId: Long?,
        @RequestBody action: AdminGeneralAction,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_BLOCK_GENERAL)
            if (!adminService.generalAction(resolvedWorldId, id, action.type)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/statistics")
    fun getStatistics(@RequestParam(required = false) worldId: Long?): ResponseEntity<List<NationStatistic>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getStatistics(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/generals/{id}/logs")
    fun getGeneralLogs(
        @PathVariable id: Long,
        @RequestParam(required = false) worldId: Long?,
    ): ResponseEntity<List<Any>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getGeneralLogs(resolvedWorldId, id))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/diplomacy")
    fun getDiplomacyMatrix(@RequestParam(required = false) worldId: Long?): ResponseEntity<List<Any>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(adminService.getDiplomacyMatrix(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/time-control")
    fun timeControl(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody request: TimeControlRequest,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
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
        @RequestParam(required = false) worldId: Long?,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            val message = body["message"] ?: return ResponseEntity.badRequest().build()
            if (!adminService.writeLog(resolvedWorldId, message)) return ResponseEntity.notFound().build()
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/generals/bulk-action")
    fun bulkGeneralAction(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_BLOCK_GENERAL)
            val ids = readLongList(body["ids"]) ?: return ResponseEntity.badRequest().build()
            val type = body["type"] as? String ?: return ResponseEntity.badRequest().build()
            for (id in ids) {
                adminService.generalAction(resolvedWorldId, id, type)
            }
            ResponseEntity.ok().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/broadcast")
    fun broadcast(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody request: BroadcastRequest,
    ): ResponseEntity<Map<String, Boolean>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            adminService.broadcastMessage(resolvedWorldId, request.generalIds, request.message)
            ResponseEntity.ok(mapOf("success" to true))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/force-rehall")
    fun forceRehall(@RequestParam(required = false) worldId: Long?): ResponseEntity<Map<String, Int>> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
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

    @GetMapping("/select-pool")
    fun listSelectPools(@RequestParam(required = false) worldId: Long?): ResponseEntity<Any> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.ok(selectPoolService.listAll(resolvedWorldId))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/select-pool")
    fun createSelectPool(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Any> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            val uniqueName = (body["uniqueName"] as? String) ?: return ResponseEntity.badRequest().build()
            ResponseEntity.status(HttpStatus.CREATED).body(selectPoolService.create(resolvedWorldId, uniqueName, body))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/select-pool/bulk")
    fun bulkCreateSelectPool(
        @RequestParam(required = false) worldId: Long?,
        @RequestBody entries: List<Map<String, Any>>,
    ): ResponseEntity<Any> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            val resolvedWorldId = adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            ResponseEntity.status(HttpStatus.CREATED).body(selectPoolService.bulkCreate(resolvedWorldId, entries))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PutMapping("/select-pool/{id}")
    fun updateSelectPool(
        @PathVariable id: Long,
        @RequestParam(required = false) worldId: Long?,
        @RequestBody body: Map<String, Any>,
    ): ResponseEntity<Any> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            val updated = selectPoolService.update(id, body) ?: return ResponseEntity.notFound().build()
            ResponseEntity.ok(updated)
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @DeleteMapping("/select-pool/{id}")
    fun deleteSelectPool(
        @PathVariable id: Long,
        @RequestParam(required = false) worldId: Long?,
    ): ResponseEntity<Void> {
        return try {
            val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            adminAuthorizationService.resolveWorldIdOrThrow(loginId, worldId, PERMISSION_OPEN_CLOSE)
            if (selectPoolService.delete(id)) ResponseEntity.noContent().build()
            else ResponseEntity.notFound().build()
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }
}
