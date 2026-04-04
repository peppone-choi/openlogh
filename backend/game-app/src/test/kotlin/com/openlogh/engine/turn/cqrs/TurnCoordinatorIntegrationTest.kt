package com.openlogh.engine.turn.cqrs

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.EconomyService
import com.openlogh.engine.EventService
import com.openlogh.engine.GeneralMaintenanceService
import com.openlogh.engine.NpcSpawnService
import com.openlogh.engine.SpecialAssignmentService
import com.openlogh.engine.UnificationService
import com.openlogh.engine.YearbookService
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.GeneralSnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.engine.turn.cqrs.memory.NationSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationTurnKey
import com.openlogh.engine.turn.cqrs.memory.NationTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.WorldStateLoader
import com.openlogh.engine.turn.cqrs.persist.WorldStatePersister
import com.openlogh.entity.WorldState
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.repository.WorldStateRepository
import com.openlogh.service.AuctionService
import com.openlogh.service.GameEventService
import com.openlogh.service.InheritanceService
import com.openlogh.service.NationService
import com.openlogh.service.TournamentService
import com.openlogh.service.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.time.OffsetDateTime

class TurnCoordinatorIntegrationTest {
    private lateinit var worldStateLoader: WorldStateLoader
    private lateinit var worldStatePersister: WorldStatePersister
    private lateinit var worldStateRepository: WorldStateRepository
    private lateinit var turnStatusService: TurnStatusService
    private lateinit var gameEventService: GameEventService
    private lateinit var processor: InMemoryTurnProcessor
    private lateinit var coordinator: TurnCoordinator

    @BeforeEach
    fun setUp() {
        worldStateLoader = mock(WorldStateLoader::class.java)
        worldStatePersister = mock(WorldStatePersister::class.java)
        worldStateRepository = mock(WorldStateRepository::class.java)
        turnStatusService = mock(TurnStatusService::class.java)
        gameEventService = mock(GameEventService::class.java)

        processor = InMemoryTurnProcessor(
            economyService = mock(EconomyService::class.java),
            eventService = mock(EventService::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
            generalMaintenanceService = mock(GeneralMaintenanceService::class.java),
            specialAssignmentService = mock(SpecialAssignmentService::class.java),
            npcSpawnService = mock(NpcSpawnService::class.java),
            unificationService = mock(UnificationService::class.java),
            inheritanceService = mock(InheritanceService::class.java),
            yearbookService = mock(YearbookService::class.java),
            auctionService = mock(AuctionService::class.java),
            tournamentService = mock(TournamentService::class.java),
            trafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java),
            worldService = mock(WorldService::class.java),
            nationService = mock(NationService::class.java),
        )

        coordinator = TurnCoordinator(
            worldStateLoader = worldStateLoader,
            inMemoryTurnProcessor = processor,
            worldStatePersister = worldStatePersister,
            worldStateRepository = worldStateRepository,
            turnStatusService = turnStatusService,
            gameEventService = gameEventService,
        )
    }

    @Test
    fun `coordinator path advances turn and mutates in-memory queues`() {
        val now = OffsetDateTime.now()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = now.minusSeconds(70),
        )

        val state = InMemoryWorldState(
            worldId = 1,
            generals = mutableMapOf(
                101L to generalSnapshot(
                    id = 101,
                    worldId = 1,
                    nationId = 10,
                    officerLevel = 5,
                    turnTime = now.minusSeconds(20),
                )
            ),
            nations = mutableMapOf(
                10L to nationSnapshot(id = 10, worldId = 1, strategicCmdLimit = 3)
            ),
            generalTurnsByGeneralId = mutableMapOf(
                101L to mutableListOf(
                    GeneralTurnSnapshot(
                        id = 1,
                        worldId = 1,
                        generalId = 101,
                        turnIdx = 0,
                        actionCode = "휴식",
                        arg = mutableMapOf(),
                        brief = null,
                        createdAt = now.minusMinutes(1),
                    )
                )
            ),
            nationTurnsByNationAndLevel = mutableMapOf(
                NationTurnKey(10, 5) to mutableListOf(
                    NationTurnSnapshot(
                        id = 2,
                        worldId = 1,
                        nationId = 10,
                        officerLevel = 5,
                        turnIdx = 0,
                        actionCode = "Nation휴식",
                        arg = mutableMapOf(),
                        brief = null,
                        createdAt = now.minusMinutes(1),
                    )
                )
            ),
        )

