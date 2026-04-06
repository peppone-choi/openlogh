package com.openlogh.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "messenger_connection")
class MessengerConnection(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "caller_id", nullable = false)
    var callerId: Long = 0,

    @Column(name = "callee_id", nullable = false)
    var calleeId: Long = 0,

    @Column(nullable = false)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "resolved_at")
    var resolvedAt: OffsetDateTime? = null,
)
