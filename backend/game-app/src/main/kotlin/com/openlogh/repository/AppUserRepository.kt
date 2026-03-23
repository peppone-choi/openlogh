package com.openlogh.repository

import com.openlogh.entity.AppUser
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, Long> {
    fun findByLoginId(loginId: String): AppUser?
    fun findByLoginIdIgnoreCase(loginId: String): AppUser?
    fun existsByLoginId(loginId: String): Boolean
    fun existsByDisplayName(displayName: String): Boolean
}
