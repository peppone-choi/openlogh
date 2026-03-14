package com.opensam.service

import com.opensam.entity.General
import com.opensam.repository.GeneralRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class PermissionServiceTest {

    private lateinit var generalRepository: GeneralRepository
    private lateinit var service: PermissionService

    @BeforeEach
    fun setUp() {
        generalRepository = mock(GeneralRepository::class.java)
        service = PermissionService(generalRepository)
    }

    private fun general(
        id: Long,
        userId: Long? = null,
        nationId: Long = 1,
        officerLevel: Short = 1,
        permission: String = "normal",
    ): General {
        return General(
            id = id,
            worldId = 1,
            userId = userId,
            name = "장수$id",
            nationId = nationId,
            cityId = 1,
            officerLevel = officerLevel,
            permission = permission,
            turnTime = OffsetDateTime.now(),
        )
    }

    @Test
    fun `setPermission applies legacy candidate filters`() {
        val leader = general(id = 1, userId = 10, nationId = 1, officerLevel = 20)
        val eligible = general(id = 2, nationId = 1)
        val noChief = general(id = 3, nationId = 1).apply {
            penalty = mutableMapOf("noChief" to true)
        }
        val existingAuditor = general(id = 4, nationId = 1, permission = "auditor")
        val ruler = general(id = 5, nationId = 1, officerLevel = 20)

        `when`(generalRepository.findByUserId(10L)).thenReturn(listOf(leader))
        `when`(generalRepository.findByNationId(1L)).thenReturn(listOf(leader, eligible, noChief, existingAuditor, ruler))

        val result = service.setPermission(
            userId = 10,
            nationId = 1,
            isAmbassador = false,
            generalIds = listOf(2, 3, 4, 5),
        )

        assertTrue(result.result)
        assertEquals("success", result.reason)
        assertEquals("auditor", eligible.permission)
        assertEquals("normal", noChief.permission)
        assertEquals("auditor", existingAuditor.permission)
        assertEquals("normal", ruler.permission)
        verify(generalRepository).save(eligible)
        verify(generalRepository, times(2)).save(existingAuditor)
    }

    @Test
    fun `setPermission clears existing target permission when selection is empty`() {
        val leader = general(id = 1, userId = 10, nationId = 1, officerLevel = 20)
        val ambassador = general(id = 2, nationId = 1, permission = "ambassador")

        `when`(generalRepository.findByUserId(10L)).thenReturn(listOf(leader))
        `when`(generalRepository.findByNationId(1L)).thenReturn(listOf(leader, ambassador))

        val result = service.setPermission(
            userId = 10,
            nationId = 1,
            isAmbassador = true,
            generalIds = emptyList(),
        )

        assertTrue(result.result)
        assertEquals("normal", ambassador.permission)
        verify(generalRepository).save(ambassador)
    }

    @Test
    fun `setPermission rejects more than two ambassadors`() {
        val leader = general(id = 1, userId = 10, nationId = 1, officerLevel = 20)

        `when`(generalRepository.findByUserId(10L)).thenReturn(listOf(leader))

        val result = service.setPermission(
            userId = 10,
            nationId = 1,
            isAmbassador = true,
            generalIds = listOf(2, 3, 4),
        )

        assertFalse(result.result)
        assertEquals("외교권자는 최대 둘까지만 설정 가능합니다.", result.reason)
        verify(generalRepository, never()).findByNationId(1L)
    }
}
