package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class CharacterCreationService(
    private val officerRepository: OfficerRepository,
) {
    companion object {
        const val STAT_TOTAL = 400
        const val STAT_MIN = 10
        const val STAT_MAX = 100
        val STAT_KEYS = listOf(
            "leadership", "command", "intelligence", "politics",
            "administration", "mobility", "attack", "defense",
        )
        val EMPIRE_ORIGINS = setOf("noble", "knight", "commoner")
        val ALLIANCE_ORIGINS = setOf("citizen")
    }

    data class StatAllocation(
        val leadership: Int,
        val command: Int,
        val intelligence: Int,
        val politics: Int,
        val administration: Int,
        val mobility: Int,
        val attack: Int,
        val defense: Int,
    ) {
        fun toList(): List<Int> = listOf(
            leadership, command, intelligence, politics,
            administration, mobility, attack, defense,
        )
    }

    fun validateStatAllocation(stats: StatAllocation): Boolean {
        val values = stats.toList()
        if (values.any { it < STAT_MIN || it > STAT_MAX }) return false
        if (values.sum() != STAT_TOTAL) return false
        return true
    }

    fun validateOrigin(factionType: String, originType: String): Boolean {
        return when (factionType) {
            "empire" -> originType in EMPIRE_ORIGINS
            "alliance" -> originType in ALLIANCE_ORIGINS
            else -> false
        }
    }

    @Transactional
    fun createGeneratedOfficer(
        sessionId: Long,
        userId: Long,
        factionId: Long,
        name: String,
        stats: StatAllocation,
        originType: String,
        factionType: String,
        planetId: Long,
    ): Officer {
        require(validateStatAllocation(stats)) { "능력치 합계는 ${STAT_TOTAL}이어야 합니다." }
        require(validateOrigin(factionType, originType)) { "유효하지 않은 출자입니다." }
        require(name.length in 2..20) { "이름은 2~20자여야 합니다." }

        val officer = Officer(
            sessionId = sessionId,
            userId = userId,
            factionId = factionId,
            name = name,
            leadership = stats.leadership.toShort(),
            command = stats.command.toShort(),
            intelligence = stats.intelligence.toShort(),
            politics = stats.politics.toShort(),
            administration = stats.administration.toShort(),
            mobility = stats.mobility.toShort(),
            attack = stats.attack.toShort(),
            defense = stats.defense.toShort(),
            originType = originType,
            careerType = "military",
            rank = 0, // sub-lieutenant
            planetId = planetId,
            locationState = "planet",
            homePlanetId = planetId,
            npcState = 0, // player-controlled
            turnTime = OffsetDateTime.now(),
        )
        return officerRepository.save(officer)
    }

    @Transactional
    fun selectOriginalOfficer(
        sessionId: Long,
        userId: Long,
        officerId: Long,
    ): Officer {
        val officer = officerRepository.findById(officerId)
            .orElseThrow { IllegalArgumentException("캐릭터를 찾을 수 없습니다.") }
        require(officer.sessionId == sessionId) { "다른 세션의 캐릭터입니다." }
        require(officer.userId == null || officer.userId == 0L) {
            "이미 다른 플레이어가 선택한 캐릭터입니다."
        }
        officer.userId = userId
        officer.npcState = 0
        return officerRepository.save(officer)
    }

    fun getAvailableOriginals(sessionId: Long, factionId: Long): List<Officer> {
        return officerRepository.findBySessionIdAndNationId(sessionId, factionId)
            .filter { (it.userId == null || it.userId == 0L) && it.npcState != 0.toShort() }
    }
}
