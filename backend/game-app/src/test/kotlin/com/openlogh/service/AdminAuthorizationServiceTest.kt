package com.openlogh.service

import com.openlogh.entity.AppUser
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.access.AccessDeniedException

class AdminAuthorizationServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var service: AdminAuthorizationService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        service = AdminAuthorizationService(appUserRepository, sessionStateRepository)
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
