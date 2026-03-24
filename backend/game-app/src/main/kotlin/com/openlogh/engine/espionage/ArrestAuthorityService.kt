package com.openlogh.engine.espionage

import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 체포 권한 체계 서비스.
 *
 * gin7 §4.8, §9.9:
 *
 * 제국(empire):
 *   - 헌병총감: 원수(rank=10) 제외 군인(military) 체포 허가 발령 가능
 *   - 내무상서: 대령(rank=4) 이하 군인 체포 허가 발령 가능
 *   - 사법상서: 정치가(politician) 체포 허가 발령 가능
 *
 * 동맹(alliance):
 *   - 헌병사령관: 군인(military) 체포 허가 발령 가능
 *   - 법질서위원장: 정치가(politician) 체포 허가 발령 가능
 *
 * 4단계 프로세스: 체포허가 → 집행명령 → 체포명령 → 처단
 *
 * 체포 목록은 SessionState.config["arrestList"] 에 저장.
 * 허가/집행 권한은 Officer.meta 에 저장 (EspionageService 참조).
 */
@Service
class ArrestAuthorityService(
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ArrestAuthorityService::class.java)

        /** SessionState.config 키: 체포 목록 (officerId 리스트) */
        const val KEY_ARREST_LIST = "arrestList"

        // 제국 직위 코드 (Officer.personalCode 또는 Officer.specialCode)
        const val ROLE_MILITARY_POLICE_CHIEF = "military_police_chief"  // 헌병총감
        const val ROLE_INTERIOR_MINISTER     = "interior_minister"      // 내무상서
        const val ROLE_JUSTICE_MINISTER      = "justice_minister"       // 사법상서

        // 동맹 직위 코드
        const val ROLE_MP_COMMANDER          = "mp_commander"           // 헌병사령관
        const val ROLE_LAW_ORDER_CHAIR       = "law_order_chair"        // 법질서위원장

        /** 대령 계급 (rank 4) */
        const val RANK_CAPTAIN = 4
        /** 원수 계급 (rank 10) */
        const val RANK_MARSHAL = 10
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 체포 허가 발령 권한 판정
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 체포 허가 발령 가능 여부.
     *
     * gin7 §4.8:
     * - 제국 헌병총감, 내무상서, 사법상서 → 각 담당 범주 내 발령 가능
     * - 동맹 헌병사령관, 법질서위원장 → 각 담당 범주 내 발령 가능
     *
     * 이 메서드는 issuer 에게 "어느 범주라도" 발령 권한이 있는지 확인한다.
     * 세부 대상 범주 확인은 [canIssueArrestWarrantFor] 사용.
     */
    fun canIssueArrestWarrant(issuer: Officer): Boolean {
        val role = resolveRole(issuer)
        return role != null
    }

    /**
     * 특정 대상에 대한 체포 허가 발령 가능 여부.
     *
     * gin7 §4.8, §9.9:
     * - 헌병총감: 원수(rank 10) 제외 군인 대상
     * - 내무상서: 대령(rank 4) 이하 군인 대상
     * - 사법상서: 정치가(politician) 대상
     * - 헌병사령관: 군인 대상
     * - 법질서위원장: 정치가 대상
     */
    fun canIssueArrestWarrantFor(issuer: Officer, target: Officer): Boolean {
        if (issuer.sessionId != target.sessionId) return false
        return when (resolveRole(issuer)) {
            ROLE_MILITARY_POLICE_CHIEF ->
                // 헌병총감: 군인, 원수(rank=10) 제외
                target.careerType == "military" && target.rank < RANK_MARSHAL
            ROLE_INTERIOR_MINISTER ->
                // 내무상서: 군인, 대령(rank=4) 이하
                target.careerType == "military" && target.rank <= RANK_CAPTAIN
            ROLE_JUSTICE_MINISTER ->
                // 사법상서: 정치가
                target.careerType == "politician"
            ROLE_MP_COMMANDER ->
                // 헌병사령관: 군인
                target.careerType == "military"
            ROLE_LAW_ORDER_CHAIR ->
                // 법질서위원장: 정치가
                target.careerType == "politician"
            else -> false
        }
    }

    /**
     * 체포 집행 권한 보유 여부.
     *
     * gin7 §9.9: Officer.meta["executeAuthority"] 집합에 target.id 가 있으면 집행 가능.
     */
    fun canExecuteArrest(executor: Officer, target: Officer): Boolean {
        @Suppress("UNCHECKED_CAST")
        val authority = (executor.meta["executeAuthority"] as? Collection<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            ?.toSet()
            ?: emptySet()
        return target.id in authority
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 체포 목록 관리 (SessionState.config)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 세션 체포 목록 조회.
     *
     * gin7 §9.9: SessionState.config["arrestList"] 에 저장된 officerId 목록.
     */
    fun getArrestList(sessionState: SessionState): List<Long> {
        @Suppress("UNCHECKED_CAST")
        return (sessionState.config[KEY_ARREST_LIST] as? List<*>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            ?: emptyList()
    }

    /**
     * 체포 목록에 대상 추가.
     *
     * gin7 §9.9: 체포 허가 발령 시 목록 등재.
     */
    fun addToArrestList(sessionState: SessionState, targetOfficerId: Long) {
        @Suppress("UNCHECKED_CAST")
        val list = (sessionState.config.getOrPut(KEY_ARREST_LIST) { mutableListOf<Long>() }
                as MutableList<Any>)
        if (!list.contains(targetOfficerId)) {
            list.add(targetOfficerId)
            log.info("체포 목록 등재: session={} target={}", sessionState.id, targetOfficerId)
        }
    }

    /**
     * 체포 목록에서 대상 제거.
     */
    fun removeFromArrestList(sessionState: SessionState, targetOfficerId: Long) {
        @Suppress("UNCHECKED_CAST")
        val list = sessionState.config[KEY_ARREST_LIST] as? MutableList<Any> ?: return
        list.remove(targetOfficerId)
        log.info("체포 목록 제거: session={} target={}", sessionState.id, targetOfficerId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 처단 (4단계 마지막)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 처단 집행.
     *
     * gin7 §9.9: 4단계 마지막 — 처단/석방/추방 중 하나.
     * 처단(execute): prisoner 사망 처리 (npcState 변경, killTurn 설정)
     * 석방(release): 체포 목록 제거, 자유 석방
     * 추방(exile): 진영에서 추방 (factionId = 0, 망명자 상태)
     *
     * @param judge 처단 집행자 (체포 허가 발령자 권한 필요)
     * @param prisoner 처단 대상 (체포된 장교)
     * @param judgment "execute" | "release" | "exile"
     * @param sessionState 현재 세션 상태
     */
    fun executeJudgment(
        judge: Officer,
        prisoner: Officer,
        judgment: String,
        sessionState: SessionState,
    ): JudgmentResult {
        if (!canIssueArrestWarrant(judge)) {
            return JudgmentResult(false, "처단 권한 없음: officer=${judge.id}")
        }
        return when (judgment) {
            "execute" -> {
                // gin7: 처단 — 장교 사망. killTurn 기록, npcState=99 (사망)
                prisoner.npcState = 99
                prisoner.killTurn = sessionState.currentMonth
                removeFromArrestList(sessionState, prisoner.id)
                officerRepository.save(prisoner)
                log.info("처단: judge={} prisoner={}", judge.id, prisoner.id)
                JudgmentResult(true, "${prisoner.name} 처단 완료")
            }
            "release" -> {
                // 석방: 체포 목록만 제거
                removeFromArrestList(sessionState, prisoner.id)
                log.info("석방: judge={} prisoner={}", judge.id, prisoner.id)
                JudgmentResult(true, "${prisoner.name} 석방")
            }
            "exile" -> {
                // 추방: 진영 탈퇴, originType=exile
                prisoner.factionId = 0
                prisoner.originType = "exile"
                removeFromArrestList(sessionState, prisoner.id)
                officerRepository.save(prisoner)
                log.info("추방: judge={} prisoner={}", judge.id, prisoner.id)
                JudgmentResult(true, "${prisoner.name} 추방")
            }
            else -> JudgmentResult(false, "알 수 없는 처단 유형: $judgment")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 장교의 직위 코드를 personalCode 또는 specialCode 에서 추출.
     */
    private fun resolveRole(officer: Officer): String? {
        val authorityRoles = setOf(
            ROLE_MILITARY_POLICE_CHIEF,
            ROLE_INTERIOR_MINISTER,
            ROLE_JUSTICE_MINISTER,
            ROLE_MP_COMMANDER,
            ROLE_LAW_ORDER_CHAIR,
        )
        if (officer.personalCode in authorityRoles) return officer.personalCode
        if (officer.specialCode in authorityRoles) return officer.specialCode
        if (officer.special2Code in authorityRoles) return officer.special2Code
        return null
    }
}

/** 처단 실행 결과 */
data class JudgmentResult(
    val success: Boolean,
    val message: String,
)
