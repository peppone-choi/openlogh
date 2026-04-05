package com.openlogh.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OfficerStatTest {

    @Test
    fun `OfficerStat has exactly 8 entries`() {
        assertEquals(8, OfficerStat.entries.size)
    }

    @Test
    fun `PCP stats are leadership, politics, administration, intelligence`() {
        val pcpStats = OfficerStat.pcpStats()
        assertEquals(4, pcpStats.size)
        assertTrue(pcpStats.contains(OfficerStat.LEADERSHIP))
        assertTrue(pcpStats.contains(OfficerStat.POLITICS))
        assertTrue(pcpStats.contains(OfficerStat.ADMINISTRATION))
        assertTrue(pcpStats.contains(OfficerStat.INTELLIGENCE))
    }

    @Test
    fun `MCP stats are command, mobility, attack, defense`() {
        val mcpStats = OfficerStat.mcpStats()
        assertEquals(4, mcpStats.size)
        assertTrue(mcpStats.contains(OfficerStat.COMMAND))
        assertTrue(mcpStats.contains(OfficerStat.MOBILITY))
        assertTrue(mcpStats.contains(OfficerStat.ATTACK))
        assertTrue(mcpStats.contains(OfficerStat.DEFENSE))
    }

    @Test
    fun `each stat has correct category`() {
        assertEquals(StatCategory.PCP, OfficerStat.LEADERSHIP.category)
        assertEquals(StatCategory.PCP, OfficerStat.POLITICS.category)
        assertEquals(StatCategory.PCP, OfficerStat.ADMINISTRATION.category)
        assertEquals(StatCategory.PCP, OfficerStat.INTELLIGENCE.category)
        assertEquals(StatCategory.MCP, OfficerStat.COMMAND.category)
        assertEquals(StatCategory.MCP, OfficerStat.MOBILITY.category)
        assertEquals(StatCategory.MCP, OfficerStat.ATTACK.category)
        assertEquals(StatCategory.MCP, OfficerStat.DEFENSE.category)
    }

    @Test
    fun `each stat has Korean name`() {
        assertEquals("통솔", OfficerStat.LEADERSHIP.koreanName)
        assertEquals("정치", OfficerStat.POLITICS.koreanName)
        assertEquals("운영", OfficerStat.ADMINISTRATION.koreanName)
        assertEquals("정보", OfficerStat.INTELLIGENCE.koreanName)
        assertEquals("지휘", OfficerStat.COMMAND.koreanName)
        assertEquals("기동", OfficerStat.MOBILITY.koreanName)
        assertEquals("공격", OfficerStat.ATTACK.koreanName)
        assertEquals("방어", OfficerStat.DEFENSE.koreanName)
    }
}
