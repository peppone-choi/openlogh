package com.openlogh.repository

import com.openlogh.entity.Sovereign
import org.springframework.data.jpa.repository.JpaRepository

interface SovereignRepository : JpaRepository<Sovereign, Long>
