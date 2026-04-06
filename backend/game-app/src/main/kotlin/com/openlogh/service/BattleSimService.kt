package com.openlogh.service

import com.openlogh.dto.SimulateRequest
import com.openlogh.dto.SimulateResult
import org.springframework.stereotype.Service

/**
 * Battle Simulation Service — stub placeholder.
 *
 * TODO Phase 3: 삼국지 BattleEngine 제거됨. gin7 전술전 엔진(TacticalBattleEngine)으로 대체 예정.
 * 삼국지 수치비교 전투 로직 완전 제거됨.
 */
@Service
class BattleSimService {

    fun simulate(request: SimulateRequest): SimulateResult {
        // TODO Phase 3: gin7 TacticalBattleEngine 기반 전투 시뮬레이션으로 대체
        return SimulateResult(
            winner = "미구현",
            attackerRemaining = 0,
            defenderRemaining = 0,
            defendersRemaining = emptyList(),
            rounds = 0,
            terrain = request.terrain,
            weather = request.weather,
            logs = listOf("TODO Phase 3: gin7 전투 엔진으로 대체 예정"),
            phaseDetails = null,
        )
    }
}
