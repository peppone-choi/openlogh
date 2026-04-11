package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-31 (gaps C14/C15, gin7 매뉴얼 p52 육전대 운용):
 * GroundForceDeployCommand / GroundForceWithdrawCommand 의 상태 전환·idempotency·
 * 함대 보유 검증·감사 카운터를 커버한다.
 */
class GroundForceCommandsTest {

    private fun mkEnv(): CommandEnv = CommandEnv(
        year = 800, month = 1, startYear = 790, sessionId = 1L,
    )

    private fun mkOfficer(): Officer = Officer(name = "Mueller").also {
        it.sessionId = 1L
        it.planetId = 1L
        it.factionId = 1L
    }

    private fun mkFleet(): Fleet = Fleet(planetId = 1L).also {
        it.id = 1L
        it.sessionId = 1L
        it.factionId = 1L
    }

    @Test
    fun `C14 - deploy flips stance from WITHDRAWN to DEPLOYED`() = runBlocking {
        val officer = mkOfficer()
        val cmd = GroundForceDeployCommand(officer, mkEnv(), null).apply { troop = mkFleet() }

        val result = cmd.run(Random(0))

        assertTrue(result.success)
        assertEquals(GROUND_FORCE_STANCE_DEPLOYED, officer.meta["groundForceStance"])
        assertEquals(1, officer.meta["groundForceDeployCount"])
    }

    @Test
    fun `C14 - deploy is idempotent when already DEPLOYED (no counter bump)`() = runBlocking {
        val officer = mkOfficer().also { it.meta["groundForceStance"] = GROUND_FORCE_STANCE_DEPLOYED }
        val cmd = GroundForceDeployCommand(officer, mkEnv(), null).apply { troop = mkFleet() }

        val result = cmd.run(Random(0))

        assertTrue(result.success, "idempotent deploy must still be a success")
        assertEquals(GROUND_FORCE_STANCE_DEPLOYED, officer.meta["groundForceStance"])
        // Counter must NOT advance on a no-op.
        assertEquals(null, officer.meta["groundForceDeployCount"])
        assertTrue(result.logs.any { it.contains("이미 출격") },
            "idempotent dispatch must emit the 이미 log. got: ${result.logs}")
    }

    @Test
    fun `C14 - deploy fails when officer has no fleet`() = runBlocking {
        val officer = mkOfficer()
        val cmd = GroundForceDeployCommand(officer, mkEnv(), null).apply { troop = null }

        val result = cmd.run(Random(0))

        assertFalse(result.success, "단독 장교는 육전대를 출격시킬 수 없다")
        assertTrue(result.message?.contains("함대가 없는") == true)
        assertEquals(null, officer.meta["groundForceStance"],
            "실패 시 stance 가 오염되지 않아야 한다")
    }

    @Test
    fun `C15 - withdraw flips stance from DEPLOYED to WITHDRAWN`() = runBlocking {
        val officer = mkOfficer().also { it.meta["groundForceStance"] = GROUND_FORCE_STANCE_DEPLOYED }
        val cmd = GroundForceWithdrawCommand(officer, mkEnv(), null)

        val result = cmd.run(Random(0))

        assertTrue(result.success)
        assertEquals(GROUND_FORCE_STANCE_WITHDRAWN, officer.meta["groundForceStance"])
        assertEquals(1, officer.meta["groundForceWithdrawCount"])
    }

    @Test
    fun `C15 - withdraw is idempotent when already WITHDRAWN`() = runBlocking {
        val officer = mkOfficer()  // default stance == WITHDRAWN
        val cmd = GroundForceWithdrawCommand(officer, mkEnv(), null)

        val result = cmd.run(Random(0))

        assertTrue(result.success)
        assertEquals(null, officer.meta["groundForceWithdrawCount"])
        assertTrue(result.logs.any { it.contains("이미 철수") })
    }

    @Test
    fun `C15 - withdraw does not require a fleet (allows orphaned withdraw)`() = runBlocking {
        // 함대가 파괴된 상태에서도 철수 처리는 가능해야 한다 — 의도적 비대칭.
        val officer = mkOfficer().also { it.meta["groundForceStance"] = GROUND_FORCE_STANCE_DEPLOYED }
        val cmd = GroundForceWithdrawCommand(officer, mkEnv(), null)

        val result = cmd.run(Random(0))
        assertTrue(result.success)
    }

    @Test
    fun `deploy-then-withdraw cycle increments both counters`() = runBlocking {
        val officer = mkOfficer()
        val fleet = mkFleet()

        val deploy = GroundForceDeployCommand(officer, mkEnv(), null).apply { troop = fleet }
        deploy.run(Random(0))
        val withdraw = GroundForceWithdrawCommand(officer, mkEnv(), null).apply { troop = fleet }
        withdraw.run(Random(0))

        assertEquals(1, officer.meta["groundForceDeployCount"])
        assertEquals(1, officer.meta["groundForceWithdrawCount"])
        assertEquals(GROUND_FORCE_STANCE_WITHDRAWN, officer.meta["groundForceStance"])
    }

    @Test
    fun `C14-C15 - stance constants are stable strings`() {
        assertEquals("DEPLOYED", GROUND_FORCE_STANCE_DEPLOYED)
        assertEquals("WITHDRAWN", GROUND_FORCE_STANCE_WITHDRAWN)
    }
}
