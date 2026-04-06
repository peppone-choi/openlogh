package com.openlogh.repository

import com.openlogh.entity.StarRoute
import org.springframework.data.jpa.repository.JpaRepository

interface StarRouteRepository : JpaRepository<StarRoute, Long> {
    fun findBySessionId(sessionId: Long): List<StarRoute>
    fun findBySessionIdAndFromStarId(sessionId: Long, fromStarId: Int): List<StarRoute>
}
