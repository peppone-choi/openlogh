package com.openlogh.service

import com.openlogh.entity.*
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.WorldStateRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.access.AccessDeniedException

class AdminAuthorizationServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var service: AdminAuthorizationService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        service = AdminAuthorizationService(appUserRepository, worldStateRepository)
    }

    @Test
    fun `requireGlobalAdmin allows system admin grade`() {
        `when`(appUserRepository.findByLoginId("system-admin")).thenReturn(
            AppUser(
                id = 1,
                loginId = "system-admin",
                displayName = "관리자",
                passwordHash = "hash",
                grade = 6,
            )
        )

        assertDoesNotThrow {
            service.requireGlobalAdmin("system-admin")
        }
    }

    @Test
    fun `requireGlobalAdmin rejects regular user`() {
        `when`(appUserRepository.findByLoginId("user")).thenReturn(
            AppUser(
                id = 2,
                loginId = "user",
                displayName = "유저",
                passwordHash = "hash",
                grade = 1,
            )
        )

        assertThrows(AccessDeniedException::class.java) {
            service.requireGlobalAdmin("user")
        }
    }
}
