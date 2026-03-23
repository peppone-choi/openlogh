@file:Suppress("unused")

package com.openlogh.command.constraint

import com.openlogh.entity.*

// ========== ConstraintResult ==========

sealed class ConstraintResult {
    object Pass : ConstraintResult()
    data class Fail(val reason: String) : ConstraintResult()
}

// ========== NotBeNeutral ==========
class NotBeNeutral : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.nationId != 0L) ConstraintResult.Pass
        else ConstraintResult.Fail("소속 국가가 없습니다")
    }
}

// ========== BeNeutral ==========
class BeNeutral : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.nationId == 0L) ConstraintResult.Pass
        else ConstraintResult.Fail("재야 상태가 아닙니다")
    }
}

// ========== OccupiedCity ==========
class OccupiedCity(private val allowNeutral: Boolean = false) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        if (allowNeutral && city.nationId == 0L) return ConstraintResult.Pass
        return if (city.nationId == ctx.general.nationId) ConstraintResult.Pass
        else ConstraintResult.Fail("아군 도시가 아닙니다")
    }
}

// ========== SuppliedCity ==========
class SuppliedCity : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        return if (city.supplyState > 0) ConstraintResult.Pass
        else ConstraintResult.Fail("보급이 차단된 도시입니다")
    }
}

// ========== ReqGeneralGold ==========
class ReqGeneralGold(private val amount: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val gold = ctx.general.gold
        return if (gold >= amount) ConstraintResult.Pass
        else ConstraintResult.Fail("자금이 부족합니다 (필요: ${amount}, 보유: ${gold})")
    }
}

// ========== ReqGeneralRice ==========
class ReqGeneralRice(private val amount: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val rice = ctx.general.rice
        return if (rice >= amount) ConstraintResult.Pass
        else ConstraintResult.Fail("군량이 부족합니다 (필요: ${amount}, 보유: ${rice})")
    }
}

// ========== ReqGeneralCrew ==========
class ReqGeneralCrew(private val minCrew: Int = 1) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.crew >= minCrew) ConstraintResult.Pass
        else ConstraintResult.Fail("병사가 부족합니다 (필요: ${minCrew})")
    }
}

// ========== RemainCityCapacity ==========
class RemainCityCapacity(private val key: String, private val label: String) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        val (current, max) = when (key) {
            "agri" -> city.agri to city.agriMax
            "comm" -> city.comm to city.commMax
            "secu" -> city.secu to city.secuMax
            "def" -> city.def to city.defMax
            "wall" -> city.wall to city.wallMax
            "pop" -> city.pop to city.popMax
            else -> return ConstraintResult.Fail("알 수 없는 키: $key")
        }
        return if (current < max) ConstraintResult.Pass
        else ConstraintResult.Fail("${label}: 이미 최대치입니다")
    }
}

// ========== BeLord ==========
class BeLord : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel >= 20) ConstraintResult.Pass
        else ConstraintResult.Fail("군주만 사용할 수 있습니다")
    }
}

// ========== BeChief ==========
class BeChief : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel >= 20) ConstraintResult.Pass
        else ConstraintResult.Fail("군주급 이상만 사용할 수 있습니다")
    }
}

// ========== NotLord ==========
class NotLord : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel < 20) ConstraintResult.Pass
        else ConstraintResult.Fail("군주는 사용할 수 없습니다")
    }
}

// ========== NotChief (alias for NotLord) ==========
class NotChief : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel < 20) ConstraintResult.Pass
        else ConstraintResult.Fail("군주는 사용할 수 없습니다")
    }
}

// ========== ReqOfficerLevel ==========
class ReqOfficerLevel(private val level: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel >= level) ConstraintResult.Pass
        else ConstraintResult.Fail("관직 레벨이 부족합니다 (필요: ${level})")
    }
}

// ========== ReqGeneralTrainMargin ==========
class ReqGeneralTrainMargin(private val max: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.train < max) ConstraintResult.Pass
        else ConstraintResult.Fail("훈련이 이미 최대치입니다 (${max})")
    }
}

// ========== ReqGeneralAtmosMargin ==========
class ReqGeneralAtmosMargin(private val max: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.atmos < max) ConstraintResult.Pass
        else ConstraintResult.Fail("사기가 이미 최대치입니다 (${max})")
    }
}

