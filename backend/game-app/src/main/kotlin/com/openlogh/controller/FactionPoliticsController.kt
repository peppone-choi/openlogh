package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.model.*
import com.openlogh.service.AlliancePoliticsService
import com.openlogh.service.EmpirePoliticsService
import com.openlogh.service.FezzanService
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/world/{sessionId}/politics")
class FactionPoliticsController(
    private val empirePoliticsService: EmpirePoliticsService,
    private val alliancePoliticsService: AlliancePoliticsService,
    private val fezzanService: FezzanService,
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
) {

    // ========== Overview ==========

    @GetMapping("/overview")
    fun getOverview(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): FactionPoliticsOverviewDto {
        val faction = factionRepository.findById(factionId).orElseThrow {
            IllegalArgumentException("Faction not found: $factionId")
        }
        val leader = officerRepository.findById(faction.chiefOfficerId).orElse(null)

        val governanceType = when (faction.factionType) {
            "empire" -> "autocracy"
            "alliance" -> "democracy"
            "fezzan" -> "npc"
            else -> "unknown"
        }

        val councilStatus = if (faction.factionType == "alliance") {
            buildCouncilStatusDto(sessionId, factionId)
        } else null

        val activeCoup = if (faction.factionType == "empire") {
            buildCoupStatusDto(sessionId, factionId)
        } else null

        val activeElection = if (faction.factionType == "alliance") {
            val elections = alliancePoliticsService.getActiveElections(sessionId, factionId)
            elections.firstOrNull()?.let { buildElectionDto(it) }
        } else null

        val loans = fezzanService.getActiveLoans(sessionId, factionId).map { loan ->
            LoanDto(
                loanId = loan.id,
                principal = loan.principal,
                interestRate = loan.interestRate,
                remainingDebt = loan.remainingDebt,
                issuedAt = loan.issuedAt,
                dueAt = loan.dueAt,
                isDefaulted = loan.isDefaulted,
            )
        }

        return FactionPoliticsOverviewDto(
            factionId = factionId,
            factionType = faction.factionType,
            governanceType = governanceType,
            leaderName = leader?.name,
            leaderId = leader?.id,
            councilStatus = councilStatus,
            activeCoup = activeCoup,
            activeElection = activeElection,
            loans = loans,
            fezzanOperational = fezzanService.isFezzanOperational(sessionId),
        )
    }

    // ========== Empire ==========

    @GetMapping("/empire/coup")
    fun getCoupStatus(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): CoupStatusDto? = buildCoupStatusDto(sessionId, factionId)

    @PostMapping("/empire/coup/initiate")
    fun initiateCoup(
        @PathVariable sessionId: Long,
        @RequestBody request: InitiateCoupRequest,
        @RequestParam officerId: Long,
    ): CoupStatusDto {
        val coup = empirePoliticsService.initiateCoup(sessionId, request.factionId, officerId)
        val leader = officerRepository.findById(coup.leaderId).orElse(null)
        return CoupStatusDto(
            coupId = coup.id,
            phase = coup.phase,
            leaderId = coup.leaderId,
            leaderName = leader?.name ?: "",
            supporterCount = coup.supporterIds.size,
            politicalPower = coup.politicalPower,
            threshold = coup.threshold,
            startedAt = coup.startedAt,
        )
    }

    @PostMapping("/empire/coup/{coupId}/join")
    fun joinCoup(
        @PathVariable sessionId: Long,
        @PathVariable coupId: Long,
        @RequestParam officerId: Long,
    ) {
        empirePoliticsService.joinCoup(sessionId, coupId, officerId)
    }

    @PostMapping("/empire/coup/{coupId}/abort")
    fun abortCoup(
        @PathVariable sessionId: Long,
        @PathVariable coupId: Long,
        @RequestParam officerId: Long,
    ) {
        empirePoliticsService.abortCoup(sessionId, coupId, officerId)
    }

    @GetMapping("/empire/nobility")
    fun getNobility(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): List<NobilityDto> {
        return empirePoliticsService.getNobilityList(sessionId, factionId).map { (officer, rank) ->
            NobilityDto(
                officerId = officer.id,
                officerName = officer.name,
                rank = rank.code,
                nameKo = rank.nameKo,
                politicsBonus = rank.politicsBonus,
            )
        }
    }

    // ========== Alliance ==========

    @GetMapping("/alliance/council")
    fun getCouncilStatus(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): CouncilStatusDto = buildCouncilStatusDto(sessionId, factionId)

    @GetMapping("/alliance/election")
    fun getActiveElection(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): ElectionDto? {
        val elections = alliancePoliticsService.getActiveElections(sessionId, factionId)
        return elections.firstOrNull()?.let { buildElectionDto(it) }
    }

    @PostMapping("/alliance/election/start")
    fun startElection(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
        @RequestBody request: StartElectionRequest,
    ): ElectionDto {
        val election = alliancePoliticsService.startElection(
            sessionId, factionId, ElectionType.COUNCIL_CHAIR, request.seatCode
        )
        return buildElectionDto(election)
    }

    @PostMapping("/alliance/election/vote")
    fun castVote(
        @PathVariable sessionId: Long,
        @RequestParam officerId: Long,
        @RequestBody request: CastElectionVoteRequest,
    ) {
        alliancePoliticsService.castVote(sessionId, request.electionId, officerId, request.candidateId)
    }

    // ========== Fezzan ==========

    @GetMapping("/fezzan/loans")
    fun getLoans(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
    ): List<LoanDto> {
        return fezzanService.getActiveLoans(sessionId, factionId).map { loan ->
            LoanDto(
                loanId = loan.id,
                principal = loan.principal,
                interestRate = loan.interestRate,
                remainingDebt = loan.remainingDebt,
                issuedAt = loan.issuedAt,
                dueAt = loan.dueAt,
                isDefaulted = loan.isDefaulted,
            )
        }
    }

    @PostMapping("/fezzan/loan/take")
    fun takeLoan(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
        @RequestBody request: TakeLoanRequest,
    ): LoanDto {
        val loan = fezzanService.offerLoan(sessionId, factionId, request.amount)
        return LoanDto(
            loanId = loan.id,
            principal = loan.principal,
            interestRate = loan.interestRate,
            remainingDebt = loan.remainingDebt,
            issuedAt = loan.issuedAt,
            dueAt = loan.dueAt,
            isDefaulted = loan.isDefaulted,
        )
    }

    @PostMapping("/fezzan/loan/repay")
    fun repayLoan(
        @PathVariable sessionId: Long,
        @RequestBody request: RepayLoanRequest,
    ) {
        fezzanService.repayLoan(sessionId, request.loanId, request.amount)
    }

    @GetMapping("/fezzan/intel")
    fun getIntelOffers(@PathVariable sessionId: Long): List<IntelligenceOfferDto> {
        if (!fezzanService.isFezzanOperational(sessionId)) return emptyList()
        return IntelligenceType.entries.map { type ->
            IntelligenceOfferDto(
                type = type.code,
                nameKo = type.nameKo,
                cost = type.cost,
                description = when (type) {
                    IntelligenceType.FLEET_POSITIONS -> "적 함대의 현재 위치를 파악합니다"
                    IntelligenceType.PLANET_RESOURCES -> "적 행성의 자원 현황을 파악합니다"
                    IntelligenceType.OFFICER_INFO -> "적 장교들의 정보를 수집합니다"
                    IntelligenceType.MILITARY_POWER -> "적 진영의 군사력을 분석합니다"
                    IntelligenceType.COUP_INTEL -> "적 진영 내부의 쿠데타 동향을 탐지합니다"
                },
            )
        }
    }

    @PostMapping("/fezzan/intel/buy")
    fun buyIntel(
        @PathVariable sessionId: Long,
        @RequestParam factionId: Long,
        @RequestBody request: BuyIntelRequest,
    ): Map<String, Any> {
        val type = IntelligenceType.fromCode(request.intelligenceType)
            ?: throw IllegalArgumentException("Unknown intelligence type: ${request.intelligenceType}")
        return fezzanService.purchaseIntelligence(sessionId, factionId, request.targetFactionId, type)
    }

    // ========== Helpers ==========

    private fun buildCoupStatusDto(sessionId: Long, factionId: Long): CoupStatusDto? {
        val coup = empirePoliticsService.getCoupStatus(sessionId, factionId) ?: return null
        val leader = officerRepository.findById(coup.leaderId).orElse(null)
        return CoupStatusDto(
            coupId = coup.id,
            phase = coup.phase,
            leaderId = coup.leaderId,
            leaderName = leader?.name ?: "",
            supporterCount = coup.supporterIds.size,
            politicalPower = coup.politicalPower,
            threshold = coup.threshold,
            startedAt = coup.startedAt,
        )
    }

    private fun buildCouncilStatusDto(sessionId: Long, factionId: Long): CouncilStatusDto {
        val seats = alliancePoliticsService.getCouncilStatus(sessionId, factionId)
        return CouncilStatusDto(
            seats = seats.map { seat ->
                val seatEnum = CouncilSeatCode.fromCode(seat.seatCode)
                val officer = seat.officerId?.let { officerRepository.findById(it).orElse(null) }
                CouncilSeatDto(
                    seatCode = seat.seatCode,
                    nameKo = seatEnum?.nameKo ?: seat.seatCode,
                    officerId = seat.officerId,
                    officerName = officer?.name,
                    electedAt = seat.electedAt,
                    termEndAt = seat.termEndAt,
                )
            }
        )
    }

    private fun buildElectionDto(election: com.openlogh.entity.Election): ElectionDto {
        // Tally votes for each candidate
        val voteCounts = mutableMapOf<Long, Int>()
        for ((_, candidateIdAny) in election.votes) {
            val candidateId = (candidateIdAny as Number).toLong()
            voteCounts[candidateId] = (voteCounts[candidateId] ?: 0) + 1
        }

        return ElectionDto(
            electionId = election.id,
            type = election.electionType,
            startedAt = election.startedAt,
            candidates = election.candidates.map { c ->
                val officerId = (c["officerId"] as Number).toLong()
                CandidateDto(
                    officerId = officerId,
                    officerName = c["name"] as? String ?: "",
                    votes = voteCounts[officerId] ?: 0,
                )
            },
            isCompleted = election.isCompleted,
            winnerId = election.winnerOfficerId,
        )
    }
}
