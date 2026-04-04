package com.openlogh.repository

import com.openlogh.entity.WorldState
import org.springframework.data.jpa.repository.JpaRepository

interface WorldStateRepository : JpaRepository<WorldState, Short> {
    fun findByCommitSha(commitSha: String): List<WorldState>
}
