package com.openlogh.repository

import com.openlogh.entity.Emperor
import org.springframework.data.jpa.repository.JpaRepository

interface EmperorRepository : JpaRepository<Emperor, Long>
