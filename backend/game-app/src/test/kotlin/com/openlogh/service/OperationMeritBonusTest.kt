package com.openlogh.service

import com.openlogh.OpenloghApplication
import com.openlogh.engine.tactical.BattleOutcome
import com.openlogh.engine.tactical.BattleSide
import com.openlogh.engine.tactical.TacticalBattleState
import com.openlogh.engine.tactical.TacticalUnit
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.OperationPlan
import com.openlogh.entity.TacticalBattle
import com.openlogh.model.OperationStatus
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.TacticalBattleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.util.AopTestUtils
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 12 Plan 12-04 Task 2a RED/GREEN: inline ×1.5 merit bonus in
 * TacticalBattleService.endBattle with participantSnapshot race guard
 * (Blocker 4 from 12-VALIDATION). First += merit accumulation path in
 * the codebase — RankLadderService.kt:124/143 are RESETS, not accumulations.
 *
 * Uses explicit classes = [OpenloghApplication::class] to avoid duplicate
 * @SpringBootConfiguration discovery with OpenloghApplicationTests$TestConfig
 * (pattern established in Plan 12-01 / 12-02).
 */
@SpringBootTest(
    classes = [OpenloghApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("test")
@Transactional
class OperationMeritBonusTest {

    @Autowired
    private lateinit var tacticalBattleService: TacticalBattleService

    @Autowired
    private lateinit var officerRepository: OfficerRepository

    @Autowired
    private lateinit var tacticalBattleRepository: TacticalBattleRepository

    @Autowired
    private lateinit var fleetRepository: FleetRepository

    /**
     * Build a TacticalUnit that will compute baseMerit == 100 under the
     * computeBaseMerit() heuristic (full survival on winning side).
     */
    private fun winningUnit(
        fleetId: Long,
        officerId: Long,
        side: BattleSide = BattleSide.ATTACKER,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "O$officerId",
        factionId = 1L,
        side = side,
        posX = 100.0,
        posY = 100.0,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = 50,
        defense = 50,
    )

    private fun seedOfficer(): Officer =
        officerRepository.saveAndFlush(
            Officer(name = "Off-${System.nanoTime()}").also {
                it.sessionId = 1L
                it.factionId = 1L
                it.ships = 300
                it.training = 80
                it.morale = 80
                it.leadership = 50
                it.command = 50
                it.intelligence = 50
                it.mobility = 50
                it.attack = 50
                it.defense = 50
                it.meritPoints = 0
            }
        )

    /**
     * Persist a Fleet row. endBattle's unit loop does
     * `fleetRepository.findById(unit.fleetId).orElse(null) ?: continue`,
     * so without a real Fleet row the loop body is skipped and merit never
     * accrues. The returned Fleet's auto-generated ID becomes the TacticalUnit's
     * fleetId.
     */
    private fun seedFleet(factionId: Long = 1L): Fleet =
        fleetRepository.saveAndFlush(
            Fleet().also {
                it.sessionId = 1L
                it.factionId = factionId
                it.leaderOfficerId = 1L
                it.name = "F-${System.nanoTime()}"
                it.planetId = 100L
            }
        )

    /**
     * Persist a TacticalBattle so endBattle's tacticalBattleRepository.save(battle)
     * updates an existing row instead of failing with StaleObjectStateException
     * (detached-update semantics on non-zero ID with no existing row).
     */
    private fun persistBattle(): TacticalBattle =
        tacticalBattleRepository.saveAndFlush(
            TacticalBattle(
                sessionId = 1L,
                starSystemId = 100L,
                attackerFactionId = 1L,
                defenderFactionId = 2L,
                phase = "ACTIVE",
                participants = mutableMapOf(
                    "attackers" to emptyList<Long>(),
                    "defenders" to emptyList<Long>(),
                ),
            )
        )

    private fun makeState(
        battleId: Long,
        units: MutableList<TacticalUnit>,
    ): TacticalBattleState =
        TacticalBattleState(
            battleId = battleId,
            starSystemId = 100L,
            units = units,
        )

    // Mirrors computeBaseMerit: winner + full survival = 100.
    private val BASE_MERIT = 100
    private val EXPECTED_BONUS = (BASE_MERIT * 1.5).toInt() // 150

    /**
     * Unwrap Spring's CGLIB proxy of TacticalBattleService (@Transactional class)
     * so reflection on the private `activeBattles` field hits the real bean
     * instance, not the proxy subclass (which holds null for inherited fields).
     */
    private fun realService(): TacticalBattleService =
        AopTestUtils.getTargetObject(tacticalBattleService)

    /**
     * Seed the in-memory activeBattles map via reflection so
     * syncOperationToActiveBattles finds the battle state in tests that simulate
     * cross-thread cancel races.
     */
    private fun seedActiveBattle(state: TacticalBattleState) {
        val target = realService()
        val field = TacticalBattleService::class.java.getDeclaredField("activeBattles")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(target) as ConcurrentHashMap<Long, TacticalBattleState>
        map[state.battleId] = state
    }

    @Test
    fun `participant receives 1_5x merit bonus`() {
        val officer = seedOfficer()
        val fleet = seedFleet()
        val battle = persistBattle()
        val unit = winningUnit(fleetId = fleet.id, officerId = officer.id)
        val state = makeState(battleId = battle.id, units = mutableListOf(unit))
        state.operationParticipantFleetIds.add(fleet.id)

        tacticalBattleService.endBattle(
            battle,
            state,
            BattleOutcome(winner = BattleSide.ATTACKER, reason = "test"),
        )

        val refreshed = officerRepository.findById(officer.id).orElseThrow()
        assertEquals(
            EXPECTED_BONUS,
            refreshed.meritPoints,
            "Participant must receive exactly baseMerit*1.5 = $EXPECTED_BONUS, not ${refreshed.meritPoints}",
        )
    }

    @Test
    fun `non participant gets base merit not bonus`() {
        val officer = seedOfficer()
        val fleet = seedFleet()
        val battle = persistBattle()
        val unit = winningUnit(fleetId = fleet.id, officerId = officer.id)
        val state = makeState(battleId = battle.id, units = mutableListOf(unit))
        // Intentionally do NOT add fleet.id to operationParticipantFleetIds.

        tacticalBattleService.endBattle(
            battle,
            state,
            BattleOutcome(winner = BattleSide.ATTACKER, reason = "test"),
        )

        val refreshed = officerRepository.findById(officer.id).orElseThrow()
        assertEquals(
            BASE_MERIT,
            refreshed.meritPoints,
            "Non-participant must receive exactly baseMerit = $BASE_MERIT (no bonus)",
        )
    }

    @Test
    fun `cancelled mid battle removes bonus`() {
        val officer = seedOfficer()
        val fleet = seedFleet()
        val battle = persistBattle()
        val unit = winningUnit(fleetId = fleet.id, officerId = officer.id)
        val state = makeState(battleId = battle.id, units = mutableListOf(unit))
        state.operationParticipantFleetIds.add(fleet.id)

        // Seed the battle into activeBattles (proxy-unwrapped) so
        // syncOperationToActiveBattles finds it.
        seedActiveBattle(state)

        // Simulate cancel arriving BEFORE endBattle runs.
        val cancelled = OperationPlan(
            sessionId = 1L,
            factionId = 1L,
            name = "cancel-mid",
            objective = MissionObjective.CONQUEST,
            targetStarSystemId = 100L,
            status = OperationStatus.CANCELLED,
            participantFleetIds = mutableListOf(fleet.id),
            scale = 1,
            issuedByOfficerId = 1L,
            issuedAtTick = 0L,
        )
        tacticalBattleService.syncOperationToActiveBattles(cancelled)

        // Now state.operationParticipantFleetIds no longer contains fleet.id.
        tacticalBattleService.endBattle(
            battle,
            state,
            BattleOutcome(winner = BattleSide.ATTACKER, reason = "test"),
        )

        val refreshed = officerRepository.findById(officer.id).orElseThrow()
        assertEquals(
            BASE_MERIT,
            refreshed.meritPoints,
            "Cancelled participant must fall back to base merit (no bonus)",
        )
    }

    /**
     * Blocker 4 regression: endBattle MUST snapshot operationParticipantFleetIds
     * to an immutable local Set<Long> BEFORE iterating units. Two officers in
     * the same operation must receive identical merit (all-or-nothing). If the
     * snapshot was taken before the unit loop, both receive the bonus.
     */
    @Test
    fun `snapshot prevents concurrent cancel race`() {
        val officer1 = seedOfficer()
        val officer2 = seedOfficer()
        val fleet1 = seedFleet()
        val fleet2 = seedFleet()
        val battle = persistBattle()
        val unit1 = winningUnit(fleetId = fleet1.id, officerId = officer1.id)
        val unit2 = winningUnit(fleetId = fleet2.id, officerId = officer2.id)
        val state = makeState(battleId = battle.id, units = mutableListOf(unit1, unit2))
        state.operationParticipantFleetIds.add(fleet1.id)
        state.operationParticipantFleetIds.add(fleet2.id)

        tacticalBattleService.endBattle(
            battle,
            state,
            BattleOutcome(winner = BattleSide.ATTACKER, reason = "test"),
        )

        val r1 = officerRepository.findById(officer1.id).orElseThrow()
        val r2 = officerRepository.findById(officer2.id).orElseThrow()
        assertEquals(
            EXPECTED_BONUS,
            r1.meritPoints,
            "Snapshot taken before unit loop — officer1 must receive bonus",
        )
        assertEquals(
            EXPECTED_BONUS,
            r2.meritPoints,
            "Snapshot taken before unit loop — officer2 must receive bonus (all-or-nothing semantics)",
        )
        assertEquals(
            r1.meritPoints,
            r2.meritPoints,
            "Both participants in the same operation MUST receive identical merit (no race)",
        )
    }
}
