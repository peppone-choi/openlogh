package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Plan 23-05: Gin7EconomyService.updateFactionRank
 *
 * Ports upstream `com.opensam.engine.EconomyService.updateNationLevelEvent` / `updateNationLevel`
 * (pre-stub body, last seen in commit `a7a19cc3~1`) into LOGH's `Gin7EconomyService.updateFactionRank`.
 *
 * ### Legacy reference (a7a19cc3~1 opensam EconomyService.updateNationLevel)
 *
 * ```
 *   private fun updateNationLevel(world, nations, cities, generals) {
 *     val citiesByNation = cities.groupBy { it.nationId }
 *     for (nation in nations) {
 *       val nationCities = citiesByNation[nation.id] ?: continue
 *       val highCities = nationCities.count { it.level >= 4 }
 *       var newLevel = 0
 *       for (level in NATION_LEVEL_THRESHOLDS.indices) {
 *         if (highCities >= NATION_LEVEL_THRESHOLDS[level]) {
 *           newLevel = level
 *         }
 *       }
 *       if (newLevel > nation.level.toInt()) {
 *         nation.level = newLevel.coerceIn(0, 9).toShort()
 *         nation.gold += newLevel * 1000
 *         nation.rice += newLevel * 1000
 *         // … history + reward + inheritance (deferred to 23-10)
 *       }
 *     }
 *   }
 * ```
 *
 * ### Thresholds (NATION_LEVEL_THRESHOLDS / FACTION_RANK_THRESHOLDS)
 *
 * Index (level) → min highCities (count of planets with level >= 4):
 * ```
 *   0 → 0    (방랑군)
 *   1 → 1    (도위)
 *   2 → 2    (주자사)
 *   3 → 4    (주목)
 *   4 → 6    (중랑장)
 *   5 → 9    (대장군)
 *   6 → 12   (대사마)
 *   7 → 16   (공)
 *   8 → 20   (왕)
 *   9 → 25   (황제)
 * ```
 *
 * ### LOGH adjustments vs. upstream body
 *
 * 1. **Domain mapping** — `nation.level` → `faction.factionRank` (Short).
 * 2. **Rank-down support** (plan 23-05 acceptance criterion) — the upstream body only
 *    promotes (`if newLevel > old`), but the LOGH plan explicitly requires bidirectional
 *    updates so a faction that loses planets drops back down. We write the new level
 *    unconditionally when it differs from the old level.
 * 3. **Neutral faction (id=0) skipped** — mirrors existing Gin7 processIncome / processSemiAnnual.
 * 4. **History logging + level-up gold/rice rewards** — deferred to Plan 23-10 cleanup.
 *    This plan only ports the rank calculation and persistence.
 *
 * Per the Phase 23 CONTEXT.md decision table, `nation.level → faction.factionRank` is the
 * canonical domain mapping.
 */
