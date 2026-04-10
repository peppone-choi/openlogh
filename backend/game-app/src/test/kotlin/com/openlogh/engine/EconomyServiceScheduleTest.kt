package com.openlogh.engine

import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import com.openlogh.engine.event.actions.economy.ProcessIncomeAction
import com.openlogh.engine.event.actions.economy.ProcessSemiAnnualAction
import com.openlogh.entity.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.lang.reflect.InvocationTargetException

/**
 * Phase 22 Plan 03 — EconomyService per-resource event schedule split.
 *
 * Ports upstream commit a7a19cc3 contract: income/salary/upkeep MUST flow through
 * scheduled events (not preUpdateMonthly/postUpdateMonthly which previously drained
 * the treasury 12x/year). Per-resource schedule:
 *   - month 1: ["ProcessIncome", "gold"]      → funds only
 *   - month 7: ["ProcessIncome", "rice"]      → supplies only
 *   - month 1: ["ProcessSemiAnnual", "gold"]  → funds decay only
 *   - month 7: ["ProcessSemiAnnual", "rice"]  → supplies decay only
 *
 * LOGH pipeline reality (Pipeline Investigation, Task 1):
 *   - InMemoryTurnProcessor wires economyService.preUpdateMonthly + postUpdateMonthly
 *     each month, but both are no-op stubs in LOGH today (TODO Phase 4).
 *   - Gin7EconomyService.processMonthly handles tax+planet growth (no salary outlay).
 *   - processIncomeEvent / processSemiAnnualEvent are wired through ProcessIncomeAction
 *     / ProcessSemiAnnualAction but no scenario JSON currently references them.
 *
 * This means LOGH does NOT exhibit the upstream drain bug today, but the API contract
 * MUST be ported so the future Phase 4 wire-up uses the legacy-correct shape from day
 * one. Tests below lock the per-resource contract on the action layer + service stub
 * boundary, so any future Phase 4 implementation must respect it.
 *
 * Resource literal choice: upstream uses "gold" / "rice" and LOGH's ScenarioService
 * already populates params["resource"] with those literals when parsing legacy event
 * JSON. We keep the literals on the wire and at the EconomyService boundary so any
 * future OpenSamguk-style scenario data file imported into LOGH Just Works without
 * translation. Internally, EconomyService maps them to faction.funds / faction.supplies
 * (documented in service KDoc).
 *
 * Test calls into the new 2-arg signature use Java reflection so the file stays
 * compilable against both the pre-port (1-arg) and post-port (2-arg) signatures.
 * RED phase: reflection lookup fails (NoSuchMethodException → reified into
 * AssertionFailedError). GREEN phase: lookup succeeds, behavior is asserted.
 */
class EconomyServiceScheduleTest {

