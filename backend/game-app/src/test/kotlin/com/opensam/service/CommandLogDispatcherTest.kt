package com.opensam.service

import com.opensam.entity.Record
import com.opensam.repository.RecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class CommandLogDispatcherTest {
    private lateinit var recordRepository: RecordRepository
    private lateinit var dispatcher: CommandLogDispatcher

    @BeforeEach
    fun setUp() {
        recordRepository = mock(RecordRepository::class.java)
        dispatcher = CommandLogDispatcher(recordRepository)
    }

    @Test
    fun `dispatchLogs saves general_action for plain log`() {
        val logs = listOf("장수가 훈련을 실시하였습니다")
        
        dispatcher.dispatchLogs(
            worldId = 1L,
            generalId = 10L,
            nationId = 5L,
            year = 220,
            month = 1,
            logs = logs
        )

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Record>>
        verify(recordRepository).saveAll(captor.capture())
        
        val savedRecords = captor.value
        assertEquals(1, savedRecords.size)
        assertEquals(RecordService.GENERAL_ACTION, savedRecords[0].recordType)
        assertEquals(10L, savedRecords[0].destId)
    }

    @Test
    fun `dispatchLogs saves world_history for _globalHistory tag`() {
        val logs = listOf("_globalHistory:천하에 대사건이 발생하였습니다")
        
        dispatcher.dispatchLogs(
            worldId = 1L,
            generalId = 10L,
            nationId = 5L,
            year = 220,
            month = 1,
            logs = logs
        )

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Record>>
        verify(recordRepository).saveAll(captor.capture())
        
        val savedRecords = captor.value
        assertEquals(1, savedRecords.size)
        assertEquals(RecordService.WORLD_HISTORY, savedRecords[0].recordType)
        assertEquals(null, savedRecords[0].destId)
    }

    @Test
    fun `dispatchLogs saves general_record for _history tag`() {
        val logs = listOf("_history:역사에 기록될 업적을 달성하였습니다")
        
        dispatcher.dispatchLogs(
            worldId = 1L,
            generalId = 10L,
            nationId = 5L,
            year = 220,
            month = 1,
            logs = logs
        )

        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Record>>
        verify(recordRepository).saveAll(captor.capture())
        
        val savedRecords = captor.value
        assertEquals(1, savedRecords.size)
        assertEquals(RecordService.GENERAL_RECORD, savedRecords[0].recordType)
        assertEquals(10L, savedRecords[0].destId)
    }
}
