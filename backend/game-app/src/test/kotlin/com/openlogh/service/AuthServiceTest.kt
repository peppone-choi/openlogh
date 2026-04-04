package com.openlogh.service

import com.openlogh.config.JwtUtil
import com.openlogh.dto.LoginByTokenRequest
import com.openlogh.dto.LoginRequest
import com.openlogh.dto.TokenLoginRequest
import com.openlogh.entity.AppUser
import com.openlogh.repository.AppUserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime

class AuthServiceTest {
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtUtil: JwtUtil
    private lateinit var service: AuthService

    @BeforeEach
    fun setUp() {
        appUserRepository = mock(AppUserRepository::class.java)
        passwordEncoder = mock(PasswordEncoder::class.java)
        jwtUtil = mock(JwtUtil::class.java)
        `when`(appUserRepository.save(any(AppUser::class.java))).thenAnswer { it.arguments[0] as AppUser }

        service = AuthService(
            appUserRepository,
            passwordEncoder,
            jwtUtil,
            "",
            "",
        )
    }

    @Test
    fun `login reuses active kakao otp challenge`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 1,
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
    fun `token endpoints validate and rotate persistent token`() {
        val now = OffsetDateTime.now()
        val user = AppUser(
            id = 2,
            loginId = "user",
            displayName = "유저",
            passwordHash = "encoded",
            meta = mutableMapOf(
                "loginTokens" to mutableListOf(
                    mutableMapOf(
                        "id" to 77L,
                        "baseToken" to "legacy-token",
                        "createdAt" to now.minusDays(5).toString(),
                        "expireDate" to now.plusHours(4).toString(),
                    ),
                ),
            ),
        )
        `when`(appUserRepository.findAll()).thenReturn(listOf(user))
        `when`(jwtUtil.generateToken(2L, "user", "유저", "USER", 1)).thenReturn("jwt")

        val nonce = service.requestLoginNonce().loginNonce
        val autoResult = service.loginByToken(
            LoginByTokenRequest(
                tokenId = 77L,
                hashedToken = sha512("legacy-token$nonce"),
            ),
        )
        val sessionToken = (autoResult.nextToken?.get(1) ?: "legacy-token") as String
        val tokenResult = service.tokenLogin(TokenLoginRequest(sessionToken))

        assertTrue(autoResult.result)
        assertNotNull(autoResult.nextToken)
        assertEquals("jwt", tokenResult.token)
        assertNotNull(tokenResult.nextToken)
    }

    private fun sha512(value: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
