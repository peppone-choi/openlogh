package com.openlogh.engine.tactical

import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OperationPlanRepository
import com.openlogh.repository.StarSystemRepository
import com.openlogh.repository.TacticalBattleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Phase 12 Plan 12-03 Task 2 RED/GREEN: BattleTriggerService must inject
 * OperationPlanRepository and populate TacticalBattleState.missionObjectiveByFleetId
 * + operationParticipantFleetIds at battle init (D-07).
 *
 * Uses plain org.mockito.Mockito (mockito-kotlin is NOT on the classpath — see
 * build.gradle.kts:85 exclusion note).
 */
class BattleTriggerOperationInjectionTest {

    private val fleetRepository: FleetRepository = mock(FleetRepository::class.java)
    private val officerRepository: OfficerRepository = mock(OfficerRepository::class.java)
    private val starSystemRepository: StarSystemRepository = mock(StarSystemRepository::class.java)
    private val tacticalBattleRepository: TacticalBattleRepository = mock(TacticalBattleRepository::class.java)
    private val operationPlanRepository: OperationPlanRepository = mock(OperationPlanRepository::class.java)

    private fun buildService(): BattleTriggerService = BattleTriggerService(
        fleetRepository = fleetRepository,
        officerRepository = officerRepository,
        starSystemRepository = starSystemRepository,
        tacticalBattleRepository = tacticalBattleRepository,
        operationPlanRepository = operationPlanRepository,
    )

    private fun stubFleetAndOfficer(fleetId: Long, officerId: Long, factionId: Long) {
        // Fleet constructor from Fleet.kt:11-49 — all fields have defaults, id is non-nullable Long.
        val fleet = Fleet().also {
            it.id = fleetId
            it.sessionId = 1L
            it.factionId = factionId
            it.leaderOfficerId = officerId
            it.name = "F$fleetId"
            it.planetId = 100L
        }
        // Officer stats are Short, not Int — cast literals.
        val officer = Officer(name = "O$officerId").also {
            it.id = officerId
            it.sessionId = 1L
            it.factionId = factionId
            it.ships = 300
            it.training = 80
            it.morale = 80
            it.leadership = 50
            it.command = 50
            it.intelligence = 50
            it.mobility = 50
            it.attack = 50
            it.defense = 50
        }
        `when`(fleetRepository.findById(fleetId)).thenReturn(Optional.of(fleet))
        `when`(officerRepository.findById(officerId)).thenReturn(Optional.of(officer))
    }

    private fun makeBattle(fleetId: Long, opposingFleetId: Long): TacticalBattle = TacticalBattle(
        sessionId = 1L,
        starSystemId = 100L,
        attackerFactionId = 1L,
        defenderFactionId = 2L,
        phase = "PREPARING",
        participants = mutableMapOf(
            "attackers" to listOf(fleetId),
            "defenders" to listOf(opposingFleetId),
        ),
    ).also { it.id = 1L }

    private fun makePlan(
        fleetIds: List<Long>,
        objective: MissionObjective,
        status: OperationStatus = OperationStatus.ACTIVE,
    ): OperationPlan = OperationPlan(
        sessionId = 1L,
        factionId = 1L,
        name = "test",
        objective = objective,
        targetStarSystemId = 5L,
        status = status,
        participantFleetIds = fleetIds.toMutableList(),
        scale = 1,
        issuedByOfficerId = 1L,
        issuedAtTick = 0L,
    )

    @Test
    fun `buildInitialState populates map for participants from operation plan`() {
        stubFleetAndOfficer(fleetId = 42L, officerId = 142L, factionId = 1L)
        stubFleetAndOfficer(fleetId = 43L, officerId = 143L, factionId = 2L)
        `when`(starSystemRepository.findById(ArgumentMatchers.anyLong()))
            .thenReturn(Optional.empty())
        `when`(operationPlanRepository.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE))
            .thenReturn(
                listOf(
                    makePlan(fleetIds = listOf(42L), objective = MissionObjective.CONQUEST),
                ),
            )
        `when`(operationPlanRepository.findBySessionIdAndStatus(1L, OperationStatus.PENDING))
            .thenReturn(emptyList())

        val state = buildService().buildInitialState(makeBattle(fleetId = 42L, opposingFleetId = 43L))

        assertEquals(
            MissionObjective.CONQUEST,
            state.missionObjectiveByFleetId[42L],
            "Participant fleet 42 must receive its operation's objective (CONQUEST)",
        )
        assertTrue(
            state.operationParticipantFleetIds.contains(42L),
            "Participant fleet 42 must be flagged in operationParticipantFleetIds",
        )
    }

    @Test
    fun `non_participant_gets_default_from_personality`() {
        stubFleetAndOfficer(fleetId = 99L, officerId = 199L, factionId = 1L)
        stubFleetAndOfficer(fleetId = 200L, officerId = 201L, factionId = 2L)
        `when`(starSystemRepository.findById(ArgumentMatchers.anyLong()))
            .thenReturn(Optional.empty())
        `when`(operationPlanRepository.findBySessionIdAndStatus(1L, OperationStatus.ACTIVE))
            .thenReturn(emptyList())
        `when`(operationPlanRepository.findBySessionIdAndStatus(1L, OperationStatus.PENDING))
            .thenReturn(emptyList())

        val state = buildService().buildInitialState(makeBattle(fleetId = 99L, opposingFleetId = 200L))

        assertTrue(
            state.missionObjectiveByFleetId.containsKey(99L),
            "Non-participants must still receive a defaultForPersonality value in the map",
        )
        assertFalse(
            state.operationParticipantFleetIds.contains(99L),
            "Non-participants must NOT be in operationParticipantFleetIds (merit bonus gate)",
        )
    }
}
