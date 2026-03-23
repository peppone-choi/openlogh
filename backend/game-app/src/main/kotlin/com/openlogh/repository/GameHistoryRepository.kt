package com.openlogh.repository

import com.openlogh.entity.GameHistory
import org.springframework.data.jpa.repository.JpaRepository

interface GameHistoryRepository : JpaRepository<GameHistory, String> {
    fun findByServerId(serverId: String): GameHistory?
}
