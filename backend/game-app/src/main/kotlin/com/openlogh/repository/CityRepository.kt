package com.openlogh.repository

import com.openlogh.entity.City
import org.springframework.data.jpa.repository.JpaRepository

interface CityRepository : JpaRepository<City, Long> {
    fun findByWorldId(worldId: Long): List<City>
    fun findByNationId(nationId: Long): List<City>
}
