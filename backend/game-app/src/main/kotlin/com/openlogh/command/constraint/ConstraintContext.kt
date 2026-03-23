package com.openlogh.command.constraint

import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet

data class ConstraintContext(
    val general: Officer,
    val city: Planet? = null,
    val nation: Faction? = null,
    val destGeneral: Officer? = null,
    val destCity: Planet? = null,
    val destNation: Faction? = null,
    val env: Map<String, Any> = emptyMap(),
)