// ========== MustBeTroopLeader ==========
class MustBeTroopLeader : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val g = ctx.general
        return if (g.troopId == 0L || g.troopId == g.id) ConstraintResult.Pass
        else ConstraintResult.Fail("부대장만 사용할 수 있습니다")
    }
}

// ========== NotSameDestCity ==========
class NotSameDestCity : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destCity = ctx.destCity ?: return ConstraintResult.Fail("목적지 도시 정보가 없습니다")
        return if (ctx.general.cityId != destCity.id) ConstraintResult.Pass
        else ConstraintResult.Fail("같은 도시로는 이동할 수 없습니다")
    }
}

// ========== ReqNationGold ==========
class ReqNationGold(private val amount: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val gold = nation.gold
        return if (gold >= amount) ConstraintResult.Pass
        else ConstraintResult.Fail("국고 자금이 부족합니다 (필요: ${amount}, 보유: ${gold})")
    }
}

// ========== ReqNationRice ==========
class ReqNationRice(private val amount: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val rice = nation.rice
        return if (rice >= amount) ConstraintResult.Pass
        else ConstraintResult.Fail("병량이 부족합니다 (필요: ${amount}, 보유: ${rice})")
    }
}

// ========== ExistsDestGeneral ==========
class ExistsDestGeneral : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.destGeneral != null) ConstraintResult.Pass
        else ConstraintResult.Fail("대상 장수가 존재하지 않습니다")
    }
}

// ========== FriendlyDestGeneral ==========
class FriendlyDestGeneral : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val dest = ctx.destGeneral ?: return ConstraintResult.Fail("대상 장수가 존재하지 않습니다")
        return if (ctx.general.nationId == dest.nationId) ConstraintResult.Pass
        else ConstraintResult.Fail("아군 장수가 아닙니다")
    }
}

// ========== WanderingNation ==========
class WanderingNation : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Pass
        return if (nation.level <= 0.toShort()) ConstraintResult.Pass
        else ConstraintResult.Fail("방랑군 상태가 아닙니다")
    }
}

// ========== BeOpeningPart ==========
class BeOpeningPart(private val relYear: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (relYear < 1) ConstraintResult.Pass
        else ConstraintResult.Fail("오프닝 기간이 아닙니다")
    }
}

// ========== NotOpeningPart ==========
class NotOpeningPart(private val relYear: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (relYear >= 1) ConstraintResult.Pass
        else ConstraintResult.Fail("오프닝 기간입니다")
    }
}

// ========== AlwaysFail ==========
class AlwaysFail(private val reason: String) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return ConstraintResult.Fail(reason)
    }
}

// ========== MustBeNPC ==========
class MustBeNPC : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.npcState > 0) ConstraintResult.Pass
        else ConstraintResult.Fail("NPC만 사용할 수 있습니다")
    }
}

// ========== NotCapital ==========
class NotCapital : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Pass
        return if (ctx.general.cityId != nation.capitalCityId) ConstraintResult.Pass
        else ConstraintResult.Fail("수도에서는 사용할 수 없습니다")
    }
}

// ========== AvailableStrategicCommand ==========
class AvailableStrategicCommand : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        return if (nation.strategicCmdLimit <= 0) ConstraintResult.Pass
        else ConstraintResult.Fail("전략 명령 횟수가 남아있습니다")
    }
}

// ========== ReqGeneralAge ==========
class ReqGeneralAge(private val minAge: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.age >= minAge) ConstraintResult.Pass
        else ConstraintResult.Fail("나이가 부족합니다 (필요: ${minAge}세)")
    }
}

// ========== NeutralCity ==========
class NeutralCity : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        return if (city.nationId == 0L) ConstraintResult.Pass
        else ConstraintResult.Fail("공백지가 아닙니다")
    }
}

// ========== RemainCityTrust ==========
class RemainCityTrust(private val max: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        return if (city.trust < max) ConstraintResult.Pass
        else ConstraintResult.Fail("민심이 이미 최대치입니다")
    }
}

