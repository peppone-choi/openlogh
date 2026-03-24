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

class OfficerMaintenanceServiceTest {

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

    private fun createOfficer(
        id: Long = 1,
        age: Short = 30,
        deathYear: Short = 300,
        experience: Int = 1000,
        dedication: Int = 1000,
        blockState: Short = 0,
        killTurn: Short? = null,
        npcState: Short = 0,
        factionId: Long = 1,
        rank: Short = 0,
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
            deathYear = deathYear,
            experience = experience,
            dedication = dedication,
            blockState = blockState,
            killTurn = killTurn,
            npcState = npcState,
            rank = rank,
            turnTime = OffsetDateTime.now(),
        )
    }

    @Test
    fun `age increments in January`() {
        val world = createWorld(month = 1)
        val officer = createOfficer(age = 30)

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(31.toShort(), officer.age)
    }

    @Test
    fun `age does not increment in non-January month`() {
        val world = createWorld(month = 6)
        val officer = createOfficer(age = 30)

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(30.toShort(), officer.age)
    }

    @Test
    fun `experience increases by 10 per turn`() {
        val world = createWorld(month = 3)
        val officer = createOfficer(experience = 1000)

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(1010, officer.experience)
    }

    @Test
    fun `dedication decays by 1 percent`() {
        val world = createWorld(month = 3)
        val officer = createOfficer(dedication = 1000)

        service.processGeneralMaintenance(world, listOf(officer))

        // 1000 * 0.99 = 990
        assertEquals(990, officer.dedication)
    }

    @Test
    fun `dedication does not decay below zero`() {
        val world = createWorld(month = 3)
        val officer = createOfficer(dedication = 0)

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(0, officer.dedication)
    }

    @Test
    fun `retirement triggers when age reaches retirementYear`() {
        val world = createWorld(
            year = 260,
            month = 1,
            config = mutableMapOf("isUnited" to 1),
        )
        val officer = createOfficer(age = 80, factionId = 7, rank = 11).apply {
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

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(7L, officer.factionId)
        assertEquals(11.toShort(), officer.rank)
        assertEquals(0.toShort(), officer.npcState)
        assertEquals(20.toShort(), officer.age)
        assertEquals(82.toShort(), officer.leadership)
        assertEquals(10.toShort(), officer.command)
        assertEquals(10.toShort(), officer.intelligence)
        assertEquals(0.toShort(), officer.injury)
        assertEquals(500, officer.experience)
        assertEquals(500, officer.dedication)
        assertEquals(0.toShort(), officer.specAge)
        assertEquals(0.toShort(), officer.spec2Age)
        assertEquals(50, officer.dex1)
        assertEquals(40, officer.dex2)
        assertEquals(30, officer.dex3)
        assertEquals(20, officer.dex4)
        assertEquals(10, officer.dex5)
        val rank = officer.meta["rank"] as Map<*, *>
        assertEquals(0, rank["warnum"])
        assertEquals(0, rank["killnum"])
        assertFalse(officer.meta.containsKey("rebirth_available"))
    }

    @Test
    fun `no retirement before retirement age`() {
        val world = createWorld(year = 260, month = 1)
        val officer = createOfficer(age = 79, factionId = 3, rank = 9)

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(3L, officer.factionId)
        assertEquals(9.toShort(), officer.rank)
        assertEquals(80.toShort(), officer.age)
    }

    @Test
    fun `injury recovers by 1 per turn`() {
        val world = createWorld(month = 3)
        val officer = createOfficer(age = 30, deathYear = 300)
        officer.injury = 5

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(4.toShort(), officer.injury)
    }

    @Test
    fun `injury does not go below zero`() {
        val world = createWorld(month = 3)
        val officer = createOfficer(age = 30, deathYear = 300)
        officer.injury = 0

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(0.toShort(), officer.injury)
    }

    @Test
    fun `multiple generals processed independently`() {
        val world = createWorld(month = 1)
        val off1 = createOfficer(age = 25, experience = 100, dedication = 500)
        val off2 = createOfficer(age = 50, experience = 200, dedication = 1000)

        service.processGeneralMaintenance(world, listOf(off1, off2))

        // off1
        assertEquals(26.toShort(), off1.age)
        assertEquals(110, off1.experience)
        assertEquals(495, off1.dedication)

        // off2
        assertEquals(51.toShort(), off2.age)
        assertEquals(210, off2.experience)
        assertEquals(990, off2.dedication)
    }

    @Test
    fun `killGeneral promotes successor disbands troop and stores old general`() {
        val world = createWorld(year = 200, month = 6)
        val faction = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#112233",
            supremeCommanderId = 1,
            officerCount = 3,
            factionRank = 4,
        )
        val fleet = Fleet(
            id = 10,
            sessionId = 1,
            leaderOfficerId = 1,
            factionId = 1,
            name = "중군",
        )
        val dead = createOfficer(
            id = 1,
            age = 40,
            killTurn = 0,
            factionId = 1,
            rank = 20,
            fleetId = 10,
        )
        val successor = createOfficer(
            id = 2,
            age = 35,
            factionId = 1,
            rank = 11,
            fleetId = 10,
        ).apply {
            dedication = 700
        }
        val member = createOfficer(
            id = 3,
            age = 33,
            factionId = 1,
            rank = 5,
            fleetId = 10,
        )

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(faction))
        `when`(fleetRepository.findById(10L)).thenReturn(Optional.of(fleet))

        service.processGeneralMaintenance(world, listOf(dead, successor, member))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.factionId)
        assertEquals(0.toShort(), dead.rank)
        assertEquals(0, dead.stationedSystem)
        assertNull(dead.killTurn)
        assertEquals(0L, dead.fleetId)

        assertEquals(20.toShort(), successor.rank)
        assertEquals(0, successor.stationedSystem)
        assertEquals(0L, successor.fleetId)
        assertEquals(0L, member.fleetId)

        assertEquals(2L, faction.supremeCommanderId)
        assertEquals(2, faction.officerCount)
        verify(fleetRepository).delete(fleet)
        verify(officerTurnRepository).deleteByOfficerId(1L)

        val captor = ArgumentCaptor.forClass(OldOfficer::class.java)
        verify(oldOfficerRepository).save(captor.capture())
        assertEquals("test-world", captor.value.serverId)
        assertEquals(1L, captor.value.officerNo)
        assertEquals(20006, captor.value.lastYearMonth)
    }

    // ===== expLevel / dedLevel parity tests (legacy: addExperience/addDedication update level) =====

    @Test
    fun `expLevel updates when experience crosses level boundary`() {
        val world = createWorld(month = 3)
        // 990/100 = 9; after +10: sqrt(1000/10).toInt() = sqrt(100).toInt() = 10
        val officer = createOfficer(experience = 990)
        officer.expLevel = 9

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(1000, officer.experience)
        assertEquals(10.toShort(), officer.expLevel)
    }

    @Test
    fun `expLevel unchanged when experience stays in same level range`() {
        val world = createWorld(month = 3)
        // 980/100 = 9; 990/100 = 9 — same level
        val officer = createOfficer(experience = 980)
        officer.expLevel = 9

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(990, officer.experience)
        assertEquals(9.toShort(), officer.expLevel)
    }

    @Test
    fun `dedLevel updates when dedication decay crosses level boundary`() {
        val world = createWorld(month = 3)
        // 8182 * 0.99 = 8100; ceil(sqrt(8182)/10)=10, ceil(sqrt(8100)/10)=9
        val officer = createOfficer(dedication = 8182)
        officer.dedLevel = 10

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(8100, officer.dedication)
        assertEquals(9.toShort(), officer.dedLevel)
    }

    @Test
    fun `dedLevel unchanged when dedication decay stays in same level range`() {
        val world = createWorld(month = 3)
        // 1000 * 0.99 = 990; ceil(sqrt(1000)/10)=ceil(3.16)=4, ceil(sqrt(990)/10)=ceil(3.14)=4
        val officer = createOfficer(dedication = 1000)
        officer.dedLevel = 4

        service.processGeneralMaintenance(world, listOf(officer))

        assertEquals(990, officer.dedication)
        assertEquals(4.toShort(), officer.dedLevel)
    }

    @Test
    fun `killGeneral collapses nation when no successor exists`() {
        val world = createWorld(year = 200, month = 6)
        val faction = Faction(
            id = 1,
            sessionId = 1,
            name = "위",
            color = "#112233",
            supremeCommanderId = 1,
            capitalPlanetId = 99,
            officerCount = 1,
            militaryPower = 400,
            factionRank = 5,
        )
        val planet = Planet(
            id = 99,
            sessionId = 1,
            name = "낙양",
            level = 5,
            factionId = 1,
        )
        val diplomacy = Diplomacy(
            id = 7,
            sessionId = 1,
            srcNationId = 1,
            destFactionId = 2,
            stateCode = "전쟁",
        )
        val factionTurn = FactionTurn(
            id = 11,
            sessionId = 1,
            factionId = 1,
            officerLevel = 20,
            turnIdx = 0,
            actionCode = "Nation휴식",
        )
        val dead = createOfficer(
            id = 1,
            age = 40,
            killTurn = 0,
            factionId = 1,
            rank = 20,
        )

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(faction))
        `when`(planetRepository.findByFactionId(1L)).thenReturn(listOf(planet))
        `when`(fleetRepository.findByFactionId(1L)).thenReturn(emptyList())
        `when`(diplomacyRepository.findBySessionId(1L)).thenReturn(listOf(diplomacy))
        `when`(factionTurnRepository.findBySessionId(1L)).thenReturn(listOf(factionTurn))

        service.processGeneralMaintenance(world, listOf(dead))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.factionId)
        assertEquals(0.toShort(), faction.factionRank)
        assertEquals(0, faction.officerCount)
        assertEquals(0, faction.militaryPower)
        assertEquals(0L, faction.supremeCommanderId)
        assertNull(faction.capitalPlanetId)
        assertEquals(0L, planet.factionId)
        assertEquals(0.toShort(), planet.frontState)
        assertTrue(diplomacy.isDead)
        assertFalse(diplomacy.isShowing)
        verify(factionTurnRepository).delete(factionTurn)
        verify(historyService).logWorldHistory(eq(1L), contains("멸망"), eq(200), eq(6))
        verify(historyService).logNationHistory(eq(1L), eq(1L), contains("사망"), eq(200), eq(6))
    }
}
