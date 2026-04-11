package com.openlogh.command.gin7.intelligence

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 24-28 (gap B4/B5, gin7 매뉴얼 p77):
 *
 * 매뉴얼의 전략 커맨드 일람표는 침입공작(Incursion) 과 귀환공작(Return Op) 의
 * 커맨드 포인트 비용을 320 으로 명시한다. v2.4 이전에는 두 커맨드 모두 160
 * (2 배 낮음)으로 설정되어 첩보 밸런스가 흔들렸다.
 *
 * Phase 24-05 가 CommandCostTable 을 BaseCommand 경로로 배선해 두 커맨드의
 * getCommandPointCost() 가 매뉴얼 값 320 을 반환하도록 정정했지만, 이 값이
 * 의도치 않게 회귀하는 것을 막기 위해 전용 회귀 테스트로 고정한다. 누군가
 * 이 값을 다시 낮추면 .planning 에 근거를 남기고 이 테스트를 갱신해야 한다.
 */
class IncursionReturnOpCpTest {

    private fun mkEnv(): CommandEnv = CommandEnv(
        year = 800,
        month = 1,
        startYear = 790,
        sessionId = 1L,
    )

    private fun mkOfficer(): Officer = Officer(name = "Agent").also {
        it.sessionId = 1L
        it.planetId = 1L
    }

    @Test
    fun `B4 - IncursionOpCommand cp cost pinned at 320`() {
        val cmd = IncursionOpCommand(mkOfficer(), mkEnv(), mapOf("destPlanetId" to 42L))
        assertEquals(
            320, cmd.getCommandPointCost(),
            "gin7 매뉴얼 p77: 침입공작 CP = 320 (was 160 before Phase 24-05 rescale)"
        )
        assertEquals("침입공작", cmd.actionName)
    }

    @Test
    fun `B5 - ReturnOpCommand cp cost pinned at 320`() {
        val cmd = ReturnOpCommand(mkOfficer(), mkEnv(), mapOf("destPlanetId" to 42L))
        assertEquals(
            320, cmd.getCommandPointCost(),
            "gin7 매뉴얼 p77: 귀환공작 CP = 320 (was 160 before Phase 24-05 rescale)"
        )
        assertEquals("귀환공작", cmd.actionName)
    }
}
