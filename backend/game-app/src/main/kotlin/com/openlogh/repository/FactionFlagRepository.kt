package com.openlogh.repository

import com.openlogh.entity.FactionFlag
import com.openlogh.entity.FactionFlagId
import org.springframework.data.jpa.repository.JpaRepository

interface FactionFlagRepository : JpaRepository<FactionFlag, FactionFlagId>
