package com.opensam.gateway.repository

import com.opensam.gateway.entity.AppUser
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByLoginId(loginId: String): AppUser?
    fun existsByLoginId(loginId: String): Boolean

    @Modifying
    @Transactional
    @Query("UPDATE AppUser u SET u.lastLoginAt = :lastLoginAt, u.role = :role WHERE u.id = :id")
    fun updateLoginInfo(
        @Param("id") id: Long,
        @Param("role") role: String,
        @Param("lastLoginAt") lastLoginAt: OffsetDateTime,
    )
}
