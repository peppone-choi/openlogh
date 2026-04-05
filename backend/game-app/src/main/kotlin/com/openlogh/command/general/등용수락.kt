package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private const val DEFAULT_GOLD = 1000
private const val DEFAULT_RICE = 1000
private const val MAX_BETRAY_CNT = 10

class 등용수락(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "등용 수락"
    override val canDisplay = false
    override val isReservable = false

    override val minConditionConstraints: List<Constraint>
        get() = listOf(AlwaysFail("예약 불가능 커맨드"))

    override val fullConditionConstraints: List<Constraint>
        get() {
            val relYear = env.year - env.startYear
            return listOf(
                ReqEnvValue("join_mode", "!=", "onlyRandom", "랜덤 임관만 가능합니다"),
                ExistsDestNation(),
                BeNeutral(),
                AllowJoinDestNation(relYear),
                ReqDestNationValue("level", "국가규모", ">", 0, "방랑군에는 임관할 수 없습니다."),
                DifferentDestNation(),
                ReqGeneralStatValue({ 20 - it.officerLevel.toInt() }, "직위(군주 불가)", 1),
            )
        }

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val destNationName = destFaction?.name ?: "알 수 없음"
        val destNationId = destFaction?.id ?: 0L
        val capitalCityId = destFaction?.capitalPlanetId ?: 0L
        val generalName = general.name
        val isTroopLeader = general.fleetId == general.id

        // Self log
        pushLog("<D>${destNationName}</>로 망명하여 수도로 이동합니다.")
        // Global log
        val josaYi = pickJosa(generalName, "이")
        val josaRo = pickJosa(destNationName, "로")
        pushGlobalLog("<Y>${generalName}</>${josaYi} <D><b>${destNationName}</b></>${josaRo} <S>망명</>하였습니다.")
        // History logs
        pushHistoryLog("<D><b>${destNationName}</b></>${josaRo} 망명")

        val statChanges = mutableMapOf<String, Any>(
            "permission" to "normal",
            "belong" to 1,
            "officerLevel" to 1,
            "officerPlanet" to 0,
            "nation" to destNationId,
            "city" to capitalCityId,
            "troop" to 0,
        )

        // Recruiter rewards
        val recruiterChanges = mutableMapOf<String, Any>(
            "experience" to 100,
            "dedication" to 100,
        )

        if (general.factionId != 0L) {
            // Return excess gold/rice to original nation
            if (general.funds > DEFAULT_GOLD) {
                statChanges["gold"] = DEFAULT_GOLD
                statChanges["returnGold"] = general.funds - DEFAULT_GOLD
            }
            if (general.supplies > DEFAULT_RICE) {
                statChanges["rice"] = DEFAULT_RICE
                statChanges["returnRice"] = general.supplies - DEFAULT_RICE
            }
            // Betrayal penalty: 10% * betray count
            val betrayPenalty = 0.1 * general.betray
            statChanges["experience"] = floor(general.experience * (1 - betrayPenalty)).toInt()
            statChanges["dedication"] = floor(general.dedication * (1 - betrayPenalty)).toInt()
            statChanges["betray"] = min(general.betray + 1, MAX_BETRAY_CNT)
        } else {
            // Neutral -> Join: Bonus exp/ded
            statChanges["experience"] = general.experience.toInt() + 100
            statChanges["dedication"] = general.dedication.toInt() + 100
        }

        // Reset killturn for non-NPC
        if (general.npcState < 2) {
            statChanges["killTurn"] = env.killturn
        }

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":${toJson(statChanges)},"recruiterChanges":${toJson(recruiterChanges)},"troopDisband":$isTroopLeader,"destGeneralLog":"<Y>${generalName}</> 등용에 성공했습니다."}"""
        )
    }

    private fun toJson(map: Map<String, Any>): String {
        val entries = map.entries.joinToString(",") { (k, v) ->
            when (v) {
                is String -> "\"$k\":\"$v\""
                else -> "\"$k\":$v"
            }
        }
        return "{$entries}"
    }
}
