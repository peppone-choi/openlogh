package com.openlogh.engine

import com.openlogh.entity.Planet
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.FactionTurn
import com.openlogh.entity.OldOfficer
import com.openlogh.entity.Fleet
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerAccessLogRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.HallOfFameRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.OldOfficerRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.service.HistoryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.Optional

class GeneralMaintenanceServiceTest {

    private lateinit var service: OfficerMaintenanceService
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var factionTurnRepository: FactionTurnRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var oldOfficerRepository: OldOfficerRepository
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var officerAccessLogRepository: OfficerAccessLogRepository
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        val gameConstService = com.openlogh.service.GameConstService()
        // Initialize GameConstService manually (it uses @PostConstruct)
        try { gameConstService.init() } catch (_: Exception) { /* resource may not be on classpath */ }
        hallOfFameRepository = mock(HallOfFameRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        factionTurnRepository = mock(FactionTurnRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        oldOfficerRepository = mock(OldOfficerRepository::class.java)
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        officerAccessLogRepository = mock(OfficerAccessLogRepository::class.java)
        historyService = mock(HistoryService::class.java)

        `when`(fleetRepository.findById(anyLong())).thenReturn(Optional.empty())
        `when`(oldOfficerRepository.findByServerIdAndOfficerNo(anyString(), anyLong())).thenReturn(null)
        `when`(officerAccessLogRepository.findByOfficerId(anyLong())).thenReturn(emptyList())

        service = OfficerMaintenanceService(
            gameConstService,
            hallOfFameRepository,
            factionRepository,
            planetRepository,
            diplomacyRepository,
            factionTurnRepository,
            fleetRepository,
            oldOfficerRepository,
            officerTurnRepository,
            officerAccessLogRepository,
            historyService,
        )
    }

    private fun createWorld(
        year: Short = 200,
        month: Short = 1,
        config: MutableMap<String, Any> = mutableMapOf(),
    ): SessionState {
        return SessionState(
            id = 1,
            name = "test-world",
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = 300,
            config = config,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        age: Short = 30,
        deadYear: Short = 300,
        experience: Int = 1000,
        dedication: Int = 1000,
        blockState: Short = 0,
        killTurn: Short? = null,
        npcState: Short = 0,
        factionId: Long = 1,
        officerLevel: Short = 0,
        fleetId: Long = 0,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = 1,
            fleetId = fleetId,
            age = age,
            deadYear = deadYear,
            experience = experience,
            dedication = dedication,
            blockState = blockState,
            killTurn = killTurn,
            npcState = npcState,
            officerLevel = officerLevel,
            turnTime = OffsetDateTime.now(),
        )
    }

    @Test
    fun `age increments in January`() {
        val world = createWorld(month = 1)
        val general = createGeneral(age = 30)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(31.toShort(), general.age)
    }

    @Test
    fun `age does not increment in non-January month`() {
        val world = createWorld(month = 6)
        val general = createGeneral(age = 30)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(30.toShort(), general.age)
    }

    @Test
    fun `experience increases by 10 per turn`() {
        val world = createWorld(month = 3)
        val general = createGeneral(experience = 1000)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(1010, general.experience)
    }

    @Test
    fun `dedication decays by 1 percent`() {
        val world = createWorld(month = 3)
        val general = createGeneral(dedication = 1000)

        service.processGeneralMaintenance(world, listOf(general))

        // 1000 * 0.99 = 990
        assertEquals(990, general.dedication)
    }

    @Test
    fun `dedication does not decay below zero`() {
        val world = createWorld(month = 3)
        val general = createGeneral(dedication = 0)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(0, general.dedication)
    }

    @Test
    fun `retirement triggers when age reaches retirementYear`() {
        val world = createWorld(
            year = 260,
            month = 1,
            config = mutableMapOf("isUnited" to 1),
        )
        val general = createGeneral(age = 80, factionId = 7, officerLevel = 11).apply {
            leadership = 97
            command = 11
            intelligence = 10
            injury = 6
            experience = 1001
            dedication = 1001
            specAge = 35
            spec2Age = 37
            dex1 = 101
            dex2 = 81
            dex3 = 61
            dex4 = 41
            dex5 = 21
            meta["rank"] = mutableMapOf(
                "warnum" to 12,
                "killnum" to 3,
            )
        }

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(7L, general.factionId)
        assertEquals(11.toShort(), general.officerLevel)
        assertEquals(0.toShort(), general.npcState)
        assertEquals(20.toShort(), general.age)
        assertEquals(82.toShort(), general.leadership)
        assertEquals(10.toShort(), general.command)
        assertEquals(10.toShort(), general.intelligence)
        assertEquals(0.toShort(), general.injury)
        assertEquals(500, general.experience)
        assertEquals(500, general.dedication)
        assertEquals(0.toShort(), general.specAge)
        assertEquals(0.toShort(), general.spec2Age)
        assertEquals(50, general.dex1)
        assertEquals(40, general.dex2)
        assertEquals(30, general.dex3)
        assertEquals(20, general.dex4)
        assertEquals(10, general.dex5)
        val rank = general.meta["rank"] as Map<*, *>
        assertEquals(0, rank["warnum"])
        assertEquals(0, rank["killnum"])
        assertFalse(general.meta.containsKey("rebirth_available"))
    }

    @Test
    fun `no retirement before retirement age`() {
        val world = createWorld(year = 260, month = 1)
        val general = createGeneral(age = 79, factionId = 3, officerLevel = 9)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(3L, general.factionId)
        assertEquals(9.toShort(), general.officerLevel)
        assertEquals(80.toShort(), general.age)
    }

    @Test
    fun `injury recovers by 1 per turn`() {
        val world = createWorld(month = 3)
        val general = createGeneral(age = 30, deadYear = 300)
        general.injury = 5

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(4.toShort(), general.injury)
    }

    @Test
    fun `injury does not go below zero`() {
        val world = createWorld(month = 3)
        val general = createGeneral(age = 30, deadYear = 300)
        general.injury = 0

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(0.toShort(), general.injury)
    }

    @Test
    fun `multiple generals processed independently`() {
        val world = createWorld(month = 1)
        val g1 = createGeneral(age = 25, experience = 100, dedication = 500)
        val g2 = createGeneral(age = 50, experience = 200, dedication = 1000)

        service.processGeneralMaintenance(world, listOf(g1, g2))

        // g1
        assertEquals(26.toShort(), g1.age)
        assertEquals(110, g1.experience)
        assertEquals(495, g1.dedication)

        // g2
        assertEquals(51.toShort(), g2.age)
        assertEquals(210, g2.experience)
        assertEquals(990, g2.dedication)
    }

    @Test
    fun `killGeneral promotes successor disbands troop and stores old general`() {
        val world = createWorld(year = 200, month = 6)
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#112233",
            chiefOfficerId = 1,
            officerCount = 3,
            factionRank = 4,
        )
        val troop = Fleet(
            id = 10,
            sessionId = 1,
            leaderOfficerId = 1,
            factionId = 1,
            name = "중군",
        )
        val dead = createGeneral(
            id = 1,
            age = 40,
            killTurn = 0,
            factionId = 1,
            officerLevel = 20,
            fleetId = 10,
        )
        val successor = createGeneral(
            id = 2,
            age = 35,
            factionId = 1,
            officerLevel = 11,
            fleetId = 10,
        ).apply {
            dedication = 700
        }
        val member = createGeneral(
            id = 3,
            age = 33,
            factionId = 1,
            officerLevel = 5,
            fleetId = 10,
        )

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(fleetRepository.findById(10L)).thenReturn(Optional.of(troop))

        service.processGeneralMaintenance(world, listOf(dead, successor, member))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.factionId)
        assertEquals(0.toShort(), dead.officerLevel)
        assertEquals(0, dead.officerPlanet)
        assertNull(dead.killTurn)
        assertEquals(0L, dead.fleetId)

