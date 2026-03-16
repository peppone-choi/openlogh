package com.opensam.service

import com.opensam.entity.Record
import com.opensam.repository.RecordRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryServiceTest {
    private val recordRepository = mockk<RecordRepository>(relaxed = true)
    private val yearbookHistoryRepository = mockk<com.opensam.repository.YearbookHistoryRepository>(relaxed = true)
    private val historyService = HistoryService(recordRepository, yearbookHistoryRepository)

    @Test
    fun `logWorldHistory should save record with correct fields`() {
        val recordSlot = slot<Record>()
        every { recordRepository.save(capture(recordSlot)) } returns mockk()

        historyService.logWorldHistory(1L, "test message", 187, 1)

        verify { recordRepository.save(any()) }
        val captured = recordSlot.captured
        assertEquals(1L, captured.worldId)
        assertEquals("world_history", captured.recordType)
        assertEquals(187.toShort(), captured.year)
        assertEquals(1.toShort(), captured.month)
        assertEquals("test message", captured.payload["message"])
    }

    @Test
    fun `logNationHistory should save record with correct fields`() {
        val recordSlot = slot<Record>()
        every { recordRepository.save(capture(recordSlot)) } returns mockk()

        historyService.logNationHistory(1L, 5L, "nation message", 187, 2)

        verify { recordRepository.save(any()) }
        val captured = recordSlot.captured
        assertEquals(1L, captured.worldId)
        assertEquals(5L, captured.destId)
        assertEquals("nation_history", captured.recordType)
        assertEquals(187.toShort(), captured.year)
        assertEquals(2.toShort(), captured.month)
        assertEquals("nation message", captured.payload["message"])
    }

    @Test
    fun `getWorldHistory should query record repository`() {
        val mockRecords = listOf(mockk<Record>())
        every { recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_history") } returns mockRecords

        val result = historyService.getWorldHistory(1L)

        assertEquals(mockRecords, result)
        verify { recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_history") }
    }

    @Test
    fun `getWorldRecords should query record repository`() {
        val mockRecords = listOf(mockk<Record>())
        every { recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_record") } returns mockRecords

        val result = historyService.getWorldRecords(1L)

        assertEquals(mockRecords, result)
        verify { recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_record") }
    }

    @Test
    fun `getGeneralRecords should query record repository`() {
        val mockRecords = listOf(mockk<Record>())
        every { recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(10L, "general_action") } returns mockRecords

        val result = historyService.getGeneralRecords(10L)

        assertEquals(mockRecords, result)
        verify { recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(10L, "general_action") }
    }

    @Test
    fun `getByYearMonth should query record repository`() {
        val mockRecords = listOf(mockk<Record>())
        every { recordRepository.findByWorldIdAndYearAndMonth(1L, 187, 1) } returns mockRecords

        val result = historyService.getByYearMonth(1L, 187, 1)

        assertEquals(mockRecords, result)
        verify { recordRepository.findByWorldIdAndYearAndMonth(1L, 187.toShort(), 1.toShort()) }
    }
}
