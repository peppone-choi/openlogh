package com.openlogh.gateway.repository

import com.openlogh.gateway.entity.SessionState
import org.springframework.data.jpa.repository.JpaRepository

interface SessionStateRepository : JpaRepository<SessionState, Short>
