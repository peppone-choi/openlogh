package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.random.Random

class 무작위건국(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "무작위 도시 건국"

    override val fullConditionConstraints: List<Constraint> by lazy {
        val relYear = env.year - env.startYear
        val factionName = arg?.get("factionName") as? String ?: ""
        listOf(
            BeLordOrUnaffiliated(),
            WanderingNation(),
            ReqNationGeneralCount(2),
            BeOpeningPart(relYear + 1),
            CheckNationNameDuplicate(factionName),
            AllowJoinAction(),
        )
    }

    override val minConditionConstraints: List<Constraint> by lazy {
        val relYear = env.year - env.startYear
        listOf(
            BeOpeningPart(relYear + 1),
            UnaffiliatedOrWanderingNation(),
        )
    }

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()
        val generalName = general.name
        val josaYi = pickJosa(generalName, "이")

        val initYearMonth = env.startYear * 12 + 1
        val yearMonth = env.year * 12 + env.month
        if (yearMonth <= initYearMonth) {
            pushLog("다음 턴부터 건국할 수 있습니다. <1>$date</>")
            return CommandResult(
                success = false,
                logs = logs,
                message = """{"alternativeCommand":"che_인재탐색"}"""
            )
        }

        val factionName = arg?.get("factionName") as? String ?: "신생국"
        val nationType = arg?.get("nationType") as? String ?: "che_도적"
        val colorType = arg?.get("colorType") ?: 0
        val josaNationUl = pickJosa(factionName, "을")
        val josaNationYi = pickJosa(factionName, "이")

        // Action log
        pushLog("<D><b>${factionName}</b></>${josaNationUl} 건국하였습니다. <1>$date</>")
        // Global action log
        pushGlobalLog("<Y>${generalName}</>${josaYi} 국가를 건설하였습니다.")
        // Global history log
        pushGlobalLog("<Y><b>【건국】</b></>${nationType} <D><b>${factionName}</b></>${josaNationYi} 새로이 등장하였습니다.")
        pushGlobalHistoryLog("<Y><b>【건국】</b></>${nationType} <D><b>${factionName}</b></>${josaNationYi} 새로이 등장하였습니다.")
        // General history log
        pushHistoryLog("<D><b>${factionName}</b></>${josaNationUl} 건국")
        // National history log
        pushNationalHistoryLog("<Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>${josaNationUl} 건국")

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"experience":1000,"dedication":1000},"nationChanges":{"foundNation":true,"factionName":"$factionName","nationType":"$nationType","colorType":$colorType,"level":1,"aux":{"can_국기변경":1,"can_무작위수도이전":1}},"findRandomCity":{"query":"neutral_constructable","levelMin":5,"levelMax":6},"moveAllNationGenerals":true,"alternativeCommand":"che_해산"}"""
        )
    }
}
