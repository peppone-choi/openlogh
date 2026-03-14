package com.opensam.command.general

import com.opensam.command.CommandCost
import com.opensam.command.CommandEnv
import com.opensam.command.CommandResult
import com.opensam.command.GeneralCommand
import com.opensam.command.constraint.*
import com.opensam.entity.General
import com.opensam.entity.Nation
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

private val RANDOM_TALK_LIST = listOf(
    "어쩌다 보니",
    "인연이 닿아",
    "발길이 닿는 대로",
    "소문을 듣고",
    "점괘에 따라",
    "천거를 받아",
    "유명한",
    "뜻을 펼칠 곳을 찾아",
    "고향에 가까운",
    "천하의 균형을 맞추기 위해",
    "오랜 은거를 마치고",
)

class 랜덤임관(general: General, env: CommandEnv, arg: Map<String, Any>? = null)
    : GeneralCommand(general, env, arg) {

    override val actionName = "무작위 국가로 임관"

    override val fullConditionConstraints = listOf(
        BeNeutral(),
        AllowJoinAction(),
    )

    override fun getCost() = CommandCost()
    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val commandServices = services ?: return CommandResult(success = false, logs = listOf("커맨드 서비스가 없습니다."))
        val date = formatDate()
        val generalName = general.name
        val josaYi = pickJosa(generalName, "이")
        val relYear = env.year - env.startYear
        val genLimit = if (relYear < 3) env.initialNationGenLimit else env.defaultMaxGeneral

        val allNations = commandServices.nationRepository.findByWorldId(env.worldId)
        val allGenerals = commandServices.generalRepository.findByWorldId(env.worldId)
        val candidateNations = allNations.filter { nation ->
            nation.scoutLevel.toInt() == 0 && nation.gennum < genLimit
        }

        val generalsByNationId = allGenerals
            .filter { it.nationId > 0L }
            .groupBy { it.nationId }

        val lordByNationId = generalsByNationId.mapNotNull { (nationId, nationGenerals) ->
            nationGenerals
                .firstOrNull { it.officerLevel.toInt() == 20 }
                ?.let { nationId to it }
        }.toMap()

        val eligibleNations = candidateNations.filter { lordByNationId.containsKey(it.id) }
        if (eligibleNations.isEmpty()) {
            pushLog("임관 가능한 국가가 없습니다. <1>$date</>")
            return CommandResult(success = false, logs = logs)
        }

        val fiction = readBoolean(env.gameStor["fiction"])
        val isHistoricalNpc = general.npcState.toInt() >= 2 && env.scenario in 1000..1999 && !fiction

        val destNation = if (isHistoricalNpc) {
            pickByAffinityScore(eligibleNations, lordByNationId, rng)
        } else {
            pickByNationPowerWeight(eligibleNations, generalsByNationId, rng)
        }

        if (destNation == null) {
            pushLog("임관 가능한 국가가 없습니다. <1>$date</>")
            return CommandResult(success = false, logs = logs)
        }

        val lordGeneral = lordByNationId[destNation.id]
        val lordCityId = lordGeneral?.cityId ?: destNation.capitalCityId ?: 0L
        if (lordCityId <= 0L) {
            pushLog("임관 가능한 국가가 없습니다. <1>$date</>")
            return CommandResult(success = false, logs = logs)
        }

        val randomTalk = RANDOM_TALK_LIST[rng.nextInt(RANDOM_TALK_LIST.size)]
        val exp = if (destNation.gennum < env.initialNationGenLimit) 700 else 100

        pushLog("<D>${destNation.name}</>에 랜덤 임관했습니다. <1>$date</>")
        pushHistoryLog("<D><b>${destNation.name}</b></>에 랜덤 임관")
        pushGlobalLog("<Y>${generalName}</>${josaYi} $randomTalk <D><b>${destNation.name}</b></>에 <S>임관</>했습니다.")

        destNation.gennum += 1
        commandServices.nationRepository.save(destNation)

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"nation":${destNation.id},"officerLevel":1,"officerCity":0,"belong":1,"troop":0,"experience":$exp,"cityId":"$lordCityId"},"inheritanceBonus":1,"tryUniqueLottery":true}"""
        )
    }

    private fun pickByAffinityScore(
        nations: List<Nation>,
        lordByNationId: Map<Long, General>,
        rng: Random,
    ): Nation? {
        val allGenCount = nations.sumOf { it.gennum }.coerceAtLeast(1)
        var bestNation: Nation? = null
        var bestScore = Double.POSITIVE_INFINITY

        for (nation in nations.shuffled(rng)) {
            val lord = lordByNationId[nation.id] ?: continue
            val affinityDiff = circularAffinityDiff(general.affinity.toInt(), lord.affinity.toInt())
            val score =
                log2(affinityDiff.toDouble() + 1.0) + rng.nextDouble() + sqrt(nation.gennum.toDouble() / allGenCount.toDouble())

            if (score < bestScore) {
                bestScore = score
                bestNation = nation
            }
        }

        return bestNation
    }

    private fun pickByNationPowerWeight(
        nations: List<Nation>,
        generalsByNationId: Map<Long, List<General>>,
        rng: Random,
    ): Nation? {
        val weighted = mutableListOf<Pair<Nation, Double>>()

        for (nation in nations) {
            val nationGenerals = generalsByNationId[nation.id].orEmpty()
            var warpower = 0.0
            var develpower = 0.0

            for (nationGeneral in nationGenerals) {
                val leadership = nationGeneral.leadership.toDouble()
                val rankMap = nationGeneral.meta["rank"] as? Map<*, *>
                val killcrew = ((rankMap?.get("killcrew") as? Number)?.toDouble() ?: 0.0) + 50000.0
                val deathcrew = ((rankMap?.get("deathcrew") as? Number)?.toDouble() ?: 0.0) + 50000.0
                val npcCoef = if (nationGeneral.npcState.toInt() < 2) 1.15 else 1.0

                if (nationGeneral.leadership.toInt() >= 40) {
                    warpower += (killcrew / deathcrew.coerceAtLeast(1.0)) * npcCoef * leadership
                }

                develpower += (sqrt(nationGeneral.intel.toDouble() * nationGeneral.strength.toDouble()) * 2.0 + leadership / 2.0) / 5.0
            }

            var calcCnt = warpower + develpower
            if (general.npcState.toInt() < 2 && nation.name.startsWith("ⓤ")) {
                calcCnt *= 100.0
            }

            val normalizedCalcCnt = calcCnt.coerceAtLeast(1e-9)
            val weight = (1.0 / normalizedCalcCnt).pow(3.0)
            weighted.add(nation to weight)
        }

        return choiceByWeight(rng, weighted)
    }

    private fun <T> choiceByWeight(rng: Random, weighted: List<Pair<T, Double>>): T? {
        if (weighted.isEmpty()) return null
        val totalWeight = weighted.sumOf { (_, weight) -> weight.coerceAtLeast(0.0) }
        if (totalWeight <= 0.0) {
            return weighted[rng.nextInt(weighted.size)].first
        }

        var roll = rng.nextDouble() * totalWeight
        for ((item, rawWeight) in weighted) {
            roll -= rawWeight.coerceAtLeast(0.0)
            if (roll <= 0.0) {
                return item
            }
        }

        return weighted.last().first
    }

    private fun circularAffinityDiff(a: Int, b: Int): Int {
        val diff = abs(a - b)
        return minOf(diff, abs(diff - 150))
    }

    private fun log2(value: Double): Double {
        return ln(value) / ln(2.0)
    }

    private fun readBoolean(raw: Any?): Boolean {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true) || raw == "1" || raw.equals("yes", ignoreCase = true)
            else -> false
        }
    }
}
