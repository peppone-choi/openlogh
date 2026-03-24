package com.openlogh.service

import com.openlogh.entity.SessionState
import com.openlogh.entity.Tournament
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TournamentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.random.Random

/**
 * 토너먼트 시스템.
 *
 * gin7:
 * - 정기적으로 1:1 전투 토너먼트가 자동 개최됨
 * - 참가 자격: 준장(rank 5) 이상, 행성 체류 중, 부상 없음
 * - 자동 등록: 조건 충족 장교 중 상위 16명(공적 순)
 * - 토너먼트 결과: 공적 + 평가 포인트 보상
 *
 * 자동 트리거: 매 게임 6개월(6턴)마다 자동 개최
 */
@Service
class TournamentService(
    private val tournamentRepository: TournamentRepository,
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TournamentService::class.java)

        /** 토너먼트 개최 주기 (게임 월 단위) */
        const val TRIGGER_INTERVAL_MONTHS = 6

        /** 최대 참가자 수 (2의 거듭제곱) */
        const val MAX_PARTICIPANTS = 16

        /** 최소 참가자 수 */
        const val MIN_PARTICIPANTS = 4

        /** 참가 최소 계급 (준장) */
        const val MIN_RANK = 5

        /** 승리 보상 공적 */
        const val MERIT_PER_WIN = 20

        /** 우승 보상 공적 */
        const val MERIT_CHAMPION = 100

        /** 우승 보상 평가 포인트 */
        const val EVAL_CHAMPION = 50
    }

    /**
     * 매 턴 호출: 토너먼트 자동 개최 판정.
     */
    fun processTournament(world: SessionState) {
        val currentMonth = world.currentMonth.toInt()
        // 6개월마다 자동 트리거 (1월, 7월)
        if (currentMonth != 1 && currentMonth != 7) return

        val sessionId = world.id.toLong()

        // 이미 이번 달에 토너먼트가 있으면 스킵
        val existing = tournamentRepository.findBySessionId(sessionId)
        val thisYearMonth = world.currentYear.toInt() * 12 + currentMonth
        val hasRecent = existing.any {
            val createdYM = it.createdAt.year * 12 + it.createdAt.monthValue
            createdYM == thisYearMonth
        }
        if (hasRecent) return

        // 참가 자격 장교 선발
        val officers = officerRepository.findBySessionId(sessionId)
        val eligible = officers.filter { officer ->
            officer.factionId != 0L &&
                officer.rank >= MIN_RANK &&
                officer.locationState == "planet" &&
                officer.injury <= 0 &&
                officer.npcState <= 1.toShort()
        }.sortedByDescending { it.experience }
            .take(MAX_PARTICIPANTS)

        if (eligible.size < MIN_PARTICIPANTS) {
            log.debug("Tournament skipped: only {} eligible (need {})", eligible.size, MIN_PARTICIPANTS)
            return
        }

        // 토너먼트 자동 등록
        log.info("Auto-triggering tournament for session {} with {} participants", sessionId, eligible.size)
        autoRegisterTournament(world, eligible.map { it.id })
    }

    /**
     * 토너먼트 참가자 등록.
     */
    private fun autoRegisterTournament(world: SessionState, officerIds: List<Long>) {
        val sessionId = world.id.toLong()
        val rng = Random(world.currentYear * 100L + world.currentMonth)

        // 셔플하여 대진표 생성
        val shuffled = officerIds.shuffled(rng)

        for ((index, officerId) in shuffled.withIndex()) {
            tournamentRepository.save(Tournament(
                sessionId = sessionId,
                officerId = officerId,
                round = 1,
                bracketPosition = index.toShort(),
                opponentId = if (index % 2 == 0 && index + 1 < shuffled.size) {
                    shuffled[index + 1]
                } else if (index % 2 == 1) {
                    shuffled[index - 1]
                } else null,
                result = 0, // 미결
            ))
        }

        // 참가 상태 설정
        for (officerId in officerIds) {
            val officer = officerRepository.findById(officerId).orElse(null) ?: continue
            officer.tournamentState = 1 // 참가 중
            officerRepository.save(officer)
        }

        log.info("Tournament registered: {} participants in session {}", officerIds.size, sessionId)
    }

    /**
     * 토너먼트 결과 처리 후 보상 지급.
     */
    fun awardTournamentResults(sessionId: Long, championOfficerId: Long, winnerIds: List<Long>) {
        // 승리자들에게 공적 부여
        for (winnerId in winnerIds) {
            val officer = officerRepository.findById(winnerId).orElse(null) ?: continue
            officer.experience += MERIT_PER_WIN
            officerRepository.save(officer)
        }

        // 우승자 추가 보상
        val champion = officerRepository.findById(championOfficerId).orElse(null)
        if (champion != null) {
            champion.experience += MERIT_CHAMPION
            champion.dedication += EVAL_CHAMPION
            officerRepository.save(champion)
            log.info("Tournament champion: {} (merit +{}, eval +{})", champion.name, MERIT_CHAMPION, EVAL_CHAMPION)
        }

        // 참가 상태 리셋
        val tournaments = tournamentRepository.findBySessionId(sessionId)
        val participantIds = tournaments.map { it.officerId }.distinct()
        for (pid in participantIds) {
            val officer = officerRepository.findById(pid).orElse(null) ?: continue
            officer.tournamentState = 0
            officerRepository.save(officer)
        }
    }
}