        doReturn(state).`when`(worldStateLoader).loadWorldState(1)
        doAnswer { it.arguments[0] }.`when`(worldStateRepository).save(org.mockito.Mockito.any(WorldState::class.java))

        coordinator.processWorld(world)

        assertEquals(2, world.currentMonth.toInt())
        assertTrue(state.generalTurnsByGeneralId[101].isNullOrEmpty())
        assertTrue(state.nationTurnsByNationAndLevel[NationTurnKey(10, 5)].isNullOrEmpty())
        assertEquals(2, state.nations.getValue(10).strategicCmdLimit.toInt())
    }

    private fun nationSnapshot(id: Long, worldId: Long, strategicCmdLimit: Int): NationSnapshot {
        val now = OffsetDateTime.now()
        return NationSnapshot(
            id = id,
            worldId = worldId,
            name = "nation-$id",
            color = "#ffffff",
            capitalCityId = null,
            gold = 1000,
            rice = 1000,
            bill = 0,
            rate = 0,
            rateTmp = 0,
            secretLimit = 3,
            chiefGeneralId = 0,
            scoutLevel = 0,
            warState = 0,
            strategicCmdLimit = strategicCmdLimit.toShort(),
            surrenderLimit = 72,
            tech = 0f,
            power = 0,
            level = 0,
            typeCode = "che_중립",
            spy = mutableMapOf(),
            meta = mutableMapOf(),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun generalSnapshot(
        id: Long,
        worldId: Long,
        nationId: Long,
        officerLevel: Int,
        turnTime: OffsetDateTime,
    ): GeneralSnapshot {
        val now = OffsetDateTime.now()
        return GeneralSnapshot(
            id = id,
            worldId = worldId,
            userId = null,
            name = "general-$id",
            nationId = nationId,
            cityId = 1,
            troopId = 0,
            npcState = 0,
            npcOrg = null,
            affinity = 0,
            bornYear = 180,
            deadYear = 260,
            picture = "",
            imageServer = 0,
            leadership = 50,
            leadershipExp = 0,
            strength = 50,
            strengthExp = 0,
            intel = 50,
            intelExp = 0,
            politics = 50,
            charm = 50,
            dex1 = 0,
            dex2 = 0,
            dex3 = 0,
            dex4 = 0,
            dex5 = 0,
            injury = 0,
            experience = 0,
            dedication = 0,
            officerLevel = officerLevel.toShort(),
            officerCity = 0,
            permission = "normal",
            gold = 1000,
            rice = 1000,
            crew = 1000,
            crewType = 0,
            train = 0,
            atmos = 0,
            weaponCode = "None",
            bookCode = "None",
            horseCode = "None",
            itemCode = "None",
            ownerName = "",
            newmsg = 0,
            turnTime = turnTime,
            recentWarTime = null,
            makeLimit = 0,
            killTurn = null,
            blockState = 0,
            dedLevel = 0,
            expLevel = 0,
            age = 30,
            startAge = 30,
            belong = 1,
            betray = 0,
            personalCode = "None",
            specialCode = "None",
            specAge = 0,
            special2Code = "None",
            spec2Age = 0,
            defenceTrain = 80,
            tournamentState = 0,
            commandPoints = 10,
            commandEndTime = null,
            lastTurn = mutableMapOf(),
            meta = mutableMapOf(),
            penalty = mutableMapOf(),
            createdAt = now,
            updatedAt = now,
        )
    }
}
