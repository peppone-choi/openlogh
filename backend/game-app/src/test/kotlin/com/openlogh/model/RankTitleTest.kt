package com.openlogh.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RankTitleTest {

    @Test
    fun `empire tier 0 returns Sub-Lieutenant`() {
        val rank = RankTitleResolver.resolve(0, "empire")
        assertEquals(0, rank.tier)
        assertEquals("Sub-Lieutenant", rank.title)
        assertEquals("소위", rank.korean)
    }

    @Test
    fun `empire tier 10 returns Reichsmarschall`() {
        val rank = RankTitleResolver.resolve(10, "empire")
        assertEquals(10, rank.tier)
        assertEquals("Reichsmarschall", rank.title)
        assertEquals("원수", rank.korean)
    }

    @Test
    fun `empire tier 9 returns Fleet Admiral`() {
        val rank = RankTitleResolver.resolve(9, "empire")
        assertEquals("Fleet Admiral", rank.title)
        assertEquals("상급대장", rank.korean)
    }

    @Test
    fun `alliance tier 0 returns Sub-Lieutenant`() {
        val rank = RankTitleResolver.resolve(0, "alliance")
        assertEquals(0, rank.tier)
        assertEquals("Sub-Lieutenant", rank.title)
        assertEquals("소위", rank.korean)
    }

    @Test
    fun `alliance tier 10 returns Fleet Admiral`() {
        val rank = RankTitleResolver.resolve(10, "alliance")
        assertEquals(10, rank.tier)
        assertEquals("Fleet Admiral", rank.title)
        assertEquals("원수", rank.korean)
    }

    @Test
    fun `alliance tier 9 returns Admiral of the Fleet`() {
        val rank = RankTitleResolver.resolve(9, "alliance")
        assertEquals("Admiral of the Fleet", rank.title)
        assertEquals("상급대장", rank.korean)
    }

    @Test
    fun `all 11 empire tiers resolve correctly`() {
        val expectedTitles = listOf(
            "Sub-Lieutenant", "Lieutenant", "Lieutenant Commander", "Commander",
            "Captain", "Commodore", "Rear Admiral", "Vice Admiral",
            "Admiral", "Fleet Admiral", "Reichsmarschall"
        )
        for (tier in 0..10) {
            val rank = RankTitleResolver.resolve(tier, "empire")
            assertEquals(tier, rank.tier)
            assertEquals(expectedTitles[tier], rank.title)
        }
    }

    @Test
    fun `all 11 alliance tiers resolve correctly`() {
        val expectedTitles = listOf(
            "Sub-Lieutenant", "Lieutenant", "Lieutenant Commander", "Commander",
            "Captain", "Commodore", "Rear Admiral", "Vice Admiral",
            "Admiral", "Admiral of the Fleet", "Fleet Admiral"
        )
        for (tier in 0..10) {
            val rank = RankTitleResolver.resolve(tier, "alliance")
            assertEquals(tier, rank.tier)
            assertEquals(expectedTitles[tier], rank.title)
        }
    }

    @Test
    fun `tier 11 throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            RankTitleResolver.resolve(11, "empire")
        }
    }

    @Test
    fun `tier -1 throws IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            RankTitleResolver.resolve(-1, "empire")
        }
    }

    @Test
    fun `unknown faction falls back to empire titles`() {
        val empireRank = RankTitleResolver.resolve(10, "empire")
        val unknownRank = RankTitleResolver.resolve(10, "fezzan")
        assertEquals(empireRank.title, unknownRank.title)
        assertEquals(empireRank.korean, unknownRank.korean)
    }
}
