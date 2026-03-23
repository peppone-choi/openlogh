package com.openlogh.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "board")
class Board(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "world_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "nation_id")
    var factionId: Long? = null,

    @Column(name = "author_general_id", nullable = false)
    var authorGeneralId: Long = 0,

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var content: String = "",

    @Column(name = "is_secret", nullable = false)
    var isSecret: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
