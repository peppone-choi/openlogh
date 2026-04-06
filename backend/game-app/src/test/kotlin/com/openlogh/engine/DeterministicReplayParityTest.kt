package com.openlogh.engine

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldPorts
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnKey
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.entity.SessionState
import com.openlogh.repository.TrafficSnapshotRepository
import com.openlogh.service.AuctionService
import com.openlogh.service.InheritanceService
import com.openlogh.service.FactionService
import com.openlogh.service.TournamentService
import com.openlogh.service.WorldService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.time.OffsetDateTime

class DeterministicReplayParityTest {
    private val processor = InMemoryTurnProcessor(
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

    @Test
    fun `replay fixture consumes player and nation queues with expected snapshot`() {
        val fixture = replayFixturePlayerAndNationQueues()
        val canonical = runFixture(fixture)

        assertEquals(1, canonical.advancedTurns)
        assertEquals(200, canonical.worldYear)
        assertEquals(2, canonical.worldMonth)
        assertEquals(2, canonical.strategicCmdLimit)
        assertEquals("개간", canonical.lastActionCode)
        assertEquals(mapOf("planetId" to 1), canonical.lastActionArg)
        assertEquals(0, canonical.generalQueueSize)
        assertEquals(0, canonical.nationQueueSize)
        assertEquals(0, canonical.generalNpcState)
        assertEquals(10, canonical.generalNationId)
        assertNull(canonical.generalKillTurn)
    }

    @Test
    fun `replay fixture handles blocked kill-turn general exactly once per tick`() {
        val fixture = replayFixtureBlockedKillTurn()
        val canonical = runFixture(fixture)

        assertEquals(1, canonical.advancedTurns)
        assertEquals(5, canonical.generalNpcState)
        assertEquals(0, canonical.generalNationId)
        assertNull(canonical.generalKillTurn)
        assertEquals("NONE", canonical.lastActionCode)
        assertEquals(emptyMap<String, Any>(), canonical.lastActionArg)
        assertEquals(1, canonical.generalQueueSize)
    }

    @Test
    fun `same replay fixture produces same canonical output`() {
        val left = runFixture(replayFixturePlayerAndNationQueues())
        val right = runFixture(replayFixturePlayerAndNationQueues())

        assertEquals(left, right)
    }

    private fun runFixture(fixture: ReplayFixture): CanonicalReplayOutput {
        val ports = InMemoryWorldPorts(fixture.state, fixture.tracker)
        val result = processor.process(fixture.world, fixture.state, ports)
        val general = fixture.state.officers.getValue(fixture.officerId)
        val nation = fixture.state.factions.getValue(fixture.factionId)
        val lastActionCode = general.lastTurn["actionCode"] as? String ?: "NONE"
        val lastActionArg = (general.lastTurn["arg"] as? Map<*, *>)
            ?.entries
            ?.filter { it.key is String && it.value != null }
            ?.associate { it.key as String to it.value as Any }
            ?: emptyMap()

        return CanonicalReplayOutput(
            advancedTurns = result.advancedTurns,
            worldYear = fixture.world.currentYear.toInt(),
            worldMonth = fixture.world.currentMonth.toInt(),
            strategicCmdLimit = nation.strategicCmdLimit.toInt(),
            lastActionCode = lastActionCode,
            lastActionArg = lastActionArg,
            generalQueueSize = fixture.state.officerTurnsByOfficerId[fixture.officerId]?.size ?: 0,
            nationQueueSize = fixture.state.factionTurnsByFactionAndLevel[
                FactionTurnKey(fixture.factionId, fixture.generalOfficerLevel)
            ]?.size ?: 0,
            generalNpcState = general.npcState.toInt(),
            generalNationId = general.factionId,
            generalKillTurn = general.killTurn?.toInt(),
        )
    }

    private fun replayFixturePlayerAndNationQueues(): ReplayFixture {
        val now = OffsetDateTime.now()
        val world = SessionState(
            id = 1,
            name = "replay-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 300,
            updatedAt = now.minusSeconds(301),
        )

        val general = generalSnapshot(
            id = 101,
            sessionId = 1,
            factionId = 10,
            officerLevel = 5,
            npcState = 0,
            killTurn = null,
            blockState = 0,
            turnTime = now.minusSeconds(20),
        )

        val nation = nationSnapshot(
            id = 10,
            sessionId = 1,
            strategicCmdLimit = 3,
        )

        val state = InMemoryWorldState(
            sessionId = 1,
            officers = mutableMapOf(101L to general),
            factions = mutableMapOf(10L to nation),
            officerTurnsByOfficerId = mutableMapOf(
                101L to mutableListOf(
                    OfficerTurnSnapshot(
                        id = 1,
                        sessionId = 1,
                        officerId = 101,
                        turnIdx = 0,
                        actionCode = "개간",
                        arg = mutableMapOf("planetId" to 1),
                        brief = null,
                        createdAt = now.minusMinutes(1),
                    )
                )
            ),
            factionTurnsByFactionAndLevel = mutableMapOf(
                FactionTurnKey(10L, 5) to mutableListOf(
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

        return ReplayFixture(
            world = world,
            state = state,
            tracker = DirtyTracker(),
            officerId = 101,
            factionId = 10,
            generalOfficerLevel = 5,
        )
    }

    private fun replayFixtureBlockedKillTurn(): ReplayFixture {
        val now = OffsetDateTime.now()
        val world = SessionState(
            id = 1,
            name = "replay-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 300,
            updatedAt = now.minusSeconds(301),
        )

        val general = generalSnapshot(
            id = 201,
            sessionId = 1,
            factionId = 20,
            officerLevel = 5,
            npcState = 2,
            killTurn = 1,
            blockState = 2,
            turnTime = now.minusSeconds(10),
        )

        val nation = nationSnapshot(
            id = 20,
            sessionId = 1,
            strategicCmdLimit = 1,
        )

        val state = InMemoryWorldState(
            sessionId = 1,
            officers = mutableMapOf(201L to general),
            factions = mutableMapOf(20L to nation),
            officerTurnsByOfficerId = mutableMapOf(
                201L to mutableListOf(
                    OfficerTurnSnapshot(
                        id = 3,
                        sessionId = 1,
                        officerId = 201,
                        turnIdx = 0,
                        actionCode = "휴식",
                        arg = mutableMapOf(),
                        brief = null,
                        createdAt = now.minusMinutes(1),
                    )
                )
            ),
        )

        return ReplayFixture(
            world = world,
            state = state,
            tracker = DirtyTracker(),
            officerId = 201,
            factionId = 20,
            generalOfficerLevel = 5,
        )
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
            factionRank = 0,
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
        npcState: Int,
        killTurn: Int?,
        blockState: Int,
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
            npcState = npcState.toShort(),
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
            killTurn = killTurn?.toShort(),
            blockState = blockState.toShort(),
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
            positionCards = mutableListOf("PERSONAL", "CAPTAIN"),
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

    private data class ReplayFixture(
        val world: SessionState,
        val state: InMemoryWorldState,
        val tracker: DirtyTracker,
        val officerId: Long,
        val factionId: Long,
        val generalOfficerLevel: Short,
    )

    private data class CanonicalReplayOutput(
        val advancedTurns: Int,
        val worldYear: Int,
        val worldMonth: Int,
        val strategicCmdLimit: Int,
        val lastActionCode: String,
        val lastActionArg: Map<String, Any>,
        val generalQueueSize: Int,
        val nationQueueSize: Int,
        val generalNpcState: Int,
        val generalNationId: Long,
        val generalKillTurn: Int?,
    )
}