// ========== ReqGeneralStatValue ==========
class ReqGeneralStatValue(
    private val statFn: (Officer) -> Short,
    private val label: String,
    private val minValue: Int,
) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val value = statFn(ctx.general)
        return if (value >= minValue) ConstraintResult.Pass
        else ConstraintResult.Fail("${label}이(가) 부족합니다 (필요: ${minValue}, 보유: ${value})")
    }
}

// ========== EmperorSystemActive ==========
class EmperorSystemActive : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val active = ctx.env["emperorSystem"] as? Boolean ?: false
        return if (active) ConstraintResult.Pass
        else ConstraintResult.Fail("황제 시스템이 활성화되어 있지 않습니다")
    }
}

// ========== NationNotExempt ==========
class NationNotExempt : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        return if (status != "exempt") ConstraintResult.Pass
        else ConstraintResult.Fail("독자적 체계를 갖추고 있어 사용할 수 없습니다")
    }
}

// ========== NationIsIndependent ==========
class NationIsIndependent : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        return if (status == "independent") ConstraintResult.Pass
        else ConstraintResult.Fail("독립 세력이 아닙니다")
    }
}

// ========== NationIsVassal ==========
class NationIsVassal : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        return if (status == "vassal") ConstraintResult.Pass
        else ConstraintResult.Fail("제후국이 아닙니다")
    }
}

// ========== NationNotEmperor ==========
class NationNotEmperor : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        return if (status != "emperor") ConstraintResult.Pass
        else ConstraintResult.Fail("황제국은 사용할 수 없습니다")
    }
}

// ========== NationIsEmperor ==========
class NationIsEmperor : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        return if (status == "emperor") ConstraintResult.Pass
        else ConstraintResult.Fail("황제국이 아닙니다")
    }
}

// ========== DestNationIsEmperor ==========
class DestNationIsEmperor : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destNation = ctx.destNation ?: return ConstraintResult.Fail("대상 국가 정보가 없습니다")
        val status = destNation.meta["imperialStatus"] as? String ?: "independent"
        return if (status == "emperor") ConstraintResult.Pass
        else ConstraintResult.Fail("대상이 황제국이 아닙니다")
    }
}

// ========== NationHasEmperorGeneral ==========
class NationHasEmperorGeneral : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        val status = nation.meta["imperialStatus"] as? String ?: "independent"
        if (status != "emperor") return ConstraintResult.Fail("황제국이 아닙니다")
        val emperorType = nation.meta["emperorType"] as? String ?: "none"
        return if (emperorType == "legitimate") ConstraintResult.Pass
        else ConstraintResult.Fail("정통 황제가 아닙니다")
    }
}

// ========== WanderingEmperorExists ==========
class WanderingEmperorExists : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val cityId = (ctx.env["wanderingEmperorCityId"] as? Number)?.toLong() ?: 0L
        return if (cityId > 0) ConstraintResult.Pass
        else ConstraintResult.Fail("유랑 중인 천자가 없습니다")
    }
}

// ========== WanderingEmperorInTerritory ==========
class WanderingEmperorInTerritory : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val emperorCityId = (ctx.env["wanderingEmperorCityId"] as? Number)?.toLong() ?: 0L
        if (emperorCityId <= 0) return ConstraintResult.Fail("유랑 중인 천자가 없습니다")

        val cityNationById = ctx.env["cityNationById"] as? Map<Long, Long> ?: emptyMap()
        val nationId = ctx.general.nationId

        // Direct territory check
        if (cityNationById[emperorCityId] == nationId) return ConstraintResult.Pass

        // Adjacent territory check
        val mapAdjacency = ctx.env["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = ctx.env["dbToMapId"] as? Map<Long, Long> ?: emptyMap()
        val cityNationByMapId = ctx.env["cityNationByMapId"] as? Map<Long, Long> ?: emptyMap()

        val emperorMapId = dbToMapId[emperorCityId] ?: return ConstraintResult.Fail("천자가 아국 영토에 없습니다")
        val adjacentMapIds = mapAdjacency[emperorMapId] ?: emptyList()
        for (adjMapId in adjacentMapIds) {
            val adjNationId = cityNationByMapId[adjMapId] ?: 0L
            if (adjNationId == nationId) return ConstraintResult.Pass
        }

        return ConstraintResult.Fail("천자가 아국 영토에 없습니다")
    }
}

