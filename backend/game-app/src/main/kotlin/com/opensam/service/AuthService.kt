package com.opensam.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.opensam.config.JwtUtil
import com.opensam.dto.AuthResponse
import com.opensam.dto.AutoLoginResponse
import com.opensam.dto.LoginByTokenRequest
import com.opensam.dto.LoginNonceResponse
import com.opensam.dto.LoginRequest
import com.opensam.dto.RegisterRequest
import com.opensam.dto.TokenLoginRequest
import com.opensam.dto.UserInfo
import com.opensam.entity.AppUser
import com.opensam.repository.AppUserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

@Service
class AuthService(
    private val userRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtUtil: JwtUtil,
    @Value("\${KAKAO_REST_API_KEY:}") private val kakaoRestApiKey: String,
    @Value("\${APP_WEB_URL:}") private val appWebUrl: String,
) {
    private val http = HttpClient.newBuilder().build()
    private val mapper = ObjectMapper()
    private val loginNonces = ConcurrentHashMap<String, OffsetDateTime>()

    fun register(request: RegisterRequest): AuthResponse {
        val normalizedLoginId = normalizeLoginId(request.loginId)
        val displayName = request.displayName.trim()

        requireRegistrationAgreements(request.agreeTerms, request.agreePrivacy)
        checkDup("username", normalizedLoginId)?.let { throw IllegalArgumentException(it) }
        checkDup("nickname", displayName)?.let { throw IllegalArgumentException(it) }

        val meta = mutableMapOf<String, Any>()
        request.agreeThirdUse?.let { meta["thirdUse"] = it }

        val user = AppUser(
            loginId = normalizedLoginId,
            displayName = displayName,
            passwordHash = passwordEncoder.encode(request.password),
            meta = meta,
        )
        val saved = userRepository.save(user)
        return loginUser(saved)
    }

    fun checkDup(field: String, rawValue: String): String? {
        val value = rawValue.trim()
        return when (field.lowercase()) {
            "username", "loginid" -> checkUsernameDup(value)
            "nickname", "displayname" -> checkNicknameDup(value)
            else -> "지원하지 않는 중복 검사 항목입니다."
        }
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByLoginId(normalizeLoginId(request.loginId))
            ?: throw IllegalArgumentException("Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }

        ensureNotBlocked(user)
        val validUntil = handleKakaoOtpFlow(user, request.otpCode)
        return loginUser(user, validUntil)
    }

    fun requestLoginNonce(): LoginNonceResponse {
        cleanupExpiredNonces()
        val loginNonce = randomToken(16)
        loginNonces[loginNonce] = OffsetDateTime.now().plusSeconds(2)
        return LoginNonceResponse(loginNonce = loginNonce)
    }

    fun loginByToken(request: LoginByTokenRequest): AutoLoginResponse {
        val tokenContext = findLoginTokenById(request.tokenId)
            ?: return AutoLoginResponse(result = false, silent = true, reason = "failed")
        val loginNonce = consumeMatchingNonce(tokenContext.token["baseToken"]?.toString(), request.hashedToken)
            ?: return AutoLoginResponse(result = false, silent = true, reason = "failed")

        val expected = sha512("${tokenContext.token["baseToken"]}${loginNonce}")
        if (!expected.equals(request.hashedToken, ignoreCase = true)) {
            return AutoLoginResponse(result = false, silent = true, reason = "failed")
        }

        return try {
            ensureNotBlocked(tokenContext.user)
            handleKakaoOtpFlow(tokenContext.user, null)

            val expireDate = parseOffsetDateTime(tokenContext.token["expireDate"]?.toString())
            val nextToken = if (expireDate == null || expireDate.isBefore(OffsetDateTime.now().plusDays(2))) {
                tokenContext.tokens.removeIf { readLong(it["id"]) == request.tokenId }
                addLoginToken(tokenContext.user, tokenContext.tokens)
            } else {
                null
            }

            saveLoginTokens(tokenContext.user, tokenContext.tokens)
            userRepository.save(tokenContext.user)

            AutoLoginResponse(result = true, nextToken = nextToken, silent = true, reason = "success")
        } catch (_: OtpRequiredException) {
            tokenContext.tokens.removeIf { readLong(it["id"]) == request.tokenId }
            saveLoginTokens(tokenContext.user, tokenContext.tokens)
            userRepository.save(tokenContext.user)
            AutoLoginResponse(result = false, silent = true, reason = "failed")
        } catch (_: IllegalArgumentException) {
            AutoLoginResponse(result = false, silent = true, reason = "failed")
        } catch (_: IllegalStateException) {
            AutoLoginResponse(result = false, silent = true, reason = "failed")
        }
    }

    fun tokenLogin(request: TokenLoginRequest): AuthResponse {
        val tokenContext = findLoginTokenByValue(request.token)
            ?: throw IllegalArgumentException("Invalid login token")

        ensureNotBlocked(tokenContext.user)
        val validUntil = handleKakaoOtpFlow(tokenContext.user, null)

        val expireDate = parseOffsetDateTime(tokenContext.token["expireDate"]?.toString())
        val nextToken = if (expireDate == null || expireDate.isBefore(OffsetDateTime.now().plusDays(2))) {
            tokenContext.tokens.removeIf { readLong(it["id"]) == readLong(tokenContext.token["id"]) }
            addLoginToken(tokenContext.user, tokenContext.tokens)
        } else {
            null
        }

        return loginUser(tokenContext.user, validUntil, tokenContext.tokens, nextToken)
    }

    fun verifyOtp(otpTicket: String, otpCode: String): AuthResponse {
        val otpContext = findUserByOtpTicket(otpTicket)
            ?: throw OtpValidationException(reset = true, message = "인증 코드를 입력할 수 있는 상태가 아닙니다.")

        ensureNotBlocked(otpContext.user)
        val validUntil = verifyOtpChallenge(otpContext.user, otpContext.provider, otpTicket, otpCode)
        return loginUser(otpContext.user, validUntil, otpContext.tokens)
    }

    private fun loginUser(
        user: AppUser,
        validUntil: OffsetDateTime? = null,
        existingTokens: MutableList<MutableMap<String, Any?>>? = null,
        nextToken: List<Any>? = null,
    ): AuthResponse {
        val role = effectiveRole(user)
        val now = OffsetDateTime.now()
        val tokens = existingTokens ?: getLoginTokens(user)
        scrubLoginTokens(tokens, now)
        val loginToken = nextToken ?: addLoginToken(user, tokens, now)

        user.role = role
        user.lastLoginAt = now
        saveLoginTokens(user, tokens)
        userRepository.save(user)

        val token = jwtUtil.generateToken(user.id, user.loginId, user.displayName, role, user.grade.toInt())
        return AuthResponse(
            token = token,
            user = UserInfo(user.id, user.loginId, user.displayName, user.meta["picture"] as? String),
            nextToken = loginToken,
            validUntil = validUntil?.toString(),
        )
    }

    private fun handleKakaoOtpFlow(user: AppUser, otpCode: String?): OffsetDateTime? {
        val providerContext = getKakaoProvider(user) ?: return null
        val provider = refreshKakaoAccessToken(providerContext.provider, user)
        val tokenValidUntil = parseOffsetDateTime(provider["tokenValidUntil"]?.toString())
        val now = OffsetDateTime.now()

        if (tokenValidUntil != null && !now.isAfter(tokenValidUntil)) {
            return tokenValidUntil
        }

        if (!otpCode.isNullOrBlank()) {
            return verifyOtpChallenge(user, provider, provider["otpTicket"]?.toString(), otpCode)
        }

        val currentTicket = provider["otpTicket"]?.toString()
        val otpValue = provider["OTPValue"]?.toString()
        val otpTrialUntil = parseOffsetDateTime(provider["OTPTrialUntil"]?.toString())
        if (!currentTicket.isNullOrBlank() && !otpValue.isNullOrBlank() && otpTrialUntil != null && now.isBefore(otpTrialUntil)) {
            throw OtpRequiredException(currentTicket, "인증 코드를 입력해주세요")
        }

        val nextTicket = UUID.randomUUID().toString()
        val nextOtp = ThreadLocalRandom.current().nextInt(1000, 10_000).toString()
        val nextTrialUntil = now.plusSeconds(180)

        if (!sendKakaoOtp(provider["accessToken"]?.toString(), nextOtp, nextTrialUntil)) {
            throw IllegalArgumentException("인증 코드를 보내는데 실패했습니다.")
        }

        provider["otpTicket"] = nextTicket
        provider["OTPValue"] = nextOtp
        provider["OTPTrialUntil"] = nextTrialUntil.toString()
        provider["OTPTrialCount"] = 3
        userRepository.save(user)

        throw OtpRequiredException(nextTicket, "인증 코드를 입력해주세요")
    }

    private fun verifyOtpChallenge(
        user: AppUser,
        provider: MutableMap<String, Any?>,
        otpTicket: String?,
        otpCode: String,
    ): OffsetDateTime {
        val now = OffsetDateTime.now()
        val currentTicket = provider["otpTicket"]?.toString()
        val otpValue = provider["OTPValue"]?.toString()
        val otpTrialUntil = parseOffsetDateTime(provider["OTPTrialUntil"]?.toString())
        val otpTrialCount = (provider["OTPTrialCount"] as? Number)?.toInt() ?: 0

        if (currentTicket.isNullOrBlank() || otpValue.isNullOrBlank() || otpTrialUntil == null || otpTicket != currentTicket) {
            clearOtpState(provider)
            userRepository.save(user)
            throw OtpValidationException(reset = true, message = "인증 코드를 입력할 수 있는 상태가 아닙니다.")
        }
        if (!now.isBefore(otpTrialUntil)) {
            clearOtpState(provider)
            userRepository.save(user)
            throw OtpValidationException(reset = true, message = "인증 기한이 만료되었습니다. 다시 로그인해주세요.")
        }
        if (otpTrialCount <= 0) {
            throw OtpValidationException(reset = false, message = "인증 실패 횟수를 초과했습니다. ${otpTrialUntil}까지 기다려주세요.")
        }
        if (otpValue != otpCode.trim()) {
            provider["OTPTrialCount"] = otpTrialCount - 1
            userRepository.save(user)
            throw OtpValidationException(reset = false, message = "인증 번호가 틀렸습니다. ${otpTrialCount - 1}회 더 시도할 수 있습니다.")
        }

        val validUntil = now.plusDays(10)
        provider["tokenValidUntil"] = validUntil.toString()
        clearOtpState(provider)
        userRepository.save(user)
        return validUntil
    }

    private fun refreshKakaoAccessToken(provider: MutableMap<String, Any?>, user: AppUser): MutableMap<String, Any?> {
        val accessToken = provider["accessToken"]?.toString()
        val refreshToken = provider["refreshToken"]?.toString()
        val accessTokenValidUntil = parseOffsetDateTime(provider["accessTokenValidUntil"]?.toString())
        val refreshTokenValidUntil = parseOffsetDateTime(provider["refreshTokenValidUntil"]?.toString())
        val now = OffsetDateTime.now()

        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank() || accessTokenValidUntil == null || refreshTokenValidUntil == null) {
            throw IllegalArgumentException("OAuth 정보가 보관되어 있지 않습니다. 카카오 로그인을 수행해 주세요.")
        }
        if (now.isAfter(refreshTokenValidUntil)) {
            throw IllegalArgumentException("로그인 토큰이 만료되었습니다. 카카오 로그인을 수행해 주세요.")
        }
        if (!now.isAfter(accessTokenValidUntil)) {
            return provider
        }

        val form = "grant_type=refresh_token" +
            "&client_id=${urlEncode(kakaoRestApiKey)}" +
            "&refresh_token=${urlEncode(refreshToken)}"

        val tokenReq = HttpRequest.newBuilder(URI.create("https://kauth.kakao.com/oauth/token"))
            .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val tokenRes = http.send(tokenReq, HttpResponse.BodyHandlers.ofString())
        if (tokenRes.statusCode() !in 200..299) {
            throw IllegalArgumentException("로그인 토큰 자동 갱신을 실패했습니다. 카카오 로그인을 수행해 주세요.")
        }

        val tokenJson = mapper.readTree(tokenRes.body())
        val nextAccessToken = tokenJson["access_token"]?.asText()
            ?: throw IllegalArgumentException("로그인 토큰 자동 갱신을 실패했습니다. 카카오 로그인을 수행해 주세요.")
        provider["accessToken"] = nextAccessToken
        provider["accessTokenValidUntil"] = now.plusSeconds(tokenJson["expires_in"]?.asLong() ?: 0).toString()

        tokenJson["refresh_token"]?.asText()?.let { provider["refreshToken"] = it }
        tokenJson["refresh_token_expires_in"]?.asLong()?.let {
            provider["refreshTokenValidUntil"] = now.plusSeconds(it).toString()
        }

        userRepository.save(user)
        return provider
    }

    private fun sendKakaoOtp(accessToken: String?, otpValue: String, validUntil: OffsetDateTime): Boolean {
        if (accessToken.isNullOrBlank()) {
            return false
        }

        val linkUrl = if (appWebUrl.isNotBlank()) appWebUrl else "https://www.kakao.com"
        val template = mapper.writeValueAsString(
            mapOf(
                "object_type" to "text",
                "text" to "인증 코드는 $otpValue 입니다. $validUntil 이내에 입력해주세요.",
                "link" to mapOf(
                    "web_url" to linkUrl,
                    "mobile_web_url" to linkUrl,
                ),
                "button_title" to "로그인 페이지 열기",
            ),
        )

        val body = "template_object=${urlEncode(template)}"
        val req = HttpRequest.newBuilder(URI.create("https://kapi.kakao.com/v2/api/talk/memo/default/send"))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        return runCatching { http.send(req, HttpResponse.BodyHandlers.ofString()) }
            .map { it.statusCode() in 200..299 }
            .getOrDefault(false)
    }

    private fun ensureNotBlocked(user: AppUser) {
        if (user.meta["deleteRequestedAt"] != null) {
            throw IllegalStateException("User requested deletion")
        }
        val blockedUntil = (user.meta["blockedUntil"] as? String)?.let {
            runCatching { OffsetDateTime.parse(it) }.getOrNull()
        }
        if (user.grade.toInt() == 0 && (blockedUntil == null || blockedUntil.isAfter(OffsetDateTime.now()))) {
            throw IllegalStateException("User is blocked")
        }
    }

    private fun effectiveRole(user: AppUser): String {
        return if (user.grade.toInt() >= 5) "ADMIN" else "USER"
    }

    private fun requireRegistrationAgreements(agreeTerms: Boolean?, agreePrivacy: Boolean?) {
        if (agreeTerms != true) {
            throw IllegalArgumentException("약관에 동의해야 가입하실 수 있습니다.")
        }
        if (agreePrivacy != true) {
            throw IllegalArgumentException("개인정보 제공 및 이용에 대해 동의해야 가입하실 수 있습니다.")
        }
    }

    private fun normalizeLoginId(raw: String): String = raw.trim().lowercase()

    private fun checkUsernameDup(loginId: String): String? {
        if (loginId.isBlank()) return "계정명을 입력해주세요"
        if (loginId.length !in 4..64) return "적절하지 않은 길이입니다."
        if (userRepository.existsByLoginId(loginId)) return "이미 사용중인 계정명입니다"
        return null
    }

    private fun checkNicknameDup(displayName: String): String? {
        if (displayName.isBlank()) return "닉네임을 입력해주세요"
        val width = mbStrWidth(displayName)
        if (width !in 1..18) return "적절하지 않은 길이입니다."
        if (userRepository.existsByDisplayName(displayName)) return "이미 사용중인 닉네임입니다"
        return null
    }

    private fun mbStrWidth(value: String): Int {
        var width = 0
        value.codePoints().forEach { code ->
            width += if (
                (code in 0x1100..0x115f) ||
                (code in 0x2e80..0xa4cf && code != 0x303f) ||
                (code in 0xac00..0xd7a3) ||
                (code in 0xf900..0xfaff) ||
                (code in 0xfe10..0xfe19) ||
                (code in 0xfe30..0xfe6f) ||
                (code in 0xff00..0xff60) ||
                (code in 0xffe0..0xffe6) ||
                (code in 0x20000..0x2fffd) ||
                (code in 0x30000..0x3fffd)
            ) 2 else 1
        }
        return width
    }

    private fun getKakaoProvider(user: AppUser): ProviderContext? {
        val providers = getProviderEntries(user)
        val provider = providers.firstOrNull { it["provider"]?.toString()?.lowercase() == "kakao" } ?: return null
        saveProviders(user, providers)
        return ProviderContext(user, providers, provider)
    }

    private fun getProviderEntries(user: AppUser): MutableList<MutableMap<String, Any?>> {
        val raw = user.meta["oauthProviders"] as? List<*> ?: return mutableListOf()
        return raw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            map.entries.associate { it.key.toString() to it.value }.toMutableMap()
        }.toMutableList()
    }

    private fun saveProviders(user: AppUser, providers: List<MutableMap<String, Any?>>) {
        if (providers.isEmpty()) {
            user.meta.remove("oauthProviders")
        } else {
            user.meta["oauthProviders"] = providers
        }
    }

    private fun getLoginTokens(user: AppUser): MutableList<MutableMap<String, Any?>> {
        val raw = user.meta["loginTokens"] as? List<*> ?: return mutableListOf()
        return raw.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            map.entries.associate { it.key.toString() to it.value }.toMutableMap()
        }.toMutableList()
    }

    private fun saveLoginTokens(user: AppUser, tokens: List<MutableMap<String, Any?>>) {
        if (tokens.isEmpty()) {
            user.meta.remove("loginTokens")
        } else {
            user.meta["loginTokens"] = tokens
        }
    }

    private fun scrubLoginTokens(tokens: MutableList<MutableMap<String, Any?>>, now: OffsetDateTime): Boolean {
        val before = tokens.map { it.toMap() }
        tokens.removeIf { token ->
            val expireDate = parseOffsetDateTime(token["expireDate"]?.toString())
            expireDate == null || !now.isBefore(expireDate)
        }
        tokens.sortByDescending { readLong(it["id"]) ?: Long.MIN_VALUE }
        if (tokens.size > 8) {
            tokens.subList(8, tokens.size).clear()
        }
        return before != tokens.map { it.toMap() }
    }

    private fun addLoginToken(
        user: AppUser,
        tokens: MutableList<MutableMap<String, Any?>>,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): List<Any> {
        val tokenId = nextTokenId()
        val baseToken = randomToken(20)
        tokens.add(
            0,
            mutableMapOf(
                "id" to tokenId,
                "baseToken" to baseToken,
                "createdAt" to now.toString(),
                "expireDate" to now.plusDays(7).toString(),
            ),
        )
        saveLoginTokens(user, tokens)
        return listOf(tokenId, baseToken)
    }

    private fun findLoginTokenById(tokenId: Long): LoginTokenContext? {
        val now = OffsetDateTime.now()
        for (user in userRepository.findAll()) {
            val tokens = getLoginTokens(user)
            val changed = scrubLoginTokens(tokens, now)
            val token = tokens.firstOrNull { readLong(it["id"]) == tokenId }
            if (changed) {
                saveLoginTokens(user, tokens)
                userRepository.save(user)
            }
            if (token != null) {
                return LoginTokenContext(user, tokens, token)
            }
        }
        return null
    }

    private fun findLoginTokenByValue(rawToken: String): LoginTokenContext? {
        val now = OffsetDateTime.now()
        for (user in userRepository.findAll()) {
            val tokens = getLoginTokens(user)
            val changed = scrubLoginTokens(tokens, now)
            val token = tokens.firstOrNull { it["baseToken"]?.toString() == rawToken }
            if (changed) {
                saveLoginTokens(user, tokens)
                userRepository.save(user)
            }
            if (token != null) {
                return LoginTokenContext(user, tokens, token)
            }
        }
        return null
    }

    private fun findUserByOtpTicket(otpTicket: String): OtpContext? {
        for (user in userRepository.findAll()) {
            val providerContext = getKakaoProvider(user) ?: continue
            if (providerContext.provider["otpTicket"]?.toString() == otpTicket) {
                return OtpContext(user, providerContext.provider, getLoginTokens(user))
            }
        }
        return null
    }

    private fun consumeMatchingNonce(baseToken: String?, hashedToken: String): String? {
        if (baseToken.isNullOrBlank()) {
            return null
        }
        cleanupExpiredNonces()
        for (entry in loginNonces.entries) {
            if (sha512(baseToken + entry.key).equals(hashedToken, ignoreCase = true)) {
                if (loginNonces.remove(entry.key, entry.value)) {
                    return entry.key
                }
            }
        }
        return null
    }

    private fun cleanupExpiredNonces() {
        val now = OffsetDateTime.now()
        loginNonces.entries.removeIf { !now.isBefore(it.value) }
    }

    private fun clearOtpState(provider: MutableMap<String, Any?>) {
        provider.remove("otpTicket")
        provider.remove("OTPValue")
        provider.remove("OTPTrialUntil")
        provider.remove("OTPTrialCount")
    }

    private fun parseOffsetDateTime(raw: String?): OffsetDateTime? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return runCatching { OffsetDateTime.parse(raw) }.getOrNull()
    }

    private fun readLong(value: Any?): Long? {
        return when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }

    private fun nextTokenId(): Long {
        var candidate = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
        while (findLoginTokenById(candidate) != null) {
            candidate = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
        }
        return candidate
    }

    private fun randomToken(length: Int): String {
        val source = buildString {
            while (length > this.length) {
                append(UUID.randomUUID().toString().replace("-", ""))
            }
        }
        return source.take(length)
    }

    private fun sha512(value: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    data class OtpRequiredException(
        val otpTicket: String,
        override val message: String,
    ) : RuntimeException(message)

    data class OtpValidationException(
        val reset: Boolean,
        override val message: String,
    ) : RuntimeException(message)

    private data class ProviderContext(
        val user: AppUser,
        val providers: MutableList<MutableMap<String, Any?>>,
        val provider: MutableMap<String, Any?>,
    )

    private data class LoginTokenContext(
        val user: AppUser,
        val tokens: MutableList<MutableMap<String, Any?>>,
        val token: MutableMap<String, Any?>,
    )

    private data class OtpContext(
        val user: AppUser,
        val provider: MutableMap<String, Any?>,
        val tokens: MutableList<MutableMap<String, Any?>>,
    )
}
