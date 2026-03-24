package com.openlogh.command

import com.openlogh.command.constraint.*
import com.openlogh.entity.*
import kotlin.random.Random

abstract class BaseCommand(
    val general: General,
    val env: CommandEnv,
    val arg: Map<String, Any>? = null,
) {
    abstract val actionName: String
    open val canDisplay: Boolean = true
    open val isReservable: Boolean = true

    open var city: City? = null
    open var nation: Nation? = null
    open var destCity: City? = null
    open var destGeneral: General? = null
    open var destNation: Nation? = null
    open var destCityGenerals: List<General>? = null
    open var services: CommandServices? = null

    abstract suspend fun run(rng: Random): CommandResult
    open fun getCost(): CommandCost = CommandCost()
    open fun getPreReqTurn(): Int = 0
    open fun getPostReqTurn(): Int = 0
    open fun getDuration(): Long = 0

    open fun getConstraints(): List<Constraint> = emptyList()

    open fun checkFullCondition(): ConstraintResult {
        val ctx = ConstraintContext(
            officer = general,
            planet = city,
            faction = nation,
            destGeneral = destGeneral,
            destCity = destCity,
            destNation = destNation,
        )
        return ConstraintChain.testAll(getConstraints(), ctx)
    }

    protected fun formatDate(): String =
        "${env.year}년 ${"%02d".format(env.month)}월"
}
