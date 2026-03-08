package com.opensam.service

import com.opensam.command.CommandExecutor
import com.opensam.command.CommandRegistry
import com.opensam.entity.General
import com.opensam.entity.GeneralTurn
import com.opensam.entity.WorldState
import com.opensam.engine.RealtimeService
import com.opensam.repository.AppUserRepository
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.GeneralTurnRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.NationTurnRepository
import com.opensam.repository.WorldStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional

class CommandServiceTest {
    private lateinit var generalTurnRepository: GeneralTurnRepository
    private lateinit var nationTurnRepository: NationTurnRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var realtimeService: RealtimeService
    private lateinit var gameConstService: GameConstService
    private lateinit var service: CommandService

    @BeforeEach
    fun setUp() {
        generalTurnRepository = mock(GeneralTurnRepository::class.java)
        nationTurnRepository = mock(NationTurnRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        commandExecutor = mock(CommandExecutor::class.java)
        commandRegistry = mock(CommandRegistry::class.java)
        realtimeService = mock(RealtimeService::class.java)
        gameConstService = mock(GameConstService::class.java)
        `when`(gameConstService.getInt("maxTurn")).thenReturn(5)
        `when`(gameConstService.getInt("maxChiefTurn")).thenReturn(4)
        `when`(generalTurnRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer { it.arguments[0] }
        `when`(nationTurnRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer { it.arguments[0] }

        service = CommandService(
            generalTurnRepository = generalTurnRepository,
            nationTurnRepository = nationTurnRepository,
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
            worldStateRepository = worldStateRepository,
            appUserRepository = appUserRepository,
            commandExecutor = commandExecutor,
            commandRegistry = commandRegistry,
            realtimeService = realtimeService,
            gameConstService = gameConstService,
        )
    }

    @Test
    fun `pushTurns shifts queue right and fills leading slots with rest`() {
        val general = createGeneral()
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.pushTurns(1L, 2)

        assertEquals(listOf("휴식", "휴식", "A", "B", "C"), result!!.map { it.actionCode })
    }

    @Test
    fun `pushTurns with negative amount pulls queue left and fills tail with rest`() {
        val general = createGeneral()
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.pushTurns(1L, -2)

        assertEquals(listOf("C", "D", "E", "휴식", "휴식"), result!!.map { it.actionCode })
    }

    @Test
    fun `repeatTurns copies interval pattern across fixed queue`() {
        val general = createGeneral()
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(generalTurnRepository.findByGeneralIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.repeatTurns(1L, 2)

        assertEquals(listOf("A", "B", "A", "B", "A"), result!!.map { it.actionCode })
        assertTrue(result.all { it.turnIdx.toInt() in 0..4 })
    }

    @Test
    fun `pushNationTurns shifts queue right and fills leading slots with rest`() {
        val general = createGeneral(officerLevel = 5)
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(nationTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.pushNationTurns(1L, 1L, 1)

        assertEquals(listOf("휴식", "N1", "N2", "N3"), result!!.map { it.actionCode })
    }

    @Test
    fun `pushNationTurns with negative amount pulls queue left and fills tail with rest`() {
        val general = createGeneral(officerLevel = 5)
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(nationTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.pushNationTurns(1L, 1L, -2)

        assertEquals(listOf("N3", "N4", "휴식", "휴식"), result!!.map { it.actionCode })
    }

    @Test
    fun `repeatNationTurns copies interval pattern across fixed chief queue`() {
        val general = createGeneral(officerLevel = 5)
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(nationTurnRepository.findByNationIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.repeatNationTurns(1L, 1L, 2)

        assertEquals(listOf("N1", "N2", "N1", "N2"), result!!.map { it.actionCode })
        assertTrue(result.all { it.turnIdx.toInt() in 0..3 })
    }

    @Test
    fun `repeatNationTurns rejects mismatched nation before mutating`() {
        val general = createGeneral(officerLevel = 5)
        val world = WorldState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)

        `when`(generalRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(worldStateRepository.findById(1)).thenReturn(Optional.of(world))

        val result = service.repeatNationTurns(1L, 99L, 2)

        assertEquals(null, result)
        verify(nationTurnRepository, never()).findByNationIdAndOfficerLevelOrderByTurnIdx(1L, 5)
    }

    private fun createGeneral(officerLevel: Short = 0): General = General(
        id = 1,
        worldId = 1,
        name = "장수",
        nationId = 1,
        cityId = 1,
        officerLevel = officerLevel,
        turnTime = OffsetDateTime.now(),
    )

    private fun queue(general: General, actions: List<String>): List<GeneralTurn> {
        return actions.mapIndexed { idx, action ->
            GeneralTurn(
                worldId = general.worldId,
                generalId = general.id,
                turnIdx = idx.toShort(),
                actionCode = action,
                brief = action,
            )
        }
    }

    private fun nationQueue(general: General, actions: List<String>): List<com.opensam.entity.NationTurn> {
        return actions.mapIndexed { idx, action ->
            com.opensam.entity.NationTurn(
                worldId = general.worldId,
                nationId = general.nationId,
                officerLevel = general.officerLevel,
                turnIdx = idx.toShort(),
                actionCode = action,
                brief = action,
            )
        }
    }
}
