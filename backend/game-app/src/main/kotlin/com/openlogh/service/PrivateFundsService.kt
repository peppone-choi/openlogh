package com.openlogh.service

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 사적 구좌(私的口座) 서비스.
 *
 * gin7 §20:
 * - 사적 구좌: 캐릭터 개인 자금 보관 (Officer.meta["privateFunds"])
 * - 지방자금고 투입: 통치 행성에 원조금 투입 (행성 funds 증가)
 * - 신임 박스: 현 황제/의장에 대한 신임/불신임 표 투입
 * - 지지 박스: 차기 황제/의장 후보 지지 자금 투입
 */
@Service
class PrivateFundsService(
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PrivateFundsService::class.java)

        // Officer.meta 키
        private const val KEY_PRIVATE_FUNDS = "privateFunds"
        // SessionState.config 키 (신임/지지 박스는 진영 설정에 누적)
        private const val KEY_CONFIDENCE_VOTES = "confidenceVotes"
        private const val KEY_SUPPORT_VOTES    = "supportVotes"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 기본 잔액 조회 / 입출금
    // ─────────────────────────────────────────────────────────────────────────

    /** 사적 구좌 잔액 조회. */
    fun getBalance(officer: Officer): Int =
        (officer.meta[KEY_PRIVATE_FUNDS] as? Number)?.toInt() ?: 0

    /**
     * 사적 구좌에 자금 입금.
     *
     * gin7 §20: Officer.funds(개인 전투/행정 자금)에서 사적 구좌로 이체.
     * @return 잔액 부족 시 false
     */
    fun deposit(officer: Officer, amount: Int): Boolean {
        if (amount <= 0) return false
        if (officer.funds < amount) {
            log.warn("deposit 실패: officer={} funds={} < amount={}", officer.id, officer.funds, amount)
            return false
        }
        officer.funds -= amount
        val current = getBalance(officer)
        officer.meta[KEY_PRIVATE_FUNDS] = current + amount
        officerRepository.save(officer)
        return true
    }

    /**
     * 사적 구좌에서 자금 출금.
     *
     * @return 잔액 부족 시 false
     */
    fun withdraw(officer: Officer, amount: Int): Boolean {
        if (amount <= 0) return false
        val current = getBalance(officer)
        if (current < amount) {
            log.warn("withdraw 실패: officer={} privateFunds={} < amount={}", officer.id, current, amount)
            return false
        }
        officer.meta[KEY_PRIVATE_FUNDS] = current - amount
        officer.funds += amount
        officerRepository.save(officer)
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 지방자금고 투입 (행성 원조)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 지방자금고 투입: 통치 행성에 사적 구좌 자금을 원조.
     *
     * gin7 §20: 해당 행성 factionId 자금(funds) 증가.
     * 장교가 해당 행성의 fiefOfficer이거나 같은 진영 소속이어야 한다.
     *
     * @return 잔액 부족 또는 권한 없음 시 false
     */
    fun investInPlanet(officer: Officer, planet: Planet, amount: Int): Boolean {
        if (amount <= 0) return false
        val balance = getBalance(officer)
        if (balance < amount) {
            log.warn("investInPlanet 실패: officer={} privateFunds={} < amount={}", officer.id, balance, amount)
            return false
        }
        // 진영 권한 확인: 같은 진영 또는 해당 행성 영지 장교
        if (planet.factionId != officer.factionId && planet.fiefOfficerId != officer.id) {
            log.warn("investInPlanet 거부: officer={} 진영 불일치", officer.id)
            return false
        }
        officer.meta[KEY_PRIVATE_FUNDS] = balance - amount

        // 진영 자금 증가
        val faction = factionRepository.findById(planet.factionId).orElse(null) ?: run {
            log.error("investInPlanet: faction={} 없음", planet.factionId)
            return false
        }
        faction.funds += amount
        factionRepository.save(faction)
        officerRepository.save(officer)
        log.info("investInPlanet: officer={} → planet={} amount={}", officer.id, planet.id, amount)
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 신임 박스 (현 황제/의장 신임/불신임)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 신임/불신임 투표 자금 투입.
     *
     * gin7 §20: 현 황제/의장에 대한 신임(confidence) 또는 불신임(no-confidence) 표명.
     * 진영 config["confidenceVotes"][targetOfficerId] 에 누적.
     *
     * @param isConfidence true=신임, false=불신임
     * @return 잔액 부족 시 false
     */
    fun voteConfidence(
        officer: Officer,
        targetOfficerId: Long,
        amount: Int,
        isConfidence: Boolean,
    ): Boolean {
        if (amount <= 0) return false
        val balance = getBalance(officer)
        if (balance < amount) {
            log.warn("voteConfidence 실패: officer={} privateFunds={} < amount={}", officer.id, balance, amount)
            return false
        }
        officer.meta[KEY_PRIVATE_FUNDS] = balance - amount

        val faction = factionRepository.findById(officer.factionId).orElse(null) ?: return false
        @Suppress("UNCHECKED_CAST")
        val votes = (faction.meta.getOrPut(KEY_CONFIDENCE_VOTES) { mutableMapOf<String, Any>() }
                as MutableMap<String, Any>)
        val key = targetOfficerId.toString()
        val signedAmount = if (isConfidence) amount else -amount
        val current = (votes[key] as? Number)?.toInt() ?: 0
        votes[key] = current + signedAmount
        faction.meta[KEY_CONFIDENCE_VOTES] = votes

        factionRepository.save(faction)
        officerRepository.save(officer)
        log.info("voteConfidence: officer={} target={} amount={} confidence={}", officer.id, targetOfficerId, amount, isConfidence)
        return true
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 지지 박스 (차기 황제/의장 후보 지지)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 차기 황제/의장 후보 지지 자금 투입.
     *
     * gin7 §20: 진영 config["supportVotes"][candidateOfficerId] 에 누적.
     *
     * @return 잔액 부족 시 false
     */
    fun voteSupport(officer: Officer, candidateOfficerId: Long, amount: Int): Boolean {
        if (amount <= 0) return false
        val balance = getBalance(officer)
        if (balance < amount) {
            log.warn("voteSupport 실패: officer={} privateFunds={} < amount={}", officer.id, balance, amount)
            return false
        }
        officer.meta[KEY_PRIVATE_FUNDS] = balance - amount

        val faction = factionRepository.findById(officer.factionId).orElse(null) ?: return false
        @Suppress("UNCHECKED_CAST")
        val votes = (faction.meta.getOrPut(KEY_SUPPORT_VOTES) { mutableMapOf<String, Any>() }
                as MutableMap<String, Any>)
        val key = candidateOfficerId.toString()
        val current = (votes[key] as? Number)?.toInt() ?: 0
        votes[key] = current + amount
        faction.meta[KEY_SUPPORT_VOTES] = votes

        factionRepository.save(faction)
        officerRepository.save(officer)
        log.info("voteSupport: officer={} candidate={} amount={}", officer.id, candidateOfficerId, amount)
        return true
    }
}
