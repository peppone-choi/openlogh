package com.opensam.repository

import com.opensam.entity.Message
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MessageRepository : JpaRepository<Message, Long> {
    fun findByDestIdOrderBySentAtDesc(destId: Long): List<Message>
    fun findByDestIdAndIdGreaterThanOrderBySentAtDesc(destId: Long, id: Long): List<Message>
    fun findByWorldIdAndMailboxCodeOrderBySentAtDesc(worldId: Long, mailboxCode: String): List<Message>
    fun findByWorldIdAndMailboxCodeAndSrcIdOrderBySentAtDesc(worldId: Long, mailboxCode: String, srcId: Long): List<Message>
    fun findBySrcIdAndMailboxCodeOrderBySentAtDesc(srcId: Long, mailboxCode: String): List<Message>
    fun findByWorldIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(worldId: Long, mailboxCode: String, id: Long): List<Message>
    fun findByIdGreaterThanOrderBySentAtDesc(id: Long): List<Message>
    fun findByDestIdAndMailboxCodeOrderBySentAtDesc(destId: Long, mailboxCode: String): List<Message>
    fun findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId: Long, mailboxCode: String, destId: Long): List<Message>
    fun findByWorldIdAndMailboxTypeOrderBySentAtDesc(worldId: Long, mailboxType: String): List<Message>
    fun findByWorldIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(worldId: Long, mailboxType: String, id: Long): List<Message>
    fun findByDestIdAndMailboxTypeOrderBySentAtDesc(destId: Long, mailboxType: String): List<Message>
    fun findByDestIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message>
    fun findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxType: String, id: Long): List<Message>
    fun findByDestIdAndMailboxCodeAndIdLessThanOrderByIdDesc(destId: Long, mailboxCode: String, id: Long): List<Message>
    fun findByDestIdAndMailboxCodeAndIdGreaterThanOrderBySentAtDesc(destId: Long, mailboxCode: String, id: Long): List<Message>

    @Query(
        value =
            """
            SELECT *
            FROM message m
            WHERE m.world_id = :worldId
              AND m.mailbox_code IN ('world_history', 'general_record')
              AND CAST(m.payload->>'year' AS integer) = :year
              AND CAST(m.payload->>'month' AS integer) = :month
            ORDER BY m.sent_at ASC, m.id ASC
            """,
        nativeQuery = true,
    )
    fun findByWorldIdAndYearAndMonthOrderBySentAtAsc(
        @Param("worldId") worldId: Long,
        @Param("year") year: Int,
        @Param("month") month: Int,
    ): List<Message>

    @Query(
        value =
            """
            SELECT *
            FROM message m
            WHERE m.world_id = :worldId
              AND m.mailbox_code IN ('world_history', 'general_record')
              AND CAST(m.payload->>'year' AS integer) = :year
            ORDER BY m.sent_at DESC, m.id DESC
            """,
        nativeQuery = true,
    )
    fun findByWorldIdAndYearOrderBySentAtDesc(
        @Param("worldId") worldId: Long,
        @Param("year") year: Int,
    ): List<Message>

    @Query(
        """
        SELECT m FROM Message m
        WHERE m.mailboxType = :mailboxType
          AND (m.srcId = :ownerId OR m.destId = :ownerId)
        ORDER BY m.sentAt DESC
        """
    )
    fun findConversationByMailboxTypeAndOwnerId(
        @Param("mailboxType") mailboxType: String,
        @Param("ownerId") ownerId: Long,
    ): List<Message>

    @Query(
        """
        SELECT m FROM Message m
        WHERE m.mailboxType = :mailboxType
          AND (m.srcId = :ownerId OR m.destId = :ownerId)
          AND m.id < :beforeId
        ORDER BY m.sentAt DESC
        """
    )
    fun findConversationByMailboxTypeAndOwnerIdAndIdLessThan(
        @Param("mailboxType") mailboxType: String,
        @Param("ownerId") ownerId: Long,
        @Param("beforeId") beforeId: Long,
    ): List<Message>

    @Query(
        """
        SELECT m FROM Message m
        WHERE m.mailboxType = :mailboxType
          AND (m.srcId = :ownerId OR m.destId = :ownerId)
          AND m.id > :sinceId
        ORDER BY m.sentAt DESC
        """
    )
    fun findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(
        @Param("mailboxType") mailboxType: String,
        @Param("ownerId") ownerId: Long,
        @Param("sinceId") sinceId: Long,
    ): List<Message>
}
