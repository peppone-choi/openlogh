package com.openlogh.repository

import com.openlogh.entity.NationFlag
import com.openlogh.entity.NationFlagId
import org.springframework.data.jpa.repository.JpaRepository

interface NationFlagRepository : JpaRepository<NationFlag, NationFlagId>
