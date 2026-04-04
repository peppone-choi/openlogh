package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

private const val DECREASE_COEFF = 0.4
private const val CONVERT_COEFF = 0.9

private val ARM_TYPE_NAMES = mapOf(
    0 to "보병", 1 to "궁병", 2 to "기병",
    3 to "극병", 4 to "노병", 5 to "차병"
)

class che_숙련전환(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "숙련전환"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralGold(cost.gold),
                ReqGeneralRice(cost.rice)
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            return listOf(
                NotBeNeutral(),
                OccupiedCity(),
                ReqGeneralGold(cost.gold),
                ReqGeneralRice(cost.rice)
            )
        }

    override fun getCost() = CommandCost(gold = env.develCost, rice = env.develCost)
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0
    override fun getDuration() = 300

    override suspend fun run(rng: Random): CommandResult {
        if (arg == null) {
            return CommandResult(success = false, logs = listOf("인자가 없습니다."))
        }

        val date = formatDate()
        val srcArmType = (arg!!["srcArmType"] as? Number)?.toInt() ?: 0
        val destArmType = (arg!!["destArmType"] as? Number)?.toInt() ?: 0

        if (srcArmType == destArmType) {
            return CommandResult(success = false, logs = listOf("같은 병종으로 전환할 수 없습니다."))
        }

        val srcTypeName = ARM_TYPE_NAMES[srcArmType] ?: "병종$srcArmType"
        val destTypeName = ARM_TYPE_NAMES[destArmType] ?: "병종$destArmType"

        val srcDex = (general.meta["dex$srcArmType"] as? Number)?.toInt() ?: 0
        val cutDex = (srcDex * DECREASE_COEFF).toInt()
        val addDex = (cutDex * CONVERT_COEFF).toInt()

        val cost = getCost()

        // Legacy PHP uses JosaUtil::pick for proper Korean particles
        val cutDexText = "%,d".format(cutDex)
        val addDexText = "%,d".format(addDex)
        // Josa: 을/를 for cutDex, 로 for addDex
        val josaUl = if (cutDexText.last().code % 28 != 0) "을" else "를"
        val josaRo = if (addDexText.last().code % 28 != 0) "으로" else "로"

        pushLog("${srcTypeName} 숙련 ${cutDexText}${josaUl} ${destTypeName} 숙련 ${addDexText}${josaRo} 전환했습니다. <1>$date</>")
        pushHistoryLog("${srcTypeName} 숙련 ${cutDexText}${josaUl} ${destTypeName} 숙련 ${addDexText}${josaRo} 전환했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} 숙련전환을 실행했습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost.gold},"rice":${-cost.rice},"experience":10,"leadershipExp":2,"dex$srcArmType":${-cutDex},"dex$destArmType":$addDex},"dexConversion":true,"tryUniqueLottery":true}"""
        )
    }
}
