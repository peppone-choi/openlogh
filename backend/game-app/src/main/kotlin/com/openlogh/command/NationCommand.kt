package com.openlogh.command

import com.openlogh.command.constraint.*
import com.openlogh.entity.*
import kotlin.random.Random

abstract class NationCommand(
    val general: General,
    val env: CommandEnv,
    val arg: Map<String, Any>? = null,
) {
    abstract val actionName: String

    var city: City? = null
    var nation: Nation? = null
    var destCity: City? = null
    var destNation: Nation? = null
    var destGeneral: General? = null
    var services: CommandServices? = null
    var constraintEnv: Map<String, Any> = emptyMap()

    abstract suspend fun run(rng: Random): CommandResult
    open fun getCost(): CommandCost = CommandCost()
    open fun getPreReqTurn(): Int = 0
    open fun getPostReqTurn(): Int = 0
    open fun getDuration(): Long = 0

    open fun getConstraints(): List<Constraint> = emptyList()

    open fun checkFullCondition(): ConstraintResult {
        val ctx = ConstraintContext(
            general = general,
            city = city,
            nation = nation,
            destCity = destCity,
            destNation = destNation,
            destGeneral = destGeneral,
            env = constraintEnv,
        )
        return ConstraintChain.testAll(getConstraints(), ctx)
    }

    protected fun formatDate(): String =
        "${env.year}년 ${"%02d".format(env.month)}월"
}
