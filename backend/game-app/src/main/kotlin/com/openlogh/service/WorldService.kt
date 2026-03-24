package com.openlogh.service

import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.repository.WorldHistoryRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * 세션 상태 및 게임 페이즈 관리 서비스.
 *
 * gin7 서버 상태 6단계:
 * - closed: 세션 미시작 (startTime 이전)
 * - pre_open: 가오픈 (startTime~opentime 사이, 캐릭터 등록만 가능)
 * - opening: 초기 단계 (개시 후 3년 이내, 진영 미형성)
 * - open: 정상 운영 (메인 게임 진행 중)
 * - united: 통일 달성 (승패 판정 완료, 결과 열람 가능)
 * - paused: 일시 정지 (관리자 조작, 턴 진행 중단)
 */
@Service
class WorldService(
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val worldHistoryRepository: WorldHistoryRepository,
) {
    companion object {
        const val PHASE_CLOSED = "closed"
        const val PHASE_PRE_OPEN = "pre_open"
        const val PHASE_OPENING = "opening"
        const val PHASE_OPEN = "open"
        const val PHASE_UNITED = "united"
        const val PHASE_PAUSED = "paused"

        /** Legacy alias — existing code references PHASE_RUNNING */
        const val PHASE_RUNNING = PHASE_OPEN
    }

    fun getWorld(id: Short): SessionState? = sessionStateRepository.findById(id).orElse(null)

    fun deleteWorld(sessionId: Short) {
        sessionStateRepository.deleteById(sessionId)
    }

    /**
     * 현재 게임 페이즈 판정.
     *
     * 판정 우선순위:
     * 1. config["locked"]==true → paused
     * 2. config["isUnited"]!=0 → united
     * 3. startTime 이전 → closed
     * 4. opentime 이전 → pre_open
     * 5. 개시 후 3년 이내 또는 진영 없음 → opening
     * 6. 그 외 → open
     */
    fun getGamePhase(world: SessionState): String {
        // Paused: 관리자 일시 정지
        if (world.config["locked"] == true) return PHASE_PAUSED

        // United: 통일 달성
        val isUnited = (world.config["isUnited"] as? Number)?.toInt()
            ?: (world.config["isunited"] as? Number)?.toInt()
            ?: 0
        if (isUnited != 0) return PHASE_UNITED

        val now = OffsetDateTime.now()

        // Closed: 세션 미시작
        val startTimeStr = world.config["startTime"] as? String
        if (startTimeStr != null) {
            val startTime = OffsetDateTime.parse(startTimeStr)
            if (now.isBefore(startTime)) return PHASE_CLOSED
        }

        // Pre-open: 가오픈 (캐릭터 등록만 가능)
        val openTimeStr = world.config["opentime"] as? String
        if (openTimeStr != null) {
            val openTime = OffsetDateTime.parse(openTimeStr)
            if (now.isBefore(openTime)) return PHASE_PRE_OPEN
        }

        // Opening: 초기 단계
        val startYear = (world.config["startYear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val yearDiff = world.currentYear - startYear
        val factions = factionRepository.findBySessionId(world.id.toLong())

        if (yearDiff <= 3 || factions.isEmpty()) {
            return PHASE_OPENING
        }

        // Open: 정상 운영
        return PHASE_OPEN
    }

    /**
     * 관리자: 세션 일시 정지.
     */
    fun pauseSession(world: SessionState) {
        world.config["locked"] = true
        sessionStateRepository.save(world)
    }

    /**
     * 관리자: 세션 일시 정지 해제.
     */
    fun resumeSession(world: SessionState) {
        world.config.remove("locked")
        sessionStateRepository.save(world)
    }

    /**
     * 특정 페이즈에서 허용되는 액션 카테고리.
     */
    fun getAllowedActions(phase: String): Set<String> = when (phase) {
        PHASE_CLOSED -> emptySet()
        PHASE_PRE_OPEN -> setOf("register", "select_character", "chat")
        PHASE_OPENING -> setOf("register", "select_character", "chat", "domestic", "personal", "move")
        PHASE_OPEN -> setOf("all")
        PHASE_UNITED -> setOf("chat", "view", "ranking", "history")
        PHASE_PAUSED -> setOf("chat", "view")
        else -> emptySet()
    }

    /**
     * 페이즈에 따라 턴 진행 가능 여부.
     */
    fun canProcessTurns(world: SessionState): Boolean {
        val phase = getGamePhase(world)
        return phase == PHASE_OPENING || phase == PHASE_OPEN
    }
}
