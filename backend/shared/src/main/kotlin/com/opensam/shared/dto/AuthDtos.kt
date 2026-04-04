package com.opensam.shared.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank val loginId: String,
    @field:NotBlank val password: String,
    val otpCode: String? = null,
)

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 4, max = 64) val loginId: String,
    @field:NotBlank @field:Size(min = 1, max = 20) val displayName: String,
    @field:NotBlank @field:Size(min = 6, max = 100) val password: String,
    val agreeTerms: Boolean? = null,
    val agreePrivacy: Boolean? = null,
    val agreeThirdUse: Boolean? = null,
)

data class TokenLoginRequest(
    @field:NotBlank val token: String,
)

data class LoginByTokenRequest(
    @JsonProperty("token_id")
    val tokenId: Long,
    @field:NotBlank val hashedToken: String,
)

data class OtpVerifyRequest(
    @field:NotBlank val otpTicket: String,
    @field:NotBlank val otpCode: String,
)

data class LoginNonceResponse(
    val result: Boolean = true,
    val loginNonce: String,
)

data class AutoLoginResponse(
    val result: Boolean,
    val nextToken: List<Any>? = null,
    val silent: Boolean = false,
    val reason: String? = null,
)

data class OtpChallengeResponse(
    val otpRequired: Boolean = true,
    val otpTicket: String,
    val error: String = "인증 코드를 입력해주세요",
)

data class AuthResponse(
    val token: String,
    val user: UserInfo,
    val nextToken: List<Any>? = null,
    val validUntil: String? = null,
)

data class UserInfo(
    val id: Long,
    val loginId: String,
    val displayName: String,
    val picture: String? = null,
)
