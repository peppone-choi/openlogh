package com.openlogh.service

import com.openlogh.entity.Record
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.YearbookHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class HistoryServiceTest {
    private lateinit var recordRepository: RecordRepository
    private lateinit var yearbookHistoryRepository: YearbookHistoryRepository
    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        recordRepository = mock(RecordRepository::class.java)
        yearbookHistoryRepository = mock(YearbookHistoryRepository::class.java)
        historyService = HistoryService(recordRepository, yearbookHistoryRepository)
    }

    @Test
    fun `logWorldHistory should save record with correct fields`() {
        val recordCaptor = ArgumentCaptor.forClass(Record::class.java)
        `when`(recordRepository.save(recordCaptor.capture())).thenReturn(mock(Record::class.java))

        historyService.logWorldHistory(1L, "test message", 187, 1)

        verify(recordRepository).save(recordCaptor.capture())
        val captured = recordCaptor.value
        assertEquals(1L, captured.worldId)
        assertEquals("world_history", captured.recordType)
        assertEquals(187, captured.year)
        assertEquals(1, captured.month)
        assertEquals("test message", captured.payload["message"])
    }

    @Test
    fun `logNationHistory should save record with correct fields`() {
        val recordCaptor = ArgumentCaptor.forClass(Record::class.java)
        `when`(recordRepository.save(recordCaptor.capture())).thenReturn(mock(Record::class.java))

        historyService.logNationHistory(1L, 5L, "nation message", 187, 2)

        verify(recordRepository).save(recordCaptor.capture())
        val captured = recordCaptor.value
        assertEquals(1L, captured.worldId)
        assertEquals(5L, captured.destId)
        assertEquals("nation_history", captured.recordType)
        assertEquals(187, captured.year)
        assertEquals(2, captured.month)
        assertEquals("nation message", captured.payload["message"])
    }

    @Test
    fun `getWorldHistory should query record repository`() {
        val mockRecords = listOf(mock(Record::class.java))
        `when`(recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_history"))
            .thenReturn(mockRecords)

        val result = historyService.getWorldHistory(1L)

        assertEquals(mockRecords, result)
        verify(recordRepository).findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_history")
    }

    @Test
    fun `getWorldRecords should query record repository`() {
        val mockRecords = listOf(mock(Record::class.java))
        `when`(recordRepository.findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_record"))
            .thenReturn(mockRecords)

        val result = historyService.getWorldRecords(1L)

        assertEquals(mockRecords, result)
        verify(recordRepository).findByWorldIdAndRecordTypeOrderByCreatedAtDesc(1L, "world_record")
    }

    @Test
    fun `getGeneralRecords should query record repository`() {
        val mockRecords = listOf(mock(Record::class.java))
        `when`(recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(10L, "general_action"))
            .thenReturn(mockRecords)

        val result = historyService.getGeneralRecords(10L)

        assertEquals(mockRecords, result)
        verify(recordRepository).findByDestIdAndRecordTypeOrderByCreatedAtDesc(10L, "general_action")
    }

    @Test
    fun `getByYearMonth should query record repository`() {
        val mockRecords = listOf(mock(Record::class.java))
        `when`(recordRepository.findByWorldIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(
            1L, listOf("world_history", "world_record"), 187, 1
        )).thenReturn(mockRecords)

        val result = historyService.getByYearMonth(1L, 187, 1)

        assertEquals(mockRecords, result)
        verify(recordRepository).findByWorldIdAndRecordTypeInAndYearAndMonthOrderByCreatedAtDesc(
            1L, listOf("world_history", "world_record"), 187, 1
        )
    }
}
