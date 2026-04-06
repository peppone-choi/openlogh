package com.openlogh.entity

import jakarta.persistence.*

@Entity
@Table(name = "star_route")
class StarRoute(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Column(name = "session_id", nullable = false)
    var sessionId: Long = 0,

    @Column(name = "from_star_id", nullable = false)
    var fromStarId: Int = 0,

    @Column(name = "to_star_id", nullable = false)
    var toStarId: Int = 0,

    @Column(nullable = false)
    var distance: Int = 1,
)
