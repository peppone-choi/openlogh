package com.openlogh.service

import com.openlogh.entity.CouncilSeat
import com.openlogh.entity.Election
import com.openlogh.model.CouncilSeatCode
import com.openlogh.model.ElectionType
import com.openlogh.repository.CouncilSeatRepository
import com.openlogh.repository.ElectionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Manages the Alliance democratic governance system:
 * Supreme Council seats, elections, and council votes.
 */
@Service
class AlliancePoliticsService(
    private val councilSeatRepository: CouncilSeatRepository,
    private val electionRepository: ElectionRepository,
    private val officerRepository: OfficerRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Election duration in ticks (approx 1 game-day = 3600 ticks at 24x) */
        const val ELECTION_DURATION_TICKS = 3600
        /** Council term duration in ticks (approx 1 game-month = 108000 ticks) */
        const val COUNCIL_TERM_TICKS = 108000L
    }

    /**
     * Initialize the 11 Supreme Council seats for an Alliance faction.
     */
    @Transactional
    fun initializeCouncil(sessionId: Long, factionId: Long) {
        val existing = councilSeatRepository.findBySessionIdAndFactionId(sessionId, factionId)
        if (existing.isNotEmpty()) {
            log.info("[Session {}] Council already initialized for faction {}", sessionId, factionId)
            return
        }

        val seats = CouncilSeatCode.entries.map { code ->
            CouncilSeat(
                sessionId = sessionId,
                factionId = factionId,
                seatCode = code.code,
            )
        }
        councilSeatRepository.saveAll(seats)
        log.info("[Session {}] Initialized {} council seats for Alliance faction {}", sessionId, seats.size, factionId)
    }

    /**
     * Start an election for the Alliance faction.
     */
    @Transactional
    fun startElection(sessionId: Long, factionId: Long, type: ElectionType, seatCode: String? = null): Election {
        // Check for active elections
        val active = electionRepository.findBySessionIdAndFactionIdAndIsCompleted(sessionId, factionId, false)
        if (active.isNotEmpty()) {
            throw IllegalStateException("An election is already in progress for faction $factionId")
        }

        val election = Election(
            sessionId = sessionId,
            factionId = factionId,
            electionType = type.code,
            meta = mutableMapOf<String, Any>().also { m ->
                if (seatCode != null) m["seatCode"] = seatCode
            },
        )
        val saved = electionRepository.save(election)
        log.info("[Session {}] Election started: type={}, faction={}", sessionId, type.code, factionId)
        return saved
    }

    /**
     * Add a candidate to an active election.
     */
    @Transactional
    fun nominateCandidate(sessionId: Long, factionId: Long, electionId: Long, officerId: Long) {
        val election = electionRepository.findById(electionId).orElseThrow {
            IllegalArgumentException("Election not found: $electionId")
        }
        if (election.isCompleted) throw IllegalStateException("Election already completed")

        val officer = officerRepository.findById(officerId).orElseThrow {
            IllegalArgumentException("Officer not found: $officerId")
        }

        // Check not already a candidate
        val alreadyCandidate = election.candidates.any {
            (it["officerId"] as? Number)?.toLong() == officerId
        }
        if (alreadyCandidate) return

        election.candidates.add(mapOf(
            "officerId" to officerId,
            "name" to officer.name,
        ))
        electionRepository.save(election)
        log.info("[Session {}] Candidate nominated: officer {} for election {}", sessionId, officerId, electionId)
    }

    /**
     * Cast a vote in an election.
     */
    @Transactional
    fun castVote(sessionId: Long, electionId: Long, voterId: Long, candidateId: Long) {
        val election = electionRepository.findById(electionId).orElseThrow {
            IllegalArgumentException("Election not found: $electionId")
        }
        if (election.isCompleted) throw IllegalStateException("Election already completed")

        // Verify candidate exists
        val isCandidate = election.candidates.any {
            (it["officerId"] as? Number)?.toLong() == candidateId
        }
        if (!isCandidate) throw IllegalArgumentException("Officer $candidateId is not a candidate")

        // Record vote (one vote per officer, overwrites previous)
        election.votes[voterId.toString()] = candidateId
        electionRepository.save(election)
    }

    /**
     * Resolve an election: tally votes, seat the winner.
     */
    @Transactional
    fun resolveElection(sessionId: Long, electionId: Long) {
        val election = electionRepository.findById(electionId).orElseThrow {
            IllegalArgumentException("Election not found: $electionId")
        }
        if (election.isCompleted) return

        // Tally votes
        val voteCounts = mutableMapOf<Long, Int>()
        for ((_, candidateIdAny) in election.votes) {
            val candidateId = (candidateIdAny as Number).toLong()
            voteCounts[candidateId] = (voteCounts[candidateId] ?: 0) + 1
        }

        // Find winner (most votes, tie goes to first candidate)
        val winnerId = voteCounts.maxByOrNull { it.value }?.key
        if (winnerId != null) {
            election.winnerOfficerId = winnerId

            // Seat the winner in the council if applicable
            val seatCode = election.meta["seatCode"] as? String
            if (seatCode != null) {
                val seat = councilSeatRepository.findBySessionIdAndFactionIdAndSeatCode(
                    sessionId, election.factionId, seatCode
                )
                if (seat != null) {
                    seat.officerId = winnerId
                    seat.electedAt = OffsetDateTime.now()
                    seat.termEndAt = OffsetDateTime.now().plusSeconds(COUNCIL_TERM_TICKS)
                    seat.votesReceived = voteCounts[winnerId] ?: 0
                    councilSeatRepository.save(seat)
                }
            }
        }

        election.isCompleted = true
        election.endedAt = OffsetDateTime.now()
        electionRepository.save(election)

        log.info("[Session {}] Election {} resolved. Winner: {}", sessionId, electionId, winnerId)
    }

    /**
     * Call a vote of no-confidence against a seated council member.
     */
    @Transactional
    fun callConfidenceVote(sessionId: Long, factionId: Long, targetOfficerId: Long): Election {
        return startElection(sessionId, factionId, ElectionType.CONFIDENCE_VOTE).also {
            it.meta["targetOfficerId"] = targetOfficerId
            electionRepository.save(it)
        }
    }

    /**
     * Get current council status.
     */
    fun getCouncilStatus(sessionId: Long, factionId: Long): List<CouncilSeat> {
        return councilSeatRepository.findBySessionIdAndFactionId(sessionId, factionId)
    }

    /**
     * Get active (uncompleted) elections.
     */
    fun getActiveElections(sessionId: Long, factionId: Long): List<Election> {
        return electionRepository.findBySessionIdAndFactionIdAndIsCompleted(sessionId, factionId, false)
    }

    /**
     * Process election tick: auto-resolve expired elections.
     */
    @Transactional
    fun processElectionTick(sessionId: Long) {
        val active = electionRepository.findBySessionIdAndIsCompleted(sessionId, false)
        val now = OffsetDateTime.now()
        for (election in active) {
            val electionDeadline = election.startedAt.plusSeconds(ELECTION_DURATION_TICKS.toLong())
            if (now.isAfter(electionDeadline)) {
                resolveElection(sessionId, election.id)
            }
        }
    }
}
