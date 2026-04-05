package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

private const val DEFAULT_GOLD = 1000
private const val DEFAULT_RICE = 1000

class 해산(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "해산"

    override val fullConditionConstraints = listOf(
        BeLord(),
        WanderingNation(),
    )

    override val minConditionConstraints = listOf(
        BeLord(),
        WanderingNation(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val generalName = general.name
        val nationName = nation?.name ?: "소속세력"

        // Legacy: compare joinYearMonth(init_year, init_month) vs joinYearMonth(year, month)
        val initYearMonth = env.startYear * 12 + (env.startMonth ?: 1) - 1
        val currentYearMonth = env.year * 12 + env.month - 1
        if (currentYearMonth <= initYearMonth) {
            pushLog("다음 턴부터 해산할 수 있습니다. <1>$date</>")
            return CommandResult(
                success = false,
                logs = logs,
                message = """{"alternativeCommand":"che_인재탐색"}"""
            )
        }

        // Legacy: cap all nation generals' gold/rice to defaultGold/Rice
        // Legacy: refreshNationStaticInfo, deleteNation
        pushLog("세력을 해산했습니다. <1>$date</>")
        pushLog("_global:<Y>${generalName}</>${josa(generalName, "이")} 세력을 해산했습니다.")
        pushLog("_history:<D><b>${nationName}</b></>${josa(nationName, "을")} 해산")
        pushHistoryLog("<D><b>${nationName}</b></>${josa(nationName, "을")} 해산")
        pushGlobalLog("<Y>${generalName}</>${josa(generalName, "이")} 세력을 해산했습니다.")
        pushGlobalHistoryLog("<Y><b>【해산】</b></><D><b>${nationName}</b></>${josa(nationName, "이")} 해산되었습니다.")

        return CommandResult(
            success = true,
            logs = logs,
            message = buildString {
                append("""{"disbandNation":true""")
                append(""","generalChanges":{"makeLimit":12,"gold":{"max":$DEFAULT_GOLD},"rice":{"max":$DEFAULT_RICE}}""")
                append(""","allNationGenerals":{"gold":{"max":$DEFAULT_GOLD},"rice":{"max":$DEFAULT_RICE}}""")
                append(""","releaseCities":true""")
                append(""","triggerEvent":"OccupyCity"""")
                append("}")
            }
        )
    }
}
