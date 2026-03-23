package com.openlogh.command.constraint

interface Constraint {
    fun test(ctx: ConstraintContext): ConstraintResult
}
