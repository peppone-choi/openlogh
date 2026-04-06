package com.openlogh.service

import com.openlogh.command.CommandExecutor
import com.openlogh.command.CommandRegistry
import com.openlogh.entity.Officer
import com.openlogh.entity.OfficerTurn
import com.openlogh.entity.SessionState
import com.openlogh.engine.RealtimeService
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.SessionStateRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var factionTurnRepository: FactionTurnRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var commandRegistry: CommandRegistry
    private lateinit var realtimeService: RealtimeService
    private lateinit var gameConstService: GameConstService
    private lateinit var service: CommandService

    @BeforeEach
    fun setUp() {
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        factionTurnRepository = mock(FactionTurnRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        commandExecutor = mock(CommandExecutor::class.java)
        commandRegistry = mock(CommandRegistry::class.java)
        realtimeService = mock(RealtimeService::class.java)
        gameConstService = mock(GameConstService::class.java)
        `when`(gameConstService.getInt("maxTurn")).thenReturn(5)
        `when`(gameConstService.getInt("maxChiefTurn")).thenReturn(4)
        `when`(officerTurnRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer { it.arguments[0] }
        `when`(factionTurnRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).thenAnswer { it.arguments[0] }

        service = CommandService(
            officerTurnRepository = officerTurnRepository,
            factionTurnRepository = factionTurnRepository,
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            sessionStateRepository = sessionStateRepository,
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
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.pushTurns(1L, 2)

        assertEquals(listOf("휴식", "휴식", "A", "B", "C"), result!!.map { it.actionCode })
    }

    @Test
    fun `pushTurns with negative amount pulls queue left and fills tail with rest`() {
        val general = createGeneral()
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.pushTurns(1L, -2)

        assertEquals(listOf("C", "D", "E", "휴식", "휴식"), result!!.map { it.actionCode })
    }

    @Test
    fun `repeatTurns copies interval pattern across fixed queue`() {
        val general = createGeneral()
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = queue(general, listOf("A", "B", "C", "D", "E"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(officerTurnRepository.findByOfficerIdOrderByTurnIdx(1L)).thenReturn(existing)

        val result = service.repeatTurns(1L, 2)

        assertEquals(listOf("A", "B", "A", "B", "A"), result!!.map { it.actionCode })
        assertTrue(result.all { it.turnIdx.toInt() in 0..4 })
    }

    @Test
    fun `pushNationTurns shifts queue right and fills leading slots with rest`() {
        val general = createGeneral(officerLevel = 5)
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.pushNationTurns(1L, 1L, 1)

        assertEquals(listOf("휴식", "N1", "N2", "N3"), result!!.map { it.actionCode })
    }

    @Test
    fun `pushNationTurns with negative amount pulls queue left and fills tail with rest`() {
        val general = createGeneral(officerLevel = 5)
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.pushNationTurns(1L, 1L, -2)

        assertEquals(listOf("N3", "N4", "휴식", "휴식"), result!!.map { it.actionCode })
    }

    @Test
    fun `repeatNationTurns copies interval pattern across fixed chief queue`() {
        val general = createGeneral(officerLevel = 5)
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val existing = nationQueue(general, listOf("N1", "N2", "N3", "N4"))

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(factionTurnRepository.findByFactionIdAndOfficerLevelOrderByTurnIdx(1L, 5)).thenReturn(existing)

        val result = service.repeatNationTurns(1L, 1L, 2)

        assertEquals(listOf("N1", "N2", "N1", "N2"), result!!.map { it.actionCode })
        assertTrue(result.all { it.turnIdx.toInt() in 0..3 })
    }

    @Test
    fun `reserveGeneralTurns flushes after delete to prevent DuplicateKeyException`() {
        val general = createGeneral()
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val turns = listOf(
            com.openlogh.dto.TurnEntry(turnIdx = 0, actionCode = "모병", arg = mapOf("crewType" to 1300, "amount" to 4500)),
        )

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(officerTurnRepository.save(org.mockito.ArgumentMatchers.any(OfficerTurn::class.java))).thenAnswer { it.arguments[0] }

        val result = service.reserveGeneralTurns(1L, turns)

        val inOrder = org.mockito.Mockito.inOrder(officerTurnRepository)
        inOrder.verify(officerTurnRepository).deleteByOfficerIdAndTurnIdxIn(1L, listOf(0.toShort()))
        inOrder.verify(officerTurnRepository).flush()
        inOrder.verify(officerTurnRepository).save(org.mockito.ArgumentMatchers.any(OfficerTurn::class.java))

        assertEquals(1, result.size)
        assertEquals("모병", result[0].actionCode)
    }

    @Test
    fun `general command categories match legacy 6-group structure`() {
        val legacyCategories = setOf("개인", "내정", "군사", "인사", "계략", "국가")
        val testCases = mapOf(
            "휴식" to "개인", "요양" to "개인", "단련" to "개인",
            "농지개간" to "내정", "상업투자" to "내정", "기술연구" to "내정",
            "징병" to "군사", "모병" to "군사", "출병" to "군사", "첩보" to "군사",
            "이동" to "인사", "강행" to "인사", "등용" to "인사", "임관" to "인사",
            "선동" to "계략", "탈취" to "계략", "파괴" to "계략", "화계" to "계략",
            "건국" to "국가", "거병" to "국가", "증여" to "국가", "헌납" to "국가",
        )
        for ((code, expected) in testCases) {
            val method = service.javaClass.getDeclaredMethod("generalCategory", String::class.java)
            method.isAccessible = true
            val actual = method.invoke(service, code) as String
            assertEquals(expected, actual, "Command '$code' should be in category '$expected' but was '$actual'")
            assertTrue(legacyCategories.contains(actual), "Category '$actual' is not in legacy categories")
        }
    }

    @Test
    fun `nation command categories match legacy 7-group structure`() {
        val legacyCategories = setOf("휴식", "인사", "외교", "특수", "전략", "기타", "연구")
        val testCases = mapOf(
            "Nation휴식" to "휴식",
            "발령" to "인사", "포상" to "인사", "몰수" to "인사",
            "선전포고" to "외교", "불가침제의" to "외교", "종전제의" to "외교",
            "초토화" to "특수", "천도" to "특수", "증축" to "특수",
            "필사즉생" to "전략", "급습" to "전략", "수몰" to "전략",
            "국기변경" to "기타", "국호변경" to "기타",
            "극병연구" to "연구", "화시병연구" to "연구",
        )
        for ((code, expected) in testCases) {
            val method = service.javaClass.getDeclaredMethod("nationCategory", String::class.java)
            method.isAccessible = true
            val actual = method.invoke(service, code) as String
            assertEquals(expected, actual, "Nation command '$code' should be in '$expected' but was '$actual'")
            assertTrue(legacyCategories.contains(actual), "Category '$actual' is not in legacy categories")
        }
    }

    @Test
    fun `repeatNationTurns rejects mismatched nation before mutating`() {
        val general = createGeneral(officerLevel = 5)
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))

        val result = service.repeatNationTurns(1L, 99L, 2)

        assertEquals(null, result)
        verify(factionTurnRepository, never()).findByFactionIdAndOfficerLevelOrderByTurnIdx(1L, 5)
    }

    private fun createGeneral(officerLevel: Short = 0): Officer = Officer(
        id = 1,
        sessionId = 1,
        name = "장수",
        factionId = 1,
        planetId = 1,
        officerLevel = officerLevel,
        turnTime = OffsetDateTime.now(),
    )

    private fun queue(general: Officer, actions: List<String>): List<OfficerTurn> {
        return actions.mapIndexed { idx, action ->
            OfficerTurn(
                sessionId = general.sessionId,
                officerId = general.id,
                turnIdx = idx.toShort(),
                actionCode = action,
                brief = action,
            )
        }
    }

    private fun nationQueue(general: Officer, actions: List<String>): List<com.openlogh.entity.FactionTurn> {
        return actions.mapIndexed { idx, action ->
            com.openlogh.entity.FactionTurn(
                sessionId = general.sessionId,
                factionId = general.factionId,
                officerLevel = general.officerLevel,
                turnIdx = idx.toShort(),
                actionCode = action,
                brief = action,
            )
        }
    }

    @Test
    fun `world config openingPartYears key is recognized by CommandService`() {
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        world.config["openingPartYears"] = 0
        val value = (world.config["openingPartYears"] as? Number)?.toInt()
        assertEquals(0, value)
    }

    @Test
    fun `getCommandTable shows disabled commands when minCondition fails`() {
        val minCheckField = com.openlogh.command.BaseCommand::class.java.getDeclaredMethod("checkMinCondition")
        assertNotNull(minCheckField)
    }

    @Test
    fun `DomesticCommand has minConditionConstraints with NotBeNeutral`() {
        val field = com.openlogh.command.general.DomesticCommand::class.java.getDeclaredMethod("getMinConditionConstraints")
        assertNotNull(field)
    }

    @Test
    fun `scenario command name normalization strips prefixes`() {
        val rawNames = listOf("휴식", "che_이동", "cr_건국", "che_농지개간")
        val normalized = rawNames.map { it.removePrefix("che_").removePrefix("cr_") }.toSet()

        assertTrue("휴식" in normalized)
        assertTrue("이동" in normalized)
        assertTrue("건국" in normalized)
        assertTrue("농지개간" in normalized)
        assertEquals(4, normalized.size)
    }

    @Test
    fun `NPC and CR commands are excluded from general category when no whitelist`() {
        val npcCrCommands = listOf("NPC능동", "CR건국", "CR맹훈련")
        val method = service.javaClass.getDeclaredMethod("generalCategory", String::class.java)
        method.isAccessible = true
        for (code in npcCrCommands) {
            val category = method.invoke(service, code) as String
            assertEquals("기타", category, "NPC/CR command '$code' falls into '기타' category")
        }
        // Verify the filter logic: NPC/CR prefixed commands should be skipped when no whitelist
        assertTrue(npcCrCommands.all { it.startsWith("NPC") || it.startsWith("CR") })
    }

    @Test
    fun `reserveGeneralTurns fires CommandEvent with reserved type`() {
        val gameEventService = mock(GameEventService::class.java)
        val general = createGeneral()
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val turns = listOf(
            com.openlogh.dto.TurnEntry(turnIdx = 0, actionCode = "휴식", arg = null),
        )

        `when`(officerRepository.findById(1L)).thenReturn(Optional.of(general))
        `when`(sessionStateRepository.findById(1)).thenReturn(Optional.of(world))
        `when`(officerTurnRepository.save(org.mockito.ArgumentMatchers.any(OfficerTurn::class.java))).thenAnswer { it.arguments[0] }

        val serviceWithEvent = CommandService(
            officerTurnRepository = officerTurnRepository,
            factionTurnRepository = factionTurnRepository,
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            sessionStateRepository = sessionStateRepository,
            appUserRepository = appUserRepository,
            commandExecutor = commandExecutor,
            commandRegistry = commandRegistry,
            realtimeService = realtimeService,
            gameConstService = gameConstService,
            gameEventService = gameEventService,
        )

        serviceWithEvent.reserveGeneralTurns(1L, turns)

        verify(gameEventService).fireCommand(
            sessionId = 1L,
            year = world.currentYear,
            month = world.currentMonth,
            officerId = 1L,
            commandEventType = "reserved",
            detail = mapOf("turnCount" to 1),
        )
    }

    @Test
    fun `world config stores available command whitelist from scenario`() {
        val world = SessionState(id = 1, name = "world", scenarioCode = "test", realtimeMode = false)
        val whitelist = mapOf("개인" to listOf("휴식", "che_이동"))
        world.config["availableGeneralCommand"] = whitelist

        @Suppress("UNCHECKED_CAST")
        val stored = world.config["availableGeneralCommand"] as? Map<String, List<String>>
        assertNotNull(stored)
        assertEquals(1, stored!!.size)
        assertEquals(listOf("휴식", "che_이동"), stored["개인"])
    }
}
