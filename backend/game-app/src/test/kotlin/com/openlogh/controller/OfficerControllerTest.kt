package com.openlogh.controller

import com.openlogh.dto.CreateOfficerRequest
import com.openlogh.dto.SelectNpcRequest
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.AppUserRepository
import com.openlogh.service.OfficerService
import com.openlogh.service.WorldService
import com.openlogh.entity.WorldState
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

class OfficerControllerTest {

    private lateinit var officerService: OfficerService
    private lateinit var officerRepository: OfficerRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var worldService: WorldService
    private lateinit var controller: OfficerController

    @BeforeEach
    fun setUp() {
        officerService = mock(OfficerService::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        worldService = mock(WorldService::class.java)
        controller = OfficerController(officerService, officerRepository, appUserRepository, worldService)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("testuser", null)
    }

    private fun closedWorld(): WorldState {
        val future = java.time.OffsetDateTime.now().plusHours(2).toString()
        return WorldState(
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
    fun `createOfficer returns 403 when world is closed`() {
        val world = closedWorld()
        `when`(worldService.getWorld(1.toShort())).thenReturn(world)
        `when`(worldService.getGamePhase(world)).thenReturn(WorldService.PHASE_CLOSED)

        val result = controller.createOfficer(1L, CreateOfficerRequest(name = "test", factionId = 1))

        assertEquals(HttpStatus.FORBIDDEN, result.statusCode)
    }

    // selectNpc / selectFromPool methods were moved to SelectPoolController
}
