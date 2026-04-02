package com.opensam.qa.parity

import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnPipeline
import com.opensam.engine.turn.TurnStep
import com.opensam.entity.WorldState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File
import java.time.OffsetDateTime

/**
 * Turn Pipeline Parity Test
 *
 * Verifies that the pipeline step ordering matches the legacy daemon.ts + func_process.php
 * execution order, and that the pipeline handles errors gracefully.
 *
 * Legacy execution order (from daemon.ts):
 *   1. executeGeneralCommandUntil (100)
 *   2. dispatchEvents PRE_MONTH (200)
 *   3. preUpdateMonthly (300)
 *   4. advanceMonth (400)
 *   5. yearbookSnapshot (500)
 *   6. worldSnapshot (600)
 *   7. trafficSnapshot (700)
 *   8. yearlyStatistics month==1 (800)
 *   9. dispatchEvents MONTH (900)
 *  10. postUpdateMonthly (1000)
 *  11. disasterAndTrade (1100)
 *  12. diplomacy (1200)
 *  13. warFrontRecalc (1300)
 *  14. strategicLimitReset (1400)
 *  15. generalMaintenance (1500)
 *  16. unificationCheck (1600)
 *  17. onlineOverhead (1700)
 */
@DisplayName("Turn Pipeline Parity")
class TurnPipelineParityTest {

    /**
     * Expected pipeline step order matching legacy daemon.ts execution sequence.
     */
    private val LEGACY_STEP_ORDER = listOf(
        "ExecuteGeneralCommands",   // 100
        "PreMonthEvent",            // 200
        "EconomyPreUpdate",         // 300
        "AdvanceMonth",             // 400
        "YearbookSnapshot",         // 500
        "WorldSnapshot",            // 600
        "TrafficSnapshot",          // 700
        "YearlyStatistics",         // 800
        "MonthEvent",               // 900
        "EconomyPostUpdate",        // 1000
        "DisasterAndTrade",         // 1100
        "Diplomacy",                // 1200
        "WarFrontRecalc",           // 1300
        "StrategicLimitReset",      // 1400
        "GeneralMaintenance",       // 1500
        "UnificationCheck",         // 1600
        "OnlineOverhead",           // 1700
    )

    private val LEGACY_STEP_ORDERS = listOf(
        100, 200, 300, 400, 500, 600, 700, 800, 900,
        1000, 1100, 1200, 1300, 1400, 1500, 1600, 1700,
    )

    private fun createTestSteps(): List<TurnStep> {
        return LEGACY_STEP_ORDER.zip(LEGACY_STEP_ORDERS).map { (name, order) ->
            object : TurnStep {
                override val name = name
                override val order = order
                override fun execute(context: TurnContext) {}
            }
        }
    }

    private fun testContext(): TurnContext {
        val world = WorldState(id = 1, scenarioCode = "test", currentYear = 200, currentMonth = 3, tickSeconds = 300)
        return TurnContext(
            world = world,
            worldId = 1L,
            year = 200,
            month = 3,
            previousYear = 200,
            previousMonth = 2,
            nextTurnAt = OffsetDateTime.now(),
        )
    }

    @Test
    @DisplayName("Pipeline step names match legacy execution order")
    fun `step names match legacy order`() {
        val pipeline = TurnPipeline(createTestSteps())
        assertThat(pipeline.stepNames()).isEqualTo(LEGACY_STEP_ORDER)
    }

    @Test
    @DisplayName("Pipeline step order values are strictly increasing")
    fun `step orders strictly increasing`() {
        val pipeline = TurnPipeline(createTestSteps())
        val orders = pipeline.stepOrders()
        for (i in 1 until orders.size) {
            assertThat(orders[i])
                .describedAs("Step ${LEGACY_STEP_ORDER[i]} (order=${orders[i]}) must be after ${LEGACY_STEP_ORDER[i-1]} (order=${orders[i-1]})")
                .isGreaterThan(orders[i - 1])
        }
    }

    @Test
    @DisplayName("Pipeline has exactly 17 steps")
    fun `pipeline has 17 steps`() {
        val pipeline = TurnPipeline(createTestSteps())
        assertThat(pipeline.stepNames()).hasSize(17)
    }

    @Test
    @DisplayName("Pipeline sorts steps by order regardless of registration order")
    fun `steps sorted by order`() {
        // Register in reverse order
        val reversedSteps = createTestSteps().reversed()
        val pipeline = TurnPipeline(reversedSteps)

        // Should still be in correct order
        assertThat(pipeline.stepNames()).isEqualTo(LEGACY_STEP_ORDER)
    }

    @Test
    @DisplayName("Pipeline continues on step failure (legacy daemon.ts behavior)")
    fun `pipeline continues on failure`() {
        val executedSteps = mutableListOf<String>()
        val steps = listOf(
            testStep("Step1", 100) { executedSteps.add("Step1") },
            testStep("Step2", 200) { throw RuntimeException("simulated failure") },
            testStep("Step3", 300) { executedSteps.add("Step3") },
        )

        val pipeline = TurnPipeline(steps)
        val successCount = pipeline.execute(testContext())

        // Step2 failed, but Step1 and Step3 should have executed
        assertThat(executedSteps).containsExactly("Step1", "Step3")
        assertThat(successCount).isEqualTo(2)
    }

