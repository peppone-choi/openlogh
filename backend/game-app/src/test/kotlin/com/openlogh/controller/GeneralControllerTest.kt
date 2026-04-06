package com.openlogh.controller

import com.openlogh.dto.CreateGeneralRequest
import com.openlogh.dto.SelectNpcRequest
import com.openlogh.service.FrontInfoService
import com.openlogh.service.OfficerService
import com.openlogh.service.WorldService
import com.openlogh.entity.SessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.any
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class GeneralControllerTest {

    private lateinit var officerService: OfficerService
    private lateinit var frontInfoService: FrontInfoService
    private lateinit var worldService: WorldService
    private lateinit var controller: GeneralController

    @BeforeEach
    fun setUp() {
        officerService = mock(OfficerService::class.java)
        frontInfoService = mock(FrontInfoService::class.java)
        worldService = mock(WorldService::class.java)
        controller = GeneralController(officerService, frontInfoService, worldService)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("testuser", null)
    }

    private fun closedWorld(): SessionState {
        val future = java.time.OffsetDateTime.now().plusHours(2).toString()
        return SessionState(
            id = 1,
            name = "test",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 300,
            config = mutableMapOf("startTime" to future),
        )
    }

    @Test
    fun `createGeneral returns 403 when world is closed`() {
        val world = closedWorld()
        `when`(worldService.getWorld(1.toShort())).thenReturn(world)
        `when`(worldService.getGamePhase(world)).thenReturn(WorldService.PHASE_CLOSED)

        val result = controller.createGeneral(1L, CreateGeneralRequest(name = "test", factionId = 1))

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
    }

    @Test
    fun `selectNpc returns 403 when world is closed`() {
        val world = closedWorld()
        `when`(worldService.getWorld(1.toShort())).thenReturn(world)
        `when`(worldService.getGamePhase(world)).thenReturn(WorldService.PHASE_CLOSED)

        val result = controller.selectNpc(1L, SelectNpcRequest(officerId = 1))

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
    }

    @Test
    fun `selectFromPool returns 403 when world is closed`() {
        val world = closedWorld()
        `when`(worldService.getWorld(1.toShort())).thenReturn(world)
        `when`(worldService.getGamePhase(world)).thenReturn(WorldService.PHASE_CLOSED)

        val result = controller.selectFromPool(1L, SelectNpcRequest(officerId = 1))

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
    }
}
