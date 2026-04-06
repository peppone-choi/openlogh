package com.openlogh.engine.tactical

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlanetConquestServiceTest {

    private lateinit var service: PlanetConquestService

    private fun baseRequest(
        command: ConquestCommand,
        planetDefense: Int = 3000,
        missileCount: Int = 1000,
        militaryWorkPoint: Int = 0,
        intelWorkPoint: Int = 0,
    ) = ConquestRequest(
        command = command,
        attackerOfficerId = 1L,
        attackerFactionId = 10L,
        attackerFactionType = "empire",
        defenderFactionId = 20L,
        planetId = 99L,
        planetName = "테스트행성",
        planetDefense = planetDefense,
        garrisonUnits = 5,
        warehouseSupplies = 500,
        shipyardShipsInProgress = 10,
        shipyardShipsStored = 20,
        isFortress = false,
        defeatedOfficerIds = listOf(101L, 102L),
        planetPositionCards = listOf("GARRISON_COMMANDER"),
        attackerMissileCount = missileCount,
        militaryWorkPoint = militaryWorkPoint,
        intelWorkPoint = intelWorkPoint,
    )

    @BeforeEach
    fun setUp() {
        service = PlanetConquestService()
    }

    // ── PRECISION_BOMBING ──

    @Test
    fun `PRECISION_BOMBING fails when missile count is insufficient`() {
        val req = baseRequest(ConquestCommand.PRECISION_BOMBING, missileCount = 100)
        val result = service.executeConquest(req)
        assertFalse(result.success)
        assertTrue(result.reason.contains("미사일 부족"))
    }

    @Test
    fun `PRECISION_BOMBING succeeds with enough missiles and applies defenseMultiplier`() {
        val req = baseRequest(ConquestCommand.PRECISION_BOMBING, missileCount = 200, planetDefense = 1000)
        val result = service.executeConquest(req)
        assertEquals(200, result.missilesConsumed)
        assertEquals(0.6, result.defenseMultiplier, 0.001)
    }

    @Test
    fun `PRECISION_BOMBING consumes 200 missiles even when not conquering`() {
        val req = baseRequest(ConquestCommand.PRECISION_BOMBING, missileCount = 500, planetDefense = 5000)
        val result = service.executeConquest(req)
        assertEquals(200, result.missilesConsumed)
    }

    // ── CARPET_BOMBING ──

    @Test
    fun `CARPET_BOMBING fails when missile count is below 500`() {
        val req = baseRequest(ConquestCommand.CARPET_BOMBING, missileCount = 499)
        val result = service.executeConquest(req)
        assertFalse(result.success)
        assertTrue(result.reason.contains("미사일 부족"))
    }

    @Test
    fun `CARPET_BOMBING applies correct approval and economy effects`() {
        val req = baseRequest(ConquestCommand.CARPET_BOMBING, missileCount = 500, planetDefense = 5000)
        val result = service.executeConquest(req)
        assertEquals(-30, result.approvalChange)
        assertEquals(0.5, result.economyMultiplier, 0.001)
        assertEquals(500, result.missilesConsumed)
    }

    // ── INFILTRATION ──

    @Test
    fun `INFILTRATION fails when militaryWorkPoint is below 4000`() {
        val req = baseRequest(ConquestCommand.INFILTRATION, militaryWorkPoint = 3999)
        val result = service.executeConquest(req)
        assertFalse(result.success)
        assertTrue(result.reason.contains("군사공작포인트 부족"))
    }

    @Test
    fun `INFILTRATION succeeds with militaryWorkPoint exactly 4000`() {
        val req = baseRequest(ConquestCommand.INFILTRATION, militaryWorkPoint = 4000)
        val result = service.executeConquest(req)
        assertTrue(result.success)
        assertNotNull(result.captureResult)
    }

    @Test
    fun `INFILTRATION succeeds and captureResult contains logs`() {
        val req = baseRequest(ConquestCommand.INFILTRATION, militaryWorkPoint = 5000)
        val result = service.executeConquest(req)
        assertTrue(result.success)
        assertTrue(result.captureResult!!.logs.isNotEmpty())
    }

    // ── SUBVERSION ──

    @Test
    fun `SUBVERSION fails when intelWorkPoint is below 1000`() {
        val req = baseRequest(ConquestCommand.SUBVERSION, intelWorkPoint = 999)
        val result = service.executeConquest(req)
        assertFalse(result.success)
        assertTrue(result.reason.contains("정보공작포인트 부족"))
    }

    @Test
    fun `SUBVERSION succeeds with intelWorkPoint exactly 1000 and applies approval change`() {
        val req = baseRequest(ConquestCommand.SUBVERSION, intelWorkPoint = 1000)
        val result = service.executeConquest(req)
        assertTrue(result.success)
        assertEquals(-50, result.approvalChange)
    }

    // ── SURRENDER_DEMAND ──

    @Test
    fun `SURRENDER_DEMAND has approximately 30 percent success rate when defense below 5000`() {
        val req = baseRequest(ConquestCommand.SURRENDER_DEMAND, planetDefense = 4000)
        var successCount = 0
        repeat(1000) {
            val result = service.executeConquest(req, Random(it.toLong()))
            if (result.success) successCount++
        }
        // Expect ~300 successes (±5% tolerance → 250..350)
        assertTrue(successCount in 250..350,
            "Expected ~30% success rate, got $successCount/1000")
    }

    @Test
    fun `SURRENDER_DEMAND has approximately 5 percent success rate when defense above 5000`() {
        val req = baseRequest(ConquestCommand.SURRENDER_DEMAND, planetDefense = 6000)
        var successCount = 0
        repeat(1000) {
            val result = service.executeConquest(req, Random(it.toLong()))
            if (result.success) successCount++
        }
        // Expect ~50 successes (±3% tolerance → 20..80)
        assertTrue(successCount in 20..80,
            "Expected ~5% success rate, got $successCount/1000")
    }

    // ── GROUND_ASSAULT ──

    @Test
    fun `GROUND_ASSAULT returns ground battle start signal`() {
        val req = baseRequest(ConquestCommand.GROUND_ASSAULT)
        val result = service.executeConquest(req)
        assertFalse(result.success)  // not captured yet — ground battle commences
        assertTrue(result.reason.contains("지상전 개시"))
    }

    // ── captureResult integration ──

    @Test
    fun `capture result includes empire direct rule when captorFactionType is empire`() {
        val req = baseRequest(ConquestCommand.INFILTRATION, militaryWorkPoint = 4000)
        val result = service.executeConquest(req)
        assertTrue(result.captureResult!!.empireDirectRule)
    }

    @Test
    fun `capture result garrison annihilated matches garrisonUnits`() {
        val req = baseRequest(ConquestCommand.INFILTRATION, militaryWorkPoint = 4000)
        val result = service.executeConquest(req)
        assertEquals(5, result.captureResult!!.garrisonAnnihilated)
    }

    // ── ConquestCommand enum ──

    @Test
    fun `all 6 ConquestCommand values exist`() {
        val commands = ConquestCommand.entries
        assertEquals(6, commands.size)
        assertTrue(ConquestCommand.SURRENDER_DEMAND in commands)
        assertTrue(ConquestCommand.PRECISION_BOMBING in commands)
        assertTrue(ConquestCommand.CARPET_BOMBING in commands)
        assertTrue(ConquestCommand.GROUND_ASSAULT in commands)
        assertTrue(ConquestCommand.INFILTRATION in commands)
        assertTrue(ConquestCommand.SUBVERSION in commands)
    }
}