// ========== ReqNationCityCount ==========
class ReqNationCityCount(private val minCount: Int) : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val cityNationById = ctx.env["cityNationById"] as? Map<Long, Long> ?: emptyMap()
        val nationId = ctx.general.nationId
        val count = cityNationById.values.count { it == nationId }
        return if (count >= minCount) ConstraintResult.Pass
        else ConstraintResult.Fail("도시가 부족합니다 (필요: ${minCount}, 보유: ${count})")
    }
}

// ========== HasRoute ==========
class HasRoute : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destCity = ctx.destCity ?: return ConstraintResult.Fail("목적지 정보가 없습니다")
        val city = ctx.city
        val srcCityId = city?.id ?: ctx.general.cityId

        val mapAdjacency = ctx.env["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = ctx.env["dbToMapId"] as? Map<Long, Long> ?: emptyMap()
        val mapToDbId = ctx.env["mapToDbId"] as? Map<Long, Long> ?: emptyMap()
        val cityNationByMapId = ctx.env["cityNationByMapId"] as? Map<Long, Long> ?: emptyMap()

        val srcMapId = dbToMapId[srcCityId] ?: return ConstraintResult.Fail("경로를 찾을 수 없습니다")
        val destMapId = dbToMapId[destCity.id] ?: return ConstraintResult.Fail("경로를 찾을 수 없습니다")

        // BFS route finding
        val visited = mutableSetOf(srcMapId)
        val queue = ArrayDeque<Long>()
        queue.add(srcMapId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == destMapId) return ConstraintResult.Pass
            val neighbors = mapAdjacency[current] ?: continue
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        return ConstraintResult.Fail("경로를 찾을 수 없습니다")
    }
}

// ========== NearCity ==========
class NearCity(private val maxDist: Int) : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destCity = ctx.destCity ?: return ConstraintResult.Fail("목적지 정보가 없습니다")
        val srcCityId = ctx.general.cityId

        val mapAdjacency = ctx.env["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = ctx.env["dbToMapId"] as? Map<Long, Long> ?: emptyMap()

        val srcMapId = dbToMapId[srcCityId] ?: return ConstraintResult.Fail("거리를 계산할 수 없습니다")
        val destMapId = dbToMapId[destCity.id] ?: return ConstraintResult.Fail("거리를 계산할 수 없습니다")

        // BFS with distance
        val visited = mutableSetOf(srcMapId)
        val queue = ArrayDeque<Pair<Long, Int>>()
        queue.add(srcMapId to 0)

        while (queue.isNotEmpty()) {
            val (current, dist) = queue.removeFirst()
            if (current == destMapId) {
                return if (dist <= maxDist) ConstraintResult.Pass
                else ConstraintResult.Fail("거리가 너무 멉니다 (최대: ${maxDist})")
            }
            if (dist >= maxDist) continue
            val neighbors = mapAdjacency[current] ?: continue
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor to dist + 1)
                }
            }
        }

        return ConstraintResult.Fail("거리가 너무 멉니다 (최대: ${maxDist})")
    }
}

// ========== ReqTroopMembers ==========
class ReqTroopMembers : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val g = ctx.general
        val troopId = if (g.troopId == g.id) g.id else g.troopId
        val memberExists = ctx.env["troopMemberExistsByTroopId"] as? Map<Long, Boolean> ?: emptyMap()
        return if (memberExists[troopId] == true) ConstraintResult.Pass
        else ConstraintResult.Fail("부대원이 없습니다")
    }
}

// ========== AllowWar ==========
class AllowWar : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val nation = ctx.nation ?: return ConstraintResult.Fail("국가 정보가 없습니다")
        return if (nation.warState <= 0) ConstraintResult.Pass
        else ConstraintResult.Fail("전쟁 금지 상태입니다")
    }
}

