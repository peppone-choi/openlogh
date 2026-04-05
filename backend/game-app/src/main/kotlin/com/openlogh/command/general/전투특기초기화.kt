package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

open class 전투특기초기화(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "전투 특기 초기화"

    protected open val specialField: String = "special2Code"
    protected open val specialText: String = "전투 특기"
    protected open val specAgeField: String = "specAge2"

    override val fullConditionConstraints: List<Constraint> by lazy {
        listOf(
            ReqGeneralStatValue({ g ->
                val value = when (specialField) {
                    "specialCode" -> g.specialCode
                    "special2Code" -> g.special2Code
                    else -> "None"
                }
                if (value != "None") 1 else 0
            }, specialText, 1),
        )
    }

    override val minConditionConstraints get() = fullConditionConstraints

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 1
    override fun getPostReqTurn() = 60

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        pushLog("새로운 ${specialText}를 가질 준비가 되었습니다. <1>$date</>")
        pushHistoryLog("새로운 ${specialText}를 가질 준비가 되었습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} ${specialText} 초기화를 실행했습니다.")

        val currentAge = general.age.toInt()

        // Track previous specials to avoid re-rolls (legacy: prev_types_special2)
        val prevTypesKey = "prev_types_$specialField"
        val currentSpecial = when (specialField) {
            "specialCode" -> general.specialCode
            "special2Code" -> general.special2Code
            else -> "None"
        }

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"$specialField":"None","$specAgeField":${currentAge + 1}},"specialReset":{"type":"$specialField","prevTypesKey":"$prevTypesKey","oldSpecial":"$currentSpecial"}}"""
        )
    }
}
