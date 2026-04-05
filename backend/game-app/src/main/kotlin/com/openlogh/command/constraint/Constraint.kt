package com.openlogh.command.constraint

import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction

data class ConstraintContext(
    val general: Officer,
    val city: Planet? = null,
    val nation: Faction? = null,
    val destOfficer: Officer? = null,
    val destPlanet: Planet? = null,
    val destFaction: Faction? = null,
    val arg: Map<String, Any>? = null,
    val env: Map<String, Any> = emptyMap()
)

sealed class ConstraintResult {
    data object Pass : ConstraintResult()
    data class Fail(val reason: String) : ConstraintResult()
}

interface Constraint {
    fun test(ctx: ConstraintContext): ConstraintResult
    val name: String
}
