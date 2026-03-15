package com.opensam.service

import com.opensam.entity.Message
import com.opensam.repository.BoardCommentRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class MessageServiceTest {
    private lateinit var messageRepository: MessageRepository
    private lateinit var boardCommentRepository: BoardCommentRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var service: MessageService

    @BeforeEach
    fun setUp() {
        messageRepository = mock(MessageRepository::class.java)
        boardCommentRepository = mock(BoardCommentRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        val worldStateRepository = mock(com.opensam.repository.WorldStateRepository::class.java)
        service = MessageService(messageRepository, boardCommentRepository, generalRepository, nationRepository, worldStateRepository)
    }

    @Test
    fun `getMessages uses sinceId when provided`() {
        val general = general(id = 5L, nationId = 7L, officerLevel = 4)
        val privateMessages = listOf(message(11, mailboxType = MessageService.MAILBOX_PRIVATE, mailboxCode = "message"))
        val nationalMessages = listOf(message(10, mailboxType = MessageService.MAILBOX_NATIONAL, mailboxCode = "national"))
        val diplomacyMessages = listOf(message(12, mailboxType = MessageService.MAILBOX_DIPLOMACY, mailboxCode = "diplomacy"))

        `when`(generalRepository.findById(5L)).thenReturn(java.util.Optional.of(general))
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MessageService.MAILBOX_PRIVATE, 5L, 9L))
            .thenReturn(privateMessages)
        `when`(messageRepository.findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(7L, MessageService.MAILBOX_NATIONAL, 9L))
            .thenReturn(nationalMessages)
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MessageService.MAILBOX_DIPLOMACY, 7L, 9L))
            .thenReturn(diplomacyMessages)

        val result = service.getMessages(5L, 9L)

        assertEquals(setOf(10L, 11L, 12L), result.map { it.id }.toSet())
        assertEquals(3, result.size)
        verify(messageRepository).findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MessageService.MAILBOX_PRIVATE, 5L, 9L)
        verify(messageRepository).findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(7L, MessageService.MAILBOX_NATIONAL, 9L)
        verify(messageRepository).findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MessageService.MAILBOX_DIPLOMACY, 7L, 9L)
        verify(messageRepository, never()).findByDestIdOrderBySentAtDesc(5L)
    }

    @Test
    fun `getMessages excludes diplomacy for low officer level`() {
        val general = general(id = 5L, nationId = 7L, officerLevel = 3)
        val privateMessages = listOf(message(11, mailboxType = MessageService.MAILBOX_PRIVATE, mailboxCode = "message"))

        `when`(generalRepository.findById(5L)).thenReturn(java.util.Optional.of(general))
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerId(MessageService.MAILBOX_PRIVATE, 5L))
            .thenReturn(privateMessages)
        `when`(messageRepository.findByDestIdAndMailboxTypeOrderBySentAtDesc(7L, MessageService.MAILBOX_NATIONAL))
            .thenReturn(emptyList())

        val result = service.getMessages(5L)

        assertEquals(listOf(11L), result.map { it.id })
        verify(messageRepository, never()).findByDestIdAndMailboxTypeOrderBySentAtDesc(7L, MessageService.MAILBOX_DIPLOMACY)
    }

    @Test
    fun `getMessages defaults to page size on initial load`() {
        val general = general(id = 5L, nationId = 7L, officerLevel = 3)
        val privateMessages = (1L..35L).map { message(it, mailboxType = MessageService.MAILBOX_PRIVATE, mailboxCode = "message") }

        `when`(generalRepository.findById(5L)).thenReturn(java.util.Optional.of(general))
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerId(MessageService.MAILBOX_PRIVATE, 5L))
            .thenReturn(privateMessages)
        `when`(messageRepository.findByDestIdAndMailboxTypeOrderBySentAtDesc(7L, MessageService.MAILBOX_NATIONAL))
            .thenReturn(emptyList())

        val result = service.getMessages(5L)

        assertEquals(30, result.size)
    }

    @Test
    fun `getMessages uses explicit limit when provided`() {
        val general = general(id = 5L, nationId = 7L, officerLevel = 3)
        val privateMessages = (1L..20L).map { message(it, mailboxType = MessageService.MAILBOX_PRIVATE, mailboxCode = "message") }

        `when`(generalRepository.findById(5L)).thenReturn(java.util.Optional.of(general))
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerId(MessageService.MAILBOX_PRIVATE, 5L))
            .thenReturn(privateMessages)
        `when`(messageRepository.findByDestIdAndMailboxTypeOrderBySentAtDesc(7L, MessageService.MAILBOX_NATIONAL))
            .thenReturn(emptyList())

        val result = service.getMessages(5L, limit = 5)

        assertEquals(5, result.size)
    }

    @Test
    fun `getPublicMessages loads older page with limit`() {
        val messages = listOf(message(8), message(7), message(6))
        `when`(messageRepository.findByWorldIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(1L, MessageService.MAILBOX_PUBLIC, 9L))
            .thenReturn(messages)

        val result = service.getPublicMessages(1L, 9L, 2)

        assertEquals(listOf(8L, 7L), result.map { it.id })
    }

    @Test
    fun `getPrivateMessages uses conversation beforeId query`() {
        val messages = listOf(message(4), message(3))
        `when`(messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdLessThan(MessageService.MAILBOX_PRIVATE, 2L, 5L))
            .thenReturn(messages)

        val result = service.getPrivateMessages(2L, 5L, 30)

        assertEquals(listOf(4L, 3L), result.map { it.id })
    }

    @Test
    fun `getPublicMessages defaults to typed page size`() {
        val messages = (1L..35L).map { message(it, mailboxType = MessageService.MAILBOX_PUBLIC, mailboxCode = "public") }
        `when`(messageRepository.findByWorldIdAndMailboxTypeOrderBySentAtDesc(1L, MessageService.MAILBOX_PUBLIC)).thenReturn(messages)

        val result = service.getPublicMessages(1L)

        assertEquals(30, result.size)
    }

    @Test
    fun `getDiplomacyMessages still enforces officer level`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.getDiplomacyMessages(1L, 3, 10L, 15)
        }
    }

    private fun general(id: Long, nationId: Long, officerLevel: Short): com.opensam.entity.General {
        return com.opensam.entity.General(
            id = id,
            worldId = 1,
            name = "장수",
            nationId = nationId,
            cityId = 1,
            officerLevel = officerLevel,
            turnTime = java.time.OffsetDateTime.now(),
        )
    }

    private fun message(id: Long, mailboxType: String = MessageService.MAILBOX_PRIVATE, mailboxCode: String = "message"): Message {
        return Message(
            id = id,
            worldId = 1,
            mailboxCode = mailboxCode,
            mailboxType = mailboxType,
            messageType = "test",
        )
    }
}
