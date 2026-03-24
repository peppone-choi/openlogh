package com.openlogh.command

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.GridEntryValidator
import com.openlogh.engine.fleet.FleetFormationRules
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.engine.planet.PlanetFacilityService
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.CityRepository
import com.openlogh.repository.NationRepository

data class CommandServices(
    val generalRepository: GeneralRepository,
    val cityRepository: CityRepository,
    val nationRepository: NationRepository,
    val diplomacyService: DiplomacyService,
    val modifierService: ModifierService? = null,
    val planetFacilityService: PlanetFacilityService? = null,
    val fleetFormationRules: FleetFormationRules? = null,
    val gridEntryValidator: GridEntryValidator? = null,
)
