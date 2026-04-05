package com.openlogh.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "diplomacy")
class Diplomacy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "src_faction_id", nullable = false)
    var srcFactionId: Long = 0,

    @Column(name = "dest_faction_id", nullable = false)
    var destFactionId: Long = 0,

    @Column(name = "state_code", nullable = false)
    var stateCode: String = "",

    @Column(nullable = false)
    var term: Short = 0,

    @Column(name = "is_dead", nullable = false)
    var isDead: Boolean = false,

    @Column(name = "is_showing", nullable = false)
    var isShowing: Boolean = true,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var meta: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
