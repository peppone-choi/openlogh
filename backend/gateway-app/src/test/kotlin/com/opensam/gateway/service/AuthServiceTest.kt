package com.opensam.gateway.service

import com.opensam.gateway.config.JwtUtil
import com.opensam.gateway.entity.AppUser
import com.opensam.gateway.repository.AppUserRepository
import com.opensam.shared.dto.LoginByTokenRequest
import com.opensam.shared.dto.LoginRequest
import com.opensam.shared.dto.RegisterRequest
import com.opensam.shared.dto.TokenLoginRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime

class AuthServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtUtil: JwtUtil
    private lateinit var systemSettingsService: SystemSettingsService
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        passwordEncoder = mock(PasswordEncoder::class.java)
        jwtUtil = mock(JwtUtil::class.java)
        systemSettingsService = mock(SystemSettingsService::class.java)

        `when`(systemSettingsService.getAuthFlags()).thenReturn(SystemSettingsService.AuthFlags(allowLogin = true, allowJoin = true))
        `when`(appUserRepository.save(any(AppUser::class.java))).thenAnswer { it.arguments[0] as AppUser }

        service = AuthService(
            appUserRepository,
            passwordEncoder,
            jwtUtil,
            systemSettingsService,
            "",
            "",
        )
    }

    @Test
    fun `register requires terms agreements`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.register(
                RegisterRequest(
                    loginId = "TestUser",
                    displayName = "닉네임",
                    password = "secret123",
                    agreeTerms = false,
                    agreePrivacy = true,
                ),
            )
        }

        assertEquals("약관에 동의해야 가입하실 수 있습니다.", ex.message)
        verify(appUserRepository, never()).save(any(AppUser::class.java))
    }

    @Test
    fun `register normalizes login id stores third use and issues next token`() {
        `when`(appUserRepository.existsByLoginId("testuser")).thenReturn(false)
        `when`(appUserRepository.existsByDisplayName("닉네임")).thenReturn(false)
        `when`(passwordEncoder.encode("secret123")).thenReturn("encoded")
        `when`(jwtUtil.generateToken(0L, "testuser", "닉네임", "USER", 1)).thenReturn("jwt")

        val response = service.register(
            RegisterRequest(
                loginId = "TestUser",
                displayName = "닉네임",
                password = "secret123",
                agreeTerms = true,
                agreePrivacy = true,
                agreeThirdUse = true,
            ),
        )

        assertEquals("jwt", response.token)
        assertEquals("testuser", response.user.loginId)
        assertNotNull(response.nextToken)

        val captor = ArgumentCaptor.forClass(AppUser::class.java)
        verify(appUserRepository, atLeastOnce()).save(captor.capture())
        val savedUser = captor.allValues.last()
        assertEquals("testuser", savedUser.loginId)
        assertEquals(true, savedUser.meta["thirdUse"])
        assertTrue((savedUser.meta["loginTokens"] as? List<*>)?.isNotEmpty() == true)
    }

    @Test
    fun `checkDup validates username and nickname with legacy rules`() {
        `when`(appUserRepository.existsByLoginId("takenid")).thenReturn(true)
        `when`(appUserRepository.existsByDisplayName("중복닉")).thenReturn(true)

        assertEquals("적절하지 않은 길이입니다.", service.checkDup("username", "abc"))
        assertEquals("이미 사용중인 계정명입니다", service.checkDup("username", "takenid"))
        assertEquals("이미 사용중인 닉네임입니다", service.checkDup("nickname", "중복닉"))
        assertEquals("지원하지 않는 중복 검사 항목입니다.", service.checkDup("email", "a@b.c"))
        assertEquals(null, service.checkDup("username", "usableid"))
    }

    @Test
    fun `login rejects pending deletion`() {
        val user = AppUser(
            id = 9,
            loginId = "deleted",
            displayName = "탈퇴유저",
            passwordHash = "encoded",
            meta = mutableMapOf("deleteRequestedAt" to "2026-03-01T00:00:00Z"),
        )
        `when`(appUserRepository.findByLoginId("deleted")).thenReturn(user)
        `when`(passwordEncoder.matches("secret123", "encoded")).thenReturn(true)

        val ex = assertThrows(IllegalStateException::class.java) {
            service.login(LoginRequest("deleted", "secret123"))
        }

        assertEquals("User requested deletion", ex.message)
    }

    @Test
    fun `login reuses active kakao otp challenge`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 11,
            loginId = "kakao_user",
            displayName = "카카오유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "oauthProviders" to mutableListOf(
                    mutableMapOf(
                        "provider" to "kakao",
                        "externalId" to "123",
                        "linkedAt" to now.toString(),
                        "accessToken" to "access",
                        "refreshToken" to "refresh",
                        "accessTokenValidUntil" to now.plusMinutes(10).toString(),
                        "refreshTokenValidUntil" to now.plusDays(10).toString(),
                        "otpTicket" to "otp-ticket",
                        "OTPValue" to "123456",
                        "OTPTrialUntil" to now.plusMinutes(3).toString(),
                        "OTPTrialCount" to 3,
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findByLoginId("kakao_user")).thenReturn(user)
        `when`(passwordEncoder.matches("secret123", "encoded")).thenReturn(true)

        val ex = assertThrows(AuthService.OtpRequiredException::class.java) {
            service.login(LoginRequest("kakao_user", "secret123"))
        }

        assertEquals("otp-ticket", ex.otpTicket)
    }

    @Test
    fun `verifyOtp completes login and issues persistent token`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 12,
            loginId = "kakao_user",
            displayName = "카카오유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "oauthProviders" to mutableListOf(
                    mutableMapOf(
                        "provider" to "kakao",
                        "externalId" to "123",
                        "linkedAt" to now.toString(),
                        "accessToken" to "access",
                        "refreshToken" to "refresh",
                        "accessTokenValidUntil" to now.plusMinutes(10).toString(),
                        "refreshTokenValidUntil" to now.plusDays(10).toString(),
                        "otpTicket" to "otp-ticket",
                        "OTPValue" to "123456",
                        "OTPTrialUntil" to now.plusMinutes(3).toString(),
                        "OTPTrialCount" to 3,
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findAll()).thenReturn(listOf(user))
        `when`(jwtUtil.generateToken(12L, "kakao_user", "카카오유저", "USER", 1)).thenReturn("jwt")

        val response = service.verifyOtp("otp-ticket", "123456")

        assertEquals("jwt", response.token)
        assertNotNull(response.validUntil)
        assertNotNull(response.nextToken)
        val provider = ((user.meta["oauthProviders"] as List<*>).first() as Map<*, *>)
        assertNotNull(provider["tokenValidUntil"])
        assertEquals(null, provider["otpTicket"])
    }

    @Test
    fun `loginByToken rotates expiring persistent token`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 13,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "loginTokens" to mutableListOf(
                    mutableMapOf(
                        "id" to 42L,
                        "baseToken" to "legacy-token",
                        "createdAt" to now.minusDays(5).toString(),
                        "expireDate" to now.plusHours(12).toString(),
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findAll()).thenReturn(listOf(user))

        val nonce = service.requestLoginNonce().loginNonce
        val result = service.loginByToken(
            LoginByTokenRequest(
                tokenId = 42L,
                hashedToken = sha512("legacy-token$nonce"),
            ),
        )

        assertTrue(result.result)
        assertNotNull(result.nextToken)
        assertFalse(result.nextToken!!.contains("legacy-token"))
    }

    @Test
    fun `tokenLogin issues jwt from persistent token`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 14,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "loginTokens" to mutableListOf(
                    mutableMapOf(
                        "id" to 55L,
                        "baseToken" to "legacy-token",
                        "createdAt" to now.minusDays(1).toString(),
                        "expireDate" to now.plusDays(5).toString(),
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findAll()).thenReturn(listOf(user))
        `when`(jwtUtil.generateToken(14L, "user", "유저", "USER", 1)).thenReturn("jwt")

        val response = service.tokenLogin(TokenLoginRequest("legacy-token"))

        assertEquals("jwt", response.token)
        assertEquals("user", response.user.loginId)
        assertNotNull(response.nextToken)
    }

    private fun sha512(value: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
