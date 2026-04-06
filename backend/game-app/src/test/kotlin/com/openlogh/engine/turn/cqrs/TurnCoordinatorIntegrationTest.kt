package com.openlogh.engine.turn.cqrs

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.EconomyService
import com.openlogh.engine.EventService
import com.openlogh.engine.OfficerMaintenanceService
import com.openlogh.engine.NpcSpawnService
import com.openlogh.engine.SpecialAssignmentService
import com.openlogh.engine.UnificationService
import com.openlogh.engine.YearbookService
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnKey
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.WorldStateLoader
import com.openlogh.engine.turn.cqrs.persist.WorldStatePersister
import com.openlogh.entity.SessionState
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.AuctionService
import com.openlogh.service.GameEventService
import com.openlogh.service.InheritanceService
import com.openlogh.service.FactionService
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
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var turnStatusService: TurnStatusService
    private lateinit var gameEventService: GameEventService
    private lateinit var processor: InMemoryTurnProcessor
    private lateinit var coordinator: TurnCoordinator

    @BeforeEach
    fun setUp() {
        worldStateLoader = mock(WorldStateLoader::class.java)
        worldStatePersister = mock(WorldStatePersister::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        turnStatusService = mock(TurnStatusService::class.java)
        gameEventService = mock(GameEventService::class.java)

        processor = InMemoryTurnProcessor(
            economyService = mock(EconomyService::class.java),
            eventService = mock(EventService::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
            officerMaintenanceService = mock(OfficerMaintenanceService::class.java),
            specialAssignmentService = mock(SpecialAssignmentService::class.java),
            npcSpawnService = mock(NpcSpawnService::class.java),
            unificationService = mock(UnificationService::class.java),
            inheritanceService = mock(InheritanceService::class.java),
            yearbookService = mock(YearbookService::class.java),
            auctionService = mock(AuctionService::class.java),
            tournamentService = mock(TournamentService::class.java),
            trafficSnapshotRepository = mock(TrafficSnapshotRepository::class.java),
            worldService = mock(WorldService::class.java),
            factionService = mock(FactionService::class.java),
        )

        coordinator = TurnCoordinator(
            worldStateLoader = worldStateLoader,
            inMemoryTurnProcessor = processor,
            worldStatePersister = worldStatePersister,
            sessionStateRepository = sessionStateRepository,
            turnStatusService = turnStatusService,
            gameEventService = gameEventService,
        )
    }

    @Test
    fun `coordinator path advances turn and mutates in-memory queues`() {
        val now = OffsetDateTime.now()
        val world = SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = now.minusSeconds(70),
        )

        val state = InMemoryWorldState(
            sessionId = 1,
            officers = mutableMapOf(
                101L to generalSnapshot(
                    id = 101,
                    sessionId = 1,
                    factionId = 10,
                    officerLevel = 5,
                    turnTime = now.minusSeconds(20),
                )
            ),
            factions = mutableMapOf(
                10L to nationSnapshot(id = 10, sessionId = 1, strategicCmdLimit = 3)
            ),
            officerTurnsByOfficerId = mutableMapOf(
                101L to mutableListOf(
                    OfficerTurnSnapshot(
                        id = 1,
                        sessionId = 1,
                        officerId = 101,
                        turnIdx = 0,
                        actionCode = "휴식",
                        arg = mutableMapOf(),
                        brief = null,
                        createdAt = now.minusMinutes(1),
                    )
                )
            ),
            factionTurnsByFactionAndLevel = mutableMapOf(
                FactionTurnKey(10, 5) to mutableListOf(
                    FactionTurnSnapshot(
                        id = 2,
                        sessionId = 1,
                        factionId = 10,
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
        doAnswer { it.arguments[0] }.`when`(sessionStateRepository).save(org.mockito.Mockito.any(SessionState::class.java))

        coordinator.processSession(world)

        assertEquals(2, world.currentMonth.toInt())
        assertTrue(state.officerTurnsByOfficerId[101].isNullOrEmpty())
        assertTrue(state.factionTurnsByFactionAndLevel[FactionTurnKey(10, 5)].isNullOrEmpty())
        assertEquals(2, state.factions.getValue(10).strategicCmdLimit.toInt())
    }

    private fun nationSnapshot(id: Long, sessionId: Long, strategicCmdLimit: Int): FactionSnapshot {
        val now = OffsetDateTime.now()
        return FactionSnapshot(
            id = id,
            sessionId = sessionId,
            name = "nation-$id",
            color = "#ffffff",
            capitalPlanetId = null,
            funds = 1000,
            supplies = 1000,
            taxRate = 0,
            conscriptionRate = 0,
            conscriptionRateTmp = 0,
            secretLimit = 3,
            chiefOfficerId = 0,
            scoutLevel = 0,
            warState = 0,
            strategicCmdLimit = strategicCmdLimit.toShort(),
            surrenderLimit = 72,
            techLevel = 0f,
            militaryPower = 0,
            officerCount = 0,
            level = 0,
            factionType = "che_중립",
            abbreviation = "",
            spy = mutableMapOf(),
            meta = mutableMapOf(),
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun generalSnapshot(
        id: Long,
        sessionId: Long,
        factionId: Long,
        officerLevel: Int,
        turnTime: OffsetDateTime,
    ): OfficerSnapshot {
        val now = OffsetDateTime.now()
        return OfficerSnapshot(
            id = id,
            sessionId = sessionId,
            userId = null,
            name = "general-$id",
            factionId = factionId,
            planetId = 1,
            fleetId = 0,
            npcState = 0,
            npcOrg = null,
            affinity = 0,
            bornYear = 180,
            deadYear = 260,
            picture = "",
            imageServer = 0,
            leadership = 50,
            leadershipExp = 0,
            command = 50,
            commandExp = 0,
            intelligence = 50,
            intelligenceExp = 0,
            politics = 50,
            politicsExp = 0,
            administration = 50,
            administrationExp = 0,
            mobility = 50,
            mobilityExp = 0,
            attack = 50,
            attackExp = 0,
            defense = 50,
            defenseExp = 0,
            dex1 = 0,
            dex2 = 0,
            dex3 = 0,
            dex4 = 0,
            dex5 = 0,
            dex6 = 0,
            dex7 = 0,
            dex8 = 0,
            injury = 0,
            experience = 0,
            dedication = 0,
            officerLevel = officerLevel.toShort(),
            officerPlanet = 0,
            permission = "normal",
            funds = 1000,
            supplies = 1000,
            ships = 1000,
            shipClass = 0,
            training = 0,
            morale = 0,
            flagshipCode = "None",
            equipCode = "None",
            engineCode = "None",
            accessoryCode = "None",
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
            posX = 0f,
            posY = 0f,
            destX = null,
            destY = null,
            lastTurn = mutableMapOf(),
            meta = mutableMapOf(),
            penalty = mutableMapOf(),
            createdAt = now,
            updatedAt = now,
        )
    }
}
