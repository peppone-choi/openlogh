package com.opensam.engine

import com.opensam.entity.City
import com.opensam.entity.Diplomacy
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.NationTurn
import com.opensam.entity.OldGeneral
import com.opensam.entity.Troop
import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.DiplomacyRepository
import com.opensam.repository.GeneralAccessLogRepository
import com.opensam.repository.GeneralTurnRepository
import com.opensam.repository.HallOfFameRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.NationTurnRepository
import com.opensam.repository.OldGeneralRepository
import com.opensam.repository.TroopRepository
import com.opensam.service.HistoryService
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

    private lateinit var service: GeneralMaintenanceService
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var nationTurnRepository: NationTurnRepository
    private lateinit var troopRepository: TroopRepository
    private lateinit var oldGeneralRepository: OldGeneralRepository
    private lateinit var generalTurnRepository: GeneralTurnRepository
    private lateinit var generalAccessLogRepository: GeneralAccessLogRepository
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        val gameConstService = com.opensam.service.GameConstService()
        // Initialize GameConstService manually (it uses @PostConstruct)
        try { gameConstService.init() } catch (_: Exception) { /* resource may not be on classpath */ }
        hallOfFameRepository = mock(HallOfFameRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        nationTurnRepository = mock(NationTurnRepository::class.java)
        troopRepository = mock(TroopRepository::class.java)
        oldGeneralRepository = mock(OldGeneralRepository::class.java)
        generalTurnRepository = mock(GeneralTurnRepository::class.java)
        generalAccessLogRepository = mock(GeneralAccessLogRepository::class.java)
        historyService = mock(HistoryService::class.java)

        `when`(troopRepository.findById(anyLong())).thenReturn(Optional.empty())
        `when`(oldGeneralRepository.findByServerIdAndGeneralNo(anyString(), anyLong())).thenReturn(null)
        `when`(generalAccessLogRepository.findByGeneralId(anyLong())).thenReturn(emptyList())

        service = GeneralMaintenanceService(
            gameConstService,
            hallOfFameRepository,
            nationRepository,
            cityRepository,
            diplomacyRepository,
            nationTurnRepository,
            troopRepository,
            oldGeneralRepository,
            generalTurnRepository,
            generalAccessLogRepository,
            historyService,
        )
    }

    private fun createWorld(
        year: Short = 200,
        month: Short = 1,
        config: MutableMap<String, Any> = mutableMapOf(),
    ): WorldState {
        return WorldState(
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
        nationId: Long = 1,
        officerLevel: Short = 0,
        troopId: Long = 0,
    ): General {
        return General(
            id = id,
            worldId = 1,
            name = "장수$id",
            nationId = nationId,
            cityId = 1,
            troopId = troopId,
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
        val general = createGeneral(age = 80, nationId = 7, officerLevel = 11).apply {
            leadership = 97
            strength = 11
            intel = 10
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

        assertEquals(7L, general.nationId)
        assertEquals(11.toShort(), general.officerLevel)
        assertEquals(0.toShort(), general.npcState)
        assertEquals(20.toShort(), general.age)
        assertEquals(82.toShort(), general.leadership)
        assertEquals(10.toShort(), general.strength)
        assertEquals(10.toShort(), general.intel)
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
        val general = createGeneral(age = 79, nationId = 3, officerLevel = 9)

        service.processGeneralMaintenance(world, listOf(general))

        assertEquals(3L, general.nationId)
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
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "위",
            color = "#112233",
            chiefGeneralId = 1,
            gennum = 3,
            level = 4,
        )
        val troop = Troop(
            id = 10,
            worldId = 1,
            leaderGeneralId = 1,
            nationId = 1,
            name = "중군",
        )
        val dead = createGeneral(
            id = 1,
            age = 40,
            killTurn = 0,
            nationId = 1,
            officerLevel = 12,
            troopId = 10,
        )
        val successor = createGeneral(
            id = 2,
            age = 35,
            nationId = 1,
            officerLevel = 11,
            troopId = 10,
        ).apply {
            dedication = 700
        }
        val member = createGeneral(
            id = 3,
            age = 33,
            nationId = 1,
            officerLevel = 5,
            troopId = 10,
        )

        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(troopRepository.findById(10L)).thenReturn(Optional.of(troop))

        service.processGeneralMaintenance(world, listOf(dead, successor, member))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.nationId)
        assertEquals(0.toShort(), dead.officerLevel)
        assertEquals(0, dead.officerCity)
        assertNull(dead.killTurn)
        assertEquals(0L, dead.troopId)

        assertEquals(12.toShort(), successor.officerLevel)
        assertEquals(0, successor.officerCity)
        assertEquals(0L, successor.troopId)
        assertEquals(0L, member.troopId)

        assertEquals(2L, nation.chiefGeneralId)
        assertEquals(2, nation.gennum)
        verify(troopRepository).delete(troop)
        verify(generalTurnRepository).deleteByGeneralId(1L)

        val captor = ArgumentCaptor.forClass(OldGeneral::class.java)
        verify(oldGeneralRepository).save(captor.capture())
        assertEquals("test-world", captor.value.serverId)
        assertEquals(1L, captor.value.generalNo)
        assertEquals(20006, captor.value.lastYearMonth)
    }

    @Test
    fun `killGeneral collapses nation when no successor exists`() {
        val world = createWorld(year = 200, month = 6)
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "위",
            color = "#112233",
            chiefGeneralId = 1,
            capitalCityId = 99,
            gennum = 1,
            power = 400,
            level = 5,
        )
        val city = City(
            id = 99,
            worldId = 1,
            name = "낙양",
            level = 5,
            nationId = 1,
        )
        val diplomacy = Diplomacy(
            id = 7,
            worldId = 1,
            srcNationId = 1,
            destNationId = 2,
            stateCode = "전쟁",
        )
        val nationTurn = NationTurn(
            id = 11,
            worldId = 1,
            nationId = 1,
            officerLevel = 12,
            turnIdx = 0,
            actionCode = "Nation휴식",
        )
        val dead = createGeneral(
            id = 1,
            age = 40,
            killTurn = 0,
            nationId = 1,
            officerLevel = 12,
        )

        `when`(nationRepository.findById(1L)).thenReturn(Optional.of(nation))
        `when`(cityRepository.findByNationId(1L)).thenReturn(listOf(city))
        `when`(troopRepository.findByNationId(1L)).thenReturn(emptyList())
        `when`(diplomacyRepository.findByWorldId(1L)).thenReturn(listOf(diplomacy))
        `when`(nationTurnRepository.findByWorldId(1L)).thenReturn(listOf(nationTurn))

        service.processGeneralMaintenance(world, listOf(dead))

        assertEquals(5.toShort(), dead.npcState)
        assertEquals(0L, dead.nationId)
        assertEquals(0.toShort(), nation.level)
        assertEquals(0, nation.gennum)
        assertEquals(0, nation.power)
        assertEquals(0L, nation.chiefGeneralId)
        assertNull(nation.capitalCityId)
        assertEquals(0L, city.nationId)
        assertEquals(0.toShort(), city.frontState)
        assertTrue(diplomacy.isDead)
        assertFalse(diplomacy.isShowing)
        verify(nationTurnRepository).delete(nationTurn)
        verify(historyService).logWorldHistory(eq(1L), contains("멸망"), eq(200), eq(6))
        verify(historyService).logNationHistory(eq(1L), eq(1L), contains("사망"), eq(200), eq(6))
    }
}
