package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
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

class 랜덤임관(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "무작위 국가로 임관"

    override val minConditionConstraints = listOf(BeNeutral())

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

        val allNations = commandServices.factionRepository.findBySessionId(env.sessionId)
        val allGenerals = commandServices.officerRepository.findBySessionId(env.sessionId)
        val candidateNations = allNations.filter { nation ->
            nation.scoutLevel.toInt() == 0 && nation.officerCount < genLimit
        }

        val generalsByNationId = allGenerals
            .filter { it.factionId > 0L }
            .groupBy { it.factionId }

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

        val destFaction = if (isHistoricalNpc) {
            pickByAffinityScore(eligibleNations, lordByNationId, rng)
        } else {
            pickByNationPowerWeight(eligibleNations, generalsByNationId, rng)
        }

        if (destFaction == null) {
            pushLog("임관 가능한 국가가 없습니다. <1>$date</>")
            return CommandResult(success = false, logs = logs)
        }

        val lordGeneral = lordByNationId[destFaction.id]
        val lordCityId = lordGeneral?.planetId ?: destFaction.capitalPlanetId ?: 0L
        if (lordCityId <= 0L) {
            pushLog("임관 가능한 국가가 없습니다. <1>$date</>")
            return CommandResult(success = false, logs = logs)
        }

        val randomTalk = RANDOM_TALK_LIST[rng.nextInt(RANDOM_TALK_LIST.size)]
        val exp = if (destFaction.officerCount < env.initialNationGenLimit) 700 else 100

        pushLog("<D>${destFaction.name}</>에 랜덤 임관했습니다. <1>$date</>")
        pushHistoryLog("<D><b>${destFaction.name}</b></>에 랜덤 임관")
        pushGlobalLog("<Y>${generalName}</>${josaYi} $randomTalk <D><b>${destFaction.name}</b></>에 <S>임관</>했습니다.")

        destFaction.officerCount += 1
        commandServices.factionRepository.save(destFaction)

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"nation":${destFaction.id},"officerLevel":1,"officerCity":0,"belong":1,"troop":0,"experience":$exp,"cityId":"$lordCityId"},"inheritanceBonus":1,"tryUniqueLottery":true}"""
        )
    }

    private fun pickByAffinityScore(
        nations: List<Faction>,
        lordByNationId: Map<Long, Officer>,
        rng: Random,
    ): Faction? {
        val allGenCount = nations.sumOf { it.officerCount }.coerceAtLeast(1)
        var bestNation: Faction? = null
        var bestScore = Double.POSITIVE_INFINITY

        for (nation in nations.shuffled(rng)) {
            val lord = lordByNationId[nation.id] ?: continue
            val affinityDiff = circularAffinityDiff(general.affinity.toInt(), lord.affinity.toInt())
            val score =
                log2(affinityDiff.toDouble() + 1.0) + rng.nextDouble() + sqrt(nation.officerCount.toDouble() / allGenCount.toDouble())

            if (score < bestScore) {
                bestScore = score
                bestNation = nation
            }
        }

        return bestNation
    }

    private fun pickByNationPowerWeight(
        nations: List<Faction>,
        generalsByNationId: Map<Long, List<Officer>>,
        rng: Random,
    ): Faction? {
        val weighted = mutableListOf<Pair<Faction, Double>>()

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

                develpower += (sqrt(nationGeneral.intelligence.toDouble() * nationGeneral.command.toDouble()) * 2.0 + leadership / 2.0) / 5.0 *
                    DomesticUtils.statBonus(nationGeneral.administration.toInt(), 0.5)
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
