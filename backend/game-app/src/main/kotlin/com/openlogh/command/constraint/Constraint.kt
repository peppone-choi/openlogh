package com.openlogh.command.constraint

import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation

data class ConstraintContext(
    val general: General,
    val city: City? = null,
    val nation: Nation? = null,
    val destGeneral: General? = null,
    val destCity: City? = null,
    val destNation: Nation? = null,
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
