package com.openlogh.entity

import com.openlogh.command.CommandExecutor
import com.openlogh.repository.OfficerRepository
import jakarta.persistence.Version
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.dao.OptimisticLockingFailureException
import java.util.*

/**
 * HARD-01: Officer @Version optimistic locking + retry handler tests.
 *
 * Verifies:
 * 1. Officer entity has @Version annotation on 'version' field
 * 2. withOptimisticRetry retries on OptimisticLockingFailureException and succeeds on fresh read
 * 3. withOptimisticRetry propagates exception after 3 failures
 */
class OfficerOptimisticLockTest {

    @Test
    fun `Officer entity has @Version annotated version field of type Long`() {
        val field = Officer::class.java.getDeclaredField("version")
        assertNotNull(field, "Officer must have a 'version' field")
        assertEquals(Long::class.java, field.type, "version field must be Long")

        val versionAnnotation = field.getAnnotation(Version::class.java)
        assertNotNull(versionAnnotation, "version field must have @Version annotation")
    }

    @Test
    fun `withOptimisticRetry retries on conflict and succeeds on second attempt`() {
        val officerRepository = mock(OfficerRepository::class.java)

        val officer1 = Officer(id = 1, sessionId = 1, name = "Test")
        val officer2 = Officer(id = 1, sessionId = 1, name = "Test")

        // First findById returns officer1, save throws conflict
        // Second findById returns officer2, save succeeds
        `when`(officerRepository.findById(1L))
            .thenReturn(Optional.of(officer1))
            .thenReturn(Optional.of(officer2))

        `when`(officerRepository.save(officer1))
            .thenThrow(OptimisticLockingFailureException("Stale version"))

        `when`(officerRepository.save(officer2))
            .thenReturn(officer2)

        val executor = CommandExecutor(
            officerRepository = officerRepository,
        )

        var actionCallCount = 0
        val result = executor.withOptimisticRetry(1L) { officer ->
            actionCallCount++
            officer.pcp -= 1
            "success"
        }

        assertEquals("success", result)
        assertEquals(2, actionCallCount, "Action should be called twice (first attempt + retry)")
        verify(officerRepository, times(2)).findById(1L)
    }

    @Test
    fun `withOptimisticRetry propagates exception after 3 failures`() {
        val officerRepository = mock(OfficerRepository::class.java)

        val officer = Officer(id = 1, sessionId = 1, name = "Test")

        `when`(officerRepository.findById(1L))
            .thenReturn(Optional.of(officer))

        `when`(officerRepository.save(any(Officer::class.java)))
            .thenThrow(OptimisticLockingFailureException("Stale version"))

        val executor = CommandExecutor(
            officerRepository = officerRepository,
        )

        assertThrows(OptimisticLockingFailureException::class.java) {
            executor.withOptimisticRetry(1L) { it.pcp -= 1 }
        }

        // Should have attempted 3 times
        verify(officerRepository, times(3)).findById(1L)
    }

    @Test
    fun `withOptimisticRetry throws on officer not found`() {
        val officerRepository = mock(OfficerRepository::class.java)

        `when`(officerRepository.findById(999L))
            .thenReturn(Optional.empty())

        val executor = CommandExecutor(
            officerRepository = officerRepository,
        )

        assertThrows(IllegalArgumentException::class.java) {
            executor.withOptimisticRetry(999L) { it.pcp -= 1 }
        }
    }
}
