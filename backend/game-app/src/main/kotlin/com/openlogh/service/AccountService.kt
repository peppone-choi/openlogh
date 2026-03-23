package com.openlogh.service

import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class AccountService(
    private val appUserRepository: AppUserRepository,
    private val officerRepository: OfficerRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    fun changePassword(loginId: String, currentPassword: String, newPassword: String): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        if (!passwordEncoder.matches(currentPassword, user.passwordHash)) return false
        user.passwordHash = passwordEncoder.encode(newPassword)
        appUserRepository.save(user)
        return true
    }

    fun deleteAccount(loginId: String, password: String): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        if (!passwordEncoder.matches(password, user.passwordHash)) return false
        if (user.meta["deleteRequestedAt"] != null) return false

        val now = OffsetDateTime.now()
        val deleteAfter = now.plusMonths(1)
        user.meta["deleteRequestedAt"] = now.toString()
        user.meta["deleteAfter"] = deleteAfter.toString()
        appUserRepository.save(user)
        return true
    }

    fun updateSettings(
        loginId: String,
        defenceTrain: Int?,
        tournamentState: Int?,
        potionThreshold: Int?,
        autoFactionTurn: Boolean?,
        preRiseDelete: Boolean?,
        preOpenDelete: Boolean?,
        borderReturn: Boolean?,
        customCss: String?,
        thirdUse: Boolean?,
        picture: String?,
    ): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val officers = officerRepository.findByUserId(user.id)
        var userChanged = false

        thirdUse?.let {
            user.meta["thirdUse"] = it
            userChanged = true
        }

        picture?.let {
            if (it.isBlank()) {
                user.meta.remove("picture")
                user.meta.remove("imageServer")
            } else {
                user.meta["picture"] = it
                user.meta["imageServer"] = 0
            }
            userChanged = true
        }

        officers.forEach { gen ->
            defenceTrain?.let { gen.defenceTrain = it.toShort() }
            tournamentState?.let { gen.tournamentState = it.toShort() }
            picture?.let {
                gen.picture = it
                gen.imageServer = 0
            }

            val meta = gen.meta.toMutableMap()
            potionThreshold?.let { meta["potionThreshold"] = it }
            autoFactionTurn?.let { meta["autoFactionTurn"] = it }
            preRiseDelete?.let { meta["preRiseDelete"] = it }
            preOpenDelete?.let { meta["preOpenDelete"] = it }
            borderReturn?.let { meta["borderReturn"] = it }
            customCss?.let { meta["customCss"] = it }
            gen.meta = meta

            officerRepository.save(gen)
        }
        if (userChanged) {
            appUserRepository.save(user)
        }
        return true
    }

    fun toggleVacation(loginId: String): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        val officers = officerRepository.findByUserId(user.id)
        officers.forEach { gen ->
            val current = gen.meta["vacationMode"] as? Boolean ?: false
            gen.meta["vacationMode"] = !current
            officerRepository.save(gen)
        }
        return true
    }

    fun updateIconUrl(loginId: String, iconUrl: String): Boolean {
        val user = appUserRepository.findByLoginId(loginId) ?: return false
        if (iconUrl.isBlank()) {
            user.meta.remove("picture")
            user.meta.remove("imageServer")
        } else {
            user.meta["picture"] = iconUrl
            user.meta["imageServer"] = 0
        }
        appUserRepository.save(user)
        return true
    }

    fun getDetailedInfo(loginId: String): Map<String, Any?>? {
        val user = appUserRepository.findByLoginId(loginId) ?: return null
        val oauthEntry = (user.meta["oauthProviders"] as? List<*>)
            ?.firstOrNull() as? Map<*, *>
        return mapOf(
            "loginId" to user.loginId,
            "displayName" to user.displayName,
            "grade" to user.grade.toInt(),
            "role" to user.role,
            "joinDate" to user.createdAt.toString(),
            "lastLoginAt" to user.lastLoginAt?.toString(),
            "thirdUse" to (user.meta["thirdUse"] as? Boolean ?: false),
            "oauthType" to oauthEntry?.get("provider")?.toString()?.uppercase(),
            "tokenValidUntil" to (oauthEntry?.get("tokenValidUntil") ?: oauthEntry?.get("accessTokenValidUntil") ?: user.meta["oauthExpiresAt"]),
            "acl" to user.meta["acl"],
        )
    }
}