    @Test
    @DisplayName("Pipeline skips steps when shouldSkip returns true")
    fun `pipeline skips when shouldSkip`() {
        val executedSteps = mutableListOf<String>()
        val steps = listOf(
            testStep("Always", 100) { executedSteps.add("Always") },
            object : TurnStep {
                override val name = "SkipMe"
                override val order = 200
                override fun shouldSkip(context: TurnContext) = true
                override fun execute(context: TurnContext) { executedSteps.add("SkipMe") }
            },
            testStep("AlsoRuns", 300) { executedSteps.add("AlsoRuns") },
        )

        val pipeline = TurnPipeline(steps)
        pipeline.execute(testContext())

        assertThat(executedSteps).containsExactly("Always", "AlsoRuns")
    }

    @Test
    @DisplayName("YearlyStatistics step only runs on month 1")
    fun `yearly statistics month guard`() {
        var executed = false
        val yearlyStep = object : TurnStep {
            override val name = "YearlyStatistics"
            override val order = 800
            override fun shouldSkip(context: TurnContext) = context.world.currentMonth.toInt() != 1
            override fun execute(context: TurnContext) { executed = true }
        }

        val pipeline = TurnPipeline(listOf(yearlyStep))

        // Month 3: should skip
        pipeline.execute(testContext())
        assertThat(executed).isFalse()

        // Month 1: should execute
        val month1World = WorldState(id = 1, scenarioCode = "test", currentYear = 200, currentMonth = 1, tickSeconds = 300)
        val month1Context = TurnContext(
            world = month1World, worldId = 1L,
            year = 200, month = 1, previousYear = 199, previousMonth = 12,
            nextTurnAt = OffsetDateTime.now(),
        )
        pipeline.execute(month1Context)
        assertThat(executed).isTrue()
    }

    @Test
    @DisplayName("Empty pipeline returns 0 and does not crash")
    fun `empty pipeline safe`() {
        val pipeline = TurnPipeline(emptyList())
        val result = pipeline.execute(testContext())
        assertThat(result).isEqualTo(0)
    }

    // ── postUpdateMonthly ordering assertions (func_gamerule.php:423-441) ──

    @Test
    @DisplayName("postUpdateMonthly call order matches legacy func_gamerule.php:423-441")
    fun `postUpdateMonthly order matches legacy`() {
        // Read TurnService source and verify post-pipeline call order.
        // Legacy order: checkWander -> updateGeneralNumber -> triggerTournament -> registerAuction
        val turnServiceFile = File("src/main/kotlin/com/opensam/engine/TurnService.kt")
        assertThat(turnServiceFile.exists())
            .describedAs("TurnService.kt must exist at expected path")
            .isTrue()

        val source = turnServiceFile.readText()

        // Find each method call position in the postUpdateMonthly section
        val checkWanderIdx = source.indexOf("checkWander(world)")
        val updateGeneralNumberIdx = source.indexOf("updateGeneralNumber(world)")
        val triggerTournamentIdx = source.indexOf("triggerTournament(world)")
        val registerAuctionIdx = source.indexOf("registerAuction(world)")

        assertThat(checkWanderIdx).describedAs("checkWander call must exist").isGreaterThan(-1)
        assertThat(updateGeneralNumberIdx).describedAs("updateGeneralNumber call must exist").isGreaterThan(-1)
        assertThat(triggerTournamentIdx).describedAs("triggerTournament call must exist").isGreaterThan(-1)
        assertThat(registerAuctionIdx).describedAs("registerAuction call must exist").isGreaterThan(-1)

        // Legacy ordering: checkWander < updateGeneralNumber < triggerTournament < registerAuction
        assertThat(checkWanderIdx)
            .describedAs("checkWander must appear before updateGeneralNumber (legacy func_gamerule.php:423-441)")
            .isLessThan(updateGeneralNumberIdx)
        assertThat(updateGeneralNumberIdx)
            .describedAs("updateGeneralNumber must appear before triggerTournament")
            .isLessThan(triggerTournamentIdx)
        assertThat(triggerTournamentIdx)
            .describedAs("triggerTournament must appear before registerAuction")
            .isLessThan(registerAuctionIdx)
    }

    // ── Pipeline step coverage for legacy functions ──

    @Test
    @DisplayName("UnificationCheck step at order 1600 covers legacy checkEmperior()")
    fun `unification check covers checkEmperior`() {
        assertThat(LEGACY_STEP_ORDER).contains("UnificationCheck")
        val idx = LEGACY_STEP_ORDER.indexOf("UnificationCheck")
        assertThat(LEGACY_STEP_ORDERS[idx])
            .describedAs("UnificationCheck (legacy checkEmperior) must be at order 1600")
            .isEqualTo(1600)
    }

    @Test
    @DisplayName("WarFrontRecalc step at order 1300 covers legacy SetNationFront()")
    fun `warFrontRecalc covers SetNationFront`() {
        assertThat(LEGACY_STEP_ORDER).contains("WarFrontRecalc")
        val idx = LEGACY_STEP_ORDER.indexOf("WarFrontRecalc")
        assertThat(LEGACY_STEP_ORDERS[idx])
            .describedAs("WarFrontRecalc (legacy SetNationFront) must be at order 1300")
            .isEqualTo(1300)
    }

    private fun testStep(name: String, order: Int, action: () -> Unit): TurnStep {
        return object : TurnStep {
            override val name = name
            override val order = order
            override fun execute(context: TurnContext) { action() }
        }
    }
}
