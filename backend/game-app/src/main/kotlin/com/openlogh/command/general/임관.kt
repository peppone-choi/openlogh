package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.GeneralCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.General
import kotlin.random.Random

private const val INITIAL_NATION_GEN_LIMIT = 8

class 임관(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "임관"

    override val fullConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            return listOf(
                ReqEnvValue("join_mode", "!=", "onlyRandom", "랜덤 임관만 가능합니다"),
                BeNeutral(),
                ExistsDestNation(),
                AllowJoinDestNation(relYear),
                AllowJoinAction(),
                NoPenalty("noChosenAssignment"),
            )
        }

    override val minConditionConstraints: List<Constraint>
        get() = listOf(
            ReqEnvValue("join_mode", "!=", "onlyRandom", "랜덤 임관만 가능합니다"),
            BeNeutral(),
            AllowJoinAction(),
            NoPenalty("noChosenAssignment"),
        )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val commandServices = services
            ?: return CommandResult(success = false, logs = listOf("커맨드 서비스가 없습니다."))
        val date = formatDate()
        val dn = destNation ?: return CommandResult(success = false, logs = listOf("대상 국가가 없습니다."))
        val destNationName = dn.name
        val generalName = general.name

        // Legacy PHP: gennum < initialNationGenLimit → exp 700, else 100
        val gennum = commandServices.generalRepository.findByNationId(dn.id)?.size ?: 0
        val exp = if (gennum < INITIAL_NATION_GEN_LIMIT) 700 else 100

        // Legacy PHP: Josa 이/가
        val josaYi = if (generalName.last().code % 28 != 0) "이" else ""

        pushLog("<D>${destNationName}</>에 임관했습니다. <1>$date</>")
        pushHistoryLog("<D><b>${destNationName}</b></>에 임관")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>에 <S>임관</>했습니다.")

        // Directly increment dest nation gennum (Applicator doesn't handle gennum,
        // and nationChanges applies to source nation which is null for wanderers)
        dn.gennum += 1
        commandServices.nationRepository.save(dn)

        // Legacy PHP: move general to lord's city
        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"nation":${dn.id},"officerLevel":1,"officerCity":0,"belong":1,"troop":0,"experience":$exp},"moveToCityOfLord":true,"inheritanceBonus":1,"tryUniqueLottery":true}"""
        )
    }
}
