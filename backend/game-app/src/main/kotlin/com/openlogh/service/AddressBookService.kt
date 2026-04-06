package com.openlogh.service

import com.openlogh.entity.AddressBookEntry as AddressBookEntryEntity
import com.openlogh.model.PositionCard
import com.openlogh.repository.AddressBookRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Mail address entry returned to clients.
 * Each officer has personal + position card addresses.
 */
data class AddressBookEntry(
    val officerId: Long,
    val name: String,
    val factionId: Long,
    val factionName: String,
    val rankLevel: Int,
    val isContact: Boolean,
    val addressType: String = "PERSONAL",
    val positionCard: String? = null,
    val positionCardName: String? = null,
)

/**
 * Address book service for the position card mail system.
 *
 * Each character has:
 * - 1 personal mail address (개인 메일)
 * - N position card mail addresses (직무카드 부속 메일주소), one per held card
 *
 * Address book is populated via 명함교환 (name card exchange) chat command.
 * Max 100 entries. Wiped on faction defection.
 */
@Service
class AddressBookService(
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val factionRepository: FactionRepository,
    private val addressBookRepository: AddressBookRepository,
) {
    companion object {
        const val MAX_ADDRESS_BOOK_SIZE = 100
    }

    /**
     * Get all mail addresses for an officer (personal + position card addresses).
     */
    fun getOfficerMailAddresses(sessionId: Long, officerId: Long): List<MailAddress> {
        val officer = officerRepository.findById(officerId).orElse(null) ?: return emptyList()
        val addresses = mutableListOf<MailAddress>()

        // Personal address
        addresses.add(
            MailAddress(
                officerId = officer.id,
                officerName = officer.name,
                addressType = "PERSONAL",
                positionCard = null,
                displayName = "${officer.name} (개인)",
            )
        )

        // Position card addresses
        for (cardName in officer.positionCards) {
            val card = runCatching { PositionCard.valueOf(cardName) }.getOrNull() ?: continue
            if (card == PositionCard.PERSONAL || card == PositionCard.CAPTAIN) continue
            addresses.add(
                MailAddress(
                    officerId = officer.id,
                    officerName = officer.name,
                    addressType = "POSITION_CARD",
                    positionCard = card.name,
                    displayName = "${card.nameKo} (${officer.name})",
                )
            )
        }

        return addresses
    }

    /**
     * Get address book for an officer.
     * Returns all addresses obtained via name card exchange.
     * Falls back to faction members + previous message contacts for backward compat.
     */
    fun getAddressBook(sessionId: Long, officerId: Long): List<AddressBookEntry> {
        val officer = officerRepository.findById(officerId).orElse(null) ?: return emptyList()
        val factions = factionRepository.findBySessionId(sessionId).associateBy { it.id }

        // Get persisted address book entries from name card exchange
        val persistedEntries = addressBookRepository.findBySessionIdAndOwnerId(sessionId, officerId)

        // Backward compat: also include officers from private messages
        val sentMessages = messageRepository.findBySrcIdAndMailboxType(officerId, "PRIVATE")
        val receivedMessages = messageRepository.findByDestIdAndMailboxType(officerId, "PRIVATE")
        val messageContactIds = (sentMessages.mapNotNull { it.destId } + receivedMessages.mapNotNull { it.srcId })
            .filter { it != officerId }
            .toSet()

        // Get faction members
        val factionMembers = if (officer.factionId != 0L) {
            officerRepository.findBySessionIdAndFactionId(sessionId, officer.factionId)
                .filter { it.id != officerId }
        } else {
            emptyList()
        }

        // Build entries: persisted entries first, then faction + message contacts
        val persistedTargetIds = persistedEntries.map { it.targetId }.toSet()
        val results = mutableListOf<AddressBookEntry>()

        // Add persisted address book entries
        val persistedOfficers = if (persistedTargetIds.isNotEmpty()) {
            officerRepository.findAllById(persistedTargetIds).associateBy { it.id }
        } else {
            emptyMap()
        }

        for (entry in persistedEntries) {
            val target = persistedOfficers[entry.targetId] ?: continue
            val cardName = if (entry.addressType == "POSITION_CARD" && entry.positionCard != null) {
                runCatching { PositionCard.valueOf(entry.positionCard!!) }.getOrNull()?.nameKo
            } else null

            results.add(
                AddressBookEntry(
                    officerId = target.id,
                    name = target.name,
                    factionId = target.factionId,
                    factionName = factions[target.factionId]?.name ?: "재야",
                    rankLevel = target.officerLevel.toInt(),
                    isContact = true,
                    addressType = entry.addressType,
                    positionCard = entry.positionCard,
                    positionCardName = cardName,
                )
            )
        }

        // Add faction members not already in address book
        val allAddedIds = results.map { it.officerId }.toMutableSet()
        for (member in factionMembers) {
            if (member.id in allAddedIds) continue
            allAddedIds.add(member.id)
            results.add(
                AddressBookEntry(
                    officerId = member.id,
                    name = member.name,
                    factionId = member.factionId,
                    factionName = factions[member.factionId]?.name ?: "재야",
                    rankLevel = member.officerLevel.toInt(),
                    isContact = false,
                )
            )
        }

        // Add message contacts not already in results
        for (contactId in messageContactIds) {
            if (contactId in allAddedIds) continue
            val target = officerRepository.findById(contactId).orElse(null) ?: continue
            allAddedIds.add(contactId)
            results.add(
                AddressBookEntry(
                    officerId = target.id,
                    name = target.name,
                    factionId = target.factionId,
                    factionName = factions[target.factionId]?.name ?: "재야",
                    rankLevel = target.officerLevel.toInt(),
                    isContact = false,
                )
            )
        }

        return results.sortedWith(
            compareByDescending<AddressBookEntry> { it.isContact }
                .thenBy { it.factionId }
                .thenByDescending { it.rankLevel }
        )
    }

    /**
     * Exchange name cards (명함교환): add each other's addresses to address book.
     * Both officers exchange personal + all position card addresses.
     * Returns the number of new addresses added for the initiator.
     */
    @Transactional
    fun exchangeNameCards(sessionId: Long, officerAId: Long, officerBId: Long): Int {
        val officerA = officerRepository.findById(officerAId).orElse(null)
            ?: throw IllegalArgumentException("Officer not found: $officerAId")
        val officerB = officerRepository.findById(officerBId).orElse(null)
            ?: throw IllegalArgumentException("Officer not found: $officerBId")

        var addedForA = 0
        var addedForB = 0

        // Add B's addresses to A's book
        addedForA += addAddressIfNotExists(sessionId, officerAId, officerBId, "PERSONAL", null)
        for (cardName in officerB.positionCards) {
            val card = runCatching { PositionCard.valueOf(cardName) }.getOrNull() ?: continue
            if (card == PositionCard.PERSONAL || card == PositionCard.CAPTAIN) continue
            addedForA += addAddressIfNotExists(sessionId, officerAId, officerBId, "POSITION_CARD", card.name)
        }

        // Add A's addresses to B's book
        addedForB += addAddressIfNotExists(sessionId, officerBId, officerAId, "PERSONAL", null)
        for (cardName in officerA.positionCards) {
            val card = runCatching { PositionCard.valueOf(cardName) }.getOrNull() ?: continue
            if (card == PositionCard.PERSONAL || card == PositionCard.CAPTAIN) continue
            addedForB += addAddressIfNotExists(sessionId, officerBId, officerAId, "POSITION_CARD", card.name)
        }

        return addedForA
    }

    /**
     * Wipe address book on faction defection.
     */
    @Transactional
    fun wipeAddressBook(sessionId: Long, officerId: Long) {
        addressBookRepository.deleteBySessionIdAndOwnerId(sessionId, officerId)
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

    private fun addAddressIfNotExists(
        sessionId: Long,
        ownerId: Long,
        targetId: Long,
        addressType: String,
        positionCard: String?,
    ): Int {
        val count = addressBookRepository.countBySessionIdAndOwnerId(sessionId, ownerId)
        if (count >= MAX_ADDRESS_BOOK_SIZE) return 0

        val existing = addressBookRepository
            .findBySessionIdAndOwnerIdAndTargetIdAndAddressTypeAndPositionCard(
                sessionId, ownerId, targetId, addressType, positionCard
            )
        if (existing != null) return 0

        addressBookRepository.save(
            AddressBookEntryEntity(
                sessionId = sessionId,
                ownerId = ownerId,
                targetId = targetId,
                addressType = addressType,
                positionCard = positionCard,
            )
        )
        return 1
    }
}

/**
 * Represents a single mail address (personal or position card).
 */
data class MailAddress(
    val officerId: Long,
    val officerName: String,
    val addressType: String,
    val positionCard: String?,
    val displayName: String,
)
