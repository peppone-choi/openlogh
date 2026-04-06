package com.openlogh.service

import com.openlogh.entity.Record
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.RecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime

class RecordServiceTest {
    private lateinit var recordRepository: RecordRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var service: RecordService

    @BeforeEach
    fun setUp() {
        recordRepository = mock(RecordRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        service = RecordService(recordRepository, officerRepository)
    }

    @Test
    fun `getGeneralActions returns records`() {
        val records = listOf(
            record(1, RecordService.GENERAL_ACTION, destId = 10L),
            record(2, RecordService.GENERAL_ACTION, destId = 10L)
        )
        `when`(recordRepository.findByDestIdAndRecordTypeOrderByCreatedAtDesc(10L, RecordService.GENERAL_ACTION))
            .thenReturn(records)

        val result = service.getGeneralActions(10L)

        assertEquals(2, result.size)
    }

    @Test
    fun `getWorldRecords returns records`() {
        val records = listOf(
            record(1, RecordService.WORLD_RECORD),
            record(2, RecordService.WORLD_RECORD)
        )
        `when`(recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(1L, RecordService.WORLD_RECORD))
            .thenReturn(records)

        val result = service.getWorldRecords(1L)

        assertEquals(2, result.size)
    }

    @Test
    fun `saveRecord creates record`() {
        val payload = mapOf("message" to "test message")
        val saved = record(1, RecordService.GENERAL_ACTION, destId = 10L)
        
        `when`(recordRepository.save(org.mockito.ArgumentMatchers.any(Record::class.java)))
            .thenReturn(saved)

        val result = service.saveRecord(
            worldId = 1L,
            recordType = RecordService.GENERAL_ACTION,
            srcId = 5L,
            destId = 10L,
            year = 220,
            month = 1,
            payload = payload
        )

        assertEquals(saved.id, result.id)
    }

    private fun record(
        id: Long,
        recordType: String,
        sessionId: Long = 1L,
        srcId: Long? = 1L,
        destId: Long? = null,
        year: Int = 220,
        month: Int = 1
    ): Record {
        return Record(
            id = id,
            sessionId = sessionId,
            recordType = recordType,
            srcId = srcId,
            destId = destId,
            year = year,
            month = month,
            payload = mutableMapOf("message" to "test"),
            createdAt = OffsetDateTime.now()
        )
    }
}
