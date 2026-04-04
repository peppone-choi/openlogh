package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

class che_소집해제(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "소집해제"

    override val fullConditionConstraints: List<Constraint>
        get() = listOf(ReqGeneralCrew())

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val crewToReturn = general.crew.toInt()

        // Legacy PHP: uses <R> tag for formatting
        pushLog("병사들을 <R>소집해제</>하였습니다. <1>$date</>")
        pushHistoryLog("병사들을 <R>소집해제</>하였습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 병사 소집을 해제했습니다.")

        val exp = 70
        val ded = 100

        // Legacy PHP: crew converted to population via onCalcDomestic('징집인구', 'score', crew)
        // For now, return raw crew count; the caller should apply any modifiers
        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"crew":${-crewToReturn},"experience":$exp,"dedication":$ded},"cityChanges":{"pop":$crewToReturn}}"""
        )
    }
}
