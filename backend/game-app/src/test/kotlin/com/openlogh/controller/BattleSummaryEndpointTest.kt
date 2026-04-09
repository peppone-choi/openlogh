package com.openlogh.controller

import com.openlogh.dto.BattleSummaryDto
import com.openlogh.dto.BattleSummaryRow
import com.openlogh.service.TacticalBattleService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

/**
 * Phase 14 Plan 14-02 Task 1 — RED/GREEN: BattleSummary REST endpoint contract.
 *
 * The endpoint `/api/v1/battle/{sessionId}/{battleId}/summary` must expose a
 * per-unit merit breakdown so the end-of-battle modal (14-18, D-32..D-34) can
 * render "기본 X + 작전 +Y = 총 Z" rows. Phase 12 persists the summed merit
 * to Officer.meritPoints but not the breakdown — this endpoint computes it
 * from the persisted TacticalBattle snapshot so OPS-02 (×1.5 operation
 * multiplier) is visually verifiable.
 *
 * Contract:
 *   200 — battle ended → BattleSummaryDto with rows[]
 *   404 — NoSuchElementException → battle not found
 *   409 — IllegalStateException → battle not yet ended
 *
 * Uses plain Mockito (mockito-kotlin is NOT on the :game-app classpath —
 * see build.gradle.kts:85 exclusion, documented in Phase 12 D-17). Mirrors
 * GeneralControllerTest's lightweight constructor-injection pattern rather
 * than the SpringBootTest + Transactional pattern used by
 * OperationMeritBonusTest, because this test only validates HTTP mapping.
 */
class BattleSummaryEndpointTest {

    private lateinit var tacticalBattleService: TacticalBattleService
    private lateinit var controller: TacticalBattleRestController

    @BeforeEach
    fun setUp() {
        tacticalBattleService = mock(TacticalBattleService::class.java)
        controller = TacticalBattleRestController(tacticalBattleService)
    }

    private fun sampleDto(battleId: Long = 42L): BattleSummaryDto = BattleSummaryDto(
        battleId = battleId,
        winner = "attacker_win",
        durationTicks = 180,
        rows = listOf(
            BattleSummaryRow(
                fleetId = 1L,
                officerId = 10L,
                officerName = "라인하르트",
                side = "ATTACKER",
                survivingShips = 300,
                maxShips = 300,
                baseMerit = 100,
                operationMultiplier = 1.5,
                totalMerit = 150,
                isOperationParticipant = true,
            ),
            BattleSummaryRow(
                fleetId = 2L,
                officerId = 11L,
                officerName = "키르히아이스",
                side = "ATTACKER",
                survivingShips = 270,
                maxShips = 300,
                baseMerit = 90,
                operationMultiplier = 1.0,
                totalMerit = 90,
                isOperationParticipant = false,
            ),
            BattleSummaryRow(
                fleetId = 3L,
                officerId = 20L,
                officerName = "양웬리",
                side = "DEFENDER",
                survivingShips = 0,
                maxShips = 300,
                baseMerit = 0,
                operationMultiplier = 1.0,
                totalMerit = 0,
                isOperationParticipant = false,
            ),
        ),
    )

    @Test
    fun `returns 200 with merit breakdown for ended battle`() {
        val dto = sampleDto()
        `when`(tacticalBattleService.buildBattleSummary(1L, 42L)).thenReturn(dto)

        val response = controller.getBattleSummary(sessionId = 1L, battleId = 42L)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body, "200 response must have a body")
        assertEquals(42L, body!!.battleId)
        assertEquals("attacker_win", body.winner)
        assertEquals(180, body.durationTicks)
        assertEquals(3, body.rows.size)

        // Participant with full survival on winning side: base=100, ×1.5 = 150
        val participant = body.rows.first { it.isOperationParticipant }
        assertEquals(100, participant.baseMerit, "winning full-survival base must be 100")
        assertEquals(1.5, participant.operationMultiplier, "participant multiplier must be 1.5")
        assertEquals(150, participant.totalMerit, "150 = 100 × 1.5")

        // Non-participant on winning side: base > 0, ×1.0 = base
        val nonParticipant = body.rows.first {
            !it.isOperationParticipant && it.side == "ATTACKER"
        }
        assertEquals(1.0, nonParticipant.operationMultiplier, "non-participant multiplier must be 1.0")
        assertEquals(nonParticipant.baseMerit, nonParticipant.totalMerit, "non-participant total == base")
        assertTrue(nonParticipant.baseMerit > 0, "winning side must have positive base merit")

        // Losing side: base must be 0
        val loser = body.rows.first { it.side == "DEFENDER" }
        assertEquals(0, loser.baseMerit, "losing side base merit must be 0")
        assertEquals(0, loser.totalMerit, "losing side total merit must be 0")
    }

    @Test
    fun `returns 404 for non-existent battle`() {
        `when`(tacticalBattleService.buildBattleSummary(1L, 999L))
            .thenThrow(NoSuchElementException("Battle 999 not found"))

        val response = controller.getBattleSummary(sessionId = 1L, battleId = 999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `returns 409 when battle not yet ended`() {
        `when`(tacticalBattleService.buildBattleSummary(1L, 7L))
            .thenThrow(IllegalStateException("전투가 아직 종료되지 않았습니다"))

        val response = controller.getBattleSummary(sessionId = 1L, battleId = 7L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
