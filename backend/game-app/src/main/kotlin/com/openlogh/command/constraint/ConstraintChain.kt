package com.openlogh.command.constraint

object ConstraintChain {
    fun testAll(constraints: List<Constraint>, ctx: ConstraintContext): ConstraintResult {
        for (c in constraints) {
            val result = c.test(ctx)
            if (result is ConstraintResult.Fail) return result
        }
        return ConstraintResult.Pass
    }
}
