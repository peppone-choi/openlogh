package com.openlogh.repository

import com.openlogh.entity.SessionState
import org.springframework.data.jpa.repository.JpaRepository

interface SessionStateRepository : JpaRepository<SessionState, Short> {
    fun findByCommitSha(commitSha: String): List<SessionState>
}