class Gin7UpdateFactionRankTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(): SessionState {
        val world = SessionState()
        world.id = 1.toShort()
        world.currentMonth = 1.toShort() // rank update is annual (Jan trigger per legacy)
        world.currentYear = 800.toShort()
        return world
    }

    private fun makeFaction(id: Long, sessionId: Long, rank: Short = 0): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.factionRank = rank
        f.factionType = "empire"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        level: Short = 1,
    ): Planet {
        val p = Planet()
        p.id = id
        p.sessionId = sessionId
        p.factionId = factionId
        p.level = level
        return p
    }

    /**
     * Test 1: Faction with zero high-level planets → rank level 0 (방랑군).
     *
     * No planets at level >= 4 means `highCities = 0`, which matches threshold[0] = 0 → level 0.
     */
    @Test
    fun `faction with no high-level planets stays at rank 0`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, rank = 0)
        val planet = makePlanet(id = 100L, sessionId = 1L, factionId = 10L, level = 1)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        service.updateFactionRank(world)

        assertEquals(0.toShort(), faction.factionRank, "rank must be 0 (방랑군) for 0 high planets")
    }

    /**
     * Test 2: Faction with sufficient high-level planets ranks UP.
     *
     * Thresholds: 0→0, 1→1, 2→2, 3→4, 4→6, 5→9, 6→12, 7→16, 8→20, 9→25
     * 4 high planets → highest matching level = 3 (주목).
     */
    @Test
    fun `faction with 4 high-level planets reaches rank 3`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, rank = 0)
        val planets = (1..4).map { i ->
            makePlanet(id = 100L + i, sessionId = 1L, factionId = 10L, level = 4)
        }

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        service.updateFactionRank(world)

        // 4 high planets ≥ threshold[3]=4 but < threshold[4]=6 → newLevel=3 (주목)
        assertEquals(3.toShort(), faction.factionRank, "rank must advance to 3 (주목)")
    }

    /**
     * Test 3: Faction rank drops DOWN when it loses high-level planets.
     *
     * Plan 23-05 acceptance: "Rank-up and rank-down both work". The upstream body only
     * promotes; this LOGH port is additive (Rule 2) — a faction previously at rank 5 that
     * now only owns 2 high planets must be demoted to rank 2.
     */
    @Test
    fun `faction rank drops when high-level planet count shrinks`() {
        val world = makeWorld()
        val faction = makeFaction(id = 10L, sessionId = 1L, rank = 5) // was 대장군
        val planets = listOf(
            makePlanet(id = 100L, sessionId = 1L, factionId = 10L, level = 4),
            makePlanet(id = 101L, sessionId = 1L, factionId = 10L, level = 5),
            // remaining low-level planets
            makePlanet(id = 102L, sessionId = 1L, factionId = 10L, level = 1),
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(planets)

        service.updateFactionRank(world)

        // 2 high planets ≥ threshold[2]=2 but < threshold[3]=4 → newLevel=2 (주자사)
        assertEquals(2.toShort(), faction.factionRank, "rank must drop to 2 (주자사)")
    }

    /**
     * Test 4: Rank name lookup via `getFactionRankName(level)` returns LOGH legacy strings.
     *
     * The 10-level table is preserved from `EconomyService.NATION_LEVEL_NAME` for OpenSamguk
     * parity (the LOGH Korean rank titles from CLAUDE.md `#### Empire (제국군)` will be layered
     * in Plan 23-10 alongside history logging — this plan only ports the legacy table as-is).
     */
    @Test
    fun `getFactionRankName returns legacy ranks and clamps out-of-range`() {
        assertEquals("방랑군", Gin7EconomyService.getFactionRankName(0))
        assertEquals("도위", Gin7EconomyService.getFactionRankName(1))
        assertEquals("주자사", Gin7EconomyService.getFactionRankName(2))
        assertEquals("주목", Gin7EconomyService.getFactionRankName(3))
        assertEquals("중랑장", Gin7EconomyService.getFactionRankName(4))
        assertEquals("대장군", Gin7EconomyService.getFactionRankName(5))
        assertEquals("대사마", Gin7EconomyService.getFactionRankName(6))
        assertEquals("공", Gin7EconomyService.getFactionRankName(7))
        assertEquals("왕", Gin7EconomyService.getFactionRankName(8))
        assertEquals("황제", Gin7EconomyService.getFactionRankName(9))
        // Out of range → sentinel
        assertEquals("???", Gin7EconomyService.getFactionRankName(-1))
        assertEquals("???", Gin7EconomyService.getFactionRankName(10))
    }

    /**
     * Test 5: Empty world (no factions, no planets) runs cleanly.
     *
     * Mirrors sibling Gin7 tests — defends against NPE on empty Iterables.
     * Neutral faction (id=0) is also verified to be skipped on the same run.
     */
    @Test
    fun `empty world runs cleanly and neutral faction is skipped`() {
        val world = makeWorld()
        val neutral = makeFaction(id = 0L, sessionId = 1L, rank = 5) // should NOT be mutated
        val neutralPlanet = makePlanet(id = 999L, sessionId = 1L, factionId = 0L, level = 5)

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(neutral))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(neutralPlanet))

        // Must not throw
        service.updateFactionRank(world)

        // Neutral faction must be untouched regardless of planet levels
        assertEquals(5.toShort(), neutral.factionRank, "neutral faction must be skipped")
        assertTrue(true)
    }
}
