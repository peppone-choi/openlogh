package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.util.JosaUtil
import kotlin.random.Random

class 건국(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "건국"

    override val fullConditionConstraints: List<Constraint> by lazy {
        val relYear = env.year - env.startYear
        val factionName = arg?.get("factionName") as? String ?: ""
        listOf(
            BeLordOrUnaffiliated(),
            WanderingNation(),
            ReqNationGenCount(2),
            BeOpeningPart(relYear + 1),
            CheckNationNameDuplicate(factionName),
            AllowJoinAction(),
            ConstructableCity(),
            NoPenalty("noFoundNation"),
        )
    }

    override val minConditionConstraints: List<Constraint> by lazy {
        val relYear = env.year - env.startYear
        listOf(
            BeOpeningPart(relYear + 1),
            ReqNationValue("level", "국가규모", "==", 0, "이미 국가에 소속되어 있습니다."),
            NoPenalty("noFoundNation"),
        )
    }

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        // Legacy parity: check initYearMonth
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
        val abbreviation = (arg?.get("abbreviation") as? String)?.take(2)?.ifBlank { null }
            ?: factionName.take(1)
        val nationType = arg?.get("nationType") as? String ?: "che_도적"
        val colorType = arg?.get("colorType") ?: 0
        val cityName = city?.name ?: "알 수 없음"
        val generalName = general.name

        val josaUl = JosaUtil.pick(factionName, "을")
        val josaYi = JosaUtil.pick(generalName, "이")
        val josaNationYi = JosaUtil.pick(factionName, "이")

        pushLog("<D><b>${factionName}</b></>${josaUl} 건국하였습니다. <1>$date</>")
        pushHistoryLog("<D><b>${factionName}</b></>${josaUl} 건국하였습니다. <1>$date</>")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <G><b>${cityName}</b></>에 국가를 건설하였습니다.")
        pushGlobalHistoryLog("<Y><b>【건국】</b></>${nationType} <D><b>${factionName}</b></>${josaNationYi} 새로이 등장하였습니다.")

        val exp = 1000
        val ded = 1000

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"experience":$exp,"dedication":$ded},"nationChanges":{"foundNation":true,"factionName":"$factionName","abbreviation":"$abbreviation","nationType":"$nationType","colorType":$colorType,"level":1,"capital":${general.planetId},"can_국기변경":1},"cityChanges":{"claimCity":true},"historyLog":{"global":"<Y><b>【건국】</b></>${nationType} <D><b>${factionName}</b></>${josaNationYi} 새로이 등장하였습니다.","globalAction":"<Y>${generalName}</>${josaYi} <G><b>${cityName}</b></>에 국가를 건설하였습니다.","general":"<D><b>${factionName}</b></>${josaUl} 건국","nation":"<Y>${generalName}</>${josaYi} <D><b>${factionName}</b></>${josaUl} 건국"},"inheritancePoint":{"active_action":1,"unifier":250}}"""
        )
    }
}
