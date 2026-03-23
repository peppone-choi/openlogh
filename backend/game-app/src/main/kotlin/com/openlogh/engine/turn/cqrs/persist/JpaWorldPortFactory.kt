package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository

data class JpaWorldPortFactory(
    val generalRepository: OfficerRepository,
    val cityRepository: PlanetRepository,
    val nationRepository: FactionRepository,
    val diplomacyRepository: DiplomacyRepository,
)
