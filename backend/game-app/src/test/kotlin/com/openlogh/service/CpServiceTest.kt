package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import com.openlogh.model.OfficerStat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CpServiceTest {

    private lateinit var cpService: CpService
    private lateinit var officer: Officer

    @BeforeEach
    fun setUp() {
        cpService = CpService()
        officer = Officer().apply {
            pcp = 10
            mcp = 10
            pcpMax = 20
            mcpMax = 20
            politics = 50
            administration = 30
            command = 60
            mobility = 40
        }
    }

    @Nested
    inner class DeductCp {

        @Test
        fun `deduct PCP with sufficient primary pool`() {
            val result = cpService.deductCp(officer, 5, StatCategory.PCP)

            assertTrue(result.success)
            assertFalse(result.crossUsed)
            assertEquals(5, result.actualCost)
            assertEquals(StatCategory.PCP, result.poolUsed)
            assertNull(result.errorMessage)
            assertEquals(5, officer.pcp)
            assertEquals(10, officer.mcp) // unchanged
        }

        @Test
        fun `deduct MCP with sufficient primary pool`() {
            val result = cpService.deductCp(officer, 3, StatCategory.MCP)

            assertTrue(result.success)
            assertFalse(result.crossUsed)
            assertEquals(3, result.actualCost)
            assertEquals(StatCategory.MCP, result.poolUsed)
            assertEquals(10, officer.pcp) // unchanged
            assertEquals(7, officer.mcp)
        }

        @Test
        fun `deduct exact amount empties pool`() {
            val result = cpService.deductCp(officer, 10, StatCategory.PCP)

            assertTrue(result.success)
            assertFalse(result.crossUsed)
            assertEquals(0, officer.pcp)
        }

        @Test
        fun `cross-use MCP command falls back to PCP at 2x cost`() {
            officer.mcp = 1
            officer.pcp = 10

            val result = cpService.deductCp(officer, 3, StatCategory.MCP)

            assertTrue(result.success)
            assertTrue(result.crossUsed)
            assertEquals(6, result.actualCost) // 3 * 2
            assertEquals(StatCategory.PCP, result.poolUsed)
            assertEquals(4, officer.pcp) // 10 - 6
            assertEquals(1, officer.mcp) // unchanged - cross-use deducts from OTHER pool
        }

        @Test
        fun `cross-use PCP command falls back to MCP at 2x cost`() {
            officer.pcp = 0
            officer.mcp = 10

            val result = cpService.deductCp(officer, 2, StatCategory.PCP)

            assertTrue(result.success)
            assertTrue(result.crossUsed)
            assertEquals(4, result.actualCost) // 2 * 2
            assertEquals(StatCategory.MCP, result.poolUsed)
            assertEquals(0, officer.pcp) // unchanged
            assertEquals(6, officer.mcp) // 10 - 4
        }

        @Test
        fun `insufficient both pools returns failure`() {
            officer.pcp = 1
            officer.mcp = 1

            val result = cpService.deductCp(officer, 5, StatCategory.PCP)

            assertFalse(result.success)
            assertFalse(result.crossUsed)
            assertNotNull(result.errorMessage)
            assertTrue(result.errorMessage!!.contains("커맨드 포인트가 부족합니다"))
            // pools unchanged
            assertEquals(1, officer.pcp)
            assertEquals(1, officer.mcp)
        }

        @Test
        fun `insufficient primary and insufficient cross pool returns failure`() {
            officer.pcp = 2
            officer.mcp = 3 // cross cost would be 6, but mcp only 3

            val result = cpService.deductCp(officer, 4, StatCategory.PCP)

            assertFalse(result.success)
            assertEquals(2, officer.pcp) // unchanged
            assertEquals(3, officer.mcp) // unchanged
        }

        @Test
        fun `cross-use example from plan - MCP cost 3, mcp=1, pcp=10`() {
            officer.mcp = 1
            officer.pcp = 10

            val result = cpService.deductCp(officer, 3, StatCategory.MCP)

            assertTrue(result.success)
            assertTrue(result.crossUsed)
            assertEquals(6, result.actualCost)
            assertEquals(4, officer.pcp) // 10 - 6
            assertEquals(1, officer.mcp) // unchanged
        }
    }

    @Nested
    inner class RegeneratePcpMcp {

        @Test
        fun `regenerates PCP based on politics and administration`() {
            officer.pcp = 5
            officer.pcpMax = 20
            officer.politics = 50
            officer.administration = 30
            // regen = floor((50+30)/20) + 1 = floor(4.0) + 1 = 5

            cpService.regeneratePcpMcp(officer)

            assertEquals(10, officer.pcp) // 5 + 5
        }

        @Test
        fun `regenerates MCP based on command and mobility`() {
            officer.mcp = 5
            officer.mcpMax = 20
            officer.command = 60
            officer.mobility = 40
            // regen = floor((60+40)/20) + 1 = floor(5.0) + 1 = 6

            cpService.regeneratePcpMcp(officer)

            assertEquals(11, officer.mcp) // 5 + 6
        }

        @Test
        fun `PCP capped at pcpMax`() {
            officer.pcp = 18
            officer.pcpMax = 20
            officer.politics = 80
            officer.administration = 80
            // regen = floor((80+80)/20) + 1 = floor(8.0) + 1 = 9

            cpService.regeneratePcpMcp(officer)

            assertEquals(20, officer.pcp) // capped at max, not 18+9=27
        }

        @Test
        fun `MCP capped at mcpMax`() {
            officer.mcp = 19
            officer.mcpMax = 20
            officer.command = 90
            officer.mobility = 90
            // regen = floor((90+90)/20) + 1 = floor(9.0) + 1 = 10

            cpService.regeneratePcpMcp(officer)

            assertEquals(20, officer.mcp) // capped at max
        }

        @Test
        fun `already at max stays at max`() {
            officer.pcp = 20
            officer.pcpMax = 20
            officer.mcp = 20
            officer.mcpMax = 20

            cpService.regeneratePcpMcp(officer)

            assertEquals(20, officer.pcp)
            assertEquals(20, officer.mcp)
        }

        @Test
        fun `low stats give minimum regen of 1`() {
            officer.pcp = 0
            officer.mcp = 0
            officer.politics = 1
            officer.administration = 1
            officer.command = 1
            officer.mobility = 1
            // pcp regen = floor((1+1)/20) + 1 = floor(0.1) + 1 = 1
            // mcp regen = floor((1+1)/20) + 1 = floor(0.1) + 1 = 1

            cpService.regeneratePcpMcp(officer)

            assertEquals(1, officer.pcp)
            assertEquals(1, officer.mcp)
        }
    }

    @Nested
    inner class GetExpStatsForPool {

        @Test
        fun `PCP pool returns PCP stats`() {
            val stats = cpService.getExpStatsForPool(StatCategory.PCP)

            assertEquals(4, stats.size)
            assertTrue(stats.contains(OfficerStat.LEADERSHIP))
            assertTrue(stats.contains(OfficerStat.POLITICS))
            assertTrue(stats.contains(OfficerStat.ADMINISTRATION))
            assertTrue(stats.contains(OfficerStat.INTELLIGENCE))
        }

        @Test
        fun `MCP pool returns MCP stats`() {
            val stats = cpService.getExpStatsForPool(StatCategory.MCP)

            assertEquals(4, stats.size)
            assertTrue(stats.contains(OfficerStat.COMMAND))
            assertTrue(stats.contains(OfficerStat.MOBILITY))
            assertTrue(stats.contains(OfficerStat.ATTACK))
            assertTrue(stats.contains(OfficerStat.DEFENSE))
        }
    }
}
