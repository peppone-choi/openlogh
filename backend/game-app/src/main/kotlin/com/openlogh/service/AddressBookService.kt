package com.openlogh.service

import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import org.springframework.stereotype.Service

data class AddressBookEntry(
    val officerId: Long,
    val name: String,
    val factionId: Long,
    val factionName: String,
    val rankLevel: Int,
    val isContact: Boolean,
)

/**
 * Address book service for mail system.
 * Returns officers the player has communicated with + faction members.
 */
@Service
class AddressBookService(
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val factionRepository: com.openlogh.repository.FactionRepository,
) {
    /**
     * Get address book for an officer: previous contacts + faction members.
     */
    fun getAddressBook(sessionId: Long, officerId: Long): List<AddressBookEntry> {
        val officer = officerRepository.findById(officerId).orElse(null) ?: return emptyList()
        val factions = factionRepository.findBySessionId(sessionId).associateBy { it.id }

        // Get officers this player has exchanged private messages with
        val sentMessages = messageRepository.findBySrcIdAndMailboxType(officerId, "PRIVATE")
        val receivedMessages = messageRepository.findByDestIdAndMailboxType(officerId, "PRIVATE")

        val contactIds = (sentMessages.mapNotNull { it.destId } + receivedMessages.mapNotNull { it.srcId })
            .filter { it != officerId }
            .toSet()

        // Get faction members
        val factionMembers = if (officer.factionId != 0L) {
            officerRepository.findBySessionIdAndFactionId(sessionId, officer.factionId)
                .filter { it.id != officerId }
        } else {
            emptyList()
        }

        val allOfficerIds = (contactIds + factionMembers.map { it.id }).distinct()
        val allOfficers = officerRepository.findAllById(allOfficerIds).associateBy { it.id }

        return allOfficerIds.mapNotNull { id ->
            val target = allOfficers[id] ?: return@mapNotNull null
            AddressBookEntry(
                officerId = target.id,
                name = target.name,
                factionId = target.factionId,
                factionName = factions[target.factionId]?.name ?: "재야",
                rankLevel = target.officerLevel.toInt(),
                isContact = id in contactIds,
            )
        }.sortedWith(
            compareByDescending<AddressBookEntry> { it.isContact }
                .thenBy { it.factionId }
                .thenByDescending { it.rankLevel }
        )
    }

    /**
     * Search officers by name for addressing mail.
     */
    fun searchOfficers(sessionId: Long, query: String): List<AddressBookEntry> {
        if (query.isBlank()) return emptyList()

        val factions = factionRepository.findBySessionId(sessionId).associateBy { it.id }
        val officers = officerRepository.findBySessionId(sessionId)
            .filter { it.name.contains(query, ignoreCase = true) }
            .take(20)

        return officers.map { target ->
            AddressBookEntry(
                officerId = target.id,
                name = target.name,
                factionId = target.factionId,
                factionName = factions[target.factionId]?.name ?: "재야",
                rankLevel = target.officerLevel.toInt(),
                isContact = false,
            )
        }
    }
}
