package com.openlogh.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "officer_access_log")
class OfficerAccessLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "officer_id", nullable = false)
    var officerId: Long = 0,

    @Column(name = "world_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "accessed_at", nullable = false)
    var accessedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "ip_address")
    var ipAddress: String? = null,

    @Column(nullable = false)
    var refresh: Int = 0,

    @Column(name = "refresh_score_total", nullable = false)
    var refreshScoreTotal: Int = 0,
)
