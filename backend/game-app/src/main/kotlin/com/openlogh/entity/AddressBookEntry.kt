package com.openlogh.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "address_book")
class AddressBookEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "owner_id", nullable = false)
    var ownerId: Long = 0,

    @Column(name = "target_id", nullable = false)
    var targetId: Long = 0,

    @Column(name = "address_type", nullable = false)
    var addressType: String = "PERSONAL",

    @Column(name = "position_card")
    var positionCard: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
