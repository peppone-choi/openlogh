package com.openlogh.repository

import com.openlogh.entity.AddressBookEntry
import org.springframework.data.jpa.repository.JpaRepository

interface AddressBookRepository : JpaRepository<AddressBookEntry, Long> {
    fun findBySessionIdAndOwnerId(sessionId: Long, ownerId: Long): List<AddressBookEntry>
    fun countBySessionIdAndOwnerId(sessionId: Long, ownerId: Long): Long
    fun deleteBySessionIdAndOwnerId(sessionId: Long, ownerId: Long)
    fun findBySessionIdAndOwnerIdAndTargetIdAndAddressTypeAndPositionCard(
        sessionId: Long,
        ownerId: Long,
        targetId: Long,
        addressType: String,
        positionCard: String?,
    ): AddressBookEntry?
}