// ========== HasRouteWithEnemy ==========
class HasRouteWithEnemy : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destCity = ctx.destCity ?: return ConstraintResult.Fail("목적지 정보가 없습니다")
        val atWarNationIds = ctx.env["atWarNationIds"] as? Set<Long> ?: emptySet()

        if (destCity.nationId !in atWarNationIds) {
            return ConstraintResult.Fail("교전중인 국가가 아닙니다")
        }

        val srcCityId = ctx.general.cityId
        val mapAdjacency = ctx.env["mapAdjacency"] as? Map<Long, List<Long>> ?: emptyMap()
        val dbToMapId = ctx.env["dbToMapId"] as? Map<Long, Long> ?: emptyMap()
        val mapToDbId = ctx.env["mapToDbId"] as? Map<Long, Long> ?: emptyMap()
        val cityNationByMapId = ctx.env["cityNationByMapId"] as? Map<Long, Long> ?: emptyMap()
        val nationId = ctx.general.nationId

        val srcMapId = dbToMapId[srcCityId] ?: return ConstraintResult.Fail("경로를 찾을 수 없습니다")
        val destMapId = dbToMapId[destCity.id] ?: return ConstraintResult.Fail("경로를 찾을 수 없습니다")

        // BFS through friendly/neutral/enemy territory
        val visited = mutableSetOf(srcMapId)
        val queue = ArrayDeque<Long>()
        queue.add(srcMapId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == destMapId) return ConstraintResult.Pass
            val neighbors = mapAdjacency[current] ?: continue
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    val ownerNationId = cityNationByMapId[neighbor] ?: 0L
                    if (ownerNationId == nationId || ownerNationId == 0L || ownerNationId in atWarNationIds) {
                        visited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }

        return ConstraintResult.Fail("경로를 찾을 수 없습니다")
    }
}

// ========== AllowJoinAction ==========
class AllowJoinAction : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val g = ctx.general
        if (g.makeLimit <= 0) return ConstraintResult.Pass
        val limit = (ctx.env["joinActionLimit"] as? Number)?.toInt() ?: 12
        return ConstraintResult.Fail("${limit}턴 후에 가능합니다")
    }
}

// ========== BattleGroundCity ==========
class BattleGroundCity : Constraint {
    @Suppress("UNCHECKED_CAST")
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val destCity = ctx.destCity ?: return ConstraintResult.Fail("목적지 정보가 없습니다")
        if (destCity.nationId == 0L) return ConstraintResult.Pass
        if (destCity.nationId == ctx.general.nationId) return ConstraintResult.Pass
        val atWarNationIds = ctx.env["atWarNationIds"] as? Set<Long> ?: emptySet()
        return if (destCity.nationId in atWarNationIds) ConstraintResult.Pass
        else ConstraintResult.Fail("교전중인 국가의 도시가 아닙니다")
    }
}

// ========== AllowDiplomacy ==========
class AllowDiplomacy(private val minLevel: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.officerLevel >= minLevel) ConstraintResult.Pass
        else ConstraintResult.Fail("외교 권한이 없습니다 (필요 관직: ${minLevel})")
    }
}

// ========== NotInjured ==========
class NotInjured(private val maxInjury: Int = 0) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        return if (ctx.general.injury <= maxInjury) ConstraintResult.Pass
        else ConstraintResult.Fail("부상 상태에서는 사용할 수 없습니다 (부상: ${ctx.general.injury})")
    }
}

// ========== ReqCityCapacity ==========
class ReqCityCapacity(
    private val field: String,
    private val displayName: String,
    private val reqAmount: Int,
) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val city = ctx.city ?: return ConstraintResult.Fail("도시 정보가 없습니다")
        val value = when (field) {
            "pop" -> city.pop
            "agri" -> city.agri
            "comm" -> city.comm
            else -> 0
        }
        return if (value >= reqAmount) ConstraintResult.Pass
        else ConstraintResult.Fail("${displayName}이 부족합니다 (필요: $reqAmount, 현재: $value)")
    }
}

// ========== ReqGeneralCrewMargin ==========
class ReqGeneralCrewMargin(private val targetCrewType: Int) : Constraint {
    override fun test(ctx: ConstraintContext): ConstraintResult {
        val gen = ctx.general
        if (gen.crewType.toInt() != targetCrewType) return ConstraintResult.Pass
        val maxCrew = gen.leadership * 100
        return if (gen.crew < maxCrew) ConstraintResult.Pass
        else ConstraintResult.Fail("병력이 이미 최대치입니다")
    }
}
