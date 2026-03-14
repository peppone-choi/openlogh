package com.opensam.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "select_pool")
class SelectPool(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "world_id", nullable = false)
    var worldId: Long = 0,

    @Column(name = "unique_name", nullable = false)
    var uniqueName: String = "",

    @Column(name = "owner_id", nullable = true)
    var ownerId: Long? = null,

    @Column(name = "general_id", nullable = true)
    var generalId: Long? = null,

    @Column(name = "reserved_until", nullable = true)
    var reservedUntil: OffsetDateTime? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var info: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
