package com.openlogh.engine.ai

import com.openlogh.entity.*

data class AIContext(
    val world: SessionState,
    val general: Officer,
    val city: Planet,
    val nation: Faction?,
    val diplomacyState: DiplomacyState,
    val generalType: Int,
    val allCities: List<Planet>,
    val allGenerals: List<Officer>,
    val allNations: List<Faction>,
    val frontCities: List<Planet>,
    val rearCities: List<Planet>,
    val nationGenerals: List<Officer>,
    val mapAdjacency: Map<Long, List<Long>> = emptyMap(),
)