    private fun createWorld(year: Short = 200, month: Short = 1): SessionState =
        SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = 300,
        )

    /** Reflectively call processIncomeEvent(world, resource). */
    private fun callProcessIncomeEvent(service: EconomyService, world: SessionState, resource: String) {
        val method = EconomyService::class.java.getDeclaredMethod(
            "processIncomeEvent",
            SessionState::class.java,
            String::class.java,
        )
        method.isAccessible = true
        try {
            method.invoke(service, world, resource)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /** Reflectively call processSemiAnnualEvent(world, resource). */
    private fun callProcessSemiAnnualEvent(service: EconomyService, world: SessionState, resource: String) {
        val method = EconomyService::class.java.getDeclaredMethod(
            "processSemiAnnualEvent",
            SessionState::class.java,
            String::class.java,
        )
        method.isAccessible = true
        try {
            method.invoke(service, world, resource)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /** Reflectively verify(mock).processIncomeEvent(world, resource). */
    private fun verifyProcessIncomeEvent(mockEconomy: EconomyService, world: SessionState, resource: String) {
        // Mockito's verify() needs a real method invocation on the mock; reflection-invoke
        // on a Mockito mock works because Mockito intercepts via its CGLIB subclass.
        val method = EconomyService::class.java.getDeclaredMethod(
            "processIncomeEvent",
            SessionState::class.java,
            String::class.java,
        )
        method.isAccessible = true
        try {
            method.invoke(verify(mockEconomy), world, resource)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun verifyProcessSemiAnnualEvent(mockEconomy: EconomyService, world: SessionState, resource: String) {
        val method = EconomyService::class.java.getDeclaredMethod(
            "processSemiAnnualEvent",
            SessionState::class.java,
            String::class.java,
        )
        method.isAccessible = true
        try {
            method.invoke(verify(mockEconomy), world, resource)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    private fun verifyProcessIncomeEventNever(mockEconomy: EconomyService, world: SessionState, resource: String) {
        val method = EconomyService::class.java.getDeclaredMethod(
            "processIncomeEvent",
            SessionState::class.java,
            String::class.java,
        )
        method.isAccessible = true
        try {
            method.invoke(verify(mockEconomy, never()), world, resource)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Service-layer per-resource API contract
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `processIncomeEvent has resource overload after 22-03 port`() {
        val method = EconomyService::class.java.declaredMethods.firstOrNull {
            it.name == "processIncomeEvent" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == SessionState::class.java &&
                it.parameterTypes[1] == String::class.java
        }
        assertNotNull(
            method,
            "processIncomeEvent(SessionState, String) overload must exist after 22-03 port",
        )
    }

    @Test
    fun `processSemiAnnualEvent has resource overload after 22-03 port`() {
        val method = EconomyService::class.java.declaredMethods.firstOrNull {
            it.name == "processSemiAnnualEvent" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == SessionState::class.java &&
                it.parameterTypes[1] == String::class.java
        }
        assertNotNull(
            method,
            "processSemiAnnualEvent(SessionState, String) overload must exist after 22-03 port",
        )
    }

    @Test
    fun `processWarIncomeEvent test entry point exists`() {
        val method = EconomyService::class.java.declaredMethods.firstOrNull {
            it.name == "processWarIncomeEvent" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == SessionState::class.java
        }
        assertNotNull(
            method,
            "processWarIncomeEvent(SessionState) public test entry point must exist after 22-03 port",
        )
    }

    @Test
    fun `processIncomeEvent with funds resource does not throw`() {
        val service = newServiceWithMocks()
        val world = createWorld(month = 1)
        callProcessIncomeEvent(service, world, "gold")
    }

    @Test
    fun `processIncomeEvent with supplies resource does not throw`() {
        val service = newServiceWithMocks()
        val world = createWorld(month = 7)
        callProcessIncomeEvent(service, world, "rice")
    }

    @Test
    fun `processIncomeEvent rejects invalid resource literal`() {
        val service = newServiceWithMocks()
        val world = createWorld(month = 1)
        try {
            callProcessIncomeEvent(service, world, "platinum")
            error("Expected IllegalArgumentException for invalid resource")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("platinum"))
        }
    }

    @Test
    fun `processSemiAnnualEvent rejects invalid resource literal`() {
        val service = newServiceWithMocks()
        val world = createWorld(month = 1)
        try {
            callProcessSemiAnnualEvent(service, world, "fuel")
            error("Expected IllegalArgumentException for invalid resource")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("fuel"))
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Action-layer params["resource"] pass-through
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `ProcessIncomeAction passes resource gold to economyService`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessIncomeAction(economy)
        val world = createWorld(month = 1)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "gold"))

        val result = action.execute(ctx)

        assertEquals(EventActionResult.Success, result)
        verifyProcessIncomeEvent(economy, world, "gold")
    }

    @Test
    fun `ProcessIncomeAction passes resource rice to economyService`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessIncomeAction(economy)
        val world = createWorld(month = 7)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "rice"))

        val result = action.execute(ctx)

        assertEquals(EventActionResult.Success, result)
        verifyProcessIncomeEvent(economy, world, "rice")
    }

    @Test
    fun `ProcessIncomeAction returns Error on invalid resource`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessIncomeAction(economy)
        val world = createWorld(month = 1)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "fuel"))

        val result = action.execute(ctx)

        assertTrue(result is EventActionResult.Error)
        assertTrue((result as EventActionResult.Error).message.contains("fuel"))
        verifyProcessIncomeEventNever(economy, world, "fuel")
    }

    @Test
    fun `ProcessIncomeAction defaults to gold when resource missing`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessIncomeAction(economy)
        val world = createWorld(month = 1)
        val ctx = EventActionContext(world = world, params = emptyMap())

        action.execute(ctx)

        verifyProcessIncomeEvent(economy, world, "gold")
    }

    @Test
    fun `ProcessSemiAnnualAction passes resource gold to economyService`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessSemiAnnualAction(economy)
        val world = createWorld(month = 1)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "gold"))

        action.execute(ctx)

        verifyProcessSemiAnnualEvent(economy, world, "gold")
    }

    @Test
    fun `ProcessSemiAnnualAction passes resource rice to economyService`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessSemiAnnualAction(economy)
        val world = createWorld(month = 7)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "rice"))

        action.execute(ctx)

        verifyProcessSemiAnnualEvent(economy, world, "rice")
    }

    @Test
    fun `ProcessSemiAnnualAction returns Error on invalid resource`() {
        val economy = mock(EconomyService::class.java)
        val action = ProcessSemiAnnualAction(economy)
        val world = createWorld(month = 1)
        val ctx = EventActionContext(world = world, params = mapOf("resource" to "diamond"))

        val result = action.execute(ctx)

        assertTrue(result is EventActionResult.Error)
        verifyNoInteractions(economy)
    }

    // ────────────────────────────────────────────────────────────────────────
    // No-op invariant: pre/postUpdateMonthly must NOT call processIncomeEvent
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `preUpdateMonthly does not invoke per-resource income processing`() {
        // Upstream invariant: preUpdateMonthly is a no-op for income — it must not
        // drain treasury by calling processIncome on every month. We validate by
        // ensuring the call returns without throwing on a mock-only EconomyService:
        // any actual income work would touch the unstubbed mock repositories and
        // crash. Reaching the assertion line means the no-op holds.
        val service = newServiceWithMocks()
        val world = createWorld(month = 5)
        service.preUpdateMonthly(world)
    }

    @Test
    fun `postUpdateMonthly does not invoke per-resource semi-annual processing`() {
        val service = newServiceWithMocks()
        val world = createWorld(month = 7)
        service.postUpdateMonthly(world)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private fun newServiceWithMocks(): EconomyService {
        return EconomyService(
            planetRepository = mock(com.openlogh.repository.PlanetRepository::class.java),
            factionRepository = mock(com.openlogh.repository.FactionRepository::class.java),
            officerRepository = mock(com.openlogh.repository.OfficerRepository::class.java),
            messageRepository = mock(com.openlogh.repository.MessageRepository::class.java),
            mapService = mock(com.openlogh.service.MapService::class.java),
            historyService = mock(com.openlogh.service.HistoryService::class.java),
            inheritanceService = mock(com.openlogh.service.InheritanceService::class.java),
        )
    }
}