        assertEquals(20.toShort(), successor.officerLevel)
        assertEquals(0, successor.officerPlanet)
        assertEquals(0L, successor.fleetId)
        assertEquals(0L, member.fleetId)

        assertEquals(2L, nation.chiefOfficerId)
        assertEquals(2, nation.officerCount)
        verify(fleetRepository).delete(troop)
        verify(officerTurnRepository).deleteByOfficerId(1L)

        val captor = ArgumentCaptor.forClass(OldOfficer::class.java)
        verify(oldOfficerRepository).save(captor.capture())
        assertEquals("test-world", captor.value.serverId)
        assertEquals(1L, captor.value.generalNo)
        assertEquals(20006, captor.value.lastYearMonth)
    }

    // ===== expLevel / dedLevel parity tests (legacy: addExperience/addDedication update level) =====

    @Test
    fun `expLevel updates when experience crosses level boundary`() {
        val world = createWorld(month = 3)
        // 990/100 = 9; after +10: sqrt(1000/10).toInt() = sqrt(100).toInt() = 10
        val general = createGeneral(experience = 990)
        general.expLevel = 9

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(1000, general.experience)
        assertEquals(10.toShort(), general.expLevel)
    }

    @Test
    fun `expLevel unchanged when experience stays in same level range`() {
        val world = createWorld(month = 3)
        // 980/100 = 9; 990/100 = 9 — same level
        val general = createGeneral(experience = 980)
        general.expLevel = 9

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(990, general.experience)
        assertEquals(9.toShort(), general.expLevel)
    }

    @Test
    fun `dedLevel updates when dedication decay crosses level boundary`() {
        val world = createWorld(month = 3)
        // 8182 * 0.99 = 8100; ceil(sqrt(8182)/10)=10, ceil(sqrt(8100)/10)=9
        val general = createGeneral(dedication = 8182)
        general.dedLevel = 10

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(8100, general.dedication)
        assertEquals(9.toShort(), general.dedLevel)
    }

    @Test
    fun `dedLevel unchanged when dedication decay stays in same level range`() {
        val world = createWorld(month = 3)
        // 1000 * 0.99 = 990; ceil(sqrt(1000)/10)=ceil(3.16)=4, ceil(sqrt(990)/10)=ceil(3.14)=4
        val general = createGeneral(dedication = 1000)
        general.dedLevel = 4

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(990, general.dedication)
        assertEquals(4.toShort(), general.dedLevel)
    }

    @Test
    fun `killGeneral collapses nation when no successor exists`() {
        val world = createWorld(year = 200, month = 6)
        val nation = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#112233",
            chiefOfficerId = 1,
            capitalPlanetId = 99,
            officerCount = 1,
            militaryPower = 400,
            factionRank = 5,
        )
        val city = Planet(
            id = 99,
            sessionId = 1,
            name = "낙양",
            level = 5,
            factionId = 1,
        )
        val diplomacy = Diplomacy(
            id = 7,
            sessionId = 1,
            srcFactionId = 1,
            destFactionId = 2,
            stateCode = "전쟁",
        )
        val nationTurn = FactionTurn(
            id = 11,
            sessionId = 1,
            factionId = 1,
            officerLevel = 20,
            turnIdx = 0,
            actionCode = "Nation휴식",
        )
        val dead = createGeneral(
            id = 1,
            age = 40,
            killTurn = 0,
            factionId = 1,
            officerLevel = 20,
        )

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(planetRepository.findByFactionId(1L)).thenReturn(listOf(city))
        `when`(fleetRepository.findByFactionId(1L)).thenReturn(emptyList())
        `when`(diplomacyRepository.findBySessionId(1L)).thenReturn(listOf(diplomacy))
        `when`(factionTurnRepository.findBySessionId(1L)).thenReturn(listOf(nationTurn))

        service.processGeneralMaintenance(world, listOf(dead))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.factionId)
        assertEquals(0.toShort(), nation.factionRank)
        assertEquals(0, nation.officerCount)
        assertEquals(0, nation.militaryPower)
        assertEquals(0L, nation.chiefOfficerId)
        assertNull(nation.capitalPlanetId)
        assertEquals(0L, city.factionId)
        assertEquals(0.toShort(), city.frontState)
        assertTrue(diplomacy.isDead)
        assertFalse(diplomacy.isShowing)
        verify(factionTurnRepository).delete(nationTurn)
        verify(historyService).logWorldHistory(eq(1L), contains("멸망"), eq(200), eq(6), eq(false))
        verify(historyService).logNationHistory(eq(1L), eq(1L), contains("사망"), eq(200), eq(6))
    }
}
