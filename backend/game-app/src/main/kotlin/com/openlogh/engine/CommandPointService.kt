package com.openlogh.engine

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * PCP/MCP 커맨드 포인트 시스템.
 *
 * gin7 매뉴얼 기반:
 * - PCP (정략 커맨드 포인트): 정치/외교/인사/경제 커맨드에 사용
 * - MCP (군사 커맨드 포인트): 군사/작전/전투 커맨드에 사용
 * - 2게임시간(실시간 5분)마다 회복. 회복량 = 정치 + 운영 능력치 영향
 * - 부족 시 다른 CP로 2배 대용
 * - 전술전 중 CP 회복 정지
 * - CP 사용량 누적 → 경험치 연동
 */
@Service
class CommandPointService(
    private val officerRepository: OfficerRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandPointService::class.java)

        /** 기본 최대 CP */
        const val BASE_MAX_CP = 20

        /** 기본 회복량 (2게임시간당) */
        const val BASE_RECOVERY = 2

        /** 대용 시 배율 */
        const val SUBSTITUTION_MULTIPLIER = 2

        /** CP 사용량 → 경험치 전환 단위 (이만큼 사용하면 경험치 1 랜덤 증가) */
        const val CP_TO_EXP_THRESHOLD = 50
    }

    enum class CpType { PCP, MCP }

    /**
     * CP 최대값. 능력치에 따라 증가.
     */
    fun getMaxPcp(officer: Officer): Int =
        BASE_MAX_CP + officer.politics / 5 + officer.administration / 10

    fun getMaxMcp(officer: Officer): Int =
        BASE_MAX_CP + officer.command / 5 + officer.leadership / 10

    /**
     * CP 소비 시도.
     * 해당 CP가 부족하면 다른 CP를 2배로 대용.
     *
     * @return true = 성공, false = 양쪽 모두 부족
     */
    fun consume(officer: Officer, type: CpType, amount: Int): Boolean {
        if (amount <= 0) return true

        return when (type) {
            CpType.PCP -> consumePcp(officer, amount)
            CpType.MCP -> consumeMcp(officer, amount)
        }
    }

    private fun consumePcp(officer: Officer, amount: Int): Boolean {
        if (officer.pcp >= amount) {
            officer.pcp -= amount
            officer.pcpUsedTotal += amount
            checkExpGain(officer, CpType.PCP)
            return true
        }
        // PCP 부족 → MCP 대용 (2배)
        val deficit = amount - officer.pcp
        val mcpNeeded = deficit * SUBSTITUTION_MULTIPLIER
        if (officer.mcp >= mcpNeeded) {
            officer.pcpUsedTotal += officer.pcp
            officer.pcp = 0
            officer.mcp -= mcpNeeded
            officer.mcpUsedTotal += mcpNeeded
            checkExpGain(officer, CpType.PCP)
            checkExpGain(officer, CpType.MCP)
            return true
        }
        return false
    }

    private fun consumeMcp(officer: Officer, amount: Int): Boolean {
        if (officer.mcp >= amount) {
            officer.mcp -= amount
            officer.mcpUsedTotal += amount
            checkExpGain(officer, CpType.MCP)
            return true
        }
        // MCP 부족 → PCP 대용 (2배)
        val deficit = amount - officer.mcp
        val pcpNeeded = deficit * SUBSTITUTION_MULTIPLIER
        if (officer.pcp >= pcpNeeded) {
            officer.mcpUsedTotal += officer.mcp
            officer.mcp = 0
            officer.pcp -= pcpNeeded
            officer.pcpUsedTotal += pcpNeeded
            checkExpGain(officer, CpType.PCP)
            checkExpGain(officer, CpType.MCP)
            return true
        }
        return false
    }

    /**
     * 잔여 CP 확인 (대용 포함).
     */
    fun canAfford(officer: Officer, type: CpType, amount: Int): Boolean {
        if (amount <= 0) return true
        return when (type) {
            CpType.PCP -> {
                if (officer.pcp >= amount) true
                else {
                    val deficit = amount - officer.pcp
                    officer.mcp >= deficit * SUBSTITUTION_MULTIPLIER
                }
            }
            CpType.MCP -> {
                if (officer.mcp >= amount) true
                else {
                    val deficit = amount - officer.mcp
                    officer.pcp >= deficit * SUBSTITUTION_MULTIPLIER
                }
            }
        }
    }

    /**
     * CP 회복 (매 2게임시간 = 실시간 5분마다 호출).
     * 전술전 중인 장교는 회복 정지.
     */
    fun recoverCp(officer: Officer) {
        // 전술전 중이면 회복 정지
        if (officer.locationState == "tactical") return

        val pcpRecovery = BASE_RECOVERY + officer.politics / 20 + officer.administration / 20
        val mcpRecovery = BASE_RECOVERY + officer.command / 20 + officer.leadership / 20

        val maxPcp = getMaxPcp(officer)
        val maxMcp = getMaxMcp(officer)

        officer.pcp = (officer.pcp + pcpRecovery).coerceAtMost(maxPcp)
        officer.mcp = (officer.mcp + mcpRecovery).coerceAtMost(maxMcp)
    }

    /**
     * 전체 장교 CP 일괄 회복 (턴 처리에서 호출).
     */
    fun recoverAllCp(sessionId: Long) {
        val officers = officerRepository.findBySessionId(sessionId)
        for (officer in officers) {
            recoverCp(officer)
        }
        officerRepository.saveAll(officers)
    }

    /**
     * CP 누적 사용량 → 경험치 전환.
     *
     * gin7: PCP 사용 → 통솔/정치/운영/정보 중 랜덤 1개 경험치 +1
     *        MCP 사용 → 지휘/기동/공격/방어 중 랜덤 1개 경험치 +1
     */
    private fun checkExpGain(officer: Officer, type: CpType) {
        when (type) {
            CpType.PCP -> {
                val threshold = CP_TO_EXP_THRESHOLD
                while (officer.pcpUsedTotal >= threshold) {
                    officer.pcpUsedTotal -= threshold
                    gainRandomPcpExp(officer)
                }
            }
            CpType.MCP -> {
                val threshold = CP_TO_EXP_THRESHOLD
                while (officer.mcpUsedTotal >= threshold) {
                    officer.mcpUsedTotal -= threshold
                    gainRandomMcpExp(officer)
                }
            }
        }
    }

    /** PCP → 통솔/정치/운영/정보 경험치 랜덤 +1 */
    private fun gainRandomPcpExp(officer: Officer) {
        when ((Math.random() * 4).toInt()) {
            0 -> { officer.leadershipExp++; if (officer.leadershipExp >= 100) { officer.leadershipExp = 0; officer.leadership++ } }
            1 -> { officer.politicsExp++; if (officer.politicsExp >= 100) { officer.politicsExp = 0; officer.politics++ } }
            2 -> { officer.administrationExp++; if (officer.administrationExp >= 100) { officer.administrationExp = 0; officer.administration++ } }
            3 -> { officer.intelligenceExp++; if (officer.intelligenceExp >= 100) { officer.intelligenceExp = 0; officer.intelligence++ } }
        }
    }

    /** MCP → 지휘/기동/공격/방어 경험치 랜덤 +1 */
    private fun gainRandomMcpExp(officer: Officer) {
        when ((Math.random() * 4).toInt()) {
            0 -> { officer.commandExp++; if (officer.commandExp >= 100) { officer.commandExp = 0; officer.command++ } }
            1 -> { officer.mobilityExp++; if (officer.mobilityExp >= 100) { officer.mobilityExp = 0; officer.mobility++ } }
            2 -> { officer.attackExp++; if (officer.attackExp >= 100) { officer.attackExp = 0; officer.attack++ } }
            3 -> { officer.defenseExp++; if (officer.defenseExp >= 100) { officer.defenseExp = 0; officer.defense++ } }
        }
    }
}
