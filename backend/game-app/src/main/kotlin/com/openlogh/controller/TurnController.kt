package com.openlogh.controller

import com.openlogh.engine.TickDaemon
import com.openlogh.service.AdminAuthorizationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

/**
 * Admin controls for the real-time tick daemon.
 * Provides pause/resume/status/manual-run endpoints.
 */
@RestController
@RequestMapping("/api/turns")
class TurnController(
    private val turnDaemon: TickDaemon,
    private val adminAuthorizationService: AdminAuthorizationService,
) {
    private companion object {
        const val PERMISSION_OPEN_CLOSE = "openClose"
    }

    private fun currentLoginId(): String? = SecurityContextHolder.getContext().authentication?.name

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("state" to turnDaemon.getStatus().name))
    }

    @PostMapping("/run")
    fun manualRun(): ResponseEntity<Map<String, String>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireAnyPermission(
                loginId,
                listOf(PERMISSION_OPEN_CLOSE, "admin.profiles.manage"),
            )
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        turnDaemon.manualRun()
        return ResponseEntity.ok(mapOf("result" to "triggered"))
    }

    @PostMapping("/pause")
    fun pause(): ResponseEntity<Map<String, String>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireAnyPermission(
                loginId,
                listOf(PERMISSION_OPEN_CLOSE, "admin.profiles.manage"),
            )
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        turnDaemon.pause()
        return ResponseEntity.ok(mapOf("state" to turnDaemon.getStatus().name))
    }

    @PostMapping("/resume")
    fun resume(): ResponseEntity<Map<String, String>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        try {
            adminAuthorizationService.requireAnyPermission(
                loginId,
                listOf(
                    PERMISSION_OPEN_CLOSE,
                    "admin.profiles.manage",
                    "admin.resume.when-stopped",
                ),
            )
        } catch (_: AccessDeniedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        turnDaemon.resume()
        return ResponseEntity.ok(mapOf("state" to turnDaemon.getStatus().name))
    }
}
