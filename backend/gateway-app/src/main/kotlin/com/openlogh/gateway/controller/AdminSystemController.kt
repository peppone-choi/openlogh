package com.openlogh.gateway.controller

import com.openlogh.gateway.repository.AppUserRepository
import com.openlogh.gateway.service.GatewayAdminAuthorizationService
import com.openlogh.gateway.service.SystemSettingsService
import com.openlogh.gateway.service.WorldService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/admin")
class AdminSystemController(
    private val authorizationService: GatewayAdminAuthorizationService,
    private val systemSettingsService: SystemSettingsService,
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val worldService: WorldService,
) {
    data class AuthFlagsResponse(val allowLogin: Boolean, val allowJoin: Boolean)
    data class AuthFlagsPatchRequest(val allowLogin: Boolean? = null, val allowJoin: Boolean? = null)

    data class ScrubRequest(val type: String)
    data class ScrubResponse(val affected: Int)

    data class AdminWorldListEntry(
        val id: Short,
        val scenarioCode: String,
        val year: Short,
        val month: Short,
        val locked: Boolean,
    )

    private fun currentLoginId(): String? = SecurityContextHolder.getContext().authentication?.name

    @GetMapping("/worlds")
    fun listWorlds(): ResponseEntity<List<AdminWorldListEntry>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            authorizationService.requireGlobalAdmin(loginId)
            val worlds = worldService.listWorlds().map { w ->
                AdminWorldListEntry(
                    id = w.id,
                    scenarioCode = w.scenarioCode,
                    year = w.currentYear,
                    month = w.currentMonth,
                    locked = w.config["locked"] as? Boolean ?: false,
                )
            }
            ResponseEntity.ok(worlds)
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @GetMapping("/system-flags")
    fun getSystemFlags(): ResponseEntity<AuthFlagsResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            authorizationService.requireGlobalAdmin(loginId)
            val flags = systemSettingsService.getAuthFlags()
            ResponseEntity.ok(AuthFlagsResponse(flags.allowLogin, flags.allowJoin))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PatchMapping("/system-flags")
    fun patchSystemFlags(@RequestBody req: AuthFlagsPatchRequest): ResponseEntity<AuthFlagsResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            authorizationService.requireGlobalAdmin(loginId)
            val flags = systemSettingsService.updateAuthFlags(req.allowLogin, req.allowJoin)
            ResponseEntity.ok(AuthFlagsResponse(flags.allowLogin, flags.allowJoin))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/scrub")
    fun scrub(@RequestBody req: ScrubRequest): ResponseEntity<ScrubResponse> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            authorizationService.requireGlobalAdmin(loginId)
            val affected = when (req.type) {
                // NOTE: OpenSam doesn't have legacy delete_after/icon storage; provide safe equivalents.
                "scrub_old_user" -> {
                    val threshold = OffsetDateTime.now().minusMonths(6)
                    val candidates = appUserRepository.findAll().filter { u ->
                        val last = u.lastLoginAt ?: u.createdAt
                        u.grade.toInt() < 5 && last.isBefore(threshold)
                    }
                    appUserRepository.deleteAll(candidates)
                    candidates.size
                }
                "scrub_blocked_user" -> {
                    val threshold = OffsetDateTime.now().minusMonths(12)
                    val candidates = appUserRepository.findAll().filter { u ->
                        val last = u.lastLoginAt ?: u.createdAt
                        u.grade.toInt() == 0 && last.isBefore(threshold)
                    }
                    appUserRepository.deleteAll(candidates)
                    candidates.size
                }
                "scrub_deleted" -> {
                    val threshold = OffsetDateTime.now().minusMonths(1)
                    val candidates = appUserRepository.findAll().filter { u ->
                        val deleteReq = u.meta["deleteRequestedAt"] as? String
                        deleteReq != null && runCatching { OffsetDateTime.parse(deleteReq) }.getOrNull()?.isBefore(threshold) == true
                    }
                    appUserRepository.deleteAll(candidates)
                    candidates.size
                }
                "scrub_icon" -> {
                    val threshold = OffsetDateTime.now().minusMonths(1)
                    var count = 0
                    appUserRepository.findAll().forEach { u ->
                        val iconUpdated = u.meta["iconUpdatedAt"] as? String
                        val stale = iconUpdated != null && runCatching { OffsetDateTime.parse(iconUpdated) }.getOrNull()?.isBefore(threshold) == true
                        if (stale && u.meta.containsKey("icon")) {
                            u.meta.remove("icon")
                            u.meta.remove("iconUpdatedAt")
                            appUserRepository.save(u)
                            count++
                        }
                    }
                    count
                }
                else -> return ResponseEntity.badRequest().build()
            }
            ResponseEntity.ok(ScrubResponse(affected))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }

    @PostMapping("/users/{id}/reset-password")
    fun resetPassword(@PathVariable id: Long): ResponseEntity<Map<String, String>> {
        val loginId = currentLoginId() ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return try {
            authorizationService.requireGlobalAdmin(loginId)
            authorizationService.requireLowerGrade(loginId, id)
            val user = appUserRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
            val raw = (1..8).map { ("ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random()) }.joinToString("")
            user.passwordHash = passwordEncoder.encode(raw)
            user.meta["tempPasswordIssuedAt"] = OffsetDateTime.now().toString()
            appUserRepository.save(user)
            ResponseEntity.ok(mapOf("tempPassword" to raw))
        } catch (_: AccessDeniedException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
    }
}
