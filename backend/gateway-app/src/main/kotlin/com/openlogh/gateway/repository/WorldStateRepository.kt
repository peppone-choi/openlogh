package com.openlogh.gateway.repository

import com.openlogh.gateway.entity.WorldState
import org.springframework.data.jpa.repository.JpaRepository

interface WorldStateRepository : JpaRepository<WorldState, Short>
