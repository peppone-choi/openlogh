package com.openlogh.service

import com.openlogh.entity.CoupEvent
import com.openlogh.model.CoupPhase
import com.openlogh.model.NobilityRank
import com.openlogh.entity.Officer
import com.openlogh.repository.CoupEventRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Manages the Empire autocratic governance system:
 * nobility ranks, coup state machine, and sovereign succession.
 */
@Service
class EmpirePoliticsService(
    private val coupEventRepository: CoupEventRepository,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Minimum rank to initiate a coup (Commodore = rank 5) */
        const val MIN_COUP_RANK = 5
        /** Default political power threshold for coup trigger (per gin7) */
        const val DEFAULT_COUP_THRESHOLD = 8000
        /** Ticks after which a failed coup is resolved if loyalists dominate */
        const val COUP_RESOLUTION_TICKS = 10
    }

    // ========== Coup State Machine ==========

    /**
     * Initiate a coup (PLANNING phase).
     */
    @Transactional
    fun initiateCoup(sessionId: Long, factionId: Long, leaderId: Long): CoupEvent {
        val faction = factionRepository.findById(factionId).orElseThrow {
            IllegalArgumentException("Faction not found: $factionId")
        }
        if (faction.factionType != "empire") {
            throw IllegalStateException("Coups can only occur in Empire factions")
        }

        val leader = officerRepository.findById(leaderId).orElseThrow {
            IllegalArgumentException("Officer not found: $leaderId")
        }
        if (leader.officerLevel < MIN_COUP_RANK) {
            throw IllegalStateException("Rank too low to initiate coup (need rank $MIN_COUP_RANK+)")
        }

        // Check no active coup
        val active = coupEventRepository.findBySessionIdAndFactionIdAndPhaseIn(
            sessionId, factionId, listOf(CoupPhase.PLANNING.code, CoupPhase.ACTIVE.code)
        )
        if (active.isNotEmpty()) {
            throw IllegalStateException("A coup is already in progress for this faction")
        }

        val coup = CoupEvent(
            sessionId = sessionId,
            factionId = factionId,
            leaderId = leaderId,
            phase = CoupPhase.PLANNING.code,
            supporterIds = mutableListOf(leaderId),
            targetSovereignId = faction.chiefOfficerId,
            threshold = DEFAULT_COUP_THRESHOLD,
        )
        val saved = coupEventRepository.save(coup)
        log.info("[Session {}] Coup initiated by officer {} against sovereign {}", sessionId, leaderId, faction.chiefOfficerId)
        return saved
    }

    /**
     * Join an existing coup.
     */
    @Transactional
    fun joinCoup(sessionId: Long, coupId: Long, officerId: Long) {
        val coup = coupEventRepository.findById(coupId).orElseThrow {
            IllegalArgumentException("Coup not found: $coupId")
        }
        if (coup.phase != CoupPhase.PLANNING.code && coup.phase != CoupPhase.ACTIVE.code) {
            throw IllegalStateException("Cannot join a coup in phase ${coup.phase}")
        }

        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }
        // Sovereign cannot join a coup against themselves
        if (officerId == coup.targetSovereignId) {
            throw IllegalStateException("The sovereign cannot join a coup against themselves")
        }

        if (officerId !in coup.supporterIds) {
            coup.supporterIds.add(officerId)
            coupEventRepository.save(coup)
            log.info("[Session {}] Officer {} joined coup {}", sessionId, officerId, coupId)
        }
    }

    /**
     * Add political power to a coup (from political machinations).
     */
    @Transactional
    fun addPoliticalPower(sessionId: Long, coupId: Long, points: Int) {
        val coup = coupEventRepository.findById(coupId).orElseThrow {
            IllegalArgumentException("Coup not found: $coupId")
        }
        if (coup.phase != CoupPhase.PLANNING.code) {
            throw IllegalStateException("Can only add political power during PLANNING phase")
        }

        coup.politicalPower += points
        coupEventRepository.save(coup)

        // Check if threshold reached -> transition to ACTIVE
        if (coup.politicalPower >= coup.threshold) {
            coup.phase = CoupPhase.ACTIVE.code
            coupEventRepository.save(coup)
            log.info("[Session {}] Coup {} triggered! Political power {} >= threshold {}",
                sessionId, coupId, coup.politicalPower, coup.threshold)
        }
    }

    /**
     * Resolve a coup (SUCCESS or FAILED).
     */
    @Transactional
    fun resolveCoup(sessionId: Long, coupId: Long, success: Boolean) {
        val coup = coupEventRepository.findById(coupId).orElseThrow {
            IllegalArgumentException("Coup not found: $coupId")
        }
        if (coup.phase != CoupPhase.ACTIVE.code) {
            throw IllegalStateException("Can only resolve an ACTIVE coup")
        }

        if (success) {
            coup.phase = CoupPhase.SUCCESS.code
            coup.result = "overthrow"
            coup.resolvedAt = OffsetDateTime.now()

            // Overthrow: set leader as new sovereign
            val faction = factionRepository.findById(coup.factionId).orElseThrow {
                IllegalArgumentException("Faction not found: ${coup.factionId}")
            }
            faction.chiefOfficerId = coup.leaderId
            factionRepository.save(faction)

            log.info("[Session {}] Coup {} succeeded! Officer {} is the new sovereign",
                sessionId, coupId, coup.leaderId)
        } else {
            coup.phase = CoupPhase.FAILED.code
            coup.result = "suppressed"
            coup.resolvedAt = OffsetDateTime.now()

            // Punish supporters: set blockState = 1 (arrested)
            for (supporterId in coup.supporterIds) {
                val supporter = officerRepository.findById(supporterId).orElse(null)
                if (supporter != null) {
                    supporter.blockState = 1
                    officerRepository.save(supporter)
                }
            }

            log.info("[Session {}] Coup {} failed! {} supporters arrested",
                sessionId, coupId, coup.supporterIds.size)
        }

        coupEventRepository.save(coup)
    }

    /**
     * Abort a coup during PLANNING phase.
     */
    @Transactional
    fun abortCoup(sessionId: Long, coupId: Long, requesterId: Long) {
        val coup = coupEventRepository.findById(coupId).orElseThrow {
            IllegalArgumentException("Coup not found: $coupId")
        }
        if (coup.phase != CoupPhase.PLANNING.code) {
            throw IllegalStateException("Can only abort a coup during PLANNING phase")
        }
        if (coup.leaderId != requesterId) {
            throw IllegalStateException("Only the coup leader can abort")
        }

        coup.phase = CoupPhase.ABORTED.code
        coup.result = "aborted"
        coup.resolvedAt = OffsetDateTime.now()
        coupEventRepository.save(coup)
        log.info("[Session {}] Coup {} aborted by leader {}", sessionId, coupId, requesterId)
    }

    /**
     * Get active coup status for a faction.
     */
    fun getCoupStatus(sessionId: Long, factionId: Long): CoupEvent? {
        val active = coupEventRepository.findBySessionIdAndFactionIdAndPhaseIn(
            sessionId, factionId, listOf(CoupPhase.PLANNING.code, CoupPhase.ACTIVE.code)
        )
        return active.firstOrNull()
    }

    /**
     * Process coup tick: evaluate active coups for resolution.
     */
    @Transactional
    fun processCoupTick(sessionId: Long) {
        val activeCoups = coupEventRepository.findBySessionIdAndPhaseIn(
            sessionId, listOf(CoupPhase.ACTIVE.code)
        )

        for (coup in activeCoups) {
            // Calculate military power of supporters vs loyalists
            val supporters = coup.supporterIds.mapNotNull { officerRepository.findById(it).orElse(null) }
            val allOfficers = officerRepository.findBySessionIdAndFactionId(sessionId, coup.factionId)
            val loyalists = allOfficers.filter { it.id !in coup.supporterIds }

            val supporterPower = supporters.sumOf { it.ships.toLong() }
            val loyalistPower = loyalists.sumOf { it.ships.toLong() }

            // Check resolution conditions
            if (supporterPower > loyalistPower * 1.2) {
                resolveCoup(sessionId, coup.id, success = true)
            } else {
                val ticksActive = java.time.Duration.between(coup.startedAt, OffsetDateTime.now()).seconds
                if (loyalistPower > supporterPower * 1.5 && ticksActive > COUP_RESOLUTION_TICKS) {
                    resolveCoup(sessionId, coup.id, success = false)
                }
            }
        }
    }

    // ========== Nobility ==========

    /**
     * Grant a nobility rank to an officer (stored in officer.meta).
     */
    @Transactional
    fun grantNobility(sessionId: Long, officerId: Long, rank: NobilityRank) {
        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }
        officer.meta["nobility_rank"] = rank.code
        officerRepository.save(officer)
        log.info("[Session {}] Officer {} granted nobility rank: {}", sessionId, officerId, rank.nameKo)
    }

    /**
     * Get an officer's nobility rank.
     */
    fun getNobility(officer: Officer): NobilityRank {
        val code = officer.meta["nobility_rank"] as? String ?: return NobilityRank.COMMONER
        return NobilityRank.fromCode(code)
    }

    /**
     * Get all officers with nobility in a faction.
     */
    fun getNobilityList(sessionId: Long, factionId: Long): List<Pair<Officer, NobilityRank>> {
        val officers = officerRepository.findBySessionIdAndFactionId(sessionId, factionId)
        return officers
            .map { it to getNobility(it) }
            .filter { it.second != NobilityRank.COMMONER }
            .sortedByDescending { it.second.ordinal }
    }
}
