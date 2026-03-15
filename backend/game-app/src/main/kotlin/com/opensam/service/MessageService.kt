package com.opensam.service

import com.opensam.dto.ContactInfo
import com.opensam.dto.BoardCommentResponse
import com.opensam.entity.BoardComment
import com.opensam.entity.Message
import com.opensam.repository.BoardCommentRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val boardCommentRepository: BoardCommentRepository,
    private val generalRepository: GeneralRepository,
    private val nationRepository: NationRepository,
    private val worldStateRepository: com.opensam.repository.WorldStateRepository,
) {
    companion object {
        const val MAILBOX_PUBLIC = "PUBLIC"
        const val MAILBOX_NATIONAL = "NATIONAL"
        const val MAILBOX_PRIVATE = "PRIVATE"
        const val MAILBOX_DIPLOMACY = "DIPLOMACY"
        private const val DEFAULT_TYPED_PAGE_SIZE = 30
        private const val MAX_PAGE_SIZE = 100
    }

    fun getMessages(generalId: Long, sinceId: Long? = null, limit: Int? = null): List<Message> {
        val general = generalRepository.findById(generalId).orElse(null) ?: return emptyList()
        val privateMessages = if (sinceId != null) {
            messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MAILBOX_PRIVATE, generalId, sinceId)
        } else {
            messageRepository.findConversationByMailboxTypeAndOwnerId(MAILBOX_PRIVATE, generalId)
        }

        val nationalMessages = if (general.nationId != 0L) {
            if (sinceId != null) {
                messageRepository.findByDestIdAndMailboxTypeAndIdGreaterThanOrderBySentAtDesc(
                    general.nationId,
                    MAILBOX_NATIONAL,
                    sinceId,
                )
            } else {
                messageRepository.findByDestIdAndMailboxTypeOrderBySentAtDesc(general.nationId, MAILBOX_NATIONAL)
            }
        } else {
            emptyList()
        }

        val diplomacyMessages = if (general.nationId != 0L && general.officerLevel >= 4) {
            if (sinceId != null) {
                messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdGreaterThan(MAILBOX_DIPLOMACY, general.nationId, sinceId)
            } else {
                messageRepository.findConversationByMailboxTypeAndOwnerId(MAILBOX_DIPLOMACY, general.nationId)
            }
        } else {
            emptyList()
        }

        val messages = (privateMessages + nationalMessages + diplomacyMessages)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Message> { it.sentAt }.thenByDescending { it.id })

        val normalizedLimit = when {
            limit != null -> limit.coerceIn(1, MAX_PAGE_SIZE)
            sinceId == null -> DEFAULT_TYPED_PAGE_SIZE
            else -> null
        }
        return if (normalizedLimit != null) messages.take(normalizedLimit) else messages
    }

    fun getPublicMessages(worldId: Long, beforeId: Long? = null, limit: Int? = null): List<Message> {
        val messages = if (beforeId != null) {
            messageRepository.findByWorldIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(worldId, MAILBOX_PUBLIC, beforeId)
        } else {
            messageRepository.findByWorldIdAndMailboxTypeOrderBySentAtDesc(worldId, MAILBOX_PUBLIC)
        }
        return applyLimit(messages, limit)
    }

    fun getNationalMessages(nationId: Long, beforeId: Long? = null, limit: Int? = null): List<Message> {
        val messages = if (beforeId != null) {
            messageRepository.findByDestIdAndMailboxTypeAndIdLessThanOrderBySentAtDesc(nationId, MAILBOX_NATIONAL, beforeId)
        } else {
            messageRepository.findByDestIdAndMailboxTypeOrderBySentAtDesc(nationId, MAILBOX_NATIONAL)
        }
        return applyLimit(messages, limit)
    }

    fun getPrivateMessages(generalId: Long, beforeId: Long? = null, limit: Int? = null): List<Message> {
        val messages = if (beforeId != null) {
            messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdLessThan(MAILBOX_PRIVATE, generalId, beforeId)
        } else {
            messageRepository.findConversationByMailboxTypeAndOwnerId(MAILBOX_PRIVATE, generalId)
        }
        return applyLimit(messages, limit)
    }

    fun getDiplomacyMessages(nationId: Long, officerLevel: Short, beforeId: Long? = null, limit: Int? = null): List<Message> {
        require(officerLevel >= 4) { "Diplomacy mailbox requires officer level 4 or higher" }
        val messages = if (beforeId != null) {
            messageRepository.findConversationByMailboxTypeAndOwnerIdAndIdLessThan(MAILBOX_DIPLOMACY, nationId, beforeId)
        } else {
            messageRepository.findConversationByMailboxTypeAndOwnerId(MAILBOX_DIPLOMACY, nationId)
        }
        return applyLimit(messages, limit)
    }

    fun getBoardMessages(worldId: Long, nationId: Long): List<Message> {
        return messageRepository.findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId, "board", nationId)
    }

    fun getSecretBoardMessages(worldId: Long, nationId: Long): List<Message> {
        return messageRepository.findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(worldId, "secret", nationId)
    }

    @Transactional
    fun sendMessage(
        worldId: Long,
        mailboxCode: String,
        mailboxType: String? = null,
        messageType: String,
        srcId: Long?,
        destId: Long?,
        officerLevel: Short? = null,
        payload: Map<String, Any>,
    ): Message {
        val resolvedMailboxType = resolveMailboxType(mailboxType, mailboxCode)

        if (resolvedMailboxType == MAILBOX_DIPLOMACY) {
            val resolvedOfficerLevel = officerLevel ?: srcId
                ?.let { senderId -> generalRepository.findById(senderId).orElse(null)?.officerLevel }
            require((resolvedOfficerLevel ?: 0) >= 4) { "Diplomacy mailbox requires officer level 4 or higher" }
        }

        if (resolvedMailboxType == MAILBOX_NATIONAL && mailboxCode == "national" && srcId != null && destId != null) {
            val recipientNationIds = linkedSetOf(srcId, destId)
            val copies = recipientNationIds.map { nationId ->
                Message(
                    worldId = worldId,
                    mailboxCode = mailboxCode,
                    mailboxType = resolvedMailboxType,
                    messageType = messageType,
                    srcId = srcId,
                    destId = nationId,
                    payload = payload.toMutableMap(),
                )
            }
            return messageRepository.saveAll(copies).first()
        }

        return messageRepository.save(
            Message(
                worldId = worldId,
                mailboxCode = mailboxCode,
                mailboxType = resolvedMailboxType,
                messageType = messageType,
                srcId = srcId,
                destId = destId,
                payload = payload.toMutableMap(),
            )
        )
    }

    @Transactional
    fun deleteMessage(id: Long) {
        messageRepository.deleteById(id)
    }

    @Transactional
    fun markAsRead(id: Long) {
        val message = messageRepository.findById(id).orElseThrow {
            IllegalArgumentException("Message not found: $id")
        }
        message.meta["readAt"] = OffsetDateTime.now().toString()
        messageRepository.save(message)
    }

    fun getContacts(worldId: Long): List<ContactInfo> {
        val generals = generalRepository.findByWorldId(worldId)
        val nations = nationRepository.findByWorldId(worldId).associateBy { it.id }
        return generals.map { gen ->
            ContactInfo(
                generalId = gen.id,
                name = gen.name,
                nationId = gen.nationId,
                nationName = nations[gen.nationId]?.name ?: "",
                picture = gen.picture,
            )
        }
    }

    fun getRecentMessages(lastId: Long): List<Message> {
        return messageRepository.findByIdGreaterThanOrderBySentAtDesc(lastId)
    }

    fun getBoardComments(postId: Long): List<BoardCommentResponse> {
        val post = getBoardPost(postId)
        migrateLegacyPayloadComments(post)
        return boardCommentRepository.findByBoardIdOrderByCreatedAtAsc(postId).map(::toBoardCommentResponse)
    }

    @Transactional
    fun createBoardComment(postId: Long, authorGeneralId: Long, content: String): BoardCommentResponse {
        val post = getBoardPost(postId)
        migrateLegacyPayloadComments(post)

        val saved = boardCommentRepository.save(
            BoardComment(
                boardId = postId,
                authorGeneralId = authorGeneralId,
                content = content,
                createdAt = OffsetDateTime.now(),
            )
        )

        return BoardCommentResponse(
            id = saved.id,
            authorGeneralId = authorGeneralId,
            content = content,
            createdAt = saved.createdAt,
        )
    }

    @Transactional
    fun deleteBoardComment(postId: Long, commentId: Long, generalId: Long): Boolean {
        val post = getBoardPost(postId)
        migrateLegacyPayloadComments(post)

        val comment = boardCommentRepository.findById(commentId).orElse(null) ?: return false
        if (comment.boardId != postId) return false
        if (comment.authorGeneralId != generalId) return false

        boardCommentRepository.delete(comment)
        return true
    }

    /**
     * Send a national message between nations (used by commands like 선전포고).
     */
    @Transactional
    fun sendNationalMessage(
        worldId: Long,
        srcNationId: Long,
        destNationId: Long,
        srcGeneralId: Long,
        text: String,
    ) {
        sendMessage(
            worldId = worldId,
            mailboxCode = "national",
            mailboxType = MAILBOX_NATIONAL,
            messageType = "national_message",
            srcId = srcNationId,
            destId = destNationId,
            payload = mapOf(
                "srcNationId" to srcNationId,
                "destNationId" to destNationId,
                "srcGeneralId" to srcGeneralId,
                "text" to text,
            ),
        )
    }

    @Transactional
    fun respondDiplomacy(messageId: Long, accept: Boolean) {
        val message = messageRepository.findById(messageId).orElseThrow()
        message.meta["responded"] = true
        message.meta["accepted"] = accept
        messageRepository.save(message)
    }

    private fun getBoardPost(postId: Long): Message {
        val post = messageRepository.findById(postId).orElseThrow {
            IllegalArgumentException("Board post not found: $postId")
        }
        require(post.mailboxCode == "board" || post.mailboxCode == "secret") {
            "Not a board post: $postId"
        }
        return post
    }

    private fun parseLegacyBoardComments(post: Message): List<BoardCommentResponse> {
        val raw = post.payload["comments"] as? List<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = (map["id"] as? Number)?.toLong() ?: return@mapNotNull null
            val authorGeneralId = (map["authorGeneralId"] as? Number)?.toLong() ?: return@mapNotNull null
            val content = map["content"]?.toString() ?: return@mapNotNull null
            val createdAtRaw = map["createdAt"]?.toString() ?: return@mapNotNull null
            val createdAt = runCatching { OffsetDateTime.parse(createdAtRaw) }.getOrNull() ?: return@mapNotNull null

            BoardCommentResponse(
                id = id,
                authorGeneralId = authorGeneralId,
                content = content,
                createdAt = createdAt,
            )
        }
    }

    private fun migrateLegacyPayloadComments(post: Message) {
        val existing = boardCommentRepository.findByBoardIdOrderByCreatedAtAsc(post.id)
        if (existing.isNotEmpty()) return

        val legacy = parseLegacyBoardComments(post)
        if (legacy.isEmpty()) return

        val entities = legacy.map {
            BoardComment(
                boardId = post.id,
                authorGeneralId = it.authorGeneralId,
                content = it.content,
                createdAt = it.createdAt,
            )
        }
        boardCommentRepository.saveAll(entities)
        post.payload.remove("comments")
        messageRepository.save(post)
    }

    private fun toBoardCommentResponse(comment: BoardComment): BoardCommentResponse {
        return BoardCommentResponse(
            id = comment.id,
            authorGeneralId = comment.authorGeneralId,
            content = comment.content,
            createdAt = comment.createdAt,
        )
    }

    @Transactional
    fun acceptRecruitment(messageId: Long, receiverGeneralId: Long): String {
        val message = messageRepository.findById(messageId).orElseThrow {
            IllegalArgumentException("메시지를 찾을 수 없습니다: $messageId")
        }

        if (message.messageType != "recruitment") throw IllegalStateException("등용장이 아닙니다.")
        if (message.destId != receiverGeneralId) throw IllegalStateException("수신자가 아닙니다.")
        val used = message.meta["used"] as? Boolean ?: false
        if (used) throw IllegalStateException("이미 사용된 등용장입니다.")

        val action = message.payload["action"] as? String
        if (action != "scout") throw IllegalStateException("유효하지 않은 등용장입니다.")

        val fromNationId = (message.payload["fromNationId"] as? Number)?.toLong()
            ?: throw IllegalStateException("국가 정보가 없습니다.")
        val fromGeneralId = (message.payload["fromGeneralId"] as? Number)?.toLong()
            ?: throw IllegalStateException("등용자 정보가 없습니다.")

        val receiver = generalRepository.findById(receiverGeneralId).orElseThrow {
            IllegalArgumentException("장수를 찾을 수 없습니다.")
        }

        if (receiver.officerLevel >= 20) throw IllegalStateException("군주는 등용장을 수락할 수 없습니다.")

        val destNation = nationRepository.findById(fromNationId).orElse(null)
            ?: throw IllegalStateException("대상 국가가 존재하지 않습니다.")
        if (destNation.level <= 0) throw IllegalStateException("방랑군에는 임관할 수 없습니다.")

        val world = worldStateRepository.findById(receiver.worldId.toShort()).orElse(null)
        if (world != null) {
            val startYear = (world.config["startyear"] as? Number)?.toInt() ?: world.currentYear.toInt()
            val openingPartYears = (world.config["openingPartYears"] as? Number)?.toInt() ?: 3
            val relYear = world.currentYear.toInt() - startYear
            if (relYear < openingPartYears) {
                val genCount = generalRepository.findByWorldIdAndNationId(world.id.toLong(), fromNationId).size
                val genLimit = (world.config["initialNationGenLimit"] as? Number)?.toInt() ?: 10
                if (genCount >= genLimit) {
                    throw IllegalStateException("임관이 제한되고 있습니다. (개방 기간 중 국가당 최대 ${genLimit}명)")
                }
            }
        }

        val oldNationId = receiver.nationId
        val isTroopLeader = receiver.troopId == receiver.id

        if (oldNationId != 0L && receiver.gold > 1000) {
            receiver.gold = 1000
        }
        if (oldNationId != 0L && receiver.rice > 1000) {
            receiver.rice = 1000
        }

        if (oldNationId != 0L) {
            val penalty = 0.1 * receiver.betray
            receiver.experience = maxOf(0, (receiver.experience * (1 - penalty)).toInt())
            receiver.dedication = maxOf(0, (receiver.dedication * (1 - penalty)).toInt())
            receiver.betray = minOf(receiver.betray + 1, 10).toShort()
        } else {
            receiver.experience += 100
            receiver.dedication += 100
        }

        receiver.nationId = fromNationId
        receiver.cityId = destNation.capitalCityId ?: receiver.cityId
        receiver.officerLevel = 1
        receiver.officerCity = 0
        receiver.permission = "normal"
        receiver.belong = 1
        receiver.troopId = 0

        if (isTroopLeader) {
            generalRepository.findByTroopId(receiverGeneralId).forEach { member ->
                if (member.id != receiverGeneralId) {
                    member.troopId = 0
                    generalRepository.save(member)
                }
            }
        }

        generalRepository.save(receiver)

        message.meta["used"] = true
        messageRepository.save(message)

        invalidateOtherScoutMessages(receiverGeneralId, messageId)

        val recruiter = generalRepository.findById(fromGeneralId).orElse(null)
        if (recruiter != null) {
            recruiter.experience += 100
            recruiter.dedication += 100
            generalRepository.save(recruiter)
        }

        return destNation.name
    }

    @Transactional
    fun declineRecruitment(messageId: Long, receiverGeneralId: Long) {
        val message = messageRepository.findById(messageId).orElseThrow {
            IllegalArgumentException("메시지를 찾을 수 없습니다: $messageId")
        }
        if (message.messageType != "recruitment") throw IllegalStateException("등용장이 아닙니다.")
        if (message.destId != receiverGeneralId) throw IllegalStateException("수신자가 아닙니다.")

        message.meta["used"] = true
        messageRepository.save(message)
    }

    private fun invalidateOtherScoutMessages(generalId: Long, exceptMessageId: Long) {
        val pendingScouts = messageRepository.findByDestIdAndMailboxTypeAndMessageTypeOrderBySentAtDesc(
            generalId, MAILBOX_PRIVATE, "recruitment"
        )
        pendingScouts.filter { it.id != exceptMessageId && (it.meta["used"] as? Boolean) != true }.forEach { msg ->
            msg.meta["used"] = true
            messageRepository.save(msg)
        }
    }

    private fun resolveMailboxType(mailboxType: String?, mailboxCode: String): String {
        val normalizedType = mailboxType?.trim()?.uppercase()
        if (normalizedType in setOf(MAILBOX_PUBLIC, MAILBOX_NATIONAL, MAILBOX_PRIVATE, MAILBOX_DIPLOMACY)) {
            return normalizedType!!
        }

        return when (mailboxCode) {
            "secret", "national", "nation" -> MAILBOX_NATIONAL
            "personal", "message", "private" -> MAILBOX_PRIVATE
            "diplomacy", "diplomacy_letter" -> MAILBOX_DIPLOMACY
            else -> MAILBOX_PUBLIC
        }
    }

    private fun applyLimit(messages: List<Message>, limit: Int?): List<Message> {
        val normalizedLimit = (limit ?: DEFAULT_TYPED_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        return messages.take(normalizedLimit)
    }
}
