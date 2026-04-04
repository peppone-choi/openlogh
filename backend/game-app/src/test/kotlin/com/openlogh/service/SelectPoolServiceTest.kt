package com.openlogh.service

import com.openlogh.entity.SelectPool
import com.openlogh.repository.SelectPoolRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

class SelectPoolServiceTest {

    private lateinit var repo: SelectPoolRepository
    private lateinit var service: SelectPoolService

    @BeforeEach
    fun setUp() {
        repo = mock(SelectPoolRepository::class.java)
        service = SelectPoolService(repo)
    }

    @Test
    fun `create saves pool entry with worldId and uniqueName`() {
        val pool = SelectPool(id = 1, worldId = 10, uniqueName = "test")
        `when`(repo.save(any(SelectPool::class.java))).thenReturn(pool)

        val result = service.create(10L, "test", mapOf("generalName" to "조조"))
        assertEquals("test", result.uniqueName)
        verify(repo).save(any(SelectPool::class.java))
    }

    @Test
    fun `delete returns false when not found`() {
        `when`(repo.existsById(999L)).thenReturn(false)
        assertFalse(service.delete(999L))
        verify(repo, never()).deleteById(999L)
    }

    @Test
    fun `delete returns true and deletes when found`() {
        `when`(repo.existsById(1L)).thenReturn(true)
        assertTrue(service.delete(1L))
        verify(repo).deleteById(1L)
    }

    @Test
    fun `update returns null when not found`() {
        `when`(repo.findById(999L)).thenReturn(Optional.empty())
        assertNull(service.update(999L, mapOf("generalName" to "유비")))
    }

    @Test
    fun `reserve returns null when already picked`() {
        val pool = SelectPool(id = 1, worldId = 10, uniqueName = "test", generalId = 100L)
        `when`(repo.findById(1L)).thenReturn(Optional.of(pool))
        assertNull(service.reserve(1L, 42L))
    }
}
