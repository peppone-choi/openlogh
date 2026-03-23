package com.openlogh.gateway.controller

import com.openlogh.gateway.service.AuthService
import com.openlogh.shared.dto.LoginByTokenRequest
import com.openlogh.shared.dto.LoginRequest
import com.openlogh.shared.dto.OtpVerifyRequest
import com.openlogh.shared.dto.RegisterRequest
import com.openlogh.shared.dto.TokenLoginRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


data class OAuthLoginRequest(
    val provider: String,
    val code: String,
    val redirectUri: String,
)

data class OAuthRegisterRequest(
    val provider: String,
    val code: String,
    val redirectUri: String,
    val displayName: String? = null,
    val agreeTerms: Boolean? = null,
    val agreePrivacy: Boolean? = null,
    val agreeThirdUse: Boolean? = null,
)

data class DupCheckRequest(
    val field: String,
    val value: String,
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(authService.login(request))
        } catch (e: AuthService.OtpRequiredException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "otpRequired" to true,
                    "otpTicket" to e.otpTicket,
                    "error" to e.message,
                ),
            )
        } catch (e: AuthService.OtpValidationException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "reset" to e.reset,
                    "reason" to e.message,
                ),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to (e.message ?: "invalid credentials")))
        }
    }

    @PostMapping("/nonce")
    fun nonce(): ResponseEntity<Any> = ResponseEntity.ok(authService.requestLoginNonce())

    @PostMapping("/login-by-token")
    fun loginByToken(@Valid @RequestBody request: LoginByTokenRequest): ResponseEntity<Any> {
        return ResponseEntity.ok(authService.loginByToken(request))
    }

    @PostMapping("/token-login")
    fun tokenLogin(@Valid @RequestBody request: TokenLoginRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(authService.tokenLogin(request))
        } catch (e: AuthService.OtpRequiredException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "otpRequired" to true,
                    "otpTicket" to e.otpTicket,
                    "error" to e.message,
                ),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to (e.message ?: "invalid token")))
        }
    }

    @PostMapping("/otp/verify")
    fun verifyOtp(@Valid @RequestBody request: OtpVerifyRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(authService.verifyOtp(request.otpTicket, request.otpCode))
        } catch (e: AuthService.OtpValidationException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "reset" to e.reset,
                    "reason" to e.message,
                ),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }

    @PostMapping("/check-dup")
    fun checkDup(@RequestBody request: DupCheckRequest): ResponseEntity<Map<String, Any>> {
        val reason = authService.checkDup(request.field, request.value)
        return ResponseEntity.ok(
            if (reason == null) {
                mapOf("result" to true)
            } else {
                mapOf("result" to false, "reason" to reason)
            },
        )
    }

    @PostMapping("/oauth/login")
    fun oauthLogin(@RequestBody request: OAuthLoginRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(authService.oauthLogin(request.provider, request.code, request.redirectUri))
        } catch (e: AuthService.OtpRequiredException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "otpRequired" to true,
                    "otpTicket" to e.otpTicket,
                    "error" to e.message,
                ),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }

    @PostMapping("/oauth/register")
    fun oauthRegister(@RequestBody request: OAuthRegisterRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.status(HttpStatus.CREATED)
                .body(
                    authService.oauthRegister(
                        request.provider,
                        request.code,
                        request.redirectUri,
                        request.displayName,
                        request.agreeTerms,
                        request.agreePrivacy,
                        request.agreeThirdUse,
                    )
                )
        } catch (e: AuthService.OtpRequiredException) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                mapOf(
                    "otpRequired" to true,
                    "otpTicket" to e.otpTicket,
                    "error" to e.message,
                ),
            )
        } catch (e: IllegalStateException) {
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "forbidden")))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "bad request")))
        }
    }
}
