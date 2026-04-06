package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class FleetSortieCostServiceTest {

    private lateinit var fleetRepository: FleetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var service: FleetSortieCostService

    @BeforeEach
    fun setUp() {
        fleetRepository = mock(FleetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        service = FleetSortieCostService(fleetRepository, officerRepository, factionRepository)
    }

    private fun makeFleet(
        id: Long,
        sessionId: Long,
        factionId: Long,
        currentUnits: Int,
        commanderId: Long = 1L,
        isSortie: Boolean = false,
    ): Fleet {
        val f = Fleet()
        f.id = id
        f.sessionId = sessionId
        f.factionId = factionId
        f.currentUnits = currentUnits
        f.leaderOfficerId = commanderId
        if (isSortie) {
            f.meta["isSortie"] = true as Any
        }
        return f
    }

    private fun makeFaction(id: Long, sessionId: Long, funds: Int = 100000): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.funds = funds
        f.factionType = "empire"
        return f
    }

    private fun makeOfficer(id: Long, administration: Short = 50): Officer {
        val o = Officer()
        o.id = id
        o.administration = administration
        return o
    }

    /**
     * Test 1: 출격 중 함대 3개(각 10유닛), 기본 비용 = 3 * 10 * BASE_COST_PER_UNIT → faction.funds 차감
     * administration=50 → no discount
     */
    @Test
    fun `processSortieCost - 출격함대 3개 기본비용 차감`() {
        val sessionId = 1L
        val factionId = 10L
        val initialFunds = 100000
        val faction = makeFaction(factionId, sessionId, initialFunds)

        // 3 fleets, 10 units each, isSortie=true
        val fleets = (1L..3L).map { makeFleet(it, sessionId, factionId, 10, commanderId = 100L, isSortie = true) }
        val commander = makeOfficer(100L, administration = 50)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(fleets)
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(officerRepository.findById(100L)).thenReturn(Optional.of(commander))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.processSortieCost(sessionId)

        // baseCost = 3 * 10 * 10 = 300; admin=50 → discount=0 → finalCost=300
        val expectedFunds = initialFunds - 300
        assertEquals(expectedFunds, faction.funds)
    }

    /**
     * Test 2: commander.administration=80이면 비용 20% 절감
     * discount = (80-50)/100.0 * 0.5 = 0.15 → finalCost = baseCost * 0.85
     */
    @Test
    fun `processSortieCost - administration 80이면 비용 절감`() {
        val sessionId = 1L
        val factionId = 10L
        val initialFunds = 100000
        val faction = makeFaction(factionId, sessionId, initialFunds)

        val fleet = makeFleet(1L, sessionId, factionId, 10, commanderId = 100L, isSortie = true)
        val commander = makeOfficer(100L, administration = 80)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(listOf(fleet))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(officerRepository.findById(100L)).thenReturn(Optional.of(commander))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.processSortieCost(sessionId)

        // baseCost = 10 * 10 = 100; discount = (80-50)/100.0 * 0.5 = 0.15; finalCost = 85
        val expectedCost = 85
        val expectedFunds = initialFunds - expectedCost
        assertEquals(expectedFunds, faction.funds)
    }

    /**
     * Test 3: commander.administration=50이면 비용 그대로 (기본값)
     */
    @Test
    fun `processSortieCost - administration 50이면 기본 비용`() {
        val sessionId = 1L
        val factionId = 10L
        val initialFunds = 100000
        val faction = makeFaction(factionId, sessionId, initialFunds)

        val fleet = makeFleet(1L, sessionId, factionId, 10, commanderId = 100L, isSortie = true)
        val commander = makeOfficer(100L, administration = 50)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(listOf(fleet))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(officerRepository.findById(100L)).thenReturn(Optional.of(commander))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.processSortieCost(sessionId)

        // baseCost = 10 * 10 = 100; discount = 0; finalCost = 100
        val expectedFunds = initialFunds - 100
        assertEquals(expectedFunds, faction.funds)
    }

    /**
     * Test 4: faction.funds < 비용이면 funds를 0으로 설정 (마이너스 없음)
     */
    @Test
    fun `processSortieCost - funds 부족 시 0으로 클램핑`() {
        val sessionId = 1L
        val factionId = 10L
        val initialFunds = 50  // less than cost
        val faction = makeFaction(factionId, sessionId, initialFunds)

        val fleet = makeFleet(1L, sessionId, factionId, 10, commanderId = 100L, isSortie = true)
        val commander = makeOfficer(100L, administration = 50)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(listOf(fleet))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(officerRepository.findById(100L)).thenReturn(Optional.of(commander))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.processSortieCost(sessionId)

        // baseCost = 100, funds = 50 → funds clamped to 0
        assertEquals(0, faction.funds)
    }

    /**
     * Test 5: 출격 중 함대 없으면(isSortie=false, currentUnits=0) 비용 0
     */
    @Test
    fun `processSortieCost - 출격함대 없으면 비용 없음`() {
        val sessionId = 1L
        val factionId = 10L
        val initialFunds = 100000
        val faction = makeFaction(factionId, sessionId, initialFunds)

        // fleet with currentUnits=0 and no isSortie flag
        val fleet = makeFleet(1L, sessionId, factionId, 0, commanderId = 100L, isSortie = false)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(listOf(fleet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        service.processSortieCost(sessionId)

        // No sortie fleets → no cost
        assertEquals(initialFunds, faction.funds)
        verify(factionRepository, never()).findById(anyLong())
    }
}
